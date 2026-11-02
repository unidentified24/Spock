package io.github.unidentified24.spock.playback

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RangedResumeTest {
    @get:Rule val temp=TemporaryFolder()
    private class Reply(val body:String,val range:String,val tag:String?=null):HttpURLConnection(URL("https://example.test/audio")) {
        var disconnected=false
        override fun connect(){}
        override fun disconnect(){disconnected=true}
        override fun usingProxy()=false
        override fun getResponseCode()=206
        override fun getContentType()="audio/mp4"
        override fun getHeaderField(name:String)=when(name){"Content-Range"->range;"ETag"->tag;else->null}
        override fun getInputStream()=body.byteInputStream()
    }
    private fun interrupted(url:String,file:java.io.File,tag:String?){
        var checks=0
        try{RangedTransfer.copy(url,file,{Reply("abc","bytes 0-2/6",tag)},checkActive={if(++checks==3)throw CancellationException()},resume=true);fail("Expected interruption")}
        catch(_:CancellationException){assertEquals("abc",file.readText());assertTrue(java.io.File(file.path+".resume").isFile)}
    }
    @Test fun restartedTransferAppendsOnlyToTheSameStrongEntity(){
        val file=temp.newFile();interrupted("https://example.test/audio",file,"\"one\"")
        val next=Reply("def","bytes 3-5/6","\"one\"")
        RangedTransfer.copy("https://example.test/audio",file,{next},resume=true)
        assertEquals("abcdef",file.readText());assertTrue(next.getRequestProperty("Range").startsWith("bytes=3-"));assertEquals("\"one\"",next.getRequestProperty("If-Range"));assertFalse(java.io.File(file.path+".resume").exists())
    }
    @Test fun changedEntityRestartsInsteadOfMixingTwoFiles(){
        val file=temp.newFile();interrupted("https://example.test/audio",file,"\"old\"")
        var calls=0;val oldOffset=Reply("XYZ","bytes 3-5/6","\"new\"");val fresh=Reply("UVWXYZ","bytes 0-5/6","\"new\"")
        RangedTransfer.copy("https://example.test/audio",file,{if(calls++==0)oldOffset else fresh},resume=true)
        assertEquals("UVWXYZ",file.readText());assertTrue(fresh.getRequestProperty("Range").startsWith("bytes=0-"));assertTrue(oldOffset.disconnected)
    }
    @Test fun unvalidatedMutableUrlsDoNotReuseAStalePrefix(){
        val file=temp.newFile();interrupted("https://example.test/audio",file,null)
        val next=Reply("UVWXYZ","bytes 0-5/6")
        RangedTransfer.copy("https://example.test/audio",file,{next},resume=true)
        assertEquals("UVWXYZ",file.readText());assertTrue(next.getRequestProperty("Range").startsWith("bytes=0-"))
    }
    @Test fun expiredGoogleTokensCanResumeAnIdenticalEncodedAudioEntity(){
        val file=temp.newFile();interrupted("https://r1.googlevideo.com/videoplayback?id=a&itag=251&lmt=77&clen=6&expire=1",file,null)
        val next=Reply("def","bytes 3-5/6")
        RangedTransfer.copy("https://r2.googlevideo.com/videoplayback?id=a&itag=251&lmt=77&clen=6&expire=2",file,{next},resume=true)
        assertEquals("abcdef",file.readText());assertTrue(next.getRequestProperty("Range").startsWith("bytes=3-"))
    }
}
