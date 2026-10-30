package io.github.unidentified24.spock.soundcloud

object SoundCloudMatch {
    private fun words(s:String)=s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().split(' ').filter{it.isNotEmpty()}.toSet()
    fun accepts(title:String,artist:String,durationSec:Int,foundTitle:String,creator:String,foundDuration:Long):Boolean {
        if(durationSec>0 && (foundDuration<=0 || kotlin.math.abs(foundDuration-durationSec)>maxOf(3.0,durationSec*.025)))return false
        val expected=words(title);val found=words(foundTitle);val artists=words(artist.substringBefore(','))
        if(expected.isEmpty()||artists.isEmpty())return false
        val alternates=setOf("cover","remix","live","karaoke","instrumental","sped","slowed","acoustic")
        if(((found-expected) intersect alternates).isNotEmpty())return false
        return (expected intersect found).size.toDouble()/expected.size>=.85 &&
            (artists intersect words(creator+" "+foundTitle)).size.toDouble()/artists.size>=.75
    }
}
