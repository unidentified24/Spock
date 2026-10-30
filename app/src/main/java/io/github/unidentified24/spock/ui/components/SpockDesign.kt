package io.github.unidentified24.spock.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.bumptech.glide.integration.compose.*
import io.github.unidentified24.spock.R
import io.github.unidentified24.spock.data.api.ProfileCache
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.di.SongPlayer
import io.github.unidentified24.spock.ui.navigation.Routes
import io.github.unidentified24.spock.ui.theme.*
import io.github.unidentified24.spock.ui.viewmodel.PlayerViewModel

val StudioShape get() = RoundedCornerShape(if(currentLayout==SpockLayout.COMPACT)12.dp else 20.dp)

@Composable
fun SpockMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.spock_mark), "Spock", modifier.clip(RoundedCornerShape(12.dp)), contentScale=ContentScale.Crop)
}

@Composable
fun StudioIcon(icon: ImageVector, label: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick=onClick, modifier=modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
        .background(if(selected) AppPalette else GridBackground)) {
        Icon(icon, label, tint=if(selected) AppBackground else Color.White, modifier=Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AccountButton(nav: NavController) {
    val entry by nav.currentBackStackEntryAsState()
    if(entry?.destination?.route==Routes.Account.route || entry?.destination?.route==Routes.Connections.route || entry?.destination?.route?.startsWith(Routes.Settings.route)==true)return
    val context=LocalContext.current
    LaunchedEffect(Unit) { ProfileCache.ensure(context) }
    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(GridBackground).clickable { nav.navigate(Routes.Account.route){launchSingleTop=true} }
        .padding(start=8.dp,end=12.dp,top=8.dp,bottom=8.dp), verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        val url=ProfileCache.imageUrl
        if(!url.isNullOrBlank()) GlideImage(model=url, contentDescription=null,contentScale=ContentScale.Crop,
            modifier=Modifier.size(28.dp).clip(RoundedCornerShape(10.dp)))
        else Icon(Icons.Default.Person,null,tint=AppPalette,modifier=Modifier.size(28.dp))
        Text("Account",style=MaterialTheme.typography.labelMedium,color=Color.White)
    }
}

@Composable
fun StudioHeader(nav: NavController, title: String, eyebrow: String = "SPOCK", back: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=if(currentLayout==SpockLayout.COMPACT)8.dp else 12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            if(back) StudioIcon(Icons.AutoMirrored.Filled.ArrowBack,"Back") { nav.navigateUp() }
            else Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                SpockMark(Modifier.size(36.dp)); Text(eyebrow,style=MaterialTheme.typography.labelSmall,color=AppPalette)
            }
            AccountButton(nav)
        }
        Spacer(Modifier.height(if(currentLayout==SpockLayout.COMPACT)10.dp else 22.dp))
        Text(title,style=if(currentLayout==SpockLayout.COMPACT)MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,color=Color.White,maxLines=3,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
fun StudioSection(title: String, caption: String? = null) {
    Column(Modifier.padding(20.dp,22.dp,20.dp,12.dp)) {
        Text(title,style=MaterialTheme.typography.titleLarge,color=Color.White)
        caption?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=TextMuted,modifier=Modifier.padding(top=4.dp)) }
    }
}

