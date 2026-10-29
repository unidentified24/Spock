package io.github.unidentified24.spock.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

enum class SpockLook(val label: String, val background: Color, val surface: Color, val accent: Color, val secondary: Color) {
    EMBER("Ember", Color(0xFF151013), Color(0xFF261C22), Color(0xFFFF917C), Color(0xFFE9C28A)),
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
