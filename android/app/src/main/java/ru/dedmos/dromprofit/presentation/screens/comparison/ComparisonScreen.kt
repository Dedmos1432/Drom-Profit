package ru.dedmos.dromprofit.presentation.screens.comparison

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import ru.dedmos.dromprofit.domain.AssessmentStatus
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.Comparison
import ru.dedmos.dromprofit.domain.ComparisonItem
import ru.dedmos.dromprofit.domain.ComparisonWarning
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.presentation.components.ErrorState
import ru.dedmos.dromprofit.presentation.components.formatRubles
import ru.dedmos.dromprofit.presentation.components.LoadingState
import ru.dedmos.dromprofit.presentation.components.Panel
import ru.dedmos.dromprofit.presentation.components.ProfitScaffold
import ru.dedmos.dromprofit.presentation.components.StatusLabel
import ru.dedmos.dromprofit.presentation.ui.theme.DromProfitTheme
import ru.dedmos.dromprofit.presentation.ui.theme.ProfitTokens
import ru.dedmos.dromprofit.presentation.viewmodels.ComparisonContent
import ru.dedmos.dromprofit.presentation.viewmodels.ComparisonViewModel
import ru.dedmos.dromprofit.presentation.viewmodels.ScreenState
import java.math.BigInteger

@Composable
internal fun ComparisonRoute(
    selection: StateFlow<List<String>>,
    onBack: () -> Unit,
    viewModel: ComparisonViewModel = hiltViewModel()
) {
    val selectedIds by selection.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(selectedIds) { viewModel.load(selectedIds) }
    ComparisonScreen(state, viewModel.isDemo, onBack) { viewModel.load(selectedIds) }
}

@Composable
private fun ComparisonScreen(
    state: ScreenState<ComparisonContent>,
    isDemo: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    ProfitScaffold(title = "Сравнение", onBack = onBack) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 960.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(ProfitTokens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(ProfitTokens.contentGap)
            ) {
                if (isDemo) StatusLabel("Демонстрационный режим")
                when (state) {
                    ScreenState.Loading -> LoadingState("Получаем сравнение…")
                    is ScreenState.Error -> {
                        ErrorState(
                            message = if (state.sessionExpired) {
                                "Сессия недоступна. Вернитесь в выборку, чтобы начать новую сессию."
                            } else state.message,
                            onRetry = if (state.sessionExpired) null else onRetry
                        )
                    }
                    is ScreenState.Content -> {
                        val (comparison, carsById) = state.data
                        if (comparison.provisional) {
                            StatusLabel("Предварительное сравнение", warning = true)
                        }
                        comparison.warnings.forEach { warning ->
                            Panel {
                                Text(warning.message)
                                warning.factorKey?.let { key ->
                                    Text(
                                        text = if (key == "winter_tires") "Оснащение · зимняя резина" else "Оснащение · $key",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        ComparisonTable(comparison.items, carsById)
                        Text(
                            "Скорректированная стоимость учитывает указанную вами ценность преимуществ.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ComparisonTable(items: List<ComparisonItem>, carsById: Map<String, Car>) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val labelWidth = if (maxWidth < 360.dp) 128.dp else 150.dp
            val columnWidth = ((maxWidth - labelWidth) / items.size.coerceAtLeast(1)).coerceAtLeast(128.dp)
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableCell("Показатель", labelWidth, strong = true)
                    for (item in items) {
                        val car = carsById[item.summary.carId]
                        val carName = car?.let { "${it.brand} ${it.model}" } ?: "Автомобиль"
                        val caption = listOfNotNull(car?.year?.toString(), car?.mileage?.let { "$it км" })
                            .joinToString(" · ")
                        Column(
                            Modifier
                                .width(columnWidth)
                                .padding(10.dp)
                                .semantics { heading() },
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(carName, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
                            if (caption.isNotEmpty()) Text(caption, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                TableRow("Цена объявления", labelWidth, columnWidth, items) { formatRubles(it.summary.listingPrice) }
                TableRow("Вложения", labelWidth, columnWidth, items) { formatRubles(it.summary.totalExpenses) }
                TableRow("Преимущества", labelWidth, columnWidth, items) { formatRubles(it.summary.totalBenefits) }
                TableRow("Затраты на приобретение", labelWidth, columnWidth, items, strong = true) {
                    formatRubles(it.summary.acquisitionCost)
                }
                TableRow("Скорректированная стоимость", labelWidth, columnWidth, items, strong = true) {
                    formatRubles(it.summary.adjustedCost)
                }
                TableRow("Разница с минимумом", labelWidth, columnWidth, items, strong = true) {
                    formatRubles(it.differenceFromMin)
                }
                TableRow("Полнота оценки", labelWidth, columnWidth, items) {
                    if (it.summary.assessmentStatus == AssessmentStatus.COMPLETE) "Полная" else "Предварительная"
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    title: String,
    labelWidth: Dp,
    columnWidth: Dp,
    items: List<ComparisonItem>,
    strong: Boolean = false,
    value: (ComparisonItem) -> String
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TableCell(title, labelWidth, strong)
        for (item in items) TableCell(value(item), columnWidth, strong, alignEnd = true)
    }
}

@Composable
private fun TableCell(text: String, width: Dp, strong: Boolean, alignEnd: Boolean = false) {
    Box(
        modifier = Modifier.width(width).heightIn(min = 48.dp).padding(10.dp),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun ComparisonPreview() {
    val first = Summary(
        carId = "first",
        listingPrice = 1_200_000,
        totalExpenses = BigInteger.valueOf(140_000),
        totalBenefits = BigInteger.ZERO,
        acquisitionCost = BigInteger.valueOf(1_340_000),
        adjustedCost = BigInteger.valueOf(1_340_000),
        assessmentStatus = AssessmentStatus.INCOMPLETE,
        unknownExpenses = 1,
        unknownBenefits = 0
    )
    val second = first.copy(
        carId = "second",
        listingPrice = 1_270_000,
        totalExpenses = BigInteger.valueOf(30_000),
        totalBenefits = BigInteger.valueOf(50_000),
        acquisitionCost = BigInteger.valueOf(1_300_000),
        adjustedCost = BigInteger.valueOf(1_250_000),
        assessmentStatus = AssessmentStatus.COMPLETE,
        unknownExpenses = 0
    )
    DromProfitTheme {
        ComparisonScreen(
            ScreenState.Content(
                ComparisonContent(
                    Comparison(
                        items = listOf(ComparisonItem(first, BigInteger.valueOf(90_000)), ComparisonItem(second, BigInteger.ZERO)),
                        warnings = listOf(ComparisonWarning("incomplete", "Часть оценок ещё не завершена", null)),
                        provisional = true
                    ),
                    emptyMap()
                )
            ),
            isDemo = true,
            onBack = {},
            onRetry = {}
        )
    }
}
