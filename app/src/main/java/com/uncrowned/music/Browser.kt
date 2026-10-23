package com.uncrowned.music

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Process
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.ScriptHandler
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/*
 * Navegador privado (opção secreta).
 * - Roda num processo separado (":nav") com pasta própria do WebView: nada de cookie, login,
 *   histórico ou cache se mistura com o resto do app, e tudo é apagado ao fechar.
 * - Todo endereço é resolvido pelo DNS da Cloudflare (one.one.one.one) via DNS-over-HTTPS,
 *   através de um proxy local que só existe enquanto o navegador está aberto. Downloads também passam por ele.
 * - Abas, página inicial fixa (ou um conjunto de páginas que abrem juntas) e downloads em Download/Uncrowned.
 */
class BrowserActivity : Activity() {
    private class Tab(val web: WebView, val popup: Boolean) {
        var title = ""; val url: String get() = web.url ?: ""
        val blocked = AtomicInteger(0)          // anúncios cortados nesta página
        val scripts = ArrayList<ScriptHandler>()
        var navs = 0
    }
    private val tabs = ArrayList<Tab>()
    private var cur = -1
    private val web: WebView? get() = tabs.getOrNull(cur)?.web

    private lateinit var bar: EditText
    private lateinit var prog: ProgressBar
    private lateinit var holder: FrameLayout
    private lateinit var tabBtn: TextView
    private var proxied = false
    private var port = 0
    private var wiped = false
    private var counted = false
    private val ui = Handler(Looper.getMainLooper())

    private val BG = Color.parseColor("#0E0E10"); private val FG = Color.WHITE
    private val DIM = Color.parseColor("#8E8E93"); private val FIELD = Color.parseColor("#1F1F23")

    // configurações do navegador (não é histórico: só o que você escolheu fixar)
    private val sp by lazy { getSharedPreferences("nav_home", MODE_PRIVATE) }
    private var homes: List<String>
        get() = runCatching { JSONArray(sp.getString("pages", "[]")).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList())
        set(v) { sp.edit().putString("pages", JSONArray(v.distinct()).toString()).apply() }
    private var darkSites: Boolean
        get() = sp.getBoolean("dark", true)
        set(v) { sp.edit().putBoolean("dark", v).apply() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < 28) {
            Toast.makeText(this, "O navegador privado precisa do Android 9 ou mais novo", Toast.LENGTH_LONG).show()
            finish(); return
        }
        // sobra de uma sessão que fechou na marra: apaga antes de abrir a pasta
        // (só se for a primeira tela do navegador nesse processo, senão apagaria a que está aberta)
        if (alive++ == 0) {
            NavWipe.run(this)
            runCatching { WebView.setDataDirectorySuffix(NavWipe.SUFFIX) }
        }
        counted = true

