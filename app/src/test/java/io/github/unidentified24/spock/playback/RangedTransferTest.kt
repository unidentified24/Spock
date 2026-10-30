package io.github.unidentified24.spock.playback

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RangedTransferTest {
 @get:Rule val temp=TemporaryFolder()
 private class Response(val status:Int,val body:String,val range:String?=null,val length:Long=body.length.toLong(),val mime:String="audio/mp4"):HttpURLConnection(URL("https://example.test/audio")) {
  var closed=false
  override fun connect(){}
  override fun disconnect(){closed=true}
  override fun usingProxy()=false
  override fun getResponseCode()=status
  override fun getContentLengthLong()=length
  override fun getContentType()=mime
  override fun getHeaderField(name:String)=if(name=="Content-Range")range else null
  override fun getInputStream()=body.byteInputStream()
 }
 private fun transfer(vararg replies:Response):File {
  val target=temp.newFile();var i=0
  RangedTransfer.copy("https://example.test/audio",target,{replies[i++]})
  return target
 }
 @Test fun sequentialRangesProduceExactFile(){
  val first=Response(206,"abc","bytes 0-2/6");val second=Response(206,"def","bytes 3-5/6")
  assertEquals("abcdef",transfer(first,second).readText())
  assertTrue(second.getRequestProperty("Range").startsWith("bytes=3-"));assertTrue(first.closed&&second.closed)
 }
 @Test fun serverIgnoringResumeReplacesRatherThanAppends(){
  assertEquals("abcdef",transfer(Response(206,"abc","bytes 0-2/6"),Response(200,"abcdef")).readText())
 }
 @Test fun truncatedRangeResumesAtReceivedByte(){
  val next=Response(206,"def","bytes 3-5/6")
  assertEquals("abcdef",transfer(Response(206,"abc","bytes 0-5/6"),next).readText())
  assertTrue(next.getRequestProperty("Range").startsWith("bytes=3-"))
 }
 @Test fun truncatedFullResponsesNeverSucceed(){
  var calls=0
  try{RangedTransfer.copy("x",temp.newFile(),{calls++;Response(200,"abc",length=6)});fail("Accepted a truncated file")}
  catch(_:IOException){assertEquals(4,calls)}
 }
 @Test fun htmlResponseNeverBecomesAudio(){
  try{RangedTransfer.copy("x",temp.newFile(),{Response(200,"<html>",mime="text/html")});fail("Accepted HTML")}
  catch(_:IOException){}
 }
 @Test fun wrongRangeOffsetIsRejected(){
  try{RangedTransfer.copy("x",temp.newFile(),{Response(206,"abc","bytes 2-4/6")});fail("Accepted wrong offset")}
  catch(_:IOException){}
 }
 @Test fun changedSourceLengthIsRejected(){
  var count=0
  try{RangedTransfer.copy("x",temp.newFile(),{if(count++==0)Response(206,"abc","bytes 0-2/6")else Response(206,"defg","bytes 3-6/7")});fail("Mixed sources")}
  catch(_:IOException){}
 }
 @Test fun cancellationDisconnectsAndDoesNotRetry(){
  val response=Response(200,"abc");var checks=0;var requests=0
  try {RangedTransfer.copy("x",temp.newFile(),{requests++;response},checkActive={if(++checks==2)throw CancellationException()});fail("Ignored cancellation")}
  catch(_:CancellationException){assertTrue(response.closed);assertEquals(1,requests)}
 }
}
