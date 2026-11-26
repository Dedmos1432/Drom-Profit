package ru.dedmos.dromprofit.presentation.screens.selection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.NumberFormat
import java.util.Locale
import ru.dedmos.dromprofit.domain.AssessmentStatus
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarSource
import ru.dedmos.dromprofit.domain.Summary
import ru.dedmos.dromprofit.presentation.components.AmountLine
import ru.dedmos.dromprofit.presentation.components.BadgeTone
import ru.dedmos.dromprofit.presentation.components.ContrastAction
import ru.dedmos.dromprofit.presentation.components.DangerAction
import ru.dedmos.dromprofit.presentation.components.DeleteCarConfirmation
import ru.dedmos.dromprofit.presentation.components.EmptyState
import ru.dedmos.dromprofit.presentation.components.ErrorState
import ru.dedmos.dromprofit.presentation.components.LoadingState
import ru.dedmos.dromprofit.presentation.components.OutlineAction
import ru.dedmos.dromprofit.presentation.components.PageHeading
import ru.dedmos.dromprofit.presentation.components.PriceText
import ru.dedmos.dromprofit.presentation.components.PrimaryAction
import ru.dedmos.dromprofit.presentation.components.ProfitBadge
import ru.dedmos.dromprofit.presentation.components.ProfitScaffold
import ru.dedmos.dromprofit.presentation.components.SecondaryAction
import ru.dedmos.dromprofit.presentation.components.SessionExpiredState
import ru.dedmos.dromprofit.presentation.components.VehiclePhoto
import ru.dedmos.dromprofit.presentation.components.formatMileage
import ru.dedmos.dromprofit.presentation.components.vehicleName
import ru.dedmos.dromprofit.presentation.components.vehicleSourceLabel
import ru.dedmos.dromprofit.presentation.screens.add.ListingLinkField
import ru.dedmos.dromprofit.presentation.ui.theme.DromProfitTheme
import ru.dedmos.dromprofit.presentation.ui.theme.LinkBlue
import ru.dedmos.dromprofit.presentation.ui.theme.ProfitTokens
import ru.dedmos.dromprofit.presentation.viewmodels.ScreenState
import ru.dedmos.dromprofit.presentation.viewmodels.SelectionViewModel
import ru.dedmos.dromprofit.presentation.viewmodels.listingLinkError

@Composable
internal fun SelectionRoute(
    viewModel: SelectionViewModel,
    onAddFromLink: (String) -> Unit,
    onManual: () -> Unit,
    onOpenCar: (String) -> Unit,
    onCompare: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val deletion by viewModel.deleting.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    SelectionScreen(
        state = state,
        summaries = summaries,
        isDemo = viewModel.isDemo,
        selectedIds = selectedIds,
        onAddFromLink = onAddFromLink,
        onManual = onManual,
        onOpenCar = onOpenCar,
        onCompare = onCompare,
        onSelect = viewModel::toggleSelected,
        onSelectAll = viewModel::selectAll,
        onClear = viewModel::clearSelection,
        onRetry = viewModel::refresh,
        onRestart = viewModel::restart,
        onDelete = viewModel::askToDelete,
        onLoadSummary = viewModel::loadSummary,
        onRetrySummary = viewModel::retrySummary,
    )
    deletion?.let { candidate ->
        DeleteCarConfirmation(
            name = vehicleName(candidate.car),
            pending = candidate.pending,
            error = candidate.error,
            onClose = viewModel::cancelDelete,
            onDelete = viewModel::confirmDelete,
        )
    }
}

