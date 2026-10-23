package com.uncrowned.music

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/* Bloqueador de anúncios do player do YouTube no celular.
   1) corta os servidores de propaganda (DoubleClick, IMA, Google Ads)
   2) baixa a página do player e coloca um script que apaga a lista de anúncios da resposta
      do YouTube antes de tocar (mesmo truque do uBlock Origin)
   3) o que ainda passar, a página do app silencia, pula pro fim e aperta "Pular" */
object AdBlock {
    var on: Boolean get() = Prefs.sp.getBoolean("adblock", true); set(v) = Prefs.sp.edit().putBoolean("adblock", v).apply()

    private val hosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "imasdk.googleapis.com",
        "adservice.google.com", "google-analytics.com", "googletagservices.com", "googletagmanager.com"
    )
    private val paths = listOf("/api/stats/ads", "/pagead/", "/ptracking", "/get_midroll_info", "/youtubei/v1/log_event")

    const val STRIP_JS = """(function(){
  if (window.__uncNoAds) return; window.__uncNoAds = true;
  var KEYS = ['adPlacements','playerAds','adSlots','adBreakHeartbeatParams','adBreakParams'];
  function strip(o){ try{ if(!o||typeof o!=='object') return o; KEYS.forEach(function(k){ if(k in o) delete o[k]; });
    if(o.playerResponse) strip(o.playerResponse); if(Array.isArray(o)) o.forEach(strip); }catch(e){} return o; }
  function hasAds(o){ return o&&typeof o==='object'&&(KEYS.some(function(k){return k in o})||(o.playerResponse&&KEYS.some(function(k){return k in o.playerResponse}))); }
  var ipr; try{ Object.defineProperty(window,'ytInitialPlayerResponse',{configurable:true,get:function(){return ipr},set:function(v){ipr=strip(v)}}); }catch(e){}
  var P = JSON.parse; JSON.parse = function(){ var r = P.apply(this, arguments); return hasAds(r) ? strip(r) : r; };
  var F = window.fetch; if (F) window.fetch = function(input){ var args = arguments;
    return F.apply(this, args).then(function(res){ try{ var u = typeof input==='string'?input:(input&&input.url)||'';
      if (/\/youtubei\/v1\/(player|next|reel)/.test(u)) return res.clone().json().then(function(j){
        return hasAds(j) ? new Response(JSON.stringify(strip(j)),{status:res.status,statusText:res.statusText,headers:res.headers}) : res; }).catch(function(){return res});
    }catch(e){} return res; }); };
})();"""

    private val empty get() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    fun intercept(req: WebResourceRequest): WebResourceResponse? {
        if (!on) return null
        val u = req.url; val host = u.host ?: return null; val path = u.path ?: ""
        if (hosts.any { host == it || host.endsWith(".$it") }) return empty
        if (host.endsWith("youtube.com") && paths.any { path.startsWith(it) }) return empty
        if (host == "www.youtube.com" && path.startsWith("/embed/") && req.method == "GET" && !req.isForMainFrame) return embed(req)
        // página do YouTube (vídeos que não tocam embutidos): mesmo script que tira os anúncios
        if ((host == "m.youtube.com" || host == "www.youtube.com") && path == "/watch" && req.method == "GET" && req.isForMainFrame) return embed(req)
        return null
    }

    // baixa a página do player e coloca o script logo no começo (sem a regra de segurança que barraria ele)
    private fun embed(req: WebResourceRequest): WebResourceResponse? = try {
        val url = req.url.toString()
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        req.requestHeaders.forEach { (k, v) -> c.setRequestProperty(k, v) }
        CookieManager.getInstance().getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
        c.setRequestProperty("Accept-Encoding", "identity")
        if (c.responseCode != 200) null else {
            val html = c.inputStream.bufferedReader().use { it.readText() }
            val tag = "<script>$STRIP_JS</script>"
            val i = html.indexOf("<head")
            val out = if (i >= 0) { val j = html.indexOf('>', i) + 1; html.substring(0, j) + tag + html.substring(j) } else tag + html
            val headers = mutableMapOf<String, String>()
            c.headerFields.forEach { (k, v) -> if (k != null && !k.equals("content-security-policy", true) && !k.equals("content-length", true) && !k.equals("content-encoding", true) && !k.equals("transfer-encoding", true) && v.isNotEmpty()) headers[k] = v.joinToString(", ") }
            WebResourceResponse("text/html", "utf-8", 200, "OK", headers, ByteArrayInputStream(out.toByteArray()))
        }
    } catch (e: Exception) { null }

    // roda na página do app (mesma origem do player), então enxerga dentro dele
    const val SKIP_JS = """setInterval(function(){ if(!window.ADB) return; try{
  var f=document.querySelector('iframe'); var d=f&&f.contentDocument; if(!d) return;
  var v=d.querySelector('video'); var ad=d.querySelector('.ad-showing');
  var sk=d.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-ad-skip-button-container button,[id^="skip-button"] button'); if(sk) sk.click();
  if(ad&&v){ v.muted=true; window.__m=1; if(isFinite(v.duration)&&v.duration>0&&v.duration<=180) v.currentTime=Math.max(0,v.duration-0.05); try{v.playbackRate=16}catch(e){} }
  else if(window.__m&&v){ v.muted=false; window.__m=0; try{v.playbackRate=1}catch(e){} }
  d.querySelectorAll('.ytp-ad-overlay-close-button,.ytp-ad-overlay-close-container button').forEach(function(b){b.click()});
}catch(e){} }, 300);"""
}

class AdBlockClient : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
        request?.let { AdBlock.intercept(it) } ?: super.shouldInterceptRequest(view, request)
    // não deixa a página do YouTube pular pro app do YouTube nem sair do player
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val u = request?.url ?: return false
        if (u.scheme != "http" && u.scheme != "https") return true
        val h = u.host ?: ""
        return !(h.endsWith("youtube.com") || h.endsWith("soundcloud.com") || h.endsWith("vimeo.com") || h.endsWith("google.com") || h.endsWith("gstatic.com"))
    }
    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if (url?.contains("youtube.com/watch") == true) view?.evaluateJavascript("window.ADB=${AdBlock.on};" + AdBlock.STRIP_JS, null)
    }
    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url?.contains("youtube.com/watch") == true) view?.evaluateJavascript("window.ADB=${AdBlock.on};" + YtPlayer.WATCH_JS, null)
    }
}
