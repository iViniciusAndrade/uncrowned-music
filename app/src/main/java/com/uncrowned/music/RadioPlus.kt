package com.uncrowned.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.text.Normalizer
import javax.net.ssl.SSLSocketFactory

/* Sinal ICY das rádios: no meio do áudio vem "StreamTitle='Artista - Música'".
   Conexão crua (socket) porque muita rádio responde "ICY 200 OK", que o Android não aceita. */
object Icy {
    class Conn(val sock: Socket, val headers: Map<String, String>, val input: InputStream) { fun close() = runCatching { sock.close() } }

    fun connect(url0: String, redirects: Int = 4): Conn {
        val u = URL(url0); val secure = u.protocol == "https"
        val port = if (u.port > 0) u.port else if (secure) 443 else 80
        val raw = Socket(); raw.connect(InetSocketAddress(u.host, port), 10000); raw.soTimeout = 15000
        val s = if (secure) (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, u.host, port, true) else raw
        val path = (u.path.ifBlank { "/" }) + (u.query?.let { "?$it" } ?: "")
        s.getOutputStream().apply { write("GET $path HTTP/1.0\r\nHost: ${u.host}\r\nUser-Agent: UncrownedMusic/1.0\r\nIcy-MetaData: 1\r\nAccept: */*\r\nConnection: close\r\n\r\n".toByteArray()); flush() }
        val inp = s.getInputStream().buffered(32 * 1024)
        val head = StringBuilder(); var last4 = 0
        while (true) { val b = inp.read(); if (b < 0) error("a rádio fechou"); head.append(b.toChar()); last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A || head.endsWith("\n\n")) break; if (head.length > 16384) error("cabeçalho inválido") }
        val lines = head.toString().split("\r\n", "\n")
        val code = Regex("\\s(\\d{3})").find(lines[0])?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val h = HashMap<String, String>(); lines.drop(1).forEach { l -> val k = l.indexOf(':'); if (k > 0) h[l.substring(0, k).trim().lowercase()] = l.substring(k + 1).trim() }
        if (code in 300..399 && h["location"] != null && redirects > 0) { runCatching { s.close() }; return connect(URL(u, h["location"]).toString(), redirects - 1) }
        if (code !in 200..299) { runCatching { s.close() }; error("a rádio respondeu $code") }
        return Conn(s, h, inp)
    }

    private fun fix(s: String): String = if (Regex("Ã.|Â.").containsMatchIn(s)) runCatching { String(s.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8) }.getOrDefault(s) else s
    fun parseTitle(meta: ByteArray): String? {
        val txt = String(meta, Charsets.UTF_8).trimEnd('\u0000')
        return Regex("StreamTitle='(.*?)';", RegexOption.DOT_MATCHES_ALL).find(txt)?.groupValues?.get(1)?.trim()?.let { fix(it) }
    }

    /* separa áudio e título: lê blocos e chama onAudio / onTitle */
    fun pump(c: Conn, stop: () -> Boolean, onAudio: (ByteArray, Int) -> Unit, onTitle: (String) -> Unit) {
        val metaint = c.headers["icy-metaint"]?.toIntOrNull() ?: 0
        val inp = c.input; val buf = ByteArray(16 * 1024)
        if (metaint <= 0) { while (!stop()) { val n = inp.read(buf); if (n < 0) break; onAudio(buf, n) }; return }
        while (!stop()) {
            var left = metaint
            while (left > 0 && !stop()) { val n = inp.read(buf, 0, minOf(left, buf.size)); if (n < 0) return; onAudio(buf, n); left -= n }
            val len = inp.read(); if (len < 0) return
            if (len > 0) { val m = ByteArray(len * 16); var got = 0; while (got < m.size) { val n = inp.read(m, got, m.size - got); if (n < 0) return; got += n }
                parseTitle(m)?.let { if (it.isNotBlank()) onTitle(it) } }
        }
    }

    /* só pega o título atual e desconecta */
    suspend fun now(stream: String): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(15000) {
            runCatching {
                val c = connect(stream)
                try {
                    if ((c.headers["icy-metaint"]?.toIntOrNull() ?: 0) <= 0) return@runCatching null
                    var t: String? = null; var bytes = 0
                    pump(c, { t != null || bytes > 400_000 }, { _, n -> bytes += n }, { t = it })
                    t
                } finally { c.close() }
            }.getOrNull()
        }
    }
}

