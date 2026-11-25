package ru.dedmos.dromprofit.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ProfitTokens {
    val screenPadding = 16.dp
    val contentGap = 12.dp
    val sectionGap = 24.dp
    val cardPadding = 16.dp
    val fieldRadius = 12.dp
    val cardRadius = 16.dp
    val primaryActionHeight = 52.dp
    val minTouchHeight = 48.dp
}

private val profitColors = lightColorScheme(
    primary = ActionBlue,
    onPrimary = Color.White,
    primaryContainer = InfoBackground,
    onPrimaryContainer = InfoText,
    secondary = ActionBlue,
    onSecondary = Color.White,
    secondaryContainer = InfoBackground,
    onSecondaryContainer = InfoText,
    tertiary = WarningText,
    tertiaryContainer = WarningBackground,
    onTertiaryContainer = WarningText,
    background = PageBackground,
    onBackground = TextPrimary,
    surface = Color.White,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    surfaceVariant = PanelBackground,
    surfaceContainer = PanelBackground,
    surfaceContainerLow = PageBackground,
    surfaceContainerHigh = PanelBackground,
    outline = Border,
    outlineVariant = Divider,
    error = ErrorText,
    onError = Color.White,
    errorContainer = ErrorBackground,
    onErrorContainer = ErrorText
)

private val profitShapes = Shapes(
    small = RoundedCornerShape(ProfitTokens.fieldRadius),
    medium = RoundedCornerShape(ProfitTokens.fieldRadius),
    large = RoundedCornerShape(ProfitTokens.cardRadius),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun DromProfitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = profitColors,
        typography = Typography,
        shapes = profitShapes,
        content = content
    )
}
