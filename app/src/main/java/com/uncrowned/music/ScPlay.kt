package com.uncrowned.music

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/* No celular as músicas online tocam no player do próprio app (o mesmo das offline):
   pega o áudio do SoundCloud e entrega pro ExoPlayer. Assim equalizador, velocidade com tom,
   K7/vinil, letra, notificação e tocar com a tela apagada funcionam igual às offline.
   Música que vem do YouTube Music (artista, álbum, paradas, playlists) é procurada no SoundCloud. */
object ScPlay {
    var loading by mutableStateOf<String?>(null); private set
    // mexe no player do app (setado pela tela principal)
    var setItems: ((List<MediaItem>) -> Unit)? = null
    var addItem: ((MediaItem) -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private suspend fun item(v: YtVideo): MediaItem? {
        val sc = if (v.src == "sc") v else ScMatch.find(v) ?: return null
        val st = SoundCloudApi.stream(sc.id) ?: return null
        val art = sc.thumb.ifBlank { st.art }.ifBlank { v.thumb }
        return MediaItem.Builder().setMediaId("sc:" + sc.id).setUri(st.url)
            .apply { if (st.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(st.url)).build())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(sc.title).setArtist(sc.channel).setGenre("SoundCloud")
                .setArtworkUri(art.takeIf { it.isNotBlank() }?.let(Uri::parse)).build())
            .build()
    }

    /* toca a lista a partir da música i: a primeira sai na hora, o resto vai entrando na fila em ordem */
    fun play(list: List<YtVideo>, i: Int) {
        job?.cancel()
        val rest = list.drop(i).take(100)
        if (rest.isEmpty()) return
        loading = rest.first().title
        job = scope.launch {
            var started = false
            val sem = Semaphore(3)
            val jobs = rest.map { v -> async { sem.withPermit { runCatching { item(v) }.getOrNull() } } }
            for ((k, d) in jobs.withIndex()) {
                val it = d.await()
                withContext(Dispatchers.Main) {
                    if (it == null) {
                        if (k == 0) runCatching { Toast.makeText(Sync.app, "\"${rest[0].title}\" não está no SoundCloud, indo pra próxima", Toast.LENGTH_SHORT).show() }
                    } else if (!started) { started = true; loading = null; setItems?.invoke(listOf(it)) }
                    else addItem?.invoke(it)
                }
            }
            withContext(Dispatchers.Main) {
                if (!started) { loading = null; runCatching { Toast.makeText(Sync.app, "Não achei essas músicas no SoundCloud", Toast.LENGTH_SHORT).show() } }
            }
        }
    }

    /* link venceu (o SoundCloud troca os links de tempos em tempos): pega um novo e continua do mesmo ponto */
    suspend fun refresh(path: String): MediaItem? = withContext(Dispatchers.IO) { runCatching { item(YtVideo(path, "", "", "", "sc")) }.getOrNull() }
}
