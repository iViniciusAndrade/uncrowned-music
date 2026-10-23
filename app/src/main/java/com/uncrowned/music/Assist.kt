package com.uncrowned.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

/* ---------- Falar pelo microfone (reconhecimento de voz do próprio Android, em português) ---------- */
@Composable
fun rememberVoice(onText: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val l = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
    }
    return {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Fala aí")
        runCatching { l.launch(i) }.onFailure { android.widget.Toast.makeText(ctx, "Esse celular não tem reconhecimento de voz (instale o app Google)", android.widget.Toast.LENGTH_LONG).show() }
    }
}

/* ---------- Assistente: entende o pedido e faz dentro do app ----------
   1) comandos que ele já conhece (instantâneo, sem internet)
   2) o resto vai pra IA local (Qwen 2.5 0.5B, roda no próprio celular, baixada uma vez) */
object Assist {
    data class Msg(val me: Boolean, val text: String, val img: String? = null)
    val msgs = mutableStateListOf<Msg>()
    var busy by mutableStateOf(false)

    /* ---------- IA local ---------- */
    data class Model(val id: String, val name: String, val desc: String, val url: String, val size: Long, val ctx: Int, val tpl: String = "qwen")
    val MODELS = listOf(
        Model("phi4", "Phi-4 mini 3.8B (a mais esperta)", "≈3,9 GB • raciocina bem melhor, mais lenta e pesada", "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.task", 3_910_050_199L, 4096, "phi"),
        Model("q15", "Qwen 2.5 1.5B (esperta)", "≈1,6 GB • bem mais esperta, conversa melhor", "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.task", 1_598_556_720L, 4096),
        Model("q05", "Qwen 2.5 0.5B (leve)", "≈520 MB • rápida, mas bem limitada", "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task", 546_660_344L, 1280),
    )
    private fun file(c: Context, m: Model) = File(c.filesDir, "ia/${if (m.id == "q05") "qwen05" else m.id}.task")
    fun installed(c: Context, m: Model) = file(c, m).length() == m.size
    // a melhor instalada
    fun active(c: Context): Model? = MODELS.firstOrNull { installed(c, it) }
    fun hasModel(c: Context) = active(c) != null
    var dl by mutableFloatStateOf(-1f)   // progresso do download (0..1), -1 = parado
    var dlId by mutableStateOf("")
    private var llm: com.google.mediapipe.tasks.genai.llminference.LlmInference? = null
    private var llmId = ""

