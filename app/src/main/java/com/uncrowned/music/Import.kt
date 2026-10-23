package com.uncrowned.music

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/* Playlists online (YouTube, SoundCloud...) com capa — as importadas e as que você monta */
object OPlaylists {
    data class P(val name: String, val cover: String, val from: String, val items: List<YtVideo>)
    var version by mutableIntStateOf(0)
    private fun all(): JSONObject = runCatching { JSONObject(Prefs.sp.getString("oplists", "{}")) }.getOrDefault(JSONObject())
    fun list(): List<P> { val j = all(); return j.keys().asSequence().map { get(it, j)!! }.toList().sortedByDescending { j.getJSONObject(it.name).optLong("at") } }
    fun get(name: String, j: JSONObject = all()): P? = j.optJSONObject(name)?.let { o ->
        val a = o.optJSONArray("i") ?: JSONArray()
        P(name, o.optString("c"), o.optString("f"), List(a.length()) { YtVideo.from(a.getJSONObject(it)) })
    }
    fun save(name0: String, items: List<YtVideo>, cover: String, from: String, replace: Boolean = false): String {
        val j = all(); var name = name0.ifBlank { "Playlist importada" }; var n = 2
        if (!replace) while (j.has(name)) name = "$name0 (${n++})"
        j.put(name, JSONObject().put("c", cover.ifBlank { items.firstOrNull()?.thumb ?: "" }).put("f", from).put("at", System.currentTimeMillis()).put("i", JSONArray(items.map { it.json() })))
        Prefs.sp.edit().putString("oplists", j.toString()).apply(); version++
        return name
    }
    fun add(name: String, v: YtVideo) { val p = get(name) ?: return; if (p.items.none { it.id == v.id && it.src == v.src }) save(name, p.items + v, p.cover, p.from, true) }
    fun remove(name: String, v: YtVideo) { val p = get(name) ?: return; save(name, p.items.filter { !(it.id == v.id && it.src == v.src) }, p.cover, p.from, true) }
    fun delete(name: String) { val j = all(); j.remove(name); Prefs.sp.edit().putString("oplists", j.toString()).apply(); version++ }
}

/* Importar do Spotify e do YouTube Music: link de playlist/álbum/Mix ou o arquivo de dados (Spotify / Google Takeout) */
object Importer {
    data class Item(val title: String, val artist: String, val videoId: String = "")
    data class Pack(val name: String, val cover: String, val from: String, val items: List<Item> = emptyList(), val tracks: List<YtVideo> = emptyList())
    data class Files(val sets: List<Pack>, val liked: List<Item>, val history: Map<String, Long>)
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf(""); private set
    var progress by mutableFloatStateOf(0f); private set

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36")
        c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9"); c.connectTimeout = 10000; c.readTimeout = 15000
        return try { if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}"); c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }
    private fun find(o: Any?, key: String, d: Int = 0): Any? {
        if (d > 16) return null
        when (o) { is JSONObject -> { if (o.has(key)) return o.get(key); for (k in o.keys()) find(o.get(k), key, d + 1)?.let { return it } }
            is JSONArray -> for (i in 0 until o.length()) find(o.get(i), key, d + 1)?.let { return it } }
        return null
    }

