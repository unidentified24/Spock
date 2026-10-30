package io.github.unidentified24.spock.playback
import java.io.File
object OfflineFiles {
 fun key(query:String):String = if(query.startsWith("spotify:track:"))query.substringBefore('|') else query
 fun available(path:String)=path.isNotBlank() && File(path).isFile && File(path).length()>0
 fun audioHeader(file:File):Boolean {
  if(!available(file.path))return false
  val bytes=ByteArray(16);val n=file.inputStream().use{it.read(bytes)}
  if(n<4)return false
  fun text(start:Int,length:Int)=String(bytes,start,length,Charsets.ISO_8859_1)
  return text(0,4) in setOf("fLaC","OggS","RIFF") || text(0,3)=="ID3" ||
   (n>=8 && text(4,4)=="ftyp") ||
   (bytes[0].toInt() and 255==0x1a && bytes[1].toInt() and 255==0x45 && bytes[2].toInt() and 255==0xdf && bytes[3].toInt() and 255==0xa3) ||
   (bytes[0].toInt() and 255==255 && bytes[1].toInt() and 0xe0==0xe0)
 }
}
