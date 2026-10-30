package io.github.unidentified24.spock.di

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.*
import io.github.unidentified24.spock.playback.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Offline downloads: resolves a track's best source and saves it under filesDir/downloads. */
object Downloads {
    private const val TAG = "Downloads"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Tracks which song queries are mid-download so the UI can show a spinner and
    // we don't kick off the same download twice.
    private val downloading = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )
    @Volatile var onDownloadsChanged: (() -> Unit)? = null

    // Per-query download progress, 0..100. Present only while a download is active.
    private val downloadProgress = java.util.concurrent.ConcurrentHashMap<String, Int>()
    // The actual SongsModel of each in-progress download, so the Downloads screen can
    // render it (with a progress bar) before the file exists / it's added to prefs.
    private val downloadingSongs =
        java.util.concurrent.ConcurrentHashMap<String, io.github.unidentified24.spock.data.entity.SongsModel>()

    fun isDownloading(query: String): Boolean = downloading.contains(query)

    /** Current download progress (0..100) for a query, or -1 if unknown/not downloading. */
    fun downloadProgress(query: String): Int = downloadProgress[query] ?: -1

    /** Snapshot of the currently-downloading tracks paired with their percent (0..100). */
    fun downloadingSnapshot(): List<Pair<io.github.unidentified24.spock.data.entity.SongsModel, Int>> =
        downloadingSongs.entries.map { (q, song) -> song to (downloadProgress[q] ?: 0) }

    // Last download failure reason, surfaced to the user as a Toast for diagnosis.
    @Volatile var lastDownloadError: String? = null
    private val failures=java.util.concurrent.ConcurrentHashMap<String,Pair<io.github.unidentified24.spock.data.entity.SongsModel,String>>()
    fun failedSnapshot()=failures.values.toList()
    fun dismissFailure(query:String){failures.remove(query)}
    private val errors=java.util.concurrent.ConcurrentHashMap<String,String>()
    private fun recordError(query:String,message:String){errors[query]=message;lastDownloadError=message}
    private suspend fun <T> attempt(block:suspend ()->T):T? = try{block()}catch(cancelled:CancellationException){throw cancelled}catch(_:Exception){null}
    private val owners=java.util.concurrent.ConcurrentHashMap<String,LookupConnections>()
    fun cancel(query:String){owners[query]?.close();jobs[query]?.cancel()}


    // googlevideo stream URLs 403 without a browser User-Agent and need redirects
    // followed (http↔https) — ExoPlayer does both, so a raw URLConnection must too.
    private fun openDownloadConn(url: String): java.net.HttpURLConnection =
        (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            LookupConnections.track(this)
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            setRequestProperty("Accept-Encoding","identity")
            val host=java.net.URI(url).host.orEmpty()
            if(host=="googlevideo.com" || host.endsWith(".googlevideo.com"))
                com.metrolist.innertube.models.YouTubeClient.forStreamUrl(url).mediaHeaders().forEach{(name,value)->setRequestProperty(name,value)}
        }

    /**
     * Download [url] to [tmpFile] using HTTP **Range** requests in chunks, reporting
     * progress (0..100) for [query]. A single full-file GET of a googlevideo stream gets
     * reset partway through (`SocketException: Connection reset`) — the server expects the
     * audio fetched in byte ranges, which is how ExoPlayer/NewPipe get it. Each chunk is a
     * short connection (retried a few times on reset); writing is append-continuous so a
     * retried chunk resumes from the current byte position. Returns true iff the whole
     * file was written. Falls back gracefully if the server ignores Range (HTTP 200).
     */
    private fun httpDownloadRanged(url:String,tmpFile:java.io.File,query:String):Boolean = try {
        RangedTransfer.copy(url,tmpFile,::openDownloadConn,{pct->downloadProgress[query]=pct;onDownloadsChanged?.invoke()},LookupConnections::checkActive)
        true
    } catch(cancelled:CancellationException){throw cancelled}
      catch(error:Exception){recordError(query,error.message ?: "Download failed");false}

    /** Download every track in a list (album/playlist). Each song dedupes and
     *  reports its own progress via the existing per-song machinery. */
    fun downloadAll(songs: List<io.github.unidentified24.spock.data.entity.SongsModel>, context: Context) {
        songs.forEach { downloadSong(it, context) }
    }

    private val jobs = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Job>()

    /**
     * Stop every pending download. Tracks still waiting for a slot stop at once; the
     * active transfers have their owned connections closed as well.
     */
    fun cancelAll() {
        owners.values.forEach{it.close()};jobs.values.forEach { it.cancel() }
        onDownloadsChanged?.invoke()
    }

    // A whole playlist at once floods the sources with lookups and gets rate limited.
    private val downloadSlots = Semaphore(3)

    /** True once every track in [songs] is downloaded (for the album's "downloaded" state). */
    fun allDownloaded(
        songs: List<io.github.unidentified24.spock.data.entity.SongsModel>,
        context: Context,
    ): Boolean {
        if (songs.isEmpty()) return false
        val appContext = context.applicationContext
        return songs.all {
            io.github.unidentified24.spock.data.preferences.isDownloaded(appContext, it.id.toString())
        }
    }

    /**
     * Resolve the track's stream and save the audio to local storage for offline
     * playback. Runs on the IO scope; invokes [onComplete] (main thread) with whether
     * it succeeded. No-op if it's already downloaded or downloading.
     */
    fun downloadSong(
        song: io.github.unidentified24.spock.data.entity.SongsModel,
        context: Context,
        onComplete: (Boolean) -> Unit = {},
    ) {
        val appContext = context.applicationContext
        val query = song.url
        if (query.isBlank() ||
            io.github.unidentified24.spock.data.preferences.isDownloaded(appContext, song.id.toString()) ||
            !downloading.add(query)
        ) return
        downloadingSongs[query] = song
        downloadProgress[query] = 0
        onDownloadsChanged?.invoke()
        lastDownloadError = null
        failures.remove(query);errors.remove(query)
        val owner=LookupConnections();owners[query]=owner
        val job=scope.launch(start=CoroutineStart.LAZY) {
            var ok=false;var cancelled=false
            try {ok=withContext(owner){downloadSlots.withPermit{downloadToFile(song,appContext)}}}
            catch(error:CancellationException){cancelled=true;throw error}
            catch(error:Exception){recordError(query,error.message ?: "Unexpected download failure")}
            finally {
                owner.close();owners.remove(query);jobs.remove(query);downloading.remove(query);downloadProgress.remove(query);downloadingSongs.remove(query)
                if(!ok && !cancelled)failures[query]=song to (errors[query] ?: "No downloadable audio source responded")
                errors.remove(query)
                val dir=java.io.File(appContext.filesDir,"downloads")
                listOf("part","flacpart","dzpart").forEach{java.io.File(dir,"${song.id}.$it").delete()}
                withContext(NonCancellable+Dispatchers.Main){onDownloadsChanged?.invoke();onComplete(ok)}
            }
        }
        jobs[query]=job;job.start()
    }

    private fun commitFile(context:Context,song:io.github.unidentified24.spock.data.entity.SongsModel,tmp:java.io.File,out:java.io.File):Boolean {
        if(!OfflineFiles.audioHeader(tmp)){recordError(song.url,"Downloaded response was not a supported audio file");tmp.delete();return false}
        val reader=android.media.MediaMetadataRetriever()
        try {
            reader.setDataSource(tmp.path)
            val duration=reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            if(duration>0 && song.durationMs>0 && duration<song.durationMs*.70){recordError(song.url,"Source returned only a preview");tmp.delete();return false}
        } catch(_:Exception) { /* Some valid containers cannot be read by Android's metadata reader. */ }
          finally {runCatching{reader.release()}}
        if(!tmp.renameTo(out)){recordError(song.url,"Couldn't save audio");tmp.delete();return false}
        io.github.unidentified24.spock.data.preferences.addDownload(context,song,out.path)
        return true
    }

    private suspend fun downloadToFile(
        song: io.github.unidentified24.spock.data.entity.SongsModel,
        appContext: Context,
    ): Boolean {
        if(song.url.startsWith("content://") || song.url.startsWith("file://")) {
            val dir=java.io.File(appContext.filesDir,"downloads").apply{mkdirs()}
            val tmp=java.io.File(dir,"${song.id}.part")
            appContext.contentResolver.openInputStream(android.net.Uri.parse(song.url))?.use { input ->
                tmp.outputStream().use { output ->
                    val buffer=ByteArray(65536)
                    while(true){LookupConnections.checkActive();val n=input.read(buffer);if(n<0)break;output.write(buffer,0,n)}
                }
            } ?: return false
            return commitFile(appContext,song,tmp,java.io.File(dir,"${song.id}.audio"))
        }
        val dlQuality = io.github.unidentified24.spock.data.preferences.getDownloadQuality(appContext)
        // Deezer: use immediately if it yields FLAC (HiFi). If it only yields MP3
        // (free account), HOLD it and try the real FLAC sources first — otherwise a
        // free Deezer MP3 would pre-empt lossless.
        var heldDeezer: io.github.unidentified24.spock.deezer.DeezerSource.Resolved? = null
        if (SongPlayer.deezerEnabled && io.github.unidentified24.spock.data.preferences.isDeezerRaceEnabled(appContext)) {
            val raw = attempt { withTimeoutOrNull(15_000) { resolveDeezerRaw(song, appContext) } }
            if (raw != null) {
                if (raw.isFlac) {
                    if (downloadDeezerRaw(song, appContext, raw)) return true
                } else {
                    heldDeezer = raw
                }
            }
        }
        // Lossless FLAC: SpotiFLAC gated (if verified) + Tidal/community. Saves .flac.
        if (dlQuality.lossless && SongPlayer.losslessStreaming && song.spotifyTrackId.isNotBlank()) {
            val r = attempt { withTimeoutOrNull(25_000) {
                io.github.unidentified24.spock.lossless.LosslessSource.resolve(
                    appContext, song.spotifyTrackId,
                    io.github.unidentified24.spock.lossless.LosslessSource.TrackInfo(
                        song.title, song.singer.substringBefore(",").trim(), song.durationMs / 1000,
                    ),
                    preferHiRes = SongPlayer.losslessHiRes,
                )
            }
            }
            if (r is io.github.unidentified24.spock.lossless.LosslessSource.Result.Success) {
                val dir = java.io.File(appContext.filesDir, "downloads").apply { mkdirs() }
                val outFile = java.io.File(dir, "${song.id}.flac")
                val tmpFile = java.io.File(dir, "${song.id}.flacpart")
                if (httpDownloadRanged(r.track.url,tmpFile,song.url) && commitFile(appContext,song,tmpFile,outFile)) {
                    Log.d(TAG, "lossless downloaded (${r.track.provider} ${r.track.quality}-bit): ${song.title}")
                    return true
                }
                runCatching { tmpFile.delete() }
            }
        }
        // Deezer MP3 (held above) before a YouTube m4a, except MP3 128 at High,
        // which YouTube's Opus beats (#66); that one stays as the last resort.
        val ytFirst = heldDeezer?.lowBitrate == true && SongPlayer.youtubeEnabled &&
            dlQuality.audioQuality == com.metrolist.music.constants.AudioQuality.HIGH
        if (!ytFirst) heldDeezer?.let { if (downloadDeezerRaw(song, appContext, it)) return true }
        if (!SongPlayer.youtubeEnabled) {
            if(downloadSoundCloud(song,appContext))return true
            recordError(song.url,"Track not available for download")
            return false
        }

        val query = song.url
        // Resolve a fresh network stream URL (bypass any local-file short-circuit),
        // walking the ranked video candidates like playback does.
        val playback = attempt { SongPlayer.resolveYtPlayback(query, dlQuality.audioQuality, appContext) } ?: run {
            if (ytFirst && downloadDeezerRaw(song, appContext, heldDeezer!!)) return true
            if(downloadSoundCloud(song,appContext))return true
            recordError(query,"Couldn't resolve a stream")
            return false
        }

        val dir = java.io.File(appContext.filesDir, "downloads").apply { mkdirs() }
        val outFile = java.io.File(dir, "${song.id}."+if(playback.format.mimeType.contains("webm"))"webm" else "m4a")
        val tmpFile = java.io.File(dir, "${song.id}.part")

        if (!httpDownloadRanged(playback.streamUrl, tmpFile, song.url)) {
            runCatching { tmpFile.delete() }
            return downloadSoundCloud(song,appContext)
        }
        return commitFile(appContext,song,tmpFile,outFile) || downloadSoundCloud(song,appContext)
    }

    /** Progressive public audio only: an HLS playlist must never be saved as an offline song. */
    private suspend fun downloadSoundCloud(song:io.github.unidentified24.spock.data.entity.SongsModel,context:Context):Boolean {
        if(!io.github.unidentified24.spock.data.preferences.isSoundCloudEnabled(context))return false
        val stream=attempt{withTimeoutOrNull(10000){io.github.unidentified24.spock.soundcloud.SoundCloudSource.resolve(song.url,song.title,song.singer,song.durationMs/1000)}} ?: return false
        if(stream.hls)return false
        val dir=java.io.File(context.filesDir,"downloads").apply{mkdirs()}
        val tmp=java.io.File(dir,"${song.id}.scpart")
        if(!httpDownloadRanged(stream.url,tmp,song.url)){tmp.delete();return false}
        return commitFile(context,song,tmp,java.io.File(dir,"${song.id}.audio"))
    }

    /**
     * Download a Deezer track and decrypt it to a real audio file. Resolves the
     * stream (ISRC → Deezer, account-tier quality), streams the Blowfish-encrypted
     * CDN bytes and decrypts every 3rd 2048-byte block on the way to `<id>.flac` /
     * `<id>.mp3`. Returns false on any miss so the caller can fall back.
     */
    private suspend fun resolveDeezerRaw(
        song: io.github.unidentified24.spock.data.entity.SongsModel,
        appContext: Context,
    ): io.github.unidentified24.spock.deezer.DeezerSource.Resolved? =
        io.github.unidentified24.spock.deezer.DeezerSource.resolveRaw(
            appContext,
            spotifyId = song.spotifyTrackId.takeIf { it.isNotBlank() },
            isrc = null,
            searchQuery = listOf(song.title, song.singer).filter { it.isNotBlank() }.joinToString(" "),
            expectedDurationSec = if (song.durationMs > 0) song.durationMs / 1000 else 0,
            qualityCap = io.github.unidentified24.spock.data.preferences.getDownloadQuality(appContext).let { if(it.lossless)9 else if(it.audioQuality==com.metrolist.music.constants.AudioQuality.LOW)1 else 3 },
        )

    private fun downloadDeezerRaw(
        song: io.github.unidentified24.spock.data.entity.SongsModel,
        appContext: Context,
        raw: io.github.unidentified24.spock.deezer.DeezerSource.Resolved,
    ): Boolean {
        val ext = if (raw.isFlac) "flac" else "mp3"
        val dir = java.io.File(appContext.filesDir, "downloads").apply { mkdirs() }
        val outFile = java.io.File(dir, "${song.id}.$ext")
        val tmpFile = java.io.File(dir, "${song.id}.dzpart")
        if (!deezerDownloadDecrypted(raw.url, raw.encrypted, raw.trackId, tmpFile, song.url)) {
            runCatching { tmpFile.delete() }
            return false
        }
        return commitFile(appContext,song,tmpFile,outFile)
    }

    /**
     * Stream [url] and, if [encrypted], Blowfish-decrypt every 3rd 2048-byte block
     * (Deezer's stripe cipher) while writing to [tmpFile], reporting progress for
     * [query]. Produces a plain, fully-decrypted audio file.
     */
    private fun deezerDownloadDecrypted(
        url: String,
        encrypted: Boolean,
        trackId: String,
        tmpFile: java.io.File,
        query: String,
    ): Boolean {
        downloadProgress[query] = 0
        val conn = openDownloadConn(url)
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                recordError(query,"Deezer CDN HTTP $code")
                return false
            }
            val total = conn.contentLengthLong
            val cipher = if (encrypted) {
                io.github.unidentified24.spock.deezer.DeezerCrypto.cipher(
                    io.github.unidentified24.spock.deezer.DeezerCrypto.trackKey(trackId),
                )
            } else {
                null
            }
            java.io.BufferedOutputStream(tmpFile.outputStream()).use { output ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(2048)
                    var counter = 0
                    var position = 0L
                    var received=0L
                    while (true) {
                        LookupConnections.checkActive()
                        var read = 0
                        while (read < 2048) {
                            val r = input.read(buf, read, 2048 - read)
                            if (r < 0) break
                            read += r
                        }
                        if (read == 0) break
                        val out = if (encrypted && read == 2048 && counter % 3 == 0) {
                            cipher!!.doFinal(buf)
                        } else {
                            buf
                        }
                        output.write(out, 0, read)
                        counter++
                        position += read
                        received += read
                        if (total > 0) {
                            val pct = ((position * 100) / total).toInt().coerceIn(0, 100)
                            if (downloadProgress[query] != pct) {
                                downloadProgress[query] = pct
                                onDownloadsChanged?.invoke()
                            }
                        }
                        if (read < 2048) break // final partial chunk
                    }
                    check(received>0 && (total<0 || received==total)){"Incomplete Deezer download"}
                }
            }
            downloadProgress[query] = 100
            true
        } catch (cancelled:CancellationException) { throw cancelled
        } catch (e: Exception) {
            recordError(query,e.message ?: "Deezer download error")
            false
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}
