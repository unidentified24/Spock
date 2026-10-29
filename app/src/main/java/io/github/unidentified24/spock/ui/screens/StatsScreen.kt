package io.github.unidentified24.spock.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.unidentified24.spock.data.preferences.getListeningByDayOfWeek
import io.github.unidentified24.spock.data.preferences.getListeningStats
import io.github.unidentified24.spock.data.preferences.getListeningHistory
import io.github.unidentified24.spock.data.preferences.getTotalListeningMinutes
import io.github.unidentified24.spock.data.preferences.getTopAlbums
import io.github.unidentified24.spock.data.preferences.getTopArtists
import io.github.unidentified24.spock.ui.theme.AppBackground
import io.github.unidentified24.spock.ui.theme.AppPalette

/**
 * Dedicated listening-stats screen: total minutes listened, per-period cards,
 * a day-of-week bar chart, top artists and top albums with progress bars.
 */
@Composable
fun StatsScreen(navController: NavController) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(getListeningStats(context)) }
    var totalMinutes by remember { mutableStateOf(getTotalListeningMinutes(context)) }
    var topArtists by remember { mutableStateOf(getTopArtists(context, 8)) }
    var topAlbums by remember { mutableStateOf(getTopAlbums(context, 8)) }
    var dayMinutes by remember { mutableStateOf(getListeningByDayOfWeek(context)) }
    var songTimes by remember { mutableStateOf(io.github.unidentified24.spock.data.preferences.ListeningMeter.songs(context)) }
    var playCount by remember { mutableStateOf(getListeningHistory(context).size) }

    // Refresh the numbers on every composition (screen re-entry) so the
    // chart reflects newly logged plays without needing a manual reload.
    LaunchedEffect(Unit) {
        while (true) {
        stats = getListeningStats(context)
        totalMinutes = getTotalListeningMinutes(context)
        topArtists = getTopArtists(context, 8)
        topAlbums = getTopAlbums(context, 8)
        dayMinutes = getListeningByDayOfWeek(context)
        playCount = getListeningHistory(context).size
        songTimes = io.github.unidentified24.spock.data.preferences.ListeningMeter.songs(context)
        kotlinx.coroutines.delay(1000)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 130.dp),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            item {
                // Header row: back + title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp, 16.dp, 16.dp, 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { navController.popBackStack() }
                            .padding(6.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Listening Stats",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            item { TotalMinutesCard(totalMinutes, playCount) }
            item { Text(
                text = "Actual playback: " + (io.github.unidentified24.spock.data.preferences.ListeningMeter.milliseconds(context) / 1000).let { "%d:%02d".format(it / 60, it % 60) },
                color = AppPalette, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            item { Spacer(Modifier.height(10.dp)) }
            item { StatsPeriodCards(stats) }
            item { Spacer(Modifier.height(10.dp)) }
            item { WeekChartCard(dayMinutes) }
            item { Spacer(Modifier.height(10.dp)) }
            item { TopArtistsCard(topArtists) }
            item { Spacer(Modifier.height(10.dp)) }
            item { TopAlbumsCard(topAlbums) }
            item {
                Column(Modifier.padding(20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(io.github.unidentified24.spock.ui.theme.GridBackground).padding(18.dp)) {
                    Text("SONGS · TIME LISTENED", color = AppPalette, fontWeight = FontWeight.Bold)
                    if (songTimes.isEmpty()) Text("Play a song to start your counter.", color = Color.White)
                    songTimes.forEach { (name, ms) ->
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("%d:%02d".format(ms / 60000, ms / 1000 % 60), color = AppPalette)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hero card: big total-minutes number with hours conversion and play count.
 */
@Composable
private fun TotalMinutesCard(totalMinutes: Int, playCount: Int) {
    val target = if (totalMinutes > 0) 1f else 0f
    val progress by animateFloatAsState(targetValue = target, animationSpec = tween(1200))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        AppPalette.copy(alpha = 0.30f),
                        AppPalette.copy(alpha = 0.10f),
                    )
                )
            )
            .border(1.dp, AppPalette.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = AppPalette,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "TOTAL TIME LISTENED",
                color = AppPalette,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "${formatMinutesHuman(totalMinutes)}",
            color = Color.White,
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$totalMinutes minutes â€¢ $playCount tracks played",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))
        // Decorative animated ring: full circle when any listening exists.
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
            )
            CircularIndicator(
                progress = progress,
                size = 64.dp,
                strokeWidth = 4.dp,
                color = AppPalette,
                trackColor = Color.White.copy(alpha = 0.08f),
            )
            Text(
                text = "$playCount",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * 2x2 grid of period cards: today / week / month / all-time minutes.
 */
@Composable
private fun StatsPeriodCards(stats: Map<String, Int>) {
    val periods = listOf(
        "Today" to (stats["today"] ?: 0),
        "This Week" to (stats["thisWeek"] ?: 0),
        "This Month" to (stats["thisMonth"] ?: 0),
        "All Time" to (stats["allTime"] ?: 0),
    )
    Column {
        for (i in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                periods[i * 2]?.let { (label, value) ->
                    PeriodCard(label, value, modifier = Modifier.weight(1f))
                }
                periods[i * 2 + 1]?.let { (label, value) ->
                    PeriodCard(label, value, modifier = Modifier.weight(1f))
                }
            }
            if (i == 0) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun PeriodCard(label: String, minutes: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(io.github.unidentified24.spock.ui.theme.GridBackground)
            .padding(16.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${minutes} min",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatMinutesHuman(minutes),
            color = AppPalette,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Bar chart of listening time per day of the week (Monâ€“Sun order).
 */
@Composable
private fun WeekChartCard(dayMinutes: Map<String, Int>) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val values = days.map { dayMinutes[it] ?: 0 }
    val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(io.github.unidentified24.spock.ui.theme.GridBackground)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                tint = AppPalette,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "WEEKLY PATTERN",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            days.forEachIndexed { index, day ->
                val v = values[index]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (v > 0) v.toString() else "",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        modifier = Modifier.height(14.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    val heightFrac = if (v > 0) (v.toFloat() / max) else 0.02f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((100.dp * heightFrac).coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.verticalGradient(
                                    if (v > 0) listOf(AppPalette, AppPalette.copy(alpha = 0.4f))
                                    else listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))
                                )
                            )
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = day,
                        color = Color.White.copy(alpha = if (v > 0) 1f else 0.4f),
                        fontSize = 10.sp,
                        fontWeight = if (v > 0) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        if (values.sum() == 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Play some tracks to see your pattern here.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Top artists ranked by listening minutes, with progress bars.
 */
@Composable
private fun TopArtistsCard(topArtists: List<Pair<String, Int>>) {
    val max = (topArtists.firstOrNull()?.second ?: 1).coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(io.github.unidentified24.spock.ui.theme.GridBackground)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = AppPalette,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "TOP ARTISTS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.height(14.dp))
        if (topArtists.isEmpty()) {
            Text(
                text = "No listening data yet.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp,
            )
        } else {
            topArtists.forEachIndexed { index, (name, minutes) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (index == 0) AppPalette else Color.White.copy(alpha = 0.4f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.width(26.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        RankProgressBar(fraction = minutes.toFloat() / max, color = AppPalette)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "$minutes min",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * Top albums ranked by listening minutes, with progress bars.
 */
@Composable
private fun TopAlbumsCard(topAlbums: List<Pair<String, Int>>) {
    val max = (topAlbums.firstOrNull()?.second ?: 1).coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(io.github.unidentified24.spock.ui.theme.GridBackground)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = null,
                tint = AppPalette,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "TOP ALBUMS",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        Spacer(Modifier.height(14.dp))
        if (topAlbums.isEmpty()) {
            Text(
                text = "No listening data yet.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 13.sp,
            )
        } else {
            topAlbums.forEachIndexed { index, (name, minutes) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        color = if (index == 0) AppPalette else Color.White.copy(alpha = 0.4f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.width(26.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        RankProgressBar(
                            fraction = minutes.toFloat() / max,
                            color = if (index == 0) AppPalette else AppPalette.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "$minutes min",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Thin rounded progress bar with animated fill. */
@Composable
private fun RankProgressBar(fraction: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0.03f, 1f),
        animationSpec = tween(900),
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = Color.White.copy(alpha = 0.08f),
    )
}

/**
 * Simple circular progress ring: draws [progress] (0..1) of a circle starting at the top.
 */
@Composable
private fun CircularIndicator(
    progress: Float,
    size: androidx.compose.ui.unit.Dp,
    strokeWidth: androidx.compose.ui.unit.Dp,
    color: Color,
    trackColor: Color,
) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            // Track circle
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokePx, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }
    }
}

/** "92 min" â†’ "1h 32m" style formatting. */
private fun formatMinutesHuman(minutes: Int): String {
    if (minutes <= 0) return "0 min"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}
