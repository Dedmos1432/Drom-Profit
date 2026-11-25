package ru.dedmos.dromprofit.presentation.screens.detail

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Locale
import ru.dedmos.dromprofit.domain.AssessmentStatus
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarSource
import ru.dedmos.dromprofit.domain.NoteKind
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.presentation.components.AmountLine
import ru.dedmos.dromprofit.presentation.components.ErrorState
import ru.dedmos.dromprofit.presentation.components.formatRubles
import ru.dedmos.dromprofit.presentation.components.LoadingState
import ru.dedmos.dromprofit.presentation.components.Panel
import ru.dedmos.dromprofit.presentation.components.PrimaryAction
import ru.dedmos.dromprofit.presentation.components.ProfitScaffold
import ru.dedmos.dromprofit.presentation.components.SecondaryAction
import ru.dedmos.dromprofit.presentation.components.StatusLabel
import ru.dedmos.dromprofit.presentation.components.VehicleImage
import ru.dedmos.dromprofit.presentation.ui.theme.DromProfitTheme
import ru.dedmos.dromprofit.presentation.viewmodels.CarDetailUiState
import ru.dedmos.dromprofit.presentation.viewmodels.CarDetailViewModel
import ru.dedmos.dromprofit.presentation.viewmodels.DetailDialog

private enum class DetailSection(val title: String) {
    Overview("Автомобиль"),
    Characteristics("Характеристики"),
    Expenses("Расходы"),
    Benefits("Преимущества"),
    Notes("Осмотр и заметки"),
    History("История автомобиля"),
    Calculation("Детализация расчёта"),
}

@Composable
fun CarDetailRoute(
    carId: String,
    onBack: () -> Unit,
    onEditCar: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: CarDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var section by rememberSaveable(carId) { mutableStateOf(DetailSection.Overview) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load(carId) }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    val back: () -> Unit = {
        if (!state.busy) {
            if (section == DetailSection.Overview) onBack() else section = DetailSection.Overview
        }
    }
    BackHandler(enabled = state.busy || section != DetailSection.Overview, onBack = back)

    ProfitScaffold(title = section.title, onBack = back) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val car = state.car
            if (car == null) {
                val error = state.loadError
                if (error != null) {
                    ErrorState(error, { viewModel.load(carId) }, Modifier.align(Alignment.Center).padding(16.dp))
                } else {
                    LoadingState("Загружаем автомобиль…", Modifier.align(Alignment.Center))
                }
            } else {
                key(section) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        state.loadError?.let { ErrorState(it, { viewModel.load(carId) }) }
                        if (state.dialog == null) {
                            state.mutationError?.let { ErrorState(it) }
                            if (state.busy) LoadingState("Сохраняем изменения…")
                        }
                        when (section) {
                            DetailSection.Overview -> CarOverview(
                                car = car,
                                state = state,
                                onSection = { section = it },
                                onRetrySummary = viewModel::retrySummary,
                                onDelete = { viewModel.showDialog(DetailDialog.DeleteCar) },
                            )
                            DetailSection.Characteristics -> VehicleCharacteristics(
                                car = car,
                                enabled = state.actionsEnabled,
                                onEdit = { onEditCar(carId) },
                            )
                            DetailSection.Expenses -> ExpensesSection(
                                car.expenses,
                                car.expensesReviewed,
                                state.actionsEnabled,
                                viewModel::reviewExpenses,
                                viewModel::showDialog,
                            )
                            DetailSection.Benefits -> BenefitsSection(
                                car.benefits,
                                car.benefitsReviewed,
                                state.actionsEnabled,
                                viewModel::reviewBenefits,
                                viewModel::showDialog,
                            )
                            DetailSection.Notes, DetailSection.History -> NotesSection(
                                car.notes,
                                history = section == DetailSection.History,
                                enabled = state.actionsEnabled,
                                onDialog = viewModel::showDialog,
                            )
                            DetailSection.Calculation -> SummaryCard(
                                car,
                                state.summary,
                                state.summaryLoading,
                                state.summaryError,
                                viewModel::retrySummary,
                                detailed = true,
                            )
                        }
                    }
                }
            }
        }
    }

    state.dialog?.let {
        CarRecordDialog(
            dialog = it,
            busy = state.busy,
            error = state.mutationError,
            onDismiss = { viewModel.showDialog(null) },
            onSaveExpense = viewModel::saveExpense,
            onSaveBenefit = viewModel::saveBenefit,
            onSaveNote = viewModel::saveNote,
            onDelete = viewModel::confirmDelete,
        )
    }
}