        window.statusBarColor = BG; window.navigationBarColor = BG

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        bar = EditText(this).apply {
            hint = "Pesquisar ou digitar endereço"; setHintTextColor(DIM); setTextColor(FG); textSize = 15f
            isSingleLine = true; setSelectAllOnFocus(true); typeface = Typeface.DEFAULT
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(FIELD) }
            setPadding(dp(14), 0, dp(14), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(2); marginEnd = dp(4) }
            setOnEditorActionListener { _, id, ev ->
                if (id == EditorInfo.IME_ACTION_GO || (ev?.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)) { go(text.toString()); true } else false
            }
            setOnFocusChangeListener { _, f -> if (!f) showUrl() }
        }
        tabBtn = TextView(this).apply {
            setTextColor(FG); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setStroke(dp(2), FG) }
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginStart = dp(8); marginEnd = dp(8) }
            setOnClickListener { tabsDialog() }
        }
        val menuBtn = btn("⋮", 24f) { }
        menuBtn.setOnClickListener { menu(it) }
        top.addView(btn("‹", 26f) { back() })
        top.addView(bar)
        top.addView(tabBtn)
        top.addView(menuBtn)
        top.addView(btn("✕", 20f) { close() })

        prog = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.INVISIBLE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        }
        holder = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }

        root.addView(top); root.addView(prog); root.addView(holder)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom); WindowInsetsCompat.CONSUMED
        }
        CookieManager.getInstance().setAcceptCookie(true)
        NavBlock.on = sp.getBoolean("block", true)

        // página que veio de fora, ou as páginas iniciais (uma ou um conjunto, cada uma numa aba)
        val start = intent?.dataString?.let { listOf(it) } ?: homes.ifEmpty { listOf(HOME) }
        val openAll = { start.forEachIndexed { i, u -> newTab(u, show = i == 0) }; refreshTabBtn() }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            port = DohProxy.start()
            NavBlock.load(applicationContext, port)
            val cfg = ProxyConfig.Builder().addProxyRule("127.0.0.1:$port").build()
            ProxyController.getInstance().setProxyOverride(cfg, Executor { it.run() }) { proxied = true; runOnUiThread { openAll() } }
        } else {
            Toast.makeText(this, "Esse WebView não deixa trocar o DNS: usando o do celular", Toast.LENGTH_LONG).show()
            NavBlock.load(applicationContext, 0)
            openAll()
        }
    }

    private fun btn(t: String, size: Float, onClick: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(FG); textSize = size; gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(44))
        setOnClickListener { onClick() }
    }

    /* ---------- abas ---------- */
    @SuppressLint("SetJavaScriptEnabled")
    private fun newTab(url: String?, show: Boolean = true, popup: Boolean = false): Tab {
        val w = WebView(this)
        // fundo branco: site sem cor de fundo não fica com letra preta em cima do escuro
        w.setBackgroundColor(Color.WHITE)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true            // fica na pasta do navegador, que é apagada ao sair
            cacheMode = WebSettings.LOAD_NO_CACHE
            @Suppress("DEPRECATION") saveFormData = false
            setGeolocationEnabled(false)
            allowFileAccess = false; allowContentAccess = false
            loadWithOverviewMode = true; useWideViewPort = true
            builtInZoomControls = true; displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            textZoom = 100
            // site não abre janela sozinho; link que abre em nova aba (você tocou) vira uma aba nova
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
        }
        applyDark(w)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, false)
        val tab = Tab(w, popup)
        addScripts(tab)
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val s = req.url.scheme ?: return true
                if (s != "http" && s != "https") return true   // intent:, market:, etc. ficam bloqueados
                if (req.isForMainFrame && NavBlock.blocked(req.url.host)) { blockedNav(tab, req.url.host); return true }
                return false
            }
            // corta anúncio, rastreador e pop-under antes de baixar
            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                if (!NavBlock.on || req.isForMainFrame) return null
                val u = req.url.toString()
                if (NavBlock.blocked(req.url.host) || NavBlock.blockedPath(u)) {
                    tab.blocked.incrementAndGet()
                    return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                tab.blocked.set(0)
                // pop-up que abriu direto num site de anúncio: fecha a aba na hora
                if (tab.popup && tab.navs++ == 0 && NavBlock.blocked(host(url))) { blockedNav(tab, host(url)); return }
                if (view === web) showUrl()
                if (tab.scripts.isEmpty()) inject(view, url)
            }
            override fun onPageFinished(view: WebView, url: String) {
                if (view === web) showUrl()
                if (tab.scripts.isEmpty()) inject(view, url)
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) { if (view === web) { prog.progress = p; prog.visibility = if (p in 1..99) View.VISIBLE else View.INVISIBLE } }
            override fun onReceivedTitle(view: WebView, title: String?) { tab.title = title.orEmpty() }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                // janela que o site tentou abrir sem você tocar: barrada
                if (NavBlock.on && !isUserGesture) { tab.blocked.incrementAndGet(); return false }
                val t = newTab(null, show = true, popup = true)
                (resultMsg.obj as? WebView.WebViewTransport)?.webView = t.web
                resultMsg.sendToTarget()
                return true
            }
            override fun onCloseWindow(window: WebView) { tabs.indexOfFirst { it.web === window }.takeIf { it >= 0 }?.let { closeTab(it) } }
        }
        w.setDownloadListener { u, ua, cd, mime, len -> askDownload(u, ua, cd, mime, len) }
        w.visibility = View.GONE
        holder.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        tabs += tab
        url?.let { w.loadUrl(it) }
        if (show || cur < 0) select(tabs.size - 1)
        refreshTabBtn()
        return tab
    }

    /* scripts do bloqueador: entram no começo de cada página (e de cada quadro dentro dela) */
    private fun addScripts(t: Tab) {
        t.scripts.forEach { runCatching { it.remove() } }; t.scripts.clear()
        if (!NavBlock.on || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        runCatching {
            t.scripts += WebViewCompat.addDocumentStartJavaScript(t.web, NavBlock.PAGE_JS, setOf("*"))
            t.scripts += WebViewCompat.addDocumentStartJavaScript(t.web, AdBlock.BRAVE_JS + AdBlock.STRIP_JS + NavBlock.YT_JS, setOf("https://*.youtube.com", "https://youtube.com"))
        }
    }
    // celular sem esse recurso: coloca os scripts quando a página começa e quando termina
    private fun inject(v: WebView, url: String) {
        if (!NavBlock.on) return
        v.evaluateJavascript(NavBlock.PAGE_JS, null)
        if (host(url).endsWith("youtube.com")) v.evaluateJavascript(AdBlock.BRAVE_JS + AdBlock.STRIP_JS + NavBlock.YT_JS, null)
    }
    private fun blockedNav(t: Tab, h: String?) {
        ui.post {
            val i = tabs.indexOf(t)
            if (t.popup && t.navs <= 1 && i >= 0 && tabs.size > 1) { closeTab(i); toast("Pop-up de anúncio bloqueado") }
            else toast("Bloqueado: ${h.orEmpty()}")
        }
    }

    private fun select(i: Int) {
        if (i !in tabs.indices) return
        tabs.forEachIndexed { k, t -> t.web.visibility = if (k == i) View.VISIBLE else View.GONE }
        cur = i
        prog.visibility = View.INVISIBLE
        showUrl(); refreshTabBtn()
    }

    private fun closeTab(i: Int) {
        if (i !in tabs.indices) return
        if (tabs.size == 1) { close(); return }
        val t = tabs.removeAt(i)
        destroy(t.web)
        select(if (cur >= tabs.size) tabs.size - 1 else if (i < cur) cur - 1 else cur.coerceAtMost(tabs.size - 1))
    }

    private fun refreshTabBtn() { tabBtn.text = tabs.size.coerceAtLeast(1).toString() }

    private fun tabsDialog() {
        if (tabs.isEmpty()) return
        val names = tabs.mapIndexed { i, t -> (if (i == cur) "● " else "") + t.title.ifBlank { host(t.url).ifBlank { "Nova aba" } } }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Abas (${tabs.size})")
            .setItems(names) { _, i -> select(i) }
            .setPositiveButton("Nova aba") { _, _ -> newTab(homes.firstOrNull() ?: HOME) }
            .setNegativeButton("Fechar esta") { _, _ -> closeTab(cur) }
            .setNeutralButton("Voltar", null)
            .show()
    }

    private fun showUrl() { if (!bar.hasFocus()) bar.setText(web?.url?.takeIf { it != "about:blank" } ?: "") }
    private fun host(u: String) = runCatching { URI(u).host?.removePrefix("www.") }.getOrNull() ?: ""

    /* ---------- menu ---------- */
    private fun menu(anchor: View) {
        val p = PopupMenu(this, anchor)
        val m = p.menu
        m.add(0, 1, 0, if (proxied) "🕶 Privado • DNS 1.1.1.1" else "🕶 Privado • DNS do celular").isEnabled = false
        m.add(0, 2, 1, "Nova aba")
        m.add(0, 3, 2, "Recarregar")
        m.add(0, 4, 3, "Fixar como página inicial")
        m.add(0, 5, 4, "Adicionar às páginas iniciais")
        m.add(0, 6, 5, "Páginas iniciais…")
        m.add(0, 7, 6, "Sites no escuro").apply { isCheckable = true; isChecked = darkSites }
        val nb = tabs.getOrNull(cur)?.blocked?.get() ?: 0
        m.add(0, 9, 7, "Bloquear anúncios" + if (NavBlock.on) " ($nb nesta página)" else "").apply { isCheckable = true; isChecked = NavBlock.on }
        m.add(0, 8, 8, "Downloads")
        p.setOnMenuItemClickListener { item ->
            val url = web?.url?.takeIf { it.startsWith("http") }
            when (item.itemId) {
                2 -> newTab(homes.firstOrNull() ?: HOME)
                3 -> web?.reload()
                4 -> if (url != null) { homes = listOf(url); toast("Essa vai ser a página inicial") } else toast("Abra uma página primeiro")
                5 -> if (url != null) { homes = homes + url; toast("Adicionada: ${homes.size} página(s) abrem juntas, cada uma numa aba") } else toast("Abra uma página primeiro")
                6 -> homesDialog()
                7 -> { darkSites = !darkSites; tabs.forEach { applyDark(it.web) }; web?.reload() }
                9 -> { NavBlock.on = !NavBlock.on; sp.edit().putBoolean("block", NavBlock.on).apply(); tabs.forEach { addScripts(it) }; web?.reload()
                    toast(if (NavBlock.on) "Bloqueador ligado (${NavBlock.size} endereços na lista)" else "Bloqueador desligado") }
                8 -> runCatching { startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { toast("Os arquivos ficam em Download/Uncrowned") }
            }
            true
        }
        p.show()
    }

    private fun homesDialog() {
        val l = homes
        val b = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Páginas iniciais")
        if (l.isEmpty()) b.setMessage("Nenhuma fixada: abre no DuckDuckGo.\n\nPra fixar, abra a página e use ⋮ → Fixar como página inicial (uma só) ou Adicionar às páginas iniciais (abrem todas juntas, cada uma numa aba).")
        else b.setItems(l.map { "✕  " + host(it).ifBlank { it } }.toTypedArray()) { _, i ->
            homes = l.filterIndexed { k, _ -> k != i }; toast("Removida"); homesDialog()
        }.setNegativeButton("Limpar todas") { _, _ -> homes = emptyList(); toast("Volta a abrir no DuckDuckGo") }
        b.setPositiveButton("Fechar", null).show()
    }

    private fun applyDark(w: WebView) {
        // escurece o site de verdade (texto claro em fundo escuro), em vez de só pintar o fundo
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
            runCatching { WebSettingsCompat.setAlgorithmicDarkeningAllowed(w.settings, darkSites) }
    }

    /* ---------- navegação ---------- */
    private fun go(raw: String) {
        val t = raw.trim(); if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> SEARCH + URLEncoder.encode(t, "UTF-8")
        }
        bar.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(bar.windowToken, 0)
        if (web == null) newTab(url) else web?.loadUrl(url)
    }

    private fun back() {
        val w = web ?: return close()
        when {
            w.canGoBack() -> w.goBack()
            tabs.size > 1 -> closeTab(cur)
            else -> close()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { back() }

    private fun close() {
        if (NavDl.running.get() > 0) {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Download em andamento")
                .setMessage("Fechar o navegador cancela o que ainda está baixando.")
                .setPositiveButton("Fechar mesmo") { _, _ -> finish() }
                .setNegativeButton("Esperar", null).show()
        } else finish()
    }

    /* ---------- downloads (vão pra Download/Uncrowned, pelo mesmo DNS 1.1.1.1) ---------- */
    private fun askDownload(url: String, ua: String?, cd: String?, mime: String?, len: Long) {
        if (!url.startsWith("http")) { toast("Esse tipo de download o navegador não consegue salvar"); return }
        if (Build.VERSION.SDK_INT < 29) { toast("Downloads precisam do Android 10 ou mais novo"); return }
        val name = URLUtil.guessFileName(url, cd, mime)
        val size = if (len > 0) " (${fmtSize(len)})" else ""
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Baixar arquivo?")
            .setMessage("$name$size\n\nVai pra pasta Download/Uncrowned.")
            .setPositiveButton("Baixar") { _, _ ->
                NavDl.start(applicationContext, url, name, mime, ua, CookieManager.getInstance().getCookie(url), web?.url, if (proxied) port else 0) { msg -> ui.post { toast(msg) } }
                toast("Baixando $name…")
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        val last = counted && --alive == 0
        counted = false
        tabs.forEach { destroy(it.web) }; tabs.clear(); cur = -1
        super.onDestroy()
        if (last) {
            // última tela do navegador: apaga tudo e encerra o processo pra não sobrar nada na memória
            wipeAll()
            Process.killProcess(Process.myPid())
        }
    }

    private fun destroy(w: WebView) {
        runCatching {
            w.stopLoading(); w.loadUrl("about:blank")
            w.clearHistory(); w.clearCache(true); w.clearFormData()
            (w.parent as? ViewGroup)?.removeView(w); w.destroy()
        }
    }

    private fun wipeAll() {
        if (wiped) return; wiped = true
        runCatching { CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush() }
        runCatching { WebStorage.getInstance().deleteAllData() }
        if (proxied) runCatching { ProxyController.getInstance().clearProxyOverride(Executor { it.run() }) {} }
        DohProxy.stop()
        NavWipe.run(this)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private var alive = 0    // telas do navegador abertas nesse processo
        const val HOME = "https://lite.duckduckgo.com/lite/"
        const val SEARCH = "https://duckduckgo.com/?q="
        fun open(c: Context) {
            runCatching { c.startActivity(Intent(c, BrowserActivity::class.java)) }
                .onFailure { Toast.makeText(c, "Não deu pra abrir o navegador", Toast.LENGTH_SHORT).show() }
        }
        fun fmtSize(b: Long) = when { b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0); b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0); else -> "${b / 1024} KB" }
    }
}

/* Baixa o arquivo pelo proxy local (DNS 1.1.1.1) e salva em Download/Uncrowned, com o progresso na notificação. */
object NavDl {
    val running = AtomicInteger(0)
    private val ids = AtomicInteger(9100)
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "nav-dl").apply { isDaemon = true } }

    fun start(c: Context, url: String, name: String, mime: String?, ua: String?, cookie: String?, referer: String?, port: Int, done: (String) -> Unit) {
        running.incrementAndGet()
        val nid = ids.incrementAndGet()
        pool.execute {
            val nm = c.getSystemService(NotificationManager::class.java)
            runCatching { nm.createNotificationChannel(NotificationChannel("navdl", "Downloads do navegador", NotificationManager.IMPORTANCE_LOW)) }
            fun note(text: String, p: Int, max: Int, ongoing: Boolean, open: android.net.Uri? = null, type: String? = null) = runCatching {
                val b = Notification.Builder(c, "navdl").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(name).setContentText(text)
                    .setOngoing(ongoing).setOnlyAlertOnce(true)
                if (ongoing) b.setProgress(max, p, max == 0) else b.setSmallIcon(android.R.drawable.stat_sys_download_done).setAutoCancel(true)
                if (open != null) b.setContentIntent(PendingIntent.getActivity(c, nid, Intent(Intent.ACTION_VIEW).setDataAndType(open, type ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE))
                nm.notify(nid, b.build())
            }
            var uri: android.net.Uri? = null
            try {
                note("Começando…", 0, 0, true)
                val proxy = if (port > 0) java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port)) else java.net.Proxy.NO_PROXY
                val con = URL(url).openConnection(proxy) as HttpURLConnection
                con.connectTimeout = 20000; con.readTimeout = 60000; con.instanceFollowRedirects = true
                ua?.let { con.setRequestProperty("User-Agent", it) }
                cookie?.let { con.setRequestProperty("Cookie", it) }
                referer?.takeIf { it.startsWith("http") }?.let { con.setRequestProperty("Referer", it) }
                try {
                    if (con.responseCode !in 200..299) error("o site respondeu ${con.responseCode}")
                    val type = (con.contentType ?: mime ?: "application/octet-stream").substringBefore(';').trim()
                    val total = con.contentLengthLong
                    val v = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, type)
                        put(MediaStore.Downloads.RELATIVE_PATH, "Download/Uncrowned"); put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val r = c.contentResolver
                    uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: error("não deu pra criar o arquivo")
                    var n = 0L; var lastNote = 0L
                    (r.openOutputStream(uri!!) ?: error("não deu pra abrir o arquivo")).use { o ->
                        con.inputStream.use { inp ->
                            val buf = ByteArray(128 * 1024)
                            while (true) {
                                val k = inp.read(buf); if (k < 0) break
                                o.write(buf, 0, k); n += k
                                val now = System.currentTimeMillis()
                                if (now - lastNote > 700) { lastNote = now
                                    if (total > 0) note("${BrowserActivity.fmtSize(n)} de ${BrowserActivity.fmtSize(total)}", (n * 100 / total).toInt(), 100, true)
                                    else note(BrowserActivity.fmtSize(n), 0, 0, true) }
                            }
                        }
                    }
                    r.update(uri!!, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                    note("Baixado em Download/Uncrowned • toque pra abrir", 0, 0, false, uri, type)
                    done("Baixado: $name")
                } finally { con.disconnect() }
            } catch (e: Throwable) {
                uri?.let { u -> runCatching { c.contentResolver.delete(u, null, null) } }
                note("Não deu pra baixar: ${e.message ?: "erro de conexão"}", 0, 0, false)
                done("Não deu pra baixar $name")
            } finally { running.decrementAndGet() }
        }
    }
}

