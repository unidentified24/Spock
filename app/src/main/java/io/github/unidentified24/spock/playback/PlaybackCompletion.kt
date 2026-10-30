package io.github.unidentified24.spock.playback
object PlaybackCompletion {
 fun isNaturalEnd(heard:Boolean,position:Long,duration:Long,expected:Long):Boolean =
  heard && duration>0 && position>=duration-1500 && (expected<=0 || duration>=expected*0.70)
}