    suspend fun download(c: Context, m: Model): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(c, m); f.parentFile?.mkdirs()
            val part = File(f.path + ".part")
            dl = (part.length().toFloat() / m.size)
            var url = m.url; var con: HttpURLConnection
            var hops = 0
            while (true) {
                con = URL(url).openConnection() as HttpURLConnection
                con.instanceFollowRedirects = false; con.connectTimeout = 15000; con.readTimeout = 30000
                if (part.length() > 0) con.setRequestProperty("Range", "bytes=${part.length()}-")
                val code = con.responseCode
                if (code in 300..399) { val loc = con.getHeaderField("Location") ?: error("download falhou"); con.disconnect(); url = URL(URL(url), loc).toString(); if (!url.startsWith("https://") || ++hops > 6) error("link inseguro"); continue }
                if (code !in 200..299) error("HTTP $code")
                if (code == 200 && part.length() > 0) part.delete()   // servidor ignorou o "continuar": começa do zero
                break
            }
            con.inputStream.use { inp ->
                java.io.FileOutputStream(part, true).use { o ->
                    val buf = ByteArray(256 * 1024); var n = part.length()
                    while (true) { val r = inp.read(buf); if (r < 0) break; o.write(buf, 0, r); n += r; if (n > m.size) error("arquivo diferente do esperado"); dl = n.toFloat() / m.size }
                }
            }
            con.disconnect()
            if (part.length() != m.size) error("download incompleto, tente de novo (ele continua de onde parou)")
            part.renameTo(f)
            dl = -1f
        }.onFailure { dl = -1f }
    }
    fun deleteModel(c: Context, m: Model) { if (thinking || dl >= 0f) return; release(); file(c, m).delete(); File(file(c, m).path + ".part").delete() }
    // não fecha a IA no meio de uma resposta (isso derrubaria o app): fecha assim que ela terminar
    private val lock = Any(); private var thinking = false; private var closeLater = false
    fun release() = synchronized(lock) { if (thinking) closeLater = true else { runCatching { llm?.close() }; llm = null; llmId = "" } }
    private val bg = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    // download continua mesmo se você sair da tela
    fun startDownload(c: Context, m: Model, done: (Result<Unit>) -> Unit) { if (dl >= 0f) return; dl = 0f; dlId = m.id; bg.launch { val r = download(c.applicationContext, m); withContext(Dispatchers.Main) { done(r) } } }

    private fun engine(c: Context, m: Model): com.google.mediapipe.tasks.genai.llminference.LlmInference {
        if (llmId == m.id) llm?.let { return it }
        runCatching { llm?.close() }; llm = null
        val o = com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions.builder()
            .setModelPath(file(c, m).path).setMaxTokens(minOf(m.ctx, 2048)).setMaxTopK(40).build()
        return com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions(c, o).also { llm = it; llmId = m.id }
    }

    private fun sys(): String {
        val hoje = java.text.SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy, HH:mm", java.util.Locale("pt", "BR")).format(java.util.Date())
        val tocando = YtPlayer.current?.let { "${it.title} — ${it.channel}" } ?: "nada"
        return """Você é a IA do Uncrowned, um app de música, rodando direto no celular do usuário. Fale em português do Brasil, à vontade e direto, como um amigo. Converse sobre qualquer assunto, sem enrolar.
Hoje é $hoje. Tocando agora: $tocando.
Ferramentas (use só quando precisar):
- Pra pesquisar na internet (notícias, fatos, algo que você não sabe ou que muda com o tempo), responda APENAS uma linha: BUSCAR: termos da pesquisa
  Depois você recebe os resultados e responde com base neles.
- Pra gerar uma imagem que o usuário pediu, responda uma linha: IMAGEM: descrição detalhada da imagem em inglês
- Pra baixar um arquivo de um link direto que o usuário mandou, responda uma linha: BAIXAR: https://...
- Se o usuário pedir algo do app, escreva sua resposta e no fim uma linha: AÇÃO: tocar <o que buscar> | pausar | continuar | proxima | anterior | velocidade <0.5 a 1.5> | timer <minutos> | aleatorio | repetir | efeito <k7|vinil|black|normal>
Não invente ações que o usuário não pediu."""
    }

    // conversa com memória das últimas mensagens (cabe no limite do modelo)
    private fun prompt(m: Model, extra: List<Pair<String, String>>): String {
        val budget = (if (m.ctx >= 4096) 2048 else 1100) * 3   // ~3 letras por token
        fun turn(role: String, txt: String) = if (m.tpl == "phi") "<|$role|>\n$txt<|end|>\n" else "<|im_start|>$role\n$txt<|im_end|>\n"
        val turns = ArrayList<String>()
        var used = 0
        for (msg in (msgs.map { (if (it.me) "user" else "assistant") to it.text } + extra).asReversed()) {
            val t = turn(msg.first, msg.second.take(1500))
            if (used + t.length > budget && turns.isNotEmpty()) break
            used += t.length; turns.add(0, t)
        }
        return turn("system", sys()) + turns.joinToString("") + (if (m.tpl == "phi") "<|assistant|>\n" else "<|im_start|>assistant\n")
    }

    private fun gen(c: Context, m: Model, p: String): String {
        synchronized(lock) { thinking = true; closeLater = false }
        try { return engine(c, m).generateResponse(p).replace("<|im_end|>", "").replace("<|end|>", "").trim() }
        finally { synchronized(lock) { thinking = false; if (closeLater) { closeLater = false; runCatching { llm?.close() }; llm = null; llmId = "" } } }
    }

    /* ---------- internet (sem chave): DuckDuckGo + Wikipédia ---------- */
    /* ---------- gerar imagem (Pollinations, chave grátis em enter.pollinations.ai) ---------- */
    var imgKey: String get() = Prefs.sp.getString("polkey", "") ?: ""; set(v) { Prefs.sp.edit().putString("polkey", v.trim()).apply() }
    // limite que não sai: nada sexual envolvendo menores
    private val MINOR = Regex("(?i)(crian[cç]a|menor de idade|menina|menino|garotinh|novinh|adolescente|teen|child|kid|minor|loli|shota|underage|\\b1[0-7] ?anos|\\b[1-9] ?anos|year[s]?[ -]old)")
    private val SEXUAL = Regex("(?i)(nu[ad]?\\b|pelad|sexo|sexual|er[oó]tic|porn|nsfw|nude|naked|sex|explicit|hentai|lingerie|seios|bunda|transando)")
    suspend fun makeImage(c: Context, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (MINOR.containsMatchIn(prompt) && SEXUAL.containsMatchIn(prompt)) error("isso eu não gero")
            val key = imgKey.ifBlank { error("pra gerar imagem, cole sua chave grátis do Pollinations aqui em cima (enter.pollinations.ai)") }
            val u = "https://gen.pollinations.ai/image/" + java.net.URLEncoder.encode(prompt.take(900), "UTF-8").replace("+", "%20") + "?width=1024&height=1024&seed=-1&nologo=true"
            val con = URL(u).openConnection() as HttpURLConnection
            con.setRequestProperty("Authorization", "Bearer $key"); con.connectTimeout = 20000; con.readTimeout = 120000
            try {
                if (con.responseCode !in 200..299) error("o gerador recusou (HTTP ${con.responseCode}): " + (runCatching { con.errorStream?.bufferedReader()?.readText()?.take(160) }.getOrNull() ?: ""))
                val ct = con.contentType ?: ""
                if (!ct.startsWith("image/")) error("o gerador não mandou imagem")
                val bytes = con.inputStream.use { it.readBytes() }
                val dir = File(c.filesDir, "ia/img").apply { mkdirs() }
                val f = File(dir, "img_${System.currentTimeMillis()}.${if (ct.contains("png")) "png" else "jpg"}"); f.writeBytes(bytes)
                f.path
            } finally { con.disconnect() }
        }
    }
    // salva a imagem na galeria (Imagens/Uncrowned)
    fun saveToGallery(c: Context, path: String): Boolean = runCatching {
        val f = File(path); val png = path.endsWith("png")
        val v = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, f.name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, if (png) "image/png" else "image/jpeg")
            if (android.os.Build.VERSION.SDK_INT >= 29) put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Uncrowned")
        }
        val uri = c.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return@runCatching false
        c.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }; true
    }.getOrDefault(false)

    /* ---------- baixar arquivo de um link direto (vai pra Downloads/Uncrowned) ---------- */
    private val NO_DL = Regex("(?i)(youtube\\.com|youtu\\.be|googlevideo\\.com|soundcloud\\.com|sndcdn\\.com|spotify\\.com|deezer\\.com|music\\.apple\\.com|tidal\\.com)")
    suspend fun downloadFile(c: Context, url0: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (NO_DL.containsMatchIn(url0)) error("de site de streaming eu não baixo (YouTube, SoundCloud, Spotify…)")
            if (android.os.Build.VERSION.SDK_INT < 29) error("precisa de Android 10 ou mais novo")
            var url = url0.trim(); var con: HttpURLConnection; var hops = 0
            while (true) {
                if (!url.startsWith("https://") && !url.startsWith("http://")) error("link inválido")
                con = URL(url).openConnection() as HttpURLConnection
                con.instanceFollowRedirects = false; con.connectTimeout = 15000; con.readTimeout = 30000
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/124 Mobile")
                val code = con.responseCode
                if (code in 300..399) { url = URL(URL(url), con.getHeaderField("Location") ?: error("link quebrado")).toString(); con.disconnect(); if (NO_DL.containsMatchIn(url)) error("de site de streaming eu não baixo"); if (++hops > 8) error("redirecionou demais"); continue }
                if (code !in 200..299) error("HTTP $code")
                break
            }
            val ct = con.contentType ?: "application/octet-stream"
            if (ct.startsWith("text/html")) { con.disconnect(); error("esse link é uma página, não um arquivo") }
            val cd = con.getHeaderField("Content-Disposition") ?: ""
            var name = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)").find(cd)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                ?: java.net.URLDecoder.decode(URL(url).path.substringAfterLast('/'), "UTF-8")
            name = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120).ifBlank { "arquivo_${System.currentTimeMillis()}" }
            val v = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name); put(android.provider.MediaStore.Downloads.MIME_TYPE, ct.substringBefore(';'))
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Uncrowned")
            }
            val uri = c.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: error("não deu pra criar o arquivo")
            try {
                c.contentResolver.openOutputStream(uri)!!.use { o -> con.inputStream.use { inp ->
                    val buf = ByteArray(128 * 1024); var n = 0L
                    while (true) { val r = inp.read(buf); if (r < 0) break; n += r; if (n > 4L * 1024 * 1024 * 1024) error("arquivo grande demais"); o.write(buf, 0, r) }
                } }
            } catch (e: Throwable) { runCatching { c.contentResolver.delete(uri, null, null) }; throw e } finally { con.disconnect() }
            name
        }
    }

    private fun httpGet(u: String): String {
        val con = URL(u).openConnection() as HttpURLConnection
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
        con.connectTimeout = 10000; con.readTimeout = 12000
        return try { con.inputStream.bufferedReader().readText() } finally { con.disconnect() }
    }
    private fun strip(h: String) = android.text.Html.fromHtml(h, android.text.Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
    fun webSearch(q: String): String {
        val out = StringBuilder()
        runCatching {
            val h = httpGet("https://html.duckduckgo.com/html/?kl=br-pt&q=" + java.net.URLEncoder.encode(q, "UTF-8"))
            val titles = Regex("class=\"result__a\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(h).map { strip(it.groupValues[1]) }.toList()
            val snips = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(h).map { strip(it.groupValues[1]) }.toList()
            titles.zip(snips).take(5).forEachIndexed { i, (t, sn) -> out.append("${i + 1}. $t: $sn\n") }
        }
        if (out.length < 80) runCatching {
            val j = JSONObject(httpGet("https://pt.wikipedia.org/w/api.php?action=query&list=search&format=json&srlimit=3&srsearch=" + java.net.URLEncoder.encode(q, "UTF-8")))
            val a = j.getJSONObject("query").getJSONArray("search")
            for (i in 0 until a.length()) { val o = a.getJSONObject(i); out.append("- ${o.optString("title")}: ${strip(o.optString("snippet"))}\n") }
        }
        return out.toString().ifBlank { "(nenhum resultado)" }.take(2400)
    }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()

    fun send(c: Context, ui: PlayerUi, text0: String, scope: kotlinx.coroutines.CoroutineScope) {
        val text = text0.trim(); if (text.isEmpty() || busy) return
        msgs.add(Msg(true, text))
        busy = true
        scope.launch {
            val reply = runCatching { handle(c, ui, text) }.getOrElse { "Deu ruim aqui: ${it.message ?: "erro"}" }
            msgs.add(Msg(false, reply)); busy = false
        }
    }

    /* comandos conhecidos; null = não entendi */
    private suspend fun rules(ui: PlayerUi, t: String): String? {
        val web = YtPlayer.current != null
        fun has(vararg w: String) = w.any { Regex("(^|\\s)$it(\\s|$)").containsMatchIn(t) }
        Regex("^(toca|tocar|toque|bota|botar|coloca|colocar|poe|por|quero ouvir|ouvir|play) (.+)$").find(t)?.let { m ->
            val q = m.groupValues[2].removePrefix("a musica ").removePrefix("musica ").removePrefix("um ").removePrefix("uma ").removePrefix("umas ").removePrefix("uns ").trim()
            if (q.isNotEmpty() && q !in setOf("musica", "som", "de novo")) return play(ui, q)
        }
        Regex("(timer|desliga|desligar|dormir|para|parar) (em |daqui |depois de )?(\\d+|meia) ?(min|minuto|minutos|h|hora|horas)").find(t)?.let { m ->
            val n = if (m.groupValues[3] == "meia") 30 else m.groupValues[3].toInt() * (if (m.groupValues[4].startsWith("h")) 60 else 1)
            Sleep.start(n.coerceIn(1, 600)); return "Desligo em $n minutos."
        }
        if (has("pausa", "pause", "pausar", "para a musica", "parar", "silencio")) { ui.controller?.pause(); if (YtPlayer.playing) YtPlayer.toggle(); return "Pausei." }
        if (has("continua", "continuar", "despausa", "solta o som", "volta a tocar", "play")) { if (web) { if (!YtPlayer.playing) YtPlayer.toggle() } else ui.controller?.play(); return "Bora." }
        if (has("proxima", "pula", "pular", "passa", "skip", "next")) { if (web) YtPlayer.next() else ui.next(); return "Próxima." }
        if (has("anterior", "volta uma", "musica anterior", "voltar")) { if (web) YtPlayer.prev() else ui.prev(); return "Voltei uma." }
        if (has("slowed", "mais devagar", "mais lento", "desacelera", "muito rapido")) return speed(0.85f, "Slowed ligado.")
        if (has("sped up", "speed up", "acelera", "mais rapido", "muito lento")) return speed(1.15f, "Sped up ligado.")
        if (has("nightcore")) return speed(1.25f, "Nightcore ligado.")
        if (has("velocidade normal", "normaliza", "tira o slowed", "velocidade padrao")) return speed(1f, "Velocidade normal.")
        Regex("velocidade (\\d+([.,]\\d+)?)").find(t)?.let { val v = it.groupValues[1].replace(',', '.').toFloat().let { x -> if (x > 3) x / 100f else x }; return speed(v.coerceIn(0.5f, 1.5f), "Velocidade em ${"%.2f".format(v.coerceIn(0.5f, 1.5f))}x.") }
        if (has("cancela o timer", "tira o timer", "sem timer")) { Sleep.cancel(); return "Timer cancelado." }
        if (has("aleatorio", "embaralha", "shuffle")) { if (web) YtPlayer.shuffleRest() else ui.controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }; return "Embaralhei." }
        if (has("repete", "repetir", "repeat")) { if (web) YtPlayer.cycleRepeat() else ui.controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 }; return "Repetição trocada." }
        if (has("black metal", "black", "trve", "kvlt", "necro")) { Retro.mode = "black"; Prefs.version.intValue++; return "Modo Black Metal: som de garagem de 1984. 🤘" }
        if (has("k7", "fita", "cassete")) { Retro.mode = "k7"; Prefs.version.intValue++; return "Som de fita K7 ligado." }
        if (has("vinil", "disco")) { Retro.mode = "vinil"; Prefs.version.intValue++; return "Som de vinil ligado." }
        if (has("sem efeito", "tira o efeito", "efeito normal")) { Retro.mode = ""; Prefs.version.intValue++; return "Efeito desligado." }
        if (has("modo extremo", "economia extrema")) { Eco.mode = if (has("desliga", "tira", "sai")) "auto" else "extremo"; Prefs.version.intValue++; return if (Eco.extreme) "Modo Extremo ligado." else "Modo Extremo desligado." }
        if (has("o que ta tocando", "que musica e essa", "qual musica", "ta tocando o que")) {
            val v = YtPlayer.current; return if (v != null) "Tocando: ${v.title} — ${v.channel}" else if (ui.title.isNotBlank()) "Tocando: ${ui.title} — ${ui.artist}" else "Nada tocando agora."
        }
        if (has("minhas favoritas", "favoritas")) { val f = Prefs.favs(); val l = Library.songs.filter { it.id.toString() in f }; if (l.isEmpty()) return "Você ainda não tem favoritas no celular."; ui.play(l.shuffled(), 0); return "Tocando suas favoritas." }
        if (has("ajuda", "o que voce faz", "comandos")) return HELP
        return null
    }
    const val HELP = "Pode pedir: \"toca Matuê\", \"pausa\", \"próxima\", \"slowed\", \"nightcore\", \"velocidade normal\", \"desliga em 30 minutos\", \"aleatório\", \"repete\", \"som de vinil\", \"o que tá tocando\", \"minhas favoritas\". Com a IA local baixada, dá pra pedir do seu jeito."

    private fun speed(v: Float, msg: String): String { YtPlayer.setSpeedTo(v); Prefs.speed = v; Prefs.pitch = v; return msg }

    private suspend fun play(ui: PlayerUi, q: String): String {
        // primeiro as músicas do celular
        val n = norm(q)
        val loc = Library.songs.filter { norm(it.title).contains(n) || norm(it.artist).contains(n) }
        if (loc.isNotEmpty()) { ui.play(loc, 0); return "Tocando do celular: ${loc[0].title}" + if (loc.size > 1) " (+${loc.size - 1})" else "" }
        if (!Sync.online()) return "Sem internet e não achei \"$q\" no celular."
        val r = Ytm.search(q)
        // pediu um artista: toca as mais tocadas dele
        r.artists.firstOrNull { norm(it.name) == n }?.let { a ->
            val top = Ytm.artist(a.id)?.top.orEmpty()
            if (top.isNotEmpty()) { ui.controller?.pause(); YtPlayer.play(top, 0); return "Tocando ${a.name}: ${top[0].title}" + if (top.size > 1) " (+${top.size - 1} na fila)" else "" }
        }
        val songs = r.songs
        if (songs.isEmpty()) return "Não achei \"$q\"."
        ui.controller?.pause(); YtPlayer.play(songs, 0)
        return "Tocando ${songs[0].title} — ${songs[0].channel}"
    }

    // gera e já mostra no chat
    private suspend fun image(c: Context, prompt: String): String {
        withContext(Dispatchers.Main) { msgs.add(Msg(false, "🎨 gerando: $prompt")) }
        return makeImage(c, prompt).fold({ p -> withContext(Dispatchers.Main) { msgs.add(Msg(false, "", p)) }; "Pronto. Toca na imagem pra salvar na galeria." }, { "Não deu pra gerar: ${it.message}" })
    }

    private suspend fun handle(c: Context, ui: PlayerUi, text: String): String {
        val t = norm(text).trimEnd('.', '!', '?')
        val m = if (Eco.extreme) null else active(c)
        // gerar imagem / baixar link (funciona mesmo sem a IA baixada)
        Regex("^(gera|gerar|cria|criar|faz|fazer|desenha|desenhar|me (da|manda|faz)) (uma |um )?(imagem|foto|desenho|arte|wallpaper|papel de parede|capa)( de| do| da| com)? (.+)$").find(t)?.let { mm ->
            return image(c, mm.groupValues[6].trim())
        }
        Regex("^(baixa|baixar|download|salva|salvar)( esse| esse arquivo| o arquivo)?:? (https?://\\S+)").find(text.trim().lowercase())?.let { mm ->
            val u = Regex("https?://\\S+").find(text)!!.value
            return downloadFile(c, u).fold({ "Baixado em Downloads/Uncrowned: $it" }, { "Não deu pra baixar: ${it.message}" })
        }
        // comandos rápidos: "toca X" sempre; os outros só em frase curta (senão vira conversa com a IA)
        if (m == null || t.split(" ").size <= 5 || Regex("^(toca|tocar|toque|bota|coloca|poe|quero ouvir|ouvir) ").containsMatchIn(t))
            withContext(Dispatchers.Main) { rules(ui, t) }?.let { return it }
        if (Eco.extreme) return "No modo Extremo a IA fica desligada pra economizar. $HELP"
        if (m == null) return "Esse eu não entendi. Baixe a IA local aqui em cima pra conversar do seu jeito. $HELP"
        // a mensagem do usuário já está em msgs
        var out = withContext(Dispatchers.Default) { gen(c, m, prompt(m, emptyList())) }
        // pesquisa na internet quando ela pedir (até 2 vezes)
        val extra = ArrayList<Pair<String, String>>()
        repeat(2) {
            val q = Regex("(?im)^\\s*BUSCAR\\s*:\\s*(.+)$").find(out)?.groupValues?.get(1)?.trim() ?: return@repeat
            if (!Sync.online()) { out = "Tô sem internet agora, não consigo pesquisar \"$q\"."; return@repeat }
            withContext(Dispatchers.Main) { msgs.add(Msg(false, "🔎 pesquisando: $q")) }
            val res = withContext(Dispatchers.IO) { webSearch(q) }
            extra += "assistant" to "BUSCAR: $q"
            extra += "user" to "Resultados da pesquisa \"$q\":\n$res\nResponda minha pergunta com base nisso, em português."
            out = withContext(Dispatchers.Default) { gen(c, m, prompt(m, extra)) }
        }
        out = out.replace(Regex("(?im)^\\s*BUSCAR\\s*:.*$"), "").trim()
        // imagem / download pedidos pela IA
        Regex("(?im)^\\s*IMAGEM\\s*:\\s*(.+)$").find(out)?.let { mm -> val r = image(c, mm.groupValues[1].trim()); out = out.replace(mm.value, "").trim().ifBlank { r } }
        Regex("(?im)^\\s*BAIXAR\\s*:\\s*(https?://\\S+)").find(out)?.let { mm ->
            val r = downloadFile(c, mm.groupValues[1]).fold({ "Baixado em Downloads/Uncrowned: $it" }, { "Não deu pra baixar: ${it.message}" })
            out = (out.replace(mm.value, "").trim() + "\n" + r).trim()
        }
        // ação pedida pela IA
        val a = Regex("(?im)^\\s*A[ÇC][ÃA]O\\s*:\\s*(\\S+)\\s*(.*)$").find(out)
        var reply = out.replace(Regex("(?im)^\\s*A[ÇC][ÃA]O\\s*:.*$"), "").trim()
        // a IA pequena inventa ações: só executa se a mensagem pediu algo do app
        val pediu = Regex("(toca|tocar|toque|bota|coloca|poe|quero ouvir|ouvir|escutar|musica|som|vibe|playlist|pausa|para|proxima|pula|volta|anterior|velocidade|slowed|rapido|lento|devagar|timer|desliga|dormir|aleatorio|embaralha|repete|efeito|k7|vinil|black)").containsMatchIn(t)
        if (a != null && pediu) {
            val acao = norm(a.groupValues[1]); val valor = a.groupValues[2].trim().trim('<', '>', '"')
            val done = withContext(Dispatchers.Main) {
                when (acao) {
                    "tocar" -> if (valor.isNotBlank()) play(ui, valor) else null
                    "pausar" -> rules(ui, "pausa"); "continuar" -> rules(ui, "continua")
                    "proxima" -> rules(ui, "proxima"); "anterior" -> rules(ui, "anterior")
                    "velocidade" -> valor.replace(',', '.').toFloatOrNull()?.let { speed(it.coerceIn(0.5f, 1.5f), "Velocidade em ${"%.2f".format(it.coerceIn(0.5f, 1.5f))}x.") }
                    "timer" -> valor.filter { it.isDigit() }.toIntOrNull()?.let { Sleep.start(it.coerceIn(1, 600)); "Desligo em $it minutos." }
                    "aleatorio" -> rules(ui, "aleatorio"); "repetir" -> rules(ui, "repete")
                    "efeito" -> { Retro.mode = when (norm(valor)) { "k7" -> "k7"; "vinil" -> "vinil"; "black", "black metal" -> "black"; else -> "" }; Prefs.version.intValue++; "Efeito: ${Retro.NAMES[Retro.mode]}" }
                    else -> null
                }
            }
            if (done != null) reply = listOf(reply, done).filter { it.isNotBlank() }.joinToString("\n")
        }
        return reply.ifBlank { "…" }
    }
}