    suspend fun link(url0: String): Pack = withContext(Dispatchers.IO) {
        val url = url0.trim()
        Regex("open\\.spotify\\.com/(?:intl-[\\w-]+/)?(playlist|album|track)/([A-Za-z0-9]+)").find(url)?.let { m ->
            val (kind, id) = m.destructured
            val html = get("https://open.spotify.com/embed/$kind/$id")
            val data = Regex("<script id=\"__NEXT_DATA__\"[^>]*>([\\s\\S]*?)</script>").find(html)?.groupValues?.get(1) ?: error("o Spotify não mostrou essa playlist (ela é pública?)")
            val ent = find(JSONObject(data), "entity") as? JSONObject ?: error("playlist vazia")
            val items = if (kind == "track") listOf(Item(ent.optString("name").ifBlank { ent.optString("title") }, (ent.optJSONArray("artists")?.let { a -> (0 until a.length()).joinToString(", ") { a.getJSONObject(it).optString("name") } } ?: ent.optString("subtitle"))))
            else ent.optJSONArray("trackList")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).let { t -> Item(t.optString("title"), t.optString("subtitle")) } } } ?: emptyList()
            val img = (find(ent.optJSONObject("coverArt"), "url") as? String) ?: (find(ent.optJSONObject("visualIdentity"), "url") as? String) ?: (find(ent.optJSONArray("images"), "url") as? String) ?: ""
            return@withContext Pack(ent.optString("name").ifBlank { ent.optString("title").ifBlank { "Playlist do Spotify" } }, img, "Spotify", items.filter { it.title.isNotBlank() })
        }
        val list = Regex("[?&]list=([\\w-]+)").find(url)?.groupValues?.get(1)
        val alb = Regex("browse/(MPRE[\\w-]+)").find(url)?.groupValues?.get(1)
        if (list != null && list.startsWith("RD")) {
            val v = Regex("[?&]v=([\\w-]{11})").find(url)?.groupValues?.get(1) ?: list.replace(Regex("^RD(MM|AMVM|CLAK)?"), "").take(11)
            var tracks = Ytm.mix(v)
            if (tracks.size < 5) runCatching { Ytm.fullPlaylist(list) }.getOrNull()?.let { if (it.tracks.size > tracks.size) tracks = it.tracks }
            val first = tracks.firstOrNull { it.id == v } ?: tracks.firstOrNull()
            return@withContext Pack(first?.let { "Mix de ${it.title}".take(80) } ?: "Mix do YouTube", "https://i.ytimg.com/vi/$v/hqdefault.jpg", "YouTube", tracks = tracks)
        }
        if (list != null || alb != null) {
            val r = Ytm.fullPlaylist(alb ?: list!!)
            return@withContext Pack(r.title.ifBlank { "Playlist do YouTube" }, r.cover.ifBlank { r.tracks.firstOrNull()?.thumb ?: "" }, "YouTube", tracks = r.tracks)
        }
        error("cole um link de playlist do Spotify, YouTube ou YouTube Music")
    }

    private fun csvRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>(); var row = ArrayList<String>(); val cur = StringBuilder(); var q = false; var i = 0
        while (i < text.length) { val ch = text[i]
            if (q) { if (ch == '"') { if (i + 1 < text.length && text[i + 1] == '"') { cur.append('"'); i++ } else q = false } else cur.append(ch) }
            else when (ch) { '"' -> q = true; ',' -> { row.add(cur.toString()); cur.clear() }
                '\n', '\r' -> { if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++; row.add(cur.toString()); cur.clear(); rows.add(row); row = ArrayList() }
                else -> cur.append(ch) }
            i++ }
        if (cur.isNotEmpty() || row.isNotEmpty()) { row.add(cur.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    fun files(ctx: Context, uris: List<Uri>): Files {
        val sets = ArrayList<Pack>(); val liked = ArrayList<Item>(); val hist = HashMap<String, Long>()
        fun handle(name: String, text0: String) {
            val text = text0.removePrefix("﻿"); val base = name.substringAfterLast('/').replace(Regex("(?i)\\.(json|csv)$"), "")
            if (name.endsWith(".json", true)) {
                val j = runCatching { org.json.JSONTokener(text).nextValue() }.getOrNull() ?: return
                if (j is JSONObject) {
                    j.optJSONArray("playlists")?.let { a -> for (i in 0 until a.length()) { val pl = a.getJSONObject(i); val it = pl.optJSONArray("items") ?: JSONArray()
                        val items = (0 until it.length()).mapNotNull { k -> it.getJSONObject(k).optJSONObject("track")?.takeIf { t -> t.optString("trackName").isNotBlank() }?.let { t -> Item(t.optString("trackName"), t.optString("artistName")) } }
                        if (items.isNotEmpty()) sets += Pack(pl.optString("name", "Playlist"), "", "Spotify", items) } }
                    j.optJSONArray("tracks")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).let { t -> if (t.optString("track").isNotBlank()) liked += Item(t.optString("track"), t.optString("artist")) } }
                } else if (j is JSONArray) for (i in 0 until j.length()) j.optJSONObject(i)?.let { e ->
                    val a = e.optString("artistName").ifBlank { e.optString("master_metadata_album_artist_name") }
                    val ms = e.optLong("msPlayed", e.optLong("ms_played")); if (a.isNotBlank() && a != "null") hist[a] = (hist[a] ?: 0) + ms }
                return
            }
            val rows = csvRows(text)
            val hi = rows.indexOfFirst { r -> r.any { Regex("(?i)^(video id|id do v[ií]deo)$").matches(it.trim()) } }; if (hi < 0) return
            val h = rows[hi].map { it.trim().lowercase() }
            val vi = h.indexOfFirst { Regex("video id|id do v").containsMatchIn(it) }; val ti = h.indexOfFirst { Regex("song title|t[ií]tulo da m|^title$|^t[ií]tulo$").containsMatchIn(it) }; val ai = h.indexOfFirst { it.contains("artist") }
            val items = rows.drop(hi + 1).map { r -> Item(if (ti >= 0) r.getOrElse(ti) { "" }.trim() else "", if (ai >= 0) r.getOrElse(ai) { "" }.trim() else "", r.getOrElse(vi) { "" }.trim()) }.filter { Regex("^[\\w-]{11}$").matches(it.videoId) }
            if (items.isEmpty()) return
            val nm = base.replace(Regex("(?i)-videos?$"), "").replace(Regex("(?i)^music library songs$"), "Biblioteca do YouTube Music")
            if (Regex("(?i)liked|gostei|curtid|marquei como gostei").containsMatchIn(nm)) liked += items else sets += Pack(nm, "", "YouTube", items)
        }
        for (u in uris) runCatching {
            val name = u.lastPathSegment ?: ""
            val type = ctx.contentResolver.getType(u) ?: ""
            ctx.contentResolver.openInputStream(u)!!.use { inp ->
                if (type.contains("zip") || name.endsWith(".zip", true)) ZipInputStream(inp).use { z ->
                    var e = z.nextEntry
                    while (e != null) { if (!e.isDirectory && Regex("(?i)\\.(json|csv)$").containsMatchIn(e.name) && !e.name.contains("__MACOSX")) handle(e.name, z.readBytes().toString(Charsets.UTF_8)); e = z.nextEntry }
                } else handle(if (name.contains('.')) name else name + if (type.contains("json")) ".json" else ".csv", inp.readBytes().toString(Charsets.UTF_8))
            }
        }
        return Files(sets, liked, hist)
    }

    // "artista - música" vira música tocável do YouTube Music
    private val cache: MutableMap<String, YtVideo?> = java.util.Collections.synchronizedMap(HashMap())
    private suspend fun resolve(items: List<Item>, onStep: () -> Unit): List<YtVideo> = withContext(Dispatchers.IO) {
        val sem = Semaphore(6)
        items.map { it2 -> async { sem.withPermit {
            val r = if (it2.videoId.isNotBlank()) YtVideo(it2.videoId, it2.title.ifBlank { it2.videoId }, it2.artist, "https://i.ytimg.com/vi/${it2.videoId}/hqdefault.jpg")
            else { val k = (it2.artist + "|" + it2.title).lowercase()
                if (cache.containsKey(k)) cache[k] else runCatching { Ytm.search("${it2.artist} ${it2.title}".trim()).songs.firstOrNull() }.getOrNull().also { cache[k] = it } }
            onStep(); r } } }.awaitAll().filterNotNull()
    }

    fun run(ctx: Context, sets: List<Pack>, liked: List<Item>, history: Map<String, Long>) {
        if (busy) { Toast.makeText(ctx, "Já tem uma importação rodando", Toast.LENGTH_SHORT).show(); return }
        busy = true; progress = 0f
        CoroutineScope(Dispatchers.Main).launch {
            var lists = 0; var songs = 0
            runCatching {
                for (s in sets) {
                    status = "Importando \"${s.name}\"…"; progress = 0f
                    var done = 0; val total = s.items.size.coerceAtLeast(1)
                    val tracks = s.tracks.ifEmpty { resolve(s.items) { done++; progress = done.toFloat() / total } }
                    if (tracks.isEmpty()) continue
                    OPlaylists.save(s.name, tracks, s.cover, s.from); lists++; songs += tracks.size
                    tracks.take(200).forEach { Taste.learn(it.channel, 0.4) }
                }
                if (liked.isNotEmpty()) {
                    status = "Importando suas curtidas…"; var done = 0
                    val tr = resolve(liked) { done++; progress = done.toFloat() / liked.size }
                    tr.forEach { if (!YtStore.isFav(it.id)) YtStore.toggleFav(it); Taste.learn(it.channel, 1.0) }; songs += tr.size
                }
                history.forEach { (a, ms) -> Taste.learn(a, minOf(15.0, ms / 3.6e6 * 0.6)) }
                status = "Pronto: $lists ${if (lists == 1) "playlist" else "playlists"} e $songs músicas importadas. Elas estão em Biblioteca → Playlists."
            }.onFailure { status = "A importação parou: ${it.message}" }
            busy = false
        }
    }
}

