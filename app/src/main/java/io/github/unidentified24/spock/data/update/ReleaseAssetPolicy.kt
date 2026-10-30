package io.github.unidentified24.spock.data.update

object ReleaseAssetPolicy {
    fun version(value:String)=value.length<=64 && Regex("[0-9]+(?:\\.[0-9]+)+").matches(value)
    fun allows(version:String,url:String):Boolean = runCatching {
        if(!ReleaseAssetPolicy.version(version))return false
        val uri=java.net.URI(url)
        val base="/${UpdateChecker.REPOSITORY}/releases/download/"
        uri.scheme=="https" && uri.host=="github.com" && uri.rawUserInfo==null && uri.port==-1 &&
            uri.rawQuery==null && uri.rawFragment==null &&
            listOf(version,"v$version","V$version").any{uri.rawPath=="$base$it/Spock-$version.apk"}
    }.getOrDefault(false)
}
