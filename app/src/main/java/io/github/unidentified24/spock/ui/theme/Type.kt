package io.github.unidentified24.spock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val SpotifyMix = FontFamily.SansSerif
val SpotifyMixTitle = FontFamily.SansSerif
val Montserrat = FontFamily.SansSerif
private val body = TextStyle(fontFamily = FontFamily.SansSerif)
private val heading = body.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
val Typography = Typography(
    displayLarge = heading.copy(fontSize = 52.sp, lineHeight = 56.sp),
    displayMedium = heading.copy(fontSize = 44.sp, lineHeight = 48.sp),
    displaySmall = heading.copy(fontSize = 36.sp, lineHeight = 40.sp),
    headlineLarge = heading.copy(fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = heading.copy(fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = heading.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = heading.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = heading.copy(fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = heading.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = body.copy(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = body.copy(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = body.copy(fontSize = 11.sp, letterSpacing = 1.sp),
)
