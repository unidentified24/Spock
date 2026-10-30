package io.github.unidentified24.spock.playback
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException

/** A download is complete only after its declared full length is received. */
object RangedTransfer {
 fun copy(url:String,target:File,open:(String)->HttpURLConnection,progress:(Int)->Unit={},checkActive:()->Unit={}) {
  var position=0L;var total=-1L;var failures=0
  RandomAccessFile(target,"rw").use {output->
   output.setLength(0)
   while(true) {
    checkActive()
    val conn=open(url)
    conn.setRequestProperty("Range","bytes=$position-${position+2*1024*1024-1}")
    try {
     var expectedChunk=-1L
     val code=conn.responseCode
     val type=conn.contentType.orEmpty().lowercase()
     if(type.startsWith("text/") || type.contains("json"))throw IOException("Source returned a page instead of audio")
     if(code==200) {position=0;output.setLength(0);total=conn.contentLengthLong}
     else if(code==206) {
      val range=Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(conn.getHeaderField("Content-Range").orEmpty()) ?: throw IOException("Incomplete range metadata")
      val (start,end,size)=range.destructured
      if(start.toLong()!=position || end.toLong()<position || size.toLong()<=end.toLong())throw IOException("Invalid download range")
      if(total>0 && total!=size.toLong())throw IOException("Source changed during download")
      total=size.toLong()
      expectedChunk=end.toLong()-start.toLong()+1
     } else throw IOException("Audio download returned HTTP $code")
     output.seek(position)
     var received=0L
     conn.inputStream.use {input->
      val buffer=ByteArray(65536)
      while(true){checkActive();val n=input.read(buffer);if(n<0)break;output.write(buffer,0,n);position+=n;received+=n
       if(total>0)progress((position*100/total).toInt().coerceIn(0,99))}
     }
     if(received==0L)throw IOException("Empty download response")
     if(expectedChunk>0 && received!=expectedChunk)throw IOException("Incomplete range response")
     if(code==200){if(total>0 && position!=total)throw IOException("Incomplete download");progress(100);return}
     if(position==total){progress(100);return}
     if(position>total)throw IOException("Download exceeded its declared length")
     failures=0
    } catch(cancelled:CancellationException){throw cancelled}
      catch(error:Exception){failures++;if(failures>=4)throw error}
      finally{conn.disconnect()}
   }
  }
 }
}
