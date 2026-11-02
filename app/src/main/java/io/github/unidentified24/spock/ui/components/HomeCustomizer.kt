package io.github.unidentified24.spock.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import io.github.unidentified24.spock.features.FeatureSettings

val homeSectionNames=mapOf("continue" to "Continue listening","pinned" to "Pinned playlists","shortcuts" to "Your shortcuts","fresh" to "Recently added","forgotten" to "Forgotten favorites","unfinished" to "Unfinished albums","recommendations" to "Recommendations")
val homeShortcutNames=mapOf("collection" to "Collection","downloads" to "Downloads","history" to "History","local" to "On device","stats" to "Statistics","queue" to "Queue","account" to "Account","search" to "Explore","mixes" to "Mixes")

@Composable fun HomeCustomizer(dismiss:()->Unit){
    val options by FeatureSettings.state.collectAsState()
    AlertDialog(onDismissRequest=dismiss,title={Text("Make home yours")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())){
        options.home.order.forEachIndexed{index,id->Row(verticalAlignment=Alignment.CenterVertically){
            Checkbox(id !in options.home.hidden,{show->FeatureSettings.update{it.copy(home=it.home.copy(hidden=if(show)it.home.hidden-id else it.home.hidden+id))}})
            Text(homeSectionNames[id] ?: id,modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
            IconButton(enabled=index>0,onClick={FeatureSettings.update{val order=it.home.order.toMutableList();val position=order.indexOf(id);if(position>0)order.add(position-1,order.removeAt(position));it.copy(home=it.home.copy(order=order))}}){Icon(Icons.Default.KeyboardArrowUp,"Move ${homeSectionNames[id]} up")}
            IconButton(enabled=index<options.home.order.lastIndex,onClick={FeatureSettings.update{val order=it.home.order.toMutableList();val position=order.indexOf(id);if(position in 0 until order.lastIndex)order.add(position+1,order.removeAt(position));it.copy(home=it.home.copy(order=order))}}){Icon(Icons.Default.KeyboardArrowDown,"Move ${homeSectionNames[id]} down")}
        }}
        Text("Shortcuts",style=MaterialTheme.typography.titleLarge)
        homeShortcutNames.forEach{(id,name)->PreferenceSwitch(name,id in options.home.shortcuts){show->FeatureSettings.update{it.copy(home=it.home.copy(shortcuts=if(show)it.home.shortcuts+id else it.home.shortcuts-id))}}}
    }},confirmButton={TextButton(onClick=dismiss){Text("Done")}})
}
