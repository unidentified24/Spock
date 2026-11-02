package io.github.unidentified24.spock.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import io.github.unidentified24.spock.features.*

enum class SpockLook(val label: String, val background: Color, val surface: Color, val accent: Color, val secondary: Color) {
    ORIGINAL("Original green",Color(0xFF121212),Color(0xFF202020),Color(0xFF1ED760),Color(0xFFB3E8BD)),
    CLEAN("Clean graphite",Color(0xFF101215),Color(0xFF1B1E23),Color(0xFFE5EAF2),Color(0xFFA7B8CD)),
    ROSE("Rose quartz",Color(0xFF1B1119),Color(0xFF2C1D28),Color(0xFFFFB1D2),Color(0xFFD1BBFF)),
    EMBER("Ember", Color(0xFF121018), Color(0xFF211D2A), Color(0xFFFF917C), Color(0xFFE9C28A)),
    MIDNIGHT("Amethyst",Color(0xFF15101F),Color(0xFF251C32),Color(0xFFD2ADFF),Color(0xFFF4B5D1)),
    AURORA("Aurora", Color(0xFF101420), Color(0xFF1B2334), Color(0xFFAFA4FF), Color(0xFF91C9EB)),
    PAPER("Sandstone", Color(0xFF211D18), Color(0xFF302A22), Color(0xFFE8C99B), Color(0xFFBCB5A8)),
    OCEAN("Deep sea", Color(0xFF0C171D), Color(0xFF172A33), Color(0xFF81D4DA), Color(0xFF96B9EB)),
    FOREST("Moss",Color(0xFF101A14),Color(0xFF1D2B21),Color(0xFFB3D89C),Color(0xFFE3D09B)),
    COBALT("Cobalt",Color(0xFF0B1424),Color(0xFF162743),Color(0xFF8DBBFF),Color(0xFF91E1E0)),
    CHERRY("Cherry noir",Color(0xFF1B1012),Color(0xFF301D21),Color(0xFFFFA2AA),Color(0xFFEEC0A3)),
    PLUM("Plum velvet",Color(0xFF19101C),Color(0xFF2D1D32),Color(0xFFE4ACF2),Color(0xFFB9B2FF)),
    CITRUS("Citrus",Color(0xFF171B0D),Color(0xFF282E19),Color(0xFFD5EB7A),Color(0xFFAACFB4)),
    MONO("Ink · high contrast",Color(0xFF000000),Color(0xFF151515),Color(0xFFFFFFFF),Color(0xFFDADADA)),
    AMBER("Amber · blue contrast",Color(0xFF161616),Color(0xFF242424),Color(0xFFFFD47B),Color(0xFF8DC9FF)),
    LAGOON("Lagoon",Color(0xFF081C1C),Color(0xFF123232),Color(0xFF79E5D2),Color(0xFFFFC6AD)),
}
var currentLook by mutableStateOf(SpockLook.EMBER)
private data class CustomColors(val background:Color,val surface:Color,val accent:Color,val secondary:Color)
@Volatile private var cachedCustom:Pair<AppearanceOptions,CustomColors>?=null
private fun readable(color:Color,background:Color,minimum:Float=4.5f):Color {
    fun ratio(a:Color,b:Color):Float{val x=a.luminance();val y=b.luminance();return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)}
    if(ratio(color,background)>=minimum)return color
    for(step in 1..40){val candidate=lerp(color,Color.White,step/40f);if(ratio(candidate,background)>=minimum)return candidate}
    return Color.White
}
private fun customColors():CustomColors {
    val options=FeatureSettings.value.appearance
    cachedCustom?.takeIf{it.first==options}?.let{return it.second}
    val background=Color.hsv(options.backgroundHue,options.saturation,options.depth)
    val result=CustomColors(background,Color.hsv(options.backgroundHue,options.saturation*.8f,(options.depth+.07f).coerceAtMost(.25f)),readable(Color.hsv(options.accentHue,.55f,.98f),background),readable(Color.hsv(options.secondaryHue,.35f,.95f),background))
    cachedCustom=options to result;return result
}
val AppBackground get() = if(FeatureSettings.value.comfort.highContrast)Color.Black else if(FeatureSettings.value.appearance.custom)customColors().background else currentLook.background
val GridBackground get() = if(FeatureSettings.value.comfort.highContrast)Color(0xFF111111)else if(FeatureSettings.value.appearance.custom)customColors().surface else currentLook.surface
val AppPalette get() = (if(FeatureSettings.value.appearance.custom)customColors().accent else currentLook.accent).let{if(FeatureSettings.value.comfort.highContrast)readable(it,AppBackground,7f)else it}
val SecondaryPalette get() = if(FeatureSettings.value.appearance.custom)customColors().secondary else currentLook.secondary
val SurfaceVariant get() = GridBackground.copy(red = (GridBackground.red + 0.04f).coerceAtMost(1f))
val TextMuted get() = if(FeatureSettings.value.comfort.highContrast)Color(0xFFEAEAEA)else Color(0xFFBEBAC5)
val OutlineSubtle get() = currentLook.accent.copy(alpha = 0.20f)
val Purple80 get() = SecondaryPalette
val PurpleGrey80 = TextMuted
val Pink80 get() = AppPalette
val Purple40 get() = AppPalette
val PurpleGrey40 get() = SurfaceVariant
val Pink40 get() = SecondaryPalette

enum class SpockLayout(val label:String){STUDIO("Studio"),COMPACT("Compact"),CLASSIC("Classic"),GALLERY("Gallery"),EDITORIAL("Editorial"),RAIL("Sidebar"),MINIMAL("Minimal")}
var currentLayout by mutableStateOf(SpockLayout.STUDIO)
