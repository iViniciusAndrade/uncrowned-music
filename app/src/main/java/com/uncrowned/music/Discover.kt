@file:OptIn(ExperimentalMaterial3Api::class)

package com.uncrowned.music

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import kotlin.random.Random

/* ---------- Faixa online (Audius: API aberta, grátis, sem chave) ---------- */
data class Track(
    val id: String, val title: String, val artist: String, val genre: String,
    val duration: Long, val art: String, val downloadable: Boolean, val url: String = ""
) {
    val mediaId get() = "au:$id"
    val stream get() = url.ifBlank { "${Audius.BASE}/tracks/$id/stream?app_name=${Audius.APP}" }
    fun toItem(): MediaItem = MediaItem.Builder().setMediaId(mediaId).setUri(stream)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(stream)).build())
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).setGenre(genre)
            .setArtworkUri(art.takeIf { it.isNotBlank() }?.let(Uri::parse)).build())
        .build()
    fun json() = JSONObject().put("id", id).put("t", title).put("a", artist).put("g", genre).put("d", duration).put("art", art).put("dl", downloadable).put("u", url)
    companion object {
        fun from(o: JSONObject) = Track(o.getString("id"), o.getString("t"), o.getString("a"), o.optString("g"), o.optLong("d"), o.optString("art"), o.optBoolean("dl"), o.optString("u"))
    }
}

object Audius {
    const val BASE = "https://api.audius.co/v1"
    const val APP = "UncrownedMusic"
    val GENRES = listOf("Electronic", "Hip-Hop/Rap", "Pop", "R&B/Soul", "Rock", "Alternative", "Latin", "Lo-Fi",
        "House", "Trap", "Deep House", "Techno", "Dubstep", "Jazz", "Acoustic", "Funk", "Reggae", "Metal", "Ambient", "Soundtrack")

    private suspend fun get(path: String): JSONArray = withContext(Dispatchers.IO) {
        val sep = if ('?' in path) '&' else '?'
        val c = URL("$BASE$path${sep}app_name=$APP").openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000
        try { JSONObject(c.inputStream.bufferedReader().readText()).optJSONArray("data") ?: JSONArray() } finally { c.disconnect() }
    }

    private fun parse(a: JSONArray) = List(a.length()) { i ->
        val o = a.getJSONObject(i)
        Track(o.getString("id"), o.optString("title"), o.optJSONObject("user")?.optString("name") ?: "",
            o.optString("genre"), o.optLong("duration") * 1000,
            o.optJSONObject("artwork")?.optString("480x480") ?: "",
            o.optBoolean("is_downloadable") || o.optJSONObject("download")?.optBoolean("is_downloadable") == true)
    }.filter { it.title.isNotBlank() }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    suspend fun search(q: String) = runCatching { parse(get("/tracks/search?query=${enc(q)}")) }
    private val trendCache = HashMap<String, Pair<Long, List<Track>>>()
    // guarda por 1 hora: abrir o Descobrir de novo não baixa tudo outra vez
    suspend fun trending(genre: String? = null, time: String = "week"): Result<List<Track>> {
        val k = "$genre|$time"
        trendCache[k]?.let { if (System.currentTimeMillis() - it.first < 3600_000L) return Result.success(it.second) }
        return runCatching { parse(get("/tracks/trending?time=$time" + (genre?.let { "&genre=${enc(it)}" } ?: ""))) }.onSuccess { if (it.isNotEmpty()) trendCache[k] = System.currentTimeMillis() to it }
    }

    fun download(ctx: Context, t: Track) {
        val req = DownloadManager.Request(Uri.parse(t.url.ifBlank { "$BASE/tracks/${t.id}/download?app_name=$APP" }))
            .setTitle(t.title).setDescription(t.artist)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "Uncrowned/${t.artist} - ${t.title}.mp3".replace(Regex("[\\\\:*?\"<>|]"), ""))
        (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(ctx, "Baixando \"${t.title}\"… vai aparecer em Músicas depois de reescanear", Toast.LENGTH_LONG).show()
    }
}

