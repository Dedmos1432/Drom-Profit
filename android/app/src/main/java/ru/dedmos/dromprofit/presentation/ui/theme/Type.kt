package ru.dedmos.dromprofit.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val baseTypography = Typography()

val Typography = Typography(
    headlineMedium = baseTypography.headlineMedium.copy(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum"
    ),
    headlineSmall = baseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = baseTypography.titleLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold
    ),
    titleMedium = baseTypography.titleMedium.copy(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = baseTypography.bodyMedium.copy(letterSpacing = 0.sp),
    bodySmall = baseTypography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
)
