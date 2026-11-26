package ru.dedmos.dromprofit.presentation.screens.add

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.dedmos.dromprofit.domain.CarInput
import ru.dedmos.dromprofit.domain.ListingData
import ru.dedmos.dromprofit.presentation.forms.CarField
import ru.dedmos.dromprofit.presentation.forms.CarFieldInput
import ru.dedmos.dromprofit.presentation.forms.initialValue
import ru.dedmos.dromprofit.presentation.forms.toCarInput
import ru.dedmos.dromprofit.presentation.forms.validationErrors
import ru.dedmos.dromprofit.presentation.ui.theme.ProfitTokens

private val basicFields = listOf(
    CarField.BRAND,
    CarField.MODEL,
    CarField.PRICE,
    CarField.YEAR,
    CarField.MILEAGE,
    CarField.CITY,
)
private val listingFields = listOf(CarField.IMAGE_URL, CarField.LISTING_URL, CarField.MARKET_PRICE)
private val characteristicFields = CarField.entries - basicFields.toSet() - listingFields.toSet()

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun VehicleForm(
    initial: ListingData? = null,
    submitLabel: String,
    enabled: Boolean,
    saving: Boolean,
    saveError: String?,
    onChange: () -> Unit,
    onSubmit: (CarInput) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var values by rememberSaveable {
        mutableStateOf(CarField.entries.map { initial.initialValue(it).orEmpty() })
    }
    var attempts by rememberSaveable { mutableIntStateOf(0) }
    var scrollTo by remember { mutableStateOf<CarField?>(null) }
    val focusManager = LocalFocusManager.current

    val errors = if (attempts > 0) values.validationErrors(initial) else emptyMap()
    val updateValue: (CarField, String) -> Unit = { field, value ->
        values = values.mapIndexed { index, current -> if (index == field.ordinal) value else current }
        onChange()
    }
    val submit: () -> Unit = {
        focusManager.clearFocus()
        val invalid = values.validationErrors(initial).keys.firstOrNull()
        if (invalid == null) {
            onSubmit(values.toCarInput(initial))
        } else {
            scrollTo = invalid
            attempts++
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            "Обязательные поля отмечены звёздочкой.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        CarFieldGroup("Основные данные", basicFields, values, errors, enabled, scrollTo, attempts, updateValue)
        CarFieldGroup("Характеристики", characteristicFields, values, errors, enabled, scrollTo, attempts, updateValue)
        CarFieldGroup("Объявление и фото", listingFields, values, errors, enabled, scrollTo, attempts, updateValue)
        if (saveError != null) FormAlert("Не удалось сохранить автомобиль", saveError)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = submit,
                modifier = Modifier.heightIn(min = ProfitTokens.minTouchHeight),
                enabled = enabled,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(if (saving) "Сохраняем…" else submitLabel)
            }
            if (onCancel != null) {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = ProfitTokens.minTouchHeight),
                    enabled = !saving,
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Text("Отмена")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun CarFieldGroup(
    title: String,
    fields: List<CarField>,
    values: List<String>,
    errors: Map<CarField, String>,
    enabled: Boolean,
    scrollTo: CarField?,
    attempts: Int,
    onChange: (CarField, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        fields.forEach { field ->
            val bringIntoView = remember { BringIntoViewRequester() }
            LaunchedEffect(scrollTo, attempts) {
                if (scrollTo == field) bringIntoView.bringIntoView()
            }
            FormTextField(
                value = values[field.ordinal],
                onValueChange = { onChange(field, it) },
                modifier = Modifier.bringIntoViewRequester(bringIntoView),
                label = if (field.required) "${field.label} *" else field.label,
                enabled = enabled,
                errorMessage = errors[field],
                helperText = if (field == CarField.MARKET_PRICE) {
                    "Заполните, если оценка указана в объявлении."
                } else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = field.input.keyboardType,
                    imeAction = if (field == CarField.MARKET_PRICE) ImeAction.Done else ImeAction.Next,
                ),
            )
        }
    }
}

@Composable
internal fun FormAlert(title: String, message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val CarFieldInput.keyboardType: KeyboardType
    get() = when (this) {
        CarFieldInput.TEXT -> KeyboardType.Text
        CarFieldInput.INTEGER -> KeyboardType.Number
        CarFieldInput.DECIMAL -> KeyboardType.Decimal
        CarFieldInput.URI -> KeyboardType.Uri
    }
