package com.uncrowned.music

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MainActivity : ComponentActivity() {
    private lateinit var ui: PlayerUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Sync.init(this)
        runCatching { Sync.schedule(this) }
        // primeiro uso desta versão: já começa no tema Edgy (igual ao PC)
        if (Prefs.skin.isEmpty()) Skins.all.firstOrNull { it.id == "edgy" }?.let { Skins.apply(this, it) }
        enableEdgeToEdge()
        ui = PlayerUi(this)
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) { add(Manifest.permission.READ_MEDIA_AUDIO); add(Manifest.permission.POST_NOTIFICATIONS) }
            else add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        Eco.init(this); Listen.init(this)
        // imagens: guardadas no armazenamento (não baixa de novo), metade da memória por imagem (RGB565), sem animação
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(this)
            .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.12).build() }
            .diskCache { coil.disk.DiskCache.Builder().directory(cacheDir.resolve("imagens")).maxSizeBytes(200L * 1024 * 1024).build() }
            .respectCacheHeaders(false).allowRgb565(true).crossfade(false).build())
        handleIntent(intent)
        setContent { UncrownedTheme { App(ui, perms) } }
    }

    override fun onStart() { super.onStart(); ui.connect() }
    override fun onStop() {
        val busy = ui.playing || YtPlayer.playing || RadioRecorder.recording != null || Exporter.running || Importer.busy
        runCatching { Listen.save() }
        super.onStop()
        // ícone do tema: troca agora que você saiu do app (se nada estiver tocando)
        if (!busy) runCatching { Skins.applyPendingIcon(this) }
    }
    // pouca memória: solta o que dá pra baixar de novo (imagens em memória)
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) runCatching { coil.Coil.imageLoader(this).memoryCache?.trimMemory(level) }
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) runCatching { coil.Coil.imageLoader(this).memoryCache?.clear() }
    }
    override fun onDestroy() { runCatching { Listen.save() }; ui.disconnect(); super.onDestroy() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    private fun handleIntent(i: Intent?) {
        i?.getStringExtra("hunt_station")?.let { runCatching { Hunter.pendingPlay = Track.from(org.json.JSONObject(it)) } }
    }
}

/* Ponte entre a tela e o serviço de reprodução */
class PlayerUi(private val ctx: Context) {
    var controller by mutableStateOf<MediaController?>(null); private set
    var mediaId by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var art by mutableStateOf<Uri?>(null)
    var playing by mutableStateOf(false)
    var duration by mutableLongStateOf(0L)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var queue by mutableStateOf<List<MediaItem>>(emptyList())
    var index by mutableIntStateOf(0)

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
        // fila do SoundCloud: sabe em que música está e continua quando a fila do player acaba
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { ScPlay.onExoItem(mediaItem?.mediaId) }
        override fun onPlaybackStateChanged(state: Int) {
            val c = controller ?: return
            if (state == Player.STATE_ENDED && c.currentMediaItem?.mediaId?.startsWith("sc:") == true) ScPlay.onExoEnded()
        }
        // link do SoundCloud venceu ou falhou: pega outro e continua do mesmo ponto
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val c = controller ?: return
            val cur = c.currentMediaItem ?: return
            if (!cur.mediaId.startsWith("sc:") || retried == cur.mediaId) return
            retried = cur.mediaId
            val idx = c.currentMediaItemIndex; val pos = c.currentPosition
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                val n = ScPlay.refresh(cur.mediaId.removePrefix("sc:"))
                if (n != null) { c.replaceMediaItem(idx, n.buildUpon().setMediaMetadata(cur.mediaMetadata).build()); c.prepare(); c.seekTo(idx, pos); c.play() }
                else if (idx < c.mediaItemCount - 1) { c.seekToNextMediaItem(); c.prepare(); c.play() }
            }
        }
    }
    private var retried: String? = null
    // músicas do SoundCloud (vêm prontas do ScPlay)
    fun setItems(items: List<MediaItem>) { val c = controller ?: return; YtPlayer.close(); retried = null; c.setMediaItems(items, 0, 0); c.prepare(); c.play() }

    fun connect() {
        if (controller != null) return
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val f = MediaController.Builder(ctx, token).buildAsync()
        f.addListener({
            runCatching { f.get() }.getOrNull()?.let { c -> controller = c; c.addListener(listener); sync(c) }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun disconnect() { controller?.release(); controller = null }

    private var lastId: String? = null
    private var lastStart = 0L
    private var lastGenre = ""
    private var lastArtist = ""

    private fun sync(p: Player) {
        val m = p.mediaMetadata
        val newId = p.currentMediaItem?.mediaId
        if (newId != lastId) {
            // gosto musical: pulou antes de 30s = não curtiu; senão conta como play
            if (lastId != null && System.currentTimeMillis() - lastStart < 30_000) Taste.onSkip(lastGenre, lastArtist)
            if (newId != null) {
                lastGenre = m.genre?.toString() ?: ""; lastArtist = m.artist?.toString() ?: ""
                Taste.onPlay(lastGenre, lastArtist, newId)
            }
            lastId = newId; lastStart = System.currentTimeMillis()
        }
        mediaId = newId
        title = m.title?.toString() ?: ""
        artist = m.artist?.toString() ?: ""
        art = m.artworkUri
        playing = p.isPlaying
        duration = p.duration.coerceAtLeast(0)
        shuffle = p.shuffleModeEnabled
        repeat = p.repeatMode
        index = p.currentMediaItemIndex
        queue = List(p.mediaItemCount) { p.getMediaItemAt(it) }
    }

    val position: Long get() = controller?.currentPosition ?: 0L

    // próxima/anterior: quando a fila do player acaba, a do SoundCloud continua
    fun next() { val c = controller ?: return; if (!c.hasNextMediaItem() && ScPlay.active && c.currentMediaItem?.mediaId?.startsWith("sc:") == true) ScPlay.next() else c.seekToNext() }
    fun prev() { val c = controller ?: return; if (!c.hasPreviousMediaItem() && c.currentPosition < 3000 && ScPlay.active && c.currentMediaItem?.mediaId?.startsWith("sc:") == true) ScPlay.prev() else c.seekToPrevious() }
    fun play(list: List<Song>, start: Int) {
        val c = controller ?: return
        ScPlay.stop(); YtPlayer.close()
        c.setMediaItems(list.map { it.toItem() }, start, 0); c.prepare(); c.play()
    }
    fun playOnline(list: List<Track>, start: Int) {
        val c = controller ?: return
        ScPlay.stop(); YtPlayer.close()
        c.setMediaItems(list.map { it.toItem() }, start, 0); c.prepare(); c.play()
    }
    // toca um arquivo do celular no lugar da música online (versão sincronizada)
    fun playUri(uri: String, v: YtVideo) {
        val c = controller ?: return
        val item = MediaItem.Builder().setMediaId("off:" + v.id).setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(v.title).setArtist(v.channel).setArtworkUri(v.thumb.takeIf { it.isNotBlank() }?.let(Uri::parse)).build()).build()
        ScPlay.stop(); YtPlayer.close()
        c.setMediaItem(item); c.prepare(); c.play()
    }
    // fecha o player de baixo quando começa a tocar online (um player só por vez)
    fun stopLocal() { controller?.let { it.stop(); it.clearMediaItems() } }
    fun playNext(s: Song) { controller?.let { it.addMediaItem((it.currentMediaItemIndex + 1).coerceAtMost(it.mediaItemCount), s.toItem()) } }
    fun enqueue(s: Song) { controller?.addMediaItem(s.toItem()) }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } }
}

