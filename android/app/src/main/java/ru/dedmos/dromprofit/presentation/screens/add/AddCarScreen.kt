package ru.dedmos.dromprofit.presentation.screens.add

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.dedmos.dromprofit.domain.CarInput
import ru.dedmos.dromprofit.domain.CarSource
import ru.dedmos.dromprofit.domain.ListingData
import ru.dedmos.dromprofit.domain.toListingData
import ru.dedmos.dromprofit.presentation.components.ErrorState
import ru.dedmos.dromprofit.presentation.components.LoadingState
import ru.dedmos.dromprofit.presentation.components.Panel
import ru.dedmos.dromprofit.presentation.components.PrimaryAction
import ru.dedmos.dromprofit.presentation.components.ProfitScaffold
import ru.dedmos.dromprofit.presentation.components.StatusLabel
import ru.dedmos.dromprofit.presentation.components.VehicleImage
import ru.dedmos.dromprofit.presentation.ui.theme.DromProfitTheme
import ru.dedmos.dromprofit.presentation.ui.theme.ProfitTokens
import ru.dedmos.dromprofit.presentation.viewmodels.CarFormUiState
import ru.dedmos.dromprofit.presentation.viewmodels.CarFormViewModel
import ru.dedmos.dromprofit.presentation.viewmodels.ImportPreview

private const val DEMO_LINK = "https://auto.drom.ru/demo/1.html"

@Composable
fun AddCarRoute(
    carId: String? = null,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: CarFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(carId) {
        if (carId != null) viewModel.load(carId)
    }
    LaunchedEffect(state.savedId) {
        state.savedId?.let { id ->
            viewModel.consumeSaved(id)
            onSaved(id)
        }
    }
    BackHandler(enabled = state.saving) { }

    ProfitScaffold(
        title = if (carId == null) "Добавить автомобиль" else "Редактировать автомобиль",
        onBack = onBack.takeUnless { state.saving },
    ) { insets ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(insets)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ProfitTokens.screenPadding, vertical = ProfitTokens.contentGap)

        if (carId == null) {
            AddCarContent(
                state = state,
                isDemo = viewModel.isDemo,
                modifier = modifier,
                onImport = viewModel::importListing,
                onLinkChange = viewModel::clearLinkError,
                onDraftChange = viewModel::clearSaveError,
                onSave = viewModel::save,
            )
        } else {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(ProfitTokens.contentGap)) {
                val original = state.original
                val error = state.loadError
                when {
                    original?.id == carId -> {
                        if (original.expensesReviewed || original.benefitsReviewed) {
                            Text(
                                "При изменении данных подтверждения расходов и преимуществ сбросятся.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        key(carId) {
                            VehicleForm(
                                initial = original.toListingData(),
                                submitLabel = "Сохранить изменения",
                                enabled = !state.saving && state.savedId == null,
                                saving = state.saving,
                                saveError = state.saveError,
                                onChange = viewModel::clearSaveError,
                                onSubmit = viewModel::save,
                            )
                        }
                    }
                    error != null -> ErrorState(error, onRetry = { viewModel.load(carId) })
                    else -> LoadingState("Загружаем автомобиль…")
                }
            }
        }
    }
}

