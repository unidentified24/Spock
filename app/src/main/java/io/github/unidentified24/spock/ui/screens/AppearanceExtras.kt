package io.github.unidentified24.spock.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.unidentified24.spock.features.*
import io.github.unidentified24.spock.ui.components.*
import io.github.unidentified24.spock.ui.theme.*

@Composable fun AppearanceExtras(section:String="custom"){
    val options by FeatureSettings.state.collectAsState();val palette=options.appearance;val comfort=options.comfort
    var save by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")}
    if(section=="custom")StudioCard(Modifier.fillMaxWidth()){
        Text("Make your own palette",style=MaterialTheme.typography.headlineSmall)
        PreferenceSwitch("Custom colors",palette.custom){value->FeatureSettings.update{it.copy(appearance=it.appearance.copy(custom=value),comfort=if(value)it.comfort.copy(dayTheme="",nightTheme="",rotateThemes=false)else it.comfort)}}
        if(palette.custom){
            PreferenceSlider("Accent hue",palette.accentHue,0f..359f,label={it.toInt().toString()}){value->FeatureSettings.update{it.copy(appearance=it.appearance.copy(accentHue=value))}}
            PreferenceSlider("Second accent",palette.secondaryHue,0f..359f,label={it.toInt().toString()}){value->FeatureSettings.update{it.copy(appearance=it.appearance.copy(secondaryHue=value))}}
            PreferenceSlider("Background hue",palette.backgroundHue,0f..359f,label={it.toInt().toString()}){value->FeatureSettings.update{it.copy(appearance=it.appearance.copy(backgroundHue=value))}}
            PreferenceSlider("Background depth",palette.depth,0f..0.16f,label={"${(it/.16f*100).toInt()}%"}){value->FeatureSettings.update{it.copy(appearance=it.appearance.copy(depth=value))}}
            PreferenceSlider("Background color",palette.saturation,0f..0.7f,label={"${(it/.7f*100).toInt()}%"}){value->FeatureSettings.update{it.copy(appearance=it.appearance.copy(saturation=value))}}
            TextButton(onClick={name="";save=true}){Text("Save this palette")}
        }
        palette.presets.forEach{preset->Row{
            TextButton(modifier=Modifier.weight(1f),onClick={FeatureSettings.update{it.copy(appearance=it.appearance.copy(custom=true,accentHue=preset.accentHue,secondaryHue=preset.secondaryHue,backgroundHue=preset.backgroundHue,depth=preset.depth,saturation=preset.saturation))}}){Text(preset.name,maxLines=1)}
            TextButton(onClick={FeatureSettings.update{it.copy(appearance=it.appearance.copy(presets=it.appearance.presets.filter{p->p.id!=preset.id}))}}){Text("Remove")}
        }}
        PreferenceChoice("Type style",palette.fontStyle,listOf("MODERN" to "Spock","SERIF" to "Bookish","SYSTEM" to "System","MONO" to "Monospace")){value->FeatureSettings.update{it.copy(appearance=it.appearance.copy(fontStyle=value))}}
    }
    if(section=="schedule")StudioCard(Modifier.fillMaxWidth()){
        Text("A change of atmosphere",style=MaterialTheme.typography.headlineSmall)
        val themes=SpockLook.entries.map{it.name to it.label}+palette.presets.map{"custom:"+it.id to it.name}
        PreferenceSwitch("Schedule themes",comfort.dayTheme.isNotBlank()&&comfort.nightTheme.isNotBlank()){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(dayTheme=if(value)SpockLook.EMBER.name else "",nightTheme=if(value)SpockLook.OCEAN.name else ""))}}
        if(comfort.dayTheme.isNotBlank()){
            PreferenceChoice("Day theme",comfort.dayTheme,themes){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(dayTheme=value))}}
            PreferenceTime("Day starts",comfort.dayMinute){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(dayMinute=value))}}
            PreferenceChoice("Night theme",comfort.nightTheme,themes){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(nightTheme=value))}}
            PreferenceTime("Night starts",comfort.nightMinute){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(nightMinute=value))}}
        }
        PreferenceSwitch("Rotate favorites each day",comfort.rotateThemes){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(rotateThemes=value))}}
        if(comfort.rotateThemes){
            Text("The schedule takes priority when it is on.",style=MaterialTheme.typography.bodySmall)
            themes.forEach{(id,label)->PreferenceSwitch(label,id in comfort.favoriteThemes){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(favoriteThemes=if(value)it.comfort.favoriteThemes+id else it.comfort.favoriteThemes-id))}}}
        }
    }
    if(save)AlertDialog(onDismissRequest={save=false},title={Text("Name your palette")},text={OutlinedTextField(name,{name=it.take(40)},singleLine=true,label={Text("Name")})},confirmButton={TextButton(enabled=name.isNotBlank(),onClick={val preset=PalettePreset(java.util.UUID.randomUUID().toString(),name.trim(),palette.accentHue,palette.secondaryHue,palette.backgroundHue,palette.depth,palette.saturation);FeatureSettings.update{it.copy(appearance=it.appearance.copy(presets=(listOf(preset)+it.appearance.presets).take(40)))};save=false}){Text("Save")}},dismissButton={TextButton(onClick={save=false}){Text("Cancel")}})
}

