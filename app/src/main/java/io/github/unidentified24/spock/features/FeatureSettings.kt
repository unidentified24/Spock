package io.github.unidentified24.spock.features

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class DownloadOptions(
    val wifiOnly:Boolean=false,val chargingOnly:Boolean=false,val scheduleMinute:Int=-1,
    val parallel:Int=3,val automaticRetry:Boolean=true,val retryLimit:Int=3,
    val smartPlaylists:Set<String> = emptySet(),val favoriteDownloads:Boolean=false,
    val mostPlayedCount:Int=0,val offlineOnly:Boolean=false,val smartHour:Int=6,
)
@Serializable data class StorageOptions(
    val maxDownloadsMiB:Int=0,val removeRare:Boolean=false,val rareAfterDays:Int=90,
    val cacheMiB:Int=512,val mobileLimitMiB:Int=0,val mobileWarnOnly:Boolean=true,
    val artworkSaver:Boolean=false,val artworkWifiOnly:Boolean=false,val downloadFolder:String="",
)
@Serializable data class HomeOptions(
    val order:List<String> = listOf("continue","pinned","shortcuts","fresh","forgotten","unfinished","recommendations"),
    val hidden:Set<String> = emptySet(),val shortcuts:List<String> = listOf("collection","downloads","history","local"),
)
@Serializable data class PlayerOptions(
    val layout:String="STANDARD",val artScale:Float=1f,val compact:Boolean=false,val backdrop:Boolean=false,
    val controls:List<String> = listOf("shuffle","previous","play","next","repeat","lyrics","queue"),
    val lyricFullscreen:Boolean=false,val showTime:Boolean=false,
)
@Serializable data class SoundOptions(
    val enabled:Boolean=false,val preset:String="Flat",val equalizer:List<Int> = listOf(0,0,0,0,0),
    val gainDb:Float=0f,val balance:Float=0f,val normalize:Boolean=false,val speed:Float=1f,
    val preservePitch:Boolean=true,val pitch:Float=1f,val autoDevicePresets:Boolean=true,
    val playlistCrossfade:Map<String,Int> = emptyMap(),
)
@Serializable data class LyricOptions(
    val cache:Boolean=true,val offsetMs:Int=0,val bilingual:Boolean=false,val textScale:Float=1f,
    val layout:String="CENTER",val translateTo:String="",val fullscreen:Boolean=false,
)
@Serializable data class DiscoveryOptions(
    val excludedArtists:Set<String> = emptySet(),val avoidRepeatDays:Int=7,val genre:String="",val decade:Int=0,
    val offlineMixes:Boolean=false,
)
@Serializable data class LocalOptions(
    val folders:Set<String> = emptySet(),val excluded:Set<String> = emptySet(),val autoScan:Boolean=false,
    val preferEmbeddedArt:Boolean=true,
)
@Serializable data class PrivacyOptions(
    val privateListening:Boolean=false,val explicitFilter:Boolean=false,val lockAccounts:Boolean=false,
    val lockBackups:Boolean=false,val errorReports:Boolean=false,
)
@Serializable data class BackupOptions(
    val automatic:Boolean=false,val folder:String="",val intervalDays:Int=1,val timeMinute:Int=120,
    val historyCount:Int=10,val beforeUpdate:Boolean=true,val reminderDays:Int=30,
)
@Serializable data class ComfortOptions(
    val textScale:Float=1f,val largeControls:Boolean=false,val highContrast:Boolean=false,
    val oneHanded:Boolean=false,val haptics:Boolean=false,val reduceMotion:Boolean=false,val batterySaver:Boolean=false,
    val dayTheme:String="",val nightTheme:String="",val dayMinute:Int=420,val nightMinute:Int=1200,
    val rotateThemes:Boolean=false,val favoriteThemes:Set<String> = emptySet(),
)
@Serializable data class UpdateOptions(val channel:String="STABLE",val wifiOnly:Boolean=true,val automaticChecks:Boolean=true)
@Serializable data class PalettePreset(val id:String,val name:String,val accentHue:Float,val secondaryHue:Float,val backgroundHue:Float,val depth:Float,val saturation:Float)
@Serializable data class AppearanceOptions(val custom:Boolean=false,val accentHue:Float=16f,val secondaryHue:Float=40f,val backgroundHue:Float=270f,val depth:Float=.08f,val saturation:Float=.35f,val presets:List<PalettePreset> = emptyList(),val fontStyle:String="MODERN")
@Serializable data class AppOptions(
    val downloads:DownloadOptions=DownloadOptions(),val storage:StorageOptions=StorageOptions(),
    val home:HomeOptions=HomeOptions(),val player:PlayerOptions=PlayerOptions(),val sound:SoundOptions=SoundOptions(),
    val lyrics:LyricOptions=LyricOptions(),val discovery:DiscoveryOptions=DiscoveryOptions(),val local:LocalOptions=LocalOptions(),
    val privacy:PrivacyOptions=PrivacyOptions(),val backups:BackupOptions=BackupOptions(),
    val comfort:ComfortOptions=ComfortOptions(),val updates:UpdateOptions=UpdateOptions(),
    val appearance:AppearanceOptions=AppearanceOptions(),
) {
    fun normalized()=copy(
        home=home.copy(order=(home.order.filter{it in HomeOptions().order}+HomeOptions().order).distinct(),hidden=home.hidden.intersect(HomeOptions().order.toSet()),shortcuts=home.shortcuts.filter{it in setOf("collection","downloads","history","local","stats","queue","account","search","mixes")}.distinct().take(9)),
        discovery=discovery.copy(avoidRepeatDays=discovery.avoidRepeatDays.coerceIn(0,3650),decade=if(discovery.decade in 1900..2100)discovery.decade/10*10 else 0,genre=discovery.genre.trim().take(80),excludedArtists=discovery.excludedArtists.map{it.trim().take(128)}.filter{it.isNotBlank()}.take(1000).toSet()),
        downloads=downloads.copy(parallel=downloads.parallel.coerceIn(1,3),retryLimit=downloads.retryLimit.coerceIn(0,8),scheduleMinute=downloads.scheduleMinute.coerceIn(-1,1439),mostPlayedCount=downloads.mostPlayedCount.coerceIn(0,500),smartHour=downloads.smartHour.coerceIn(0,23)),
        storage=storage.copy(maxDownloadsMiB=storage.maxDownloadsMiB.coerceIn(0,2_000_000),cacheMiB=storage.cacheMiB.coerceIn(32,8192),mobileLimitMiB=storage.mobileLimitMiB.coerceIn(0,2_000_000),rareAfterDays=storage.rareAfterDays.coerceIn(7,730)),
        player=player.copy(artScale=player.artScale.takeIf{it.isFinite()}?.coerceIn(.45f,1.2f) ?: 1f,controls=(player.controls.filter{it in setOf("shuffle","previous","play","next","repeat","lyrics","queue")}+"play").distinct()),
        sound=sound.copy(gainDb=sound.gainDb.takeIf{it.isFinite()}?.coerceIn(-12f,6f) ?: 0f,balance=sound.balance.takeIf{it.isFinite()}?.coerceIn(-1f,1f) ?: 0f,speed=sound.speed.takeIf{it.isFinite()}?.coerceIn(.5f,2f) ?: 1f,pitch=sound.pitch.takeIf{it.isFinite()}?.coerceIn(.5f,2f) ?: 1f,equalizer=sound.equalizer.take(10).map{it.coerceIn(-1200,1200)},playlistCrossfade=sound.playlistCrossfade.mapValues{it.value.coerceIn(0,12000)}),
        lyrics=lyrics.copy(offsetMs=lyrics.offsetMs.coerceIn(-30000,30000),textScale=lyrics.textScale.takeIf{it.isFinite()}?.coerceIn(.7f,2f) ?: 1f),
        comfort=comfort.copy(textScale=comfort.textScale.takeIf{it.isFinite()}?.coerceIn(.8f,1.6f) ?: 1f,dayMinute=comfort.dayMinute.coerceIn(0,1439),nightMinute=comfort.nightMinute.coerceIn(0,1439)),
        backups=backups.copy(intervalDays=backups.intervalDays.coerceIn(1,30),timeMinute=backups.timeMinute.coerceIn(0,1439),historyCount=backups.historyCount.coerceIn(1,50),reminderDays=backups.reminderDays.coerceIn(0,365)),
        updates=updates.copy(channel=updates.channel.takeIf{it in setOf("STABLE","BETA")} ?: "STABLE"),
        appearance=appearance.copy(accentHue=appearance.accentHue.takeIf{it.isFinite()}?.coerceIn(0f,359f) ?: 16f,secondaryHue=appearance.secondaryHue.takeIf{it.isFinite()}?.coerceIn(0f,359f) ?: 40f,backgroundHue=appearance.backgroundHue.takeIf{it.isFinite()}?.coerceIn(0f,359f) ?: 270f,depth=appearance.depth.takeIf{it.isFinite()}?.coerceIn(0f,.16f) ?: .08f,saturation=appearance.saturation.takeIf{it.isFinite()}?.coerceIn(0f,.7f) ?: .35f,presets=appearance.presets.distinctBy{it.id}.take(40),fontStyle=appearance.fontStyle.takeIf{it in setOf("MODERN","SERIF","SYSTEM","MONO")} ?: "MODERN"),
    )
}

object FeatureSettings {
    val json=Json{ignoreUnknownKeys=true;encodeDefaults=true}
    private var prefs:SharedPreferences?=null
    private val mutable=MutableStateFlow(AppOptions())
    val state:StateFlow<AppOptions> = mutable
    val value get()=mutable.value
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="options")reload()}
    @Synchronized fun initialize(context:Context){
        if(prefs!=null)return
        prefs=context.applicationContext.getSharedPreferences("spock_features",0)
        prefs!!.registerOnSharedPreferenceChangeListener(listener);reload()
    }
    @Synchronized fun reload(){mutable.value=runCatching{json.decodeFromString<AppOptions>(prefs?.getString("options",null) ?: "{}").normalized()}.getOrDefault(AppOptions())}
    @Synchronized fun update(change:(AppOptions)->AppOptions){
        val next=change(mutable.value).normalized();mutable.value=next
        checkNotNull(prefs){"Settings are not initialized"}.edit().putString("options",json.encodeToString(next)).apply()
    }
    fun portable(options:AppOptions=value)=options.copy(storage=options.storage.copy(downloadFolder=""),local=options.local.copy(folders=emptySet(),excluded=emptySet()),backups=options.backups.copy(folder=""))
}
