package ru.dedmos.dromprofit.presentation.screens.selection

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarSource
import ru.dedmos.dromprofit.presentation.components.EmptyState
import ru.dedmos.dromprofit.presentation.components.ErrorState
import ru.dedmos.dromprofit.presentation.components.formatRubles
import ru.dedmos.dromprofit.presentation.components.LoadingState
import ru.dedmos.dromprofit.presentation.components.Panel
import ru.dedmos.dromprofit.presentation.components.PrimaryAction
import ru.dedmos.dromprofit.presentation.components.ProfitScaffold
import ru.dedmos.dromprofit.presentation.components.SessionExpiredState
import ru.dedmos.dromprofit.presentation.components.StatusLabel
import ru.dedmos.dromprofit.presentation.components.VehicleImage
import ru.dedmos.dromprofit.presentation.ui.theme.DromProfitTheme
import ru.dedmos.dromprofit.presentation.ui.theme.ProfitTokens
import ru.dedmos.dromprofit.presentation.viewmodels.ScreenState
import ru.dedmos.dromprofit.presentation.viewmodels.SelectionViewModel

@Composable
internal fun SelectionRoute(
    viewModel: SelectionViewModel,
    choosing: Boolean,
    onShowSelection: () -> Unit,
    onChoose: () -> Unit,
    onAdd: () -> Unit,
    onOpenCar: (String) -> Unit,
    onCompare: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, choosing) { viewModel.refresh() }
    SelectionScreen(
        state = state,
        isDemo = viewModel.isDemo,
        choosing = choosing,
        selectedIds = selectedIds,
        onShowSelection = onShowSelection,
        onChoose = onChoose,
        onAdd = onAdd,
        onCarAction = if (choosing) viewModel::toggleSelected else onOpenCar,
        onCompare = onCompare,
        onRetry = viewModel::refresh,
        onRestart = viewModel::restart
    )
}

@Composable
private fun SelectionScreen(
    state: ScreenState<List<Car>>,
    isDemo: Boolean,
    choosing: Boolean,
    selectedIds: List<String>,
    onShowSelection: () -> Unit,
    onChoose: () -> Unit,
    onAdd: () -> Unit,
    onCarAction: (String) -> Unit,
    onCompare: () -> Unit,
    onRetry: () -> Unit,
    onRestart: () -> Unit
) {
    ProfitScaffold(
        title = if (choosing) "Выбор для сравнения" else "Drom Profit",
        bottomBar = { ProfitNavigation(choosing, onShowSelection, onChoose) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(),
                contentPadding = PaddingValues(ProfitTokens.screenPadding),
                verticalArrangement = Arrangement.spacedBy(ProfitTokens.contentGap)
            ) {
                if (isDemo) item { StatusLabel("Демонстрационный режим") }
                when (state) {
                    ScreenState.Loading -> item { LoadingState("Загружаем выборку…") }
                    is ScreenState.Error -> item {
                        if (state.sessionExpired) {
                            SessionExpiredState(onRestart, error = state.restartError)
                        } else {
                            ErrorState(state.message, onRetry)
                        }
                    }
                    is ScreenState.Content -> {
                        if (choosing && state.data.size >= 2) {
                            item {
                                Panel {
                                    Text("Выберите два или больше автомобилей.")
                                    PrimaryAction(
                                        label = "Сравнить · ${selectedIds.size}",
                                        enabled = selectedIds.size >= 2,
                                        onClick = onCompare
                                    )
                                }
                            }
                        } else {
                            item { PrimaryAction(label = "Добавить автомобиль", onClick = onAdd) }
                        }

                        if (state.data.isEmpty()) item {
                            EmptyState(
                                if (choosing) "Пока нечего сравнивать" else "Выборка пуста",
                                if (choosing) "Добавьте хотя бы два автомобиля."
                                else "Вставьте ссылку на объявление или введите данные вручную."
                            )
                        }
                        if (choosing && state.data.size == 1) item {
                            Text(
                                "Добавьте ещё один автомобиль, чтобы сравнить предложения.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!choosing && state.data.isNotEmpty()) item {
                            Text("Моя выборка", style = MaterialTheme.typography.titleLarge)
                        }
                        items(state.data, key = { it.id }) { car ->
                            VehicleCard(
                                car = car,
                                selected = if (choosing) car.id in selectedIds else null,
                                onClick = { onCarAction(car.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfitNavigation(choosing: Boolean, onShowSelection: () -> Unit, onChoose: () -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = !choosing,
            onClick = onShowSelection,
            icon = { Icon(Icons.Outlined.DirectionsCar, contentDescription = null) },
            label = { Text("Выборка") }
        )
        NavigationBarItem(
            selected = choosing,
            onClick = onChoose,
            icon = { Icon(Icons.AutoMirrored.Outlined.CompareArrows, contentDescription = null) },
            label = { Text("Сравнение") }
        )
    }
}

@Composable
internal fun VehicleCard(car: Car, selected: Boolean? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val action = if (selected == null) {
        Modifier.clickable(onClickLabel = "Открыть карточку", onClick = onClick)
    } else {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clip(shape).then(action),
        shape = shape,
        border = BorderStroke(1.dp, if (selected == true) colors.primary else colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected == true) colors.secondaryContainer else colors.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(ProfitTokens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ProfitTokens.contentGap)
        ) {
            VehicleImage(car.imageUrl, Modifier.size(64.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${car.brand} ${car.model}", style = MaterialTheme.typography.titleMedium)
                val description = listOfNotNull(
                    car.year?.toString(),
                    car.mileage?.let { "$it км" },
                    car.city
                ).joinToString(" · ")
                if (description.isNotEmpty()) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Text(formatRubles(car.price), style = MaterialTheme.typography.titleMedium)
                if (car.source == CarSource.DEMO) {
                    Text("Демо-объявление", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (selected == null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            } else {
                Checkbox(checked = selected, onCheckedChange = null)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SelectionEmptyPreview() {
    DromProfitTheme {
        SelectionScreen(ScreenState.Content(emptyList()), true, false, emptyList(), {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun VehicleCardPreview() {
    DromProfitTheme {
        VehicleCard(
            car = Car(
                id = "preview-1",
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
                city = "Владивосток"
            ),
            selected = true,
            onClick = {}
        )
    }
}
