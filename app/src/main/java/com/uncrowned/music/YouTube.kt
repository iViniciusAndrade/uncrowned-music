@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.uncrowned.music

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/* src: "yt" = YouTube, "sc" = SoundCloud (id = caminho artista/faixa), "vm" = Vimeo */
data class YtVideo(val id: String, val title: String, val channel: String, val thumb: String, val src: String = "yt") {
    fun json() = JSONObject().put("id", id).put("t", title).put("c", channel).put("th", thumb).put("s", src)
    companion object { fun from(o: JSONObject) = YtVideo(o.getString("id"), o.getString("t"), o.getString("c"), o.getString("th"), o.optString("s", "yt")) }
}

/* ---------- SoundCloud (sem chave) e Vimeo (token grátis) ---------- */
object OtherApi {
    private fun http(u: String, headers: Map<String, String> = emptyMap()): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.connectTimeout = 10000; c.readTimeout = 10000
        return try {
            if (c.responseCode !in 200..299) error("Erro ${c.responseCode}")
            c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    // SoundCloud: busca nova em SoundCloud.kt (api-v2 + reserva HTML)
    suspend fun soundcloud(q: String): Result<List<YtVideo>> = SoundCloudApi.search(q)

    suspend fun vimeo(q: String, token: String): Result<List<YtVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            if (token.isBlank()) error("Coloque o token do Vimeo em Ajustes > Online")
            val body = http("https://api.vimeo.com/videos?per_page=25&fields=uri,name,user.name,pictures.base_link&query=${URLEncoder.encode(q, "UTF-8")}",
                mapOf("Authorization" to "bearer $token", "Accept" to "application/vnd.vimeo.*+json;version=3.4"))
            val a = JSONObject(body).getJSONArray("data")
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                YtVideo(o.getString("uri").substringAfterLast('/'), o.optString("name"), o.optJSONObject("user")?.optString("name") ?: "",
                    o.optJSONObject("pictures")?.optString("base_link")?.let { "${it}_640x360" } ?: "", "vm")
            }
        }
    }
}

/* ---------- Busca (YouTube Data API v3 oficial, chave grátis) ---------- */
object YtApi {
    /* Sem chave: lê os resultados da página de busca do YouTube (ytInitialData). Com chave: usa a API oficial. */
    suspend fun search(q: String, key: String): Result<List<YtVideo>> =
        if (key.isBlank()) scrape(q) else api(q, key).recoverCatching { scrape(q).getOrThrow() }

    private suspend fun scrape(q: String): Result<List<YtVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://www.youtube.com/results?search_query=${URLEncoder.encode(q, "UTF-8")}&sp=EgIQAQ%253D%253D&hl=pt-BR")
                .openConnection() as HttpURLConnection
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36")
            c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9")
            c.setRequestProperty("Cookie", "CONSENT=YES+1")
            c.connectTimeout = 10000; c.readTimeout = 10000
            val html = try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
            val start = html.indexOf("ytInitialData").let { if (it < 0) error("YouTube mudou a página") else html.indexOf('{', it) }
            val data = JSONObject(jsonAt(html, start))
            val out = ArrayList<YtVideo>()
            fun walk(x: Any?) {
                when (x) {
                    is JSONObject -> {
                        val v = x.optJSONObject("videoRenderer")
                        if (v != null) {
                            fun txt(o: JSONObject?) = o?.optString("simpleText")?.takeIf { it.isNotBlank() }
                                ?: o?.optJSONArray("runs")?.let { r -> (0 until r.length()).joinToString("") { r.getJSONObject(it).optString("text") } } ?: ""
                            val thumbs = v.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            val th = thumbs?.let { it.getJSONObject(it.length() - 1).optString("url") } ?: ""
                            out += YtVideo(v.getString("videoId"), txt(v.optJSONObject("title")), txt(v.optJSONObject("ownerText")), th)
                        } else x.keys().forEach { walk(x.get(it)) }
                    }
                    is JSONArray -> for (i in 0 until x.length()) walk(x.get(i))
                }
            }
            walk(data)
            out.distinctBy { it.id }.take(30)
        }
    }

    // recorta o objeto JSON que começa em "start", respeitando strings
    private fun jsonAt(s: String, start: Int): String {
        var depth = 0; var inStr = false; var esc = false
        for (i in start until s.length) {
            val ch = s[i]
            if (inStr) { if (esc) esc = false else if (ch == '\\') esc = true else if (ch == '"') inStr = false; continue }
            when (ch) { '"' -> inStr = true; '{' -> depth++; '}' -> { depth--; if (depth == 0) return s.substring(start, i + 1) } }
        }
        error("JSON incompleto")
    }

    private suspend fun api(q: String, key: String): Result<List<YtVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            val u = URL("https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&maxResults=25" +
                "&videoCategoryId=10&q=${URLEncoder.encode(q, "UTF-8")}&key=$key")
            val c = u.openConnection() as HttpURLConnection
            try {
                val body = (if (c.responseCode == 200) c.inputStream else c.errorStream).bufferedReader().readText()
                if (c.responseCode != 200) error(JSONObject(body).optJSONObject("error")?.optString("message") ?: "Erro ${c.responseCode}")
                val items = JSONObject(body).getJSONArray("items")
                List(items.length()) { i ->
                    val it = items.getJSONObject(i); val sn = it.getJSONObject("snippet")
                    fun dec(s: String) = HtmlCompat.fromHtml(s, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                    YtVideo(it.getJSONObject("id").getString("videoId"), dec(sn.getString("title")), dec(sn.getString("channelTitle")),
                        sn.getJSONObject("thumbnails").let { t -> (t.optJSONObject("high") ?: t.getJSONObject("default")).getString("url") })
                }
            } finally { c.disconnect() }
        }
    }
}

