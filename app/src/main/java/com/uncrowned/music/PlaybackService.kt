package com.uncrowned.music

import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var notif: DefaultMediaNotificationProvider

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        when {
            k == null -> {}
            k == "speed" || k == "pitch" -> applyParams()
            k == "skipsilence" -> player.skipSilenceEnabled = Prefs.skipSilence
            k == "eq" || k == "bass" || k == "virt" || k.startsWith("eq_") -> Effects.apply()
            k == "nicon" -> { applyIcon(); refresh() }
            k == "nb1" || k == "nb2" || k == "favs" || k == "seekstep" -> refresh()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        val sid = Util.generateAudioSessionIdV21(this)
        player.audioSessionId = sid
        Effects.attach(sid)
        player.skipSilenceEnabled = Prefs.skipSilence
        player.playbackParameters = PlaybackParameters(Prefs.speed, Prefs.pitch)
        Sleep.player = player
        player.addListener(object : Player.Listener {
            // se o Android trocar a sessão de áudio, religa o equalizador nela
            override fun onAudioSessionIdChanged(audioSessionId: Int) { Effects.attach(audioSessionId) }
        })

        player.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = refresh()
            override fun onRepeatModeChanged(repeatMode: Int) = refresh()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh()
        })

        notif = DefaultMediaNotificationProvider.Builder(this).setChannelName(R.string.app_name).build()
        applyIcon()
        setMediaNotificationProvider(notif)

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setCallback(Callback())
            .setSessionActivity(open)
            .build()
        refresh()
        Prefs.sp.registerOnSharedPreferenceChangeListener(prefListener)
        handler.postDelayed(watchdog, 1000)
    }

    /* Velocidade/tom: aplica com atraso (junta várias mudanças seguidas) e depois "reinicia" o
       processamento de áudio no mesmo ponto, o que evita o bug de ficar tudo mudo. */
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    // reconstrói todo o caminho do áudio (para, aplica, prepara de novo no mesmo ponto) — é o que acaba com o "mudo"
    private fun rebuild(p: PlaybackParameters) {
        if (player.mediaItemCount == 0) { player.playbackParameters = p; return }
        val idx = player.currentMediaItemIndex; val pos = player.currentPosition; val pwr = player.playWhenReady
        player.stop()
        player.playbackParameters = p
        player.prepare()
        player.seekTo(idx, pos)
        player.playWhenReady = pwr
    }
    private val applyRunnable = Runnable {
        val p = PlaybackParameters(Prefs.speed, Prefs.pitch)
        if (player.playbackParameters != p) rebuild(p)
    }
    // vigia: se está "tocando" mas o tempo não anda por ~3s, reconstrói o áudio sozinho
    private var lastPos = -1L; private var stuck = 0
    private val watchdog = object : Runnable {
        override fun run() {
            if (player.isPlaying) {
                val pos = player.currentPosition
                stuck = if (pos == lastPos) stuck + 1 else 0
                lastPos = pos
                if (stuck >= 3) { stuck = 0; rebuild(player.playbackParameters) }
            } else stuck = 0
            handler.postDelayed(this, 1000)
        }
    }
    private fun applyParams() { handler.removeCallbacks(applyRunnable); handler.postDelayed(applyRunnable, 250) }

    private fun applyIcon() = notif.setSmallIcon(
        when (Prefs.notifIcon) { "note" -> R.drawable.ic_note; "disc" -> R.drawable.ic_disc; else -> R.drawable.ic_crown }
    )

    private fun button(key: String): CommandButton? {
        val step = Prefs.seekStep
        val (name, icon) = when (key) {
            "shuffle" -> "Aleatório" to if (player.shuffleModeEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle_off
            "repeat" -> "Repetir" to when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
                Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_on
                else -> R.drawable.ic_repeat_off
            }
            "like" -> "Favoritar" to if (Prefs.isFav(player.currentMediaItem?.mediaId)) R.drawable.ic_heart_on else R.drawable.ic_heart_off
            "rew" -> "Voltar ${step}s" to R.drawable.ic_rewind
            "fwd" -> "Avançar ${step}s" to R.drawable.ic_forward
            "close" -> "Fechar" to R.drawable.ic_close
            else -> return null
        }
        return CommandButton.Builder()
            .setDisplayName(name)
            .setIconResId(icon)
            .setSessionCommand(SessionCommand(key, Bundle.EMPTY))
            .build()
    }

    private fun refresh() {
        val s = session ?: return
        s.setCustomLayout(listOfNotNull(button(Prefs.notifBtn1), button(Prefs.notifBtn2)))
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(s: MediaSession, c: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().apply {
                ALL.forEach { add(SessionCommand(it, Bundle.EMPTY)) }
            }.build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(s).setAvailableSessionCommands(cmds).build()
        }

        override fun onCustomCommand(
            s: MediaSession, c: MediaSession.ControllerInfo, cmd: SessionCommand, args: Bundle
        ): ListenableFuture<SessionResult> {
            val step = Prefs.seekStep * 1000L
            when (cmd.customAction) {
                "shuffle" -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                "repeat" -> player.repeatMode = (player.repeatMode + 1) % 3
                "like" -> Prefs.toggleFav(player.currentMediaItem?.mediaId)
                "rew" -> player.seekTo((player.currentPosition - step).coerceAtLeast(0))
                "fwd" -> player.seekTo(player.currentPosition + step)
                "close" -> { player.stop(); player.clearMediaItems() }
            }
            refresh()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            s: MediaSession, c: MediaSession.ControllerInfo, items: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            items.map { it.buildUpon().setUri(it.requestMetadata.mediaUri ?: it.localConfiguration?.uri).build() }.toMutableList()
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        Prefs.sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        handler.removeCallbacks(applyRunnable)
        handler.removeCallbacks(watchdog)
        Effects.release()
        Sleep.player = null
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    companion object { val ALL = listOf("shuffle", "repeat", "like", "rew", "fwd", "close") }
}
