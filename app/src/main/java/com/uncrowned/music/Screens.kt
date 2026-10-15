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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable

fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

@Composable
fun Art(model: Any?, modifier: Modifier, shape: Shape = MaterialTheme.shapes.small) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_note), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize(0.45f))
        AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/* ======================= APP ======================= */
@Composable
fun App(ui: PlayerUi, perms: List<String>) {
    val ctx = LocalContext.current
    Prefs.version.intValue
    val audioPerm = perms.first()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = it[audioPerm] == true || granted }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms.toTypedArray()) }
    LaunchedEffect(granted) { if (granted) Library.load(ctx) }

    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(Prefs.startTab) }
    var expanded by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var ytExpanded by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var playlistDetail by remember { mutableStateOf<String?>(null) }

    BackHandler(ytExpanded || expanded || showEq || detail != null || playlistDetail != null) {
        when { ytExpanded -> ytExpanded = false; showEq -> showEq = false; expanded -> expanded = false; detail != null -> detail = null; else -> playlistDetail = null }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    if (!ytExpanded) YtPlayerBar(false) { ytExpanded = it; if (it) ui.controller?.pause() }
                    if (ui.mediaId != null) MiniPlayer(ui) { expanded = true }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        listOf(
                            Icons.Rounded.MusicNote to "Músicas", Icons.Rounded.Album to "Álbuns",
                            Icons.Rounded.Person to "Artistas", Icons.Rounded.QueueMusic to "Playlists",
                            Icons.Rounded.Public to "Online", Icons.Rounded.Explore to "Descobrir",
                            Icons.Rounded.Tune to "Ajustes"
                        ).forEachIndexed { i, (ic, label) ->
                            NavigationBarItem(selected = tab == i, onClick = { tab = i; detail = null; playlistDetail = null },
                                icon = { Icon(ic, null) }, label = { Text(label, maxLines = 1) }, alwaysShowLabel = false)
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                if (!granted && tab < 4) {
                    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(R.drawable.ic_crown), null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("O Uncrowned precisa de acesso às suas músicas", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { launcher.launch(perms.toTypedArray()) }) { Text("Permitir") }
                    }
                } else {
                    val d = detail
                    val pd = playlistDetail
                    when {
                        d != null -> SongList(ui, d.second, title = d.first, onBack = { detail = null })
                        pd != null -> PlaylistDetail(ui, pd) { playlistDetail = null }
                        tab == 0 -> SongsTab(ui)
                        tab == 1 -> AlbumsTab { detail = it }
                        tab == 2 -> ArtistsTab { detail = it }
                        tab == 3 -> PlaylistsTab(ui) { playlistDetail = it }
                        tab == 4 -> YouTubeTab(ui)
                        tab == 5 -> DiscoverTab(ui)
                        else -> SettingsScreen(ui, onEq = { showEq = true }, onRescan = { if (granted) scope.launch { Library.load(ctx) } })
                    }
                }
            }
        }

        AnimatedVisibility(expanded, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            FullPlayer(ui, onClose = { expanded = false }, onEq = { showEq = true })
        }
        if (ytExpanded) YtPlayerBar(true) { ytExpanded = it }
        AnimatedVisibility(showEq, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it }) {
            EqualizerScreen { showEq = false }
        }
    }
}

/* ======================= LISTAS ======================= */
@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null) }
        else Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun SongsTab(ui: PlayerUi) {
    var query by remember { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("title") }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    Prefs.version.intValue
    val favs = Prefs.favs()
    val list = Library.songs.filter {
        (query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)) &&
            (!favOnly || it.id.toString() in favs)
    }.let { l ->
        when (sort) { "artist" -> l.sortedBy { it.artist.lowercase() }; "recent" -> l.sortedByDescending { it.dateAdded }; "duration" -> l.sortedByDescending { it.duration }; else -> l }
    }
    Column {
        Header("Uncrowned Music")
        OutlinedTextField(
            query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Buscar música, artista ou álbum") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true, shape = MaterialTheme.shapes.large
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(favOnly, { favOnly = !favOnly }, { Text("Favoritas") }, leadingIcon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(16.dp)) })
            listOf("title" to "A-Z", "artist" to "Artista", "recent" to "Recentes", "duration" to "Duração").forEach { (k, l) ->
                FilterChip(sort == k, { sort = k }, { Text(l) })
            }
        }
        SongListBody(ui, list, shuffleButton = true)
    }
}