/* ---------- Favoritos e histórico do YouTube ---------- */
object YtStore {
    private fun list(k: String): List<YtVideo> = JSONArray(Prefs.sp.getString(k, "[]")).let { a -> List(a.length()) { YtVideo.from(a.getJSONObject(it)) } }
    private fun save(k: String, l: List<YtVideo>) = Prefs.sp.edit().putString(k, JSONArray(l.map { it.json() }).toString()).apply()
    fun favs() = list("ytfavs")
    fun history() = list("ythist")
    fun isFav(id: String) = favs().any { it.id == id }
    fun toggleFav(v: YtVideo) { val f = favs(); save("ytfavs", if (f.any { it.id == v.id }) f.filter { it.id != v.id } else listOf(v) + f) }
    fun addHistory(v: YtVideo) { save("ythist", (listOf(v) + history().filter { it.id != v.id }).take(50)) }
    var apiKey: String get() = Prefs.sp.getString("ytkey", "") ?: ""; set(v) = Prefs.sp.edit().putString("ytkey", v.trim()).apply()
    var autoNext: Boolean get() = Prefs.sp.getBoolean("ytauto", true); set(v) = Prefs.sp.edit().putBoolean("ytauto", v).apply()
    var vimeoToken: String get() = Prefs.sp.getString("vmtoken", "") ?: ""; set(v) = Prefs.sp.edit().putString("vmtoken", v.trim()).apply()
    var source: String get() = Prefs.sp.getString("osrc", "yt") ?: "yt"; set(v) = Prefs.sp.edit().putString("osrc", v).apply()
    val showVideo: Boolean get() = false   // no celular não mostra vídeo, só a capa
    // toca pelo SoundCloud: música que vem do YouTube (artista, álbum, paradas, playlists) é procurada no SoundCloud e toca de lá
    var scFirst: Boolean get() = Prefs.sp.getBoolean("scfirst", true); set(v) = Prefs.sp.edit().putBoolean("scfirst", v).apply()
}

/* ---------- Estado do player do YouTube ---------- */
object YtPlayer {
    var queue by mutableStateOf<List<YtVideo>>(emptyList())
    var index by mutableIntStateOf(-1)
    var playing by mutableStateOf(false)
    var time by mutableFloatStateOf(0f)
    var duration by mutableFloatStateOf(0f)
    var web: WebView? = null
    val current get() = queue.getOrNull(index)

