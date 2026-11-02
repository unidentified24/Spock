package io.github.unidentified24.spock.features.discovery

import android.content.Context
import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.features.FeatureSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable data class DiscoveryMix(val id:String,val name:String,val songs:List<SongsModel>,val createdAt:Long=System.currentTimeMillis(),val downloadedOnly:Boolean=false)
object DiscoveryHistory {
    private var prefs:android.content.SharedPreferences?=null
    val state=MutableStateFlow<List<DiscoveryMix>>(emptyList())
    private val transientIds=mutableSetOf<String>()
    @Synchronized fun initialize(context:Context){if(prefs!=null)return;prefs=context.applicationContext.getSharedPreferences("discovery_history",0);reload()}
    @Synchronized fun reload(){state.value=runCatching{FeatureSettings.json.decodeFromString<List<DiscoveryMix>>(prefs?.getString("items","[]") ?: "[]")}.getOrDefault(emptyList()).distinctBy{it.id}.take(30)}
    @Synchronized fun save(name:String,songs:List<SongsModel>,downloadedOnly:Boolean):DiscoveryMix {
        val mix=DiscoveryMix(java.util.UUID.randomUUID().toString(),name.take(100),songs.distinctBy{it.id}.take(100),downloadedOnly=downloadedOnly)
        if(!FeatureSettings.value.privacy.privateListening){val next=(listOf(mix)+state.value.filter{it.id !in transientIds}).take(30);checkNotNull(prefs).edit().putString("items",FeatureSettings.json.encodeToString(next)).commit();state.value=next;transientIds.clear()}
        else{transientIds+=mix.id;state.value=listOf(mix)+state.value.filter{it.id!=mix.id}.take(30)}
        return mix
    }
    @Synchronized fun remove(id:String){val next=state.value.filter{it.id!=id};checkNotNull(prefs).edit().putString("items",FeatureSettings.json.encodeToString(next.filter{it.id !in transientIds})).apply();state.value=next;transientIds-=id}
    @Synchronized fun clear(){prefs?.edit()?.remove("items")?.apply();state.value=emptyList();transientIds.clear()}
}
