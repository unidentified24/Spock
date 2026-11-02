package io.github.unidentified24.spock.playback

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.CancellationException

/** Full-length ranged transfers. A restart only appends when the entity is bound to the checkpoint. */
object RangedTransfer {
 private fun identity(url:String):Pair<String,Boolean> {
  val uri=runCatching{URI(url)}.getOrNull()
  val fields=uri?.rawQuery.orEmpty().split('&').mapNotNull{part->part.split('=',limit=2).takeIf{it.size==2}?.let{it[0] to URLDecoder.decode(it[1],"UTF-8")}}.toMap()
  val google=uri?.host?.let{it=="googlevideo.com"||it.endsWith(".googlevideo.com")}==true
  val bound=google&&listOf("id","itag","lmt","clen").all{!fields[it].isNullOrBlank()}
  val key=if(bound)listOf("id","itag","lmt","clen","mime").joinToString("|"){fields[it].orEmpty()}else url
  return MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString(""){"%02x".format(it.toInt() and 255)} to bound
 }
 fun copy(url:String,target:File,open:(String)->HttpURLConnection,progress:(Int)->Unit={},checkActive:()->Unit={},resume:Boolean=false) {
  val (key,immutable)=identity(url)
  val checkpoint=File(target.path+".resume")
  val previous=Properties().apply{if(resume&&checkpoint.isFile)runCatching{checkpoint.inputStream().use{load(it)}}}
  var etag=previous.getProperty("etag","");var modified=previous.getProperty("modified","")
  var position=if(resume&&previous.getProperty("key")==key&&(etag.isNotBlank()||modified.isNotBlank()||immutable))target.length()else 0L
  var total=if(position>0)previous.getProperty("total")?.toLongOrNull() ?: -1 else -1L
  if(total>0&&position>=total){position=0;total=-1}
  if(position==0L){etag="";modified=""}
  var resuming=position>0;var failures=0
  fun saveCheckpoint(){
   if(!resume)return
   val values=Properties().apply{setProperty("key",key);setProperty("etag",etag.take(512));setProperty("modified",modified.take(128));setProperty("total",total.toString())}
   val tmp=File(checkpoint.path+".tmp");tmp.outputStream().use{values.store(it,null)}
   runCatching{java.nio.file.Files.move(tmp.toPath(),checkpoint.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING)}.getOrElse{checkpoint.delete();if(!tmp.renameTo(checkpoint))tmp.delete()}
  }
  fun done(){checkpoint.delete();File(checkpoint.path+".tmp").delete();progress(100)}
  RandomAccessFile(target,"rw").use {output->
   if(position==0L)output.setLength(0)
   while(true) {
    checkActive()
    val conn=open(url)
    conn.setRequestProperty("Range","bytes=$position-${position+2*1024*1024-1}")
    if(position>0){if(etag.isNotBlank())conn.setRequestProperty("If-Range",etag)else if(modified.isNotBlank())conn.setRequestProperty("If-Range",modified)}
    try {
     var expectedChunk=-1L
     val code=conn.responseCode
     val type=conn.contentType.orEmpty().lowercase()
     if(type.startsWith("text/")||type.contains("json"))throw IOException("Source returned a page instead of audio")
     val newTag=conn.getHeaderField("ETag").orEmpty().takeIf{!it.startsWith("W/")} ?: ""
     val newModified=conn.getHeaderField("Last-Modified").orEmpty()
     if(code==200){position=0;output.setLength(0);total=conn.contentLengthLong;resuming=false;etag=newTag;modified=newModified}
     else if(code==206){
      val range=Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(conn.getHeaderField("Content-Range").orEmpty()) ?: throw IOException("Incomplete range metadata")
      val (start,end,size)=range.destructured
      if(start.toLong()!=position||end.toLong()<position||size.toLong()<=end.toLong())throw IOException("Invalid download range: expected $position, received $start-$end/$size")
      if(resuming){
       val same=when{etag.isNotBlank()->etag==newTag;modified.isNotBlank()->modified==newModified;else->immutable}
       if(!same||(total>0&&total!=size.toLong())){position=0;total=-1;etag="";modified="";resuming=false;output.setLength(0);checkpoint.delete();continue}
       resuming=false
      }
      if(total>0&&total!=size.toLong())throw IOException("Source changed during download")
      if(etag.isNotBlank()&&newTag.isNotBlank()&&etag!=newTag)throw IOException("Source changed during download")
      total=size.toLong();expectedChunk=end.toLong()-start.toLong()+1
      if(etag.isBlank())etag=newTag
      if(modified.isBlank())modified=newModified
     }else throw IOException("Audio download returned HTTP $code")
     saveCheckpoint();output.seek(position)
     var received=0L
     conn.inputStream.use {input->
      val buffer=ByteArray(65536)
      while(true){checkActive();val n=input.read(buffer);if(n<0)break;output.write(buffer,0,n);position+=n;received+=n
       if(total>0)progress((position*100/total).toInt().coerceIn(0,99))}
     }
     if(received==0L)throw IOException("Empty download response")
     if(expectedChunk>0&&received!=expectedChunk)throw IOException("Incomplete range response")
     if(code==200){if(total>0&&position!=total)throw IOException("Incomplete download");done();return}
     if(position==total){done();return}
     if(position>total)throw IOException("Download exceeded its declared length")
     failures=0
    }catch(cancelled:CancellationException){throw cancelled}
     catch(error:Exception){failures++;if(failures>=4)throw error}
     finally{conn.disconnect()}
   }
  }
 }
}
