package io.github.unidentified24.spock.data.preferences

import android.content.Context
import io.github.unidentified24.spock.data.entity.OfflinePlaylist
import io.github.unidentified24.spock.data.entity.OfflinePlaylistCodec

private const val OFFLINE_PLAYLISTS="OfflinePlaylists"
fun saveOfflinePlaylist(context:Context,playlist:OfflinePlaylist) {
    check(context.getSharedPreferences(OFFLINE_PLAYLISTS,0).edit().putString(playlist.id,OfflinePlaylistCodec.encode(playlist)).commit())
}
fun getOfflinePlaylist(context:Context,id:String)=context.getSharedPreferences(OFFLINE_PLAYLISTS,0).getString(id,null)?.let(OfflinePlaylistCodec::decode)
fun getOfflinePlaylists(context:Context)=context.getSharedPreferences(OFFLINE_PLAYLISTS,0).all.values.mapNotNull{(it as? String)?.let(OfflinePlaylistCodec::decode)}.sortedBy{it.name.lowercase()}
fun forgetOfflinePlaylist(context:Context,id:String){context.getSharedPreferences(OFFLINE_PLAYLISTS,0).edit().remove(id).apply()}