@Composable
fun StudioCard(modifier: Modifier=Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(StudioShape).background(GridBackground).border(1.dp,OutlineSubtle,StudioShape).padding(if(currentLayout==SpockLayout.COMPACT)12.dp else 18.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
}

@Composable
fun StudioAction(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(StudioShape).background(GridBackground).clickable(onClick=onClick).padding(16.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Icon(icon,null,tint=AppPalette,modifier=Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title,color=Color.White,style=MaterialTheme.typography.titleSmall)
            Text(subtitle,color=TextMuted,style=MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.ChevronRight,null,tint=TextMuted)
    }
}

@Composable
fun StudioEmpty(title: String, description: String) {
    StudioCard(Modifier.padding(20.dp).fillMaxWidth()) {
        Icon(Icons.Default.GraphicEq,null,tint=AppPalette,modifier=Modifier.size(32.dp))
        Text(title,color=Color.White,style=MaterialTheme.typography.titleMedium)
        Text(description,color=TextMuted,style=MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun StudioCover(url: String, modifier: Modifier=Modifier, label: String?=null) {
    Box(modifier.clip(StudioShape).background(SurfaceVariant),contentAlignment=Alignment.Center) {
        if(url.isBlank()) Icon(Icons.Default.Album,label,tint=AppPalette,modifier=Modifier.size(40.dp))
        else GlideImage(model=url,contentDescription=label,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize(),
            failure=placeholder(R.drawable.placeholder),loading=placeholder(R.drawable.placeholder))
    }
}

@Composable
fun StudioChip(label: String, selected: Boolean=false, onClick: () -> Unit) {
    TextButton(onClick=onClick,shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.textButtonColors(
        containerColor=if(selected) AppPalette else GridBackground,contentColor=if(selected) AppBackground else TextMuted)) {
        Text(label,style=MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun StudioMediaRow(title:String,subtitle:String,cover:String,onClick:()->Unit) {
    Row(Modifier.padding(horizontal=20.dp,vertical=5.dp).fillMaxWidth().clip(StudioShape).background(GridBackground)
        .clickable(onClick=onClick).padding(10.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        StudioCover(cover,Modifier.size(64.dp))
        Column(Modifier.weight(1f)) {
            Text(title,color=Color.White,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(subtitle,color=TextMuted,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight,null,tint=TextMuted)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudioTrack(song:SongsModel,index:Int,active:Boolean,onPlay:()->Unit,onMenu:()->Unit) {
    Row(Modifier.padding(horizontal=20.dp,vertical=4.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(if(active) AppPalette.copy(alpha=.12f) else GridBackground)
        .combinedClickable(onClick=onPlay,onLongClick=onMenu).padding(10.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(active) "•" else "%02d".format(index+1),color=AppPalette,style=MaterialTheme.typography.labelMedium,modifier=Modifier.width(25.dp))
        StudioCover(song.coverUri,Modifier.size(46.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title,color=if(active) AppPalette else Color.White,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(song.singer+(if(song.explicit) " · E" else ""),color=TextMuted,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        IconButton(onClick=onMenu) { Icon(Icons.Default.MoreHoriz,"Song options",tint=TextMuted) }
    }
}

fun playStudioTrack(vm:PlayerViewModel, songs:List<SongsModel>, index:Int, context:android.content.Context) {
    val song=songs.getOrNull(index) ?: return
    vm.updateQueue(songs)
    vm.updateSongState(song.coverUri,song.title,song.singer,true,song.id,index,song.album)
    SongPlayer.playSong(song.url,context)
}

@Composable
fun StudioCollection(nav:NavController,title:String,subtitle:String,cover:String,songs:List<SongsModel>,loading:Boolean=false,
    extra: @Composable () -> Unit = {},onMenuDismiss:(SongsModel)->Unit={}) {
    val vm:PlayerViewModel=hiltViewModel(); val context=LocalContext.current
    var menu by remember { mutableStateOf<SongsModel?>(null) }
    var sort by remember { mutableIntStateOf(0) }
    val display=remember(songs,sort) { if(sort==1) songs.sortedBy { it.title.lowercase() } else songs }
    menu?.let { song -> SongOptionsSheet(song,nav,context,onDismiss={ onMenuDismiss(song);menu=null }) }
    LazyColumn(Modifier.fillMaxSize().background(AppBackground).statusBarsPadding(),contentPadding=PaddingValues(bottom=180.dp)) {
        item { StudioHeader(nav,title,back=true) }
        item {
            StudioCard(Modifier.padding(horizontal=20.dp).fillMaxWidth()) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                    StudioCover(cover,Modifier.size(104.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(subtitle,color=TextMuted,style=MaterialTheme.typography.bodyMedium,maxLines=4,overflow=TextOverflow.Ellipsis)
                        Text("${songs.size} TRACKS",color=AppPalette,style=MaterialTheme.typography.labelSmall)
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Button(onClick={ playStudioTrack(vm,display,0,context) },enabled=display.isNotEmpty(),shape=RoundedCornerShape(14.dp),modifier=Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow,null); Text("Listen")
                    }
                    OutlinedButton(onClick={ playStudioTrack(vm,display.shuffled(),0,context) },enabled=display.isNotEmpty(),shape=RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.Shuffle,"Shuffle")
                    }
                }
                extra()
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(20.dp,18.dp,20.dp,8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Tracklist",color=Color.White,style=MaterialTheme.typography.titleLarge)
                StudioChip(if(sort==0) "In order" else "Title A–Z") { sort=1-sort }
            }
        }
        if(loading) item { Box(Modifier.fillMaxWidth().padding(30.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator() } }
        else if(display.isEmpty()) item { StudioEmpty("No tracks here yet","Try refreshing your connection or add some music to this collection.") }
        itemsIndexed(display,key={i,s->"${s.id}:$i"}) { i,song ->
            StudioTrack(song,i,vm.currentSongId.value==song.id,{ playStudioTrack(vm,display,i,context) },{ menu=song })
        }
    }
}

@Composable
fun SpockPageTitle(title:String,subtitle:String,modifier:Modifier=Modifier) {
    Column(modifier.padding(20.dp,20.dp)) {
        Text(title,color=Color.White,style=MaterialTheme.typography.headlineLarge)
        Text(subtitle,color=TextMuted,style=MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SpockWelcome(onConnect:()->Unit,onLocal:()->Unit) {
    Column(Modifier.fillMaxSize().background(AppBackground).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(26.dp),
        verticalArrangement=Arrangement.spacedBy(26.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            SpockMark(Modifier.size(44.dp)); Text("SPOCK / MUSIC",color=AppPalette,style=MaterialTheme.typography.labelLarge)
        }
        SpockMark(Modifier.fillMaxWidth().aspectRatio(1.35f))
        Text("Make room\nfor your sound.",color=Color.White,style=MaterialTheme.typography.displayMedium)
        Text("Your Spotify collection. More ways to listen. A space that feels like you.",color=TextMuted,style=MaterialTheme.typography.bodyLarge)
        Button(onClick=onConnect,modifier=Modifier.fillMaxWidth().height(58.dp),shape=RoundedCornerShape(16.dp)) { Text("Connect Spotify") }
        OutlinedButton(onClick=onLocal,modifier=Modifier.fillMaxWidth().height(54.dp),shape=RoundedCornerShape(16.dp)) { Text("Open local music") }
    }
}