@Composable
private fun CarOverview(
    car: Car,
    state: CarDetailUiState,
    onSection: (DetailSection) -> Unit,
    onRetrySummary: () -> Unit,
    onDelete: () -> Unit,
) {
    Panel {
        VehicleImage(car.imageUrl, Modifier.fillMaxWidth().height(180.dp))
        Text(
            text = "${car.brand} ${car.model}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = listOfNotNull(car.year?.toString(), car.city?.takeIf(String::isNotBlank)).joinToString(" · ")
                .ifEmpty { "Год и город не указаны" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(formatRubles(car.price), style = MaterialTheme.typography.headlineMedium)
        StatusLabel(car.source.detailLabel)
    }
    SummaryCard(car, state.summary, state.summaryLoading, state.summaryError, onRetrySummary) {
        onSection(DetailSection.Calculation)
    }
    Panel {
        DetailSection.entries.filter { it != DetailSection.Overview && it != DetailSection.Calculation }
            .forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider()
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = when (item) {
                        DetailSection.Expenses -> ({ Text(reviewLabel(car.expensesReviewed)) })
                        DetailSection.Benefits -> ({ Text(reviewLabel(car.benefitsReviewed)) })
                        DetailSection.History -> ({
                            Text(if (car.notes.any { it.kind == NoteKind.REPORT }) "Есть записи из отчёта" else "Сведения не внесены")
                        })
                        else -> null
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable(role = Role.Button, onClick = { onSection(item) }),
                )
            }
    }
    TextButton(onClick = onDelete, enabled = state.actionsEnabled, modifier = Modifier.fillMaxWidth()) {
        Text("Удалить автомобиль", color = if (state.actionsEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryCard(
    car: Car,
    summary: Summary?,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    detailed: Boolean = false,
    onDetails: (() -> Unit)? = null,
) {
    Panel {
        SectionHeading(if (detailed) "Суммы и полнота данных" else "Экономический расчёт")
        when {
            loading -> LoadingState("Загружаем расчёт…")
            summary == null && error != null -> ErrorState(error, onRetry)
            summary != null -> {
                val complete = summary.assessmentStatus == AssessmentStatus.COMPLETE
                StatusLabel(if (complete) "Оценка полная" else "Предварительная оценка", warning = !complete)
                if (detailed) {
                    AmountLine(label = "Цена объявления", value = summary.listingPrice)
                    AmountLine(label = "Известные вложения", value = summary.totalExpenses)
                }
                AmountLine(label = "Предполагаемые затраты на приобретение", value = summary.acquisitionCost, strong = true)
                if (detailed) AmountLine(label = "Известная ценность преимуществ", value = summary.totalBenefits)
                AmountLine(label = "Скорректированная стоимость", value = summary.adjustedCost, strong = true)
                if (detailed) {
                    DetailHint("Скорректированная стоимость — условный показатель для сравнения автомобилей.")
                    DetailAttribute("Расходы с неизвестной суммой", summary.unknownExpenses.toString())
                    DetailAttribute("Преимущества с неизвестной суммой", summary.unknownBenefits.toString())
                }
                if (!complete) {
                    val reasons = buildList {
                        if (!car.expensesReviewed) add("Расходы не подтверждены.")
                        if (!car.benefitsReviewed) add("Преимущества не подтверждены.")
                        if (summary.unknownExpenses > 0 || summary.unknownBenefits > 0) add("Есть неизвестные суммы.")
                    }
                    DetailHint((listOf("Учтены только указанные суммы.") + reasons).joinToString(" "))
                }
                if (summary.adjustedCost.signum() < 0) {
                    StatusLabel("Скорректированная стоимость ниже нуля", warning = true)
                    DetailHint("Проверьте персональную ценность преимуществ.")
                }
            }
            else -> DetailHint("Расчёт пока недоступен.")
        }
        if (onDetails != null && summary != null) {
            SecondaryAction(label = "Детализация расчёта", onClick = onDetails)
        }
    }
}

@Composable
private fun VehicleCharacteristics(car: Car, enabled: Boolean, onEdit: () -> Unit) {
    Panel {
        SectionHeading("Данные автомобиля")
        DetailAttribute("Марка", car.brand)
        DetailAttribute("Модель", car.model)
        DetailAttribute("Год выпуска", car.year?.toString())
        DetailAttribute("Цена объявления", formatRubles(car.price))
        DetailAttribute("Пробег", car.mileage?.let { "${formatCharacteristic(it)} км" })
        DetailAttribute("Комплектация", car.trim)
        DetailAttribute("Тип двигателя", car.engineType)
        DetailAttribute("Объём двигателя", car.engineVolume?.let { "${formatCharacteristic(it)} л" })
        DetailAttribute("Мощность", car.enginePower?.let { "${formatCharacteristic(it)} л. с." })
        DetailAttribute("Коробка передач", car.transmission)
        DetailAttribute("Привод", car.driveType)
        DetailAttribute("Город продажи", car.city)
        DetailAttribute("Рыночная оценка Дрома", car.marketPrice?.let(::formatRubles))
        DetailAttribute("Источник", car.source.detailLabel)
        DetailAttribute("Ссылка на объявление", car.sourceUrl)
        val uriHandler = LocalUriHandler.current
        var linkError by rememberSaveable(car.id) { mutableStateOf(false) }
        val originalUrl = car.sourceUrl?.takeIf { url ->
            val uri = Uri.parse(url)
            uri.scheme == "https" && uri.host?.endsWith(".drom.ru", ignoreCase = true) == true
        }
        if (car.source == CarSource.DROM && originalUrl != null) {
            TextButton(onClick = {
                linkError = runCatching { uriHandler.openUri(originalUrl) }.isFailure
            }) { Text("Открыть объявление") }
            if (linkError) DetailHint("Не удалось открыть объявление в браузере.")
        }
    }
    PrimaryAction(label = "Редактировать характеристики", enabled = enabled, onClick = onEdit)
}

@Composable
internal fun DetailAttribute(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf(String::isNotBlank) ?: "Нет данных", Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
internal fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun DetailHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ReviewedRow(checked: Boolean, enabled: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = null, enabled = enabled)
        Text(label, Modifier.weight(1f))
    }
}

private val CarSource.detailLabel: String
    get() = when (this) {
        CarSource.DROM -> "Дром"
        CarSource.DEMO -> "Демонстрационные данные"
        CarSource.MANUAL -> "Ручной ввод"
    }

private fun reviewLabel(reviewed: Boolean) = if (reviewed) "Просмотрены" else "Требуется подтверждение"

private fun formatCharacteristic(value: Number): String = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).format(value)

@Preview(name = "Карточка автомобиля", showBackground = true)
@Composable
private fun CarOverviewPreview() {
    val car = Car(
        id = "preview",
        sourceUrl = null,
        brand = "Toyota",
        model = "Camry",
        year = 2015,
        price = 1_200_000,
        mileage = 145_000,
        engineVolume = 2.5,
        transmission = "Автомат",
        imageUrl = null,
        source = CarSource.DEMO,
        city = "Владивосток",
    )
    val summary = Summary(
        carId = car.id,
        listingPrice = car.price,
        totalExpenses = BigInteger.valueOf(140_000),
        totalBenefits = BigInteger.ZERO,
        acquisitionCost = BigInteger.valueOf(1_340_000),
        adjustedCost = BigInteger.valueOf(1_340_000),
        assessmentStatus = AssessmentStatus.INCOMPLETE,
        unknownExpenses = 1,
        unknownBenefits = 0,
    )
    DromProfitTheme {
        ProfitScaffold(title = "Автомобиль", onBack = {}) { padding ->
            Column(
                Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CarOverview(car, CarDetailUiState(car, summary, false, false), {}, {}, {})
            }
        }
    }
}
