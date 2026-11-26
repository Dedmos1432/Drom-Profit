package ru.dedmos.dromprofit.presentation.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigInteger
import java.net.URI
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch
import ru.dedmos.dromprofit.domain.AssessmentStatus
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarSource
import ru.dedmos.dromprofit.domain.Expense
import ru.dedmos.dromprofit.domain.ExpenseKind
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.presentation.components.BadgeTone
import ru.dedmos.dromprofit.presentation.components.ErrorState
import ru.dedmos.dromprofit.presentation.components.LoadingState
import ru.dedmos.dromprofit.presentation.components.OutlineAction
import ru.dedmos.dromprofit.presentation.components.PageHeading
import ru.dedmos.dromprofit.presentation.components.PriceText
import ru.dedmos.dromprofit.presentation.components.ProfitBadge
import ru.dedmos.dromprofit.presentation.components.ProfitBreadcrumb
import ru.dedmos.dromprofit.presentation.components.ProfitScaffold
import ru.dedmos.dromprofit.presentation.components.ProfitScreenColumn
import ru.dedmos.dromprofit.presentation.components.VehicleGallery
import ru.dedmos.dromprofit.presentation.components.formatMileage
import ru.dedmos.dromprofit.presentation.components.formatRubles
import ru.dedmos.dromprofit.presentation.components.isDemoModelPhoto
import ru.dedmos.dromprofit.presentation.screens.add.EditVehicleDialog
import ru.dedmos.dromprofit.presentation.ui.theme.DromProfitTheme
import ru.dedmos.dromprofit.presentation.viewmodels.CarDetailUiState
import ru.dedmos.dromprofit.presentation.viewmodels.CarDetailViewModel
import ru.dedmos.dromprofit.presentation.viewmodels.DetailDialog
import ru.dedmos.dromprofit.presentation.viewmodels.ReviewGroup

private enum class DetailAnchor(val path: String) {
    SPECS("specs"),
    EXPENSES("expenses"),
    BENEFITS("benefits"),
    NOTES("notes"),
    SUMMARY("financial-summary"),
}

