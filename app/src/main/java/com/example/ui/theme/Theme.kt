package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class ChatboxExtendedColors(
    val isLavender: Boolean = false,
    val background: Color = ChatboxBgDefault,
    val surface: Color = ChatboxSurfaceDefault,
    val fieldBg: Color = ChatboxFieldDefault,
    val selectedBg: Color = ChatboxSelectedBg,
    val border: Color = ChatboxBorder,
    val outgoingBubble: Color = ChatboxOutgoingBubble,
    val outgoingText: Color = ChatboxOutgoingText,
    val incomingBubble: Color = ChatboxIncomingBubble,
    val incomingText: Color = ChatboxIncomingText,
    val noticeBg: Color = ChatboxSoftLavender,
    val noticeInk: Color = ChatboxInk,
    val ink: Color = ChatboxInk,
    val inkMuted: Color = ChatboxInkMuted,
    val inkSubtle: Color = ChatboxInkSubtle,
    val primary: Color = ChatboxPrimary,
    val updateGreen: Color = ChatboxUpdateGreen,
    val updateGreenSoft: Color = ChatboxUpdateGreenSoft,
    val updateGreenDark: Color = ChatboxUpdateGreenDark
)

val LocalChatboxColors = staticCompositionLocalOf { ChatboxExtendedColors() }

private val DefaultExtendedColors = ChatboxExtendedColors(
    isLavender = false,
    background = ChatboxBgDefault,
    surface = ChatboxSurfaceDefault,
    fieldBg = ChatboxFieldDefault,
    selectedBg = ChatboxSelectedBg,
    border = ChatboxBorder
)

private val LavenderExtendedColors = ChatboxExtendedColors(
    isLavender = true,
    background = ChatboxBgDefault,
    surface = ChatboxSurfaceDefault,
    fieldBg = ChatboxFieldLavender,
    selectedBg = ChatboxSoftLavenderStrong,
    border = ChatboxBorderLavender
)

private val DefaultColorScheme = lightColorScheme(
    primary = ChatboxPrimary,
    onPrimary = Color.White,
    primaryContainer = ChatboxSoftLavender,
    onPrimaryContainer = ChatboxPrimaryDark,
    secondary = ChatboxAccentViolet,
    onSecondary = Color.White,
    secondaryContainer = ChatboxSelectedBg,
    onSecondaryContainer = ChatboxInk,
    background = ChatboxBgDefault,
    onBackground = ChatboxInk,
    surface = ChatboxSurfaceDefault,
    onSurface = ChatboxInk,
    surfaceVariant = ChatboxFieldDefault,
    onSurfaceVariant = ChatboxInkMuted,
    outline = ChatboxBorder,
    error = ChatboxErrorInk,
    onError = Color.White,
    errorContainer = ChatboxErrorBg,
    onErrorContainer = ChatboxErrorInk
)

private val LavenderColorScheme = DefaultColorScheme.copy(
    background = ChatboxBgDefault,
    surface = ChatboxSurfaceDefault,
    surfaceVariant = ChatboxFieldLavender,
    outline = ChatboxBorderLavender
)

val ChatboxShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ChatboxTheme(
    useLavenderTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (useLavenderTheme) LavenderColorScheme else DefaultColorScheme
    val extended = if (useLavenderTheme) LavenderExtendedColors else DefaultExtendedColors

    CompositionLocalProvider(LocalChatboxColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ChatboxTypography,
            shapes = ChatboxShapes,
            content = content
        )
    }
}
