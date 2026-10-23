@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.uncrowned.music

import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

private val Dim = Color.White.copy(alpha = 0.6f)

@Composable
fun Art(model: Any?, modifier: Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_note), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxSize(0.4f))
        // modo Extremo: não baixa nem guarda imagem nenhuma
        if (!Eco.extreme) AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/* ======================= APP ======================= */
const val FAVS = "\u0000favs"
const val ONLINE_FAVS = "\u0000ofavs"
const val ONLINE_HIST = "\u0000ohist"

@Composable
fun App(ui: PlayerUi, perms: List<String>) {
    val ctx = LocalContext.current
    Prefs.version.intValue
    val audioPerm = perms.first()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = it[audioPerm] == true || granted }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms.toTypedArray()) }
    LaunchedEffect(granted, Prefs.hideVoice, Prefs.minDurationSec) { if (granted) Library.load(ctx) }

    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Sync.playLocal = { u, v -> ui.playUri(u, v) }; YtPlayer.stopOther = { ui.stopLocal() }
        ScPlay.exoSet = { ui.setItems(it) }; ScPlay.exoAdd = { ui.controller?.addMediaItem(it) }; ScPlay.webPlay = { YtPlayer.playWeb(it) } }
    var tab by rememberSaveable { mutableIntStateOf(Prefs.startTab.coerceIn(0, 3)) }
    LaunchedEffect(Unit) { Listen.init(ctx); Eco.init(ctx); Hunter.start(ctx) }
    // conta o tempo ouvido do player do YouTube também quando o app está aberto
    val hp = Hunter.pendingPlay
    LaunchedEffect(hp) { if (hp != null) { RadioRecorder.lastStation = hp; ui.playOnline(listOf(hp), 0); tab = 2; Hunter.pendingPlay = null } }
    var expanded by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var ytExpanded by remember { mutableStateOf(false) }
    /* a fila passa de um player pro outro (música do app <-> player do YouTube/SoundCloud):
       se a tela do player estava aberta, abre a do player que está tocando agora (nada de tela vazia) */
    val webCur = YtPlayer.current
    LaunchedEffect(webCur?.id, ui.queue.isEmpty()) {
        if (!(expanded || ytExpanded)) return@LaunchedEffect
        if (webCur != null && ui.queue.isEmpty()) { expanded = false; ytExpanded = true }
        else if (webCur == null && ui.queue.isNotEmpty()) { ytExpanded = false; expanded = true }
        else if (webCur == null && ui.queue.isEmpty()) { delay(2500); ytExpanded = false; if (ScPlay.loading == null) expanded = false }
    }
    // o app fechou sozinho da última vez: mostra o motivo pra copiar
    var crash by remember { mutableStateOf(Crash.take(ctx)) }
    crash?.let { txt -> AlertDialog(onDismissRequest = { crash = null }, title = { Text("O app fechou sozinho da última vez") },
        text = { Text("Copia o erro e me manda que eu corrijo.", fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = {
            ctx.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("erro", txt))
            android.widget.Toast.makeText(ctx, "Erro copiado", android.widget.Toast.LENGTH_SHORT).show(); crash = null }) { Text("Copiar erro") } },
        dismissButton = { TextButton(onClick = { crash = null }) { Text("Fechar") } }) }
    var detail by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var playlistDetail by remember { mutableStateOf<String?>(null) }

    BackHandler(ytExpanded || expanded || showEq || detail != null || playlistDetail != null || Nav.album != null || Nav.artist != null || Nav.page != null || Nav.sim != null || tab != Prefs.startTab.coerceIn(0, 3)) {
        when { ytExpanded -> ytExpanded = false; showEq -> showEq = false; expanded -> expanded = false; Nav.sim != null -> Nav.sim = null; Nav.page != null -> Nav.page = Nav.back(); Nav.album != null -> Nav.album = null; Nav.artist != null -> Nav.artist = null; detail != null -> detail = null; playlistDetail != null -> playlistDetail = null; else -> tab = Prefs.startTab.coerceIn(0, 3) }
    }

    val tabs = listOf(
        Icons.Rounded.Explore to "Descobrir", Icons.Rounded.Search to "Buscar", Icons.Rounded.Radio to "Rádio",
        Icons.Rounded.LibraryMusic to "Biblioteca", Icons.Rounded.Settings to "Ajustes"
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SkinBackground()
        Scaffold(
            containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground,
            bottomBar = {
                Column(Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))) {
                    if (!ytExpanded) YtPlayerBar(false) { ytExpanded = it; if (it) ui.controller?.pause() }
                    // sem permissão de notificação o Android não mostra a música tocando (barra, tela de bloqueio, central de controle)
                    var notifOk by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
                    LaunchedEffect(notifOk) { while (!notifOk) { delay(1500); notifOk = androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled() } }
                    if (!notifOk) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .18f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsOff, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Notificações desligadas: a música não aparece na barra nem na tela de bloqueio.", Modifier.weight(1f).padding(horizontal = 10.dp), fontSize = 12.sp, lineHeight = 15.sp)
                        TextButton(onClick = { runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }) { Text("Ativar") }
                    }
                    ScPlay.loading?.let { t -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Text("  Carregando \"$t\"…", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    if (ui.mediaId != null) MiniPlayer(ui) { expanded = true }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp, modifier = Modifier.height(64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
                        tabs.forEachIndexed { i, (ic, label) ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i; detail = null; playlistDetail = null; Nav.clear() },
                                icon = { Icon(ic, null, Modifier.size(24.dp)) }, label = { Text(label, fontSize = 10.sp, maxLines = 1, fontFamily = Skins.font) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color.Transparent,
                                    selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (!granted && (Nav.page in listOf("songs", "albums", "artists", "export"))) {
                    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("O Uncrowned precisa de acesso às suas músicas", textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { launcher.launch(perms.toTypedArray()) }, shape = RoundedCornerShape(12.dp)) { Text("Permitir") }
                    }
                } else {
                    val d = detail
                    val pd = playlistDetail
                    val na = Nav.album; val nr = Nav.artist; val ns = Nav.sim; val np = Nav.page
                    val back = { Nav.page = Nav.back() }
                    when {
                        ns != null -> SimilarScreen(ui, ns) { Nav.sim = null }
                        np == "pend" -> PendingScreen(ui) { Nav.page = null }
                        np == "kept" -> KeptScreen(ui) { Nav.page = null }
                        np == "ia" -> AssistScreen(ui) { Nav.page = Nav.back() }
                        np == "cuts" -> CutsScreen(ui) { Nav.page = Nav.back() }
                        d != null -> SongList(ui, d.second, title = d.first, onBack = { detail = null })
                        np == "songs" -> AllSongsScreen(ui, back)
                        np == "albums" -> Column { Header("Álbuns", onBack = back); Box(Modifier.weight(1f)) { AlbumsTab { detail = it } } }
                        np == "artists" -> Column { Header("Artistas", onBack = back); Box(Modifier.weight(1f)) { ArtistsTab { detail = it } } }
                        np == "playlists" -> PlaylistsTab(ui) { playlistDetail = it }
                        np == "themes" -> ThemesScreen(back)
                        np == "stats" -> StatsScreen(ui, back)
                        np == "moments" -> MomentsScreen(ui, back)
                        np == "import" -> ImportScreen(back)
                        np == "export" -> ExportScreen(ui, back)
                        np != null && np.startsWith("op:") -> OnlinePlaylistScreen(ui, np.removePrefix("op:"), back)
                        na != null -> AlbumScreen(ui, na) { Nav.album = null }
                        nr != null -> ArtistScreen(ui, nr) { Nav.artist = null }
                        pd == ONLINE_FAVS -> OnlineList(ui, "Favoritos online", YtStore.favs()) { playlistDetail = null }
                        pd == ONLINE_HIST -> RecentGrid(ui, YtStore.history()) { playlistDetail = null }
                        pd != null -> PlaylistDetail(ui, pd) { playlistDetail = null }
                        tab == 0 -> DiscoverTab(ui)
                        tab == 1 -> SongsTab(ui)
                        tab == 2 -> RadioScreen(ui, null)
                        tab == 3 -> LibraryTab(ui) { playlistDetail = it }
                        else -> SettingsScreen(ui, onEq = { showEq = true }, onRescan = { if (granted) scope.launch { Library.load(ctx) } })
                    }
                }
            }
        }

        AnimatedVisibility(expanded, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            FullPlayer(ui, onClose = { expanded = false }, onEq = { showEq = true })
        }
        if (ytExpanded) YtPlayerBar(true) { ytExpanded = it }
        // carregando a próxima: aparece também com o player aberto (pra não ficar apertando sem saber)
        val ld = ScPlay.loading
        if ((expanded || ytExpanded) && ld != null) Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
            Row(Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("  Carregando \"$ld\"…", fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        AnimatedVisibility(showEq, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            EqualizerScreen { showEq = false }
        }
    }
}

/* ======================= COMPONENTES ESTILO APPLE ======================= */
@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Rounded.ArrowBackIosNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Voltar", fontSize = 17.sp)
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        Text(title, Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp), fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Skins.font,
            letterSpacing = (-0.5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Section(title: String) {
    Text(title.uppercase(), Modifier.padding(start = 20.dp, top = 22.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Skins.font,
        fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
}

@Composable
fun Group(title: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) Section(title) else Spacer(Modifier.height(16.dp))
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(content = content)
    }
    if (footer != null) Text(footer, Modifier.padding(start = 32.dp, end = 32.dp, top = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val voice = rememberVoice { onChange(it) }
    TextField(
        value, onChange, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 48.dp),
        placeholder = { Text("Buscar") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        // vazio: microfone pra buscar falando; com texto: limpar
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Cancel, null, Modifier.size(20.dp)) } else IconButton(onClick = voice) { Icon(Icons.Rounded.Mic, "Buscar falando") } },
        singleLine = true, shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })
    )
}