    private var loadedSrc: String? = null
    fun play(list: List<YtVideo>, i: Int) {
        val target = list.getOrNull(i) ?: return
        // sem internet: toca a versão que já foi sincronizada pro celular
        if (!Sync.online()) {
            val u = Sync.syncedUri(target)
            if (u != null && Sync.playLocal != null) { js("player.pauseVideo()"); Sync.playLocal?.invoke(u, target); return }
            runCatching { android.widget.Toast.makeText(Sync.app, "Sem internet: essa ainda não foi sincronizada", android.widget.Toast.LENGTH_SHORT).show() }
        }
        // YouTube e SoundCloud tocam no player do app, pelo SoundCloud (efeitos, velocidade e tela apagada funcionam)
        if (target.src == "yt" || target.src == "sc") {
            close()
            val web2 = list.filter { it.src == "yt" || it.src == "sc" }
            YtStore.addHistory(target)
            ScPlay.play(web2, web2.indexOf(target).coerceAtLeast(0))
            return
        }
        stopOther?.invoke()
        queue = list; index = i
        val v = current ?: return
        YtStore.addHistory(v)
        val w = web
        if (w != null && loadedSrc != v.src) load(w, v) else js("player.loadVideoById('${v.id}')")
        startDog()
    }
    /* Garantia de tocar: se em ~8s a música não andou, troca o jeito de tocar e tenta de novo, sozinho:
       1) player embutido com bloqueio total de anúncio  2) página do YouTube com bloqueio total
       3) página do YouTube só pulando/silenciando os anúncios. O jeito que funcionar fica guardado. */
    var mode: Int get() = Prefs.sp.getInt("ytmode2", 0); set(v) = Prefs.sp.edit().putInt("ytmode2", v.coerceIn(0, 2)).apply()
    // endereço do app pro YouTube: ele recusa (erro 152) player que se apresenta como o próprio youtube.com
    const val ORIGIN = "https://uncrowned.app"
    private var progressed = false; private var userPaused = false; private var tries = 0
    private val dog = Runnable {
        val v = current ?: return@Runnable
        if (v.src != "yt" || progressed || userPaused) return@Runnable
        if (tries >= 3) return@Runnable
        tries++
        mode = (mode + 1).coerceAtMost(2)
        web?.let { load(it, v) }
        h.postDelayed(dogRef(), 9000)
    }
    private fun dogRef(): Runnable = dog
    // começa sozinho: até a música andar, dá "play" de novo a cada 1,2s (sem precisar abrir o player)
    private var kicks = 0
    private val kick = object : Runnable { override fun run() {
        if (progressed || userPaused || current == null || kicks++ > 14) return
        val w = web ?: return
        w.onResume(); w.resumeTimers()
        when {
            // o player diz que está tocando mas o tempo não anda: pausa e solta de novo (é o que destrava)
            playing && kicks % 2 == 0 -> { js("player.pauseVideo()"); h.postDelayed({ js("try{player.unMute();player.setVolume(100)}catch(e){};player.playVideo()") }, 350) }
            // parado: toque de verdade no meio do vídeo (o navegador aceita como se fosse você)
            !playing && kicks % 3 == 0 && w.width > 8 && w.height > 8 -> {
                val t = android.os.SystemClock.uptimeMillis(); val x = w.width / 2f; val y = w.height / 2f
                w.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
                w.dispatchTouchEvent(android.view.MotionEvent.obtain(t, t + 60, android.view.MotionEvent.ACTION_UP, x, y, 0))
            }
            else -> js("try{player.unMute();player.setVolume(100)}catch(e){};player.playVideo()")
        }
        w.evaluateJavascript("(function(){var f=document.querySelector('iframe');var d=f&&f.contentDocument;var v=(d||document).querySelector('video');if(v){v.muted=false;if(v.paused)v.play()}})()", null)
        h.postDelayed(this, 1500)
    } }
    private fun startDog() { progressed = false; userPaused = false; tries = 0; kicks = 0; h.removeCallbacks(dog); h.removeCallbacks(kick); h.postDelayed(dog, 8000); h.postDelayed(kick, 900) }
    fun load(w: WebView, v: YtVideo?) {
        loadedSrc = v?.src ?: "yt"; time = 0f; duration = 0f
        when (v?.src) {
            "sc" -> w.loadDataWithBaseURL("https://w.soundcloud.com", SC_HTML.replace("__ID__", v.id), "text/html", "utf-8", null)
            "vm" -> w.loadDataWithBaseURL("https://uncrowned.app", VM_HTML.replace("__ID__", v.id), "text/html", "utf-8", null)
            else -> if (mode >= 1 && v != null) { loadedSrc = "ytw"; w.loadUrl("https://m.youtube.com/watch?v=${v.id}") } else w.loadDataWithBaseURL(ORIGIN, HTML.replace("<body>", "<body><script>${AdBlock.BRAVE_JS}</script>").replace("</body>", "<script>var ADB=${AdBlock.on};${AdBlock.SKIP_JS}</script></body>"), "text/html", "utf-8", null)
        }
    }
    fun next() { if (ScPlay.active && (current?.src == "sc" || current?.src == "yt")) ScPlay.next() else if (index < queue.size - 1) play(queue, index + 1) }
    fun prev() { if (time > 5) seek(0f) else if (ScPlay.active && (current?.src == "sc" || current?.src == "yt")) ScPlay.prev() else if (index > 0) play(queue, index - 1) }
    // faixa protegida do SoundCloud: toca no player oficial deles (a fila continua no ScPlay)
    fun playWeb(v: YtVideo) {
        stopOther?.invoke()
        queue = listOf(v); index = 0
        val w = web
        if (w != null && loadedSrc != v.src) load(w, v) else js("player.loadVideoById('${v.id}')")
        startDog()
    }
    fun toggle() { if (playing) userPaused = true; js(if (playing) "player.pauseVideo()" else "player.playVideo()") }
    fun seek(s: Float) = js("player.seekTo($s, true)")
    private var ticks = 0
    private val scScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    // 0 = não repete, 1 = repete a lista, 2 = repete esta música
    var repeat by mutableIntStateOf(Prefs.sp.getInt("ytrepeat", 0))
    fun cycleRepeat() { repeat = (repeat + 1) % 3; Prefs.sp.edit().putInt("ytrepeat", repeat).apply() }
    fun shuffleRest() { if (queue.size > 2) { val cur = current ?: return; queue = listOf(cur) + queue.filterIndexed { i, _ -> i != index }.shuffled(); index = 0 } }
    // controla o vídeo da página do YouTube igual ao player embutido (play, pausa, tempo, fim)
    const val WATCH_JS = """(function(){ if(window.__uncW) return; window.__uncW=1;
  var st=-1; function V(){return document.querySelector('video')}
  window.player={playVideo:function(){window.__uncPausedByUser=0;var v=V();v&&v.play()},pauseVideo:function(){window.__uncPausedByUser=1;var v=V();v&&v.pause()},seekTo:function(s){var v=V();if(v)v.currentTime=s},
    loadVideoById:function(id){location.href='https://m.youtube.com/watch?v='+id}};
  var css=document.createElement('style');css.textContent='ytm-mobile-topbar-renderer,ytm-pivot-bar-renderer,.mobile-topbar-header,ytm-item-section-renderer,ytm-companion-slot,ytm-promoted-sparkles-web-renderer,.ytm-autonav-bar{display:none!important}body{background:#000!important}';document.documentElement.appendChild(css);
  setInterval(function(){ var v=V(); if(!v) return;
    try{ if(v.paused && !v.ended && !window.__uncPausedByUser && v.readyState>2 && v.currentTime<1) v.play(); }catch(e){}
    var s=v.ended?0:(v.paused?2:1); if(s!==st){st=s;Android.state(s)}
    Android.time(v.currentTime||0, isFinite(v.duration)?v.duration:0);
    if(window.ADB!==false){ var ad=document.querySelector('.ad-showing,.ad-interrupting'); var sk=document.querySelector('.ytp-ad-skip-button,.ytp-ad-skip-button-modern,.ytp-skip-ad-button,[id^="skip-button"] button'); if(sk) sk.click();
      if(ad){ v.muted=true; window.__m=1; if(isFinite(v.duration)&&v.duration<=180) v.currentTime=Math.max(0,v.duration-0.05);} else if(window.__m){ v.muted=false; window.__m=0; } }
  }, 500);
})();"""
    // velocidade do player online (muda o tom junto, igual slowed/nightcore)
    var speed by mutableFloatStateOf(Prefs.sp.getFloat("ytspeed", 1f))
    fun setSpeedTo(v: Float) { speed = v; Prefs.sp.edit().putFloat("ytspeed", v).apply(); applySpeed() }
    fun applySpeed() = applyRate(GlobalFx.wowRate(speed, System.currentTimeMillis()))
    // velocidade com tom junto em qualquer player da web, inclusive o áudio escondido que o player cria (e o da faixa protegida)
    private fun applyRate(r: Float) { web?.evaluateJavascript("""(function(){var R=$r;window.__uncRate=R;function set(m){try{m.preservesPitch=false;m.mozPreservesPitch=false;m.webkitPreservesPitch=false;if(Math.abs(m.playbackRate-R)>0.0005)m.playbackRate=R;}catch(e){}}
      function run(d,w){try{if(d)d.querySelectorAll('video,audio').forEach(set)}catch(e){} try{if(w&&w.__uncEls)w.__uncEls.forEach(set)}catch(e){}}
      try{document.querySelectorAll('iframe').forEach(function(f){try{f.contentWindow.postMessage({unc:'rate',r:R},'*')}catch(e){} try{if(typeof __p==='function')__p(f)}catch(e){} try{run(f.contentDocument,f.contentWindow)}catch(e){}})}catch(e){} run(document,window);
      })();""", null) }
    var stopOther: (() -> Unit)? = null
    // fecha o player online (quando começa a tocar uma música offline)
    fun close() { js("player.pauseVideo()"); if (queue.isNotEmpty()) { queue = emptyList(); index = -1; playing = false }; GlobalFx.sync(false); runCatching { WebPlayService.update(Sync.app) } }
    // modo retrô dentro do player do YouTube/SoundCloud
    // faixa protegida do SoundCloud não pode passar pelo processamento da página (ficaria muda): o efeito vem do celular (GlobalFx)
    fun applyRetro() { if (current?.src != "sc") web?.evaluateJavascript(Retro.js(Retro.mode), null); GlobalFx.sync(playing && current != null); applySpeed() }
    fun js(code: String) { web?.evaluateJavascript("if(window.player&&player.loadVideoById){$code}", null) }