/* Caçador de rádio: vigia várias rádios e avisa quando tocar um artista que você curte */
object Hunter {
    var on: Boolean get() = Prefs.sp.getBoolean("hunt_on", false); set(v) { Prefs.sp.edit().putBoolean("hunt_on", v).apply(); version++ }
    var useTop: Boolean get() = Prefs.sp.getBoolean("hunt_top", true); set(v) { Prefs.sp.edit().putBoolean("hunt_top", v).apply() }
    fun artists(): List<String> = runCatching { JSONArray(Prefs.sp.getString("hunt_art", "[]")).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList())
    fun setArtists(l: List<String>) { Prefs.sp.edit().putString("hunt_art", JSONArray(l.distinct().take(40)).toString()).apply(); version++ }
    var version by mutableIntStateOf(0)
    var hits = mutableStateListOf<Triple<Track, String, Long>>()   // rádio, música, quando
    var nowPlaying = mutableStateMapOf<String, String>()
    var scanning by mutableStateOf(false)
    var pendingPlay by mutableStateOf<Track?>(null)
    private val seen = HashMap<String, Long>()
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun n(x: String) = Normalizer.normalize(x.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        job?.cancel()
        if (!on || artists().isEmpty() || Eco.extreme) return
        job = scope.launch { while (isActive) { runCatching { cycle(app) }; delay((if (Eco.on) 10 else 2) * 60_000L) } }
    }
    fun stop() { job?.cancel(); job = null }

    suspend fun cycle(app: Context) {
        val arts = artists().map { it to n(it) }.filter { it.second.length >= 3 }; if (arts.isEmpty()) return
        scanning = true
        var st = RadioApi.favs()
        if (useTop) st = st + RadioApi.stations("", "", "BR").take(40)
        st = st.distinctBy { it.id }.take(50)
        val sem = Semaphore(4)
        st.map { s -> scope.async { sem.withPermit {
            val t = Icy.now(s.url) ?: return@withPermit
            withContext(Dispatchers.Main) { nowPlaying[s.id] = t }
            val nt = " ${n(t)} "
            val hit = arts.firstOrNull { nt.contains(" ${it.second} ") } ?: return@withPermit
            val key = s.id + "|" + n(t)
            if (System.currentTimeMillis() - (seen[key] ?: 0) < 40 * 60_000) return@withPermit
            seen[key] = System.currentTimeMillis()
            withContext(Dispatchers.Main) { hits.add(0, Triple(s, t, System.currentTimeMillis())); if (hits.size > 30) hits.removeAt(hits.size - 1) }
            notify(app, s, t, hit.first)
        } } }.awaitAll()
        scanning = false
    }

    private fun notify(ctx: Context, s: Track, title: String, artist: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("hunt", "Caçador de rádio", NotificationManager.IMPORTANCE_HIGH))
        val i = Intent(ctx, MainActivity::class.java).putExtra("hunt_station", s.json().toString()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(ctx, s.id.hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, "hunt").setSmallIcon(R.drawable.ic_note).setContentTitle("$artist tocando agora na ${s.title}")
            .setContentText(title).setContentIntent(pi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build()
        runCatching { nm.notify(s.id.hashCode(), n) }
    }
}

@Composable
fun HunterCard(ui: PlayerUi) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Hunter.version
    var open by remember { mutableStateOf(false) }
    var txt by remember { mutableStateOf("") }
    val arts = Hunter.artists()
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TrackChanges, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text("Caçador de rádio", fontWeight = FontWeight.Bold, fontFamily = Skins.font)
                    Text(if (Hunter.on) "Vigiando ${if (Hunter.useTop) "suas rádios e as 40 mais ouvidas" else "suas rádios"} a cada ${if (Eco.on) 10 else 2} min" else "Avisa quando tocar quem você curte em qualquer rádio",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(Hunter.on, { Hunter.on = it; if (it) { if (arts.isEmpty()) open = true; Hunter.start(ctx) } else Hunter.stop() })
            }
            if (Hunter.on || open) {
                Text(if (arts.isEmpty()) "Diga quais artistas caçar:" else "Caçando: " + arts.joinToString(", "), Modifier.padding(top = 10.dp), fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(txt, { txt = it }, Modifier.weight(1f), placeholder = { Text("Artista (ex: Matuê)") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                    TextButton(onClick = { if (txt.isNotBlank()) { Hunter.setArtists(arts + txt.trim()); txt = ""; Hunter.start(ctx) } }) { Text("Adicionar") }
                }
                if (arts.isNotEmpty()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { Hunter.setArtists(emptyList()) }) { Text("Limpar") }
                    TextButton(onClick = { scope.launch { Hunter.cycle(ctx.applicationContext) } }, enabled = !Hunter.scanning) { Text(if (Hunter.scanning) "Procurando…" else "Procurar agora") }
                    Spacer(Modifier.weight(1f))
                    Text("Top BR", fontSize = 12.sp); Switch(Hunter.useTop, { Hunter.useTop = it; Hunter.version++ })
                }
                Hunter.hits.take(5).forEach { (s, t, at) ->
                    Row(Modifier.fillMaxWidth().clickable { RadioRecorder.lastStation = s; ui.playOnline(listOf(s), 0) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(t, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${s.title} • há ${((System.currentTimeMillis() - at) / 60000).coerceAtLeast(0)} min", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.PlayArrow, null)
                    }
                }
            }
        }
    }
}

/* nome da música que está passando na rádio que você está ouvindo */
@Composable
fun RadioNowLine(station: Track?) {
    var t by remember(station?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(station?.id) { while (station != null) { t = Icy.now(station.url) ?: t; delay(if (Eco.on) 60_000 else 20_000) } }
    val s = t ?: return
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.GraphicEq, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text("  Tocando agora: $s", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
