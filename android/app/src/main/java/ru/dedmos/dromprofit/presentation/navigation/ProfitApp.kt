package ru.dedmos.dromprofit.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.dedmos.dromprofit.presentation.components.LocalProfitNavigation
import ru.dedmos.dromprofit.presentation.components.PrimaryAction
import ru.dedmos.dromprofit.presentation.components.ProfitDestination
import ru.dedmos.dromprofit.presentation.components.ProfitNavigation
import ru.dedmos.dromprofit.presentation.screens.add.AddCarRoute
import ru.dedmos.dromprofit.presentation.screens.comparison.ComparisonRoute
import ru.dedmos.dromprofit.presentation.screens.detail.CarDetailRoute
import ru.dedmos.dromprofit.presentation.screens.selection.SelectionRoute
import ru.dedmos.dromprofit.presentation.viewmodels.ScreenState
import ru.dedmos.dromprofit.presentation.viewmodels.SelectionViewModel

private const val HOME = "selection"
private const val ADD = "add?url={url}&manual={manual}"
private const val DETAIL = "car/{carId}?section={section}"
private const val COMPARISON = "comparison"

@Composable
internal fun ProfitApp(selection: SelectionViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val requiresNewSession by selection.requiresNewSession.collectAsStateWithLifecycle(initialValue = false)
    val route = entry?.destination?.route
    val active = when (route) {
        HOME -> ProfitDestination.CARS
        ADD -> ProfitDestination.ADD
        COMPARISON -> ProfitDestination.COMPARE
        else -> null
    }

    val showCars: () -> Unit = { nav.popBackStack(HOME, inclusive = false) }
    val showComparison: () -> Unit = { nav.navigate(COMPARISON) { launchSingleTop = true } }
    val openAdd: (String?, Boolean) -> Unit = { url, manual ->
        val path = if (url == null) "add?manual=$manual" else "add?url=${Uri.encode(url)}&manual=$manual"
        nav.navigate(path) { launchSingleTop = true }
    }
    val openCar: (String, String?) -> Unit = { carId, section ->
        val path = "car/${Uri.encode(carId)}"
        nav.navigate(if (section == null) path else "$path?section=${Uri.encode(section)}")
    }
    val navigation = ProfitNavigation(active) { target ->
        if (target != active) {
            when (target) {
                ProfitDestination.CARS -> showCars()
                ProfitDestination.ADD -> openAdd(null, false)
                ProfitDestination.COMPARE -> showComparison()
            }
        }
    }

    CompositionLocalProvider(LocalProfitNavigation provides navigation) {
        NavHost(navController = nav, startDestination = HOME) {
            composable(HOME) {
                SelectionRoute(
                    viewModel = selection,
                    onAddFromLink = { openAdd(it, false) },
                    onManual = { openAdd(null, true) },
                    onOpenCar = { openCar(it, null) },
                    onCompare = showComparison,
                )
            }
            composable(
                ADD,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("manual") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { addEntry ->
                AddCarRoute(
                    onBack = { nav.popBackStack() },
                    onSaved = { showCars() },
                    initialUrl = addEntry.arguments?.getString("url"),
                    startManual = addEntry.arguments?.getBoolean("manual") == true,
                    onHome = showCars,
                )
            }
            composable(
                DETAIL,
                arguments = listOf(
                    navArgument("carId") { type = NavType.StringType },
                    navArgument("section") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { detailEntry ->
                CarDetailRoute(
                    carId = requireNotNull(detailEntry.arguments?.getString("carId")),
                    onBack = showCars,
                    initialSection = detailEntry.arguments?.getString("section"),
                )
            }
            composable(COMPARISON) {
                ComparisonRoute(
                    selection = selection.selectedIds,
                    onShowCars = showCars,
                    onExclude = selection::exclude,
                    onPrune = selection::pruneSelection,
                    onOpenCar = openCar,
                )
            }
        }
    }

    if (requiresNewSession) {
        val state by selection.state.collectAsStateWithLifecycle()
        val pending = state == ScreenState.Loading
        AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Сессия больше недоступна") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Сервер не принял сохранённую сессию. Новая сессия создаст отдельную выборку.")
                    (state as? ScreenState.Error)?.restartError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                PrimaryAction(
                    label = if (pending) "Создаём сессию…" else "Начать новую сессию",
                    enabled = !pending,
                    onClick = {
                        selection.restart()
                        showCars()
                    },
                )
            },
        )
    }
}
