package ru.dedmos.dromprofit.presentation.screens.comparison

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import ru.dedmos.dromprofit.domain.AssessmentStatus
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarSource
import ru.dedmos.dromprofit.domain.Comparison
import ru.dedmos.dromprofit.domain.ComparisonItem
import ru.dedmos.dromprofit.domain.ComparisonWarning
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.presentation.components.BadgeTone
import ru.dedmos.dromprofit.presentation.components.EmptyState
import ru.dedmos.dromprofit.presentation.components.ErrorState
import ru.dedmos.dromprofit.presentation.components.LoadingState
import ru.dedmos.dromprofit.presentation.components.PageHeading
import ru.dedmos.dromprofit.presentation.components.ProfitBadge
import ru.dedmos.dromprofit.presentation.components.ProfitBreadcrumb
import ru.dedmos.dromprofit.presentation.components.ProfitScaffold
import ru.dedmos.dromprofit.presentation.components.ProfitScreenColumn
import ru.dedmos.dromprofit.presentation.components.VehiclePhoto
import ru.dedmos.dromprofit.presentation.components.formatMileage
import ru.dedmos.dromprofit.presentation.components.formatRubles
import ru.dedmos.dromprofit.presentation.components.vehicleName
import ru.dedmos.dromprofit.presentation.components.vehicleSourceLabel
import ru.dedmos.dromprofit.presentation.ui.theme.DromProfitTheme
import ru.dedmos.dromprofit.presentation.ui.theme.LinkBlue
import ru.dedmos.dromprofit.presentation.viewmodels.ComparisonContent
import ru.dedmos.dromprofit.presentation.viewmodels.ComparisonViewModel
import ru.dedmos.dromprofit.presentation.viewmodels.ScreenState

private data class ComparisonColumn(val car: Car, val item: ComparisonItem)

@Composable
internal fun ComparisonRoute(
    selection: StateFlow<List<String>>,
    onShowCars: () -> Unit,
    onExclude: (String) -> Unit,
    onPrune: (Set<String>) -> Unit,
    onOpenCar: (String, String?) -> Unit,
    viewModel: ComparisonViewModel = hiltViewModel(),
) {
    val selectedIds by selection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(selectedIds) { viewModel.load(selectedIds, onPrune) }
    ComparisonScreen(state, selectedIds.size, onShowCars, onExclude, onOpenCar) {
        viewModel.load(selectedIds, onPrune)
    }
}