/* Tema 100% configurável */
@Composable
fun UncrownedTheme(content: @Composable () -> Unit) {
    Prefs.version.intValue
    val ctx = LocalContext.current
    val mode = Prefs.themeMode
    val dark = when (mode) { "light" -> false; "dark", "amoled" -> true; else -> isSystemInDarkTheme() }
    val accent = Color(Prefs.accent)
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    var scheme = if (Prefs.dynamicColor && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent, secondary = accent, tertiary = accent,
        primaryContainer = accent.copy(alpha = 0.3f).compositeOver(Color(0xFF151518)),
        secondaryContainer = accent.copy(alpha = 0.22f).compositeOver(Color(0xFF151518)),
        background = Color(0xFF000000), surface = Color(0xFF000000), onSurfaceVariant = Color(0xFF8E8E93),
        surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerLow = Color(0xFF0E0E0F),
        outlineVariant = Color(0xFF38383A)
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent, secondary = accent, tertiary = accent,
        primaryContainer = accent.copy(alpha = 0.2f).compositeOver(Color.White),
        secondaryContainer = accent.copy(alpha = 0.16f).compositeOver(Color.White),
        background = Color(0xFFF2F2F7), surface = Color(0xFFF2F2F7), onSurfaceVariant = Color(0xFF8E8E93),
        surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFE5E5EA), outlineVariant = Color(0xFFC6C6C8)
    )
    if (mode == "amoled") scheme = scheme.copy(
        background = Color.Black, surface = Color.Black,
        surfaceContainer = Color(0xFF141414), surfaceContainerHigh = Color(0xFF222224), surfaceContainerLow = Color(0xFF050505)
    )
    // tema escolhido: cores próprias (fundo, cards, texto)
    Skins.current?.let { sk ->
        val bg = Color(sk.bg); val card = Color(sk.card); val card2 = Color(sk.card2); val txt = Color(sk.text)
        scheme = scheme.copy(background = bg, surface = bg, surfaceContainer = card, surfaceContainerHigh = card2, surfaceContainerLow = bg,
            onBackground = txt, onSurface = txt, onSurfaceVariant = Color(sk.dim),
            primaryContainer = accent.copy(alpha = 0.25f).compositeOver(card), secondaryContainer = accent.copy(alpha = 0.2f).compositeOver(card))
    }
    val r = Prefs.corner.dp
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * Prefs.fontScale)) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                small = RoundedCornerShape(r / 2), medium = RoundedCornerShape(r),
                large = RoundedCornerShape(r * 1.5f), extraLarge = RoundedCornerShape(r * 2)
            ),
            typography = Skins.font?.let { f -> Typography().let { t -> t.copy(displayLarge = t.displayLarge.copy(fontFamily = f), displayMedium = t.displayMedium.copy(fontFamily = f),
                headlineLarge = t.headlineLarge.copy(fontFamily = f), headlineMedium = t.headlineMedium.copy(fontFamily = f), headlineSmall = t.headlineSmall.copy(fontFamily = f),
                titleLarge = t.titleLarge.copy(fontFamily = f)) } } ?: Typography()
        ) {
            // texto sempre na cor do tema (antes ficava preto em algumas telas)
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { content() }
        }
    }
}
