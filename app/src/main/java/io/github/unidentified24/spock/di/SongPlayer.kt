package io.github.unidentified24.spock.di

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.YouTubeUrlParser
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.utils.YTPlayerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import io.github.unidentified24.spock.playback.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

/**
 * Plays audio resolved from YouTube. The `song` argument is a "title artist"
 * search query (set by the Spotify-backed data layer): it's matched to a
 * YouTube video, whose stream URL is resolved via the ported [YTPlayerUtils]
 * flow (cipher / PoToken / sabr) and handed to ExoPlayer.
 */
object SongPlayer {
    private const val TAG = "SongPlayer"
    private const val SPOTIFY_TRACK_PREFIX = "spotify:track:"
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cache of resolved stream URLs keyed by the "title artist" query, so replays
    // and prefetched neighbours start instantly instead of re-hitting the network.
    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    // Which engine each cached stream came from ("YouTube", "Lossless • …") so a
    // cache hit can restore the correct source badge.
    private val sourceCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    // ── Lossless (SpotiFLAC) ──
    // Enabled FLAC sources compete with the other providers. First usable result
    // wins; a slower FLAC lookup never holds up or replaces current playback.
    @Volatile var losslessStreaming = true
    @Volatile var losslessHiRes = true

    // Source kill-switches. The Spotify web player is currently broken (off).
    // YouTube is the last-resort fallback, kept on so tracks SpotiFLAC misses or
    // can't serve during a proxy cooldown still play — with the wrong-song guards
    // (videoId match check + artist/title scoring + candidate fallback).
    @Volatile var webPlayerEnabled = false
    @Volatile var youtubeEnabled = true

    // ── Deezer ──
    // When enabled and a Deezer account is logged in (ARL stored), playback tries
    // Deezer concurrently (ISRC → Deezer track → encrypted CDN stream, decrypted on the
    // fly by DeezerDataSource). Quality follows the account tier: free → MP3 128,
    // Premium → MP3 320 / FLAC. Falls back to SpotiFLAC/YouTube on any miss.
    @Volatile var deezerEnabled = true

    // Soulseek P2P as the last lossless fallback (see SoulseekSource).
    @Volatile var soulseekEnabled = true

    // Which engine is feeding the CURRENT track, for the on-screen source badge.
    // "Lossless" (SpotiFLAC: Tidal/Qobuz/Amazon) is NOT Spotify — surfaced so the
    // user knows real Spotify vs a lossless mirror vs the YouTube fallback.
    @Volatile var currentSource: String = "YouTube"
        private set
    // Human-readable quality of the CURRENT stream (e.g. "FLAC 16-bit",
    // "OPUS 141 kbps"), shown next to the source badge.
    @Volatile var currentQuality: String = ""
        private set
    @Volatile var playbackMessage:String = ""
        private set
    @Volatile var playbackFailed:Boolean = false
        private set
    private val rejectedStreams=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val streamExpiry = java.util.concurrent.ConcurrentHashMap<String,Long>()
    private val candidateCache = java.util.concurrent.ConcurrentHashMap<String,List<String>>()
    private var matchPrefetch:kotlinx.coroutines.Job?=null
    private var matchPrefetchKey:String?=null
    private val qualityCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    // Maps a "title artist" play query -> the track's real Spotify id, so the
    // lossless resolver can be seeded from a play site that only has the query.
    // Populated centrally whenever the queue changes (see CurrentSongState).
    private val trackIdRegistry = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val alternativeKeyRegistry = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Register query→spotifyTrackId pairs so lossless can be resolved by query. */
    fun registerLossless(pairs: List<Pair<String, String>>) {
        pairs.forEach { (query, spotifyId) ->
            if (query.isNotBlank() && spotifyId.isNotBlank()) trackIdRegistry[query] = spotifyId
        }
    }

    fun registerAlternativeKeys(pairs: List<Pair<String, String>>) {
        pairs.forEach { (query, key) ->
            if (query.isNotBlank() && key.isNotBlank()) alternativeKeyRegistry[query] = key
        }
    }

    // Whether each play query is the explicit version on Spotify, so the YouTube
    // fallback can pick the matching (explicit vs clean) edit.
    private val explicitRegistry = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Register query→explicit pairs (populated whenever the queue changes). */
    fun registerExplicit(pairs: List<Pair<String, Boolean>>) {
        pairs.forEach { (query, explicit) ->
            if (query.isNotBlank()) explicitRegistry[query] = explicit
        }
    }

    // Expected track length (ms) per query, from Spotify — lets the YouTube match
    // reject a same-title song by a different artist (different duration).
    private val durationRegistry = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /** Register query→durationMs pairs (populated whenever the queue changes). */
    fun registerDuration(pairs: List<Pair<String, Int>>) {
        pairs.forEach { (query, ms) ->
            if (query.isNotBlank() && ms > 0) durationRegistry[query] = ms
        }
    }

    data class TrackMatchMetadata(
        val title: String,
        val artist: String,
        val album: String,
        val isrc: String = "",
    )

    private val metadataRegistry =
        java.util.concurrent.ConcurrentHashMap<String, TrackMatchMetadata>()
    // GQL track objects do not consistently carry the explicit flag. Keep this
    // separate from explicitRegistry so the model's default `false` is not
    // mistaken for a confirmed clean track.
    private val spotifyMetadataRepaired = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Register query→Spotify metadata pairs for strict YouTube result scoring. */
    fun registerMetadata(pairs: List<Pair<String, TrackMatchMetadata>>) {
        pairs.forEach { (query, meta) ->
            if (query.isNotBlank() && meta.title.isNotBlank()) metadataRegistry[query] = meta
        }
    }
    // Tracks which query is the latest play request so a slow resolve for an old
    // tap doesn't clobber a newer one (fast switching).
    @Volatile private var currentRequest: String = ""
    // Next queue track already appended to the player (its play query), so ExoPlayer
    // moves on by itself without passing through ENDED/IDLE (#67: Android Auto left
    // Now Playing on that gap). Null when nothing is queued ahead.
    @Volatile private var preloadedNext: String? = null
    @Volatile private var preloading = false

    /** Fired (main thread) when the player moved on to the preloaded track [query]. */
    @Volatile var onPreloadedTrackStarted: ((query: String) -> Unit)? = null

    // Request and position of the last re-resolve after a mid-play error.
    @Volatile private var retriedRequest: String? = null
    @Volatile private var retriedAtMs: Long = 0L

    // Latest track metadata (title / artist / cover URL) so the MediaItem we build
    // carries it into the system media notification. Set via [setNowPlayingMeta]
    // (driven by CurrentSongState) just before / as playback starts.
    @Volatile private var metaTitle: String = ""
    @Volatile private var metaArtist: String = ""
    @Volatile private var metaCover: String = ""

    fun setNowPlayingMeta(title: String, artist: String, coverUri: String) {
        metaTitle = title
        metaArtist = artist
        metaCover = coverUri
    }

    /**
     * Build a stable playback identity for Spotify tracks. The full value is used
     * as cache/registry key, while only the text after "|" is sent to YouTube
     * search. This prevents same-title/same-artist tracks from reusing each
     * other's resolved stream.
     */
    fun buildSpotifyPlayQuery(spotifyTrackId: String, title: String, artist: String): String {
        val searchText = listOf(cleanSpotifySearchTitle(title), artist)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return if (spotifyTrackId.isBlank()) searchText else "$SPOTIFY_TRACK_PREFIX$spotifyTrackId|$searchText"
    }

    private val featSearchPattern = Regex("""\s*[\(\[]\s*(feat|ft)\..*?[\)\]]""", RegexOption.IGNORE_CASE)

    /** What the queue registered for [song], so lossless matching needs no Spotify lookup. */
    private fun losslessTrackInfo(song: String): io.github.unidentified24.spock.lossless.LosslessSource.TrackInfo {
        val meta = metadataRegistry[song] ?: return io.github.unidentified24.spock.lossless.LosslessSource.TrackInfo()
        return io.github.unidentified24.spock.lossless.LosslessSource.TrackInfo(
            title = meta.title,
            artist = meta.artist.substringBefore(",").trim(),
            durationSec = (durationRegistry[song] ?: 0) / 1000,
            isrc = meta.isrc,
        )
    }

    private fun cleanSpotifySearchTitle(title: String): String =
        title.replace(featSearchPattern, "").trim()

    private fun searchTextForPlayback(song: String): String =
        if (song.startsWith(SPOTIFY_TRACK_PREFIX) && song.contains('|')) {
            song.substringAfter('|').ifBlank { song }
        } else {
            song
        }

