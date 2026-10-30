package io.github.unidentified24.spock.data.local
import android.content.Context
import android.content.ContentUris
import android.provider.MediaStore
import io.github.unidentified24.spock.data.preferences.*

/** Query the device index, never restrict audio to an app-owned folder. */
object DeviceAudio {
 fun scan(context:Context):List<LocalTrack> {
  val uri=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
  val columns=arrayOf("_id","title","artist","album","duration","_display_name")
  return buildList {
   context.contentResolver.query(uri,columns,null,null,"title ASC")?.use { c ->
    while(c.moveToNext()) {
     val audio=ContentUris.withAppendedId(uri,c.getLong(0)).toString()
     add(LocalTrack(localTrackId(audio),audio,c.getString(1)?.takeIf{it.isNotBlank()} ?: c.getString(5).orEmpty(),c.getString(2).orEmpty(),c.getString(3).orEmpty(),c.getInt(4),""))
    }
   }
  }
 }
}
