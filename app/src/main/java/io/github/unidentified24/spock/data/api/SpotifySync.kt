package io.github.unidentified24.spock.data.api

import android.content.Context
import android.util.Log
import com.metrolist.spotify.Spotify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap

/**
 * Fire-and-forget mirroring of local library actions to the user's REAL Spotify
 * account (web-player token): liking a song, saving an album or following an
 * artist here shows up in Spotify itself. Failures are logged, never surfaced —
 * the local action already succeeded.
 */
object SpotifySync {

    data class BatchResult(val changed:Int,val total:Int){val complete get()=changed==total}
    suspend fun addMany(context:Context,playlistId:String,trackIds:List<String>):BatchResult {
        val ids=trackIds.filter{it.isNotBlank()}.distinct()
        if(!SpotifyTokenProvider.ensureToken(context))return BatchResult(0,ids.size)
        var changed=0
        for(batch in ids.chunked(100)){
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if(Spotify.addTracksToPlaylist(playlistId,batch.map{"spotify:track:$it"}).isFailure)break
            changed+=batch.size
        }
        membershipCache.remove(playlistId);return BatchResult(changed,ids.size)
    }
    suspend fun removeMany(context:Context,playlistId:String,trackIds:Set<String>,duplicatesOnly:Boolean=false):BatchResult {
        if(!SpotifyTokenProvider.ensureToken(context))return BatchResult(0,trackIds.size.coerceAtLeast(1))
        val tracks=allPlaylistTracks(playlistId)
        val selected=if(duplicatesOnly)tracks.filter{!it.track?.id.isNullOrBlank()}.groupBy{it.track!!.id}.values.flatMap{it.drop(1)}else tracks.filter{it.track?.id in trackIds}
        val refs=selected.mapNotNull{item->item.uid?.let{Spotify.PlaylistItemRef("spotify:track:${item.track!!.id}",it)}}
        if(refs.size!=selected.size)return BatchResult(0,selected.size.coerceAtLeast(1))
        var changed=0
        for(batch in refs.chunked(100)){
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if(Spotify.removeTracksFromPlaylist(playlistId,batch).isFailure)break
            changed+=batch.size
        }
        membershipCache.remove(playlistId);return BatchResult(changed,refs.size)
    }

    private const val TAG = "SpotifySync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun setTrackSaved(context: Context, trackId: String, saved: Boolean) =
        setSaved(context, trackId, "spotify:track:$trackId", saved)

    fun setAlbumSaved(context: Context, albumId: String, saved: Boolean) =
        setSaved(context, albumId, "spotify:album:$albumId", saved)

    fun setArtistFollowed(context: Context, artistId: String, followed: Boolean) =
        setSaved(context, artistId, "spotify:artist:$artistId", followed)

    private fun setSaved(context: Context, id: String, uri: String, saved: Boolean) {
        if (id.isBlank()) return
        val app = context.applicationContext
        scope.launch {
            if (!SpotifyTokenProvider.ensureToken(app)) {
                Log.w(TAG, "no token — skipped syncing $uri saved=$saved")
                return@launch
            }
            val result =
                if (saved) Spotify.addToLibrary(listOf(uri))
                else Spotify.removeFromLibrary(listOf(uri))
            result.fold(
                onSuccess = { Log.d(TAG, "synced $uri saved=$saved") },
                onFailure = { Log.w(TAG, "failed syncing $uri saved=$saved", it) },
            )
        }
    }

    /** Adds a track to one of the user's playlists on Spotify itself. */
    fun addTrackToPlaylist(context: Context, playlistId: String, trackId: String, onDone: (Boolean) -> Unit = {}) {
        if (playlistId.isBlank() || trackId.isBlank()) { onDone(false); return }
        val app = context.applicationContext
        scope.launch {
            val ok = SpotifyTokenProvider.ensureToken(app) &&
                Spotify.addTracksToPlaylist(playlistId, listOf("spotify:track:$trackId")).isSuccess
            if (ok) membershipCache[playlistId]?.add(trackId)
            if (!ok) Log.w(TAG, "failed adding track $trackId to playlist $playlistId")
            onDone(ok)
        }
    }

    /** Removes every occurrence of a track from one of the user's playlists on
     *  Spotify itself (resolves the playlist-scoped item uids first). */
    fun removeTrackFromPlaylist(context: Context, playlistId: String, trackId: String, onDone: (Boolean) -> Unit = {}) {
        if (playlistId.isBlank() || trackId.isBlank()) { onDone(false); return }
        val app = context.applicationContext
        scope.launch {
            if (!SpotifyTokenProvider.ensureToken(app)) { onDone(false); return@launch }
            val uri = "spotify:track:$trackId"
            val refs = allPlaylistTracks(playlistId)
                .filter { it.track?.id == trackId || it.track?.uri == uri }
                .mapNotNull { pt -> pt.uid?.let { Spotify.PlaylistItemRef(uri = uri, uid = it) } }
            val ok = refs.isNotEmpty() &&
                Spotify.removeTracksFromPlaylist(playlistId, refs).isSuccess
            if (ok) membershipCache[playlistId]?.remove(trackId)
            if (!ok) Log.w(TAG, "failed removing track $trackId from playlist $playlistId")
            onDone(ok)
        }
    }

    /** Creates a new playlist on Spotify and adds the track to it. */
    fun createPlaylistWithTrack(context: Context, name: String, trackId: String, onDone: (Boolean) -> Unit = {}) {
        if (name.isBlank()) { onDone(false); return }
        val app = context.applicationContext
        scope.launch {
            if (!SpotifyTokenProvider.ensureToken(app)) { onDone(false); return@launch }
            val playlist = Spotify.createPlaylist(name).getOrNull()
            if (playlist == null || playlist.id.isBlank()) {
                Log.w(TAG, "createPlaylist '$name' failed"); onDone(false); return@launch
            }
            val ok = trackId.isBlank() ||
                Spotify.addTracksToPlaylist(playlist.id, listOf("spotify:track:$trackId")).isSuccess
            if (ok && trackId.isNotBlank()) {
                membershipCache[playlist.id] = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply { add(trackId) }
            }
            onDone(ok)
        }
    }

    // Every page: a track past the first 100 must still be found and removable.
    private suspend fun allPlaylistTracks(playlistId: String) =
        fetchAllPages { offset -> Spotify.playlistTracks(playlistId, limit = 100, offset = offset) }

    // ── Playlist membership (which playlists contain a track) ──
    // Cached per playlist for the app session so the "Saved in" sheet can show
    // check marks without re-fetching on every open.
    private val membershipCache = ConcurrentHashMap<String, MutableSet<String>>()

    /** Track ids contained in [playlistId] (first call fetches, then cached). */
    suspend fun playlistTrackIds(context: Context, playlistId: String): Set<String> {
        membershipCache[playlistId]?.let { return it }
        if (!SpotifyTokenProvider.ensureToken(context.applicationContext)) return emptySet()
        val ids = allPlaylistTracks(playlistId)
            .mapNotNull { it.track?.id?.ifBlank { null } }
            .toMutableSet()
        val set = ConcurrentHashMap.newKeySet<String>().apply { addAll(ids) }
        membershipCache[playlistId] = set
        return set
    }
}
