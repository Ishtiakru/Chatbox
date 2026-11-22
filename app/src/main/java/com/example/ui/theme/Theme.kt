package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DefaultChatboxColorScheme = lightColorScheme(
    primary = ChatboxPrimary,
    onPrimary = Color.White,
    primaryContainer = ChatboxOutgoingBubble,
    onPrimaryContainer = ChatboxOutgoingText,
    secondary = ChatboxSparkAccent,
    onSecondary = Color.White,
    secondaryContainer = ChatboxSelectedBg,
    onSecondaryContainer = ChatboxInk,
    tertiary = ChatboxLoginPrimary,
    onTertiary = Color.White,
    background = ChatboxSurface,
    onBackground = ChatboxInk,
    surface = ChatboxSurface,
    onSurface = ChatboxInk,
    surfaceVariant = ChatboxFieldBg,
    onSurfaceVariant = ChatboxMuted,
    surfaceContainerLowest = ChatboxSurface,
    surfaceContainerLow = ChatboxRailBg,
    surfaceContainer = ChatboxFieldBg,
    surfaceContainerHigh = ChatboxSelectedBg,
    outline = ChatboxBorder,
    outlineVariant = ChatboxBorder,
    error = ChatboxErrorInk,
    onError = Color.White,
    errorContainer = ChatboxErrorBg,
    onErrorContainer = ChatboxErrorInk
)

private val LavenderChatboxColorScheme = DefaultChatboxColorScheme.copy(
    background = ChatboxLavenderBg,
    surface = ChatboxLavenderBg,
    surfaceContainerLowest = ChatboxLavenderBg
)

val ChatboxShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun ChatboxTheme(
    lavenderAppearance: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (lavenderAppearance) {
        LavenderChatboxColorScheme
    } else {
        DefaultChatboxColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChatboxTypography,
        shapes = ChatboxShapes,
        content = content
    )
}
