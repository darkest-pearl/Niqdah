package com.musab.niqdah.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = LedgerGreen,
    onPrimary = SoftPaper,
    primaryContainer = MintSurface,
    onPrimaryContainer = LedgerGreenDark,
    secondary = Brass,
    onSecondary = SoftPaper,
    tertiary = ClearBlue,
    background = SoftPaper,
    onBackground = Ink,
    surface = ColorTokens.LightSurface,
    onSurface = Ink,
    surfaceVariant = MintSurface,
    onSurfaceVariant = ColorTokens.MutedInk,
    error = ColorTokens.Error,
    errorContainer = ColorTokens.ErrorContainer,
    onErrorContainer = ColorTokens.OnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = NightPrimary,
    onPrimary = LedgerGreenDark,
    primaryContainer = LedgerGreenDark,
    onPrimaryContainer = MintSurface,
    secondary = NightSecondary,
    onSecondary = Ink,
    tertiary = NightTertiary,
    background = NightBackground,
    onBackground = ColorTokens.NightText,
    surface = NightSurface,
    onSurface = ColorTokens.NightText,
    surfaceVariant = ColorTokens.NightSurfaceVariant,
    onSurfaceVariant = ColorTokens.NightMutedText,
    error = ColorTokens.NightError,
    errorContainer = ColorTokens.NightErrorContainer,
    onErrorContainer = ColorTokens.NightOnErrorContainer
)

@Composable
fun NiqdahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = NiqdahShapes,
        content = content
    )
}
