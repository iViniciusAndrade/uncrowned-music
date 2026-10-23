package com.uncrowned.music

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

/* buscas recentes (igual Spotify) */
object Recent {
    var version by mutableIntStateOf(0)
    fun list(): List<String> = runCatching { JSONArray(Prefs.sp.getString("recentq", "[]")).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList())
    fun add(q: String) { if (q.length < 2) return; Prefs.sp.edit().putString("recentq", JSONArray((listOf(q) + list().filter { !it.equals(q, true) }).take(12)).toString()).apply(); version++ }
    fun clear() { Prefs.sp.edit().remove("recentq").apply(); version++ }
}

private val BROWSE = listOf("Funk" to 0f, "Trap" to 280f, "Sertanejo" to 35f, "Pagode" to 150f, "Rap nacional" to 0f, "Phonk" to 260f, "Pop" to 320f, "Rock" to 10f,
    "Eletrônica" to 190f, "MPB" to 90f, "Gospel" to 50f, "Lo-fi" to 230f, "Anime" to 300f, "Metal" to 0f, "Reggae" to 120f, "Forró" to 25f)

/* tela da busca vazia: recentes, ouvidas recentemente e "navegar por gênero" */
@Composable
fun SearchHome(onSearch: (String) -> Unit) {
    Recent.version
    val rec = Recent.list()
    val hist = YtStore.history().take(12)
    Column {
        if (rec.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) { Section("Buscas recentes"); Spacer(Modifier.weight(1f)); TextButton(onClick = { Recent.clear() }, Modifier.padding(top = 14.dp, end = 8.dp)) { Text("Limpar") } }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rec.forEach { q -> AssistChip({ onSearch(q) }, { Text(q) }, leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(16.dp)) }) }
            }
        }
        if (hist.isNotEmpty()) {
            Section("Ouvidas recentemente")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(hist, key = { i, v -> "h$i" + v.id }) { i, v ->
                    Column(Modifier.width(120.dp).clickable { YtPlayer.play(hist, i) }) {
                        Art(v.thumb, Modifier.size(120.dp), RoundedCornerShape(10.dp))
                        Text(v.title, Modifier.padding(top = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(v.channel, maxLines = 1, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Section("Navegar por gênero")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BROWSE.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (g, hue) ->
                        Box(Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(Color.hsv(hue, .7f, .75f), Color.hsv((hue + 40) % 360, .8f, .35f))))
                            .clickable { onSearch(g) }.padding(12.dp)) {
                            Text(g, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, fontFamily = Skins.font)
                        }
                    }
                }
            }
        }
    }
}

/* Biblioteca: tudo que fica no celular e suas coleções, igual à barra lateral do PC */
@Composable
fun LibraryTab(ui: PlayerUi, onPlaylist: (String) -> Unit) {
    Prefs.version.intValue; Sync.version; OPlaylists.version
    val pend = Sync.pending().size; val kept = Sync.kept().size
    val oplists = OPlaylists.list()
    val local = Prefs.playlists()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Biblioteca") { IconButton(onClick = { Nav.page = "themes" }) { Icon(Icons.Rounded.Palette, "Temas", tint = MaterialTheme.colorScheme.primary) } } }
        item { Section("Offline") }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(Icons.Rounded.MusicNote, "Músicas", "${Library.songs.size}", Modifier.weight(1f)) { Nav.page = "songs" }
                    Tile(Icons.Rounded.Album, "Álbuns", "${Library.songs.map { it.albumId }.distinct().size}", Modifier.weight(1f)) { Nav.page = "albums" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(Icons.Rounded.Person, "Artistas", "${Library.songs.map { it.artist }.distinct().size}", Modifier.weight(1f)) { Nav.page = "artists" }
                    Tile(Icons.Rounded.GraphicEq, "Slowed / Nightcore", "Exportar", Modifier.weight(1f)) { Nav.page = "export" }
                }
            }
        }
        // últimas que entraram no celular
        val recent = Library.songs.sortedByDescending { it.dateAdded }.take(12)
        if (recent.isNotEmpty()) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Section("Adicionadas recentemente"); Spacer(Modifier.weight(1f)); TextButton(onClick = { Prefs.sp.edit().putString("libsort", "recent").apply(); Nav.page = "songs" }, Modifier.padding(top = 14.dp, end = 8.dp)) { Text("Ver todas") } } }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(recent, key = { _, x -> "rc" + x.id }) { i, x ->
                        Column(Modifier.width(112.dp).clickable { ui.play(recent, i) }) {
                            Art(x.art, Modifier.size(112.dp), RoundedCornerShape(10.dp))
                            Text(x.title, Modifier.padding(top = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(x.artist, maxLines = 1, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Section("Pra você") }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(Icons.Rounded.CalendarMonth, "Momentos", "Música do momento", Modifier.weight(1f)) { Nav.page = "moments" }
                    Tile(Icons.Rounded.BarChart, "Mais ouvidas", "e seu card", Modifier.weight(1f)) { Nav.page = "stats" }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(Icons.Rounded.AddCircleOutline, "Importar", "Spotify e YouTube", Modifier.weight(1f)) { Nav.page = "import" }
                    Tile(Icons.Rounded.Palette, "Temas", Skins.current?.name ?: "Escolher", Modifier.weight(1f)) { Nav.page = "themes" }
                }
            }
        }
        item { Section("Playlists") }
        item { LRow(Icons.Rounded.Favorite, "Favoritas", "${Prefs.favs().size} offline") { onPlaylist(FAVS) } }
        item { LRow(Icons.Rounded.CloudQueue, "Favoritos online", "${YtStore.favs().size} faixas") { onPlaylist(ONLINE_FAVS) } }
        item { LRow(Icons.Rounded.History, "Ouvidas recentemente", "Histórico") { onPlaylist(ONLINE_HIST) } }
        Cuts.version
        val nc = Cuts.list().size
        if (nc > 0) item { LRow(Icons.Rounded.ContentCut, "Recortes", "$nc ${if (nc == 1) "trecho" else "trechos"} marcados") { Nav.page = "cuts" } }
        if (kept > 0 && Secret.on) item { LRow(Icons.Rounded.OfflinePin, "Guardadas no app", "$kept • tocam sem internet") { Nav.page = "kept" } }
        if (pend > 0 && Secret.on) item { LRow(Icons.Rounded.CloudSync, "Pra sincronizar", "$pend pendentes • baixam sozinhas no Wi-Fi") { Nav.page = "pend" } }
        items(oplists, key = { "op" + it.name }) { p ->
            ListItem(headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text("${p.items.size} músicas${if (p.from.isNotBlank()) " • ${p.from}" else ""}") },
                leadingContent = { Art(p.cover.ifBlank { null }, Modifier.size(52.dp)) },
                trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.clickable { Nav.page = "op:" + p.name }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        items(local.keys.toList(), key = { "lp$it" }) { p ->
            val first = local[p].orEmpty().firstNotNullOfOrNull { Library.byId(it) }
            ListItem(headlineContent = { Text(p) }, supportingContent = { Text("${local[p].orEmpty().size} músicas offline") },
                leadingContent = { Art(first?.art, Modifier.size(52.dp)) },
                trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.clickable { onPlaylist(p) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        }
        item { TextButton(onClick = { Nav.page = "playlists" }, Modifier.padding(start = 8.dp)) { Icon(Icons.Rounded.Add, null); Text(" Nova playlist offline") } }
    }
}

@Composable
private fun Tile(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    val acc = MaterialTheme.colorScheme.primary
    Row(modifier.height(74.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(acc.copy(alpha = .18f)), Alignment.Center) { Icon(icon, null, tint = acc) }
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontFamily = Skins.font, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            Text(sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title, fontSize = 17.sp) }, supportingContent = { Text(sub) },
        leadingContent = { Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary), Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary) } },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(onClick = onClick), colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