/* ---------- Internet Archive: músicas liberadas (Creative Commons / domínio público), sem chave ---------- */
object Archive {
    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000
        return try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }
    suspend fun search(q: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            val query = URLEncoder.encode("($q) AND mediatype:audio AND collection:(opensource_audio OR netlabels OR audio_music)", "UTF-8")
            val docs = JSONObject(get("https://archive.org/advancedsearch.php?q=$query&fl[]=identifier&fl[]=title&fl[]=creator&rows=10&sort[]=downloads+desc&output=json"))
                .getJSONObject("response").getJSONArray("docs")
            (0 until docs.length()).flatMap { i ->
                val d = docs.getJSONObject(i); val ident = d.getString("identifier")
                val creator = d.opt("creator").let { if (it is JSONArray) it.optString(0) else it?.toString() ?: "" }
                runCatching {
                    val files = JSONObject(get("https://archive.org/metadata/$ident")).getJSONArray("files")
                    (0 until files.length()).map { files.getJSONObject(it) }
                        .filter { it.optString("format").contains("MP3", true) && it.optString("source") == "original" || it.optString("name").endsWith(".mp3", true) }
                        .distinctBy { it.optString("name") }.take(3).map { f ->
                            val name = f.getString("name")
                            Track("ia_${ident}_${name.hashCode()}", f.optString("title").ifBlank { name.substringBeforeLast('.') },
                                f.optString("creator").ifBlank { creator }, "Archive",
                                (f.optString("length").toDoubleOrNull() ?: 0.0).times(1000).toLong(),
                                "https://archive.org/services/img/$ident", true,
                                "https://archive.org/download/$ident/" + URLEncoder.encode(name, "UTF-8").replace("+", "%20"))
                        }
                }.getOrDefault(emptyList())
            }
        }
    }
}

/* ---------- Sistema de gosto musical ----------
   Cada play soma pontos pro gênero e artista; curtir soma mais; pular nos primeiros 30s tira.
   Os pontos "esfriam" 10% por semana, então o perfil acompanha a sua fase. */
object Taste {
    private fun obj(k: String) = JSONObject(Prefs.sp.getString(k, "{}"))
    private fun bump(k: String, key: String, v: Double) {
        if (key.isBlank()) return
        val o = obj(k); o.put(key, o.optDouble(key, 0.0) + v); Prefs.sp.edit().putString(k, o.toString()).apply()
    }
    fun genres(): List<Pair<String, Double>> = decayed("tgenre")
    fun artists(): List<Pair<String, Double>> = decayed("tartist")
    private fun decayed(k: String): List<Pair<String, Double>> {
        val o = obj(k); return o.keys().asSequence().map { it to o.getDouble(it) }.filter { it.second > 0.5 }.sortedByDescending { it.second }.toList()
    }
    private fun heard() = Prefs.sp.getStringSet("theard", emptySet()) ?: emptySet()
    fun hasHeard(id: String) = id in heard()

    fun onPlay(genre: String, artist: String, id: String) {
        weeklyDecay()
        bump("tgenre", genre, 1.0); bump("tartist", artist, 1.0)
        Prefs.sp.edit().putStringSet("theard", (heard() + id).toList().takeLast(3000).toSet()).apply()
    }
    fun onLike(genre: String, artist: String) { bump("tgenre", genre, 3.0); bump("tartist", artist, 3.0) }
    fun onSkip(genre: String, artist: String) { bump("tgenre", genre, -0.5); bump("tartist", artist, -1.0) }
    fun pickGenre(g: String) = bump("tgenre", g, 5.0)
    fun learn(artist: String, w: Double) = bump("tartist", artist, w)
    fun reset() = Prefs.sp.edit().remove("tgenre").remove("tartist").remove("theard").remove("weekly").apply()

    private fun weeklyDecay() {
        val w = weekKey(); if (Prefs.sp.getInt("tdecay", w) == w) { Prefs.sp.edit().putInt("tdecay", w).apply(); return }
        listOf("tgenre", "tartist").forEach { k ->
            val o = obj(k); o.keys().asSequence().toList().forEach { o.put(it, o.getDouble(it) * 0.9) }
            Prefs.sp.edit().putString(k, o.toString()).apply()
        }
        Prefs.sp.edit().putInt("tdecay", w).apply()
    }
    fun weekKey(): Int { val c = Calendar.getInstance(); return c.get(Calendar.YEAR) * 100 + c.get(Calendar.WEEK_OF_YEAR) }
}

/* ---------- Descobertas da Semana ----------
   Mesma ideia do Discover Weekly: 30 faixas novas pra você, renovadas toda segunda.
   Mistura: os seus 3 gêneros favoritos (em alta na semana) + artistas parecidos com os que você mais ouve,
   tirando o que você já ouviu e limitando 2 faixas por artista. */
