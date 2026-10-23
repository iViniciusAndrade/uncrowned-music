package com.uncrowned.music

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
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

/*
 * Navegador privado (opção secreta).
 * - Roda num processo separado (":nav") com pasta própria do WebView: nada de cookie, login,
 *   histórico ou cache se mistura com o resto do app, e tudo é apagado ao fechar.
 * - Todo endereço é resolvido pelo DNS da Cloudflare (one.one.one.one) via DNS-over-HTTPS,
 *   através de um proxy local que só existe enquanto o navegador está aberto.
 */
class BrowserActivity : Activity() {
    private var web: WebView? = null
    private lateinit var bar: EditText
    private lateinit var prog: ProgressBar
    private lateinit var lock: TextView
    private var proxied = false
    private var wiped = false
    private var counted = false

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

        val bg = Color.parseColor("#0E0E10"); val fg = Color.WHITE; val dim = Color.parseColor("#8E8E93")
        window.statusBarColor = bg; window.navigationBarColor = bg

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        fun btn(t: String, onClick: () -> Unit) = TextView(this).apply {
            text = t; setTextColor(fg); textSize = 20f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            setOnClickListener { onClick() }
        }
        lock = TextView(this).apply {
            text = "🕶"; textSize = 15f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(42))
            setOnClickListener {
                Toast.makeText(this@BrowserActivity, if (proxied) "Privado • DNS one.one.one.one (1.1.1.1) • nada é salvo" else "Privado • nada é salvo (DNS do celular)", Toast.LENGTH_LONG).show()
            }
        }
        bar = EditText(this).apply {
            hint = "Pesquisar ou digitar endereço"; setHintTextColor(dim); setTextColor(fg); textSize = 15f
            isSingleLine = true; setSelectAllOnFocus(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.parseColor("#1F1F23")) }
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
            setOnEditorActionListener { _, id, ev ->
                if (id == EditorInfo.IME_ACTION_GO || (ev?.keyCode == KeyEvent.KEYCODE_ENTER && ev.action == KeyEvent.ACTION_DOWN)) { go(text.toString()); true } else false
            }
        }
        top.addView(btn("‹") { web?.let { if (it.canGoBack()) it.goBack() } })
        top.addView(lock)
        top.addView(bar)
        top.addView(btn("↻") { web?.reload() })
        top.addView(btn("✕") { finish() })

        prog = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2))
        }
        val holder = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }

        val w = WebView(this)
        web = w
        w.setBackgroundColor(bg)
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
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, false)
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val s = req.url.scheme ?: return true
                return s != "http" && s != "https"   // intent:, market:, etc. ficam bloqueados
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) { if (!bar.hasFocus()) bar.setText(url) }
            override fun onPageFinished(view: WebView, url: String) { if (!bar.hasFocus()) bar.setText(url) }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) { prog.progress = p; prog.visibility = if (p in 1..99) View.VISIBLE else View.GONE }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }
        w.setDownloadListener { _, _, _, _, _ -> Toast.makeText(this, "Downloads ficam desligados no navegador privado", Toast.LENGTH_SHORT).show() }
        holder.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        root.addView(top); root.addView(prog); root.addView(holder)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom); WindowInsetsCompat.CONSUMED
        }

        val first = intent?.dataString ?: HOME
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            val port = DohProxy.start()
            val cfg = ProxyConfig.Builder().addProxyRule("127.0.0.1:$port").build()
            ProxyController.getInstance().setProxyOverride(cfg, Executor { it.run() }) { proxied = true; runOnUiThread { w.loadUrl(first) } }
        } else {
            Toast.makeText(this, "Esse WebView não deixa trocar o DNS: usando o do celular", Toast.LENGTH_LONG).show()
            w.loadUrl(first)
        }
    }

    private fun go(raw: String) {
        val t = raw.trim(); if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> SEARCH + URLEncoder.encode(t, "UTF-8")
        }
        bar.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(bar.windowToken, 0)
        web?.loadUrl(url)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val w = web
        if (w != null && w.canGoBack()) w.goBack() else { @Suppress("DEPRECATION") super.onBackPressed() }
    }

    override fun onDestroy() {
        val last = counted && --alive == 0
        counted = false
        destroyWeb()
        super.onDestroy()
        if (last) {
            // última tela do navegador: apaga tudo e encerra o processo pra não sobrar nada na memória
            wipeAll()
            Process.killProcess(Process.myPid())
        }
    }

    private fun destroyWeb() {
        web?.let { w ->
            runCatching {
                w.stopLoading(); w.loadUrl("about:blank")
                w.clearHistory(); w.clearCache(true); w.clearFormData()
                (w.parent as? ViewGroup)?.removeView(w); w.destroy()
            }
        }
        web = null
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