/* todas as músicas offline, com ordem e filtro */
@Composable
fun AllSongsScreen(ui: PlayerUi, onBack: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    // ordem lembrada; padrão: as que entraram por último primeiro
    var sort by remember { mutableStateOf(Prefs.sp.getString("libsort", "recent") ?: "recent") }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    Prefs.version.intValue
    val favs = Prefs.favs()
    val l = Library.songs.filter { (q.isBlank() || it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)) && (!favOnly || it.id.toString() in favs) }
        .let { x ->
            when (sort) {
                "recent" -> x.sortedByDescending { it.dateAdded }
                "old" -> x.sortedBy { it.dateAdded }
                "title" -> x.sortedBy { it.title.lowercase() }
                "artist" -> x.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() }))
                "album" -> x.sortedWith(compareBy({ it.album.lowercase() }, { it.title.lowercase() }))
                "duration" -> x.sortedByDescending { it.duration }
                "short" -> x.sortedBy { it.duration }
                "plays" -> { val m = remember(Listen.version) { Listen.stats("all").songs.associate { it.first.key to it.second } }; x.sortedByDescending { m["loc:${it.id}"] ?: 0L } }
                "year" -> x.sortedByDescending { it.year }
                else -> x
            }
        }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Músicas", onBack = onBack) }
        item { Text("${l.size} músicas offline", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SearchField(q) { q = it } }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(favOnly, { favOnly = !favOnly }, { Text("Favoritas") }, leadingIcon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp)) })
                listOf("recent" to "Mais recentes", "old" to "Mais antigas", "plays" to "Mais ouvidas", "title" to "A-Z", "artist" to "Artista", "album" to "Álbum",
                    "year" to "Ano", "duration" to "Mais longas", "short" to "Mais curtas").forEach { (k, n) ->
                    FilterChip(sort == k, { sort = k; Prefs.sp.edit().putString("libsort", k).apply() }, { Text(n) }) }
            }
        }
        if (l.isNotEmpty()) item { PlayShuffleRow(ui, l) }
        if (l.isEmpty()) item { Empty("Nenhuma música offline ainda. Use Buscar pra ouvir online e Sincronizar pra salvar.") }
        itemsIndexed(l, key = { _, s -> s.id }) { i, s -> SongRow(ui, s) { ui.play(l, i) } }
    }
}

/* cartão "Criado por" nos Ajustes */
@Composable
fun AboutCard() {
    val ctx = LocalContext.current
    Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(Skins.current?.icon ?: R.drawable.skin_edgy), null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("Uncrowned Music", fontWeight = FontWeight.Black, fontFamily = Skins.font, fontSize = 18.sp)
                Text("Criado por Vinicius Andrade", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/devviniciusandrade/"))) }) { Text("LinkedIn") }
        }
    }
}
