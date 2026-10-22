package com.uncrowned.music

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/* ---------- Rádio ao vivo (Radio Browser: catálogo aberto e gratuito) + rádio FM do celular ---------- */
object RadioApi {
    private val servers = listOf("https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info", "https://at1.api.radio-browser.info", "https://fi1.api.radio-browser.info")

    private fun get(path: String): String? {
        for (s in servers) try {
            val c = URL(s + path).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 12000
            c.setRequestProperty("User-Agent", "UncrownedMusic/1.0")
            if (c.responseCode == 200) return c.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {}
        return null
    }

    suspend fun stations(q: String, tag: String, country: String): List<Track> = withContext(Dispatchers.IO) {
        val p = StringBuilder("/json/stations/search?hidebroken=true&order=clickcount&reverse=true&limit=80")
        if (q.isNotBlank()) p.append("&name=").append(URLEncoder.encode(q, "UTF-8"))
        if (tag.isNotBlank()) p.append("&tag=").append(URLEncoder.encode(tag, "UTF-8"))
        if (country != "ZZ") p.append("&countrycode=").append(country)
        val body = get(p.toString()) ?: return@withContext emptyList()
        val a = JSONArray(body); val seen = HashSet<String>()
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            val name = o.optString("name").trim()
            if (o.optInt("hls") == 1 || url.isBlank() || name.isBlank() || !seen.add(name.lowercase())) null
            else {
                val sub = listOf(o.optString("state").ifBlank { o.optString("country") }, o.optString("tags").split(",").filter { it.isNotBlank() }.take(2).joinToString(", "))
                    .filter { it.isNotBlank() }.joinToString(" · ")
                Track("radio-" + o.optString("stationuuid"), name, sub, "Rádio", 0, o.optString("favicon"), false, url)
            }
        }
    }

    fun favs(): List<Track> = JSONArray(Prefs.sp.getString("radiofavs", "[]")).let { a -> List(a.length()) { Track.from(a.getJSONObject(it)) } }
    fun toggleFav(t: Track) {
        val f = favs(); val n = if (f.any { it.id == t.id }) f.filter { it.id != t.id } else listOf(t) + f
        Prefs.sp.edit().putString("radiofavs", JSONArray(n.map { it.json() }).toString()).apply()
    }

    /* Rádio FM sem internet: o Android não deixa outros apps controlarem o FM, então abrimos o app de rádio FM
       que vem no celular (Samsung, Motorola, Xiaomi, LG...). Precisa de fone com fio, que serve de antena. */
    private val fmPackages = listOf(
        "com.sec.android.app.fm", "com.samsung.android.app.fm", "com.motorola.fmplayer", "com.motorola.android.fmradio",
        "com.miui.fm", "com.android.fmradio", "com.caf.fmradio", "com.lge.fmradio", "com.huawei.android.FMRadio",
        "com.sonyericsson.fmradio", "com.sony.fmradio", "com.oppo.fmradio", "com.coloros.fmradio", "com.realme.fmradio",
        "com.vivo.FMRadio", "com.asus.fmradio", "com.lenovo.fmradio", "com.mediatek.FMRadio", "com.tcl.fmradio", "com.qualcomm.fmradio"
    )
    fun openFm(ctx: Context): Boolean {
        val pm = ctx.packageManager
        for (p in fmPackages) pm.getLaunchIntentForPackage(p)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true }
        // procura qualquer app instalado com "FM" ou "Rádio" no nome
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hit = pm.queryIntentActivities(main, 0).firstOrNull {
            val label = it.loadLabel(pm).toString()
            it.activityInfo.packageName != ctx.packageName && Regex("(?i)(\\bfm\\b|fm radio|r[aá]dio fm|fmradio)").containsMatchIn(label + " " + it.activityInfo.packageName)
        }
        if (hit != null) { ctx.startActivity(Intent(main).setClassName(hit.activityInfo.packageName, hit.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true }
        return false
    }
}

/* Gravador: puxa o áudio da rádio e salva do jeito que ela transmite (MP3/AAC) na pasta Música/Uncrowned */
object RadioRecorder {
    var recording by mutableStateOf<Track?>(null); private set
    var startedAt by mutableLongStateOf(0L); private set
    var lastStation by mutableStateOf<Track?>(null)
    @Volatile private var stopFlag = false

    fun start(ctx: Context, t: Track) {
        if (recording != null) return
        val app = ctx.applicationContext
        stopFlag = false; recording = t; startedAt = System.currentTimeMillis()
        CoroutineScope(Dispatchers.IO).launch {
            val r = runCatching { record(app, t) }
            withContext(Dispatchers.Main) {
                recording = null
                Toast.makeText(app, r.fold({ "Gravação salva: $it" }, { "A gravação não deu certo: ${it.message ?: "sem conexão"}" }), Toast.LENGTH_LONG).show()
            }
        }
    }
    fun stop() { stopFlag = true }