    private val h = Handler(Looper.getMainLooper())
    class Bridge {
        @JavascriptInterface fun state(s: Int) = h.post {
            playing = s == 1
            GlobalFx.sync(playing)
            runCatching { WebPlayService.update(Sync.app) }
            if (s == 1 && Retro.mode.isNotEmpty()) { applyRetro(); h.postDelayed({ applyRetro() }, 1500) }
            if (s == 1 && (speed != 1f || Retro.mode.isNotEmpty())) { applySpeed(); h.postDelayed({ applySpeed() }, 800) }
            // terminou: repetir esta, repetir a lista, ou seguir pra próxima
            if (s == 0 && ScPlay.active && (current?.src == "sc" || current?.src == "yt")) ScPlay.onWebEnded()
            else if (s == 0) when {
                repeat == 2 -> { seek(0f); js("player.playVideo()") }
                index < queue.size - 1 && (YtStore.autoNext || repeat == 1) -> next()
                repeat == 1 && queue.isNotEmpty() -> play(queue, 0)
            }
        }.let {}
        @JavascriptInterface fun time(t: Float, d: Float) = h.post {
            if (t > 0.6f && !progressed) { progressed = true; h.removeCallbacks(dog) }
            time = t; duration = d
            // conta o tempo ouvido (Mais ouvidas / Momentos)
            // velocidade (e a oscilação da fita no K7/vinil) aplicada pelo app, por fora do player
            if (playing && (speed != 1f && ticks % 6 == 0 || Retro.mode.isNotEmpty())) applySpeed()
            if (ticks == 0) GlobalFx.sync(playing)
            if (playing && ++ticks >= 10) { ticks = 0; current?.let { v -> runCatching { Listen.tick(LTrack("${v.src}:${v.id}", v.src, v.id, v.title, v.channel, v.thumb, ""), 5) } } }
        }.let {}
        // vídeo que o dono bloqueou fora do YouTube: avisa e pula em vez de ficar travado
        // vídeo que o dono não deixa tocar embutido: abre a página do próprio YouTube (com o bloqueador de anúncios) e toca por lá
        @JavascriptInterface fun err(code: Int) = h.post {
            val v = current ?: return@post
            val w = web ?: return@post
            if (v.src == "yt" && loadedSrc == "yt") { loadedSrc = "ytw"; time = 0f; duration = 0f; w.loadUrl("https://m.youtube.com/watch?v=${v.id}") }
            else if (index < queue.size - 1) next()
        }.let {}
        @JavascriptInterface fun ready2() = Unit
        @JavascriptInterface fun ready() = h.post { current?.takeIf { it.src == "yt" }?.let { js("player.loadVideoById('${it.id}')") } }.let {}
    }

