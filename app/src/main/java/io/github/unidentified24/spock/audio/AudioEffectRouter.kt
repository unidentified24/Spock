package io.github.unidentified24.spock.audio

import android.media.audiofx.Equalizer
import androidx.media3.exoplayer.ExoPlayer
import io.github.unidentified24.spock.features.FeatureSettings
import java.util.concurrent.ConcurrentHashMap

/** Optional system equalizer, isolated per active Media3 audio session. */
object AudioEffectRouter {
    private val equalizers=ConcurrentHashMap<Int,Equalizer>()
    private val playerSessions=java.util.WeakHashMap<ExoPlayer,Int>()
    @Synchronized fun apply(player:ExoPlayer){
        val settings=FeatureSettings.value.sound
        runCatching {
            player.playbackParameters=androidx.media3.common.PlaybackParameters(
                settings.speed.coerceIn(.5f,2f),if(settings.preservePitch)1f else settings.pitch.coerceIn(.5f,2f),
            )
        }
        val session=runCatching{player.audioSessionId}.getOrDefault(0)
        if(session<=0)return
        playerSessions.put(player,session)?.takeIf{it!=session}?.let{old->equalizers.remove(old)?.let{runCatching{it.release()}}}
        runCatching {
            val equalizer=equalizers[session] ?: Equalizer(0,session).also{equalizers[session]=it}
            val enabled=settings.enabled
            val count=equalizer.numberOfBands.toInt()
            val values=settings.equalizer
            val range=equalizer.bandLevelRange
            for(band in 0 until count){
                val source=if(values.isEmpty())0 else values[(band*values.size/count).coerceIn(0,values.lastIndex)]
                equalizer.setBandLevel(band.toShort(),source.coerceIn(range[0].toInt(),range[1].toInt()).toShort())
            }
            equalizer.enabled=enabled
        }
    }
    @Synchronized fun release(player:ExoPlayer){
        val session=playerSessions.remove(player) ?: runCatching{player.audioSessionId}.getOrDefault(0)
        equalizers.remove(session)?.let{runCatching{it.release()}}
    }
}