    var split: Boolean get() = Prefs.sp.getBoolean("recsplit", true); set(v) = Prefs.sp.edit().putBoolean("recsplit", v).apply()
    var song by mutableStateOf(""); private set
    var parts by mutableIntStateOf(0); private set

    private class Out(val os: java.io.OutputStream, val uri: android.net.Uri?, val file: File?, var bytes: Long = 0)
    private fun create(ctx: Context, folder: String, name: String, mime: String, title: String, artist: String, album: String): Out {
        val r = ctx.contentResolver
        return if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name); put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Uncrowned" + if (folder.isNotBlank()) "/$folder" else "")
                put(MediaStore.Audio.Media.TITLE, title); put(MediaStore.Audio.Media.ARTIST, artist); put(MediaStore.Audio.Media.ALBUM, album)
                put(MediaStore.Audio.Media.IS_MUSIC, 1); put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val u = r.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v) ?: throw Exception("não deu pra criar o arquivo")
            Out(r.openOutputStream(u) ?: throw Exception("não deu pra abrir o arquivo"), u, null)
        } else {
            val dir = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Uncrowned"), folder).apply { mkdirs() }
            val f = File(dir, name); Out(f.outputStream(), null, f)
        }
    }
    private fun finish(ctx: Context, o: Out, mime: String): Boolean {
        runCatching { o.os.close() }
        val r = ctx.contentResolver
        if (o.bytes < 16000) { o.uri?.let { r.delete(it, null, null) }; o.file?.delete(); return false }
        o.uri?.let { r.update(it, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null) }
        o.file?.let { MediaScannerConnection.scanFile(ctx, arrayOf(it.absolutePath), arrayOf(mime), null) }
        return true
    }

    /* grava a rádio; com "separar por música" cada música vira um arquivo (usa o nome que a rádio manda) */
    private fun record(ctx: Context, t: Track): String {
        val c = Icy.connect(t.url)
        val type = (c.headers["content-type"] ?: "audio/mpeg").lowercase()
        val (ext, mime) = when { "aac" in type || "mp4" in type -> "aac" to "audio/aac"; "ogg" in type -> "ogg" to "audio/ogg"; else -> "mp3" to "audio/mpeg" }
        val stamp = SimpleDateFormat("dd-MM-yyyy HH'h'mm", Locale("pt", "BR")).format(Date())
        val clean = { x: String -> x.replace(Regex("[\\/:*?\"<>|]"), "").replace(Regex("\\s+"), " ").trim().take(120) }
        val doSplit = split && (c.headers["icy-metaint"]?.toIntOrNull() ?: 0) > 0
        val folder = if (doSplit) clean("Rádio ${t.title} - $stamp") else ""
        var cur: Out? = null; var curTitle: String? = null; var saved = 0; var n = 0
        fun open(title: String?) {
            cur?.let { if (finish(ctx, it, mime)) saved++ }
            n++; curTitle = title
            val base = if (doSplit) clean("%02d - %s".format(n, title ?: t.title)) else clean("${t.title} - $stamp")
            val parts2 = title?.split(" - ", limit = 2)
            cur = create(ctx, folder, "$base.$ext", mime, if (doSplit) (parts2?.getOrNull(1) ?: title ?: t.title) else "${t.title} ($stamp)",
                if (doSplit) (parts2?.getOrNull(0) ?: t.title) else t.title, if (doSplit) "Rádio ${t.title}" else "Gravações de rádio")
            song = title ?: ""; parts = n
        }
        open(null)
        try {
            Icy.pump(c, { stopFlag }, { b, len -> cur?.let { o -> o.os.write(b, 0, len); o.bytes += len } }) { title ->
                if (doSplit && title != curTitle) open(title) else if (curTitle == null) { curTitle = title; song = title }
            }
        } catch (e: Exception) { if ((cur?.bytes ?: 0) < 8000 && saved == 0) { cur?.let { finish(ctx, it, mime) }; throw e } } finally { c.close() }
        cur?.let { if (finish(ctx, it, mime)) saved++ }
        if (saved == 0) throw Exception("a rádio não mandou áudio")
        return if (doSplit) "$saved ${if (saved == 1) "música" else "músicas"} em Música/Uncrowned/$folder" else "Música/Uncrowned"
    }
}

private val RTAGS = listOf("" to "Mais ouvidas", "funk" to "Funk", "sertanejo" to "Sertanejo", "pagode" to "Pagode", "pop" to "Pop", "rock" to "Rock",
    "hip hop" to "Rap", "electronic" to "Eletrônica", "gospel" to "Gospel", "mpb" to "MPB", "news" to "Notícias", "sports" to "Esportes")

