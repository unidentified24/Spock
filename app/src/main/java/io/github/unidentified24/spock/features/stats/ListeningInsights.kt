package io.github.unidentified24.spock.features.stats

import io.github.unidentified24.spock.data.entity.SongsModel
import io.github.unidentified24.spock.data.preferences.ListeningSample
import java.time.LocalDate

data class ArtistDiscovery(val artist:String,val date:LocalDate)
data class AlbumProgress(val key:String,val name:String,val artist:String,val cover:String,val heard:Int,val total:Int)
data class ListeningRecap(val ms:Long,val tracks:Int,val artists:Int,val topArtist:String,val topTrack:String)
object ListeningInsights {
    fun recap(rows:List<ListeningSample>,catalog:List<SongsModel> = emptyList()):ListeningRecap {
        val played=rows.filter{it.ms>0}
        val tracks=catalog.associateBy{it.id};val credits=played.flatMap{row->tracks[row.songId]?.artistNames?.takeIf{it.isNotEmpty()}?.map{it to row.ms} ?: listOf(row.artist to row.ms)}.filter{it.first.isNotBlank()}
        return ListeningRecap(played.sumOf{it.ms},played.map{it.songId}.distinct().size,credits.map{it.first}.distinct().size,credits.groupBy{it.first}.maxByOrNull{it.value.sumOf{row->row.second}}?.key.orEmpty(),played.groupBy{it.songId}.maxByOrNull{it.value.sumOf{row->row.ms}}?.value?.lastOrNull()?.let{it.title+" · "+it.artist}.orEmpty())
    }
    fun streaks(rows:List<ListeningSample>,today:LocalDate):Pair<Int,Int>{
        val days=rows.filter{it.ms>0&&it.date<=today}.map{it.date}.distinct().sorted()
        var longest=0;var run=0;var previous:LocalDate?=null
        days.forEach{day->run=if(previous?.plusDays(1)==day)run+1 else 1;longest=maxOf(longest,run);previous=day}
        var cursor=if(today in days)today else today.minusDays(1);val set=days.toHashSet();var current=0
        while(cursor in set){current++;cursor=cursor.minusDays(1)}
        return current to longest
    }
    fun discoveries(rows:List<ListeningSample>,catalog:List<SongsModel> = emptyList()):List<ArtistDiscovery>{val tracks=catalog.associateBy{it.id};return rows.filter{it.ms>0}.flatMap{row->(tracks[row.songId]?.artistNames?.takeIf{it.isNotEmpty()} ?: listOf(row.artist)).filter{it.isNotBlank()}.map{it to row.date}}.groupBy{it.first}.map{(artist,values)->ArtistDiscovery(artist,values.minOf{it.second})}.sortedByDescending{it.date}}
    fun albums(rows:List<ListeningSample>,catalog:List<SongsModel>):List<AlbumProgress>{
        val totals=rows.groupBy{it.songId}.mapValues{(_,values)->values.sumOf{it.ms}}
        return catalog.filter{it.album.isNotBlank()}.distinctBy{it.id}.groupBy{it.albumId.ifBlank{it.album+"|"+it.singer}}.mapNotNull{(key,songs)->
            val first=songs.first();val heard=songs.count{song->(totals[song.id] ?: 0)>=if(song.durationMs>0)(song.durationMs*.8).toLong()else 30000}
            if(heard==0)null else AlbumProgress(key,first.album,first.singer,first.coverUri,heard,songs.maxOf{it.albumTrackCount})
        }.sortedWith(compareBy<AlbumProgress>{if(it.total>0&&it.heard>=it.total)1 else 0}.thenByDescending{it.heard})
    }
    fun genres(rows:List<ListeningSample>,catalog:List<SongsModel>):List<Pair<String,Long>>{
        val tracks=catalog.associateBy{it.id};val totals=mutableMapOf<String,Long>()
        rows.forEach{row->val tags=tracks[row.songId]?.genres.orEmpty().map{it.trim().lowercase()}.filter{it.isNotBlank()}.distinct();tags.forEach{tag->totals[tag]=(totals[tag] ?: 0)+row.ms/tags.size}}
        return totals.entries.map{it.key to it.value}.sortedByDescending{it.second}
    }
    fun replays(rows:List<ListeningSample>)=rows.groupBy{it.songId}.mapNotNull{(_,values)->val count=values.sumOf{it.plays};if(count==0)null else (values.last().title+" · "+values.last().artist) to count}.sortedByDescending{it.second}
    fun csv(rows:List<ListeningSample>):String {
        fun escape(value:String):String{val safe=if(value.trimStart().firstOrNull() in listOf('=','+','-','@'))"'"+value else value;return "\""+safe.replace("\"","\"\"")+"\""}
        return "date,song_id,title,artist,album,listening_ms,plays\r\n"+rows.joinToString("\r\n"){row->listOf(row.date.toString(),row.songId.toString(),row.title,row.artist,row.album,row.ms.toString(),row.plays.toString()).joinToString(",",transform=::escape)}+"\r\n"
    }
}
