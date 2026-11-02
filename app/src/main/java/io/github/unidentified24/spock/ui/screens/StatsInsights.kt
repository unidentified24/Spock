package io.github.unidentified24.spock.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.data.preferences.*
import io.github.unidentified24.spock.features.ShareFiles
import io.github.unidentified24.spock.features.downloads.TrackCatalog
import io.github.unidentified24.spock.features.stats.*
import io.github.unidentified24.spock.ui.components.*
import io.github.unidentified24.spock.ui.theme.*
import kotlinx.coroutines.*
import java.time.*

@Composable fun StatsInsights(){
    val error by ListeningMeter.saveError.collectAsState()
    val context=LocalContext.current.applicationContext;val scope=rememberCoroutineScope();val today=LocalDate.now()
    var rows by remember{mutableStateOf<List<ListeningSample>>(emptyList())};var catalog by remember{mutableStateOf<List<SongsModel>>(emptyList())}
    var period by remember{mutableStateOf("month:"+YearMonth.now())};var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    var selectedDay by remember{mutableStateOf(today)};var confirmDeleteDay by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){while(isActive){withContext(Dispatchers.IO){ListeningMeter.samples(context) to (TrackCatalog.read(context)+getLocalSongs(context)+getDownloadedSongs(context)).distinctBy{it.id}}.let{rows=it.first;catalog=it.second};delay(15000)}}
    val months=(0..23).map{YearMonth.now().minusMonths(it.toLong())}
    val years=(listOf(today.year)+(rows.map{it.date.year})).distinct().sortedDescending()
    val choices=months.map{"month:$it" to it.month.name.lowercase().replaceFirstChar{c->c.uppercase()}+" "+it.year}+years.map{"year:$it" to "$it · yearly recap"}
    val start=if(period.startsWith("month:"))YearMonth.parse(period.substringAfter(':')).atDay(1)else LocalDate.of(period.substringAfter(':').toInt(),1,1)
    val end=if(period.startsWith("month:"))YearMonth.parse(period.substringAfter(':')).atEndOfMonth()else start.withMonth(12).withDayOfMonth(31)
    val selected=remember(rows,start,end){rows.filter{it.date>=start&&it.date<=end}}
    val recap=remember(selected,catalog){ListeningInsights.recap(selected,catalog)};val streak=remember(rows,today){ListeningInsights.streaks(rows,today)}
    val title=choices.firstOrNull{it.first==period}?.second.orEmpty()
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->if(uri!=null)scope.launch{
        try{withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri,"wt")!!.use{it.write(ListeningInsights.csv(rows).toByteArray(Charsets.UTF_8))}};message="Statistics exported"}
        catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){message="Couldn't export statistics. Try a different location."}
    }}
    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
        StudioCard(Modifier.fillMaxWidth()){
            Text("Your recaps",style=MaterialTheme.typography.headlineSmall)
            PreferenceChoice("Period",period,choices){period=it}
            Text("${recap.ms/60000} minutes",style=MaterialTheme.typography.displaySmall)
            Text("${recap.tracks} tracks · ${recap.artists} artists",color=SecondaryPalette)
            if(recap.topArtist.isNotBlank())Text("Top artist · ${recap.topArtist}")
            if(recap.topTrack.isNotBlank())Text("Top track · ${recap.topTrack}")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(enabled=!busy,onClick={busy=true;val accent=AppPalette.toArgb();val background=AppBackground.toArgb();scope.launch{
                    try{val file=withContext(Dispatchers.IO){ShareFiles.card(context,"My listening story",title,listOf("${recap.ms/60000} minutes listened","${recap.tracks} tracks · ${recap.artists} artists",recap.topArtist.takeIf{it.isNotBlank()}?.let{"Top artist · $it"}.orEmpty(),recap.topTrack.takeIf{it.isNotBlank()}?.let{"On repeat · $it"}.orEmpty()).filter{it.isNotBlank()},accent,background)};ShareFiles.share(context,file,"image/png")}
                    catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){message="Couldn't create the listening card."}finally{busy=false}
                }}){Text("Share card")}
                TextButton(onClick={export.launch("Spock-listening-${today}.csv")}){Text("Export CSV")}
            }
        }
        StudioCard(Modifier.fillMaxWidth()){
            Text("${streak.first} day streak",style=MaterialTheme.typography.headlineSmall)
            Text("Longest streak · ${streak.second} days",color=TextMuted)
            ListeningCalendar(rows,today,selectedDay){selectedDay=it}
            if(rows.any{it.date==selectedDay})TextButton(onClick={confirmDeleteDay=true}){Text("Remove ${selectedDay} from history")}
        }
        val replays=remember(selected){ListeningInsights.replays(selected)}
        if(replays.isNotEmpty())StudioCard(Modifier.fillMaxWidth()){
            Text("Most replayed",style=MaterialTheme.typography.headlineSmall)
            replays.take(8).forEach{(name,count)->Text("$name · $count ${if(count==1)"play" else "plays"}",style=MaterialTheme.typography.bodyMedium)}
        }
        val discoveries=remember(rows,catalog){ListeningInsights.discoveries(rows,catalog)}
        if(discoveries.isNotEmpty())StudioCard(Modifier.fillMaxWidth()){
            Text("Your artist timeline",style=MaterialTheme.typography.headlineSmall)
            discoveries.take(10).forEach{Text(it.artist,style=MaterialTheme.typography.titleMedium);Text("First heard in Spock · ${it.date}",color=TextMuted,style=MaterialTheme.typography.bodySmall)}
        }
        val albums=remember(rows,catalog){ListeningInsights.albums(rows,catalog)}
        if(albums.isNotEmpty())StudioCard(Modifier.fillMaxWidth()){
            Text("Album progress",style=MaterialTheme.typography.headlineSmall)
            albums.take(10).forEach{album->Text(album.name,style=MaterialTheme.typography.titleMedium);Text(if(album.total>0)"${album.heard} of ${album.total} tracks heard" else "${album.heard} tracks heard",color=TextMuted);if(album.total>0)LinearProgressIndicator(progress={(album.heard.toFloat()/album.total).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())}
        }
        val genres=remember(selected,catalog){ListeningInsights.genres(selected,catalog)}
        if(genres.isNotEmpty())StudioCard(Modifier.fillMaxWidth()){
            Text("Genre trends",style=MaterialTheme.typography.headlineSmall)
            val previousStart=if(period.startsWith("month:"))start.minusMonths(1)else start.minusYears(1)
            val previousEnd=start.minusDays(1)
            val previous=ListeningInsights.genres(rows.filter{it.date>=previousStart&&it.date<=previousEnd},catalog).toMap()
            genres.take(8).forEach{(genre,ms)->val old=previous[genre] ?: 0;Text("$genre · ${ms/60000} min · "+if(old<=0)"new this period" else "${if(ms>=old)"+" else ""}${((ms-old)*100/old)}%",style=MaterialTheme.typography.bodyMedium)}
        }
        if(message.isNotBlank())Text(message,color=TextMuted)
    }
    if(confirmDeleteDay)AlertDialog(onDismissRequest={confirmDeleteDay=false},title={Text("Remove this day's listening?")},text={Text("The saved listening time and play counts for $selectedDay will be removed.")},confirmButton={TextButton(onClick={confirmDeleteDay=false;scope.launch{runCatching{withContext(Dispatchers.IO){ListeningMeter.delete(context,start=selectedDay,end=selectedDay)}}.onFailure{message="Couldn't remove this day from history."}}}){Text("Remove day")}},dismissButton={TextButton(onClick={confirmDeleteDay=false}){Text("Cancel")}})
}

