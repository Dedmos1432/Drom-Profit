package ru.dedmos.dromprofit.presentation.screens.add

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import ru.dedmos.dromprofit.presentation.components.PageHeading
import ru.dedmos.dromprofit.presentation.components.PrimaryAction
import ru.dedmos.dromprofit.presentation.components.ProfitBreadcrumb
import ru.dedmos.dromprofit.presentation.components.ProfitScaffold
import ru.dedmos.dromprofit.presentation.components.ProfitScreenColumn
import ru.dedmos.dromprofit.presentation.components.SecondaryAction
import ru.dedmos.dromprofit.presentation.components.StatusLabel
import ru.dedmos.dromprofit.presentation.components.VehicleGallery
import ru.dedmos.dromprofit.presentation.components.isDemoModelPhoto
import ru.dedmos.dromprofit.presentation.ui.theme.DromProfitTheme
import ru.dedmos.dromprofit.presentation.viewmodels.CarFormUiState
import ru.dedmos.dromprofit.presentation.viewmodels.CarFormViewModel
import ru.dedmos.dromprofit.presentation.viewmodels.ImportPreview

private const val DEMO_LINK = "https://auto.drom.ru/demo/1.html"

@Composable
fun AddCarRoute(
    carId: String? = null,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    initialUrl: String? = null,
    startManual: Boolean = false,
    onHome: () -> Unit = onBack,
    viewModel: CarFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(carId, initialUrl, startManual) {
        if (carId != null) {
            viewModel.load(carId)
        } else if (!startManual && initialUrl != null) {
            viewModel.requestInitialImport(initialUrl)
        }
    }
    LaunchedEffect(state.savedId) {
        state.savedId?.let { id ->
            viewModel.consumeSaved(id)
            onSaved(id)
        }
    }

    BackHandler(enabled = state.saving) {}
    val title = if (carId == null) "Добавить автомобиль" else "Редактировать характеристики"
    ProfitScaffold(title = title, onBack = onBack.takeUnless { state.saving }) { insets ->
        ProfitScreenColumn(insets, Modifier.imePadding()) {
            ProfitBreadcrumb(current = title, onHome = onHome)
            PageHeading(title)
            if (carId == null) {
                AddCarContent(
                    state = state,
                    isDemo = viewModel.isDemo,
                    initialUrl = initialUrl,
                    startManual = startManual,
                    onImport = viewModel::importListing,
                    onLinkChange = viewModel::clearLinkError,
                    onDraftChange = viewModel::clearSaveError,
                    onSave = viewModel::save,
                )
            } else {
                val original = state.original?.takeIf { it.id == carId }
                val error = state.loadError
                when {
                    original != null -> VehicleForm(
                        initial = original.toListingData(),
                        submitLabel = "Сохранить изменения",
                        enabled = !state.saving && state.savedId == null,
                        saving = state.saving,
                        saveError = state.saveError,
                        onChange = viewModel::clearSaveError,
                        onSubmit = viewModel::save,
                        onCancel = onBack,
                    )
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
    initialUrl: String?,
    startManual: Boolean,
    onImport: (String) -> Unit,
    onLinkChange: () -> Unit,
    onDraftChange: () -> Unit,
    onSave: (CarInput) -> Unit,
) {
    var manual by rememberSaveable(startManual) { mutableStateOf(startManual) }
    var link by rememberSaveable(initialUrl) { mutableStateOf(initialUrl.orEmpty()) }
    val drafts = rememberSaveableStateHolder()
    val canSave = !state.saving && state.savedId == null
    val setMethod: (Boolean) -> Unit = { next ->
        manual = next
        onDraftChange()
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        AddMethodPicker(manual, !state.saving, setMethod)
        if (manual) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormSectionHeading("Ручное добавление")
                drafts.SaveableStateProvider("manual") {
                    VehicleForm(
                        submitLabel = "Сохранить автомобиль",
                        enabled = canSave,
                        saving = state.saving,
                        saveError = state.saveError,
                        onChange = onDraftChange,
                        onSubmit = onSave,
                    )
                }
            }
        } else {
            ImportPanel(
                link = link,
                onLinkChange = {
                    link = it
                    onLinkChange()
                },
                state = state,
                isDemo = isDemo,
                onSubmit = { onImport(link) },
                onManual = { setMethod(true) },
            )
            state.preview?.takeIf { it.requestedUrl == link.trim() }?.let { preview ->
                drafts.SaveableStateProvider("import") {
                    key(preview.version) {
                        ListingPreview(
                            listing = preview.listing,
                            enabled = canSave && !state.importing,
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
@OptIn(ExperimentalLayoutApi::class)
private fun AddMethodPicker(manual: Boolean, enabled: Boolean, onSelect: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Способ добавления", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MethodOption("По ссылке", !manual, enabled) { onSelect(false) }
            MethodOption("Вручную", manual, enabled) { onSelect(true) }
        }
    }
}

@Composable
private fun MethodOption(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ImportPanel(
    link: String,
    onLinkChange: (String) -> Unit,
    state: CarFormUiState,
    isDemo: Boolean,
    onSubmit: () -> Unit,
    onManual: () -> Unit,
) {
    val enabled = !state.importing && !state.saving && state.savedId == null
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSubmit()
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Поддерживаются ссылки на объявления Дрома. Данные можно проверить перед сохранением.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isDemo) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Для демопроверки подойдёт",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SelectionContainer {
                        Text(
                            DEMO_LINK,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            ListingLinkField(
                value = link,
                onValueChange = onLinkChange,
                enabled = enabled,
                error = state.linkError,
                onSubmit = submit,
            )
            PrimaryAction(
                label = if (state.importing) "Получаем данные…" else "Получить данные",
                enabled = enabled,
                onClick = submit,
            )
            state.importError?.let { error ->
                FormAlert("Не удалось получить данные", error)
                SecondaryAction(label = "Ввести данные вручную", enabled = !state.saving, onClick = onManual)
            }
        }
    }
}

@Composable
private fun ListingPreview(
    listing: ListingData,
    enabled: Boolean,
    saving: Boolean,
    saveError: String?,
    onChange: () -> Unit,
    onSubmit: (CarInput) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormSectionHeading("Предпросмотр автомобиля")
        if (listing.source == CarSource.DEMO) {
            StatusLabel(if (isDemoModelPhoto(listing.imageUrl)) "Демо · фото модели" else "Демо-источник")
        }
        Text(
            text = if (listing.source == CarSource.DEMO) {
                "Показаны синтетические данные. Проверьте и при необходимости измените их перед сохранением."
            } else {
                "Проверьте данные и при необходимости исправьте их перед сохранением."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        VehicleGallery(imageUrls = listing.imageUrls, contentDescription = "Предпросмотр автомобиля")
        VehicleForm(
            initial = listing,
            submitLabel = "Сохранить автомобиль",
            enabled = enabled,
            saving = saving,
            saveError = saveError,
            onChange = onChange,
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun FormSectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
}

@Preview(name = "Добавление по ссылке", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ImportCarPreview() = AddCarScreenPreview(CarFormUiState())

@Preview(name = "Получение данных", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoadingImportPreview() = AddCarScreenPreview(CarFormUiState(importing = true), link = DEMO_LINK)

@Preview(name = "Ошибка импорта", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FailedImportPreview() = AddCarScreenPreview(
    CarFormUiState(importError = "Объявление недоступно. Проверьте ссылку или добавьте автомобиль вручную."),
    link = "https://auto.drom.ru/demo/404.html",
)

@Preview(name = "Подтверждение демоимпорта", showBackground = true, widthDp = 390, heightDp = 844)
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
    AddCarScreenPreview(CarFormUiState(preview = ImportPreview(DEMO_LINK, listing, version = 1)), link = DEMO_LINK)
}

@Composable
private fun AddCarScreenPreview(state: CarFormUiState, link: String? = null) {
    DromProfitTheme {
        ProfitScaffold(title = "Добавить автомобиль", onBack = {}) { insets ->
            ProfitScreenColumn(insets) {
                ProfitBreadcrumb(current = "Добавить автомобиль", onHome = {})
                PageHeading("Добавить автомобиль")
                AddCarContent(
                    state = state,
                    isDemo = true,
                    initialUrl = link,
                    startManual = false,
                    onImport = {},
                    onLinkChange = {},
                    onDraftChange = {},
                    onSave = {},
                )
            }
        }
    }
}