object Weekly {
    suspend fun get(force: Boolean = false): List<Track> {
        val w = Taste.weekKey()
        val cached = runCatching { JSONObject(Prefs.sp.getString("weekly", "{}")) }.getOrNull()
        if (!force && cached?.optInt("w") == w) cached.optJSONArray("t")?.let { a -> return List(a.length()) { Track.from(a.getJSONObject(it)) } }

        val genres = Taste.genres().take(3).map { it.first }.ifEmpty { listOf("Pop", "Electronic", "Hip-Hop/Rap") }
        val artists = Taste.artists().take(4).map { it.first }
        val pool = ArrayList<Track>()
        genres.forEachIndexed { i, g -> pool += Audius.trending(g, "week").getOrDefault(emptyList()).take(25 - i * 5) }
        artists.forEach { a -> pool += Audius.search(a).getOrDefault(emptyList()).filter { !it.artist.equals(a, true) }.take(6) }
        pool += Audius.trending(null, "week").getOrDefault(emptyList()).take(10)

        val rnd = Random(w)
        val perArtist = HashMap<String, Int>()
        val out = pool.distinctBy { it.id }.filter { !Taste.hasHeard(it.mediaId) }.shuffled(rnd)
            .filter { perArtist.merge(it.artist, 1, Int::plus)!! <= 2 }.take(30)
        Prefs.sp.edit().putString("weekly", JSONObject().put("w", w).put("t", JSONArray(out.map { it.json() })).toString()).apply()
        return out
    }
}