    const val HTML = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style></head>
<body><div id="p"></div><script src="https://www.youtube.com/iframe_api"></script><script>
function __p(f){try{var w=f.contentWindow;if(!w||w.__uncP)return;w.__uncP=1;w.__uncEls=w.__uncEls||[];var P=w.HTMLMediaElement.prototype,op=P.play;P.play=function(){try{if(w.__uncEls.indexOf(this)<0)w.__uncEls.push(this);if(window.__uncRate){this.preservesPitch=false;this.webkitPreservesPitch=false;this.playbackRate=window.__uncRate}}catch(e){}return op.apply(this,arguments)}}catch(e){}}
var player;
function onYouTubeIframeAPIReady(){player=new YT.Player('p',{width:'100%',height:'100%',
playerVars:{autoplay:1,playsinline:1,rel:0,modestbranding:1,origin:location.origin},
events:{onReady:function(){try{__p(player.getIframe())}catch(e){};Android.ready()},onStateChange:function(e){Android.state(e.data)},onError:function(e){Android.err(e.data)}}});}
setInterval(function(){if(player&&player.getCurrentTime){Android.time(player.getCurrentTime()||0,player.getDuration()||0)}},500);
</script></body></html>"""

    // SoundCloud: player oficial (Widget API); "player" imita a interface do YouTube pra reaproveitar os controles
    const val SC_HTML = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{border:0;width:100%;height:100%}</style></head><body>
<script>function __p(f){try{var w=f.contentWindow;if(!w||w.__uncP)return;w.__uncP=1;w.__uncEls=w.__uncEls||[];var P=w.HTMLMediaElement.prototype,op=P.play;P.play=function(){try{if(w.__uncEls.indexOf(this)<0)w.__uncEls.push(this);if(window.__uncRate){this.preservesPitch=false;this.webkitPreservesPitch=false;this.playbackRate=window.__uncRate}}catch(e){}return op.apply(this,arguments)}}catch(e){}}</script>
<iframe id="sc" allow="autoplay" onload="__p(this)" src="https://w.soundcloud.com/player/?url=https%3A//soundcloud.com/__ID__&auto_play=true&visual=true&show_comments=false"></iframe>
<script src="https://w.soundcloud.com/player/api.js"></script><script>
var w=SC.Widget('sc');
var player={playVideo:function(){w.play()},pauseVideo:function(){w.pause()},seekTo:function(s){w.seekTo(s*1000)},
loadVideoById:function(id){w.load('https://soundcloud.com/'+id,{auto_play:true,visual:true,show_comments:false})}};
w.bind(SC.Widget.Events.READY,function(){__p(document.getElementById('sc'))});
w.bind(SC.Widget.Events.PLAY,function(){__p(document.getElementById('sc'));Android.state(1)});
w.bind(SC.Widget.Events.PAUSE,function(){Android.state(2)});
w.bind(SC.Widget.Events.FINISH,function(){Android.state(0)});
setInterval(function(){w.getPosition(function(p){w.getDuration(function(d){Android.time(p/1000,d/1000)})})},500);
</script></body></html>"""

    // Vimeo: player oficial (Player.js)
    const val VM_HTML = """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}iframe{border:0;width:100%;height:100%}</style></head><body>
<iframe id="v" allow="autoplay; fullscreen" src="https://player.vimeo.com/video/__ID__?autoplay=1"></iframe>
<script src="https://player.vimeo.com/api/player.js"></script><script>
var vp=new Vimeo.Player(document.getElementById('v'));
var player={playVideo:function(){vp.play()},pauseVideo:function(){vp.pause()},seekTo:function(s){vp.setCurrentTime(s)},
loadVideoById:function(id){vp.loadVideo(id).then(function(){vp.play()})}};
vp.on('play',function(){Android.state(1)});vp.on('pause',function(){Android.state(2)});vp.on('ended',function(){Android.state(0)});
vp.on('timeupdate',function(d){Android.time(d.seconds,d.duration)});
</script></body></html>"""
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtWebPlayer(modifier: Modifier) {
    AndroidView(modifier = modifier, factory = { ctx ->
        (YtPlayer.web ?: BgWebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = AdBlockClient()
            setBackgroundColor(android.graphics.Color.BLACK)
            addJavascriptInterface(YtPlayer.Bridge(), "Android")
            YtPlayer.web = this
            YtPlayer.load(this, YtPlayer.current)
        }).also { (it.parent as? android.view.ViewGroup)?.removeView(it) }
    })
}