@Composable
private fun ComparisonScreen(
    state: ScreenState<ComparisonContent>,
    count: Int,
    onShowCars: () -> Unit,
    onExclude: (String) -> Unit,
    onOpenCar: (String, String?) -> Unit,
    onRetry: () -> Unit,
) {
    ProfitScaffold(title = "Сравнение", onBack = onShowCars) { insets ->
        ProfitScreenColumn(insets) {
            ProfitBreadcrumb("Сравнение", onShowCars)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PageHeading("Сравнение автомобилей", "В сравнении · $count")
                ComparisonLink("Изменить состав", onClick = onShowCars)
            }
            when {
                count < 2 -> EmptyState("Выберите хотя бы два автомобиля", "Отметьте машины на экране «Моя выборка».")
                state == ScreenState.Loading -> LoadingState("Получаем сравнение…")
                state is ScreenState.Error -> ErrorState(
                    if (state.sessionExpired) "Сессия недоступна. Вернитесь к выборке, чтобы начать новую сессию." else state.message,
                    onRetry = onRetry.takeUnless { state.sessionExpired },
                )
                state is ScreenState.Content -> {
                    val result = state.data.comparison
                    val columns = result.items.mapNotNull { item ->
                        state.data.carsById[item.summary.carId]?.let { ComparisonColumn(it, item) }
                    }
                    if (columns.size < 2) {
                        EmptyState("Часть автомобилей недоступна", "Вернитесь к выборке и уточните состав сравнения.")
                    } else {
                        if (result.provisional) ProfitBadge("Предварительное сравнение", BadgeTone.WARNING)
                        ComparisonWarnings(result.warnings, columns, onOpenCar)
                        Text(
                            "Затраты на приобретение включают цену и учтённые вложения. Для сравнения мы учитываем вашу оценку преимуществ. Разница показана относительно минимальной скорректированной стоимости.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("В таблице можно прокручивать строки и столбцы.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ComparisonTable(result, columns, onExclude, onOpenCar)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonWarnings(
    warnings: List<ComparisonWarning>,
    columns: List<ComparisonColumn>,
    onOpenCar: (String, String?) -> Unit,
) {
    for ((code, group) in warnings.groupBy(ComparisonWarning::code)) {
        val title = when (code) {
            "incomplete" -> "Часть данных требует уточнения"
            "double_count" -> "Обнаружен двойной учёт оснащения"
            "negative_adjusted_cost" -> "Скорректированная стоимость ниже нуля"
            else -> group.first().message
        }
        val explanation = when (code) {
            "incomplete" -> "Некоторые расходы или преимущества ещё не рассмотрены либо их стоимость неизвестна."
            "double_count" -> "Разницы недоступны. Проверьте расходы и преимущества и выберите один способ учёта оснащения."
            "negative_adjusted_cost" -> "Персональная ценность преимуществ превышает затраты на приобретение. Проверьте указанные суммы."
            else -> null
        }
        val keys = group.mapNotNull(ComparisonWarning::factorKey).toSet()
        val affected = columns.filter { column ->
            when (code) {
                "incomplete" -> column.item.summary.assessmentStatus == AssessmentStatus.INCOMPLETE
                "negative_adjusted_cost" -> column.item.summary.adjustedCost < 0
                "double_count" -> column.car.expenses.any { it.kind == ExpenseKind.MISSING_EQUIPMENT && it.factorKey in keys } ||
                    column.car.benefits.any { it.factorKey in keys }
                else -> false
            }
        }.ifEmpty { if (code == "double_count") columns else emptyList() }
        val colors = MaterialTheme.colorScheme
        Surface(shape = MaterialTheme.shapes.medium, color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    explanation?.let { Text(it) }
                    affected.forEach { column ->
                        val car = column.car
                        val identity = listOfNotNull(
                            vehicleName(car), formatRubles(car.price), car.mileage?.let(::formatMileage),
                        ).joinToString(" · ")
                        ComparisonLink(identity) {
                            onOpenCar(car.id, if (code == "negative_adjusted_cost") "benefits" else null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonTable(
    result: Comparison,
    columns: List<ComparisonColumn>,
    onExclude: (String) -> Unit,
    onOpenCar: (String, String?) -> Unit,
) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    val colors = MaterialTheme.colorScheme
    val showUnknown = columns.any { it.item.summary.unknownExpenses > 0 || it.item.summary.unknownBenefits > 0 }
    val doubleCount = result.warnings.any { it.code == "double_count" }
    val showMinimum = !doubleCount && result.warnings.none { it.code == "negative_adjusted_cost" }
    Column(
        Modifier.fillMaxWidth().heightIn(max = 500.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, colors.outlineVariant, MaterialTheme.shapes.medium),
    ) {
        TableLine("Показатель", columns, horizontal, background = colors.surfaceVariant) { column ->
            ComparisonLink(vehicleName(column.car), end = true) { onOpenCar(column.car.id, null) }
            Text(formatRubles(column.item.summary.listingPrice), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            column.car.mileage?.let { Text(formatMileage(it), color = colors.onSurfaceVariant, textAlign = TextAlign.End) }
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(vertical)) {
            TableLine("Автомобили", columns, horizontal) { column ->
                VehiclePhoto(column.car.imageUrl, Modifier.fillMaxWidth().height(85.dp))
                ProfitBadge(vehicleSourceLabel(column.car), if (column.car.source == CarSource.DEMO) BadgeTone.INFO else BadgeTone.NEUTRAL)
                TextButton(onClick = { onExclude(column.car.id) }) { Text("Исключить") }
            }
            TableSection("Экономические показатели")
            MoneyLine("Цена объявления", columns, horizontal) { it.summary.listingPrice }
            MoneyLine("Учтённые вложения", columns, horizontal) { it.summary.totalExpenses }
            MoneyLine("Затраты на приобретение", columns, horizontal, strong = true) { it.summary.acquisitionCost }
            MoneyLine("Ценность преимуществ", columns, horizontal) { it.summary.totalBenefits }
            TableLine("Скорректированная стоимость", columns, horizontal) { column ->
                val minimum = showMinimum && column.item.differenceFromMin == 0L
                Column(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .background(if (minimum) colors.secondaryContainer else Color.Transparent)
                        .padding(if (minimum) 6.dp else 0.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(formatRubles(column.item.summary.adjustedCost), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                    if (minimum) {
                        ProfitBadge(
                            if (result.provisional) "Предварительный минимум" else "Минимум",
                            if (result.provisional) BadgeTone.WARNING else BadgeTone.INFO,
                        )
                    }
                }
            }
            TableLine("Разница с минимумом", columns, horizontal, note = when {
                doubleCount -> "Двойной учёт"
                result.provisional -> "Предварительно"
                else -> null
            }) { column ->
                Text(column.item.differenceFromMin?.let(::formatRubles) ?: "Недоступна", textAlign = TextAlign.End)
            }
            TableLine("Готовность оценки", columns, horizontal) { column ->
                val complete = column.item.summary.assessmentStatus == AssessmentStatus.COMPLETE
                ProfitBadge(
                    if (complete) "Все данные рассмотрены" else "Предварительная оценка",
                    if (complete) BadgeTone.SUCCESS else BadgeTone.WARNING,
                )
            }
            if (showUnknown) {
                TableLine("Неизвестные суммы", columns, horizontal) { column ->
                    val summary = column.item.summary
                    if (summary.unknownExpenses > 0) Text("Расходы · ${summary.unknownExpenses}")
                    if (summary.unknownBenefits > 0) Text("Преимущества · ${summary.unknownBenefits}")
                    if (summary.unknownExpenses == 0 && summary.unknownBenefits == 0) Text("Нет")
                }
            }
            TableLine("Записи по автомобилю", columns, horizontal) { column ->
                ComparisonLink("Расходы") { onOpenCar(column.car.id, "expenses") }
                ComparisonLink("Преимущества") { onOpenCar(column.car.id, "benefits") }
                ComparisonLink("Заметки") { onOpenCar(column.car.id, "notes") }
            }
            TableSection("Характеристики")
            for ((label, getValue) in specifications) {
                TableLine(label, columns, horizontal) { column ->
                    Text(getValue(column.car) ?: "Не указано", textAlign = TextAlign.End)
                }
            }
            if (columns.any { it.car.marketPrice != null }) {
                TableLine("Рыночная оценка Дрома", columns, horizontal) {
                    Text(formatRubles(it.car.marketPrice), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun MoneyLine(
    label: String,
    columns: List<ComparisonColumn>,
    scroll: ScrollState,
    strong: Boolean = false,
    amount: (ComparisonItem) -> Long?,
) {
    TableLine(label, columns, scroll) { column ->
        Text(
            text = formatRubles(amount(column.item)),
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun TableSection(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        fontWeight = FontWeight.Bold,
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun TableLine(
    label: String,
    columns: List<ComparisonColumn>,
    scroll: ScrollState,
    note: String? = null,
    background: Color = MaterialTheme.colorScheme.surface,
    cell: @Composable (ComparisonColumn) -> Unit,
) {
    Row(Modifier.fillMaxWidth().background(background), verticalAlignment = Alignment.Top) {
        Column(
            Modifier.width(136.dp).padding(horizontal = 10.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.weight(1f).horizontalScroll(scroll)) {
            for (column in columns) {
                Column(
                    Modifier.width(166.dp).padding(horizontal = 10.dp, vertical = 9.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { cell(column) }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ComparisonLink(text: String, end: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 5.dp),
        color = LinkBlue,
        style = MaterialTheme.typography.bodyMedium,
        textDecoration = TextDecoration.Underline,
        textAlign = if (end) TextAlign.End else TextAlign.Start,
    )
}

private val specifications: List<Pair<String, (Car) -> String?>> = listOf(
    "Марка и модель" to { car -> "${car.brand} ${car.model}" },
    "Год выпуска" to { car -> car.year?.toString() },
    "Пробег" to { car -> car.mileage?.let(::formatMileage) },
    "Комплектация" to Car::trim,
    "Тип двигателя" to Car::engineType,
    "Объём двигателя" to { car -> car.engineVolume?.let { "$it л" } },
    "Мощность" to { car -> car.enginePower?.let { "$it л. с." } },
    "Коробка передач" to Car::transmission,
    "Привод" to Car::driveType,
    "Город продажи" to Car::city,
)

@Preview(name = "Сравнение", widthDp = 390, heightDp = 844)
@Composable
private fun ComparisonPreview() {
    val first = Car(
        id = "camry", sourceUrl = null, brand = "Toyota", model = "Camry", year = 2015,
        price = 1_200_000, mileage = 145_000, engineVolume = 2.5, transmission = "Автомат",
        imageUrl = "/demo/vehicles/camry-xv50-silver.jpg", source = CarSource.DEMO,
    )
    val second = first.copy(id = "accord", brand = "Honda", model = "Accord", year = 2016, price = 1_350_000, imageUrl = "/demo/vehicles/accord-ix.jpg")
    val summary = Summary(
        first.id, first.price, 33_000L, 25_000L, 1_233_000L,
        1_208_000L, AssessmentStatus.COMPLETE, 0, 0,
    )
    val result = Comparison(
        listOf(
            ComparisonItem(summary, 0L),
            ComparisonItem(summary.copy(carId = second.id, listingPrice = second.price, assessmentStatus = AssessmentStatus.INCOMPLETE, unknownExpenses = 1), 147_000L),
        ),
        listOf(ComparisonWarning("incomplete", "Часть данных требует уточнения")),
        provisional = true,
    )
    DromProfitTheme {
        ComparisonScreen(
            ScreenState.Content(ComparisonContent(result, listOf(first, second).associateBy(Car::id))),
            2, {}, {}, { _, _ -> }, {},
        )
    }
}
