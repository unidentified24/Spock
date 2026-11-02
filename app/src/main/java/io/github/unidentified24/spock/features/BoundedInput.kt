package io.github.unidentified24.spock.features

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

object BoundedInput {
    fun read(input:InputStream,maxBytes:Int):ByteArray {
        require(maxBytes>=0)
        val output=ByteArrayOutputStream(minOf(maxBytes,65536));val buffer=ByteArray(16384)
        while(true){val n=input.read(buffer);if(n<0)break;if(n==0)continue
            if(output.size().toLong()+n>maxBytes)throw IOException("File is too large")
            output.write(buffer,0,n)
        }
        return output.toByteArray()
    }
}
