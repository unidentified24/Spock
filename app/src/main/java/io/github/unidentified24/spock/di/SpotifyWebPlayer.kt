package io.github.unidentified24.spock.di

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.unidentified24.spock.data.api.SpotifySession
import io.github.unidentified24.spock.playback.WebPlaybackProgress
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.coroutines.resume

/** Spotify supplies its own protected audio. Spock owns the interface and queue. */
object SpotifyWebPlayer {
    private var webView:WebView?=null
    private val handler=Handler(Looper.getMainLooper())
    private val progress=WebPlaybackProgress()
    private var token=0L
    private var requestedUri=""
    private var requestActive=false
    private var pollPending=false
    @Volatile var pageReady=false; private set
    @Volatile var canPlay=false; private set
    @Volatile var capabilityKnown=false; private set
    @Volatile var positionMs=0L; private set
    @Volatile var durationMs=0L; private set
    @Volatile var isPlaying=false; private set
    @Volatile var buffering=false; private set
    @Volatile var confirmed=false; private set
    @Volatile var ended=false; private set
    @Volatile var onStateChanged:(()->Unit)?=null
    val attached get()=webView!=null

    private val poll=object:Runnable {
        override fun run() {
            val wv=webView
            if(wv!=null&&!pollPending && (requestActive || !capabilityKnown)) {
                pollPending=true
                val expectedToken=token
                wv.evaluateJavascript("JSON.stringify({drm:window.__spockDrm,playback:window.__spockPlayback?window.__spockPlayback.snapshot():null})") { raw ->
                    pollPending=false
                    if(webView!==wv)return@evaluateJavascript
                    runCatching {
                        val text=org.json.JSONTokener(raw).nextValue() as? String ?: return@runCatching
                        val root=JSONObject(text)
                        if(root.has("drm")&&!root.isNull("drm")){capabilityKnown=true;canPlay=root.optBoolean("drm")}
                        val s=root.optJSONObject("playback") ?: return@runCatching
                        if(!requestActive || token!=expectedToken || s.optLong("token")!=token)return@runCatching
                        val accepted=progress.update(s.optString("uri"),s.optLong("position"),s.optLong("duration"),s.optBoolean("playing"),s.optBoolean("ended"))
                        ended=progress.ended;confirmed=progress.confirmed
                        if(ended){positionMs=maxOf(positionMs,s.optLong("position"));isPlaying=false;buffering=false;stopPage(wv)}
                        else if(accepted){positionMs=s.optLong("position").coerceAtLeast(0);durationMs=s.optLong("duration").coerceAtLeast(0);isPlaying=s.optBoolean("playing");buffering=s.optBoolean("buffering")}
                    }
                    onStateChanged?.invoke()
                }
            }
            if(webView!=null)handler.postDelayed(this,if(requestActive)250 else 2000)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(activity:Activity) {
        webView?.let{existing->
            (existing.parent as? ViewGroup)?.removeView(existing)
            activity.addContentView(existing,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
            existing.post{existing.translationX=existing.width.toFloat()+2000f}
            return
        }
        if(SpotifySession.spDc(activity).isBlank())return
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)){
            capabilityKnown=true;canPlay=false;return
        }
        val wv=WebView(activity.applicationContext)
        WebView.setWebContentsDebuggingEnabled(io.github.unidentified24.spock.BuildConfig.DEBUG)
        wv.settings.apply {
            javaScriptEnabled=true;domStorageEnabled=true;mediaPlaybackRequiresUserGesture=false
            allowFileAccess=false;allowContentAccess=false;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            val chrome=Regex("Chrome/[0-9.]+").find(WebSettings.getDefaultUserAgent(activity))?.value ?: "Chrome/134.0.0.0"
            userAgentString="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
        }
        val script=activity.assets.open("spock_web_playback.js").bufferedReader().use{it.readText()}
        WebViewCompat.addDocumentStartJavaScript(wv,script,setOf("https://open.spotify.com"))
        wv.webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean =
                request.isForMainFrame && (request.url.scheme!="https" || request.url.host!="open.spotify.com")
            override fun onPageStarted(view:WebView,url:String?,favicon:android.graphics.Bitmap?){pageReady=false}
            override fun onPageFinished(view:WebView,url:String?){
                if(Uri.parse(url.orEmpty()).host!="open.spotify.com")return
                pageReady=true
                view.evaluateJavascript("window.__spockDrm=null;navigator.requestMediaKeySystemAccess('com.widevine.alpha',[{initDataTypes:['cenc'],audioCapabilities:[{contentType:'audio/mp4;codecs=\"mp4a.40.2\"'}]}]).then(()=>{window.__spockDrm=true;}).catch(()=>{window.__spockDrm=false;});",null)
                if(requestActive)startPage(view)
            }
        }
        wv.webChromeClient=object:WebChromeClient(){
            override fun onPermissionRequest(request:PermissionRequest){
                val protected=PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                if(request.origin.scheme=="https" && request.origin.host=="open.spotify.com" && request.resources.contains(protected))request.grant(arrayOf(protected)) else request.deny()
            }
        }
        activity.addContentView(wv,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
        wv.importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        wv.post{wv.translationX=wv.width.toFloat()+2000f}
        webView=wv
        seedCookie(activity){wv.loadUrl("https://open.spotify.com/")}
        handler.removeCallbacks(poll);handler.postDelayed(poll,250)
    }

    private fun seedCookie(context:Context,after:()->Unit){
        val cookie=SpotifySession.spDc(context)
        if(cookie.isBlank())return
        CookieManager.getInstance().apply{
            setAcceptCookie(true)
            setCookie("https://open.spotify.com","sp_dc=$cookie; Domain=.spotify.com; Path=/; Secure; HttpOnly"){flush();after()}
        }
    }
    fun refreshLogin(context:Context){webView?.let{wv->seedCookie(context){wv.loadUrl("https://open.spotify.com/")}}}

    private fun startPage(wv:WebView){
        wv.evaluateJavascript("window.__spockPlayback && window.__spockPlayback.start(${JSONObject.quote(requestedUri)},$token)",null)
    }
    fun play(trackId:String)=playUri("spotify:track:$trackId")
    fun playEpisode(id:String)=playUri("spotify:episode:$id")
    private fun playUri(uri:String){
        if(!uri.matches(Regex("spotify:(track|episode):[A-Za-z0-9]+")))return
        handler.removeCallbacks(poll);handler.post(poll)
        token++;requestedUri=uri;requestActive=true;positionMs=0;durationMs=0;isPlaying=false;confirmed=false;ended=false;progress.reset(uri)
        webView?.let{if(pageReady)startPage(it)}
    }
    fun resume(){
        if(!requestActive)return
        token++;progress.reset(requestedUri);confirmed=false;ended=false
        webView?.evaluateJavascript("window.__spockPlayback && window.__spockPlayback.resume($token)",null)
    }
    fun pause(){token++;webView?.let(::stopPage);isPlaying=false;onStateChanged?.invoke()}
    private fun stopPage(wv:WebView){wv.evaluateJavascript("window.__spockPlayback && window.__spockPlayback.stop()",null)}

    /** Acknowledged pause before native playback starts; destroy on a wedged browser. */
    suspend fun stopForNative()=withContext(Dispatchers.Main){
        val needed=requestActive || isPlaying || buffering
        requestActive=false;token++;isPlaying=false;buffering=false;confirmed=false;ended=false
        if(!needed)return@withContext
        val wv=webView ?: return@withContext
        val stopped=withTimeoutOrNull(350){suspendCancellableCoroutine<Boolean>{c->
            wv.evaluateJavascript("window.__spockPlayback?window.__spockPlayback.stop():true"){if(c.isActive)c.resume(it=="true")}
        }} ?: false
        if(!stopped)detach()
        onStateChanged?.invoke()
    }
    fun warmNext(trackId:String){
        // Keep the existing authenticated browser and DRM session warm. Never start or seek the next song early.
        if(trackId.matches(Regex("[A-Za-z0-9]+")) && attached && !pageReady)webView?.postInvalidate()
    }
    fun seekTo(ms:Long){
        progress.reset(requestedUri);confirmed=false;ended=false
        webView?.evaluateJavascript("window.__spockPlayback && window.__spockPlayback.seek(${ms.coerceAtLeast(0)})",null)
    }
    fun detach(){
        requestActive=false;token++;handler.removeCallbacks(poll)
        webView?.let{wv->(wv.parent as? ViewGroup)?.removeView(wv);wv.stopLoading();wv.destroy()}
        webView=null;pageReady=false;capabilityKnown=false;canPlay=false;isPlaying=false;confirmed=false;ended=false;pollPending=false
    }
    fun release()=detach()
}
