package io.github.unidentified24.spock.lossless

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.*
import io.github.unidentified24.spock.playback.*
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Resolves a Spotify track to a lossless FLAC URL via SpotiFLAC's **public**
 * community backends — plain HTTP calls with a shared, published API key. No
 * login, no CAPTCHA, no per-user credentials.
 *
 * Providers are tried in priority order and each fails gracefully (a down or
 * account-limited backend just falls through to the next, then to YouTube):
 *   1. Tidal  — Spotify→Tidal id (the backend's own search) → `/track/?id=` → FLAC url
 *   2. Amazon — `/api/resolve/spotify/{id}` (self-resolving)   [often dormant]
 *
 * Odesli (song.link), which used to map Spotify ids to Tidal/Qobuz ids, closed its
 * public API (401 PUBLIC_API_ACCESS_DEPRECATED), so Qobuz (Odesli-only ids) is gone.
 * Endpoint lists come from [LosslessRegistry] so they follow the community's rotations.
 */
object LosslessSource {

    private const val TAG = "LosslessSource"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    data class FlacTrack(val url: String, val provider: String, val quality: String)

    sealed interface Result {
        data class Success(val track: FlacTrack) : Result
        data object NotFound : Result
        data class Error(val message: String) : Result
    }

    /** Track details from the play site; [title] blank means "ask Spotify". */
    data class TrackInfo(val title: String = "", val artist: String = "", val durationSec: Int = 0, val isrc: String = "")

    suspend fun resolve(
        context: Context,
        spotifyId: String,
        info: TrackInfo = TrackInfo(),
        preferHiRes: Boolean = true,
    ): Result =
        withContext(Dispatchers.IO) {
            // Independent direct-file and authenticated/legacy routes compete; dead hosts cannot serially delay playback.
            val routes:List<suspend ()->FlacTrack?> = listOf(
                {DirectLossless.resolve(info)},
                {resolveLegacy(context,spotifyId,info,preferHiRes)},
            )
            firstPlayable(routes.map{route->
                val owner=LookupConnections()
                StreamLookup(cancel={owner.close()}){withContext(owner){route()}}
            },timeoutMs=18000)?.let{return@withContext Result.Success(it)}
            Result.NotFound
        }

    private suspend fun resolveLegacy(context:Context,spotifyId:String,info:TrackInfo,preferHiRes:Boolean):FlacTrack? = coroutineScope {
        if(info.title.isBlank() || info.artist.isBlank())return@coroutineScope null
        val firstHit=CompletableDeferred<TidalHit>()
        val routes=LosslessRegistry.tidalEndpoints().map{endpoint->
            val owner=LookupConnections()
            StreamLookup<FlacTrack>(cancel={owner.close()}){withContext(owner){
                try {
                    val q=URLEncoder.encode(info.title+" "+info.artist,"UTF-8")
                    val data=JSONObject(get("$endpoint/search/?s=$q",keyHeader())).optJSONObject("data")
                    val items=data?.optJSONArray("items") ?: data?.optJSONObject("tracks")?.optJSONArray("items")
                    val hits=if(items==null)emptyList() else (0 until items.length()).mapNotNull{i->
                        val t=items.optJSONObject(i) ?: return@mapNotNull null
                        TidalHit(t.optString("id"),t.optString("title"),t.optJSONObject("artist")?.optString("name").orEmpty(),t.optInt("duration"),t.optString("isrc"),endpoint)
                    }
                    val hit=pickTidalHit(hits,info.isrc,info.title,info.artist,info.durationSec) ?: return@withContext null
                    firstHit.complete(hit)
                    val track=JSONObject(get("$endpoint/track/?id=${hit.id}&quality=LOSSLESS",keyHeader())).optJSONObject("data") ?: return@withContext null
                    val manifest=decodeBtsManifest(track.optString("manifest")) ?: return@withContext null
                    val urls=manifest.optJSONArray("urls") ?: return@withContext null
                    if(urls.length()!=1 || !manifest.optString("mimeType").contains("flac",true))return@withContext null
                    val url=urls.optString(0)
                    if(!url.startsWith("https://") || !isFullTrack(url,info.durationSec)){LosslessRegistry.failed(endpoint);return@withContext null}
                    LosslessRegistry.succeeded(endpoint)
                    FlacTrack(url,"Tidal",track.optInt("bitDepth",16).coerceIn(8,32).toString())
                }catch(c:CancellationException){throw c}
                 catch(_:Exception){LookupConnections.checkActive();LosslessRegistry.failed(endpoint);null}
            }}
        }.toMutableList()
        if(SpotiflacGated.hasSession(context)){
            val owner=LookupConnections()
            routes+=StreamLookup(cancel={owner.close()}){withContext(owner){
                val hit=firstHit.await()
                SpotiflacGated.tidalFlacUrl(context,hit.id,"LOSSLESS")?.takeIf{isFullTrack(it,info.durationSec)}?.let{FlacTrack(it,"SpotiFLAC","16")}
            }}
        }
        val winner=firstPlayable(routes,timeoutMs=3500)
        LosslessRegistry.publicResult(winner!=null)
        winner
    }

    // ── Provider: Amazon (self-resolving; skips encrypted responses) ──────────
    private fun amazonFlac(spotifyId: String): FlacTrack? {
        for (endpoint in LosslessRegistry.amazonEndpoints()) {
            val track = runCatching {
                val body = get("$endpoint/api/resolve/spotify/$spotifyId", keyHeader())
                val json = JSONObject(body)
                val data = json.optJSONObject("data") ?: json
                // Only accept a directly-playable url with NO decryption key — we
                // don't ship Amazon's stream decryption, so encrypted responses are
                // skipped rather than saved as garbled audio.
                if (data.has("decryption_key") || json.has("decryption_key")) return@runCatching null
                val url = firstNonBlank(
                    data.optString("url"), data.optString("stream_url"),
                    data.optString("download_url"), data.optString("streamUrl"),
                )
                if (url.isBlank() || !url.startsWith("http")) return@runCatching null
                if (!url.contains("flac", true)) return@runCatching null
                FlacTrack(url, "Amazon", "16")
            }.getOrNull()
            if (track != null) {
                Log.d(TAG, "Amazon FLAC via $endpoint (spotify:$spotifyId)")
                return track
            }
        }
        return null
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    internal data class TidalHit(
        val id: String, val title: String, val artist: String, val durationSec: Int, val isrc: String,
        val endpoint:String="",
    )

    /**
     * Same recording by ISRC if Spotify has one; otherwise same title and artist with
     * a length within 3 s, so a live version or a cover is never streamed instead.
     */
    internal fun pickTidalHit(hits: List<TidalHit>, isrc: String, title: String, artist: String, durationSec: Int): TidalHit? {
        fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        hits.firstOrNull { isrc.isNotBlank() && it.isrc.equals(isrc, ignoreCase = true) && it.id.isNotBlank() }?.let { return it }
        return hits.firstOrNull {
            it.id.isNotBlank() && key(it.title) == key(title) && key(it.artist) == key(artist) &&
                (durationSec <= 0 || kotlin.math.abs(it.durationSec - durationSec) <= 3)
        }
    }

    /**
     * The backends sometimes hand out a 30 s preview instead of the song; played as
     * the real track it ends early and the queue skips ahead every 30 s. A FLAC file
     * states its length in STREAMINFO (first 42 bytes), so one tiny range read tells.
     */
    internal fun isFullTrack(url: String, expectedSec: Int): Boolean {
        val seconds = runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                io.github.unidentified24.spock.playback.LookupConnections.track(this)
                connectTimeout = 1200
                readTimeout = 1800
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Range", "bytes=0-41")
            }
            val head = try {
                conn.inputStream.use { it.readPrefix(42) }
            } finally {
                conn.disconnect()
            }
            flacDurationSec(head)
        }.getOrNull() ?: return false // A search/URL alone is not evidence of playable full audio.
        val full = seconds >= expectedSec - 5
        if (!full) Log.d(TAG, "rejected ${seconds}s preview (expected ${expectedSec}s)")
        return full
    }

    /** Length from a FLAC STREAMINFO header, or null if [head] is not one. */
    internal fun flacDurationSec(head: ByteArray): Int? {
        if (head.size < 26 || String(head, 0, 4, Charsets.US_ASCII) != "fLaC") return null
        // Bytes 18..25: sample rate (20 bits), channels (3), bits/sample (5), total samples (36).
        var bits = 0L
        for (i in 18..25) bits = (bits shl 8) or (head[i].toLong() and 0xFF)
        val sampleRate = (bits ushr 44).toInt()
        val totalSamples = bits and 0xFFFFFFFFFL
        if (sampleRate <= 0 || totalSamples <= 0) return null
        return (totalSamples / sampleRate).toInt()
    }

    private fun decodeBtsManifest(b64: String): JSONObject? {
        if (b64.isBlank()) return null
        return runCatching {
            JSONObject(String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8))
        }.getOrNull()
    }

    private fun keyHeader() = mapOf("X-API-Key" to LosslessRegistry.API_KEY)

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()

    private fun get(urlString: String, headers: Map<String, String>): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                io.github.unidentified24.spock.playback.LookupConnections.track(this)
            requestMethod = "GET"
            connectTimeout = 1200
            readTimeout = 1800
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json, text/plain, */*")
            if(java.net.URI(urlString).host=="tidal.anandserver.cfd")headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val base=java.net.URI(urlString).let{it.scheme+"://"+it.host}
        return try {
            conn.connect()
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw java.io.IOException("HTTP $code: ${text.take(120)}")
            text
        } catch(error:Exception){LookupConnections.checkActive();LosslessRegistry.failed(base);throw error
        } finally {
            conn.disconnect()
        }
    }
}
