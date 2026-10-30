package io.github.unidentified24.spock.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

enum class SpockLook(val label: String, val background: Color, val surface: Color, val accent: Color, val secondary: Color) {
    ORIGINAL("Original green",Color(0xFF121212),Color(0xFF202020),Color(0xFF1ED760),Color(0xFFB3E8BD)),
    CLEAN("Clean graphite",Color(0xFF101215),Color(0xFF1B1E23),Color(0xFFE5EAF2),Color(0xFFA7B8CD)),
    ROSE("Rose quartz",Color(0xFF1B1119),Color(0xFF2C1D28),Color(0xFFFFB1D2),Color(0xFFD1BBFF)),
    EMBER("Ember", Color(0xFF121018), Color(0xFF211D2A), Color(0xFFFF917C), Color(0xFFE9C28A)),
    MIDNIGHT("Amethyst",Color(0xFF15101F),Color(0xFF251C32),Color(0xFFD2ADFF),Color(0xFFF4B5D1)),
    AURORA("Aurora", Color(0xFF101420), Color(0xFF1B2334), Color(0xFFAFA4FF), Color(0xFF91C9EB)),
    PAPER("Sandstone", Color(0xFF211D18), Color(0xFF302A22), Color(0xFFE8C99B), Color(0xFFBCB5A8)),
    OCEAN("Deep sea", Color(0xFF0C171D), Color(0xFF172A33), Color(0xFF81D4DA), Color(0xFF96B9EB)),
}
var currentLook by mutableStateOf(SpockLook.EMBER)
val AppBackground get() = currentLook.background
val GridBackground get() = currentLook.surface
val AppPalette get() = currentLook.accent
val SecondaryPalette get() = currentLook.secondary
val SurfaceVariant get() = currentLook.surface.copy(red = (currentLook.surface.red + 0.04f).coerceAtMost(1f))
val TextMuted = Color(0xFFBEBAC5)
val OutlineSubtle get() = currentLook.accent.copy(alpha = 0.20f)
val Purple80 get() = SecondaryPalette
val PurpleGrey80 = TextMuted
val Pink80 get() = AppPalette
val Purple40 get() = AppPalette
val PurpleGrey40 get() = SurfaceVariant
val Pink40 get() = SecondaryPalette

enum class SpockLayout(val label:String){STUDIO("Studio"),COMPACT("Compact"),CLASSIC("Classic")}
var currentLayout by mutableStateOf(SpockLayout.STUDIO)
