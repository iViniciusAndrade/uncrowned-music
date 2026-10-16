package com.uncrowned.music

import android.Manifest
import android.content.ComponentName
import android.content.Context
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
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class MainActivity : ComponentActivity() {
    private lateinit var ui: PlayerUi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        enableEdgeToEdge()
        ui = PlayerUi(this)
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) { add(Manifest.permission.READ_MEDIA_AUDIO); add(Manifest.permission.POST_NOTIFICATIONS) }
            else add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        setContent { UncrownedTheme { App(ui, perms) } }
    }

    override fun onStart() { super.onStart(); ui.connect() }
    override fun onStop() { ui.disconnect(); super.onStop() }
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
    }

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

    fun play(list: List<Song>, start: Int) {
        val c = controller ?: return
        YtPlayer.js("player.pauseVideo()")
        c.setMediaItems(list.map { it.toItem() }, start, 0); c.prepare(); c.play()
    }
    fun playOnline(list: List<Track>, start: Int) {
        val c = controller ?: return
        YtPlayer.js("player.pauseVideo()")
        c.setMediaItems(list.map { it.toItem() }, start, 0); c.prepare(); c.play()
    }
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
    val r = Prefs.corner.dp
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * Prefs.fontScale)) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                small = RoundedCornerShape(r / 2), medium = RoundedCornerShape(r),
                large = RoundedCornerShape(r * 1.5f), extraLarge = RoundedCornerShape(r * 2)
            ),
            content = content
        )
    }
}