@Composable
fun CarDetailRoute(
    carId: String,
    onBack: () -> Unit,
    initialSection: String? = null,
    viewModel: CarDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by rememberSaveable(carId) { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (!editing) viewModel.load(carId) }
    BackHandler(enabled = state.busy) {}

    ProfitScaffold(title = "Автомобиль", onBack = onBack) { insets ->
        CarDetailColumn(insets) {
            val car = state.car
            when {
                car != null -> VehicleDetails(
                    car = car,
                    state = state,
                    initialSection = initialSection,
                    onBack = onBack,
                    onEdit = { editing = true },
                    onRetry = { viewModel.load(carId) },
                    onRetrySummary = viewModel::retrySummary,
                    onReviewedExpenses = viewModel::reviewExpenses,
                    onReviewedBenefits = viewModel::reviewBenefits,
                    onDialog = viewModel::showDialog,
                )
                state.loading -> LoadingState("Загружаем автомобиль…")
                state.notFound -> {
                    PageHeading(title = "Автомобиль не найден")
                    DetailHint("Возможно, автомобиль уже удалён из вашей выборки.")
                    TextButton(onClick = onBack) { Text("Вернуться в выборку") }
                }
                else -> {
                    PageHeading(title = "Автомобиль")
                    ErrorState(
                        message = state.loadError ?: "Не удалось загрузить автомобиль.",
                        onRetry = { viewModel.load(carId) },
                    )
                    TextButton(onClick = onBack) { Text("Вернуться в выборку") }
                }
            }
        }
    }

    state.car?.takeIf { editing }?.let { car ->
        EditVehicleDialog(
            car = car,
            onClose = { editing = false },
            onSaved = {
                editing = false
                viewModel.load(carId)
            },
        )
    }
    state.dialog?.let { dialog ->
        CarRecordDialog(
            dialog = dialog,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CarDetailColumn(
    insets: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    val usualScrolling = LocalBringIntoViewSpec.current
    val topPadding = with(LocalDensity.current) { 16.dp.toPx() }
    val sectionScrolling = remember(topPadding) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                offset - topPadding
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides sectionScrolling) {
        ProfitScreenColumn(insets, Modifier.imePadding()) {
            CompositionLocalProvider(LocalBringIntoViewSpec provides usualScrolling) { content() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleDetails(
    car: Car,
    state: CarDetailUiState,
    initialSection: String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onRetrySummary: () -> Unit,
    onReviewedExpenses: (Boolean) -> Unit,
    onReviewedBenefits: (Boolean) -> Unit,
    onDialog: (DetailDialog) -> Unit,
) {
    val name = listOfNotNull("${car.brand} ${car.model}", car.year?.toString()).joinToString(", ")
    val anchorRequests = remember(car.id) { DetailAnchor.entries.associateWith { BringIntoViewRequester() } }
    val scope = rememberCoroutineScope()
    LaunchedEffect(car.id, initialSection) {
        val anchor = DetailAnchor.entries.firstOrNull { it.path == initialSection }
        if (anchor != null) {
            withFrameNanos { }
            anchorRequests.getValue(anchor).bringIntoView()
        }
    }

    ProfitBreadcrumb(current = name, onHome = onBack)
    state.loadError?.let {
        ErrorState(
            message = "Показаны последние загруженные характеристики. $it",
            onRetry = onRetry,
        )
    }
    VehicleGallery(imageUrl = car.imageUrl, contentDescription = name)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageHeading(title = name)
        ProfitBadge(
            text = car.sourceLabel(),
            tone = if (car.source == CarSource.DEMO) BadgeTone.INFO else BadgeTone.NEUTRAL,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            DetailHint("Цена объявления")
            PriceText(car.price)
        }
        car.marketPrice?.let { valuation ->
            Text("Рыночная оценка Дрома  ${formatRubles(valuation)}")
        }
    }
    VehicleCharacteristics(
        car = car,
        modifier = Modifier.bringIntoViewRequester(anchorRequests.getValue(DetailAnchor.SPECS)),
    )
    OpenOriginalListing(car)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        OutlineAction(label = "Редактировать характеристики", enabled = state.actionsEnabled, onClick = onEdit)
        TextButton(onClick = { scope.launch { anchorRequests.getValue(DetailAnchor.SUMMARY).bringIntoView() } }) {
            Text("К экономической оценке")
        }
    }
    val reviewAvailable = !state.loading && state.loadError == null
    ExpensesSection(
        expenses = car.expenses,
        knownTotal = state.summary?.totalExpenses,
        reviewed = car.expensesReviewed,
        enabled = state.actionsEnabled,
        reviewAvailable = reviewAvailable,
        reviewError = state.mutationError.takeIf { state.failedReview == ReviewGroup.EXPENSES },
        onReviewed = onReviewedExpenses,
        onDialog = onDialog,
        headingModifier = Modifier.bringIntoViewRequester(anchorRequests.getValue(DetailAnchor.EXPENSES)),
    )
    BenefitsSection(
        benefits = car.benefits,
        knownTotal = state.summary?.totalBenefits,
        reviewed = car.benefitsReviewed,
        enabled = state.actionsEnabled,
        reviewAvailable = reviewAvailable,
        reviewError = state.mutationError.takeIf { state.failedReview == ReviewGroup.BENEFITS },
        onReviewed = onReviewedBenefits,
        onDialog = onDialog,
        headingModifier = Modifier.bringIntoViewRequester(anchorRequests.getValue(DetailAnchor.BENEFITS)),
    )
    NotesSection(
        notes = car.notes,
        enabled = state.actionsEnabled,
        onDialog = onDialog,
        headingModifier = Modifier.bringIntoViewRequester(anchorRequests.getValue(DetailAnchor.NOTES)),
    )
    FinancialSummary(
        car = car,
        state = state,
        onRetry = onRetrySummary,
        modifier = Modifier.bringIntoViewRequester(anchorRequests.getValue(DetailAnchor.SUMMARY)),
    )
    TextButton(onClick = onBack) { Text("Вернуться в выборку") }
}

@Composable
private fun VehicleCharacteristics(car: Car, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        HorizontalDivider()
        SectionHeading("Характеристики", modifier = Modifier.padding(top = 8.dp))
        DetailAttribute("Год выпуска", car.year?.toString())
        DetailAttribute("Пробег", car.mileage?.let(::formatMileage))
        DetailAttribute("Двигатель", car.engineLabel())
        DetailAttribute("Коробка передач", car.transmission)
        DetailAttribute("Привод", car.driveType)
        DetailAttribute("Город продажи", car.city)
        DetailAttribute("Комплектация", car.trim)
    }
}

@Composable
private fun OpenOriginalListing(car: Car) {
    val original = originalListing(car) ?: return
    val uriHandler = LocalUriHandler.current
    var linkError by rememberSaveable(car.id, original) { mutableStateOf(false) }
    TextButton(onClick = {
        linkError = runCatching { uriHandler.openUri(original) }.isFailure
    }) { Text("Оригинальное объявление") }
    if (linkError) DetailHint("Не удалось открыть объявление в браузере.")
}

@Composable
private fun FinancialSummary(
    car: Car,
    state: CarDetailUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading("Экономическая оценка")
            val summary = state.summary
            val complete = summary?.assessmentStatus == AssessmentStatus.COMPLETE
            when {
                state.summaryLoading -> DetailHint(
                    if (summary == null) "Загружаем оценку…" else "Обновляем оценку…",
                )
                state.summaryError != null -> ErrorState(
                    message = state.summaryError,
                    onRetry = onRetry,
                )
                summary != null -> ProfitBadge(
                    text = if (complete) "Все данные рассмотрены" else "Предварительная оценка",
                    tone = if (complete) BadgeTone.SUCCESS else BadgeTone.WARNING,
                )
            }
            if (summary != null && !complete) {
                val reasons = buildList {
                    add("Учтены только известные суммы.")
                    if (state.loadError == null && !state.loading) {
                        if (!car.expensesReviewed) add("Отметьте, что рассмотрели расходы.")
                        if (!car.benefitsReviewed) add("Отметьте, что рассмотрели преимущества.")
                    }
                }
                DetailHint(reasons.joinToString(" "))
            }
            SummaryAmount("Цена объявления", summary?.listingPrice)
            SummaryAmount("Учтённые вложения", summary?.totalExpenses)
            SummaryTotal("Предполагаемые затраты на приобретение", summary?.acquisitionCost)
            SummaryAmount("Ценность преимуществ", summary?.totalBenefits)
            SummaryTotal("Скорректированная стоимость", summary?.adjustedCost)
            DetailHint("Учитывает вашу оценку преимуществ при сравнении.")
            if (summary != null && (summary.unknownExpenses > 0 || summary.unknownBenefits > 0)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Неизвестные суммы", modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        if (summary.unknownExpenses > 0) Text("Расходы · ${summary.unknownExpenses}")
                        if (summary.unknownBenefits > 0) Text("Преимущества · ${summary.unknownBenefits}")
                    }
                }
            }
            if (summary != null && summary.adjustedCost.signum() < 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProfitBadge("Скорректированная стоимость ниже нуля", BadgeTone.WARNING)
                    DetailHint("Проверьте персональную ценность указанных преимуществ.")
                }
            }
        }
    }
}