@Composable
private fun SelectionScreen(
    state: ScreenState<List<Car>>,
    summaries: Map<String, ScreenState<Summary>>,
    isDemo: Boolean,
    selectedIds: List<String>,
    onAddFromLink: (String) -> Unit,
    onManual: () -> Unit,
    onOpenCar: (String) -> Unit,
    onCompare: () -> Unit,
    onSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onRestart: () -> Unit,
    onDelete: (Car) -> Unit,
    onLoadSummary: (String) -> Unit,
    onRetrySummary: (String) -> Unit,
) {
    ProfitScaffold(title = "Моя выборка") { insets ->
        Box(
            Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = ProfitTokens.screenPadding, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    PageHeading("Моя выборка", "Добавьте автомобили, оцените расходы и преимущества, затем сравните варианты.")
                }
                if (state is ScreenState.Error && state.sessionExpired) {
                    item { SessionExpiredState(onRestart, error = state.restartError) }
                } else {
                    item { QuickAdd(onAddFromLink, onManual) }
                    item {
                        val cars = (state as? ScreenState.Content)?.data
                        SavedCarsHeading(
                            count = cars?.size,
                            selectedCount = cars?.count { it.id in selectedIds } ?: 0,
                            demo = isDemo && cars?.any { it.source == CarSource.DEMO } == true,
                            onSelectAll = onSelectAll,
                            onClear = onClear,
                            onCompare = onCompare,
                        )
                    }
                    when (state) {
                        ScreenState.Loading -> item { LoadingState("Загружаем выборку…") }
                        is ScreenState.Error -> item { ErrorState(state.message, onRetry = onRetry) }
                        is ScreenState.Content -> {
                            if (state.data.isEmpty()) {
                                item {
                                    EmptyState(
                                        "Пока нет автомобилей",
                                        "Вставьте ссылку на объявление выше или введите данные первого автомобиля вручную.",
                                    )
                                }
                            }
                            items(state.data, key = Car::id) { car ->
                                val summary = summaries[car.id]
                                LaunchedEffect(car.id, summary == null) {
                                    if (summary == null) onLoadSummary(car.id)
                                }
                                VehicleRow(
                                    car,
                                    summary,
                                    car.id in selectedIds,
                                    onOpen = { onOpenCar(car.id) },
                                    onSelect = { onSelect(car.id) },
                                    onDelete = { onDelete(car) },
                                    onRetrySummary = { onRetrySummary(car.id) },
                                )
                                HorizontalDivider(Modifier.padding(top = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAdd(onAdd: (String) -> Unit, onManual: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val submit = {
        error = listingLinkError(url)
        if (error == null) {
            focus.clearFocus()
            onAdd(url.trim())
        }
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ListingLinkField(
                value = url,
                onValueChange = {
                    url = it
                    error = null
                },
                error = error,
                onSubmit = submit,
            )
            ContrastAction("Добавить по ссылке", onClick = submit)
            SecondaryAction(label = "Добавить вручную", onClick = onManual)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedCarsHeading(
    count: Int?,
    selectedCount: Int,
    demo: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onCompare: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Сохранённые автомобили", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (count != null) Text(savedCount(count), color = muted, style = MaterialTheme.typography.bodySmall)
            if (demo) ProfitBadge("Демонстрационная выборка")
        }
        if (count != null && count > 0) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onSelectAll, enabled = selectedCount < count) { Text("Выбрать все") }
                Text(
                    "Выбрано $selectedCount из $count",
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineAction("Сравнить автомобили", enabled = selectedCount >= 2, onClick = onCompare)
                if (selectedCount > 0) TextButton(onClick = onClear) { Text("Очистить выбор") }
            }
            if (selectedCount < 2) {
                Text("Выберите хотя бы два автомобиля для сравнения.", color = muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleRow(
    car: Car,
    summary: ScreenState<Summary>?,
    selected: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onRetrySummary: () -> Unit,
) {
    val estimate = (summary as? ScreenState.Content)?.data
    val complete = estimate?.assessmentStatus == AssessmentStatus.COMPLETE
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val label = vehicleName(car)
    val facts = listOfNotNull(
        car.engineType,
        car.engineVolume?.let { "${NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).format(it)} л" },
        car.enginePower?.let { "$it л. с." },
        car.transmission,
        car.driveType,
        car.mileage?.let(::formatMileage),
    ).joinToString(" · ")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VehiclePhoto(car.imageUrl, Modifier.width(116.dp).height(87.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    label,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpen),
                    color = LinkBlue,
                    style = MaterialTheme.typography.titleLarge,
                )
                car.trim?.let { Text(it) }
                if (facts.isNotEmpty()) Text(facts)
                car.city?.let { Text(it, color = muted) }
                ProfitBadge(vehicleSourceLabel(car), if (car.source == CarSource.DEMO) BadgeTone.INFO else BadgeTone.NEUTRAL)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Цена объявления", color = muted, style = MaterialTheme.typography.bodySmall)
            PriceText(car.price)
            car.marketPrice?.let { AmountLine(label = "Рыночная оценка Дрома", value = it) }
            when {
                estimate != null -> {
                    ProfitBadge(
                        if (complete) "Все данные рассмотрены" else "Предварительная оценка",
                        if (complete) BadgeTone.SUCCESS else BadgeTone.WARNING,
                    )
                    AmountLine(label = "Затраты на приобретение", value = estimate.acquisitionCost, strong = true)
                    AmountLine(label = "Скорректированная стоимость", value = estimate.adjustedCost, strong = true)
                }
                summary is ScreenState.Error -> {
                    Text("Оценка недоступна", style = MaterialTheme.typography.titleMedium)
                    Text(summary.message, color = muted)
                    TextButton(onClick = onRetrySummary) { Text("Повторить") }
                }
                else -> Text("Обновляем оценку…", color = muted)
            }
        }
        val actionWidth = Modifier.widthIn(max = 220.dp)
        if (estimate != null && !complete) {
            PrimaryAction(modifier = actionWidth, label = "Заполнить данные", onClick = onOpen)
        } else {
            SecondaryAction(modifier = actionWidth, label = if (complete) "Уточнить данные" else "Открыть карточку", onClick = onOpen)
        }
        Text("Расходы, преимущества и заметки", color = muted, style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.align(Alignment.CenterVertically)
                    .heightIn(min = 48.dp)
                    .toggleable(selected, role = Role.Checkbox, onValueChange = { onSelect() }),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(selected, onCheckedChange = null)
                Text("К сравнению")
            }
            DangerAction("Удалить", onClick = onDelete)
        }
        OriginalListing(car)
    }
}

@Composable
private fun OriginalListing(car: Car) {
    val url = car.sourceUrl?.takeIf { address ->
        val uri = address.toUri()
        car.source == CarSource.DROM && uri.scheme == "https" &&
            uri.host?.let { it == "drom.ru" || it.endsWith(".drom.ru") } == true
    } ?: return
    val handler = LocalUriHandler.current
    var failed by rememberSaveable(car.id) { mutableStateOf(false) }
    Text(
        "Оригинальное объявление",
        modifier = Modifier.clickable { failed = runCatching { handler.openUri(url) }.isFailure }.padding(vertical = 8.dp),
        color = LinkBlue,
        textDecoration = TextDecoration.Underline,
    )
    if (failed) Text("Не удалось открыть объявление.", color = MaterialTheme.colorScheme.error)
}

private fun savedCount(count: Int): String {
    val unit = when {
        count % 100 in 11..14 -> "автомобилей"
        count % 10 == 1 -> "автомобиль"
        count % 10 in 2..4 -> "автомобиля"
        else -> "автомобилей"
    }
    return "$count $unit"
}

@Preview(name = "Моя выборка", widthDp = 390, heightDp = 844)
@Composable
private fun SelectionPreview() {
    val car = Car(
        id = "preview-1", sourceUrl = null, brand = "Toyota", model = "Camry", year = 2015,
        price = 1_200_000, mileage = 145_000, engineVolume = 2.5, transmission = "Автомат",
        imageUrl = "/demo/vehicles/camry-xv50-silver.jpg", trim = "Элеганс", engineType = "Бензин",
        enginePower = 181, driveType = "Передний", city = "Владивосток", source = CarSource.DEMO,
    )
    val summary = Summary(
        car.id, car.price, 33_000.toBigInteger(), 25_000.toBigInteger(), 1_233_000.toBigInteger(),
        1_208_000.toBigInteger(), AssessmentStatus.COMPLETE, 0, 0,
    )
    DromProfitTheme {
        SelectionScreen(
            state = ScreenState.Content(listOf(car)),
            summaries = mapOf(car.id to ScreenState.Content(summary)),
            isDemo = true,
            selectedIds = listOf(car.id),
            onAddFromLink = {}, onManual = {}, onOpenCar = {}, onCompare = {}, onSelect = {},
            onSelectAll = {}, onClear = {}, onRetry = {}, onRestart = {}, onDelete = {},
            onLoadSummary = {}, onRetrySummary = {},
        )
    }
}
