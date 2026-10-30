package io.github.unidentified24.spock.playback

import java.io.InputStream

/** Bounded reads compatible with Android 8+, including streams that return partial reads. */
fun InputStream.readPrefix(limit:Int):ByteArray {
    require(limit>=0)
    val bytes=ByteArray(limit);var size=0
    while(size<limit){
        val count=read(bytes,size,limit-size)
        if(count<0)break
        if(count==0){val byte=read();if(byte<0)break;bytes[size++]=byte.toByte()}
        else size+=count
    }
    return if(size==limit)bytes else bytes.copyOf(size)
}