/* ---------- Aba YouTube ---------- */
@Composable
fun YouTubeTab(ui: PlayerUi) {
    Prefs.version.intValue
    val scope = rememberCoroutineScope()
    var q by rememberSaveableString()
    var results by remember { mutableStateOf<List<YtVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadable by remember { mutableStateOf<List<Track>>(emptyList()) }
    var view by remember { mutableStateOf("busca") }
    var keyDraft by remember { mutableStateOf(YtStore.apiKey) }

    fun doSearch() {
        if (q.isBlank()) return
        loading = true; error = null; view = "busca"
        scope.launch {
            // busca em todos ao mesmo tempo e intercala os resultados
            val yt = async { YtApi.search(q, YtStore.apiKey).getOrDefault(emptyList()) }
            val sc = async { OtherApi.soundcloud(q).getOrDefault(emptyList()) }
            val vm = async { if (YtStore.vimeoToken.isBlank()) emptyList() else OtherApi.vimeo(q, YtStore.vimeoToken).getOrDefault(emptyList()) }
            val au = async { Audius.search(q).getOrDefault(emptyList()) }
            val ia = async { Archive.search(q).getOrDefault(emptyList()) }
            val lists = listOf(yt.await(), sc.await(), vm.await())
            results = (0 until (lists.maxOfOrNull { it.size } ?: 0)).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
            downloadable = au.await().filter { it.downloadable } + ia.await()
            if (results.isEmpty() && downloadable.isEmpty()) error = "Nada encontrado"
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Header("Online")
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Buscar em YouTube, SoundCloud, Vimeo e Audius") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true,
            shape = MaterialTheme.shapes.large, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(view == "busca", { view = "busca" }, { Text("Resultados") })
            FilterChip(view == "favs", { view = "favs" }, { Text("Favoritos") }, leadingIcon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp)) })
            FilterChip(view == "hist", { view = "hist" }, { Text("Histórico") })
            FilterChip(YtPlayer.queue.isNotEmpty() && view == "fila", { view = "fila" }, { Text("Fila") })
        }
        val list = when (view) { "favs" -> YtStore.favs(); "hist" -> YtStore.history(); "fila" -> YtPlayer.queue; else -> results }
        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            error != null && view == "busca" -> Text("Erro: $error", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error)
            list.isEmpty() && downloadable.isEmpty() -> Text("Nada aqui ainda.", Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (view == "busca" && downloadable.isNotEmpty()) {
                    item { Section("PRA BAIXAR (${downloadable.size})") }
                    itemsIndexed(downloadable.take(15), key = { _, t -> "dl" + t.id }) { i, t -> TrackRow(ui, t) { ui.playOnline(downloadable, i) } }
                    item { Section("ONLINE") }
                }
                itemsIndexed(list, key = { i, v -> "$i${v.src}${v.id}" }) { i, v -> YtRow(v) { ui.controller?.pause(); YtPlayer.play(list, i) } }
            }
        }
    }
}

@Composable
private fun rememberSaveableString() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