@Composable
fun RadioScreen(ui: PlayerUi, onBack: (() -> Unit)?) {
    if (onBack != null) BackHandler { onBack() }
    val ctx = LocalContext.current
    var country by remember { mutableStateOf("BR") }
    var tag by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<Track>?>(null) }
    var favs by remember { mutableStateOf(RadioApi.favs()) }
    LaunchedEffect(country, tag, search) { list = null; list = RadioApi.stations(search, tag, country) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Rádio", onBack = onBack) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Radio, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Rádio FM sem internet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Abre o rádio FM do seu celular. Conecte um fone com fio, ele funciona como antena.", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        ui.controller?.pause()
                        if (!RadioApi.openFm(ctx)) Toast.makeText(ctx, "Esse celular não tem rádio FM. Use as rádios pela internet abaixo.", Toast.LENGTH_LONG).show()
                    }) { Text("Abrir") }
                }
            }
        }
        item { RecordCard(ui) }
        item { if (RadioRecorder.lastStation != null && ui.mediaId == RadioRecorder.lastStation?.mediaId) RadioNowLine(RadioRecorder.lastStation) }
        item { HunterCard(ui) }
        item {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Procurar rádio (Jovem Pan, Antena 1...)") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true, shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search = q.trim() }))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("BR" to "Brasil", "ZZ" to "Mundo").forEach { (k, n) -> FilterChip(country == k, { country = k }, { Text(n) }) }
                Spacer(Modifier.width(6.dp))
                RTAGS.forEach { (k, n) -> FilterChip(tag == k && search.isBlank(), { tag = k; search = ""; q = "" }, { Text(n) }) }
            }
        }
        if (favs.isNotEmpty()) {
            item { Text("Suas rádios", Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            itemsIndexed(favs, key = { _, t -> "f" + t.id }) { i, t -> StationRow(t, true, ui.mediaId == t.mediaId, { RadioRecorder.lastStation = t; ui.playOnline(favs, i) }) { RadioApi.toggleFav(t); favs = RadioApi.favs() } }
        }
        item {
            Text(if (search.isNotBlank()) "Resultados pra \"$search\"" else (RTAGS.firstOrNull { it.first == tag }?.second ?: "") + if (country == "BR") " no Brasil" else " no mundo",
                Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        val l = list
        when {
            l == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
            l.isEmpty() -> item { Text("Nenhuma rádio encontrada. Precisa de internet pra essa lista.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> itemsIndexed(l, key = { _, t -> "l" + t.id }) { i, t ->
                StationRow(t, favs.any { it.id == t.id }, ui.mediaId == t.mediaId, { RadioRecorder.lastStation = t; ui.playOnline(l, i) }) { RadioApi.toggleFav(t); favs = RadioApi.favs() }
            }
        }
    }
}

@Composable
private fun StationRow(t: Track, fav: Boolean, playing: Boolean, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onPlay() }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(t.art.ifBlank { null }, Modifier.size(52.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (playing) MaterialTheme.colorScheme.primary else Color.Unspecified)
            Text(if (playing) "AO VIVO · ${t.artist}" else t.artist, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onFav) { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun RecordCard(ui: PlayerUi) {
    val ctx = LocalContext.current
    val rec = RadioRecorder.recording
    val st = RadioRecorder.lastStation
    val live = st != null && ui.mediaId == st.mediaId
    if (rec == null && !live) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(rec) { while (rec != null) { now = System.currentTimeMillis(); delay(1000) } }
    val secs = ((now - RadioRecorder.startedAt) / 1000).coerceAtLeast(0)
    val red = Color(0xFFFF3B30)
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (rec != null) red.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (rec != null) "Gravando: ${rec.title}" else "Tocando agora: ${st!!.title}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (rec != null) "● %d:%02d · %s".format(secs / 60, secs % 60, if (RadioRecorder.parts > 1 || RadioRecorder.song.isNotBlank()) "música ${RadioRecorder.parts}: ${RadioRecorder.song}" else "salva em Música/Uncrowned quando parar") else "Grave o que está passando e ouça depois sem internet",
                    fontSize = 12.5.sp, color = if (rec != null) red else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                if (rec == null) Row(verticalAlignment = Alignment.CenterVertically) {
                    var sp by remember { mutableStateOf(RadioRecorder.split) }
                    Checkbox(sp, { sp = it; RadioRecorder.split = it }); Text("Um arquivo por música", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
            if (rec != null) OutlinedButton(onClick = { RadioRecorder.stop() }) { Text("Parar", color = red) }
            else Button(onClick = { RadioRecorder.start(ctx, st!!) }, colors = ButtonDefaults.buttonColors(containerColor = red)) { Text("● Gravar", color = Color.White) }
        }
    }
}