/* ======================= MÚSICAS + BUSCA ONLINE (tudo junto) ======================= */
@Composable
fun SongsTab(ui: PlayerUi) {
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("title") }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var online by remember { mutableStateOf<List<YtVideo>>(emptyList()) }
    var dl by remember { mutableStateOf<List<Track>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var ytm by remember { mutableStateOf<Ytm.Search?>(null) }
    Prefs.version.intValue
    val q = query.trim()
    val scope = rememberCoroutineScope()

    LaunchedEffect(q) {
        online = emptyList(); dl = emptyList(); loading = false; ytm = null
        if (q.length < 2) return@LaunchedEffect
        delay(300)
        Recent.add(q)
        scope.launch { val r = Ytm.search(q); if (query.trim() == q) ytm = r }
        loading = true
        val r = OnlineSearch.search(q)
        online = r.stream; dl = r.downloadable; loading = false
    }

    val favs = Prefs.favs()
    val local = Library.songs.filter {
        (q.isEmpty() || it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)) &&
            (!favOnly || it.id.toString() in favs)
    }.let { l ->
        when (sort) { "artist" -> l.sortedBy { it.artist.lowercase() }; "recent" -> l.sortedByDescending { it.dateAdded }; "duration" -> l.sortedByDescending { it.duration }; else -> l }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Buscar") }
        item { SearchField(query) { query = it } }
        if (q.isEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f)).clickable { Nav.page = "ia" }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Assistente", fontWeight = FontWeight.SemiBold)
                        Text("Pede por voz ou texto: \"toca Matuê em slowed\", \"desliga em 30 min\"", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.Mic, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            item { SearchHome { query = it } }
        } else if (false) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(favOnly, { favOnly = !favOnly }, { Text("Favoritas") }, leadingIcon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp)) })
                    listOf("title" to "A-Z", "artist" to "Artista", "recent" to "Recentes", "duration" to "Duração").forEach { (k, l) ->
                        FilterChip(sort == k, { sort = k }, { Text(l) })
                    }
                }
            }
            if (local.isNotEmpty()) item { PlayShuffleRow(ui, local) }
            if (local.isEmpty()) item { Empty("Nenhuma música offline ainda. Use a busca pra ouvir online.") }
            itemsIndexed(local, key = { _, s -> s.id }) { i, s -> SongRow(ui, s) { ui.play(local, i) } }
        } else {
            val y = ytm
            if (y != null && y.artists.isNotEmpty()) {
                item { Section("Artistas") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(y.artists, key = { it.id }) { a ->
                            Column(Modifier.width(104.dp).clickable { Nav.artist = a }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Art(a.art.ifBlank { null }, Modifier.size(104.dp), CircleShape)
                                Text(a.name, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Artista", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (y != null && y.albums.isNotEmpty()) {
                item { Section("Álbuns") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(y.albums, key = { it.id }) { a ->
                            Column(Modifier.width(130.dp).clickable { Nav.album = a }) {
                                Art(a.art.ifBlank { null }, Modifier.size(130.dp), RoundedCornerShape(10.dp))
                                Text(a.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(listOf(a.year, a.artist.ifBlank { a.type }).filter { it.isNotBlank() }.joinToString(" • "), maxLines = 1, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (local.isNotEmpty()) {
                item { Section("Offline") }
                itemsIndexed(local.take(30), key = { _, s -> "l" + s.id }) { i, s -> SongRow(ui, s) { ui.play(local, i) } }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Section("Online")
                    if (loading) CircularProgressIndicator(Modifier.padding(start = 10.dp, top = 16.dp).size(14.dp), strokeWidth = 2.dp)
                }
            }
            itemsIndexed(online, key = { i, v -> "o$i${v.src}${v.id}" }) { i, v -> YtRow(v) { ui.controller?.pause(); YtPlayer.play(online, i) } }
            if (!loading && online.isEmpty() && q.length >= 2) item { Empty("Nada online pra \"$q\".") }
            if (dl.isNotEmpty()) {
                item { Section("Pra baixar") }
                itemsIndexed(dl, key = { _, t -> "d" + t.id }) { i, t -> TrackRow(ui, t) { ui.playOnline(dl, i) } }
            }
        }
    }
}

@Composable
fun Empty(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun PlayShuffleRow(ui: PlayerUi, songs: List<Song>) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val c = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.primary)
        FilledTonalButton(onClick = { ui.play(songs, 0) }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = c) {
            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Tocar", fontWeight = FontWeight.SemiBold)
        }
        FilledTonalButton(onClick = { ui.play(songs.shuffled(), 0) }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = c) {
            Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Aleatório", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SongList(ui: PlayerUi, songs: List<Song>, title: String, onBack: () -> Unit) {
    Column { Header(title, onBack); SongListBody(ui, songs) }
}

@Composable
fun SongListBody(ui: PlayerUi, songs: List<Song>, playlist: String? = null) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (songs.isNotEmpty()) item { PlayShuffleRow(ui, songs) }
        if (songs.isEmpty()) item { Empty("Nada por aqui ainda.") }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s -> SongRow(ui, s, playlist) { ui.play(songs, i) } }
    }
}

@Composable
fun SongRow(ui: PlayerUi, s: Song, playlist: String? = null, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf(false) }
    val current = ui.mediaId == s.id.toString()
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { menu = true })
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (Prefs.showArtInList) { Art(s.art, Modifier.size(48.dp), RoundedCornerShape(6.dp)); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (current && ui.playing) Icon(Icons.Rounded.GraphicEq, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreHoriz, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(14.dp)) {
                DropdownMenuItem({ Text("Tocar a seguir") }, { ui.playNext(s); menu = false }, leadingIcon = { Icon(Icons.Rounded.SkipNext, null) })
                DropdownMenuItem({ Text("Adicionar à fila") }, { ui.enqueue(s); menu = false }, leadingIcon = { Icon(Icons.Rounded.AddToQueue, null) })
                DropdownMenuItem({ Text("Adicionar à playlist") }, { pick = true; menu = false }, leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) })
                DropdownMenuItem({ Text(if (Prefs.isFav(s.id.toString())) "Remover dos favoritos" else "Favoritar") },
                    { Prefs.toggleFav(s.id.toString()); menu = false }, leadingIcon = { Icon(Icons.Rounded.Favorite, null) })
                if (playlist != null) DropdownMenuItem({ Text("Remover da playlist") },
                    { Prefs.removeFromPlaylist(playlist, s.id.toString()); menu = false }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
            }
        }
    }
    if (pick) PlaylistPicker(onDismiss = { pick = false }) { Prefs.addToPlaylist(it, s.id.toString()); pick = false }
}

