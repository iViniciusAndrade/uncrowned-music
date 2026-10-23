package com.uncrowned.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
    data class Msg(val me: Boolean, val text: String)
    val msgs = mutableStateListOf<Msg>()
    var busy by mutableStateOf(false)

    // IA local
    const val MODEL_URL = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    const val MODEL_SIZE = 546_660_344L
    private fun modelFile(c: Context) = File(c.filesDir, "ia/qwen05.task")
    fun hasModel(c: Context) = modelFile(c).length() == MODEL_SIZE
    var dl by mutableFloatStateOf(-1f)   // progresso do download (0..1), -1 = parado
    private var llm: com.google.mediapipe.tasks.genai.llminference.LlmInference? = null

    suspend fun download(c: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val f = modelFile(c); f.parentFile?.mkdirs()
            val part = File(f.path + ".part")
            dl = (part.length().toFloat() / MODEL_SIZE)
            var url = MODEL_URL; var con: HttpURLConnection
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
                    while (true) { val r = inp.read(buf); if (r < 0) break; o.write(buf, 0, r); n += r; if (n > MODEL_SIZE) error("arquivo diferente do esperado"); dl = n.toFloat() / MODEL_SIZE }
                }
            }
            con.disconnect()
            if (part.length() != MODEL_SIZE) error("download incompleto, tente de novo (ele continua de onde parou)")
            part.renameTo(f)
            dl = -1f
        }.onFailure { dl = -1f }
    }
    fun deleteModel(c: Context) { release(); modelFile(c).delete(); File(modelFile(c).path + ".part").delete() }
    fun release() { runCatching { llm?.close() }; llm = null }

    private fun engine(c: Context): com.google.mediapipe.tasks.genai.llminference.LlmInference {
        llm?.let { return it }
        val o = com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile(c).path).setMaxTokens(768).setMaxTopK(20).build()
        return com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions(c, o).also { llm = it }
    }

    private const val SYS = """Você é o assistente do app de música Uncrowned. Transforme o pedido em UMA linha de JSON, sem mais nada:
{"acao":"...","valor":"...","resposta":"..."}
acao pode ser: tocar (valor = música/artista/gênero pra buscar), pausar, continuar, proxima, anterior, velocidade (valor = número de 0.5 a 1.5), timer (valor = minutos), aleatorio, repetir, efeito (valor = k7, vinil ou normal), conversa (só responder).
resposta = frase curta e descontraída em português do Brasil.
Exemplos:
pedido: me dá uma vibe triste pra chorar no banho -> {"acao":"tocar","valor":"músicas tristes","resposta":"Separei umas pra chorar em paz."}
pedido: tá muito rápido isso -> {"acao":"velocidade","valor":"0.85","resposta":"Desacelerei pra você."}
pedido: vou dormir daqui meia hora -> {"acao":"timer","valor":"30","resposta":"Desligo em 30 minutos. Bom sono."}
pedido: quem é você -> {"acao":"conversa","valor":"","resposta":"Sou o assistente do Uncrowned. Me pede música que eu toco."}"""

    private fun askModel(c: Context, text: String): String {
        val p = "<|im_start|>system\n$SYS<|im_end|>\n<|im_start|>user\npedido: $text<|im_end|>\n<|im_start|>assistant\n"
        return engine(c).generateResponse(p)
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
        if (has("slowed", "lento", "devagar", "desacelera")) return speed(0.85f, "Slowed ligado.")
        if (has("sped up", "speed up", "acelera", "rapido")) return speed(1.15f, "Sped up ligado.")
        if (has("nightcore")) return speed(1.25f, "Nightcore ligado.")
        if (has("velocidade normal", "normaliza", "tira o slowed", "velocidade padrao")) return speed(1f, "Velocidade normal.")
        Regex("velocidade (\\d+([.,]\\d+)?)").find(t)?.let { val v = it.groupValues[1].replace(',', '.').toFloat().let { x -> if (x > 3) x / 100f else x }; return speed(v.coerceIn(0.5f, 1.5f), "Velocidade em ${"%.2f".format(v.coerceIn(0.5f, 1.5f))}x.") }
        if (has("cancela o timer", "tira o timer", "sem timer")) { Sleep.cancel(); return "Timer cancelado." }
        if (has("aleatorio", "embaralha", "shuffle")) { if (web) YtPlayer.shuffleRest() else ui.controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }; return "Embaralhei." }
        if (has("repete", "repetir", "repeat")) { if (web) YtPlayer.cycleRepeat() else ui.controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 }; return "Repetição trocada." }
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
        val songs = r.songs
        if (songs.isEmpty()) return "Não achei \"$q\"."
        ui.controller?.pause(); YtPlayer.play(songs, 0)
        return "Tocando ${songs[0].title} — ${songs[0].channel}"
    }

    private suspend fun handle(c: Context, ui: PlayerUi, text: String): String {
        val t = norm(text).trimEnd('.', '!', '?')
        withContext(Dispatchers.Main) { rules(ui, t) }?.let { return it }
        if (Eco.extreme) return "No modo Extremo a IA fica desligada pra economizar. $HELP"
        if (!hasModel(c)) return "Esse eu não entendi. Baixe a IA local aqui em cima pra eu entender pedidos do seu jeito. $HELP"
        val out = withContext(Dispatchers.Default) { askModel(c, text) }
        val j = Regex("\\{.*\\}", RegexOption.DOT_MATCHES_ALL).find(out)?.value?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?: return out.trim().ifBlank { "Não entendi." }
        val acao = j.optString("acao"); val valor = j.optString("valor"); val resp = j.optString("resposta")
        val done = withContext(Dispatchers.Main) {
            when (acao) {
                "tocar" -> if (valor.isNotBlank()) play(ui, valor) else null
                "pausar" -> rules(ui, "pausa"); "continuar" -> rules(ui, "continua")
                "proxima" -> rules(ui, "proxima"); "anterior" -> rules(ui, "anterior")
                "velocidade" -> valor.replace(',', '.').toFloatOrNull()?.let { speed(it.coerceIn(0.5f, 1.5f), "") }
                "timer" -> valor.filter { it.isDigit() }.toIntOrNull()?.let { Sleep.start(it.coerceIn(1, 600)); "" }
                "aleatorio" -> rules(ui, "aleatorio"); "repetir" -> rules(ui, "repete")
                "efeito" -> { Retro.mode = when (norm(valor)) { "k7" -> "k7"; "vinil" -> "vinil"; else -> "" }; Prefs.version.intValue++; "" }
                else -> null
            }
        }
        return listOfNotNull(resp.ifBlank { null }, done?.takeIf { it.isNotBlank() && acao == "tocar" }).joinToString("\n").ifBlank { done ?: "Feito." }
    }
}

