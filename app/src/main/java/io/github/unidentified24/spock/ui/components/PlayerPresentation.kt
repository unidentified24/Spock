package io.github.unidentified24.spock.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.unidentified24.spock.ui.theme.*
import kotlinx.coroutines.isActive
import kotlin.math.*

enum class PlayerView(val label:String){ARTWORK("Artwork"),DISC("Record"),WAVES("Sound waves"),SPECTRUM("Spectrum"),ORBIT("Orbit")}

@Composable fun PlayerPresentation(title:String,cover:String,playing:Boolean) {
    val context=LocalContext.current
    var view by remember {mutableStateOf(PlayerView.entries.firstOrNull{it.name==context.getSharedPreferences("appearance",0).getString("player_view",null)} ?: PlayerView.ARTWORK)}
    var expanded by remember {mutableStateOf(false)}
    var audible by remember{mutableStateOf(false)}
    LaunchedEffect(playing){audible=false;if(playing)while(isActive){audible=io.github.unidentified24.spock.di.SongPlayer.isPlaying();kotlinx.coroutines.delay(500)}}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("PLAYER VIEW",color=TextMuted,style=MaterialTheme.typography.labelSmall)
            Box {
                TextButton(onClick={expanded=true}){Text("${view.label} ▾")}
                DropdownMenu(expanded,onDismissRequest={expanded=false}){
                    PlayerView.entries.forEach {choice->DropdownMenuItem(text={Text(choice.label+(if(view==choice)" ✓" else ""))},onClick={view=choice;expanded=false;context.getSharedPreferences("appearance",0).edit().putString("player_view",choice.name).apply()})}
                }
            }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1.12f).clip(StudioShape).background(GridBackground),contentAlignment=Alignment.Center) {
            if(view==PlayerView.ARTWORK)StudioCover(cover,Modifier.fillMaxSize().padding(16.dp))
            else AmbientPlayer(view,title,audible)
        }
        if(view!=PlayerView.ARTWORK)Text("Animated artwork · follows play and pause",color=TextMuted,style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun AmbientPlayer(view:PlayerView,title:String,playing:Boolean) {
    var seconds by remember {mutableFloatStateOf(0f)}
    LaunchedEffect(playing) {
        if(playing){var last=0L;while(isActive){withFrameNanos{now->if(last!=0L)seconds=(seconds+(now-last)/1_000_000_000f)%3600f;last=now}}}
    }
    val trackHue=(title.hashCode().toLong().and(0x7fffffffL)%360).toFloat()
    val trackColor=Color.hsv(trackHue,.42f,.95f)
    val accent=lerp(AppPalette,trackColor,.45f)
    val secondary=lerp(SecondaryPalette,trackColor,.25f)
    Canvas(Modifier.fillMaxSize().padding(24.dp)) {
        val t=seconds;val cx=size.width/2;val cy=size.height/2;val radius=min(cx,cy)*.91f
        when(view) {
            PlayerView.DISC -> {
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha=.20f),Color(0xFF101014))),radius)
                for(i in 1..16)drawCircle(accent.copy(alpha=if(i%3==0).24f else .10f),radius*i/17,style=Stroke(1.2f))
                rotate(t*24f){
                    drawArc(secondary.copy(alpha=.23f),-40f,65f,false,topLeft=Offset(cx-radius,cy-radius),size=androidx.compose.ui.geometry.Size(radius*2,radius*2),style=Stroke(radius*.65f))
                    drawCircle(accent,radius*.31f)
                    drawLine(GridBackground,Offset(cx,cy-radius*.26f),Offset(cx,cy+radius*.26f),radius*.045f)
                }
                drawCircle(GridBackground,radius*.055f)
            }
            PlayerView.WAVES -> {
                for(line in 0..4){
                    val path=Path();val amplitude=radius*(.12f+line*.055f)
                    for(x in 0..140){val px=size.width*x/140;val envelope=sin(PI*x/140).toFloat();val y=cy+sin(x*.075f+t*2+line*.55f)*amplitude*envelope
                        if(x==0)path.moveTo(px,y)else path.lineTo(px,y)}
                    drawPath(path,lerp(accent,secondary,line/4f).copy(alpha=.45f+line*.1f),style=Stroke(2.6f,cap=StrokeCap.Round))
                }
            }
            PlayerView.SPECTRUM -> {
                val bars=28;val step=size.width/bars
                for(i in 0 until bars){
                    val energy=(sin(i*.63+t*2.4)+sin(i*.24-t*1.1)+2)/4
                    val h=(radius*(.13+energy*1.45)).toFloat()
                    drawRoundRect(lerp(accent,secondary,i.toFloat()/bars),Offset(i*step+step*.16f,cy-h/2),androidx.compose.ui.geometry.Size(step*.58f,h),androidx.compose.ui.geometry.CornerRadius(step*.3f))
                }
            }
            PlayerView.ORBIT -> {
                for(i in 1..3)drawCircle(accent.copy(alpha=.15f),radius*i/3,style=Stroke(1.5f))
                for(i in 0..8){val angle=t*(.3+i*.025)+i*PI/4;val r=radius*(.4+(i%3)*.28)
                    drawCircle(if(i%2==0)accent else secondary,4f+i%3*2f,Offset(cx+(cos(angle)*r).toFloat(),cy+(sin(angle)*r).toFloat()))}
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha=.6f),accent.copy(alpha=.02f))),radius*.34f)
            }
            else -> Unit
        }
    }
    if(view==PlayerView.ORBIT)Text(title.trim().take(1).uppercase().ifBlank{"S"},color=Color.White,style=MaterialTheme.typography.headlineMedium)
}
