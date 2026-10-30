package io.github.unidentified24.spock.ui.screens
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import io.github.unidentified24.spock.data.api.Response
import io.github.unidentified24.spock.data.entity.*
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.di.SongPlayer
import io.github.unidentified24.spock.ui.components.*
import io.github.unidentified24.spock.ui.navigation.*
import io.github.unidentified24.spock.ui.theme.*
import io.github.unidentified24.spock.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

private data class Story(val minutes:Long=0,val songs:Int=0,val artists:Int=0,val topSongs:List<Pair<String,Long>> = emptyList(),
    val topArtists:List<Pair<String,Long>> = emptyList(),val days:List<Pair<String,Long>> = emptyList())

@Composable
private fun listeningStory(days:Int?):Story {
    val context=LocalContext.current;var story by remember(days) {mutableStateOf(Story())}
    LaunchedEffect(days) {while(true){
        story=withContext(Dispatchers.IO) {
            val samples=ListeningMeter.samples(context)
            val start=days?.let {java.time.LocalDate.now().minusDays(it.toLong()-1)} ?: java.time.LocalDate.MIN
            val rows=samples.filter {it.date>=start}
            val total=rows.sumOf {it.ms}
            Story(total/60000,rows.map {it.songId}.distinct().size,rows.map {it.artist}.filter {it.isNotBlank()}.distinct().size,
                rows.groupBy {it.title+" · "+it.artist}.map {(n,r)->n to r.sumOf {it.ms}}.sortedByDescending {it.second}.take(8),
                rows.groupBy {it.artist}.filterKeys {it.isNotBlank()}.map {(n,r)->n to r.sumOf {it.ms}}.sortedByDescending {it.second}.take(6),
                (6 downTo 0).map {back-> val d=java.time.LocalDate.now().minusDays(back.toLong());d.dayOfWeek.name.take(3) to rows.filter {it.date==d}.sumOf {it.ms} })
        }
        delay(10000)
    }}
    return story
}

@Composable
fun AccountScreen(navController:NavController) {
    val context=LocalContext.current;val story=listeningStory(null)
    LaunchedEffect(Unit){io.github.unidentified24.spock.data.api.ProfileCache.ensure(context)}
    val name=io.github.unidentified24.spock.data.api.ProfileCache.name ?: "Your space"
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=180.dp)) {
        item{StudioHeader(navController,"Hello,\n${name.substringBefore(' ')}.",back=true)}
        item {
            StudioCard(Modifier.padding(horizontal=20.dp).fillMaxWidth()) {
                Text("YOUR LISTENING STORY",color=AppPalette,style=MaterialTheme.typography.labelSmall)
                Text(story.minutes.toString(),color=Color.White,style=MaterialTheme.typography.displayLarge)
                Text("minutes in your own world",color=TextMuted,style=MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    Text("${story.songs} tracks",color=SecondaryPalette);Text("${story.artists} artists",color=SecondaryPalette)
                }
                Button(onClick={navController.navigate(Routes.Stats.route)},shape=RoundedCornerShape(14.dp)) {Text("Explore your story");Icon(Icons.Default.NorthEast,null)}
            }
        }
        item{StudioSection("Your controls")}
        item {
            Column(Modifier.padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                StudioAction("Settings","Sound, appearance & updates",Icons.Default.Settings) {navController.navigate(Routes.Settings.route)}
                StudioAction("Listening history","Your recent rotation",Icons.Default.History) {navController.navigate(Routes.History.route)}
                StudioAction("Connected music","YouTube, Deezer & lossless setup",Icons.Default.GraphicEq) {navController.navigate(Routes.Connections.route)}
            }
        }
        item {StudioSection("Made for your music","Stats count playback on this device. Pauses and buffering don't add listening time.")}
        item {Text("Spock ${io.github.unidentified24.spock.BuildConfig.VERSION_NAME} · Interface ${io.github.unidentified24.spock.BuildConfig.UI_VERSION}",color=TextMuted,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(horizontal=20.dp))}
    }
}

@Composable
fun StatsScreen(navController:NavController) {
    var period by remember {mutableIntStateOf(1)};val story=listeningStory(listOf(7,30,null)[period])
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=180.dp)) {
        item {StudioHeader(navController,"This is\nyour rhythm.",back=true)}
        item {Row(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("7 days","30 days","All time").forEachIndexed{i,label->StudioChip(label,period==i){period=i}}}}
        item {
            StudioCard(Modifier.padding(20.dp).fillMaxWidth()) {
                Text("THE SOUNDTRACK SO FAR",color=AppPalette,style=MaterialTheme.typography.labelSmall)
                Text("${story.minutes}",color=Color.White,style=MaterialTheme.typography.displayLarge)
                Text("minutes listened",color=TextMuted,style=MaterialTheme.typography.titleMedium)
                Text("${story.songs} different tracks · ${story.artists} artists",color=SecondaryPalette)
                if(story.topArtists.isNotEmpty()) {
                    HorizontalDivider(color=OutlineSubtle)
                    Text("ON REPEAT",color=AppPalette,style=MaterialTheme.typography.labelSmall)
                    Text(story.topArtists.first().first,color=Color.White,style=MaterialTheme.typography.headlineMedium)
                    Text("Your most listened artist in this period",color=TextMuted,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {StudioSection("Your week in sound","Actual listening across the last seven days")}
        item {
            StudioCard(Modifier.padding(horizontal=20.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().height(132.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Bottom) {
                    val max=(story.days.maxOfOrNull{it.second} ?: 1L).coerceAtLeast(1)
                    story.days.forEach {(day,ms)->Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height((90f*ms/max).coerceAtLeast(4f).dp).clip(RoundedCornerShape(7.dp)).background(if(ms>0)AppPalette else SurfaceVariant))
                        Text(day,color=TextMuted,style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(top=10.dp))
                    }}
                }
            }
        }
        item {StudioSection("The artists you return to")}
        itemsIndexed(story.topArtists) {i,pair->StoryRank(i,pair.first,pair.second,story.topArtists.first().second)}
        item {StudioSection("Your heavy rotation")}
        itemsIndexed(story.topSongs) {i,pair->StoryRank(i,pair.first,pair.second,story.topSongs.first().second)}
        if(story.songs==0) item {StudioEmpty("Your story starts with a song","Listen to some music and your own rankings will appear here. No made-up stats.")}
    }
}
@Composable private fun StoryRank(index:Int,name:String,ms:Long,max:Long) {
    Row(Modifier.padding(horizontal=20.dp,vertical=5.dp).fillMaxWidth().clip(StudioShape).background(GridBackground).padding(16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("%02d".format(index+1),color=AppPalette,style=MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(name,color=Color.White,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
            LinearProgressIndicator(progress={(ms.toFloat()/max.coerceAtLeast(1)).coerceIn(0f,1f)},color=AppPalette,trackColor=SurfaceVariant,modifier=Modifier.fillMaxWidth().height(3.dp))
        }
        Text(if(ms<60000) "<1 min" else "${ms/60000} min",color=TextMuted,style=MaterialTheme.typography.bodySmall)
    }
}
