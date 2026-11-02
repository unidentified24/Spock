package io.github.unidentified24.spock.ui.components

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.unidentified24.spock.ui.theme.*

@Composable fun PreferenceSwitch(title:String,value:Boolean,detail:String="",change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)){Text(title,color=MaterialTheme.colorScheme.onSurface,style=MaterialTheme.typography.titleSmall);if(detail.isNotBlank())Text(detail,color=TextMuted,style=MaterialTheme.typography.bodySmall)}
        Switch(checked=value,onCheckedChange=change)
    }
}
@Composable fun PreferenceChoice(title:String,value:String,choices:List<Pair<String,String>>,change:(String)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){
            Column(Modifier.weight(1f)){Text(title,color=TextMuted,style=MaterialTheme.typography.labelMedium);Text(choices.firstOrNull{it.first==value}?.second ?: value)}
            Icon(Icons.Default.ExpandMore,null)
        }
        DropdownMenu(expanded,{expanded=false}){choices.forEach{(id,label)->DropdownMenuItem(text={Text(label)},onClick={change(id);expanded=false},trailingIcon={if(value==id)Icon(Icons.Default.Check,null)})}}
    }
}
@Composable fun PreferenceSlider(title:String,value:Float,range:ClosedFloatingPointRange<Float>,steps:Int=0,label:(Float)->String={"%.1f".format(it)},change:(Float)->Unit){
    var selected by remember(value){mutableFloatStateOf(value.coerceIn(range))}
    Column{Text("$title · ${label(selected)}",color=MaterialTheme.colorScheme.onSurface);Slider(selected,{selected=it},valueRange=range,steps=steps,onValueChangeFinished={change(selected)})}
}
@Composable fun PreferenceTime(title:String,minute:Int,change:(Int)->Unit){
    val context=LocalContext.current
    TextButton(onClick={TimePickerDialog(context,{_,hour,min->change(hour*60+min)},minute.coerceAtLeast(0)/60,minute.coerceAtLeast(0)%60,android.text.format.DateFormat.is24HourFormat(context)).show()}){
        Icon(Icons.Default.Schedule,null);Text(" $title · %02d:%02d".format(minute.coerceAtLeast(0)/60,minute.coerceAtLeast(0)%60))
    }
}
