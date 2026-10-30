package io.github.unidentified24.spock.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun SpockTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val saved = context.getSharedPreferences("appearance", 0).getString("theme", null)
        currentLook = SpockLook.entries.firstOrNull { it.name == saved } ?: SpockLook.EMBER
    }
    MaterialTheme(
        colorScheme = darkColorScheme(primary = AppPalette, secondary = SecondaryPalette,
            background = AppBackground, surface = GridBackground, surfaceVariant = SurfaceVariant,
            outline=OutlineSubtle,onSurfaceVariant=TextMuted,primaryContainer=SurfaceVariant,
            onPrimary = AppBackground, onSecondary = AppBackground, onBackground = Color.White, onSurface = Color.White),
        typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(30.dp)),
    ) { ProvideTextStyle(MaterialTheme.typography.bodyMedium, content) }
}