@Composable private fun ListeningCalendar(rows:List<ListeningSample>,today:LocalDate,day:LocalDate,onSelect:(LocalDate)->Unit){
    val totals=remember(rows){rows.groupBy{it.date}.mapValues{(_,samples)->samples.sumOf{it.ms}}}
    val start=remember(today){today.minusDays(364).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))}
    val weeks=((java.time.temporal.ChronoUnit.DAYS.between(start,today)+7)/7).toInt()
    val accent=AppPalette;val empty=SurfaceVariant
    val total=totals.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column{
        Canvas(Modifier.fillMaxWidth().height(110.dp).semantics{contentDescription="Listening calendar for the past year. ${totals.filterKeys{it>=start&&it<=today}.count{it.value>0}} active days.";onClick("Select $day"){onSelect(day);true}}.pointerInput(start,weeks){detectTapGestures{point->val cell=size.width.toFloat()/weeks;val column=(point.x/cell).toInt().coerceIn(0,weeks-1);val row=(point.y/(size.height/7f)).toInt().coerceIn(0,6);onSelect(start.plusDays((column*7+row).toLong()).coerceAtMost(today))}}){
            val width=size.width/weeks;val height=size.height/7
            repeat(weeks){column->repeat(7){row->val date=start.plusDays((column*7+row).toLong());if(date<=today){val ms=totals[date] ?: 0;drawRect(if(ms==0L)empty else accent.copy(alpha=(.25f+.75f*ms/total).coerceIn(.25f,1f)),Offset(column*width,row*height),Size((width-2).coerceAtLeast(1f),(height-2).coerceAtLeast(1f)))}}}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            TextButton(onClick={onSelect(day.minusDays(1).coerceAtLeast(start))}){Text("Previous day")}
            TextButton(onClick={onSelect(day.plusDays(1).coerceAtMost(today))}){Text("Next day")}
        }
        Text("$day · ${(totals[day] ?: 0)/60000} minutes",color=TextMuted,style=MaterialTheme.typography.bodySmall)
    }
}
