package com.example.airquality.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = lightColorScheme(
    primary            = OrangeMain,
    onPrimary          = White,
    primaryContainer   = OrangeBadge,
    background         = BgMain,
    onBackground       = TextDark,
    surface            = CardWhite,
    onSurface          = TextDark,
    surfaceVariant     = InputBg,
    onSurfaceVariant   = TextMid,
    outline            = DividerColor,
)

@Composable
fun AirQualityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = Typography,
        content     = content
    )
}