@Composable
fun YtRow(v: YtVideo, onClick: () -> Unit) {
    Prefs.version.intValue
    val cur = YtPlayer.current?.id == v.id
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .background(if (cur) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(v.thumb, Modifier.width(96.dp).aspectRatio(16f / 9f))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                color = if (cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(mapOf("yt" to "YouTube", "sc" to "SoundCloud", "vm" to "Vimeo")[v.src].orEmpty() + " • " + v.channel, maxLines = 1,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { YtStore.toggleFav(v) }) {
            Icon(if (YtStore.isFav(v.id)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null,
                tint = if (YtStore.isFav(v.id)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        IconButton(onClick = {
            val link = when (v.src) { "sc" -> "https://soundcloud.com/${v.id}"; "vm" -> "https://vimeo.com/${v.id}"; else -> "https://youtu.be/${v.id}" }
            ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${v.title} — $link"), "Compartilhar"))
        }) { Icon(Icons.Rounded.Share, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/* ---------- Player do YouTube (fica sempre montado; expande/recolhe) ---------- */
@Composable
fun YtPlayerBar(expanded: Boolean, onExpand: (Boolean) -> Unit) {
    Prefs.version.intValue
    val v = YtPlayer.current ?: return
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        Modifier.fillMaxWidth().then(if (expanded) Modifier.fillMaxHeight() else Modifier),
        color = if (expanded) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.secondaryContainer,
        shape = if (expanded) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.large
    ) {
        Column(if (expanded) Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp) else Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (expanded) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onExpand(false) }) { Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(32.dp)) }
                Text(mapOf("yt" to "YOUTUBE", "sc" to "SOUNDCLOUD", "vm" to "VIMEO")[v.src] ?: "", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                Sync.version
                val synced = Sync.syncedUri(v) != null
                var showSync by remember { mutableStateOf(false) }
                Spacer(Modifier.size(48.dp))
                if (showSync) SyncSheet(v) { showSync = false }
            }
            val showVid = false
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(if (expanded) 1.dp else 78.dp)) {
                    YtWebPlayer(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    if (!expanded) Art(v.thumb, Modifier.matchParentSize(), MaterialTheme.shapes.small)
                }
                if (!expanded) {
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp).clickable { onExpand(true) }) {
                        Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(v.channel, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { YtPlayer.toggle() }) { Icon(if (YtPlayer.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null) }
                    IconButton(onClick = { YtPlayer.next() }) { Icon(Icons.Rounded.SkipNext, null) }
                }
            }
            if (expanded) {
                if (!showVid) Art(v.thumb, Modifier.fillMaxWidth(Prefs.artSize).aspectRatio(1f).padding(vertical = 16.dp), MaterialTheme.shapes.extraLarge)
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(v.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                        Text(v.channel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { YtStore.toggleFav(v) }) {
                        Icon(if (YtStore.isFav(v.id)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = accent)
                    }
                }
                val d = YtPlayer.duration.coerceAtLeast(1f)
                var drag by remember { mutableStateOf<Float?>(null) }
                Slider(drag ?: (YtPlayer.time / d).coerceIn(0f, 1f), { drag = it }, onValueChangeFinished = { drag?.let { YtPlayer.seek(it * d) }; drag = null })
                Row(Modifier.fillMaxWidth()) {
                    Text(fmt((YtPlayer.time * 1000).toLong())); Spacer(Modifier.weight(1f)); Text(fmt((YtPlayer.duration * 1000).toLong()))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { YtPlayer.shuffleRest() }) { Icon(Icons.Rounded.Shuffle, "Embaralhar") }
                    IconButton(onClick = { YtPlayer.prev() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(40.dp)) }
                    FilledIconButton(onClick = { YtPlayer.toggle() }, Modifier.size(76.dp), shape = MaterialTheme.shapes.extraLarge) {
                        Icon(if (YtPlayer.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(42.dp))
                    }
                    IconButton(onClick = { YtPlayer.next() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(40.dp)) }
                    IconButton(onClick = { YtPlayer.cycleRepeat() }) {
                        Icon(if (YtPlayer.repeat == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Repetir", tint = if (YtPlayer.repeat > 0) accent else LocalContentColor.current.copy(alpha = .6f))
                    }
                }
                // som retrô (K7 / vinil) e adicionar à playlist
                var addOpen by remember { mutableStateOf(false) }
                var retro by remember { mutableStateOf(Retro.mode) }
                var speedOpen by remember { mutableStateOf(false) }
                var syncOpen by remember { mutableStateOf(false) }
                // botões compactos, iguais aos do player do app
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)) {
                    ToolBtn(Icons.Rounded.Speed, "${"%.2f".format(YtPlayer.speed)}x", YtPlayer.speed != 1f) { speedOpen = true }
                    ToolBtn(Icons.Rounded.Album, Retro.NAMES[retro] ?: "Normal", retro.isNotEmpty()) { Retro.next(); retro = Retro.mode }
                    ToolBtn(Icons.Rounded.PlaylistAdd, "Playlist", false) { addOpen = true }
                    Sync.version
                    val sDone = Sync.syncedUri(v) != null
                    ToolBtn(if (sDone) Icons.Rounded.DownloadDone else Icons.Rounded.Download, if (sDone) "Offline ✓" else "Salvar", sDone) { syncOpen = true }
                }
                if (syncOpen) SyncSheet(v) { syncOpen = false }
                if (speedOpen) ModalBottomSheet(onDismissRequest = { speedOpen = false }) {
                    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                        Text("Velocidade e tom", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("O tom muda junto (slowed / nightcore)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Slider(YtPlayer.speed, { YtPlayer.setSpeedTo((Math.round(it * 20) / 20f)) }, valueRange = 0.5f..1.5f, modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.75f to "Lento", 0.85f to "Slowed", 1f to "Normal", 1.15f to "Sped up", 1.25f to "Nightcore").forEach { (r, n) ->
                                FilterChip(YtPlayer.speed == r, { YtPlayer.setSpeedTo(r) }, { Text(n) }) }
                        }
                    }
                }
                if (addOpen) {
                    var nome by remember { mutableStateOf("") }
                    val ctxA = androidx.compose.ui.platform.LocalContext.current
                    AlertDialog(onDismissRequest = { addOpen = false }, title = { Text("Adicionar à playlist") },
                        text = { Column {
                            OPlaylists.list().take(12).forEach { p -> Text(p.name, Modifier.fillMaxWidth().clickable { OPlaylists.add(p.name, v); addOpen = false
                                android.widget.Toast.makeText(ctxA, "Adicionada em ${p.name}", android.widget.Toast.LENGTH_SHORT).show() }.padding(vertical = 10.dp)) }
                            OutlinedTextField(nome, { nome = it }, placeholder = { Text("Nova playlist") }, singleLine = true)
                        } },
                        confirmButton = { TextButton(onClick = { if (nome.isNotBlank()) { OPlaylists.save(nome.trim(), listOf(v), v.thumb, ""); addOpen = false } }) { Text("Criar") } },
                        dismissButton = { TextButton(onClick = { addOpen = false }) { Text("Fechar") } })
                }
            }
        }
    }
}

/* acha no SoundCloud a mesma música de um vídeo do YouTube */
object ScMatch {
    private val cache = HashMap<String, Pair<Long, List<Pair<SoundCloudApi.Hit, Int>>>>()
    private fun n(x: String) = java.text.Normalizer.normalize(x.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace(Regex("\\b(official|oficial|video|audio|lyrics?|letra|clipe|hd|4k|visualizer|ft\\.?.*|feat\\.?.*)\\b"), " ")
        .replace(Regex("[^a-z0-9]+"), " ").trim()
    private fun c(x: String) = n(x).replace(" ", "")
    private val JUNK = Regex("(?i)saved on|liked on|youtube|spotify|reposted|free download|\\bleak|snippet|preview|type beat|re-?upload|sped up|slowed|nightcore|remix|\\bcover\\b|\\b8d\\b|reverb|instrumental|karaok|acapella|\\blive\\b|ao vivo|mashup|\\bedit\\b|bootleg|tiktok")
    /* acha a mesma música no SoundCloud: título igual, artista certo (de preferência o perfil oficial),
       sem versão alterada (slowed, remix, cover...) e que dê pra ouvir inteira */
    suspend fun find(v: YtVideo): YtVideo? = candidates(v).firstOrNull { it.first.policy != "SNIP" }?.first?.let { YtVideo(it.path, it.title, it.artist, it.art, "sc") }
    /* candidatas em ordem (a melhor primeiro); as que só têm prévia de 30s vêm marcadas (policy SNIP) */
    suspend fun candidates(v: YtVideo): List<Pair<SoundCloudApi.Hit, Int>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        cache[v.id]?.let { if (it.second.isNotEmpty() || System.currentTimeMillis() - it.first < 600_000) return@withContext it.second }
        var title = v.title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim()
        var artist = v.channel.replace(Regex("(?i) - Topic$"), "").replace(Regex("(?i)VEVO$"), "").trim()
        if (" - " in title) { artist = title.substringBefore(" - ").trim(); title = title.substringAfter(" - ").trim() }
        val nt = n(title); val ct0 = c(title); val ca0 = c(artist)
        val origJunk = JUNK.findAll(v.title).map { it.value.lowercase() }.toSet()
        fun score(h: SoundCloudApi.Hit): Int {
            if (h.policy == "BLOCK") return -99
            val ht = n(h.title.replace(Regex("^.*? - "), { m -> if (c(m.value).contains(ca0) || ca0.contains(c(m.value).ifBlank { "#" })) "" else m.value }))
            val htc = ht.replace(" ", "")
            var s = when { htc == ct0 -> 6; htc.startsWith(ct0) || htc.endsWith(ct0) -> 4; htc.contains(ct0) -> 2; else -> return -99 }
            val ha = c(h.artist); val hu = c(h.user)
            if (ca0.isNotBlank() && (ha == ca0 || hu == ca0)) s += 5
            else if (ca0.isNotBlank() && (ha.contains(ca0) || ca0.contains(ha.ifBlank { "#" }) || hu.contains(ca0) || ca0.contains(hu.ifBlank { "#" }))) s += 3
            else if (ca0.isNotBlank() && c(h.title).contains(ca0)) s += 1
            // artista diferente = outra música com o mesmo nome: não serve
            else if (ca0.isNotBlank()) return -99
            val junk = JUNK.findAll(h.title).map { it.value.lowercase() }.filter { it !in origJunk }.count()
            s -= junk * 4
            val extra = ht.split(" ").filter { it.isNotBlank() && !nt.contains(it) && !n(artist).contains(it) }.size
            s -= minOf(3, maxOf(0, extra - 1))
            if (h.plays > 50_000) s += 1
            return s
        }
        // procura de mais de um jeito (artista + música, música + artista, só a música)
        val hits = LinkedHashMap<String, SoundCloudApi.Hit>()
        for (q in listOf("$artist $title".trim(), "$title $artist".trim(), title).distinct()) {
            SoundCloudApi.searchRich(q).forEach { hits.putIfAbsent(it.path, it) }
            if (hits.values.any { score(it) >= 9 && it.policy != "SNIP" }) break
        }
        val r = hits.values.map { it to score(it) }.filter { it.second >= 5 }.sortedByDescending { it.second }.take(6)
        if (hits.isNotEmpty()) cache[v.id] = System.currentTimeMillis() to r
        r
    }
}