@Composable fun ComfortSettings(){
    val options by FeatureSettings.state.collectAsState();val comfort=options.comfort
    StudioCard(Modifier.fillMaxWidth()){
        Text("Comfort & accessibility",style=MaterialTheme.typography.headlineSmall)
        PreferenceSlider("Text size",comfort.textScale,.8f..1.6f,label={"${(it*100).toInt()}%"}){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(textScale=value))}}
        PreferenceSwitch("Larger controls",comfort.largeControls){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(largeControls=value))}}
        PreferenceSwitch("High contrast",comfort.highContrast){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(highContrast=value))}}
        PreferenceSwitch("Controls within reach",comfort.oneHanded,"Keep player controls near the bottom."){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(oneHanded=value))}}
        PreferenceSwitch("Button haptics",comfort.haptics){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(haptics=value))}}
        PreferenceSwitch("Reduce motion",comfort.reduceMotion){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(reduceMotion=value))}}
        PreferenceSwitch("Battery saver",comfort.batterySaver,"Pause animated artwork and prepare less audio ahead."){value->FeatureSettings.update{it.copy(comfort=it.comfort.copy(batterySaver=value))}}
    }
}

@Composable fun PlayerAppearanceSettings(){
    val options by FeatureSettings.state.collectAsState();val player=options.player
    StudioCard(Modifier.fillMaxWidth()){
        Text("Your listening room",style=MaterialTheme.typography.headlineSmall)
        PreferenceChoice("Player layout",player.layout,listOf("STANDARD" to "Adaptive","FOCUS" to "Cover focus","DESK" to "Desk")){value->FeatureSettings.update{it.copy(player=it.player.copy(layout=value))}}
        PreferenceSlider("Artwork size",player.artScale,.45f..1.2f,label={"${(it*100).toInt()}%"}){value->FeatureSettings.update{it.copy(player=it.player.copy(artScale=value))}}
        PreferenceSwitch("Compact player",player.compact){value->FeatureSettings.update{it.copy(player=it.player.copy(compact=value))}}
        PreferenceSwitch("Song-colored backdrop",player.backdrop){value->FeatureSettings.update{it.copy(player=it.player.copy(backdrop=value))}}
        Text("Player controls",style=MaterialTheme.typography.titleMedium)
        listOf("shuffle" to "Shuffle","previous" to "Previous","next" to "Next","repeat" to "Repeat","lyrics" to "Lyrics","queue" to "Queue").forEach{(id,label)->PreferenceSwitch(label,id in player.controls){value->FeatureSettings.update{it.copy(player=it.player.copy(controls=if(value)it.player.controls+id else it.player.controls-id))}}}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun AppearancePage(){
    val context=LocalContext.current;var section by remember{mutableStateOf("styles")}
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        listOf("styles" to "Styles","custom" to "Custom colors","player" to "Player","comfort" to "Comfort","schedule" to "Schedules").forEach{(id,label)->StudioChip(label,section==id){section=id}}
    }
    when(section){
        "custom"->AppearanceExtras("custom")
        "player"->PlayerAppearanceSettings()
        "comfort"->ComfortSettings()
        "schedule"->AppearanceExtras("schedule")
        else->{
            PreferenceChoice("App layout",currentLayout.name,SpockLayout.entries.map{it.name to it.label}){value->currentLayout=SpockLayout.valueOf(value);context.getSharedPreferences("appearance",0).edit().putString("layout",value).apply()}
            SpockLook.entries.forEach{look->
                OutlinedButton(modifier=Modifier.fillMaxWidth(),onClick={FeatureSettings.update{it.copy(appearance=it.appearance.copy(custom=false),comfort=it.comfort.copy(dayTheme="",nightTheme="",rotateThemes=false))};currentLook=look;context.getSharedPreferences("appearance",0).edit().putString("theme",look.name).apply()}){
                    androidx.compose.foundation.layout.Box(Modifier.size(22.dp).then(Modifier.background(look.accent,androidx.compose.foundation.shape.CircleShape)))
                    Text(look.label,modifier=Modifier.weight(1f));if(currentLook==look&&!FeatureSettings.value.appearance.custom)Text("Selected")
                }
            }
        }
    }
}