@Composable
private fun AddCarContent(
    state: CarFormUiState,
    isDemo: Boolean,
    onImport: (String) -> Unit,
    onLinkChange: () -> Unit,
    onDraftChange: () -> Unit,
    onSave: (CarInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    var manual by rememberSaveable { mutableStateOf(false) }
    var link by rememberSaveable { mutableStateOf(state.preview?.requestedUrl.orEmpty()) }
    val drafts = rememberSaveableStateHolder()
    val enabled = !state.importing && !state.saving && state.savedId == null

    Column(modifier, verticalArrangement = Arrangement.spacedBy(ProfitTokens.contentGap)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !manual,
                onClick = {
                    if (manual) onDraftChange()
                    manual = false
                },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                enabled = enabled,
                label = { Text("По ссылке") },
            )
            SegmentedButton(
                selected = manual,
                onClick = {
                    if (!manual) onDraftChange()
                    manual = true
                },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                enabled = enabled,
                label = { Text("Вручную") },
            )
        }

        if (manual) {
            drafts.SaveableStateProvider("manual") {
                VehicleForm(
                    submitLabel = "Сохранить автомобиль",
                    enabled = enabled,
                    saving = state.saving,
                    saveError = state.saveError,
                    onChange = onDraftChange,
                    onSubmit = onSave,
                )
            }
        } else {
            ImportPanel(
                link = link,
                onLinkChange = {
                    link = it
                    onLinkChange()
                },
                isDemo = isDemo,
                enabled = enabled,
                error = state.linkError,
                onImport = { onImport(link) },
            )
            if (state.importing) LoadingState("Получаем данные объявления…")
            state.importError?.let {
                ErrorState(it)
                TextButton(onClick = {
                    onDraftChange()
                    manual = true
                }, enabled = enabled) { Text("Ввести данные вручную") }
            }

            state.preview?.takeIf { it.requestedUrl == link.trim() }?.let { preview ->
                val listing = preview.listing
                Text(
                    "Предпросмотр автомобиля",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                if (listing.source == CarSource.DEMO) StatusLabel("Демо-источник")
                Text(
                    "Проверьте данные и при необходимости исправьте их перед сохранением.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (listing.source != CarSource.DEMO && listing.imageUrl != null) {
                    VehicleImage(listing.imageUrl, Modifier.fillMaxWidth().height(156.dp))
                }
                drafts.SaveableStateProvider("import") {
                    key(preview.version) {
                        VehicleForm(
                            initial = listing,
                            submitLabel = "Сохранить автомобиль",
                            enabled = enabled,
                            saving = state.saving,
                            saveError = state.saveError,
                            onChange = onDraftChange,
                            onSubmit = onSave,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportPanel(
    link: String,
    onLinkChange: (String) -> Unit,
    isDemo: Boolean,
    enabled: Boolean,
    error: String?,
    onImport: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val submit = {
        if (enabled) {
            focus.clearFocus()
            onImport()
        }
    }
    Panel {
        OutlinedTextField(
            value = link,
            onValueChange = onLinkChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = { Text("Ссылка на объявление Дрома") },
            placeholder = { Text("https://auto.drom.ru/…") },
            supportingText = if (error != null) ({ Text(error) }) else null,
            isError = error != null,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        if (isDemo) {
            TextButton(enabled = enabled, onClick = { onLinkChange(DEMO_LINK) }) {
                Text("Вставить демоссылку")
            }
        }
        PrimaryAction(label = "Получить данные", enabled = enabled, onClick = submit)
    }
}

@Preview(name = "Импорт объявления", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ImportCarPreview() = AddCarScreenPreview(CarFormUiState())

@Preview(name = "Подтверждение демоимпорта", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ConfirmImportPreview() {
    val listing = ListingData(
        source = CarSource.DEMO,
        sourceUrl = DEMO_LINK,
        brand = "Toyota",
        model = "Camry",
        price = 1_200_000,
        year = 2015,
        city = "Владивосток",
    )
    AddCarScreenPreview(CarFormUiState(preview = ImportPreview(DEMO_LINK, listing, version = 1)))
}

@Composable
private fun AddCarScreenPreview(state: CarFormUiState) {
    DromProfitTheme {
        ProfitScaffold(title = "Добавить автомобиль", onBack = {}) { insets ->
            AddCarContent(
                state = state,
                isDemo = true,
                modifier = Modifier.padding(insets).verticalScroll(rememberScrollState())
                    .padding(ProfitTokens.screenPadding),
                onImport = {},
                onLinkChange = {},
                onDraftChange = {},
                onSave = {},
            )
        }
    }
}

@Preview(name = "Ручное добавление", showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ManualCarPreview() {
    DromProfitTheme {
        ProfitScaffold(title = "Добавить автомобиль", onBack = {}) { insets ->
            Column(
                Modifier.padding(insets).verticalScroll(rememberScrollState()).padding(ProfitTokens.screenPadding),
            ) {
                VehicleForm(
                    submitLabel = "Сохранить автомобиль",
                    enabled = true,
                    saving = false,
                    saveError = null,
                    onChange = {},
                    onSubmit = {},
                )
            }
        }
    }
}
