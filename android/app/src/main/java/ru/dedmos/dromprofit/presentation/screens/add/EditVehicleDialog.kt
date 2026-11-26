package ru.dedmos.dromprofit.presentation.screens.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.toListingData
import ru.dedmos.dromprofit.presentation.viewmodels.CarFormViewModel

@Composable
internal fun EditVehicleDialog(
    car: Car,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val viewModel: CarFormViewModel = hiltViewModel(key = "vehicle-edit-${car.id}")
    val state by viewModel.state.collectAsStateWithLifecycle()
    val latestOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(car.id) { viewModel.prepareEdit(car) }
    LaunchedEffect(state.savedId) {
        if (state.savedId != null) {
            viewModel.finishEdit()
            latestOnSaved()
        }
    }
    val dismiss: () -> Unit = {
        if (!state.saving) {
            viewModel.finishEdit()
            onClose()
        }
    }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            dismissOnBackPress = !state.saving,
            dismissOnClickOutside = !state.saving,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        "Редактировать характеристики",
                        modifier = Modifier.weight(1f).padding(top = 8.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(enabled = !state.saving, onClick = dismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
                VehicleForm(
                    initial = car.toListingData(),
                    submitLabel = "Сохранить изменения",
                    enabled = state.original?.id == car.id && !state.saving && state.savedId == null,
                    saving = state.saving,
                    saveError = state.saveError,
                    onChange = viewModel::clearSaveError,
                    onSubmit = viewModel::save,
                    onCancel = dismiss,
                )
            }
        }
    }
}
