package com.uncrowned.music

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL

/* Bloqueador de anúncios BRUTO do navegador privado (só roda no processo do navegador).
   - Corta o endereço inteiro: anúncio, rastreador, pop-up/pop-under e mineração, tanto pela página
     quanto direto no proxy de DNS (pega até o que a página tenta esconder).
   - Lista própria embutida + lista grande pública (StevenBlack, ~100 mil endereços) baixada e atualizada toda semana.
   - Corta também pelo caminho do arquivo (/ads/, prebid, pagead...), esconde os espaços de anúncio da página
     e não deixa site abrir janela sozinho. No YouTube tira a lista de anúncios e pula o que passar. */
object NavBlock {
    @Volatile var on = true
    @Volatile private var big: Set<String> = emptySet()
    private const val LIST_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    private const val WEEK = 7L * 24 * 3600_000

    private val base: Set<String> = """
        doubleclick.net googlesyndication.com googleadservices.com googletagservices.com googletagmanager.com google-analytics.com
        adservice.google.com pagead2.googlesyndication.com imasdk.googleapis.com googleads.g.doubleclick.net app-measurement.com
        adsystem.amazon.com amazon-adsystem.com aax.amazon-adsystem.com media.net adnxs.com adnxs-simple.com criteo.com criteo.net
        taboola.com outbrain.com outbrainimg.com revcontent.com mgid.com zergnet.com content.ad nativeads.com adblade.com
        pubmatic.com rubiconproject.com openx.net openx.com casalemedia.com indexww.com contextweb.com lijit.com sovrn.com
        smartadserver.com sharethrough.com triplelift.com 33across.com yieldmo.com teads.tv teads.com gumgum.com undertone.com
        spotxchange.com spotx.tv springserve.com freewheel.tv stickyadstv.com advertising.com adform.net adform.com serving-sys.com
        bidswitch.net adsrvr.org mathtag.com turn.com rlcdn.com bluekai.com demdex.net everesttech.net adition.com yieldlab.net
        moatads.com moatpixel.com doubleverify.com adsafeprotected.com iasds01.com scorecardresearch.com quantserve.com quantcount.com
        chartbeat.com chartbeat.net hotjar.com hotjar.io mouseflow.com crazyegg.com fullstory.com clarity.ms
        mixpanel.com segment.io segment.com amplitude.com branch.io appsflyer.com adjust.com kochava.com
        facebook.net connect.facebook.net an.facebook.com pixel.facebook.com ads.linkedin.com px.ads.linkedin.com
        ads.twitter.com static.ads-twitter.com analytics.twitter.com ads-api.tiktok.com analytics.tiktok.com business-api.tiktok.com
        ads.yahoo.com analytics.yahoo.com gemini.yahoo.com adtech.de yimg.jp-ads.com ads.pinterest.com ct.pinterest.com
        propellerads.com propellerclick.com propellerpops.com popads.net popcash.net popunder.net popmyads.com adcash.com
        exoclick.com exosrv.com exdynsrv.com realsrv.com trafficjunky.com trafficjunky.net juicyads.com trafficstars.com tsyndicate.com
        adsterra.com adsterratech.com hilltopads.net hilltopads.com clickadu.com clickaine.com monetag.com onclickads.net onclkds.com
        onclickmax.com onclickperformance.com bidvertiser.com revenuehits.com plugrush.com adxpansion.com ero-advertising.com
        a-ads.com coinzilla.com cointraffic.io bitmedia.io adskeeper.com adskeeper.co.uk admaven.com ad-maven.com
        richpush.co pushnami.com pushcrew.com onesignal.com pushwoosh.com subscribers.com notix.io pushprofit.net
        galaksion.com zeropark.com evadav.com rollerads.com dolohen.com acint.net adspyglass.com highperformanceformat.com
        highcpmrevenuegate.com profitablecpmrate.com profitablegatecpm.com effectivegatecpm.com displayvertising.com
        coinhive.com coin-hive.com jsecoin.com crypto-loot.com minero.cc webminepool.com
        yandex.ru/ads an.yandex.ru mc.yandex.ru adfox.ru adriver.ru begun.ru
        unityads.unity3d.com ads.unity3d.com applovin.com applvn.com vungle.com chartboost.com inmobi.com ironsrc.com supersonicads.com
        startappservice.com startapp.com mopub.com adcolony.com tapjoy.com fyber.com smaato.net smaato.com
        zedo.com tribalfusion.com conversantmedia.com valueclick.com cj.com dotomi.com emxdgt.com districtm.io
        gamoshi.io lkqd.net lkqd.com aniview.com connatix.com primis.tech vidazoo.com ex.co playwire.com
        adthrive.com mediavine.com ezoic.net ezoic.com ezodn.com setupad.net venatus.com freestar.io pbxai.com
        prebid.org hb.adscale.de adscale.de ligatus.com plista.com yieldlove.com stroeerdigitalmedia.de
        uol.com.br/ads ads.uol.com.br tm.uol.com.br adsplay.globo.com adnetwork.globo.com t.co/i/adsct
        """.trim().split(Regex("\\s+")).filter { it.isNotBlank() && '/' !in it }.toHashSet()

    private val PATH = Regex("(?i)(/ads?[/_.?=-]|/adserver|/adframe|/adview|/advert|/banners?/|/popunder|/popup[s]?[/._]|prebid|/pagead|adsbygoogle|/doubleclick|/sponsor(ed)?[/._]|/affiliate|/beacon[/.?]|/pixel\\.gif|/analytics(\\.min)?\\.js|/gtag/js|/gtm\\.js|fingerprint|/adsense|/ad_status|/adblock-?detect|/fuckadblock|/blockadblock)")

