package io.github.unidentified24.spock.data.entity
object CollectionIdentity {
 fun entries(items:List<LibraryEntry>)=items.distinctBy{(if(it.isPlaylist)"playlist:" else "album:")+it.spotifyId.ifBlank{it.name.lowercase()+":"+it.artists.lowercase()}}
 fun artists(items:List<ArtistsModel>)=items.distinctBy{it.id.ifBlank{it.name.lowercase()}}
}
