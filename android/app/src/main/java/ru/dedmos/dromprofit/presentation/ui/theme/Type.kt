package ru.dedmos.dromprofit.presentation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaults = Typography()

val Typography = Typography(
    headlineMedium = defaults.headlineMedium.copy(
        fontSize = 30.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum",
    ),
    headlineSmall = defaults.headlineSmall.copy(
        fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold,
    ),
    titleLarge = defaults.titleLarge.copy(
        fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold,
    ),
    titleMedium = defaults.titleMedium.copy(
        fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = defaults.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium = defaults.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodySmall = defaults.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
    labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
)