/* ---------- Tela do assistente ---------- */
@Composable
fun AssistScreen(ui: PlayerUi, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var ver by remember { mutableIntStateOf(0) }
    val voice = rememberVoice { Assist.send(ctx, ui, it, scope) }
    // saiu da tela: tira a IA da memória (ela ocupa bastante RAM)
    DisposableEffect(Unit) { onDispose { Assist.release() } }
    val st = rememberLazyListState()
    LaunchedEffect(Assist.msgs.size) { if (Assist.msgs.isNotEmpty()) st.animateScrollToItem(Assist.msgs.size) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Header("Assistente", onBack = onBack)
        LazyColumn(Modifier.weight(1f), state = st, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp)) {
                    Text("Fala ou escreve o que quer. Ex.: \"toca Matuê em slowed\", \"desliga em 20 minutos\", \"próxima\".", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    ver // atualiza quando instala/apaga
                    val act = Assist.active(ctx)
                    Text(if (act != null) "Usando: ${act.name}. Conversa sobre qualquer coisa, pesquisa na internet e controla o app." else "Baixe uma IA local (roda no celular, sem chave). Melhor no Wi-Fi.", fontSize = 13.sp)
                    Assist.MODELS.forEach { m ->
                        val inst = Assist.installed(ctx, m)
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (inst) Icons.Rounded.CheckCircle else Icons.Rounded.Memory, null, tint = if (inst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(m.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(m.desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            when {
                                Assist.dl >= 0f && Assist.dlId == m.id -> Text("${(Assist.dl * 100).toInt()}%", fontSize = 13.sp)
                                inst -> TextButton(onClick = { Assist.deleteModel(ctx, m); ver++ }) { Text("Apagar") }
                                else -> TextButton(enabled = Assist.dl < 0f, onClick = { val app = ctx.applicationContext; Assist.startDownload(ctx, m) { r ->
                                    ver++
                                    android.widget.Toast.makeText(app, if (r.isSuccess) "IA instalada" else "Não deu: ${r.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                                } }) { Text("Baixar") }
                            }
                        }
                        if (Assist.dl >= 0f && Assist.dlId == m.id) LinearProgressIndicator({ Assist.dl }, Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    // chave do gerador de imagens
                    var key by remember { mutableStateOf(Assist.imgKey) }
                    OutlinedTextField(key, { key = it; Assist.imgKey = it }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true,
                        label = { Text("Chave do Pollinations (pra gerar imagens)") }, placeholder = { Text("grátis em enter.pollinations.ai") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), shape = RoundedCornerShape(12.dp))
                    if (Assist.msgs.isNotEmpty()) TextButton(onClick = { Assist.msgs.clear() }, Modifier.align(Alignment.End)) { Text("Nova conversa") }
                }
            }
            items(Assist.msgs) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.me) Alignment.CenterEnd else Alignment.CenterStart) {
                    if (m.img != null) coil.compose.AsyncImage(java.io.File(m.img), null, Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                        .clickable { android.widget.Toast.makeText(ctx, if (Assist.saveToGallery(ctx, m.img)) "Salva em Imagens/Uncrowned" else "Não deu pra salvar", android.widget.Toast.LENGTH_SHORT).show() },
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    else Text(m.text, Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (m.me) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                        color = if (m.me) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                }
            }
            if (Assist.busy) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Text("  pensando…", fontSize = 13.sp) } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Pede aí…") }, singleLine = true, shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { Assist.send(ctx, ui, text, scope); text = "" }))
            if (text.isBlank()) FilledIconButton(onClick = voice, Modifier.padding(start = 6.dp).size(48.dp)) { Icon(Icons.Rounded.Mic, "Falar") }
            else FilledIconButton(onClick = { Assist.send(ctx, ui, text, scope); text = "" }, Modifier.padding(start = 6.dp).size(48.dp)) { Icon(Icons.Rounded.Send, "Enviar") }
        }
    }
}