/* ---------- Tela do assistente ---------- */
@Composable
fun AssistScreen(ui: PlayerUi, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var has by remember { mutableStateOf(Assist.hasModel(ctx)) }
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
                    if (has) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("  IA local instalada (roda no celular, sem internet)", fontSize = 13.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { Assist.deleteModel(ctx); has = false }) { Text("Apagar") }
                    } else if (Assist.dl >= 0f) {
                        Text("Baixando a IA… ${(Assist.dl * 100).toInt()}%", fontSize = 13.sp)
                        LinearProgressIndicator({ Assist.dl }, Modifier.fillMaxWidth().padding(top = 6.dp))
                    } else {
                        Text("IA local (opcional): entende pedidos do seu jeito, sem internet e sem chave. Baixa uma vez (~520 MB, melhor no Wi-Fi).", fontSize = 13.sp)
                        OutlinedButton(onClick = { scope.launch {
                            val r = Assist.download(ctx); has = Assist.hasModel(ctx)
                            android.widget.Toast.makeText(ctx, if (r.isSuccess) "IA instalada" else "Não deu: ${r.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                        } }, Modifier.padding(top = 6.dp)) { Icon(Icons.Rounded.Download, null); Text(" Baixar IA local") }
                    }
                }
            }
            items(Assist.msgs) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.me) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(m.text, Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
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