/* ---------- Tela "Descobrir" ---------- */
@Composable
fun DiscoverTab(ui: PlayerUi) {
    Prefs.version.intValue
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>?>(null) }
    var weekly by remember { mutableStateOf<List<Track>>(emptyList()) }
    var trending by remember { mutableStateOf<List<Track>>(emptyList()) }
    var trendGenre by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var onboarding by remember { mutableStateOf(Taste.genres().isEmpty()) }

    LaunchedEffect(onboarding) { if (!onboarding) weekly = Weekly.get() }
    LaunchedEffect(trendGenre) { trending = Audius.trending(trendGenre).getOrDefault(emptyList()) }

    if (onboarding) { GenrePicker { onboarding = false }; return }
    var ytListOpen by remember { mutableStateOf<YtmList?>(null) }
    ytListOpen?.let { l -> YtmListScreen(ui, l) { ytListOpen = null }; return }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Descobrir") { IconButton(onClick = { Nav.page = "themes" }) { Icon(Icons.Rounded.Palette, "Temas", tint = MaterialTheme.colorScheme.primary) } } }
        item { WeeklyHero(ui, weekly, onNew = { scope.launch { weekly = Weekly.get(force = true) } }, onTaste = { onboarding = true }) }
        item {
            LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(weekly, key = { _, t -> "w" + t.id }) { i, t ->
                    Column(Modifier.width(140.dp).clickable { ui.playOnline(weekly, i) }) {
                        Art(t.art, Modifier.size(140.dp), MaterialTheme.shapes.medium)
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(t.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (Eco.extreme) item {
            Text("Modo economia Extremo ligado: paradas e playlists ficam desligadas pra gastar o mínimo. Use Buscar pra achar músicas.",
                Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            item { WeekCharts(ui, trending) }
            item { PopularPlaylists(ui) { ytListOpen = it } }
        }

        item { TasteCard { onboarding = true } }

    }
}

@Composable
fun TrackRow(ui: PlayerUi, t: Track, rank: Int? = null, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val cur = ui.mediaId == t.mediaId
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .background(if (cur) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (rank != null) Text("$rank", Modifier.width(28.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Art(t.art, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                color = if (cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text("${t.artist} • ${t.genre.ifBlank { "—" }} • ${fmt(t.duration)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { Prefs.toggleFav(t.mediaId); if (Prefs.isFav(t.mediaId)) Taste.onLike(t.genre, t.artist) }) {
            Icon(if (Prefs.isFav(t.mediaId)) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null,
                tint = if (Prefs.isFav(t.mediaId)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = {
            if (t.downloadable) Audius.download(ctx, t)
            else Toast.makeText(ctx, "O artista não liberou download dessa. Dá pra ouvir online.", Toast.LENGTH_SHORT).show()
        }) { Icon(if (t.downloadable) Icons.Rounded.Download else Icons.Rounded.CloudOff, null,
            tint = if (t.downloadable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) }
    }
}

@Composable
fun TasteCard(onEdit: () -> Unit) {
    val g = Taste.genres().take(6); val a = Taste.artists().take(5)
    val max = (g.firstOrNull()?.second ?: 1.0)
    Card(Modifier.fillMaxWidth().padding(16.dp), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Seu gosto musical", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onEdit) { Text("Ajustar") }
            }
            g.forEach { (name, v) ->
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.width(110.dp), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { (v / max).toFloat() }, Modifier.weight(1f).height(8.dp))
                }
            }
            if (a.isNotEmpty()) Text("Mais ouvidos: " + a.joinToString { it.first }, Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenrePicker(onDone: () -> Unit) {
    var picked by remember { mutableStateOf(setOf<String>()) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Header("Do que você curte?")
        Text("Escolhe pelo menos 3. O app aprende o resto com o que você ouve, curte e pula.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.padding(vertical = 16.dp).weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Audius.GENRES.forEach { g -> FilterChip(g in picked, { picked = if (g in picked) picked - g else picked + g }, { Text(g) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { Taste.reset(); picked = emptySet() }) { Text("Zerar gosto") }
            Spacer(Modifier.weight(1f))
            Button(enabled = picked.size >= 3, onClick = { picked.forEach(Taste::pickGenre); Prefs.sp.edit().remove("weekly").apply(); onDone() }) { Text("Pronto") }
        }
    }
}


/* ---------- Descobertas da Semana: card com play e capas em leque ---------- */
@Composable
fun WeeklyHero(ui: PlayerUi, weekly: List<Track>, onNew: () -> Unit, onTaste: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.padding(16.dp).fillMaxWidth().height(230.dp).clip(MaterialTheme.shapes.extraLarge)
        .background(Brush.linearGradient(listOf(accent.copy(alpha = .95f).compositeOver(Color.Black), accent.copy(alpha = .35f).compositeOver(Color.Black), Color(0xFF0A0A0C))))
        .clickable { if (weekly.isNotEmpty()) ui.playOnline(weekly, 0) }) {
        // capas em leque
        Box(Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 10.dp).size(170.dp, 110.dp)) {
            weekly.take(3).forEachIndexed { i, t ->
                val d = i - 1
                Art(t.art, Modifier.align(Alignment.Center).offset(x = (d * 42).dp, y = (kotlin.math.abs(d) * 6).dp).graphicsLayer(rotationZ = d * 9f).size(if (d == 0) 96.dp else 82.dp).zIndex(if (d == 0) 2f else 1f).shadow(10.dp, RoundedCornerShape(10.dp)), RoundedCornerShape(10.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text("FEITA PRA VOCÊ", color = Color.White.copy(alpha = .8f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
            Text("Descobertas da Semana", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
            Text("${weekly.size} músicas novas pra você • renova toda semana", color = Color.White.copy(alpha = .85f), fontSize = 12.5.sp)
            val arts = weekly.map { it.artist }.filter { it.isNotBlank() }.distinct().take(3)
            if (arts.isNotEmpty()) Text("Com ${arts.joinToString(", ")} e mais", color = Color.White.copy(alpha = .6f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(52.dp).shadow(10.dp, CircleShape).clip(CircleShape).background(Color.White).clickable { if (weekly.isNotEmpty()) ui.playOnline(weekly, 0) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(32.dp), tint = Color.Black)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)).clickable { if (weekly.isNotEmpty()) ui.playOnline(weekly.shuffled(), 0) }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Shuffle, null, tint = Color.White) }
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)).clickable { onNew() }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Refresh, null, tint = Color.White) }
                Text("Ajustar gosto", Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .1f)).clickable { onTaste() }.padding(horizontal = 12.dp, vertical = 8.dp), color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/* ---------- Mais tocadas da semana: Audius, YouTube Music e SoundCloud ---------- */
@Composable
fun WeekCharts(ui: PlayerUi, audius: List<Track>) {
    var gl by remember { mutableStateOf("BR") }
    var src by remember { mutableStateOf("sc") }
    var all by remember { mutableStateOf(false) }
    var yt by remember { mutableStateOf<Ytm.Charts?>(null) }
    var sc by remember { mutableStateOf<List<YtVideo>?>(null) }
    LaunchedEffect(gl) { yt = null; sc = null; yt = Ytm.charts(gl); sc = SoundCloudApi.charts(gl) }
    val accent = mapOf("au" to Color(0xFFCC33FF), "yt" to Color(0xFFFF3B30), "sc" to Color(0xFFFF7700))
    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mais tocadas da semana", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            listOf("BR" to "Brasil", "ZZ" to "Mundo").forEach { (k, n) -> FilterChip(gl == k, { gl = k; all = false }, { Text(n, fontSize = 12.sp) }, Modifier.padding(start = 6.dp)) }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("sc" to "SoundCloud", "yt" to "YouTube", "au" to "Audius").forEach { (k, n) ->
                val on = src == k
                Text(n, Modifier.clip(RoundedCornerShape(50)).background(if (on) accent[k]!! else MaterialTheme.colorScheme.surfaceContainerHigh).clickable { src = k; all = false }.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
        val sub = when (src) { "au" -> "Em alta no mundo · artistas independentes"; "yt" -> yt?.title?.ifBlank { null } ?: "YouTube Music"; else -> if (gl == "BR") "Top no Brasil" else "Top no mundo" }
        Text(sub, Modifier.padding(horizontal = 16.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (src) {
            "au" -> { val l = if (all) audius else audius.take(10); l.forEachIndexed { i, t -> TrackRow(ui, t, rank = i + 1) { ui.playOnline(audius, i) } }; if (audius.size > 10) MoreBtn(all) { all = !all } }
            else -> {
                val list = if (src == "yt") yt?.top else sc
                if (list == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                else if (list.isEmpty()) Text("Não deu pra carregar agora.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else { (if (all) list else list.take(10)).forEachIndexed { i, v -> RankYtRow(i + 1, v) { ui.controller?.pause(); YtPlayer.play(list, i) } }; if (list.size > 10) MoreBtn(all) { all = !all } }
            }
        }
        val vids = yt?.videos.orEmpty()
        if (vids.isNotEmpty()) {
            Text("Clipes em alta", Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(vids, key = { _, v -> "cv" + v.id }) { i, v ->
                    Column(Modifier.width(220.dp).clickable { ui.controller?.pause(); YtPlayer.play(vids, i) }) {
                        Art(v.thumb, Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(10.dp))
                        Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(v.channel, maxLines = 1, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreBtn(all: Boolean, onClick: () -> Unit) = TextButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp)) { Text(if (all) "Mostrar menos" else "Ver todas") }

@Composable
fun RankYtRow(rank: Int, v: YtVideo, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", Modifier.width(28.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Art(v.thumb, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(v.channel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ---------- Playlists mais populares do YouTube ---------- */
@Composable
fun PopularPlaylists(ui: PlayerUi, onOpen: (YtmList) -> Unit) {
    var groups by remember { mutableStateOf<List<Pair<String, List<YtmList>>>>(emptyList()) }
    LaunchedEffect(Unit) { groups = Ytm.popularLists("BR") }
    if (groups.isEmpty()) return
    Column {
        Text("Playlists mais populares do YouTube", Modifier.padding(start = 16.dp, top = 18.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        groups.forEach { (shelf, items) ->
            if (groups.size > 1) Text(shelf, Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { l ->
                    Column(Modifier.width(140.dp).clickable { onOpen(l) }) {
                        Art(l.art.ifBlank { null }, Modifier.size(140.dp), MaterialTheme.shapes.medium)
                        Text(l.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(l.sub, maxLines = 1, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun YtmListScreen(ui: PlayerUi, l: YtmList, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    var tracks by remember { mutableStateOf<List<YtVideo>?>(null) }
    LaunchedEffect(l.id) { tracks = Ytm.playlist(l.id) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(l.title, onBack = onBack) }
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(l.art.ifBlank { null }, Modifier.size(120.dp), MaterialTheme.shapes.medium)
                Column(Modifier.padding(start = 14.dp)) {
                    Text("PLAYLIST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(l.title, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text(tracks?.let { "${it.size} músicas · YouTube Music" } ?: "Carregando…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val t = tracks
                    if (!t.isNullOrEmpty()) Button(onClick = { ui.controller?.pause(); YtPlayer.play(t, 0) }, Modifier.padding(top = 8.dp)) { Icon(Icons.Rounded.PlayArrow, null); Text(" Tocar") }
                }
            }
        }
        val t = tracks
        if (t == null) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
        else itemsIndexed(t, key = { i, v -> "$i" + v.id }) { i, v -> RankYtRow(i + 1, v) { ui.controller?.pause(); YtPlayer.play(t, i) } }
    }
}
