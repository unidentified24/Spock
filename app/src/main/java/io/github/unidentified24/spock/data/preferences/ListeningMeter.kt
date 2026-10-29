package io.github.unidentified24.spock.data.preferences

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object ListeningMeter {
    private val clock = PlaybackClock()
    private var ledger: JSONObject? = null
    private var lastFlush = 0L
    private var dirty = false
    private fun data(context: Context): JSONObject = ledger ?: runCatching {
        JSONObject(context.getSharedPreferences("listening_meter", 0).getString("ledger", "{}")!!)
    }.getOrDefault(JSONObject()).also { ledger = it }
    @Synchronized fun tick(context: Context, playing: Boolean, entry: HistoryEntry) {
        val now = SystemClock.elapsedRealtime()
        val delta = clock.sample(now, playing, entry.songId.toString())
        val rows = data(context)
        if (delta > 0) {
            val key = "${LocalDate.now()}:${entry.songId}"
            val row = rows.optJSONObject(key) ?: JSONObject().put("date", LocalDate.now().toString())
                .put("songId", entry.songId).put("artist", entry.singer).put("album", entry.album).put("title", entry.title)
            row.put("ms", row.optLong("ms") + delta); rows.put(key, row); dirty = true
        }
        if (dirty && (!playing || now - lastFlush >= 5000)) {
            context.getSharedPreferences("listening_meter", 0).edit().putString("ledger", rows.toString()).apply()
            lastFlush = now; dirty = false
        }
    }
    @Synchronized private fun rows(context: Context): List<JSONObject> = data(context).let { d -> d.keys().asSequence().map { d.getJSONObject(it) }.toList() }
    fun milliseconds(context: Context) = rows(context).sumOf { it.optLong("ms") }
    fun songMilliseconds(context: Context, songId: Int) = rows(context).filter { it.optInt("songId", -1) == songId }.sumOf { it.optLong("ms") }
    fun songs(context: Context): List<Pair<String, Long>> = rows(context)
        .groupBy { it.optString("title") + " · " + it.optString("artist") }
        .map { (name, values) -> name to values.sumOf { it.optLong("ms") } }.sortedByDescending { it.second }.take(20)
    fun stats(context: Context): Map<String, Int> {
        val now = LocalDate.now()
        val starts = mapOf("today" to now, "thisWeek" to now.with(java.time.DayOfWeek.MONDAY), "thisMonth" to now.withDayOfMonth(1), "allTime" to LocalDate.MIN)
        return starts.mapValues { (_, start) -> (rows(context).filter { LocalDate.parse(it.getString("date")) >= start }.sumOf { it.optLong("ms") } / 60000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
    }
    fun top(context: Context, field: String, limit: Int): List<Pair<String, Int>> = rows(context)
        .filter { it.optString(field).isNotBlank() }.groupBy { it.optString(field) }
        .map { (name, values) -> name to (values.sumOf { it.optLong("ms") } / 60000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        .sortedByDescending { it.second }.take(limit)
    fun days(context: Context): Map<String, Int> = rows(context)
        .groupBy { LocalDate.parse(it.getString("date")).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) }
        .mapValues { (_, values) -> (values.sumOf { it.optLong("ms") } / 60000).toInt() }
}