    /* o endereço (ou o domínio "pai" dele) está na lista? */
    fun blocked(host: String?): Boolean {
        if (!on || host.isNullOrBlank()) return false
        var h = host.lowercase().trimEnd('.')
        val b = big
        while (true) {
            if (h in base || h in b) return true
            val i = h.indexOf('.'); if (i < 0) return false
            h = h.substring(i + 1)
            if ('.' !in h) return false   // não bloqueia o domínio de topo sozinho (.com, .br)
        }
    }
    fun blockedPath(url: String): Boolean = on && PATH.containsMatchIn(url.substringAfter("://").substringAfter('/', ""))

    /* carrega a lista grande guardada e, se tiver mais de uma semana (ou não existir), baixa de novo pelo proxy */
    fun load(c: Context, proxyPort: Int) {
        Thread {
            val f = File(c.filesDir, "navblock_hosts.txt")
            if (f.exists()) big = parse(f)
            if (!f.exists() || System.currentTimeMillis() - f.lastModified() > WEEK) runCatching {
                val proxy = if (proxyPort > 0) java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxyPort)) else java.net.Proxy.NO_PROXY
                val con = URL(LIST_URL).openConnection(proxy) as HttpURLConnection
                con.connectTimeout = 15000; con.readTimeout = 30000
                try {
                    if (con.responseCode == 200) {
                        val tmp = File(f.path + ".part")
                        con.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                        if (tmp.length() > 100_000) { tmp.renameTo(f); big = parse(f) } else tmp.delete()
                    }
                } finally { con.disconnect() }
            }
        }.apply { isDaemon = true; name = "nav-blocklist" }.start()
    }
    val size get() = base.size + big.size

    private fun parse(f: File): Set<String> = runCatching {
        val out = HashSet<String>(160_000)
        f.forEachLine { raw ->
            val l = raw.substringBefore('#').trim(); if (l.isEmpty()) return@forEachLine
            val p = l.split(Regex("\\s+"))
            val d = (if (p.size >= 2 && (p[0] == "0.0.0.0" || p[0] == "127.0.0.1")) p[1] else p[0]).lowercase()
            if ('.' in d && d != "localhost" && d != "0.0.0.0" && !d.startsWith("local")) out += d
        }
        out
    }.getOrDefault(emptySet())

    // esconde os espaços de anúncio e barra janela aberta sem você tocar
    const val PAGE_JS = """(function(){ if(window.__nb) return; window.__nb=1;
  var css='ins.adsbygoogle,.adsbygoogle,[id^="google_ads"],[id^="div-gpt-ad"],[id^="gpt-ad"],[id^="ad-slot"],[id^="adslot"],[class*="ad-slot"],[class*="adslot"],[class*="ad-container"],[class*="ad-wrapper"],[class*="ad-banner"],[class*="adbanner"],[class*="advert"],[id*="advert"],[class*="sponsored"],[data-ad],[data-ad-slot],[data-adunit],[data-google-query-id],[aria-label="Advertisement"],[aria-label="Publicidade"],[aria-label="Anúncio"],.ad,.ads,.ad-box,.adBox,.adsbox,.ad-unit,.adunit,.banner-ad,.publicidade,.anuncio,.taboola,[id^="taboola"],.OUTBRAIN,[id^="outbrain"],.trc_related_container,iframe[src*="doubleclick"],iframe[src*="googlesyndication"],iframe[id^="google_ads"],iframe[src*="/ads/"],a[href*="doubleclick.net"],a[href*="/aclk?"],div[id^="taboola-"],amp-ad,amp-embed[type="taboola"]{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important}';
  function add(){ try{ if(document.getElementById('__nbcss')) return; var s=document.createElement('style'); s.id='__nbcss'; s.textContent=css; (document.head||document.documentElement).appendChild(s);}catch(e){} }
  add(); document.addEventListener('DOMContentLoaded',add);
  var o=window.open; window.open=function(){ try{ if(navigator.userActivation&&navigator.userActivation.isActive) return o.apply(this,arguments); }catch(e){} return null; };
  try{ Object.defineProperty(window,'onbeforeunload',{configurable:true,set:function(){},get:function(){return null}}); }catch(e){}
})();"""

    // YouTube no navegador: tira a lista de anúncios e o que passar fica mudo, acelerado e pulado
    const val YT_JS = """(function(){ if(window.__nby) return; window.__nby=1;
  setInterval(function(){ try{ var v=document.querySelector('video');
    var sk=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,[id^="skip-button"] button'); if(sk) sk.click();
    document.querySelectorAll('.ytp-ad-overlay-close-button,ytm-promoted-sparkles-web-renderer,ytm-companion-ad-renderer,ytd-ad-slot-renderer,ytm-promoted-video-renderer,ad-slot-renderer').forEach(function(e){ if(e.click&&e.className.indexOf('close')>=0) e.click(); else e.style.display='none'; });
    var ad=document.querySelector('.ad-showing,.ad-interrupting');
    if(ad&&v){ v.muted=true; window.__nbm=1; try{v.playbackRate=16}catch(e){} if(isFinite(v.duration)&&v.duration>0&&v.duration<=180) v.currentTime=Math.max(0,v.duration-0.05); }
    else if(window.__nbm&&v){ v.muted=false; window.__nbm=0; try{v.playbackRate=1}catch(e){} }
  }catch(e){} }, 300);
})();"""
}