@Composable
fun PlaylistPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(20.dp), confirmButton = {
        TextButton(onClick = { if (newName.isNotBlank()) { Prefs.createPlaylist(newName.trim()); onPick(newName.trim()) } }) { Text("Criar e adicionar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }, title = { Text("Adicionar à playlist") }, text = {
        Column {
            Prefs.playlists().keys.forEach { name ->
                Text(name, Modifier.fillMaxWidth().clickable { onPick(name) }.padding(vertical = 12.dp), fontSize = 16.sp)
            }
            OutlinedTextField(newName, { newName = it }, label = { Text("Nova playlist") }, singleLine = true, shape = RoundedCornerShape(12.dp))
        }
    })
}

@Composable
fun AlbumsTab(onOpen: (Pair<String, List<Song>>) -> Unit) {
    val albums = Library.songs.groupBy { it.albumId }.values.sortedBy { it.first().album.lowercase() }
    Column {
        Header("Álbuns")
        LazyVerticalGrid(GridCells.Fixed(Prefs.gridColumns), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(albums, key = { it.first().albumId }) { a ->
                Column(Modifier.clickable { onOpen(a.first().album to a) }) {
                    Art(a.first().art, Modifier.fillMaxWidth().aspectRatio(1f).shadow(6.dp, RoundedCornerShape(10.dp)), RoundedCornerShape(10.dp))
                    Text(a.first().album, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(a.first().artist, maxLines = 1, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ArtistsTab(onOpen: (Pair<String, List<Song>>) -> Unit) {
    val artists = Library.songs.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    Column {
        Header("Artistas")
        LazyColumn {
            items(artists.entries.toList(), key = { it.key }) { (name, songs) ->
                ListItem(
                    headlineContent = { Text(name, maxLines = 1, fontSize = 17.sp) },
                    supportingContent = { Text("${songs.size} músicas") },
                    leadingContent = { Art(songs.first().art, Modifier.size(52.dp), CircleShape) },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clickable { onOpen(name to songs) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
fun PlaylistsTab(ui: PlayerUi, onOpen: (String) -> Unit) {
    Prefs.version.intValue
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val pls = Prefs.playlists()
    @Composable
    fun PRow(title: String, sub: String, icon: ImageVector?, art: Any?, key: String) {
        ListItem(
            headlineContent = { Text(title, fontSize = 17.sp) }, supportingContent = { Text(sub) },
            leadingContent = {
                if (icon != null) Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary), Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary)
                } else Art(art, Modifier.size(52.dp))
            },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.clickable { onOpen(key) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
    Column {
        Header("Playlists") { IconButton(onClick = { create = true }) { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) } }
        LazyColumn {
            item { PRow("Favoritas", "${Prefs.favs().size} músicas", Icons.Rounded.Favorite, null, FAVS) }
            item { PRow("Favoritos online", "${YtStore.favs().size} faixas", Icons.Rounded.CloudQueue, null, ONLINE_FAVS) }
            item { PRow("Ouvidas recentemente", "Histórico", Icons.Rounded.History, null, ONLINE_HIST) }
            Sync.version
            val pend = Sync.pending().size; val kept = Sync.kept().size
            Secret.init()
            if (kept > 0 && Secret.on) item { ListItem(headlineContent = { Text("Guardadas no app", fontSize = 17.sp) }, supportingContent = { Text("$kept • tocam sem internet") },
                leadingContent = { Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary), Alignment.Center) { Icon(Icons.Rounded.OfflinePin, null, tint = MaterialTheme.colorScheme.onPrimary) } },
                trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.clickable { Nav.page = "kept" }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
            if (pend > 0 && Secret.on) item { ListItem(headlineContent = { Text("Pra sincronizar", fontSize = 17.sp) }, supportingContent = { Text("$pend pendentes • baixam sozinhas no Wi-Fi") },
                leadingContent = { Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary), Alignment.Center) { Icon(Icons.Rounded.CloudSync, null, tint = MaterialTheme.colorScheme.onPrimary) } },
                trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.clickable { Nav.page = "pend" }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }
            items(pls.keys.toList()) { p ->
                val first = pls[p].orEmpty().firstNotNullOfOrNull { Library.byId(it) }
                PRow(p, "${pls[p].orEmpty().size} músicas", null, first?.art, p)
            }
        }
    }
    if (create) AlertDialog(onDismissRequest = { create = false }, shape = RoundedCornerShape(20.dp), title = { Text("Nova playlist") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nome") }, shape = RoundedCornerShape(12.dp)) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) Prefs.createPlaylist(name.trim()); name = ""; create = false }) { Text("Criar") } })
}

@Composable
fun OnlineList(ui: PlayerUi, title: String, list: List<YtVideo>, onBack: () -> Unit) {
    Prefs.version.intValue
    Column {
        Header(title, onBack)
        LazyColumn {
            if (list.isEmpty()) item { Empty("Nada por aqui ainda.") }
            itemsIndexed(list, key = { i, v -> "$i${v.src}${v.id}" }) { i, v -> YtRow(v) { ui.controller?.pause(); YtPlayer.play(list, i) } }
        }
    }
}

@Composable
fun PlaylistDetail(ui: PlayerUi, name: String, onBack: () -> Unit) {
    Prefs.version.intValue
    val ids = if (name == FAVS) Prefs.favs().toList() else Prefs.playlists()[name].orEmpty()
    val songs = ids.mapNotNull { Library.byId(it) }
    Column {
        Header(if (name == FAVS) "Favoritas" else name, onBack) {
            if (name != FAVS) IconButton(onClick = { Prefs.deletePlaylist(name); onBack() }) { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.primary) }
        }
        SongListBody(ui, songs, playlist = name.takeIf { it != FAVS })
    }
}

/* ======================= MINI PLAYER ======================= */
@Composable
fun MiniPlayer(ui: PlayerUi, onOpen: () -> Unit) {
    val style = Prefs.miniStyle
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(ui.playing) { while (true) { pos = ui.position; delay(500) } }
    val outer = if (style == "flat") Modifier else Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
    val shape = if (style == "flat") RoundedCornerShape(0.dp) else RoundedCornerShape(14.dp)
    Surface(outer.fillMaxWidth().shadow(if (style == "floating") 12.dp else 0.dp, shape).clickable(onClick = onOpen), shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(ui.art, Modifier.size(44.dp), RoundedCornerShape(6.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(ui.artist, maxLines = 1, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { ui.toggle() }) { Icon(if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(30.dp)) }
                IconButton(onClick = { ui.next() }) { Icon(Icons.Rounded.FastForward, null, Modifier.size(28.dp)) }
            }
            if (Prefs.showProgressMini && ui.duration > 0)
                LinearProgressIndicator(progress = { (pos.toFloat() / ui.duration).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(2.dp),
                    trackColor = Color.Transparent, drawStopIndicator = {})
        }
    }
}

/* ======================= PLAYER COMPLETO (estilo Apple Music) ======================= */
@Composable
fun FullPlayer(ui: PlayerUi, onClose: () -> Unit, onEq: () -> Unit) {
    Prefs.version.intValue
    val c = ui.controller
    var pos by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { pos = ui.position; delay(200) } }
    val accent = MaterialTheme.colorScheme.primary
    val artScale by animateFloatAsState(if (Prefs.artPulse && !Eco.on && !ui.playing) 0.82f else 1f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "art")

    // mesmo visual do player online: fundo do tema, origem no topo, botão de play grande na cor do tema
    val Dim = MaterialTheme.colorScheme.onSurfaceVariant
    val src = ui.mediaId.let { m -> when { m == null -> ""; m.startsWith("sc:") -> "SOUNDCLOUD"; m.startsWith("au:radio-") -> "RÁDIO AO VIVO"; m.startsWith("au:") -> "AUDIUS"; else -> "OFFLINE" } }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickable(enabled = false) {}) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(48.dp)) {
                    IconButton(onClick = onClose, Modifier.align(Alignment.CenterStart)) { Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(32.dp)) }
                    Text(src, Modifier.align(Alignment.Center), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.align(Alignment.CenterEnd)) {
                        IconButton(onClick = onEq) { Icon(Icons.Rounded.Tune, null, Modifier.size(22.dp)) }
                        // botão de info: pequeno, no canto
                        IconButton(onClick = { showInfo = true }) { Icon(Icons.Outlined.Info, "Informações da música", Modifier.size(22.dp)) }
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (showLyrics) LyricsView(ui) { pos } else {
                        val shape: Shape = when (Prefs.artShape) {
                            "square" -> RoundedCornerShape(2.dp); "circle", "disc" -> CircleShape; else -> RoundedCornerShape(Prefs.corner.dp.coerceAtLeast(10.dp))
                        }
                        val rot = rememberInfiniteTransition(label = "disc")
                        val angle by rot.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "a")
                        val spin = Prefs.artShape == "disc" && ui.playing && !Eco.on
                        Art(ui.art, Modifier.fillMaxWidth(Prefs.artSize).aspectRatio(1f)
                            .graphicsLayer(scaleX = artScale, scaleY = artScale)
                            .then(if (spin) Modifier.rotate(angle) else Modifier), if (Prefs.artShape == "rounded") MaterialTheme.shapes.extraLarge else shape)
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ui.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                        Text(ui.artist, color = Dim, maxLines = 1, modifier = Modifier.basicMarquee())
                    }
                    val fav = Prefs.isFav(ui.mediaId)
                    IconButton(onClick = { Prefs.toggleFav(ui.mediaId); if (Prefs.isFav(ui.mediaId)) Taste.onLike(c?.mediaMetadata?.genre?.toString() ?: "", ui.artist) }) {
                        Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = accent)
                    }
                }

                val dur = ui.duration.coerceAtLeast(1)
                Slider(value = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f), onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { c?.seekTo((it * dur).toLong()) }; dragging = null })
                Row(Modifier.fillMaxWidth()) {
                    Text(fmt(dragging?.let { (it * dur).toLong() } ?: pos))
                    Spacer(Modifier.weight(1f))
                    Text(fmt(ui.duration))
                }

                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { c?.shuffleModeEnabled = !ui.shuffle }) {
                        Icon(Icons.Rounded.Shuffle, null, tint = if (ui.shuffle) accent else LocalContentColor.current)
                    }
                    IconButton(onClick = { ui.prev() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(40.dp)) }
                    FilledIconButton(onClick = { ui.toggle() }, Modifier.size(76.dp), shape = MaterialTheme.shapes.extraLarge) {
                        Icon(if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(42.dp))
                    }
                    IconButton(onClick = { ui.next() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(40.dp)) }
                    IconButton(onClick = { c?.repeatMode = (ui.repeat + 1) % 3 }) {
                        Icon(if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null,
                            tint = if (ui.repeat != Player.REPEAT_MODE_OFF) accent else LocalContentColor.current.copy(alpha = .6f))
                    }
                }

                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)) {
                    ToolBtn(Icons.Rounded.FormatQuote, "Letra", showLyrics) { showLyrics = !showLyrics }
                    ToolBtn(Icons.Rounded.Timer, if (Sleep.endsAt.longValue > 0) "Timer ✓" else "Timer", Sleep.endsAt.longValue > 0) { showSleep = true }
                    val onRadio = ui.mediaId?.startsWith("au:radio-") == true
                    val ctxR = LocalContext.current
                    Box(Modifier.alpha(if (onRadio) 0.35f else 1f)) {
                        ToolBtn(Icons.Rounded.Speed, if (onRadio) "Ao vivo" else "${"%.2f".format(Prefs.speed)}x", !onRadio && (Prefs.speed != 1f || Prefs.pitch != 1f)) {
                            if (onRadio) android.widget.Toast.makeText(ctxR, "A velocidade fica desligada na rádio ao vivo", android.widget.Toast.LENGTH_SHORT).show() else showSpeed = true
                        }
                    }
                    ToolBtn(Icons.Rounded.QueueMusic, "Fila", false) { showQueue = true }
                    if (!onRadio) { Prefs.version.intValue; ToolBtn(Icons.Rounded.Album, Retro.NAMES[Retro.mode] ?: "Normal", Retro.mode.isNotEmpty()) { Retro.next(); Prefs.version.intValue++ } }
                    val mid = ui.mediaId
                    Secret.init()
                    if (mid != null && mid.startsWith("sc:") && Secret.on) {
                        Sync.version
                        var showSync by remember { mutableStateOf(false) }
                        val sv = YtVideo(mid.removePrefix("sc:"), ui.title, ui.artist, ui.art?.toString() ?: "", "sc")
                        val done = Sync.syncedUri(sv) != null
                        ToolBtn(if (done) Icons.Rounded.DownloadDone else Icons.Rounded.Download, if (done) "Offline ✓" else "Salvar", done) { showSync = true }
                        if (showSync) SyncSheet(sv) { showSync = false }
                    }
                    // faixa de fonte aberta (Audius/Archive): guardar dentro do app pra tocar sem internet
                    if (mid != null && mid.startsWith("au:") && !onRadio && Secret.on) {
                        Sync.version
                        val item = ui.controller?.currentMediaItem
                        val uri = item?.requestMetadata?.mediaUri?.toString() ?: item?.localConfiguration?.uri?.toString() ?: ""
                        val tid = mid.removePrefix("au:")
                        val isKept = uri.startsWith("file:") || Sync.isKept(tid)
                        val scopeK = rememberCoroutineScope()
                        ToolBtn(if (isKept) Icons.Rounded.OfflinePin else Icons.Rounded.DownloadForOffline, if (isKept) "Offline ✓" else "Offline", isKept) {
                            if (isKept) return@ToolBtn
                            android.widget.Toast.makeText(ctxR, "Guardando pra ouvir offline…", android.widget.Toast.LENGTH_SHORT).show()
                            val t = Track(tid, ui.title, ui.artist, "", ui.duration, ui.art?.toString() ?: "", false, uri)
                            scopeK.launch {
                                val r = Sync.keep(t)
                                android.widget.Toast.makeText(ctxR, if (r.isSuccess) "Guardada no app: toca mesmo sem internet" else "Não deu pra guardar: ${r.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInfo) ModalBottomSheet(onDismissRequest = { showInfo = false }) { InfoSheet(ui) }

    if (showQueue) ModalBottomSheet(onDismissRequest = { showQueue = false }) {
        Text("A seguir", Modifier.padding(horizontal = 20.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val st = rememberLazyListState(initialFirstVisibleItemIndex = ui.index.coerceAtLeast(0))
        LazyColumn(state = st, modifier = Modifier.fillMaxHeight(0.7f)) {
            itemsIndexed(ui.queue) { i, item ->
                ListItem(
                    headlineContent = { Text(item.mediaMetadata.title?.toString() ?: "", maxLines = 1, color = if (i == ui.index) accent else Color.Unspecified) },
                    supportingContent = { Text(item.mediaMetadata.artist?.toString() ?: "", maxLines = 1) },
                    leadingContent = { Art(item.mediaMetadata.artworkUri, Modifier.size(44.dp), RoundedCornerShape(6.dp)) },
                    trailingContent = { IconButton(onClick = { c?.removeMediaItem(i) }) { Icon(Icons.Rounded.RemoveCircleOutline, null) } },
                    modifier = Modifier.clickable { c?.seekTo(i, 0) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    if (showSleep) ModalBottomSheet(onDismissRequest = { showSleep = false }) {
        Column(Modifier.padding(20.dp)) {
            Text("Timer pra parar a música", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(if (Sleep.endsAt.longValue > 0) "Para em ${fmt(Sleep.endsAt.longValue - System.currentTimeMillis())}" else "A música pausa sozinha depois do tempo escolhido.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30, 45, 60, 90, 120).forEach { m -> AssistChip({ Sleep.start(m); showSleep = false }, { Text("$m min") }) }
                if (Sleep.endsAt.longValue > 0) AssistChip({ Sleep.cancel(); showSleep = false }, { Text("Desligar timer") })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSpeed) ModalBottomSheet(onDismissRequest = { showSpeed = false }) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("Velocidade e tom", Modifier.padding(horizontal = 20.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            SliderRow("Velocidade (o tom muda junto)", Prefs.speed, 0.5f..2f, "%.2fx", steps = 29) { Prefs.speed = it; Prefs.pitch = it }
            SliderRow("Tom (pitch)", Prefs.pitch, 0.5f..2f, "%.2f", steps = 29) { Prefs.pitch = it }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Slowed" to (0.85f to 0.85f), "Normal" to (1f to 1f), "Nightcore" to (1.25f to 1.25f), "Sped up" to (1.15f to 1.15f)).forEach { (n, v) ->
                    AssistChip({ Prefs.speed = v.first; Prefs.pitch = v.second }, { Text(n) })
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun InfoSheet(ui: PlayerUi) {
    val local = ui.mediaId?.let { Library.byId(it) }
    var info by remember(ui.mediaId) { mutableStateOf<SongInfo?>(null) }
    LaunchedEffect(ui.mediaId) { info = SongInfoApi.lookup(ui.title, ui.artist, local?.album, local?.year) }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Art(info?.art ?: ui.art, Modifier.size(72.dp), RoundedCornerShape(8.dp))
            Column(Modifier.padding(start = 14.dp)) {
                Text(info?.title ?: ui.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(info?.artist ?: ui.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.height(16.dp))
        val i = info
        if (i == null) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("  Buscando informações…") }
        else Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column {
                listOf("Artista" to i.artist, "Álbum" to i.album, "Lançamento" to i.year, "Gênero" to i.genre, "Faixa" to i.trackNumber,
                    "Duração" to fmt(ui.duration), "Fonte" to i.source).filter { !it.second.isNullOrBlank() }.forEachIndexed { k, (l, v) ->
                    if (k > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(l, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v!!, textAlign = TextAlign.End, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
fun ToolBtn(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.8f))
        Text(label, fontSize = 11.sp, color = LocalContentColor.current.copy(alpha = 0.8f))
    }
}

@Composable
fun LyricsView(ui: PlayerUi, position: () -> Long) {
    var lyrics by remember(ui.mediaId) { mutableStateOf<Lyrics?>(null) }
    LaunchedEffect(ui.mediaId, Prefs.lyricsSource) { lyrics = LyricsApi.get(ui.artist, ui.title, ui.duration) }
    val l = lyrics
    val align = when (Prefs.lyricsAlign) { "left" -> TextAlign.Start; "right" -> TextAlign.End; else -> TextAlign.Center }
    val size = Prefs.lyricsSize.sp
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                l == null -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                l.synced != null -> {
                    val st = rememberLazyListState()
                    var cur by remember { mutableIntStateOf(0) }
                    LaunchedEffect(l) {
                        while (true) {
                            val p = position()
                            val i = l.synced.indexOfLast { it.first <= p }.coerceAtLeast(0)
                            if (i != cur) { cur = i; st.animateScrollToItem((i - 2).coerceAtLeast(0)) }
                            delay(150)
                        }
                    }
                    LazyColumn(state = st, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 120.dp)) {
                        itemsIndexed(l.synced) { i, (t, line) ->
                            Text(line.ifBlank { "♪" }, Modifier.fillMaxWidth().clickable { ui.controller?.seekTo(t) }.padding(vertical = 8.dp),
                                textAlign = align, fontSize = size, fontWeight = FontWeight.Bold,
                                color = if (i == cur) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.35f))
                        }
                    }
                }
                l.plain != null -> Text(l.plain, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                    textAlign = align, fontSize = size * 0.85f, fontWeight = FontWeight.SemiBold, lineHeight = size * 1.25f)
                else -> Text("Letra não encontrada", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (l?.source?.isNotBlank() == true) Text("Letra: ${l.source}", Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ======================= AJUSTES (grupos estilo iOS) ======================= */
@Composable
fun SwitchRow(title: String, sub: String? = null, value: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title, fontSize = 16.sp) }, supportingContent = if (sub != null) { { Text(sub, fontSize = 13.sp) } } else null,
        trailingContent = { Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedBorderColor = Color.Transparent)) },
        modifier = Modifier.clickable { onChange(!value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

@Composable
fun ChoiceRow(title: String, options: List<Pair<String, String>>, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, fontSize = 16.sp)
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (k, l) -> FilterChip(value == k, { onChange(k) }, { Text(l) }, shape = RoundedCornerShape(10.dp)) }
        }
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, format: String, steps: Int = 0, onChange: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row { Text(title, Modifier.weight(1f), fontSize = 16.sp); Text(format.format(v), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Slider(v, { v = it }, valueRange = range, steps = steps, onValueChangeFinished = { onChange(v) })
    }
}

@Composable
fun NavRow(title: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title, fontSize = 16.sp) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(onClick = onClick), colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

@Composable
fun Div() = HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

val ACCENTS = listOf(
    0xFFFA2D48, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF00C7BE, 0xFF0A84FF,
    0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF375F, 0xFFAC8E68, 0xFF8E8E93, 0xFFFFFFFF
).map { it.toInt() }

@Composable
fun SettingsScreen(ui: PlayerUi, onEq: () -> Unit, onRescan: () -> Unit) {
    val ctxS = LocalContext.current
    Prefs.version.intValue
    var hex by remember { mutableStateOf("") }
    val notifOpts = listOf("none" to "Nenhum", "shuffle" to "Aleatório", "repeat" to "Repetir", "like" to "Favoritar",
        "rew" to "Voltar", "fwd" to "Avançar", "close" to "Fechar")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp)) {
        Header("Ajustes")
        SkinGallery()
        TextButton(onClick = { Nav.page = "themes" }, Modifier.padding(start = 12.dp)) { Icon(Icons.Rounded.Palette, null); Text("  Ver todos os temas em tela cheia") }
        Group("Economia de bateria", footer = if (Eco.bySystem) "Ligado agora porque o celular está em economia de bateria." else "Corta o que gasta à toa (fundo do tema, vídeo, fontes, borrão) sem mexer em tocar, buscar, baixar ou gravar.") {
            ChoiceRow("Modo economia", listOf("auto" to "Automático", "on" to "Sempre", "extremo" to "Extremo", "off" to "Nunca"), Eco.mode) { Eco.mode = it; if (it != "extremo") Hunter.start(ctxS); Prefs.version.intValue++ }
            if (Eco.extreme) Text("Modo Extremo: só o essencial pra tocar música. Sem capas e fotos, sem paradas e playlists no Descobrir, sem caçador de rádio, sem animações e fundo preto puro. Tocar, buscar, fila, tela apagada e controles continuam iguais.",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Group("Notificações") {
            var nagOn by remember { mutableStateOf(Nag.on(ctxS)) }
            SwitchRow("Me chamar quando eu sumir", "Se você ficar um dia sem ouvir nada, o app manda uma mensagem sem noção te chamando de volta (no máximo 1 por dia, das 10h às 22h)", nagOn) { nagOn = it; Nag.setOn(ctxS, it) }
            if (nagOn) { Div(); TextButton(onClick = { Nag.fire(ctxS) }, Modifier.padding(start = 8.dp)) { Text("Testar uma mensagem agora") } }
        }

        Group("Aparência") {
            ChoiceRow("Fundo", listOf("system" to "Sistema", "light" to "Claro", "dark" to "Escuro", "amoled" to "Preto total"), Prefs.themeMode) { Prefs.themeMode = it }
            Div()
            SwitchRow("Cores do papel de parede", "Material You (Android 12+)", Prefs.dynamicColor) { Prefs.dynamicColor = it }
            Div()
            Column(Modifier.padding(16.dp)) {
                Text("Cor dos botões", fontSize = 16.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                    ACCENTS.forEach { c ->
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                            .border(if (Prefs.accent == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { Prefs.accent = c })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    OutlinedTextField(hex, { hex = it.take(7) }, Modifier.weight(1f), label = { Text("Cor própria (#RRGGBB)") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                    TextButton(onClick = {
                        runCatching { android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") }.getOrNull()?.let { Prefs.accent = it }
                    }) { Text("Aplicar") }
                }
            }
            Div()
            SliderRow("Arredondamento", Prefs.corner, 0f..32f, "%.0f") { Prefs.corner = it }
            Div()
            SliderRow("Tamanho do texto", Prefs.fontScale, 0.8f..1.4f, "%.2fx") { Prefs.fontScale = it }
        }

        Group("Player") {
            ChoiceRow("Capa", listOf("square" to "Quadrada", "rounded" to "Arredondada", "circle" to "Círculo", "disc" to "Disco girando"), Prefs.artShape) { Prefs.artShape = it }
            Div()
            SliderRow("Tamanho da capa", Prefs.artSize, 0.5f..1f, "%.2f") { Prefs.artSize = it }
            Div()
            SwitchRow("Capa encolhe ao pausar", "Igual ao Apple Music", Prefs.artPulse) { Prefs.artPulse = it }
            Div()
            ChoiceRow("Fundo", listOf("blur" to "Capa desfocada", "gradient" to "Degradê da cor", "solid" to "Sólido"), Prefs.playerBg) { Prefs.playerBg = it }
            Div()
            SliderRow("Desfoque", Prefs.blurAmount, 10f..120f, "%.0f") { Prefs.blurAmount = it }
            Div()
            ChoiceRow("Mini player", listOf("card" to "Cartão", "floating" to "Flutuante", "flat" to "Reto"), Prefs.miniStyle) { Prefs.miniStyle = it }
            Div()
            SwitchRow("Barra de progresso no mini player", null, Prefs.showProgressMini) { Prefs.showProgressMini = it }
        }

        Group("Letras") {
            ChoiceRow("Fonte", listOf("letras" to "Letras.mus.br", "synced" to "Sincronizada (LRCLIB)"), Prefs.lyricsSource) { Prefs.lyricsSource = it }
            Div()
            SliderRow("Tamanho", Prefs.lyricsSize, 14f..36f, "%.0f") { Prefs.lyricsSize = it }
            Div()
            ChoiceRow("Alinhamento", listOf("left" to "Esquerda", "center" to "Centro", "right" to "Direita"), Prefs.lyricsAlign) { Prefs.lyricsAlign = it }
        }

        Group("Biblioteca", footer = "Notas de voz e áudios do WhatsApp, Telegram e gravações ficam de fora.") {
            SwitchRow("Mostrar só músicas", "Esconde áudios de WhatsApp e gravações", Prefs.hideVoice) { Prefs.hideVoice = it }
            Div()
            ChoiceRow("Aba inicial", listOf("0" to "Descobrir", "1" to "Buscar", "2" to "Rádio", "3" to "Biblioteca"), Prefs.startTab.toString()) { Prefs.startTab = it.toInt() }
            Div()
            ChoiceRow("Colunas de álbuns", listOf("2" to "2", "3" to "3", "4" to "4"), Prefs.gridColumns.toString()) { Prefs.gridColumns = it.toInt() }
            Div()
            SwitchRow("Capa na lista", null, Prefs.showArtInList) { Prefs.showArtInList = it }
            Div()
            SliderRow("Ignorar áudios menores que", Prefs.minDurationSec.toFloat(), 0f..120f, "%.0f s") { Prefs.minDurationSec = it.toInt() }
            Div()
            NavRow("Reescanear músicas", Icons.Rounded.Refresh, onRescan)
        }

        Group("Reprodução") {
            SliderRow("Velocidade (o tom muda junto)", Prefs.speed, 0.5f..2f, "%.2fx", steps = 29) { Prefs.speed = it; Prefs.pitch = it }
            Div()
            SliderRow("Tom (pitch)", Prefs.pitch, 0.5f..2f, "%.2f", steps = 29) { Prefs.pitch = it }
            Div()
            SwitchRow("Pular silêncio", null, Prefs.skipSilence) { Prefs.skipSilence = it }
            Div()
            NavRow("Equalizador e efeitos", Icons.Rounded.Equalizer, onEq)
        }

        Group("Notificação") {
            ChoiceRow("Botão extra 1", notifOpts, Prefs.notifBtn1) { Prefs.notifBtn1 = it }
            Div()
            ChoiceRow("Botão extra 2", notifOpts, Prefs.notifBtn2) { Prefs.notifBtn2 = it }
            Div()
            ChoiceRow("Ícone", listOf("crown" to "Coroa", "note" to "Nota", "disc" to "Disco"), Prefs.notifIcon) { Prefs.notifIcon = it }
            Div()
            ChoiceRow("Pulo voltar/avançar", listOf("5" to "5 s", "10" to "10 s", "15" to "15 s", "30" to "30 s"), Prefs.seekStep.toString()) { Prefs.seekStep = it.toInt() }
        }

        Group("Online", footer = "No celular as músicas tocam pelo SoundCloud. Artistas, álbuns e paradas vêm do YouTube Music, mas cada música toca a versão do SoundCloud.") {
            var k by remember { mutableStateOf(YtStore.apiKey) }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(k, { k = it }, Modifier.weight(1f), label = { Text("Chave do YouTube (opcional)") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                TextButton(onClick = { YtStore.apiKey = k }) { Text("Salvar") }
            }
            Div()
            var vk by remember { mutableStateOf(YtStore.vimeoToken) }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(vk, { vk = it }, Modifier.weight(1f), label = { Text("Token do Vimeo (opcional)") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                TextButton(onClick = { YtStore.vimeoToken = vk }) { Text("Salvar") }
            }
            Div()
            SwitchRow("Tocar o próximo sozinho", null, YtStore.autoNext) { YtStore.autoNext = it }

            var adb by remember { mutableStateOf(AdBlock.on) }
            SwitchRow("Bloquear anúncios", "Corta propaganda do YouTube e pula o que passar", adb) { adb = it; AdBlock.on = it; YtPlayer.web?.evaluateJavascript("window.ADB=$it", null) }

            Div()
            SwitchRow("Mostrar vídeo", "Músicas do YouTube tocam com o vídeo no player (gasta mais internet e bateria)", YtStore.showVideo) { YtStore.showVideo = it }
            Secret.init()
            if (Secret.on) {
                Div()
                var aw by remember { mutableStateOf(Sync.autoWifi) }
                SwitchRow("Baixar pendentes sozinho no Wi-Fi", "Músicas em Pra sincronizar são procuradas de novo a cada 6 horas", aw) { aw = it; Sync.autoWifi = it }
            }
        }

        if (Secret.on) Group("Secreto", footer = "Navegador leve que usa o DNS one.one.one.one (1.1.1.1) e não guarda histórico, cache nem cookies: tudo some quando você fecha.") {
            NavRow("Navegador privado", Icons.Rounded.TravelExplore) { BrowserActivity.open(ctxS) }
        }

        AboutCard()
        Text("Uncrowned Music 1.4", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ======================= EQUALIZADOR ======================= */
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    Prefs.version.intValue
    val eq = Effects.eq
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Header("Equalizador", onBack)
            Group { SwitchRow("Ativar efeitos", null, Prefs.eqEnabled) { Prefs.eqEnabled = it } }
            if (eq == null) {
                Empty("Toque uma música primeiro para liberar o equalizador.")
            } else {
                val presets = runCatching { List(eq.numberOfPresets.toInt()) { eq.getPresetName(it.toShort()) } }.getOrDefault(emptyList())
                if (presets.isNotEmpty()) {
                    Section("Presets")
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEachIndexed { i, n -> AssistChip({ Effects.usePreset(i) }, { Text(n) }) }
                        AssistChip({ repeat(eq.numberOfBands.toInt()) { Prefs.setBand(it, 0) } }, { Text("Zerar") })
                    }
                }
                val range = runCatching { eq.bandLevelRange }.getOrNull()
                val min = range?.get(0)?.toFloat() ?: -1500f
                val max = range?.get(1)?.toFloat() ?: 1500f
                Group("Bandas") {
                    repeat(runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)) { b ->
                        val hz = runCatching { eq.getCenterFreq(b.toShort()) / 1000 }.getOrDefault(0)
                        val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
                        if (b > 0) Div()
                        SliderRow(label, Prefs.band(b).toFloat(), min..max, "%.0f") { Prefs.setBand(b, it.toInt()) }
                    }
                }
            }
            Group("Efeitos") {
                SliderRow("Graves", Prefs.bass.toFloat(), 0f..1000f, "%.0f") { Prefs.bass = it.toInt() }
                Div()
                SliderRow("Som 3D", Prefs.virtualizer.toFloat(), 0f..1000f, "%.0f") { Prefs.virtualizer = it.toInt() }
            }
        }
    }
}

/* Ouvidas recentemente: quadradinhos */
@Composable
fun RecentGrid(ui: PlayerUi, list: List<YtVideo>, onBack: () -> Unit) {
    Prefs.version.intValue
    Column {
        Header("Ouvidas recentemente", onBack)
        if (list.isEmpty()) { Empty("As músicas que você ouvir aparecem aqui."); return@Column }
        LazyVerticalGrid(GridCells.Adaptive(108.dp), contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(list.size) { i ->
                val v = list[i]
                Column(Modifier.clickable { ui.controller?.pause(); YtPlayer.play(list, i) }) {
                    Box {
                        Art(v.thumb, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(12.dp))
                        Text(when (v.src) { "sc" -> "SOUNDCLOUD"; "vm" -> "VIMEO"; else -> "YOUTUBE" }, Modifier.padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = .6f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            color = when (v.src) { "sc" -> Color(0xFFFF8A1F); "vm" -> Color(0xFF1AB7EA); else -> Color(0xFFFF5A50) }, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(v.title, Modifier.padding(top = 6.dp), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                    Text(v.channel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}


/* ---------- Página do artista e do álbum (YouTube Music) ---------- */
object Nav {
    var artist by mutableStateOf<YtmArtistHit?>(null)
    var album by mutableStateOf<YtmAlbumHit?>(null)
    private val stack = ArrayList<String>()
    private var pageS = mutableStateOf<String?>(null)
    // páginas internas (biblioteca, temas, mais ouvidas...): guardam o caminho pra voltar
    var page: String?
        get() = pageS.value
        set(v) { if (v != null && pageS.value != null && pageS.value != v) stack.add(pageS.value!!); if (v == null) stack.clear(); pageS.value = v }
    fun back(): String? { val p = stack.removeLastOrNull(); pageS.value = p; return p }
    fun clear() { stack.clear(); pageS.value = null; artist = null; album = null; sim = null }
    var sim by mutableStateOf<YtVideo?>(null)       // parecidas que baixam
}

@Composable
fun ArtistScreen(ui: PlayerUi, hit: YtmArtistHit, onBack: () -> Unit) {
    var a by remember(hit.id) { mutableStateOf<YtmArtist?>(null) }
    var failed by remember(hit.id) { mutableStateOf(false) }
    var all by remember(hit.id) { mutableStateOf(false) }
    LaunchedEffect(hit.id) { a = Ytm.artist(hit.id); failed = a == null }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                Art((a?.art ?: hit.art).ifBlank { null }, Modifier.fillMaxSize().graphicsLayer(alpha = .55f).blur(30.dp), RoundedCornerShape(0.dp))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
                IconButton(onClick = onBack, Modifier.statusBarsPadding().padding(8.dp)) { Icon(Icons.Rounded.ArrowBackIosNew, null) }
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Art((a?.art ?: hit.art).ifBlank { null }, Modifier.size(120.dp), CircleShape)
                    Text("ARTISTA", Modifier.padding(top = 10.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(a?.name?.ifBlank { null } ?: hit.name, fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 36.sp)
                    a?.subs?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        val ar = a
        if (ar == null) { item { if (failed) Empty("Não deu pra abrir esse artista agora.") else LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }; return@LazyColumn }
        if (ar.top.isNotEmpty()) item {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable { ui.controller?.pause(); YtPlayer.play(ar.top, 0) }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                OutlinedButton(onClick = { ui.controller?.pause(); YtPlayer.play(ar.top.shuffled(), 0) }) { Icon(Icons.Rounded.Shuffle, null); Text(" Aleatório") }
            }
        }
        if (ar.top.isNotEmpty()) {
            item { Section("Populares") }
            itemsIndexed(if (all) ar.top else ar.top.take(10), key = { i, v -> "t$i" + v.id }) { i, v -> RankYtRow(i + 1, v) { ui.controller?.pause(); YtPlayer.play(ar.top, i) } }
            if (ar.top.size > 10) item { TextButton(onClick = { all = !all }, Modifier.padding(start = 8.dp)) { Text(if (all) "Mostrar menos" else "Ver mais (${ar.top.size})") } }
        }
        listOf("Álbuns" to ar.albums, "Singles e EPs" to ar.singles).forEach { (title, l) ->
            if (l.isNotEmpty()) {
                item { Section(title) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(l, key = { it.id }) { x ->
                            Column(Modifier.width(130.dp).clickable { Nav.album = YtmAlbumHit(x.id, x.title, title, ar.name, "", x.art) }) {
                                Art(x.art.ifBlank { null }, Modifier.size(130.dp), RoundedCornerShape(10.dp))
                                Text(x.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(x.sub, maxLines = 1, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (ar.videos.isNotEmpty()) {
            item { Section("Vídeos") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(ar.videos, key = { _, v -> "v" + v.id }) { i, v ->
                        Column(Modifier.width(220.dp).clickable { ui.controller?.pause(); YtPlayer.play(ar.videos, i) }) {
                            Art(v.thumb, Modifier.fillMaxWidth().aspectRatio(16f / 9f), RoundedCornerShape(10.dp))
                            Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumScreen(ui: PlayerUi, hit: YtmAlbumHit, onBack: () -> Unit) {
    var al by remember(hit.id) { mutableStateOf<YtmAlbum?>(null) }
    var failed by remember(hit.id) { mutableStateOf(false) }
    LaunchedEffect(hit.id) { al = Ytm.album(hit.id); failed = al == null || al!!.tracks.isEmpty() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(hit.title, onBack = onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Art((al?.art ?: hit.art).ifBlank { null }, Modifier.size(220.dp).shadow(16.dp, RoundedCornerShape(12.dp)), RoundedCornerShape(12.dp))
                Text((al?.type ?: hit.type).uppercase(), Modifier.padding(top = 14.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(al?.title?.ifBlank { null } ?: hit.title, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text(listOf(al?.artist ?: hit.artist, al?.year ?: hit.year, al?.tracks?.let { "${it.size} músicas" } ?: "").filter { it.isNotBlank() }.joinToString(" • "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val t = al?.tracks
                if (!t.isNullOrEmpty()) Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { ui.controller?.pause(); YtPlayer.play(t, 0) }) { Icon(Icons.Rounded.PlayArrow, null); Text(" Tocar") }
                    OutlinedButton(onClick = { ui.controller?.pause(); YtPlayer.play(t.shuffled(), 0) }) { Icon(Icons.Rounded.Shuffle, null) }
                }
            }
        }
        val t = al?.tracks
        if (t == null) item { if (failed) Empty("Não deu pra abrir esse álbum agora.") else LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
        else itemsIndexed(t, key = { i, v -> "a$i" + v.id }) { i, v -> RankYtRow(i + 1, v) { ui.controller?.pause(); YtPlayer.play(t, i) } }
    }
}