    private fun spotifyTrackIdForPlayback(song: String): String? =
        if (song.startsWith(SPOTIFY_TRACK_PREFIX)) {
            song.removePrefix(SPOTIFY_TRACK_PREFIX).substringBefore('|').takeIf { it.isNotBlank() }
        } else {
            null
        }

    fun videoIdFromYouTubeLink(text: String): String? =
        YouTubeUrlParser.extractVideoId(text)
            ?: text.trim().takeIf { it.matches(Regex("""[A-Za-z0-9_-]{11}""")) }

    fun invalidateResolvedStream(song: String) {
        streamExpiry.remove(song)
        streamCache.remove(song)
        sourceCache.remove(song)
        qualityCache.remove(song)
    }

    private val playbackLookups = java.util.concurrent.ConcurrentHashMap.newKeySet<kotlinx.coroutines.Job>()
    @Volatile private var heardCurrent=false
    private var playRequestJob: kotlinx.coroutines.Job? = null

    fun playSong(song: String, context: Context) {
        playRequestJob?.cancel()
        playbackLookups.toList().forEach { it.cancel() }
        val appContext = context.applicationContext
        appCtx = appContext
        rejectedStreams.clear()
        currentRequest = song
        PlaybackDiagnostics.event("Selected track; resolving audio")
        heardCurrent=false
        playbackFailed=false
        playbackMessage="Finding your audio…"
        retriedRequest = null
        preloadedNext = null
        // A manual play (tap / next / prev) supersedes any in-flight crossfade.
        cancelCrossfade()
        // Do not keep the previous track audible while this request resolves.
        // Otherwise the UI can show the newly tapped track while old audio keeps
        // playing for several seconds, or forever if resolution fails.
        runCatching {
            player?.pause()
            player?.clearMediaItems()
        }

        // Podcast episodes are encoded as "episode:<id>" queries — play them via the
        // Spotify web player's episode page (same engine as tracks).
        if (song.startsWith("episode:") && webPlayerEnabled && SpotifyWebPlayer.canPlay &&
            io.github.unidentified24.spock.data.preferences.isWebPlaybackEnabled(appContext)
        ) {
            runCatching { player?.pause() }
            currentSource = "Spotify"
            currentQuality = ""
            SpotifyWebPlayer.playEpisode(song.removePrefix("episode:"))
            return
        }

        // Downloaded tracks ALWAYS play the local file — even with Spotify web
        // playback on. (Web is now the default and used to run first, so a
        // downloaded track streamed from Spotify instead of playing offline.)
        val downloadedPath = io.github.unidentified24.spock.data.preferences.downloadedPathForQuery(appContext, song)
        if (downloadedPath == null && webPlayerEnabled &&
            // Experimental: stream through Spotify's own web player (real Spotify audio,
            // no bypass) when enabled AND the device WebView has Widevine. Otherwise
            // fall through to the normal YouTube/FLAC engine so playback is never silent.
            io.github.unidentified24.spock.data.preferences.isWebPlaybackEnabled(appContext) &&
            SpotifyWebPlayer.canPlay
        ) {
            val spotifyId = trackIdRegistry[song] ?: spotifyTrackIdForPlayback(song)
            if (spotifyId != null) {
                runCatching { player?.pause() }
                currentSource = "Spotify"
                currentQuality = ""
                SpotifyWebPlayer.play(spotifyId)
                return
            }
            Log.w(TAG, "web playback on but no Spotify id for query: $song — using fallback engine")
        }
        playRequestJob = scope.launch {
            try {
                val streamUrl = resolveStreamUrl(song, appContext, forPlayback = true) ?: run {
                    // Tell the user instead of silently leaving the request on.
                    if (currentRequest == song) withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            appContext, "Couldn't find a playable stream for this track",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        reportStreamFailure(song)
                    }
                    return@launch
                }
                // A newer tap superseded this one while we were resolving — drop it.
                if (currentRequest != song) return@launch
                startStream(song, streamUrl, appContext)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Playback lookup failed", e)
                withContext(Dispatchers.Main) {if(currentRequest==song)reportStreamFailure(song)}
            }
        }
    }

    private suspend fun startStream(song: String, streamUrl: String, appContext: Context, mimeType: String? = streamMimeType(streamUrl)) {
        withContext(Dispatchers.Main) {
            if (currentRequest != song) return@withContext
            ensurePlayer(appContext)
            player!!.setMediaItem(buildMediaItem(streamUrl, mimeType))
            playbackMessage="Starting audio…"
            player!!.prepare()
            // Restored session: continue from where the last run stopped.
            if (song == restoreQuery && restorePositionMs > 0) {
                player!!.seekTo(restorePositionMs)
            }
            restoreQuery = null
            player!!.playWhenReady = true
        }
        startPositionWatch()
        // Keep the winning source. Other lookups have already been cancelled.
    }

    private fun soulseekOn(ctx: Context): Boolean =
        soulseekEnabled && io.github.unidentified24.spock.data.preferences.isSoulseekEnabled(ctx)

    private fun soulseekKey(song: String): String =
        trackIdRegistry[song] ?: spotifyTrackIdForPlayback(song) ?: song.hashCode().toUInt().toString()

    // Build a MediaItem carrying the current track's metadata so the system media
    // notification (MediaSession) shows the right title / artist / artwork.
    private fun buildMediaItem(
        streamUrl: String,
        mimeType: String? = null,
        query: String = currentRequest,
        title: String = metaTitle,
        artist: String = metaArtist,
        cover: String = metaCover,
    ): MediaItem {
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply { if (cover.isNotBlank()) setArtworkUri(android.net.Uri.parse(cover)) }
            .build()
        return MediaItem.Builder()
            // The play query, so a gapless transition tells us which track is now playing.
            .setMediaId(query)
            .setUri(streamUrl)
            // Hint the container so ExoPlayer picks the right source/extractor even
            // when the URL has no extension: TIDAL lossless is a DASH .mpd manifest,
            // and single-file lossless is FLAC.
            .apply { if (mimeType != null) setMimeType(mimeType) }
            .setMediaMetadata(metadata)
            .build()
    }

    /** MIME hint for a resolved stream: DASH manifest, single-file FLAC, or none. */
    private fun streamMimeType(streamUrl: String, source: String = currentSource): String? {
        val bare = streamUrl.substringBefore('?').lowercase()
        return when {
            streamUrl.startsWith("deezer://") ->
                if (streamUrl.contains("fmt=flac")) androidx.media3.common.MimeTypes.AUDIO_FLAC
                else androidx.media3.common.MimeTypes.AUDIO_MPEG
            streamUrl.startsWith("data:application/dash+xml") ||
                bare.endsWith(".mpd") || streamUrl.contains("manifest.tidal.com") || streamUrl.contains("/manifests/") ->
                androidx.media3.common.MimeTypes.APPLICATION_MPD
            bare.endsWith(".flac") || source.startsWith("Lossless") ->
                androidx.media3.common.MimeTypes.AUDIO_FLAC
            else -> null
        }
    }

    /** Prepare one next-track match, without racing short-lived transport URLs. */
    fun prefetch(song:String,context:Context) {
        if(song.isBlank() || song==matchPrefetchKey || candidateCache.containsKey(song+":"+YouTube.SearchFilter.FILTER_SONG) || webPlaybackActive() ||
            !io.github.unidentified24.spock.data.preferences.isPreloadEnabled(context) ||
            !io.github.unidentified24.spock.data.preferences.isYouTubeSourceEnabled(context))return
        matchPrefetch?.cancel();matchPrefetchKey=song
        matchPrefetch=scope.launch {
            try { delay(1200);resolveVideoCandidates(song) }
            finally {if(matchPrefetchKey==song)matchPrefetchKey=null}
        }
    }

    // ── Intro preloading (instant playback) ──
    // Resolving the stream URL hides most latency, but ExoPlayer still has to open the
    // connection and buffer the first segment on tap. We pre-cache the first ~1 MB (≈20–40s
    // of audio) of upcoming tracks into a media cache the player reads through, so a tap on a
    // preloaded track starts almost instantly. Skipped for local files (already instant) and
    // when the user turns preloading off in Settings.
    private const val PRELOAD_BYTES = 1L * 1024 * 1024

    @Volatile private var mediaCache: androidx.media3.datasource.cache.SimpleCache? = null

    internal fun mediaCache(context: Context): androidx.media3.datasource.cache.SimpleCache =
        mediaCache ?: synchronized(this) {
            mediaCache ?: androidx.media3.datasource.cache.SimpleCache(
                java.io.File(context.cacheDir, "media"),
                androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
                androidx.media3.database.StandaloneDatabaseProvider(context),
            ).also { mediaCache = it }
        }

    private fun cacheDataSourceFactory(context: Context): androidx.media3.datasource.cache.CacheDataSource.Factory {
        val http = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .setAllowCrossProtocolRedirects(true)
        val baseUpstream = androidx.media3.datasource.DefaultDataSource.Factory(context, http)
        val resolvedUpstream = androidx.media3.datasource.ResolvingDataSource.Factory(baseUpstream) { dataSpec ->
            val url = dataSpec.uri.toString()
            if (url.contains("googlevideo.com")) {
                dataSpec.withAdditionalHeaders(
                    com.metrolist.innertube.models.YouTubeClient.forStreamUrl(url).mediaHeaders(),
                )
            } else dataSpec
        }
        val upstream = io.github.unidentified24.spock.playback.ChunkedDataSource.Factory(
            resolvedUpstream,
            2L * 1024 * 1024,
        )
        return androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(mediaCache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Pre-cache the first [PRELOAD_BYTES] of [url] into the media cache (http(s) only). */
    private fun cacheIntro(url: String, appContext: Context) {
        if (!url.startsWith("http")) return
        if (!io.github.unidentified24.spock.data.preferences.isPreloadEnabled(appContext)) return
        runCatching {
            val ds = cacheDataSourceFactory(appContext).createDataSource()
            val spec = androidx.media3.datasource.DataSpec.Builder()
                .setUri(android.net.Uri.parse(url))
                .setLength(PRELOAD_BYTES)
                .build()
            androidx.media3.datasource.cache.CacheWriter(ds, spec, null, null).cache()
        }.onFailure { Log.d(TAG, "intro preload skipped: ${it.message}") }
    }

    // forPlayback=true only for the track actually being played — so background
    // prefetch of upcoming tracks doesn't clobber the current source badge (a
    // prefetch resolving the NEXT track via YouTube was flipping the badge to
    // "YouTube" while the current track streamed from Spotify).
    private suspend fun resolveStreamUrl(
        song: String,
        appContext: Context,
        forPlayback: Boolean = false,
        allowYouTube: Boolean = true,
    ): String? {
        val job = currentCoroutineContext()[kotlinx.coroutines.Job]
        if (forPlayback && job != null) playbackLookups.add(job)
        return try { resolveStreamUrlInternal(song, appContext, forPlayback, allowYouTube) }
        finally { if (forPlayback && job != null) playbackLookups.remove(job) }
    }

    private suspend fun resolveStreamUrlInternal(
        song: String,
        appContext: Context,
        forPlayback: Boolean = false,
        allowYouTube: Boolean = true,
    ): String? {
        // Imported local files: the play query IS the file's content:// / file:// URI.
        // ExoPlayer plays it directly (FLAC/MP3/WAV/… via its built-in extractors).
        if (song.startsWith("content://") || song.startsWith("file://")) {
            if (forPlayback) {
                currentSource = "Local file"
                currentQuality = song.substringBefore('?').substringAfterLast('.', "")
                    .uppercase().takeIf { it.length in 2..5 }.orEmpty()
            }
            return song
        }
        alternativeStreamForPlayback(song, appContext)?.let { alt ->
            invalidateResolvedStream(song)
            return when {
                alt.isLocal -> {
                    if (forPlayback) {
                        currentSource = "Alternative file"
                        currentQuality = alt.label.substringAfterLast('.', "").uppercase().takeIf { it.length in 2..5 }.orEmpty()
                    }
                    alt.value
                }
                alt.isYouTube -> {
                    if (forPlayback) {
                        currentSource = "Alternative YouTube"
                        currentQuality = ""
                    }
                    val quality = io.github.unidentified24.spock.data.preferences.currentStreamingQuality(appContext)
                    val playback = resolveYtPlayback(alt.value, quality.audioQuality, appContext) ?: return null
                    if (forPlayback) currentQuality = ytQualityLabel(playback)
                    playback.streamUrl
                }
                else -> null
            }
        }
        // Offline: if this track was downloaded, play the local file instead of the network.
        io.github.unidentified24.spock.data.preferences.downloadedPathForQuery(appContext, song)?.let { path ->
            if (forPlayback) {
                currentSource = "Downloaded"
                currentQuality = path.substringAfterLast('.', "").uppercase()
            }
            return android.net.Uri.fromFile(java.io.File(path)).toString()
        }
        if ((streamExpiry[song] ?: 0L) <= System.currentTimeMillis()) invalidateResolvedStream(song)
        streamCache[song]?.let { cachedUrl ->
            // Source choices take effect immediately; never let a URL cached under the
            // previous choice silently keep using that provider.
            val cachedSource = sourceCache[song]
            val sourceStillSelected = when (cachedSource) {
                "Deezer" -> deezerEnabled && io.github.unidentified24.spock.data.preferences.isDeezerRaceEnabled(appContext)
                "YouTube" -> youtubeEnabled && allowYouTube && io.github.unidentified24.spock.data.preferences.isYouTubeSourceEnabled(appContext)
                else -> cachedSource?.startsWith("Lossless") != true ||
                    (losslessStreaming && io.github.unidentified24.spock.data.preferences.isFlacSourceEnabled(appContext) && io.github.unidentified24.spock.data.preferences.currentStreamingQuality(appContext).lossless)
            }
            if (!sourceStillSelected) {
                invalidateResolvedStream(song)
            } else {
            // Cache hits must still update the badge — returning early kept the
            // previous track's label (e.g. "Downloaded") on a streamed track.
                if (forPlayback) {
                    currentSource = cachedSource ?: "YouTube"
                    currentQuality = qualityCache[song] ?: ""
                }
                return cachedUrl
            }
        }
        // Quality for the current network (Wi-Fi vs cellular), from Settings.
        val quality = io.github.unidentified24.spock.data.preferences.currentStreamingQuality(appContext)

        // A Soulseek FLAC fetched earlier (look-ahead or a past play) starts right away.
        if (soulseekOn(appContext) && quality.lossless) {
            io.github.unidentified24.spock.soulseek.SoulseekSource.cached(appContext, soulseekKey(song))?.let { file ->
                if (forPlayback) { currentSource = "Lossless • Soulseek"; currentQuality = "FLAC" }
                sourceCache[song] = "Lossless • Soulseek"
                qualityCache[song] = "FLAC"
                return android.net.Uri.fromFile(file).toString()
            }
        }

        val spotifyId = trackIdRegistry[song] ?: spotifyTrackIdForPlayback(song)
        val lookups = mutableListOf<StreamLookup<ResolvedStream>>()
        fun lookup(resolve: suspend () -> ResolvedStream?) {
            val connections = LookupConnections()
            lookups += StreamLookup(cancel = { connections.close() }) {
                withContext(connections) {
                    resolve()?.takeIf {
                        PlaybackDiagnostics.event("${it.source}: URL resolved; preparing")
                        it.url.isNotBlank() && it.url !in rejectedStreams && prepareCandidate(it,appContext,(durationRegistry[song] ?: 0).toLong())
                    }
                }
            }
        }
        if (deezerEnabled && io.github.unidentified24.spock.data.preferences.isDeezerRaceEnabled(appContext)) {
            lookup {
                val result = io.github.unidentified24.spock.deezer.DeezerSource.resolve(
                    appContext, spotifyId,
                    isrc = metadataRegistry[song]?.isrc?.takeIf { it.isNotBlank() },
                    searchQuery = searchTextForPlayback(song),
                    expectedDurationSec = (durationRegistry[song] ?: 0) / 1000,
                )
                (result as? io.github.unidentified24.spock.deezer.DeezerSource.Result.Success)?.let {
                    ResolvedStream(it.uri, "Deezer", it.qualityLabel)
                }
            }
        }
        if (losslessStreaming && io.github.unidentified24.spock.data.preferences.isFlacSourceEnabled(appContext) && quality.lossless && spotifyId != null) {
            lookup {
                val result = io.github.unidentified24.spock.lossless.LosslessSource.resolve(
                    appContext, spotifyId, losslessTrackInfo(song), losslessHiRes)
                (result as? io.github.unidentified24.spock.lossless.LosslessSource.Result.Success)?.let {
                    ResolvedStream(it.track.url, "Lossless • ${it.track.provider}", "FLAC ${it.track.quality}-bit")
                }
            }
        }
        if (youtubeEnabled && allowYouTube && io.github.unidentified24.spock.data.preferences.isYouTubeSourceEnabled(appContext)) {
            lookup {
                resolveYtPlayback(song, quality.audioQuality, appContext)?.let {
                    ResolvedStream(it.streamUrl, "YouTube", ytQualityLabel(it), (it.streamExpiresInSeconds-30).coerceIn(0,1800))
                }
            }
        }
        val winner = firstPlayable(lookups) ?: return null
        currentCoroutineContext().ensureActive()
        if (winner.url.isBlank()) return null
        PlaybackDiagnostics.event("${winner.source}: buffered winner")
        // Only this coordinator can publish provider metadata or cache a winning URL.
        if (forPlayback) withContext(Dispatchers.Main) {
            if (currentRequest == song) {
                currentSource = winner.source
                currentQuality = winner.quality
            }
        }
        currentCoroutineContext().ensureActive()
        streamExpiry[song] = System.currentTimeMillis() + winner.ttlSeconds * 1000L
        streamCache[song] = winner.url
        sourceCache[song] = winner.source
        qualityCache[song] = winner.quality
        return winner.url
    }

    /** A URL is only eligible after Media3 can prepare buffered audio from it.
     * Muted preparation uses the shared cache; no contender plays audibly. */
    private suspend fun prepareCandidate(candidate:ResolvedStream,context:Context,expected:Long):Boolean = withContext(Dispatchers.Main) {
        val probe=createPlayerWithFilter(context,handleAudioFocus=false).first
        probe.volume=0f
        try {
            kotlinx.coroutines.withTimeoutOrNull(10000) {
                kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
                    probe.addListener(object:Player.Listener {
                        override fun onPlaybackStateChanged(state:Int) {
                            if(!continuation.isActive)return
                            if(state==Player.STATE_READY) {
                                val duration=probe.duration
                                val complete=expected<=0 || duration<=0 || duration>=expected*.70
                                PlaybackDiagnostics.event("${candidate.source}: prepared; length accepted=$complete")
                                continuation.resumeWith(Result.success(complete))
                            } else if(state==Player.STATE_ENDED)continuation.resumeWith(Result.success(false))
                        }
                        override fun onPlayerError(error:PlaybackException) {
                            PlaybackDiagnostics.event("${candidate.source}: prepare error ${error.errorCode}")
                            if(continuation.isActive)continuation.resumeWith(Result.success(false))
                        }
                    })
                    probe.setMediaItem(buildMediaItem(candidate.url,streamMimeType(candidate.url,candidate.source)))
                    probe.prepare()
                }
            } == true
        } finally {probe.release()}
    }

    private data class ResolvedStream(val url: String, val source: String, val quality: String, val ttlSeconds:Int=0)

    // e.g. "OPUS 141 kbps" from the chosen adaptive format.
    private fun ytQualityLabel(playback: YTPlayerUtils.PlaybackData): String {
        val codec = playback.format.mimeType
            .substringAfter("codecs=\"", "").substringBefore('"').substringBefore('.')
            .uppercase()
        return listOf(codec, "${playback.format.bitrate / 1000} kbps")
            .filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun alternativeStreamForPlayback(
        song: String,
        appContext: Context,
    ): io.github.unidentified24.spock.data.preferences.AlternativeStream? {
        val key = alternativeKeyRegistry[song]
            ?: spotifyTrackIdForPlayback(song)?.let {
                io.github.unidentified24.spock.data.preferences.alternativeStreamKeyForSpotifyId(it)
            }
        return key?.let { io.github.unidentified24.spock.data.preferences.getAlternativeStream(appContext, it) }
    }

    /** Fired (main thread) when the current track plays to its end. */
    @Volatile var onTrackEnded: (() -> Unit)? = null

    /** Fired (main thread) when a stream can't be resolved for [song], so the caller
     *  can skip to the next track instead of leaving playback silent. */
    fun reportStreamFailure(song:String) {
        if(currentRequest!=song)return
        PlaybackDiagnostics.event("Stopped on failed audio; automatic skip blocked")
        playbackFailed=true
        playbackMessage="No playable audio found. Retry or check your sources."
        onStreamFailed?.invoke(song)
    }
    @Volatile var onStreamFailed: ((String) -> Unit)? = null

    private data class CandidateScore(
        val item: SongItem,
        val score: Double,
        val titleScore: Double,
        val artistScore: Double,
        val artistEvidenceScore: Double,
        val durationScore: Double?,
        val albumScore: Double?,
        val alternatePenalty: Double,
        val unexpectedAlternates: List<String>,
    )

    private fun normalizedForMatch(value: String): String =
        value.lowercase()
            .replace(featSearchPattern, "")
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private val anyLatinTransliterator by lazy {
        runCatching {
            android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII")
        }.getOrNull()
    }

    private fun foldLatinDiacritics(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("""\p{Mn}+"""), "")

    private fun transliterateCyrillic(value: String): String {
        val map = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya",
        )
        return buildString {
            value.lowercase().forEach { ch ->
                append(map[ch] ?: ch)
            }
        }
    }

    private fun bigramSimilarity(a: String, b: String): Double {
        fun variants(value: String): List<String> =
            listOfNotNull(
                value,
                foldLatinDiacritics(value),
                transliterateCyrillic(value),
                anyLatinTransliterator?.transliterate(value),
            )
                .map(::normalizedForMatch)
                .filter { it.isNotBlank() }
                .distinct()

        fun score(na: String, nb: String): Double {
            if (na == nb) return 1.0
            if (na.length < 2 || nb.length < 2) return 0.0
            val aBigrams = na.windowed(2).toSet()
            val bBigrams = nb.windowed(2).toSet()
            if (aBigrams.isEmpty() || bBigrams.isEmpty()) return 0.0
            val intersection = aBigrams.count { it in bBigrams }
            return (2.0 * intersection) / (aBigrams.size + bBigrams.size)
        }
        return variants(a).maxOf { aa ->
            variants(b).maxOf { bb -> score(aa, bb) }
        }
    }

    private data class VersionMarker(
        val name: String,
        val pattern: Regex,
        val hardReject: Boolean,
    )

    private fun markerPattern(terms: String): Regex =
        Regex("""(^|\s)($terms)(\s|$)""", RegexOption.IGNORE_CASE)

    private val alternateVersionMarkers = listOf(
        VersionMarker("remix", markerPattern("""re\s*mix|rmx|club mix|dance mix|dub mix|vip mix|ремикс|рмикс"""), true),
        VersionMarker("alternate", markerPattern("""alternative|alternate|alt version|demo|demo version|unreleased|rough mix|early version|альтернатив\w*|демо|неиздан\w*|чернов\w*"""), true),
        VersionMarker("sped up", markerPattern("""sped\s*up|speed\s*up|fast version|ускоренн\w*|быстрая версия"""), true),
        VersionMarker("slowed", markerPattern("""slowed|slowed reverb|slow version|замедленн\w*|медленная версия"""), true),
        VersionMarker("nightcore", markerPattern("""nightcore|daycore"""), true),
        VersionMarker("live", markerPattern("""live|concert|session|performance|лайв|концерт|с концерта|выступлен\w*"""), true),
        VersionMarker("acoustic", markerPattern("""acoustic|unplugged|piano version|guitar version|акустик\w*|пианино|гитар\w*"""), true),
        VersionMarker("cover", markerPattern("""cover|covered by|tribute|кавер|трибьют"""), true),
        VersionMarker("karaoke", markerPattern("""karaoke|minus one|караоке|минусовка"""), true),
        VersionMarker("instrumental", markerPattern("""instrumental|no vocals|инструментал|без вокала"""), true),
        VersionMarker("mashup", markerPattern("""mashup|mash up|bootleg|rework|flip|мешап|мэшап|бутлег"""), true),
        VersionMarker("fan edit", markerPattern("""fan edit|fanmade|right version|edit audio|перезалив|перезалит\w*"""), true),
        VersionMarker("extended", markerPattern("""extended mix|extended version|12 inch|12"""), false),
        VersionMarker("radio edit", markerPattern("""radio edit|single edit|edit version"""), false),
        VersionMarker("remaster", markerPattern("""remaster|remastered|anniversary edition"""), false),
    )

    private fun versionMarkers(value: String): Set<String> {
        val normalized = normalizedForMatch(value)
        return alternateVersionMarkers
            .filter { marker -> marker.pattern.containsMatchIn(normalized) }
            .map { it.name }
            .toSet()
    }

    private fun hardVersionMarkers(names: Collection<String>): Set<String> {
        val hardNames = alternateVersionMarkers.filter { it.hardReject }.map { it.name }.toSet()
        return names.filterTo(mutableSetOf()) { it in hardNames }
    }

    private fun ytmusicTransferScore(
        candidate: SongItem,
        expected: TrackMatchMetadata,
        expectedDurationMs: Int,
    ): CandidateScore {
        var candidateTitle = candidate.title
        if (candidate.isVideoSong) {
            val split = candidateTitle.split("-", limit = 2)
            if (split.size == 2) candidateTitle = split[1].trim()
        }

        val titleScore = bigramSimilarity(candidateTitle, expected.title)
        val uploaderArtistScore = bigramSimilarity(
            candidate.artists.joinToString(" ") { it.name },
            expected.artist,
        )
        val titleArtistScore = if (candidate.isVideoSong) {
            bigramSimilarity(candidate.title.substringBefore("-"), expected.artist)
        } else {
            0.0
        }
        val artistScore = maxOf(uploaderArtistScore, titleArtistScore)

        val expectedDurationSec = expectedDurationMs / 1000.0
        val candidateDuration = candidate.duration
        val durationScore = if (expectedDurationSec > 0 && candidateDuration != null) {
            (1.0 - abs(candidateDuration - expectedDurationSec) * 2.0 / expectedDurationSec)
                .coerceIn(0.0, 1.0)
        } else {
            null
        }

        val albumScore = if (!candidate.isVideoSong && expected.album.isNotBlank()) {
            candidate.album?.name?.let { bigramSimilarity(it, expected.album) }
        } else {
            null
        }

        val expectedMarkers = versionMarkers(expected.title)
        val candidateMarkers = versionMarkers(
            listOf(
                candidate.title,
                candidate.album?.name.orEmpty(),
            ).joinToString(" "),
        )
        val unexpectedAlternates = (candidateMarkers - expectedMarkers).toList().sorted()
        val hardUnexpected = hardVersionMarkers(unexpectedAlternates).size
        val softUnexpected = unexpectedAlternates.size - hardUnexpected
        val alternatePenalty = hardUnexpected * 1.75 + softUnexpected * 0.65

        val parts = mutableListOf(titleScore, artistScore)
        durationScore?.let { parts += it * 5.0 }
        albumScore?.let { parts += it }
        val resultTypeBoost = if (candidate.isVideoSong) 1.0 else 2.0
        val baseScore = parts.average() * resultTypeBoost
        return CandidateScore(
            item = candidate,
            score = (baseScore - alternatePenalty).coerceAtLeast(0.0),
            titleScore = titleScore,
            artistScore = uploaderArtistScore,
            artistEvidenceScore = artistScore,
            durationScore = durationScore,
            albumScore = albumScore,
            alternatePenalty = alternatePenalty,
            unexpectedAlternates = unexpectedAlternates,
        )
    }

    private fun CandidateScore.isAcceptableMatch(): Boolean {
        val durationStrong = durationScore?.let { it >= 0.94 } ?: false
        val albumUseful = albumScore?.let { it >= 0.45 } ?: false
        val hasDuration = durationScore != null
        val minScore = when {
            hasDuration && item.isVideoSong -> 1.55
            hasDuration -> 2.25
            item.isVideoSong -> 0.78
            else -> 1.35
        }

        // Duration is deliberately weighted heavily, as in spotify_to_ytmusic,
        // but these gates prevent a same-length wrong-artist song from winning.
        // If Spotify did not ask for a remix/live/acoustic/etc version, do not
        // let that alternate upload win just because its title and length are close.
        // When duration is missing, allow strong title+artist/album matches
        // instead of rejecting every possible candidate.
        val hasUnexpectedHardAlternate = hardVersionMarkers(unexpectedAlternates).isNotEmpty()
        val sameRecording = titleScore >= 0.82 && artistEvidenceScore >= 0.60 &&
            durationScore?.let {it>=0.94}==true
        return (score >= minScore || sameRecording) &&
            !hasUnexpectedHardAlternate &&
            titleScore >= 0.45 &&
            (
                artistEvidenceScore >= 0.32 ||
                    (albumUseful && artistEvidenceScore >= 0.18) ||
                    (durationStrong && artistEvidenceScore >= 0.25)
                )
    }

    private suspend fun ensureSpotifyMatchMetadata(query: String): TrackMatchMetadata? {
        val currentMeta = metadataRegistry[query]
        val hasUsefulMeta = currentMeta?.let {
            it.title.isNotBlank() && it.artist.isNotBlank() && it.album.isNotBlank()
        } ?: false
        if (hasUsefulMeta && durationRegistry[query] != null && explicitRegistry.containsKey(query) &&
            spotifyMetadataRepaired.contains(query)) {
            return currentMeta
        }

        val spotifyId = trackIdRegistry[query] ?: spotifyTrackIdForPlayback(query) ?: return currentMeta
        val track = runCatching { com.metrolist.spotify.Spotify.track(spotifyId).getOrNull() }
            .onFailure { Log.w(TAG, "Spotify metadata repair failed for $spotifyId", it) }
            .getOrNull()
            ?: return currentMeta

        val repaired = TrackMatchMetadata(
            title = track.name,
            artist = track.artists.joinToString(", ") { it.name },
            album = track.album?.name ?: currentMeta?.album.orEmpty(),
            isrc = track.isrc.orEmpty(),
        )
        metadataRegistry[query] = repaired
        trackIdRegistry[query] = spotifyId
        explicitRegistry[query] = track.explicit
        if (track.durationMs > 0) durationRegistry[query] = track.durationMs
        spotifyMetadataRepaired += query
        return repaired
    }


    private suspend fun resolveVideoCandidates(query:String,filter:YouTube.SearchFilter=YouTube.SearchFilter.FILTER_SONG):List<String> {
        val text=searchTextForPlayback(query)
        if(text.length==11 && !text.contains(' '))return listOf(text)
        val cacheKey=query+":"+filter
        if(candidateCache.size>256)candidateCache.clear()
        candidateCache[cacheKey]?.let{return it}
        // Queue metadata is already registered. A redundant Spotify request used to
        // precede every first play, even when title/artist/duration were available.
        val meta=metadataRegistry[query] ?: kotlinx.coroutines.withTimeoutOrNull(1500) {ensureSpotifyMatchMetadata(query)}
        // A true badge is positive evidence. GQL's default false is often an
        // absent badge, so only a repaired metadata response confirms clean.
        val explicit=explicitRegistry[query]?.takeIf{it || spotifyMetadataRepaired.contains(query)}
        fun rank(hits:List<SongItem>):List<String> {
            if(meta==null)return hits.filter {item ->
                val combined=normalizedForMatch(item.title+" "+item.artists.joinToString(" "){it.name})
                val words=normalizedForMatch(text).split(" ").filter {it.length>2}
                words.isNotEmpty() && words.count {combined.contains(it)} >= (words.size*.75).toInt().coerceAtLeast(1)
            }.map{it.id}.take(3)
            return hits.map {ytmusicTransferScore(it,meta,durationRegistry[query] ?: 0)}
                .filter {it.isAcceptableMatch()}
                .filter {MatchPolicy.editionAllowed(explicit,it.item.explicit,it.item.title,it.titleScore,it.durationScore)}
                .sortedWith(compareByDescending<CandidateScore>{explicit==null || it.item.explicit==explicit}.thenByDescending{it.score})
                .map{it.item.id}.distinct().take(3)
        }
        suspend fun search(term:String):List<SongItem> = kotlinx.coroutines.withTimeoutOrNull(7000) {
            YouTube.search(term,filter).getOrNull()?.items?.filterIsInstance<SongItem>()
        }.orEmpty()
        rank(search(text)).takeIf{it.isNotEmpty()}?.let {candidateCache[cacheKey]=it;return it}
        // Only broaden after a miss; ISRC is not an indexed identifier in every catalog.
        val alternatives=buildList {
            if(!meta?.isrc.isNullOrBlank())add(meta!!.isrc)
            if(meta!=null)add(meta.title+" "+meta.artist.substringBefore(','))
        }.distinct().filter{it!=text}.take(2)
        val extra=kotlinx.coroutines.coroutineScope {
            alternatives.map{term->async(Dispatchers.IO){search(term)}}.awaitAll().flatten()
        }
        val matched=rank(extra)
        if(matched.isNotEmpty())candidateCache[cacheKey]=matched
        return matched
    }

    /**
     * Resolves a playable YouTube stream for [query], falling back through up to
     * 3 ranked video candidates when one has no obtainable stream.
     */
    internal suspend fun resolveYtPlayback(
        query: String,
        audioQuality: com.metrolist.music.constants.AudioQuality,
        appContext: Context,
    ): YTPlayerUtils.PlaybackData? =
        // The PoToken/solver WebViews wait on JS callbacks with no deadline; a hung
        // WebView left playback at 0:00 forever (#95, #79) instead of failing over.
        kotlinx.coroutines.withTimeoutOrNull(45_000L) {
            resolveYtPlaybackUnbounded(query, audioQuality, appContext)
        } ?: null.also { Log.w(TAG, "YouTube resolve failed or timed out for: ${searchTextForPlayback(query)}") }

    private suspend fun resolveYtPlaybackUnbounded(
        query: String,
        audioQuality: com.metrolist.music.constants.AudioQuality,
        appContext: Context,
    ): YTPlayerUtils.PlaybackData? {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val tried = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        suspend fun tryIds(ids:List<String>):YTPlayerUtils.PlaybackData? {
            val candidates=ids.distinct().filter{tried.add(it)}.take(3)
            for(batch in candidates.chunked(2)) {
            val result=firstPlayable(batch.map {videoId ->
                val connections=LookupConnections()
                StreamLookup(cancel={connections.close()}) {
                    withContext(connections) {kotlinx.coroutines.withTimeoutOrNull(14000) {
                        YTPlayerUtils.playerResponseForPlayback(videoId=videoId,audioQuality=audioQuality,connectivityManager=connectivityManager).getOrNull()
                    }}
                }
            },timeoutMs=15000)
            if(result!=null)return result
            }
            return null
        }
        val filters=buildList {
            add(YouTube.SearchFilter.FILTER_SONG)
            if(io.github.unidentified24.spock.data.preferences.isVideoFallbackEnabled(appContext))add(YouTube.SearchFilter.FILTER_VIDEO)
        }
        firstPlayable(filters.map {filter ->
            val connections=LookupConnections()
            StreamLookup(cancel={connections.close()}) {
                withContext(connections) {tryIds(resolveVideoCandidates(query,filter).take(3))}
            }
        })?.let{return it}
        candidateCache.remove(query+":"+YouTube.SearchFilter.FILTER_SONG)
        candidateCache.remove(query+":"+YouTube.SearchFilter.FILTER_VIDEO)
        Log.e(TAG, "No playable YouTube match")
        return null
    }

    private fun buildAudioAttributes() =
        androidx.media3.common.AudioAttributes.Builder()
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .build()

    /**
     * Build an ExoPlayer that reads through the shared media cache (so preloaded intro
     * bytes are reused) and carries its own [CrossfadeFilterAudioProcessor] so the DJ-style
     * low/high-pass sweep can be applied per track during a crossfade. The filter is
     * disabled (pass-through) outside a crossfade, so there's no overhead in normal playback.
     *
     * @param handleAudioFocus true for the active/session player, false for the transient
     *   secondary (incoming) player so it doesn't fight the primary for focus mid-fade.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun createPlayerWithFilter(
        context: Context,
        handleAudioFocus: Boolean,
    ): Pair<ExoPlayer, io.github.unidentified24.spock.audio.CrossfadeFilterAudioProcessor> {
        val filter = io.github.unidentified24.spock.audio.CrossfadeFilterAudioProcessor()
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): androidx.media3.exoplayer.audio.AudioSink =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain(
                            filter,
                        ),
                    ).build()
        }
        val p = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    // Routes deezer:// URIs to the decrypting DeezerDataSource and
                    // everything else through the normal cached HTTP stack.
                    io.github.unidentified24.spock.deezer.DeezerAwareDataSourceFactory(cacheDataSourceFactory(context)),
                ),
            )
            .setLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000,45000,750,1500).build())
            .setRenderersFactory(renderers)
            .setAudioAttributes(buildAudioAttributes(), handleAudioFocus)
            .setHandleAudioBecomingNoisy(handleAudioFocus)
            .build()
        return p to filter
    }

    private fun ensurePlayer(context: Context) {
        appCtx = context.applicationContext
        if (player == null) {
            val (p, filter) = createPlayerWithFilter(context, handleAudioFocus = true)
            player = p
            currentPlayerFilter = filter
            p.addListener(midPlayErrorListener)
            onPlayerCreated?.invoke(p)
        }
    }

    /**
     * Catches ExoPlayer errors that happen mid-stream (expired CDN URL, network drop,
     * HTTP 403/404, decode failure, etc.) — none of which were previously handled,
     * so the player would silently go idle.
     *
     * IO errors (2xxx) get one re-resolve attempt: the cached URL is discarded and a
     * fresh one is fetched; if that succeeds we resume from where we left off.
     * A failed retry leaves the selected track available for explicit recovery.
     */
    private val midPlayErrorListener = object : Player.Listener {
        // Track end is detected here, not in the UI: Compose stops when the screen is
        // off or the app is in the background, so the queue used to stall there.
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val query = mediaItem?.mediaId ?: return
            if (query != preloadedNext) return
            // Gapless move onto the preloaded track: it is now the current request.
            preloadedNext = null
            currentRequest = query
            heardCurrent=player?.isPlaying==true
            playbackFailed=false
            retriedRequest = null
            val p = player ?: return
            if (p.currentMediaItemIndex > 0) p.removeMediaItems(0, p.currentMediaItemIndex)
            currentSource=sourceCache[query] ?: currentSource
            currentQuality=qualityCache[query].orEmpty()
            onPreloadedTrackStarted?.invoke(query)
        }

        // Play/pause from the notification, headset or car goes straight to the player;
        // mirror it so the in-app button doesn't keep showing the old state.
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!isCrossfading) boundState?.updatePlayingState(playWhenReady)
        }

        override fun onIsPlayingChanged(isPlaying:Boolean) {
            if(isPlaying) {
                heardCurrent=true
                PlaybackDiagnostics.event("Audio playback started")
                playbackMessage="";playbackFailed=false
                val state=boundState;val q=state?.queue?.value.orEmpty()
                val i=q.indexOfFirst{it.url==currentRequest}
                if(i>=0)q.getOrNull(i+1)?.let{next->appCtx?.let{prefetch(next.url,it)}}
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // Mid-crossfade the outgoing player may end first; the blend advances itself.
            // An emptied playlist also reports ENDED (playSong clears it on every tap);
            // treating that as a finished song skipped the track the user just picked.
            if (playbackState == Player.STATE_ENDED && !isCrossfading &&
                (player?.mediaItemCount ?: 0) > 0
            ) {
                val p=player
                if(PlaybackCompletion.isNaturalEnd(heardCurrent,p?.currentPosition ?: 0,p?.duration ?: 0,(durationRegistry[currentRequest] ?: 0).toLong()))onTrackEnded?.invoke()
                else {
                    streamCache[currentRequest]?.let{rejectedStreams.add(it)}
                    reportStreamFailure(currentRequest)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // A queued-ahead track can fail after the player moved onto it.
            val song = player?.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() }
                ?: currentRequest.takeIf { it.isNotBlank() } ?: return
            preloadedNext = null
            Log.w(TAG, "mid-play error for $song: ${error.errorCodeName} (code ${error.errorCode})")
            streamCache[song]?.let{rejectedStreams.add(it)}
            if(rejectedStreams.size>128)rejectedStreams.clear()
            // Drop the cached URL — it may have expired (Deezer CDN, YouTube signing).
            streamExpiry.remove(song)
        streamCache.remove(song)
            sourceCache.remove(song)
            val ctx = appCtx ?: run { reportStreamFailure(song); return }
            // Re-resolve again only if playback got further since the last one: YouTube
            // can 403 a stream after ~1 min (#76, #48), and each fresh URL plays on. A URL
            // that fails at the same spot again is dead, so skip instead of looping.
            val pos = player?.currentPosition ?: 0L
            val madeProgress = retriedRequest != song || pos > retriedAtMs + 10_000L
            val isIoError = error.errorCode in 2000..2999 && madeProgress
            retriedRequest = song
            retriedAtMs = pos
            scope.launch {
                if (isIoError) {
                    // Brief wait so a transient network blip has time to recover.
                    delay(350L)
                    // User may have manually skipped while we were waiting — bail if so.
                    if (currentRequest != song) return@launch
                    val fresh = resolveStreamUrl(song, ctx, forPlayback = true)
                    if (fresh != null && currentRequest == song) {
                        val savedPos = withContext(Dispatchers.Main) { player?.currentPosition ?: 0L }
                        withContext(Dispatchers.Main) {
                            player?.setMediaItem(buildMediaItem(fresh, streamMimeType(fresh)))
                            player?.prepare()
                            if (savedPos > 5_000L) player?.seekTo(savedPos)
                            player?.play()
                        }
                        return@launch
                    }
                }
                // Stop on this track and expose retry/source settings.
                withContext(Dispatchers.Main) { reportStreamFailure(song) }
            }
        }
    }

    /** The live ExoPlayer instance (may be null before first play). */
    val exoPlayer: ExoPlayer? get() = player

    /** Make sure the player exists (used by the media-session service). */
    fun ensureCreated(context: Context) = ensurePlayer(context.applicationContext)

    /** Notified right after the ExoPlayer is built so the session can attach to it. */
    @Volatile var onPlayerCreated: ((ExoPlayer) -> Unit)? = null

    fun isPlaying(): Boolean {
        if (webPlaybackActive()) return SpotifyWebPlayer.isPlaying
        return player?.isPlaying ?: false
    }

    fun webPlaybackActive(): Boolean {
        if (!webPlayerEnabled) return false
        val ctx = appCtx ?: return false
        // Spotify web playback needs: user hasn't opted out, the WebView actually has
        // Widevine, AND the user is logged into Spotify (sp_dc). Missing any of these
        // → fall back to the YouTube/FLAC engine so playback is never silent.
        return io.github.unidentified24.spock.data.preferences.isWebPlaybackEnabled(ctx) &&
            SpotifyWebPlayer.canPlay &&
            io.github.unidentified24.spock.data.api.SpotifySession.spDc(ctx).isNotBlank()
    }

    // ── Session restore (survive app restarts) ──
    // Set at launch from the persisted playback state; the first playSong for
    // this query seeks to the saved position, and play() with an empty player
    // re-resolves the track instead of doing nothing.
    @Volatile private var restoreQuery: String? = null
    @Volatile private var restorePositionMs: Long = 0L

    fun setRestorePoint(query: String, positionMs: Long) {
        if (query.isBlank()) return
        restoreQuery = query
        restorePositionMs = positionMs.coerceAtLeast(0L)
    }

    fun play() {
        if (webPlaybackActive()) { SpotifyWebPlayer.resume(); return }
        // Fresh launch: nothing loaded yet — resume the restored session track.
        if (playbackFailed && currentRequest.isNotBlank()) {appCtx?.let{playSong(currentRequest,it)};return}
        if ((player?.mediaItemCount ?: 0) == 0) {
            val q = restoreQuery ?: currentRequest.takeIf{it.isNotBlank()}
            val ctx = appCtx
            if (q != null && ctx != null) { playSong(q, ctx); return }
        }
        player?.play()
    }

    fun pause() {
        playRequestJob?.cancel()
        playbackLookups.toList().forEach{it.cancel()}
        cancelCrossfade()
        if (webPlaybackActive()) { SpotifyWebPlayer.pause(); return }
        player?.let {
            it.playWhenReady = false
            // Remember where we stopped so a relaunch can resume mid-track.
            appCtx?.let { ctx ->
                val pos = it.currentPosition
                if (pos > 0) io.github.unidentified24.spock.data.preferences.saveLastPosition(ctx, pos)
            }
        }
    }

    fun stop() {
        playRequestJob?.cancel()
        playbackLookups.toList().forEach { it.cancel() }
        cancelCrossfade()
        player?.stop()
    }

    fun seekTo(position: Long) {
        cancelCrossfade()
        if (webPlaybackActive()) { SpotifyWebPlayer.seekTo(position); return }
        player?.seekTo(position)
    }

    fun release() {
        matchPrefetch?.cancel()
        playRequestJob?.cancel()
        playbackLookups.toList().forEach { it.cancel() }
        positionWatchJob?.cancel()
        cancelCrossfade()
        player?.release()
        player = null
    }

    fun getDuration(): Long {
        if (webPlaybackActive()) return SpotifyWebPlayer.durationMs
        return player?.duration ?: 0L
    }

    fun getCurrentPosition(): Long {
        if (webPlaybackActive()) return SpotifyWebPlayer.positionMs
        return player?.currentPosition ?: 0L
    }

    fun isPrepared(): Boolean {
        val playerState = player?.playbackState
        return playerState != null && playerState != ExoPlayer.STATE_IDLE && playerState != ExoPlayer.STATE_ENDED
    }

    // ── Crossfade + DJ-style mixing ──
    // The end of the current track is blended into the start of the next over a user-set
    // window (Settings). A second, transient ExoPlayer plays the incoming track while the
    // primary fades out; volumes follow an equal-power (cos/sin) curve so total loudness
    // stays constant. In DJ mode, the outgoing track is low-passed (treble drops out) and the
    // incoming track high-passed (bass fills in) via per-player [CrossfadeFilterAudioProcessor]s,
    // swept on an S-curve — like a real DJ mixer. When the blend finishes the secondary player
    // is promoted to primary and the media session is re-bound to it via [onPlayerSwapped].
    private const val CF_LPF_START_HZ = 20000f
    private const val CF_LPF_END_HZ = 200f
    private const val CF_HPF_START_HZ = 2000f
    private const val CF_HPF_END_HZ = 20f
    private const val CF_SIGMOID_K = 6f

    @Volatile private var appCtx: Context? = null
    @Volatile private var boundState: CurrentSongState? = null
    @Volatile private var currentPlayerFilter: io.github.unidentified24.spock.audio.CrossfadeFilterAudioProcessor? = null
    @Volatile private var secondaryPlayer: ExoPlayer? = null
    @Volatile private var secondaryPlayerFilter: io.github.unidentified24.spock.audio.CrossfadeFilterAudioProcessor? = null
    @Volatile private var isCrossfading = false
    @Volatile private var crossfadeJob: kotlinx.coroutines.Job? = null
    @Volatile private var positionWatchJob: kotlinx.coroutines.Job? = null
    @Volatile private var pendingNextSong: io.github.unidentified24.spock.data.entity.SongsModel? = null
    @Volatile private var pendingNextSongIdx: Int = 0

    /** Notified (on the main thread) when the active ExoPlayer instance changes after a
     *  crossfade, so the media session can re-bind to the promoted player. */
    @Volatile var onPlayerSwapped: ((ExoPlayer) -> Unit)? = null

    /** Give the player access to the shared queue/now-playing state so it can advance the
     *  app's notion of "current track" itself when a crossfade fires. Called once at startup. */
    fun bindState(state: CurrentSongState) { boundState = state }

    fun isCrossfadeActive(): Boolean = isCrossfading

    private fun sigmoid(t: Float): Float = 1.0f / (1.0f + exp(-CF_SIGMOID_K * (t - 0.5f)))

    private fun expInterpolate(start: Float, end: Float, t: Float): Float {
        if (start <= 0f || end <= 0f) return end
        return exp(ln(start) + (ln(end) - ln(start)) * t).toFloat()
    }

    /** Cancel an in-flight crossfade and tear down the secondary player, restoring the
     *  primary to full volume with its filter disabled. Safe to call when not crossfading. */
    private fun cancelCrossfade() {
        if (!isCrossfading && secondaryPlayer == null) return
        crossfadeJob?.cancel()
        crossfadeJob = null
        currentPlayerFilter?.enabled = false
        secondaryPlayerFilter?.enabled = false
        runCatching { secondaryPlayer?.release() }
        secondaryPlayer = null
        secondaryPlayerFilter = null
        player?.volume = 1f
        isCrossfading = false
        pendingNextSong = null
    }

    /** (Re)start the loop that watches playback position and fires a crossfade as the
     *  current track approaches its end. */
    private var posSaveTick = 0

    private fun startPositionWatch() {
        positionWatchJob?.cancel()
        positionWatchJob = scope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(250)
                val ctx = appCtx ?: continue
                // Persist the position every ~3s so a relaunch resumes mid-track.
                if (++posSaveTick % 12 == 0 && !webPlaybackActive()) {
                    player?.let { p ->
                        val pos = withContext(Dispatchers.Main) {
                            if (p.isPlaying) p.currentPosition else -1L
                        }
                        if (pos > 0) io.github.unidentified24.spock.data.preferences.saveLastPosition(ctx, pos)
                    }
                }
                if (isCrossfading) continue
                val crossfadeMs = io.github.unidentified24.spock.data.preferences.getCrossfadeMs(ctx)
                managePreload(ctx, crossfadeMs)
                if (crossfadeMs <= 0) continue
                val state = boundState ?: continue
                if (state.repeat.value) continue // repeat-one loops the same track
                val p = player ?: continue
                val playing = withContext(Dispatchers.Main) { p.isPlaying }
                if (!playing) continue
                val dur = withContext(Dispatchers.Main) { p.duration }
                val pos = withContext(Dispatchers.Main) { p.currentPosition }
                if (dur <= 0 || pos < 0) continue
                if (pos >= dur - crossfadeMs) {
                    triggerCrossfade(ctx, crossfadeMs)
                }
            }
        }
    }

    /**
     * Queue the next track inside ExoPlayer ~30 s before the current one ends, so it
     * starts gaplessly and the session never reports "stopped" between tracks.
     * Crossfade and repeat keep their own paths; a stale preload is removed.
     */
    private suspend fun managePreload(ctx: Context, crossfadeMs: Int) {
        if(!io.github.unidentified24.spock.data.preferences.isPreloadEnabled(ctx))return
        // Short-lived lossless and Deezer URLs must be minted at the actual play.
        val shortLived=(losslessStreaming && io.github.unidentified24.spock.data.preferences.isFlacSourceEnabled(ctx) &&
            io.github.unidentified24.spock.data.preferences.currentStreamingQuality(ctx).lossless) ||
            (deezerEnabled && io.github.unidentified24.spock.data.preferences.isDeezerRaceEnabled(ctx))
        val state = boundState ?: return
        val p = player ?: return
        val next = withContext(Dispatchers.Main) {
            val q = state.queue.value
            val cur = q.indexOfFirst { it.id == state.songId.value }
            q.getOrNull(cur + 1)?.takeIf { cur >= 0 }
        }
        val wanted = next?.url?.takeIf { crossfadeMs <= 0 && !state.repeat.value && !webPlaybackActive() }
        val queued = preloadedNext
        if (queued != null && queued != wanted) {
            // Repeat/crossfade turned on or the queue changed: drop the queued track.
            withContext(Dispatchers.Main) {
                if (p.mediaItemCount > 1) p.removeMediaItems(1, p.mediaItemCount)
            }
            preloadedNext = null
        }
        if (wanted == null || preloadedNext != null || preloading) return
        val (pos, dur, ready) = withContext(Dispatchers.Main) {
            Triple(p.currentPosition, p.duration, p.playbackState == Player.STATE_READY && p.mediaItemCount == 1)
        }
        if (!ready || dur <= 0 || dur - pos > (if(shortLived)8000L else 30000L)) return
        val song = next
        preloading = true
        try {
            val url = runCatching { resolveStreamUrl(song.url, ctx) }.getOrNull() ?: return
            withContext(Dispatchers.Main) {
                // Only if nothing changed while resolving.
                if (player !== p || p.mediaItemCount != 1 || state.queue.value.none { it.url == song.url }) return@withContext
                val mime = streamMimeType(url, sourceCache[song.url].orEmpty())
                p.addMediaItem(buildMediaItem(url, mime, song.url, song.title, song.singer, song.coverUri))
                preloadedNext = song.url
                Log.d(TAG, "queued next track ahead: ${song.title}")
            }
        } finally {
            preloading = false
        }
    }

    /** Begin blending the current track into the next queue item. */
    private fun triggerCrossfade(ctx: Context, configuredMs: Int) {
        if (isCrossfading) return
        val state = boundState ?: return
        val q = state.queue.value
        if (q.isEmpty()) return
        val cur = q.indexOfFirst { it.id == state.songId.value }
        if (cur < 0 || cur >= q.size - 1) return // last track ends normally
        val nextSong = q[cur + 1]
        isCrossfading = true
        scope.launch {
            try {
                val nextUrl = resolveStreamUrl(nextSong.url, ctx, forPlayback = true) ?: run {
                    isCrossfading = false; return@launch
                }
                // Effective duration: never longer than the real time left on the outgoing track.
                val remaining = withContext(Dispatchers.Main) {
                    val p = player ?: return@withContext configuredMs.toLong()
                    val d = p.duration; val ps = p.currentPosition
                    if (d > 0 && ps >= 0) (d - ps) else configuredMs.toLong()
                }
                val effectiveMs = minOf(configuredMs.toLong(), remaining).coerceAtLeast(1000L).toInt()
                val djMode = io.github.unidentified24.spock.data.preferences.isCrossfadeDjMode(ctx)

                // Stash for finalizeCrossfade, which updates the UI when the new track
                // is the sole audio source (not at blend-start while the old track fades).
                pendingNextSong = nextSong
                pendingNextSongIdx = cur + 1
                // Pre-load metadata so buildMediaItem() tags the secondary player correctly.
                setNowPlayingMeta(nextSong.title, nextSong.singer, nextSong.coverUri)
                withContext(Dispatchers.Main) {
                    val (sp, sf) = createPlayerWithFilter(ctx, handleAudioFocus = false)
                    secondaryPlayer = sp
                    secondaryPlayerFilter = sf
                    sp.setMediaItem(buildMediaItem(nextUrl, streamMimeType(nextUrl)))
                    sp.prepare()
                    sp.volume = 0f
                    sp.playWhenReady = true
                }
                performCrossfade(effectiveMs, djMode)
            } catch (e: Exception) {
                Log.e(TAG, "crossfade failed", e)
                cancelCrossfade()
            }
        }
    }

    private suspend fun performCrossfade(effectiveMs: Int, djMode: Boolean) {
        val steps = 50
        val delayPerStep = (effectiveMs / steps).coerceAtLeast(20)
        if (djMode) {
            currentPlayerFilter?.apply {
                filterType = io.github.unidentified24.spock.audio.BiquadFilter.FilterType.LOW_PASS
                cutoffFrequencyHz = CF_LPF_START_HZ; enabled = true
            }
            secondaryPlayerFilter?.apply {
                filterType = io.github.unidentified24.spock.audio.BiquadFilter.FilterType.HIGH_PASS
                cutoffFrequencyHz = CF_HPF_START_HZ; enabled = true
            }
        }
        crossfadeJob?.cancel()
        val job = scope.launch {
            try {
                for (step in 0..steps) {
                    if (!isActive) break
                    val progress = step.toFloat() / steps
                    val angle = (progress * PI / 2).toFloat()
                    withContext(Dispatchers.Main) {
                        player?.volume = cos(angle)
                        secondaryPlayer?.volume = sin(angle)
                        if (djMode) {
                            val fp = sigmoid(progress)
                            currentPlayerFilter?.cutoffFrequencyHz = expInterpolate(CF_LPF_START_HZ, CF_LPF_END_HZ, fp)
                            secondaryPlayerFilter?.cutoffFrequencyHz = expInterpolate(CF_HPF_START_HZ, CF_HPF_END_HZ, fp)
                        }
                    }
                    kotlinx.coroutines.delay(delayPerStep.toLong())
                }
                finalizeCrossfade()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            }
        }
        crossfadeJob = job
        job.join()
    }

    private suspend fun finalizeCrossfade() {
        withContext(Dispatchers.Main) {
            val incoming = secondaryPlayer ?: run { isCrossfading = false; return@withContext }
            val old = player
            // Promote the incoming (secondary) player to primary.
            currentPlayerFilter?.enabled = false
            secondaryPlayerFilter?.enabled = false
            player = incoming
            currentPlayerFilter = secondaryPlayerFilter
            secondaryPlayer = null
            secondaryPlayerFilter = null
            incoming.volume = 1f
            // The promoted player now owns audio focus / becoming-noisy handling.
            incoming.setAudioAttributes(buildAudioAttributes(), /* handleAudioFocus = */ true)
            incoming.setHandleAudioBecomingNoisy(true)
            // Error recovery and end-of-track detection follow the promoted player.
            incoming.addListener(midPlayErrorListener)
            runCatching { old?.stop(); old?.release() }
            isCrossfading = false
            // Now that the new track is the sole audio source, switch the in-app UI
            // (cover art, canvas, title) and update the playback identity for error recovery.
            pendingNextSong?.let { next ->
                currentRequest = next.url
                boundState?.updateSongState(
                    next.coverUri, next.title, next.singer, true,
                    next.id, pendingNextSongIdx, next.album,
                )
                pendingNextSong = null
            }
            // Re-bind the media session to the new player.
            onPlayerSwapped?.invoke(incoming)
        }
        // Watch the newly-promoted track for its own end.
        startPositionWatch()
    }

    // ── Sleep timer ──
    // Pauses playback after a delay. A new call replaces any pending timer;
    // passing 0 (or calling cancelSleepTimer) clears it.
    @Volatile private var sleepJob: kotlinx.coroutines.Job? = null
    @Volatile var sleepTimerEndAt: Long = 0L
        private set

    fun setSleepTimer(durationMillis: Long) {
        sleepJob?.cancel()
        if (durationMillis <= 0L) {
            sleepTimerEndAt = 0L
            return
        }
        sleepTimerEndAt = System.currentTimeMillis() + durationMillis
        sleepJob = scope.launch {
            kotlinx.coroutines.delay(durationMillis)
            withContext(Dispatchers.Main) { pause() }
            sleepTimerEndAt = 0L
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepTimerEndAt = 0L
    }
}