@Composable
fun SongList(ui: PlayerUi, songs: List<Song>, title: String, onBack: () -> Unit) {
    Column { Header(title, onBack); SongListBody(ui, songs, shuffleButton = true) }
}

@Composable
fun SongListBody(ui: PlayerUi, songs: List<Song>, shuffleButton: Boolean, playlist: String? = null) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (shuffleButton && songs.isNotEmpty()) item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ui.play(songs, 0) }) { Icon(Icons.Rounded.PlayArrow, null); Text("Tocar tudo") }
                FilledTonalButton(onClick = { ui.play(songs.shuffled(), 0) }) { Icon(Icons.Rounded.Shuffle, null); Text(" Aleatório") }
                Spacer(Modifier.weight(1f))
                Text("${songs.size} músicas", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium)
            }
        }
        if (songs.isEmpty()) item {
            Text("Nada por aqui ainda.", Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
            .background(if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (Prefs.showArtInList) { Art(s.art, Modifier.size(48.dp)); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text("${s.artist} • ${fmt(s.duration)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, null) }
            DropdownMenu(menu, { menu = false }) {
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
    AlertDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = { if (newName.isNotBlank()) { Prefs.createPlaylist(newName.trim()); onPick(newName.trim()) } }) { Text("Criar e adicionar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }, title = { Text("Adicionar à playlist") }, text = {
        Column {
            Prefs.playlists().keys.forEach { name ->
                Text(name, Modifier.fillMaxWidth().clickable { onPick(name) }.padding(vertical = 12.dp))
            }
            OutlinedTextField(newName, { newName = it }, label = { Text("Nova playlist") }, singleLine = true)
        }
    })
}

@Composable
fun AlbumsTab(onOpen: (Pair<String, List<Song>>) -> Unit) {
    val albums = Library.songs.groupBy { it.albumId }.values.sortedBy { it.first().album.lowercase() }
    Column {
        Header("Álbuns")
        LazyVerticalGrid(GridCells.Fixed(Prefs.gridColumns), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums, key = { it.first().albumId }) { a ->
                Column(Modifier.clip(MaterialTheme.shapes.medium).clickable { onOpen(a.first().album to a) }) {
                    Art(a.first().art, Modifier.fillMaxWidth().aspectRatio(1f), MaterialTheme.shapes.medium)
                    Text(a.first().album, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Text(a.first().artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    headlineContent = { Text(name, maxLines = 1) },
                    supportingContent = { Text("${songs.size} músicas") },
                    leadingContent = { Art(songs.first().art, Modifier.size(52.dp), CircleShape) },
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
    Column {
        Header("Playlists") { IconButton(onClick = { create = true }) { Icon(Icons.Rounded.Add, null) } }
        LazyColumn {
            item {
                ListItem(headlineContent = { Text("Favoritas") }, supportingContent = { Text("${Prefs.favs().size} músicas") },
                    leadingContent = { Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary), Alignment.Center) { Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.onPrimary) } },
                    modifier = Modifier.clickable { onOpen(FAVS) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            items(pls.keys.toList()) { p ->
                val first = pls[p].orEmpty().firstNotNullOfOrNull { Library.byId(it) }
                ListItem(headlineContent = { Text(p) }, supportingContent = { Text("${pls[p].orEmpty().size} músicas") },
                    leadingContent = { Art(first?.art, Modifier.size(52.dp)) },
                    modifier = Modifier.clickable { onOpen(p) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
    }
    if (create) AlertDialog(onDismissRequest = { create = false }, title = { Text("Nova playlist") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nome") }) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) Prefs.createPlaylist(name.trim()); name = ""; create = false }) { Text("Criar") } })
}

const val FAVS = "\u0000favs"

@Composable
fun PlaylistDetail(ui: PlayerUi, name: String, onBack: () -> Unit) {
    Prefs.version.intValue
    val ids = if (name == FAVS) Prefs.favs().toList() else Prefs.playlists()[name].orEmpty()
    val songs = ids.mapNotNull { Library.byId(it) }
    Column {
        Header(if (name == FAVS) "Favoritas" else name, onBack) {
            if (name != FAVS) IconButton(onClick = { Prefs.deletePlaylist(name); onBack() }) { Icon(Icons.Rounded.Delete, null) }
        }
        SongListBody(ui, songs, shuffleButton = true, playlist = name.takeIf { it != FAVS })
    }
}

/* ======================= MINI PLAYER ======================= */
@Composable
fun MiniPlayer(ui: PlayerUi, onOpen: () -> Unit) {
    val style = Prefs.miniStyle
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(ui.playing) { while (true) { pos = ui.position; delay(500) } }
    val outer = when (style) { "floating" -> Modifier.padding(horizontal = 10.dp, vertical = 6.dp); else -> Modifier }
    val shape = when (style) { "flat" -> RoundedCornerShape(0.dp); else -> MaterialTheme.shapes.large }
    Surface(outer.fillMaxWidth().clickable(onClick = onOpen), shape = shape,
        color = if (style == "flat") MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = if (style == "floating") 8.dp else 0.dp) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(ui.art, Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(ui.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(ui.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { ui.toggle() }) { Icon(if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null) }
                IconButton(onClick = { ui.controller?.seekToNext() }) { Icon(Icons.Rounded.SkipNext, null) }
            }
            if (Prefs.showProgressMini && ui.duration > 0)
                LinearProgressIndicator(progress = { (pos.toFloat() / ui.duration).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(2.dp))
        }
    }
}

/* ======================= PLAYER COMPLETO ======================= */
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
    LaunchedEffect(Unit) { while (true) { pos = ui.position; delay(200) } }
    val accent = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxSize().background(bg).clickable(enabled = false) {}) {
        when (Prefs.playerBg) {
            "blur" -> AsyncImage(ui.art, null, Modifier.fillMaxSize().blur(Prefs.blurAmount.dp), contentScale = ContentScale.Crop, alpha = 0.55f)
            "gradient" -> Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), bg))))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.85f)))))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(32.dp)) }
                Text("TOCANDO AGORA", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
                IconButton(onClick = onEq) { Icon(Icons.Rounded.Equalizer, null) }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (showLyrics) LyricsView(ui) { pos } else {
                    val shape: Shape = when (Prefs.artShape) {
                        "square" -> RoundedCornerShape(0.dp); "circle", "disc" -> CircleShape; else -> MaterialTheme.shapes.extraLarge
                    }
                    val rot = rememberInfiniteTransition(label = "disc")
                    val angle by rot.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "a")
                    val spin = Prefs.artShape == "disc" && ui.playing
                    Art(ui.art, Modifier.fillMaxWidth(Prefs.artSize).aspectRatio(1f).then(if (spin) Modifier.rotate(angle) else Modifier), shape)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ui.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee())
                    Text(ui.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                val fav = Prefs.isFav(ui.mediaId)
                IconButton(onClick = { Prefs.toggleFav(ui.mediaId); if (Prefs.isFav(ui.mediaId)) Taste.onLike(c?.mediaMetadata?.genre?.toString() ?: "", ui.artist) }) {
                    Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (fav) accent else MaterialTheme.colorScheme.onSurface)
                }
            }

            val dur = ui.duration.coerceAtLeast(1)
            Slider(value = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f), onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { c?.seekTo((it * dur).toLong()) }; dragging = null })
            Row(Modifier.fillMaxWidth()) {
                Text(fmt(dragging?.let { (it * dur).toLong() } ?: pos), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(fmt(ui.duration), style = MaterialTheme.typography.labelMedium)
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { c?.shuffleModeEnabled = !ui.shuffle }) {
                    Icon(Icons.Rounded.Shuffle, null, tint = if (ui.shuffle) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { c?.seekToPrevious() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(40.dp)) }
                FilledIconButton(onClick = { ui.toggle() }, Modifier.size(76.dp), shape = MaterialTheme.shapes.extraLarge) {
                    Icon(if (ui.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(42.dp))
                }
                IconButton(onClick = { c?.seekToNext() }, Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(40.dp)) }
                IconButton(onClick = { c?.repeatMode = (ui.repeat + 1) % 3 }) {
                    Icon(if (ui.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null,
                        tint = if (ui.repeat != Player.REPEAT_MODE_OFF) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolBtn(Icons.Rounded.FormatQuote, "Letra", showLyrics) { showLyrics = !showLyrics }
                ToolBtn(Icons.Rounded.QueueMusic, "Fila", false) { showQueue = true }
                ToolBtn(Icons.Rounded.Bedtime, if (Sleep.endsAt.longValue > 0) "Timer ✓" else "Timer", Sleep.endsAt.longValue > 0) { showSleep = true }
                ToolBtn(Icons.Rounded.Speed, "${"%.2f".format(Prefs.speed)}x", Prefs.speed != 1f) { showSpeed = true }
            }
        }
    }

    if (showQueue) ModalBottomSheet(onDismissRequest = { showQueue = false }) {
        Text("Fila (${ui.queue.size})", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.titleLarge)
        val st = rememberLazyListState(initialFirstVisibleItemIndex = ui.index.coerceAtLeast(0))
        LazyColumn(state = st, modifier = Modifier.fillMaxHeight(0.7f)) {
            itemsIndexed(ui.queue) { i, item ->
                ListItem(
                    headlineContent = { Text(item.mediaMetadata.title?.toString() ?: "", maxLines = 1, color = if (i == ui.index) accent else Color.Unspecified) },
                    supportingContent = { Text(item.mediaMetadata.artist?.toString() ?: "", maxLines = 1) },
                    leadingContent = { Art(item.mediaMetadata.artworkUri, Modifier.size(44.dp)) },
                    trailingContent = { IconButton(onClick = { c?.removeMediaItem(i) }) { Icon(Icons.Rounded.Close, null) } },
                    modifier = Modifier.clickable { c?.seekTo(i, 0) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    if (showSleep) ModalBottomSheet(onDismissRequest = { showSleep = false }) {
        Column(Modifier.padding(20.dp)) {
            Text("Timer para parar", style = MaterialTheme.typography.titleLarge)
            if (Sleep.endsAt.longValue > 0) Text("Para em ${fmt(Sleep.endsAt.longValue - System.currentTimeMillis())}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30, 45, 60, 90, 120).forEach { m ->
                    AssistChip({ Sleep.start(m); showSleep = false }, { Text("$m min") })
                }
                if (Sleep.endsAt.longValue > 0) AssistChip({ Sleep.cancel(); showSleep = false }, { Text("Cancelar") })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSpeed) ModalBottomSheet(onDismissRequest = { showSpeed = false }) {
        Column(Modifier.padding(20.dp)) {
            SliderRow("Velocidade", Prefs.speed, 0.5f..2f, "%.2fx") { Prefs.speed = it }
            SliderRow("Tom (pitch)", Prefs.pitch, 0.5f..2f, "%.2f") { Prefs.pitch = it }
            TextButton(onClick = { Prefs.speed = 1f; Prefs.pitch = 1f }) { Text("Restaurar") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun ToolBtn(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun LyricsView(ui: PlayerUi, position: () -> Long) {
    var lyrics by remember(ui.mediaId) { mutableStateOf<Lyrics?>(null) }
    LaunchedEffect(ui.mediaId) { lyrics = LyricsApi.get(ui.artist, ui.title, ui.duration) }
    val l = lyrics
    val align = when (Prefs.lyricsAlign) { "left" -> TextAlign.Start; "right" -> TextAlign.End; else -> TextAlign.Center }
    val size = Prefs.lyricsSize.sp
    when {
        l == null -> CircularProgressIndicator()
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
                        textAlign = align, fontSize = size, fontWeight = if (i == cur) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (i == cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                }
            }
        }
        l.plain != null -> Text(l.plain, Modifier.fillMaxSize().verticalScroll(rememberScrollState()), textAlign = align, fontSize = size * 0.8f)
        else -> Text("Letra não encontrada", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ======================= AJUSTES ======================= */
@Composable
fun Section(title: String) {
    Text(title, Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
}

@Composable
fun SwitchRow(title: String, sub: String? = null, value: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = if (sub != null) { { Text(sub) } } else null,
        trailingContent = { Switch(value, onChange) }, modifier = Modifier.clickable { onChange(!value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

@Composable
fun ChoiceRow(title: String, options: List<Pair<String, String>>, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (k, l) -> FilterChip(value == k, { onChange(k) }, { Text(l) }) }
        }
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, format: String, steps: Int = 0, onChange: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row { Text(title, Modifier.weight(1f)); Text(format.format(v), color = MaterialTheme.colorScheme.primary) }
        Slider(v, { v = it }, valueRange = range, steps = steps, onValueChangeFinished = { onChange(v) })
    }
}

val ACCENTS = listOf(
    0xFF9D4EDD, 0xFFFFD166, 0xFFEF476F, 0xFF06D6A0, 0xFF118AB2, 0xFFFF7B00,
    0xFFE63946, 0xFF00B4D8, 0xFF80ED99, 0xFFF72585, 0xFFB5838D, 0xFFFFFFFF
).map { it.toInt() }

@Composable
fun SettingsScreen(ui: PlayerUi, onEq: () -> Unit, onRescan: () -> Unit) {
    Prefs.version.intValue
    var hex by remember { mutableStateOf("") }
    val notifOpts = listOf("none" to "Nenhum", "shuffle" to "Aleatório", "repeat" to "Repetir", "like" to "Favoritar",
        "rew" to "Voltar", "fwd" to "Avançar", "close" to "Fechar")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { Header("Ajustes") }

        item { Section("TEMA") }
        item { ChoiceRow("Modo", listOf("system" to "Sistema", "light" to "Claro", "dark" to "Escuro", "amoled" to "AMOLED"), Prefs.themeMode) { Prefs.themeMode = it } }
        item { SwitchRow("Cores do papel de parede (Material You)", "Android 12+", Prefs.dynamicColor) { Prefs.dynamicColor = it } }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Cor de destaque")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    ACCENTS.forEach { c ->
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                            .border(if (Prefs.accent == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { Prefs.accent = c })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(hex, { hex = it.take(7) }, Modifier.weight(1f), label = { Text("Cor personalizada (#RRGGBB)") }, singleLine = true)
                    TextButton(onClick = {
                        runCatching { android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex") }.getOrNull()?.let { Prefs.accent = it }
                    }) { Text("Aplicar") }
                }
            }
        }
        item { SliderRow("Arredondamento dos cantos", Prefs.corner, 0f..32f, "%.0f dp") { Prefs.corner = it } }
        item { SliderRow("Tamanho do texto", Prefs.fontScale, 0.8f..1.4f, "%.2fx") { Prefs.fontScale = it } }

        item { Section("BIBLIOTECA") }
        item { ChoiceRow("Aba inicial", listOf("0" to "Músicas", "1" to "Álbuns", "2" to "Artistas", "3" to "Playlists", "4" to "Online", "5" to "Descobrir"), Prefs.startTab.toString()) { Prefs.startTab = it.toInt() } }
        item { ChoiceRow("Colunas na grade de álbuns", listOf("1" to "1", "2" to "2", "3" to "3", "4" to "4"), Prefs.gridColumns.toString()) { Prefs.gridColumns = it.toInt() } }
        item { SwitchRow("Mostrar capa na lista", null, Prefs.showArtInList) { Prefs.showArtInList = it } }
        item { SliderRow("Ignorar áudios menores que", Prefs.minDurationSec.toFloat(), 0f..120f, "%.0f s") { Prefs.minDurationSec = it.toInt(); onRescan() } }
        item { ListItem(headlineContent = { Text("Reescanear músicas") }, leadingContent = { Icon(Icons.Rounded.Refresh, null) },
            modifier = Modifier.clickable(onClick = onRescan), colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }

        item { Section("TELA DO PLAYER") }
        item { ChoiceRow("Formato da capa", listOf("square" to "Quadrada", "rounded" to "Arredondada", "circle" to "Círculo", "disc" to "Disco girando"), Prefs.artShape) { Prefs.artShape = it } }
        item { SliderRow("Tamanho da capa", Prefs.artSize, 0.5f..1f, "%.2f") { Prefs.artSize = it } }
        item { ChoiceRow("Fundo", listOf("solid" to "Sólido", "blur" to "Capa desfocada", "gradient" to "Degradê da cor"), Prefs.playerBg) { Prefs.playerBg = it } }
        item { SliderRow("Intensidade do desfoque", Prefs.blurAmount, 10f..120f, "%.0f") { Prefs.blurAmount = it } }
        item { ChoiceRow("Mini player", listOf("card" to "Cartão", "flat" to "Reto", "floating" to "Flutuante"), Prefs.miniStyle) { Prefs.miniStyle = it } }
        item { SwitchRow("Barra de progresso no mini player", null, Prefs.showProgressMini) { Prefs.showProgressMini = it } }

        item { Section("ONLINE (YOUTUBE / SOUNDCLOUD / VIMEO)") }
        item {
            var k by remember { mutableStateOf(YtStore.apiKey) }
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(k, { k = it }, Modifier.weight(1f), label = { Text("Chave da API do YouTube (opcional)") }, singleLine = true)
                TextButton(onClick = { YtStore.apiKey = k }) { Text("Salvar") }
            }
        }
        item {
            var vk by remember { mutableStateOf(YtStore.vimeoToken) }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(vk, { vk = it }, Modifier.weight(1f), label = { Text("Token do Vimeo") }, singleLine = true)
                    TextButton(onClick = { YtStore.vimeoToken = vk }) { Text("Salvar") }
                }
                Text("Grátis: developer.vimeo.com → Create app → Generate access token (público)", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { SwitchRow("Tocar o próximo vídeo sozinho", null, YtStore.autoNext) { YtStore.autoNext = it } }
        item { SwitchRow("Mostrar vídeo no player", "Desligado mostra só a capa", YtStore.showVideo) { YtStore.showVideo = it } }

        item { Section("LETRAS") }
        item { SliderRow("Tamanho da letra", Prefs.lyricsSize, 14f..36f, "%.0f sp") { Prefs.lyricsSize = it } }
        item { ChoiceRow("Alinhamento", listOf("left" to "Esquerda", "center" to "Centro", "right" to "Direita"), Prefs.lyricsAlign) { Prefs.lyricsAlign = it } }

        item { Section("BARRA DE NOTIFICAÇÃO") }
        item { ChoiceRow("Botão extra 1", notifOpts, Prefs.notifBtn1) { Prefs.notifBtn1 = it } }
        item { ChoiceRow("Botão extra 2", notifOpts, Prefs.notifBtn2) { Prefs.notifBtn2 = it } }
        item { ChoiceRow("Ícone da notificação", listOf("crown" to "Coroa", "note" to "Nota", "disc" to "Disco"), Prefs.notifIcon) { Prefs.notifIcon = it } }
        item { ChoiceRow("Pulo dos botões voltar/avançar", listOf("5" to "5 s", "10" to "10 s", "15" to "15 s", "30" to "30 s"), Prefs.seekStep.toString()) { Prefs.seekStep = it.toInt() } }

        item { Section("REPRODUÇÃO") }
        item { SliderRow("Velocidade", Prefs.speed, 0.5f..2f, "%.2fx") { Prefs.speed = it } }
        item { SliderRow("Tom (pitch)", Prefs.pitch, 0.5f..2f, "%.2f") { Prefs.pitch = it } }
        item { SwitchRow("Pular silêncio", "Corta partes mudas das faixas", Prefs.skipSilence) { Prefs.skipSilence = it } }
        item { ListItem(headlineContent = { Text("Equalizador e efeitos") }, leadingContent = { Icon(Icons.Rounded.Equalizer, null) },
            modifier = Modifier.clickable(onClick = onEq), colors = ListItemDefaults.colors(containerColor = Color.Transparent)) }

        item {
            Text("Uncrowned Music 1.0", Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ======================= EQUALIZADOR ======================= */
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    Prefs.version.intValue
    val eq = Effects.eq
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Header("Equalizador", onBack)
            SwitchRow("Ativar efeitos", null, Prefs.eqEnabled) { Prefs.eqEnabled = it }
            if (eq == null) {
                Text("Toque uma música primeiro para liberar o equalizador.", Modifier.padding(20.dp))
            } else {
                val presets = runCatching { List(eq.numberOfPresets.toInt()) { eq.getPresetName(it.toShort()) } }.getOrDefault(emptyList())
                if (presets.isNotEmpty()) {
                    Section("PRESETS")
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEachIndexed { i, n -> AssistChip({ Effects.usePreset(i) }, { Text(n) }) }
                        AssistChip({ repeat(eq.numberOfBands.toInt()) { Prefs.setBand(it, 0) } }, { Text("Zerar") })
                    }
                }
                Section("BANDAS")
                val range = runCatching { eq.bandLevelRange }.getOrNull()
                val min = range?.get(0)?.toFloat() ?: -1500f
                val max = range?.get(1)?.toFloat() ?: 1500f
                repeat(runCatching { eq.numberOfBands.toInt() }.getOrDefault(0)) { b ->
                    val hz = runCatching { eq.getCenterFreq(b.toShort()) / 1000 }.getOrDefault(0)
                    val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
                    SliderRow(label, Prefs.band(b).toFloat(), min..max, "%.0f mB") { Prefs.setBand(b, it.toInt()) }
                }
            }
            Section("EFEITOS")
            SliderRow("Graves (Bass Boost)", Prefs.bass.toFloat(), 0f..1000f, "%.0f") { Prefs.bass = it.toInt() }
            SliderRow("Som 3D (Virtualizer)", Prefs.virtualizer.toFloat(), 0f..1000f, "%.0f") { Prefs.virtualizer = it.toInt() }
            Spacer(Modifier.height(32.dp))
        }
    }
}
