package ru.dedmos.dromprofit.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.dedmos.dromprofit.presentation.screens.add.AddCarRoute
import ru.dedmos.dromprofit.presentation.screens.comparison.ComparisonRoute
import ru.dedmos.dromprofit.presentation.screens.detail.CarDetailRoute
import ru.dedmos.dromprofit.presentation.screens.selection.SelectionRoute
import ru.dedmos.dromprofit.presentation.viewmodels.SelectionViewModel

@Composable
internal fun ProfitApp(selection: SelectionViewModel = hiltViewModel()) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    val openCar: (String) -> Unit = { id -> nav.navigate("car/${Uri.encode(id)}") }

    NavHost(navController = nav, startDestination = "selection") {
        composable("selection") {
            SelectionRoute(
                viewModel = selection,
                choosing = false,
                onShowSelection = {},
                onChoose = { nav.navigate("choose") { launchSingleTop = true } },
                onAdd = { nav.navigate("add") },
                onOpenCar = openCar,
                onCompare = {}
            )
        }
        composable("choose") {
            SelectionRoute(
                viewModel = selection,
                choosing = true,
                onShowSelection = { nav.popBackStack("selection", inclusive = false) },
                onChoose = {},
                onAdd = { nav.navigate("add") },
                onOpenCar = openCar,
                onCompare = { nav.navigate("comparison") { launchSingleTop = true } }
            )
        }
        composable("comparison") {
            ComparisonRoute(selection.selectedIds, onBack = back)
        }
        composable("add") {
            AddCarRoute(onBack = back, onSaved = { id ->
                nav.popBackStack()
                openCar(id)
            })
        }
        composable("car/{carId}", arguments = listOf(navArgument("carId") { type = NavType.StringType })) { entry ->
            val carId = requireNotNull(entry.arguments?.getString("carId"))
            CarDetailRoute(
                carId = carId,
                onBack = back,
                onEditCar = { id -> nav.navigate("edit/${Uri.encode(id)}") },
                onDeleted = back
            )
        }
        composable("edit/{carId}", arguments = listOf(navArgument("carId") { type = NavType.StringType })) { entry ->
            AddCarRoute(
                carId = requireNotNull(entry.arguments?.getString("carId")),
                onBack = back,
                onSaved = { nav.popBackStack() }
            )
        }
    }
}
