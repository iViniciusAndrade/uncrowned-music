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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* No celular as músicas online tocam pelo SoundCloud, numa fila só:
   - faixa com áudio aberto: toca no player do app (efeitos, velocidade, notificação, tela apagada)
   - faixa protegida (selo/gravadora): toca no player oficial do SoundCloud, dentro do app
   - faixa que não existe lá (ou só tem prévia de 30s): pula pra próxima
   Música que vem do YouTube Music (artista, álbum, paradas, playlists) é procurada no SoundCloud. */
object ScPlay {
    var loading by mutableStateOf<String?>(null); private set
    var active by mutableStateOf(false); private set
    private var list: List<YtVideo> = emptyList()
    var pos = -1; private set
    // ganchos (setados pela tela principal)
    var exoSet: ((List<MediaItem>) -> Unit)? = null
    var exoAdd: ((MediaItem) -> Unit)? = null
    var webPlay: ((YtVideo) -> Unit)? = null
    private val idxOf = HashMap<String, Int>()
    private var exoLast = -1
    private var gen = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null; private var pre: Job? = null

    private sealed class R { class Native(val item: MediaItem) : R(); class Web(val v: YtVideo) : R(); class None(val why: String) : R() }

    private fun resolve(v: YtVideo, sc: YtVideo?): R {
        sc ?: return R.None("não achei no SoundCloud")
        val st = SoundCloudApi.stream(sc.id) ?: return R.None("no SoundCloud só tem a prévia de 30s")
        val art = sc.thumb.ifBlank { st.art }.ifBlank { v.thumb }
        if (st.web) return R.Web(sc.copy(thumb = art))
        val item = MediaItem.Builder().setMediaId("sc:" + sc.id).setUri(st.url)
            .apply { if (st.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(st.url)).build())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(sc.title).setArtist(sc.channel).setGenre("SoundCloud")
                .setArtworkUri(art.takeIf { it.isNotBlank() }?.let(Uri::parse)).build())
            .build()
        return R.Native(item)
    }
    /* tenta as melhores versões encontradas, uma por uma, até achar uma que toque inteira */
    private suspend fun resolve(v: YtVideo): R = runCatching {
        if (v.src == "sc") return@runCatching resolve(v, v)
        // "Mostrar vídeo" ligado: toca o vídeo do YouTube mesmo
        if (v.src == "yt" && YtStore.showVideo) return@runCatching R.Web(v)
        val cands = ScMatch.candidates(v)
        // não existe no SoundCloud: toca pelo player oficial do YouTube em vez de pular
        if (cands.isEmpty() || cands.all { it.first.policy == "SNIP" }) return@runCatching if (v.src == "yt") R.Web(v) else R.None("não achei no SoundCloud")
        var last: R = R.None("no SoundCloud só tem a prévia de 30s (precisa do SoundCloud Go+)")
        for ((h, _) in cands.filter { it.first.policy != "SNIP" }.take(4)) {
            val r = resolve(v, YtVideo(h.path, h.title, h.artist, h.art, "sc"))
            if (r !is R.None) return@runCatching r
            last = r
        }
        if (v.src == "yt") R.Web(v) else last
    }.getOrDefault(R.None("sem conexão com o SoundCloud"))

    private fun toast(t: String) { runCatching { Toast.makeText(Sync.app, t, Toast.LENGTH_SHORT).show() } }

    fun play(l: List<YtVideo>, i: Int) { list = l.take(200); active = true; startAt(i) }
    fun stop() { active = false; gen++; job?.cancel(); pre?.cancel(); loading = null }

    /* começa na música i; se ela não estiver no SoundCloud, segue pra próxima que estiver */
    fun startAt(i: Int) {
        val g = ++gen; job?.cancel(); pre?.cancel()
        if (i !in list.indices) { loading = null; return }
        loading = list[i].title
        job = scope.launch {
            var k = i; var warned = false
            while (k < list.size) {
                val r = resolve(list[k])
                if (g != gen) return@launch
                when (r) {
                    is R.Native -> { withContext(Dispatchers.Main) { if (g != gen) return@withContext; loading = null; pos = k; idxOf.clear(); idxOf[r.item.mediaId] = k; exoLast = k; exoSet?.invoke(listOf(r.item)) }; prefetch(g, k + 1); return@launch }
                    is R.Web -> { withContext(Dispatchers.Main) { if (g != gen) return@withContext; loading = null; pos = k; exoLast = k; webPlay?.invoke(r.v) }; return@launch }
                    is R.None -> { if (!warned) { warned = true; val t = list[k].title; val why = r.why; withContext(Dispatchers.Main) { toast("\"$t\": $why. Indo pra próxima") } }
                        k++; if (k < list.size) withContext(Dispatchers.Main) { loading = list[k].title } }
                }
            }
            withContext(Dispatchers.Main) { if (g == gen) { loading = null; toast("Não achei as próximas no SoundCloud") } }
        }
    }

    /* põe as próximas na fila do player do app (pra próxima, notificação e tela apagada funcionarem); para na primeira protegida */
    private fun prefetch(g: Int, from: Int) {
        pre = scope.launch {
            var k = from; var added = 0
            while (k < list.size && added < 25) {
                val r = resolve(list[k])
                if (g != gen) return@launch
                when (r) {
                    is R.Native -> { val kk = k; withContext(Dispatchers.Main) { if (g == gen) { idxOf[r.item.mediaId] = kk; exoLast = kk; exoAdd?.invoke(r.item) } }; added++ }
                    is R.Web -> return@launch
                    is R.None -> {}
                }
                k++
            }
        }
    }

    fun onExoItem(mediaId: String?) { if (mediaId != null) idxOf[mediaId]?.let { pos = it } }
    // a fila do player do app acabou: continua depois da última que entrou nela
    fun onExoEnded() { if (active) startAt(exoLast + 1) }
    fun onWebEnded() { if (active) startAt(pos + 1) }
    fun next() { if (active) startAt(pos + 1) }
    fun prev() { if (active && pos > 0) startAt(pos - 1) }
    fun hasNext() = active && pos < list.size - 1

    /* link venceu (o SoundCloud troca os links de tempos em tempos): pega um novo e continua do mesmo ponto */
    suspend fun refresh(path: String): MediaItem? = withContext(Dispatchers.IO) {
        (runCatching { resolve(YtVideo(path, "", "", "", "sc"), YtVideo(path, "", "", "", "sc")) }.getOrNull() as? R.Native)?.item
    }
}
