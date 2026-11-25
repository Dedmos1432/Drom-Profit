package ru.dedmos.dromprofit.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ru.dedmos.dromprofit.presentation.ui.theme.BrandRed
import ru.dedmos.dromprofit.presentation.ui.theme.DromProfitTheme
import ru.dedmos.dromprofit.presentation.ui.theme.ProfitTokens
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfitScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        modifier = Modifier.semantics { heading() },
                        color = if (title == "Drom Profit") BrandRed else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = bottomBar,
        content = content
    )
}

@Composable
fun PrimaryAction(
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = ProfitTokens.primaryActionHeight),
        shape = MaterialTheme.shapes.small
    ) { Text(label) }
}

@Composable
fun SecondaryAction(
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = ProfitTokens.minTouchHeight),
        shape = MaterialTheme.shapes.small
    ) { Text(label) }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(ProfitTokens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
fun PriceText(value: Number?, modifier: Modifier = Modifier) {
    Text(formatRubles(value), modifier = modifier, style = MaterialTheme.typography.headlineMedium)
}

@Composable
fun AmountLine(
    modifier: Modifier = Modifier,
    label: String,
    value: Number?,
    strong: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            text = formatRubles(value),
            textAlign = TextAlign.End,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal
        )
    }
}

fun formatRubles(value: Number?): String = value?.let {
    val amount = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU")).format(it)
    "$amount\u00A0₽"
} ?: "Нет данных"

@Composable
fun StatusLabel(text: String, warning: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (warning) colors.tertiaryContainer else colors.secondaryContainer,
        contentColor = if (warning) colors.onTertiaryContainer else colors.onSecondaryContainer
    ) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun VehicleImage(imageUrl: String?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.DirectionsCar,
                contentDescription = if (imageUrl == null) "Фото отсутствует" else "Фото недоступно",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LoadingState(message: String = "Загрузка…", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ProfitTokens.contentGap)
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
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Column(
            modifier = Modifier.padding(ProfitTokens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(message)
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Повторить") }
        }
    }
}

@Composable
fun SessionExpiredState(
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Panel(modifier) {
        Text("Сессия недоступна", style = MaterialTheme.typography.titleMedium)
        Text("Можно начать новую анонимную сессию с пустой выборкой.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        SecondaryAction(label = "Начать новую сессию", onClick = { confirm = true })
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Начать новую сессию?") },
            text = { Text("В новой сессии прежняя выборка будет недоступна.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onRestart()
                }) { Text("Начать") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } }
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ProfitComponentsPreview() {
    DromProfitTheme {
        Panel {
            Text("Стоимость приобретения", style = MaterialTheme.typography.titleMedium)
            PriceText(1_300_000L)
            StatusLabel("Предварительная оценка", warning = true)
            AmountLine(label = "Преимущества", value = 50_000L)
            PrimaryAction(label = "Открыть карточку", onClick = {})
        }
    }
}