/* Apaga a pasta e o cache do WebView do navegador (só os do processo ":nav"). */
object NavWipe {
    const val SUFFIX = "nav"
    fun run(c: Context) {
        runCatching {
            val data = File(c.applicationInfo.dataDir)
            data.listFiles()?.filter { it.name.startsWith("app_webview") && it.name.endsWith("_$SUFFIX") }?.forEach { it.deleteRecursively() }
            c.cacheDir.listFiles()?.forEach { f ->
                val n = f.name.lowercase()
                if ((n.contains("webview") || n.contains("chromium")) && n.endsWith("_$SUFFIX")) f.deleteRecursively()
                else if (f.isDirectory && (n.contains("webview") || n.contains("chromium"))) File(f, SUFFIX).takeIf { it.exists() }?.deleteRecursively()
            }
        }
    }
}

/* DNS-over-HTTPS da Cloudflare (one.one.one.one). Respostas ficam só na memória, enquanto o navegador está aberto. */
object Doh {
    private val servers = listOf("1.1.1.1", "1.0.0.1")
    private class Hit(val ip: InetAddress, val until: Long)
    private val mem = ConcurrentHashMap<String, Hit>()

    fun resolve(host: String): InetAddress {
        val h = host.trim('[', ']').lowercase()
        if (h == "localhost") throw IllegalArgumentException("bloqueado")
        // já é um IP: não precisa perguntar
        if (h.matches(Regex("^[0-9.]+$")) || h.contains(':')) return InetAddress.getByName(h)
        mem[h]?.let { if (it.until > System.currentTimeMillis()) return it.ip }
        for (type in listOf("A", "AAAA")) for (s in servers) {
            val r = runCatching { ask(s, h, type) }.getOrNull() ?: continue
            mem[h] = Hit(r.first, System.currentTimeMillis() + r.second.coerceIn(30, 600) * 1000L)
            return r.first
        }
        throw java.net.UnknownHostException(h)
    }

