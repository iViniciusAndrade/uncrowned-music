package com.uncrowned.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.AttributeSet
import android.view.View
import android.webkit.WebView
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/* O player da web (YouTube / SoundCloud oficial) sempre "acha" que está na tela,
   mesmo com a tela apagada ou o app no fundo — igual o Brave faz pra tocar em segundo plano. */
class BgWebView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : WebView(ctx, attrs) {
    init {
        // o processo que roda a página não perde prioridade quando some da tela (senão o Android congela ele)
        runCatching { setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, false) }
    }
    // tela apagou / app foi pro fundo: o Android manda "sumiu" (GONE/INVISIBLE). A página nunca fica sabendo.
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(View.VISIBLE) }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, View.VISIBLE) }
    override fun dispatchWindowVisibilityChanged(visibility: Int) { super.dispatchWindowVisibilityChanged(View.VISIBLE) }
    override fun onPause() { if (!YtPlayer.playing) super.onPause() }
}

/* Serviço de "tocando agora" pro player da web: notificação com controles, tela de bloqueio,
   e mantém o app acordado enquanto toca (é o que segura a música com a tela desligada). */
class WebPlayService : Service() {
    private var session: MediaSession? = null
    private var wake: PowerManager.WakeLock? = null
    private var wifi: android.net.wifi.WifiManager.WifiLock? = null
    // fone desconectado: pausa de verdade (e não deixa o "anti-pausa" voltar a tocar)
    private val noisy = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { h.post { if (YtPlayer.playing) YtPlayer.toggle() } }
    }
    // a cada 4s com a música tocando: mantém a página acordada
    private val keep = object : Runnable { override fun run() {
        if (YtPlayer.current != null) { YtPlayer.web?.let { it.onResume(); it.resumeTimers() }; YtPlayer.guard() }
        h.postDelayed(this, 4000)
    } }
    private val h = Handler(Looper.getMainLooper())
    private var art: Bitmap? = null; private var artFor = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "UncrownedWeb").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { h.post { if (!YtPlayer.playing) YtPlayer.toggle() } }
                override fun onPause() { h.post { if (YtPlayer.playing) YtPlayer.toggle() } }
                override fun onSkipToNext() { h.post { YtPlayer.next() } }
                override fun onSkipToPrevious() { h.post { YtPlayer.prev() } }
                override fun onSeekTo(pos: Long) { h.post { YtPlayer.seek(pos / 1000f) } }
            })
            isActive = true
        }
        wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "uncrowned:web").apply { setReferenceCounted(false) }
        wifi = runCatching { (applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager).createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "uncrowned:web").apply { setReferenceCounted(false) } }.getOrNull()
        runCatching { registerReceiver(noisy, android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)) }
        h.postDelayed(keep, 4000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "toggle" -> h.post { YtPlayer.toggle() }
            "next" -> h.post { YtPlayer.next() }
            "prev" -> h.post { YtPlayer.prev() }
            "stop" -> { h.post { YtPlayer.close() }; stopSelf(); return START_NOT_STICKY }
        }
        show()
        return START_NOT_STICKY
    }

    private fun pi(action: String, code: Int) = PendingIntent.getService(this, code, Intent(this, WebPlayService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun show() {
        val v = YtPlayer.current ?: run { stopSelf(); return }
        val playing = YtPlayer.playing
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("webplay", "Tocando agora", NotificationManager.IMPORTANCE_LOW))
        session?.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, v.title).putString(MediaMetadata.METADATA_KEY_ARTIST, v.channel)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, (YtPlayer.duration * 1000).toLong()).apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }.build())
        session?.setPlaybackState(PlaybackState.Builder().setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, (YtPlayer.time * 1000).toLong(), YtPlayer.speed)
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO).build())
        val open = PendingIntent.getActivity(this, 7, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "webplay") else Notification.Builder(this))
            .setSmallIcon(R.drawable.ic_note).setContentTitle(v.title).setContentText(v.channel).setContentIntent(open).setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setLargeIcon(art)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_previous), "Anterior", pi("prev", 1)).build())
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play), if (playing) "Pausar" else "Tocar", pi("toggle", 2)).build())
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_next), "Próxima", pi("next", 3)).build())
            .setDeleteIntent(pi("stop", 4))
            .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1, 2))
        val n = b.build()
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(77, n)
        }
        if (playing) { wake?.acquire(4 * 60 * 60 * 1000L); runCatching { wifi?.acquire() } }
        else { runCatching { wake?.release() }; runCatching { wifi?.release() }; if (YtPlayer.userPausedNow) stopForeground(STOP_FOREGROUND_DETACH) }
        // capa da música na notificação e na tela de bloqueio
        if (artFor != v.thumb && v.thumb.isNotBlank()) {
            artFor = v.thumb
            CoroutineScope(Dispatchers.IO).launch {
                val bmp = runCatching { (ImageLoader(this@WebPlayService).execute(ImageRequest.Builder(this@WebPlayService).data(v.thumb).allowHardware(false).size(512).build()) as? SuccessResult)?.drawable?.toBitmap() }.getOrNull()
                if (bmp != null) h.post { art = bmp; show() }
            }
        }
    }

    override fun onDestroy() { h.removeCallbacks(keep); runCatching { unregisterReceiver(noisy) }; runCatching { wake?.release() }; runCatching { wifi?.release() }; session?.release(); session = null; super.onDestroy() }

    companion object {
        private var lastKey = ""
        // chamado quando o player da web começa, pausa ou troca de música
        fun update(ctx: Context) {
            val v = YtPlayer.current
            val key = "${v?.id}|${YtPlayer.playing}"
            if (key == lastKey) return
            lastKey = key
            val i = Intent(ctx, WebPlayService::class.java)
            if (v == null) { ctx.stopService(i); return }
            runCatching { if (Build.VERSION.SDK_INT >= 26 && YtPlayer.playing) ctx.startForegroundService(i) else ctx.startService(i) }
        }
    }
}
