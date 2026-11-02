package io.github.unidentified24.spock.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.unidentified24.spock.features.*
import kotlinx.coroutines.delay

val LocalComfort=staticCompositionLocalOf{ComfortOptions()}

@Composable
fun SpockTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val options by FeatureSettings.state.collectAsState()
    val density=LocalDensity.current
    val preferred=remember(density,options.comfort.textScale){Density(density.density,density.fontScale*options.comfort.textScale)}
    LaunchedEffect(Unit) {
        val saved = context.getSharedPreferences("appearance", 0).getString("theme", null)
        currentLayout=SpockLayout.entries.firstOrNull{it.name==context.getSharedPreferences("appearance",0).getString("layout",null)} ?: SpockLayout.STUDIO
        currentLook = SpockLook.entries.firstOrNull { it.name == saved } ?: SpockLook.EMBER
    }
    LaunchedEffect(options.comfort,options.appearance.presets){
        while(true){
            val choice=ThemeSchedule.select(options.comfort,SpockLook.entries.map{it.name}+options.appearance.presets.map{"custom:"+it.id},java.time.ZonedDateTime.now())
            val preset=options.appearance.presets.firstOrNull{"custom:"+it.id==choice}
            if(preset!=null){
                val current=FeatureSettings.value.appearance
                val next=current.copy(custom=true,accentHue=preset.accentHue,secondaryHue=preset.secondaryHue,backgroundHue=preset.backgroundHue,depth=preset.depth,saturation=preset.saturation)
                if(next!=current)FeatureSettings.update{it.copy(appearance=next)}
            }else{
                if(choice!=null&&FeatureSettings.value.appearance.custom)FeatureSettings.update{it.copy(appearance=it.appearance.copy(custom=false))}
                currentLook=SpockLook.entries.firstOrNull{it.name==(choice ?: context.getSharedPreferences("appearance",0).getString("theme",null))} ?: currentLook
            }
            delay(60000)
        }
    }
    CompositionLocalProvider(LocalComfort provides options.comfort,LocalDensity provides preferred){
    MaterialTheme(
        colorScheme = darkColorScheme(primary = AppPalette, secondary = SecondaryPalette,
            background = AppBackground, surface = GridBackground, surfaceVariant = SurfaceVariant,
            outline=OutlineSubtle,onSurfaceVariant=TextMuted,primaryContainer=SurfaceVariant,
            onPrimary = AppBackground, onSecondary = AppBackground, onBackground = Color.White, onSurface = Color.White),
        typography = appearanceTypography(options.appearance.fontStyle),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(22.dp), large = RoundedCornerShape(30.dp)),
    ) { ProvideTextStyle(MaterialTheme.typography.bodyMedium, content) }
    }
}
