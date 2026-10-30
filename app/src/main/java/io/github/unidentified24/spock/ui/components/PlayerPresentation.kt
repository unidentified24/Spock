package io.github.unidentified24.spock.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import io.github.unidentified24.spock.ui.theme.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

enum class PlayerView(val label:String){ARTWORK("Artwork"),DISC("Record"),WAVES("Sound waves"),SPECTRUM("Spectrum"),ORBIT("Orbit")}
private data class TrackArt(val bitmap:Bitmap,val primary:Color,val secondary:Color)

@Composable fun PlayerPresentation(title:String,cover:String,playing:Boolean,maxArtSize:Dp=440.dp) {
    val context=LocalContext.current.applicationContext
    var view by remember {mutableStateOf(PlayerView.entries.firstOrNull{it.name==context.getSharedPreferences("appearance",0).getString("player_view",null)} ?: PlayerView.ARTWORK)}
    var expanded by remember {mutableStateOf(false)}
    var art by remember(cover){mutableStateOf<TrackArt?>(null)}
    // One small cached image per cover; palette extraction never runs on the UI/audio thread.
    LaunchedEffect(cover) {
        if(cover.isNotBlank()) art=withContext(Dispatchers.IO){
            val request=Glide.with(context).asBitmap().load(cover).submit(512,512)
            try {
                val bitmap=request.get(5,TimeUnit.SECONDS).copy(Bitmap.Config.ARGB_8888,false)
                val palette=Palette.from(bitmap).maximumColorCount(12).generate()
                TrackArt(bitmap,Color(palette.getVibrantColor(palette.getDominantColor(0xFF7D9CDE.toInt()))),Color(palette.getLightMutedColor(0xFFE3CEAB.toInt())))
            } catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){null}
            finally{Glide.with(context).clear(request)}
        }
    }
    val primary=art?.primary ?: AppPalette
    val secondary=art?.secondary ?: SecondaryPalette
    var audible by remember{mutableStateOf(false)}
    LaunchedEffect(playing,view){audible=false;if(playing && view!=PlayerView.ARTWORK)while(isActive){audible=io.github.unidentified24.spock.di.SongPlayer.isPlaying();delay(500)}}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
            Box {
                TextButton(onClick={expanded=true}){Text("${view.label} ▾")}
                DropdownMenu(expanded,onDismissRequest={expanded=false}){
                    PlayerView.entries.forEach {choice->DropdownMenuItem(text={Text(choice.label+(if(view==choice)" ✓" else ""))},onClick={view=choice;expanded=false;context.getSharedPreferences("appearance",0).edit().putString("player_view",choice.name).apply()})}
                }
            }
        }
        Box(Modifier.align(Alignment.CenterHorizontally).widthIn(max=maxArtSize).fillMaxWidth().aspectRatio(1f).clip(StudioShape).background(Brush.linearGradient(listOf(primary.copy(alpha=.28f),GridBackground,secondary.copy(alpha=.18f)))),contentAlignment=Alignment.Center) {
            if(view==PlayerView.ARTWORK) {
                StudioCover(cover,Modifier.fillMaxSize().padding(12.dp),title)
            } else {
                art?.let{Image(it.bitmap.asImageBitmap(),null,Modifier.fillMaxSize().blur(38.dp).alpha(.23f),contentScale=ContentScale.Crop)}
                AmbientPlayer(view,title,cover,audible,art,primary,secondary)
                if(view==PlayerView.WAVES || view==PlayerView.SPECTRUM) {
                    Box(Modifier.align(Alignment.TopStart).padding(18.dp)) {StudioCover(cover,Modifier.size(60.dp),title)}
                }
            }
        }
    }
}

@Composable private fun AmbientPlayer(view:PlayerView,title:String,cover:String,playing:Boolean,art:TrackArt?,accent:Color,secondary:Color) {
    var seconds by remember(cover,title){mutableFloatStateOf(0f)}
    LaunchedEffect(playing) {
        if(playing){var last=0L;while(isActive){withFrameNanos{now->if(last==0L)last=now else if(now-last>=33_000_000L){seconds=(seconds+(now-last)/1_000_000_000f)%3600f;last=now}}}}
    }
    val seed=remember(cover,title){((cover+title).hashCode().toLong().and(0x7fffffffL)%1000)/1000f}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(24.dp)) {
            val t=seconds;val cx=size.width/2;val cy=size.height/2;val radius=min(cx,cy)*.91f
            when(view) {
                PlayerView.DISC -> {
                    drawCircle(Color(0xFF101014),radius)
                    for(i in 1..32)drawCircle(accent.copy(alpha=if(i%3==0).27f else .10f),radius*i/33,style=Stroke(1.2f))
                    rotate(t*24f){drawArc(secondary.copy(alpha=.23f),-40f,65f,false,topLeft=Offset(cx-radius,cy-radius),size=androidx.compose.ui.geometry.Size(radius*2,radius*2),style=Stroke(radius*.55f))}
                }
                PlayerView.WAVES -> for(line in 0..5){
                    val path=Path();val amplitude=radius*(.12f+line*.055f)
                    for(x in 0..140){val px=size.width*x/140;val envelope=sin(PI*x/140).toFloat();val y=cy+sin(x*(.045f+seed*.06f)+t*2+line*.55f)*amplitude*envelope
                        if(x==0)path.moveTo(px,y)else path.lineTo(px,y)}
                    drawPath(path,lerp(accent,secondary,line/5f).copy(alpha=.45f+line*.08f),style=Stroke(3f,cap=StrokeCap.Round))
                }
                PlayerView.SPECTRUM -> {
                    val bars=28;val step=size.width/bars
                    for(i in 0 until bars){
                        val energy=(sin(i*(.43+seed*.4)+t*2.4)+sin(i*.24-t*1.1)+2)/4
                        val h=(radius*(.13+energy*1.45)).toFloat()
                        drawRoundRect(lerp(accent,secondary,i.toFloat()/bars),Offset(i*step+step*.16f,cy-h/2),androidx.compose.ui.geometry.Size(step*.58f,h),androidx.compose.ui.geometry.CornerRadius(step*.3f))
                    }
                }
                PlayerView.ORBIT -> {
                    for(i in 1..3)drawCircle(accent.copy(alpha=.22f),radius*i/3,style=Stroke(1.5f))
                    for(i in 0..8){val angle=t*(.3+i*.025)+i*PI/4+seed*PI;val r=radius*(.4+(i%3)*.28)
                        drawCircle(if(i%2==0)accent else secondary,4f+i%3*2f,Offset(cx+(cos(angle)*r).toFloat(),cy+(sin(angle)*r).toFloat()))}
                }
                else -> Unit
            }
        }
        if(view==PlayerView.DISC || view==PlayerView.ORBIT) {
            BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                val diameter=maxWidth*(if(view==PlayerView.DISC).34f else .28f)
                if(art!=null)Image(art.bitmap.asImageBitmap(),title,Modifier.size(diameter).rotate(if(view==PlayerView.DISC)seconds*24 else 0f).clip(CircleShape),contentScale=ContentScale.Crop)
                else StudioCover(cover,Modifier.size(diameter).clip(CircleShape),title)
                if(view==PlayerView.DISC)Box(Modifier.size(8.dp).clip(CircleShape).background(GridBackground))
            }
        }
    }
}