@Composable
private fun SummaryAmount(label: String, value: Number?) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, Modifier.weight(1f))
        Text(
            text = value?.let(::formatRubles) ?: "—",
            modifier = Modifier.weight(0.55f),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SummaryTotal(label: String, value: Number?) {
    HorizontalDivider()
    Column(Modifier.padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        if (value != null) PriceText(value)
        else Text("—", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun DetailAttribute(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, Modifier.weight(0.42f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf(String::isNotBlank) ?: "Не указано", Modifier.weight(0.58f))
    }
}

@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
internal fun DetailHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun Car.sourceLabel(): String = when (source) {
    CarSource.DROM -> "Дром"
    CarSource.DEMO -> if (isDemoModelPhoto(imageUrl)) "Демо · фото модели" else "Демо-источник"
    CarSource.MANUAL -> "Ручной ввод"
}

private fun Car.engineLabel(): String? {
    val number = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
    return listOfNotNull(
        engineType?.takeIf(String::isNotBlank),
        engineVolume?.let { number.format(it) + " л" },
        enginePower?.let { number.format(it) + " л. с." },
    ).joinToString(", ").takeIf(String::isNotBlank)
}

private fun originalListing(car: Car): String? {
    val original = car.sourceUrl ?: return null
    if (car.source == CarSource.DEMO) return null
    val uri = runCatching { URI(original) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    if (uri.scheme?.lowercase(Locale.ROOT) !in listOf("http", "https") || uri.userInfo != null) return null
    if (host.equals("auto.drom.ru", ignoreCase = true) && uri.path.orEmpty().startsWith("/demo/")) return null
    return original
}

@Preview(name = "Карточка автомобиля · Web", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CarDetailPreview() {
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
        imageUrl = "/demo/vehicles/camry-xv50-silver.jpg",
        engineType = "Бензин",
        enginePower = 181,
        driveType = "Передний",
        city = "Владивосток",
        source = CarSource.DEMO,
        expenses = listOf(Expense("brakes", "Замена колодок", ExpenseKind.REPAIR, null)),
    )
    val summary = Summary(
        carId = car.id,
        listingPrice = car.price,
        totalExpenses = BigInteger.ZERO,
        totalBenefits = BigInteger.ZERO,
        acquisitionCost = BigInteger.valueOf(car.price),
        adjustedCost = BigInteger.valueOf(car.price),
        assessmentStatus = AssessmentStatus.INCOMPLETE,
        unknownExpenses = 1,
        unknownBenefits = 0,
    )
    DromProfitTheme {
        ProfitScaffold(title = "Автомобиль") { insets ->
            ProfitScreenColumn(insets) {
                VehicleDetails(
                    car,
                    CarDetailUiState(car = car, summary = summary, loading = false, summaryLoading = false),
                    initialSection = null,
                    onBack = {},
                    onEdit = {},
                    onRetry = {},
                    onRetrySummary = {},
                    onReviewedExpenses = {},
                    onReviewedBenefits = {},
                    onDialog = {},
                )
            }
        }
    }
}
