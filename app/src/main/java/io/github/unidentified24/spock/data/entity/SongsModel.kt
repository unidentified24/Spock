package io.github.unidentified24.spock.data.entity

@kotlinx.serialization.Serializable
data class SongsModel @JvmOverloads constructor(
    val id : Int,
    val title : String,
    val album : String,
    val singer : String,
    val coverUri : String,
    val url : String,
    // Real Spotify track id (e.g. "3n3Ppam7vgaVa1iaRUc9Lp"), kept so playback can
    // seed Spotify's recommendations endpoint for autoplay radio. Empty when unknown.
    val spotifyTrackId : String = "",
    // Whether the Spotify track is the explicit version, so the YouTube fallback
    // can pick the matching (explicit vs clean) edit.
    val explicit : Boolean = false,
    // Track length in ms (from Spotify). Used to disambiguate the YouTube match:
    // a same-title-different-artist song almost always has a different duration.
    val durationMs : Int = 0,
    val addedAt:Long=0,
    val albumId:String="",
    val artistIds:List<String> = emptyList(),
    val artistNames:List<String> = emptyList(),
    val genres:List<String> = emptyList(),
    val year:Int=0,
    val trackNumber:Int=0,
    val albumTrackCount:Int=0,
){
    constructor() : this(-1 ,"" ,"" ,"" ,"" ,"", "")
}
