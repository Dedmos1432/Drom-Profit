package ru.dedmos.dromprofit.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.dedmos.dromprofit.domain.Car
import ru.dedmos.dromprofit.domain.CarSource
import ru.dedmos.dromprofit.presentation.ui.theme.ActionBackground
import ru.dedmos.dromprofit.presentation.ui.theme.ActionText
import ru.dedmos.dromprofit.presentation.ui.theme.DisabledBackground
import ru.dedmos.dromprofit.presentation.ui.theme.LinkBlue
import ru.dedmos.dromprofit.presentation.ui.theme.ProfitTokens
import ru.dedmos.dromprofit.presentation.ui.theme.SuccessBackground
import ru.dedmos.dromprofit.presentation.ui.theme.SuccessText
import ru.dedmos.dromprofit.presentation.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProfitScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val navigation = LocalProfitNavigation.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { ProfitSiteHeader(navigation, ProfitDestination.fromTitle(title), onBack, actions) },
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
internal fun ProfitScreenColumn(
    insets: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier.widthIn(max = 720.dp).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ProfitTokens.screenPadding, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
internal fun PageHeading(title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun PrimaryAction(
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = FilledAction(label, modifier.fillMaxWidth(), enabled, MaterialTheme.colorScheme.primary, Color.White, onClick)

@Composable
fun SecondaryAction(
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = FilledAction(label, modifier.fillMaxWidth(), enabled, ActionBackground, ActionText, onClick)

@Composable
internal fun ContrastAction(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    FilledAction(label, modifier.fillMaxWidth(), enabled, Color.Black, Color.White, onClick)

@Composable
internal fun DangerAction(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    FilledAction(label, modifier, enabled, MaterialTheme.colorScheme.error, Color.White, onClick)

@Composable
private fun FilledAction(
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = ProfitTokens.primaryActionHeight),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = background,
            contentColor = foreground,
            disabledContainerColor = DisabledBackground,
            disabledContentColor = TextSecondary,
        ),
    ) { Text(label, textAlign = TextAlign.Center) }
}

@Composable
internal fun OutlineAction(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = ProfitTokens.minTouchHeight),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LinkBlue, disabledContentColor = TextSecondary),
        border = BorderStroke(1.dp, if (enabled) LinkBlue else MaterialTheme.colorScheme.outlineVariant),
    ) { Text(label, textAlign = TextAlign.Center) }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(ProfitTokens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun PriceText(value: Long?, modifier: Modifier = Modifier) {
    Text(formatRubles(value), modifier = modifier, style = MaterialTheme.typography.headlineMedium)
}

@Composable
fun AmountLine(
    modifier: Modifier = Modifier,
    label: String,
    value: Long?,
    strong: Boolean = false,
    missingText: String = "Не указано",
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            text = if (value == null) missingText else formatRubles(value),
            textAlign = TextAlign.End,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

fun formatRubles(value: Long?): String = value?.let { "${formatInteger(it)}\u00A0₽" } ?: "Не указано"

internal fun formatMileage(value: Long?): String = value?.let { "${formatInteger(it)} км" } ?: "Пробег не указан"

private fun formatInteger(value: Long): String =
    NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU")).format(value)

internal fun vehicleName(car: Car): String = listOfNotNull("${car.brand} ${car.model}", car.year?.toString()).joinToString(", ")

internal fun vehicleSourceLabel(car: Car): String = when (car.source) {
    CarSource.DEMO -> if (isDemoModelPhoto(car.imageUrl)) "Демо · фото модели" else "Демо-источник"
    CarSource.DROM -> "Дром"
    CarSource.MANUAL -> "Введено вручную"
}

internal enum class BadgeTone { INFO, NEUTRAL, WARNING, SUCCESS }

@Composable
internal fun ProfitBadge(text: String, tone: BadgeTone = BadgeTone.INFO) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (tone) {
        BadgeTone.INFO -> ActionBackground to ActionText
        BadgeTone.NEUTRAL -> colors.surfaceVariant to colors.onSurfaceVariant
        BadgeTone.WARNING -> colors.tertiaryContainer to colors.onTertiaryContainer
        BadgeTone.SUCCESS -> SuccessBackground to SuccessText
    }
    Surface(shape = CircleShape, color = background, contentColor = foreground) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun StatusLabel(text: String, warning: Boolean = false) {
    ProfitBadge(text, if (warning) BadgeTone.WARNING else BadgeTone.INFO)
}

@Composable
fun VehicleImage(imageUrl: String?, modifier: Modifier = Modifier) {
    VehiclePhoto(imageUrl, modifier)
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LoadingState(message: String = "Загрузка…", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ProfitTokens.contentGap),
    ) {
        CircularProgressIndicator(Modifier.size(32.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.padding(ProfitTokens.cardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Повторить") }
        }
    }
}

@Composable
fun SessionExpiredState(onRestart: () -> Unit, modifier: Modifier = Modifier, error: String? = null) {
    Panel(modifier) {
        Text("Сессия больше недоступна", style = MaterialTheme.typography.titleMedium)
        Text("Начните новую сессию, чтобы создать новую выборку.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        PrimaryAction(label = "Начать новую сессию", onClick = onRestart)
    }
}

@Composable
internal fun DeleteCarConfirmation(name: String, pending: Boolean, error: String?, onClose: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!pending) onClose() },
        title = { Text("Удалить автомобиль?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$name будет удалён из выборки вместе с его расходами, преимуществами и заметками.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            DangerAction(label = if (pending) "Удаляем…" else "Удалить", enabled = !pending, onClick = onDelete)
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !pending) { Text("Отмена") } },
    )
}
