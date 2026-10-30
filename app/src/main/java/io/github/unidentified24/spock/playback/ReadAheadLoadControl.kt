package io.github.unidentified24.spock.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator

/** Inherit concrete callbacks; interface delegation leaves Media3's default overloads recursive. */
class ReadAheadLoadControl(
    private val plan:BufferPlan,
    @Volatile var active:Boolean,
):DefaultLoadControl(
    DefaultAllocator(true,C.DEFAULT_BUFFER_SEGMENT_SIZE),
    plan.minMs,plan.maxMs,plan.startMs,plan.rebufferMs,plan.maxBytes,false,0,false,
) {
    override fun shouldContinueLoading(playbackPositionUs:Long,bufferedDurationUs:Long,playbackSpeed:Float):Boolean {
        if(!active && (bufferedDurationUs>=plan.nextMs*1000L || allocator.totalBytesAllocated>=plan.nextBytes))return false
        return super.shouldContinueLoading(playbackPositionUs,bufferedDurationUs,playbackSpeed)
    }
}