@Composable
fun ImportScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<Importer.Files?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        working = true
        scope.launch {
            val f = withContext(Dispatchers.IO) { Importer.files(ctx, uris) }
            working = false
            if (f.sets.isEmpty() && f.liked.isEmpty() && f.history.isEmpty()) Toast.makeText(ctx, "Não achei playlists nesse arquivo", Toast.LENGTH_LONG).show() else found = f
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Importar", onBack = onBack) }
        item { Text("Traga suas playlists, curtidas e seu gosto do Spotify e do YouTube Music. Tudo vira playlist aqui, com a capa, e o app aprende o que você curte.",
            Modifier.padding(horizontal = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (Importer.busy || Importer.status.isNotBlank()) item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) { Column(Modifier.padding(16.dp)) {
                Text(Importer.status, fontWeight = FontWeight.SemiBold)
                if (Importer.busy) LinearProgressIndicator({ Importer.progress }, Modifier.fillMaxWidth().padding(top = 10.dp))
            } }
        }
        item { Section("Por link") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), placeholder = { Text("Cole o link da playlist, álbum ou Mix") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                Text("Funciona com Spotify (playlist pública, álbum ou música), YouTube, YouTube Music e Mix do YouTube.", Modifier.padding(top = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    working = true
                    scope.launch {
                        val r = runCatching { Importer.link(url) }
                        working = false
                        r.onSuccess { s -> Importer.run(ctx, listOf(s), emptyList(), emptyMap()); url = "" }.onFailure { Toast.makeText(ctx, it.message ?: "Não deu pra ler esse link", Toast.LENGTH_LONG).show() }
                    }
                }, Modifier.padding(top = 10.dp), enabled = url.isNotBlank() && !working && !Importer.busy) { Icon(Icons.Rounded.Link, null); Text(" Importar link") }
            }
        }
        item { Section("Pelo arquivo de dados") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Spotify: Conta → Privacidade → Baixar seus dados (chega um .zip por e-mail).\nYouTube Music: takeout.google.com → YouTube e YouTube Music → playlists.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { pick.launch(arrayOf("application/zip", "application/json", "text/csv", "text/comma-separated-values", "application/octet-stream", "*/*")) }, Modifier.padding(top = 10.dp), enabled = !working && !Importer.busy) {
                    Icon(Icons.Rounded.FolderOpen, null); Text(" Escolher arquivo (.zip, .json, .csv)") }
            }
        }
        if (working) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
    }
    found?.let { f ->
        val sel = remember(f) { mutableStateListOf<Int>().apply { addAll(f.sets.indices) } }
        var likes by remember(f) { mutableStateOf(f.liked.isNotEmpty()) }
        AlertDialog(onDismissRequest = { found = null }, title = { Text("O que importar?") },
            text = { LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (f.liked.isNotEmpty()) item { Row(Modifier.clickable { likes = !likes }, verticalAlignment = Alignment.CenterVertically) { Checkbox(likes, { likes = it }); Text("Curtidas (${f.liked.size})") } }
                itemsIndexed(f.sets) { i, s -> Row(Modifier.clickable { if (i in sel) sel.remove(i) else sel.add(i) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(i in sel, { if (it) sel.add(i) else sel.remove(i) }); Text("${s.name} (${s.items.size})", maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                if (f.history.isNotEmpty()) item { Text("+ seu histórico de ${f.history.size} artistas pra aprender seu gosto", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } },
            confirmButton = { TextButton(onClick = { Importer.run(ctx, sel.sorted().map { f.sets[it] }, if (likes) f.liked else emptyList(), f.history); found = null }) { Text("Importar") } },
            dismissButton = { TextButton(onClick = { found = null }) { Text("Cancelar") } })
    }
}

/* tela de uma playlist online */
@Composable
fun OnlinePlaylistScreen(ui: PlayerUi, name: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    OPlaylists.version
    val p = OPlaylists.get(name)
    if (p == null) { LaunchedEffect(name) { onBack() }; return }
    var ask by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(p.name, onBack = onBack) { IconButton(onClick = { ask = true }) { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.primary) } } }
        item {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(p.cover.ifBlank { null }, Modifier.size(120.dp), RoundedCornerShape(14.dp))
                Column(Modifier.padding(start = 16.dp)) {
                    Text("${p.items.size} músicas${if (p.from.isNotBlank()) " • de ${p.from}" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { ui.controller?.pause(); YtPlayer.play(p.items, 0) }, enabled = p.items.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, null) }
                        OutlinedButton(onClick = { ui.controller?.pause(); YtPlayer.play(p.items.shuffled(), 0) }, enabled = p.items.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, null) }
                        OutlinedButton(onClick = { p.items.forEach { if (Sync.syncedUri(it) == null) Sync.addPending(it) }; Sync.runNow(ctx)
                            Toast.makeText(ctx, "Sincronizando ${p.items.size} músicas em segundo plano", Toast.LENGTH_LONG).show() }, enabled = p.items.isNotEmpty()) { Icon(Icons.Rounded.Download, null) }
                    }
                }
            }
        }
        if (p.items.isEmpty()) item { Empty("Playlist vazia. No player do YouTube, toque em + pra adicionar músicas aqui.") }
        itemsIndexed(p.items, key = { i, v -> "$i${v.src}${v.id}" }) { i, v ->
            Row(Modifier.fillMaxWidth().clickable { ui.controller?.pause(); YtPlayer.play(p.items, i) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(v.thumb.ifBlank { null }, Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(v.channel, maxLines = 1, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (Sync.syncedUri(v) != null) Icon(Icons.Rounded.DownloadDone, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { OPlaylists.remove(p.name, v) }) { Icon(Icons.Rounded.RemoveCircleOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (ask) AlertDialog(onDismissRequest = { ask = false }, title = { Text("Apagar \"${p.name}\"?") },
        confirmButton = { TextButton(onClick = { OPlaylists.delete(p.name); ask = false; onBack() }) { Text("Apagar") } }, dismissButton = { TextButton(onClick = { ask = false }) { Text("Cancelar") } })
}
