package io.github.unidentified24.spock.features.discovery

import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.data.preferences.ListeningSample
import io.github.unidentified24.spock.features.DiscoveryOptions
import java.time.LocalDate

object DiscoveryPolicy {
    fun allowed(song:SongsModel,options:DiscoveryOptions,explicitFilter:Boolean,applyGenre:Boolean=true):Boolean {
        if(explicitFilter&&song.explicit)return false
        val names=(song.artistNames+song.singer).map{it.lowercase().trim()}.toSet()
        if(options.excludedArtists.any{excluded->excluded.lowercase().trim() in names||excluded.removePrefix("artist:") in song.artistIds})return false
        if(options.decade>0&&song.year !in options.decade..options.decade+9)return false
        if(applyGenre&&options.genre.isNotBlank()&&song.genres.none{it.contains(options.genre,true)})return false
        return true
    }
    fun filter(songs:List<SongsModel>,options:DiscoveryOptions,rows:List<ListeningSample>,today:LocalDate,explicitFilter:Boolean,applyGenre:Boolean=true):List<SongsModel>{
        val allowed=songs.distinctBy{it.id}.filter{allowed(it,options,explicitFilter,applyGenre)}
        if(options.avoidRepeatDays<=0)return allowed
        val last=rows.filter{it.ms>0}.groupBy{it.songId}.mapValues{(_,samples)->samples.maxOf{it.date}}
        val cutoff=today.minusDays(options.avoidRepeatDays.toLong())
        val fresh=allowed.filter{last[it.id]?.let{date->date<cutoff} ?: true}
        return fresh.ifEmpty{allowed.sortedBy{last[it.id] ?: LocalDate.MIN}}
    }
    fun forgotten(catalog:List<SongsModel>,liked:Set<Int>,rows:List<ListeningSample>,today:LocalDate):List<SongsModel>{
        val last=rows.filter{it.ms>0}.groupBy{it.songId}.mapValues{(_,values)->values.maxOf{it.date}}
        val totals=rows.groupBy{it.songId}.mapValues{(_,values)->values.sumOf{it.ms}}
        return catalog.distinctBy{it.id}.filter{it.id in liked&&(last[it.id]?.let{day->day<today.minusDays(30)} ?: true)}.sortedByDescending{totals[it.id] ?: 0}
    }
}