    private fun ask(server: String, host: String, type: String): Pair<InetAddress, Long>? {
        val c = URL("https://$server/dns-query?name=${URLEncoder.encode(host, "UTF-8")}&type=$type").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 5000; c.readTimeout = 5000; c.useCaches = false
            c.setRequestProperty("Accept", "application/dns-json")
            if (c.responseCode != 200) return null
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            if (j.optInt("Status", 2) != 0) return null
            val ans = j.optJSONArray("Answer") ?: return null
            val want = if (type == "A") 1 else 28
            for (i in 0 until ans.length()) {
                val a = ans.getJSONObject(i)
                if (a.optInt("type") == want) return InetAddress.getByName(a.getString("data")) to a.optLong("TTL", 300)
            }
            return null
        } finally { c.disconnect() }
    }

    fun clear() = mem.clear()
}

/* Proxy local (127.0.0.1) que o WebView usa: resolve cada endereço pelo Doh e só repassa os bytes. */
object DohProxy {
    @Volatile private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "nav-proxy").apply { isDaemon = true } }

    @Synchronized fun start(): Int {
        server?.let { if (!it.isClosed) return it.localPort }
        val s = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        server = s
        pool.execute {
            while (!s.isClosed) {
                val c = runCatching { s.accept() }.getOrNull() ?: break
                pool.execute { handle(c) }
            }
        }
        return s.localPort
    }

    @Synchronized fun stop() {
        runCatching { server?.close() }; server = null; Doh.clear()
    }

    private fun handle(client: Socket) {
        var up: Socket? = null
        try {
            client.soTimeout = 30000
            val cin = client.getInputStream(); val cout = client.getOutputStream()
            val head = readHead(cin) ?: return client.close()
            val lines = head.split("\r\n")
            val parts = lines[0].split(' ')
            if (parts.size < 3) return client.close()
            val method = parts[0]
            if (method.equals("CONNECT", true)) {
                val hp = parts[1]
                val i = hp.lastIndexOf(':')
                val host = if (i > 0) hp.substring(0, i) else hp
                val port = if (i > 0) hp.substring(i + 1).toIntOrNull() ?: 443 else 443
                up = open(host, port) ?: return fail(client, cout)
                cout.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray()); cout.flush()
                client.soTimeout = 0
                pipe(client, up)
            } else {
                val u = runCatching { URI(parts[1]) }.getOrNull() ?: return fail(client, cout)
                val host = u.host ?: return fail(client, cout)
                val port = if (u.port > 0) u.port else 80
                val path = (u.rawPath?.ifEmpty { "/" } ?: "/") + (u.rawQuery?.let { "?$it" } ?: "")
                up = open(host, port) ?: return fail(client, cout)
                val sb = StringBuilder("$method $path ${parts[2]}\r\n")
                for (l in lines.drop(1)) {
                    if (l.isEmpty()) continue
                    val k = l.substringBefore(':').trim().lowercase()
                    if (k == "proxy-connection" || k == "connection" || k == "keep-alive") continue
                    sb.append(l).append("\r\n")
                }
                sb.append("Connection: close\r\n\r\n")
                up.getOutputStream().apply { write(sb.toString().toByteArray(Charsets.ISO_8859_1)); flush() }
                client.soTimeout = 0
                pipe(client, up)
            }
        } catch (_: Throwable) {
            runCatching { client.close() }; runCatching { up?.close() }
        }
    }

    private fun open(host: String, port: Int): Socket? = runCatching {
        // bloqueador bruto: endereço de anúncio/rastreador nem chega a ser procurado no DNS
        if (NavBlock.blocked(host)) return null
        val ip = Doh.resolve(host)
        if (ip.isLoopbackAddress || ip.isAnyLocalAddress) return null
        Socket().apply { tcpNoDelay = true; connect(InetSocketAddress(ip, port), 10000) }
    }.getOrNull()

    private fun fail(c: Socket, out: OutputStream) {
        runCatching { out.write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); out.flush() }
        runCatching { c.close() }
    }

    // lê o cabeçalho até a linha em branco, byte a byte, sem engolir o corpo
    private fun readHead(inp: InputStream): String? {
        val buf = java.io.ByteArrayOutputStream()
        var m = 0
        while (buf.size() < 65536) {
            val b = inp.read(); if (b < 0) return null
            buf.write(b)
            m = when {
                (m == 0 || m == 2) && b == '\r'.code -> m + 1
                (m == 1 || m == 3) && b == '\n'.code -> m + 1
                b == '\r'.code -> 1
                else -> 0
            }
            if (m == 4) return buf.toString("ISO-8859-1").removeSuffix("\r\n\r\n")
        }
        return null
    }

    private fun pipe(a: Socket, b: Socket) {
        val done = { runCatching { a.close() }; runCatching { b.close() } }
        pool.execute {
            try { copy(a.getInputStream(), b.getOutputStream()) } catch (_: Throwable) {} finally { runCatching { b.shutdownOutput() } }
        }
        try { copy(b.getInputStream(), a.getOutputStream()) } catch (_: Throwable) {} finally { done() }
    }

    private fun copy(i: InputStream, o: OutputStream) {
        val buf = ByteArray(16384)
        while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); o.flush() }
    }
}
