package ru.dedmos.dromprofit.presentation.screens.add

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import ru.dedmos.dromprofit.domain.CarInput
import ru.dedmos.dromprofit.domain.ListingData
import ru.dedmos.dromprofit.presentation.components.ErrorState
import ru.dedmos.dromprofit.presentation.components.Panel
import ru.dedmos.dromprofit.presentation.components.PrimaryAction
import ru.dedmos.dromprofit.presentation.forms.CarField
import ru.dedmos.dromprofit.presentation.forms.CarFieldInput
import ru.dedmos.dromprofit.presentation.forms.initialValue
import ru.dedmos.dromprofit.presentation.forms.toCarInput
import ru.dedmos.dromprofit.presentation.forms.validationErrors
import ru.dedmos.dromprofit.presentation.ui.theme.ProfitTokens

private val basicFields = listOf(CarField.BRAND, CarField.MODEL, CarField.PRICE)
private val listingFields = listOf(CarField.IMAGE_URL, CarField.LISTING_URL, CarField.MARKET_PRICE)
private val characteristicFields = CarField.entries - basicFields.toSet() - listingFields.toSet()
@Composable
internal fun VehicleForm(
    initial: ListingData? = null,
    submitLabel: String,
    enabled: Boolean,
    saving: Boolean,
    saveError: String?,
    onChange: () -> Unit,
    onSubmit: (CarInput) -> Unit,
) {
    var values by rememberSaveable {
        mutableStateOf(CarField.entries.map { initial.initialValue(it).orEmpty() })
    }
    var showCharacteristics by rememberSaveable { mutableStateOf(false) }
    var showListing by rememberSaveable { mutableStateOf(false) }
    var attempts by rememberSaveable { mutableIntStateOf(0) }
    var scrollTo by remember { mutableStateOf<CarField?>(null) }
    val focusManager = LocalFocusManager.current

    val errors = if (attempts > 0) values.validationErrors(initial) else emptyMap()
    val updateValue: (CarField, String) -> Unit = { field, value ->
        values = values.mapIndexed { index, current -> if (index == field.ordinal) value else current }
        onChange()
    }

    Column(verticalArrangement = Arrangement.spacedBy(ProfitTokens.contentGap)) {
        Panel {
            Text(
                "Основные данные",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Обязательные поля отмечены *.", style = MaterialTheme.typography.bodySmall)
            CarFields(basicFields, values, errors, enabled, scrollTo, attempts, updateValue)
        }
        Panel {
            FormSectionToggle("Характеристики", showCharacteristics) {
                showCharacteristics = !showCharacteristics
            }
            if (showCharacteristics) {
                CarFields(characteristicFields, values, errors, enabled, scrollTo, attempts, updateValue)
            }
        }
        Panel {
            FormSectionToggle("Объявление и фото", showListing) { showListing = !showListing }
            if (showListing) {
                CarFields(listingFields, values, errors, enabled, scrollTo, attempts, updateValue)
            }
        }

        if (saveError != null) ErrorState(message = saveError)
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        PrimaryAction(label = if (saving) "Сохраняем…" else submitLabel, enabled = enabled) {
            focusManager.clearFocus()
            val invalid = values.validationErrors(initial).keys.firstOrNull()
            if (invalid == null) {
                onSubmit(values.toCarInput(initial))
            } else {
                showCharacteristics = showCharacteristics || invalid in characteristicFields
                showListing = showListing || invalid in listingFields
                scrollTo = invalid
                attempts++
            }
        }
    }
}

@Composable
private fun FormSectionToggle(label: String, expanded: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Свернуть" else "Развернуть",
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CarFields(
    fields: List<CarField>,
    values: List<String>,
    errors: Map<CarField, String>,
    enabled: Boolean,
    scrollTo: CarField?,
    attempts: Int,
    onChange: (CarField, String) -> Unit,
) {
    fields.forEach { field ->
        val requester = remember { BringIntoViewRequester() }
        LaunchedEffect(attempts, scrollTo) {
            if (scrollTo == field) requester.bringIntoView()
        }
        val error = errors[field]
        OutlinedTextField(
            value = values[field.ordinal],
            onValueChange = { onChange(field, it) },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(requester),
            enabled = enabled,
            label = { Text(if (field.required) "${field.label} *" else field.label) },
            supportingText = when {
                error != null -> ({ Text(error) })
                field == CarField.MARKET_PRICE -> ({ Text("Если оценка указана в объявлении.") })
                else -> null
            },
            isError = error != null,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = field.input.keyboardType, imeAction = ImeAction.Next),
        )
    }
}

private val CarFieldInput.keyboardType: KeyboardType
    get() = when (this) {
        CarFieldInput.TEXT -> KeyboardType.Text
        CarFieldInput.INTEGER -> KeyboardType.Number
        CarFieldInput.DECIMAL -> KeyboardType.Decimal
        CarFieldInput.URI -> KeyboardType.Uri
    }
