package com.uncrowned.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/* Busca online única: tudo ao mesmo tempo, na ordem SoundCloud → YouTube → Vimeo.
   As faixas que dá pra baixar (Audius / Internet Archive) vêm separadas. */
object OnlineSearch {
    data class Result(val stream: List<YtVideo>, val downloadable: List<Track>)

    suspend fun search(q: String): Result = coroutineScope {
        val sc = async { OtherApi.soundcloud(q).getOrDefault(emptyList()) }
        val yt = async { YtApi.search(q, YtStore.apiKey).getOrDefault(emptyList()) }
        val vm = async { if (YtStore.vimeoToken.isBlank()) emptyList() else OtherApi.vimeo(q, YtStore.vimeoToken).getOrDefault(emptyList()) }
        val au = async { Audius.search(q).getOrDefault(emptyList()) }
        val ia = async { Archive.search(q).getOrDefault(emptyList()) }
        // músicas vêm do SoundCloud; YouTube só entra se o SoundCloud não achar quase nada
        val scL = sc.await()
        Result(
            (scL.take(40) + (if (!YtStore.scFirst || scL.size < 5) yt.await().take(20) else emptyList()) + vm.await().take(10)).distinctBy { it.src + it.id },
            (au.await().filter { it.downloadable } + ia.await()).take(15)
        )
    }
}

/* Botão de informações: dados da faixa pelo catálogo público do iTunes (grátis, sem chave). */
data class SongInfo(
    val title: String, val artist: String, val album: String?, val year: String?,
    val genre: String?, val art: String?, val trackNumber: String?, val source: String
)

object SongInfoApi {
    private val cache = HashMap<String, SongInfo>()

    suspend fun lookup(title: String, artist: String, localAlbum: String?, localYear: Int?): SongInfo = withContext(Dispatchers.IO) {
        val key = "$artist|$title"
        cache[key]?.let { return@withContext it }
        val cleanTitle = title.replace(Regex("(?i)\\s*[\\(\\[].*?[\\)\\]]"), "").substringAfter(" - ").trim()
        val cleanArtist = if (title.contains(" - ") && (artist.isBlank() || artist.contains("topic", true))) title.substringBefore(" - ") else artist
        val found = runCatching {
            val term = URLEncoder.encode("$cleanArtist $cleanTitle", "UTF-8")
            val c = URL("https://itunes.apple.com/search?term=$term&entity=song&limit=5&country=BR").openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            val body = try { c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
            val arr = JSONObject(body).getJSONArray("results")
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .firstOrNull { it.optString("trackName").contains(cleanTitle.take(12), true) } ?: arr.optJSONObject(0)
        }.getOrNull()
        val info = if (found != null) SongInfo(
            found.optString("trackName", cleanTitle), found.optString("artistName", cleanArtist),
            found.optString("collectionName").ifBlank { localAlbum },
            found.optString("releaseDate").take(4).ifBlank { localYear?.takeIf { it > 0 }?.toString() },
            found.optString("primaryGenreName").ifBlank { null },
            found.optString("artworkUrl100").replace("100x100", "600x600").ifBlank { null },
            found.optInt("trackNumber").takeIf { it > 0 }?.let { "$it de ${found.optInt("trackCount")}" },
            "Apple Music / iTunes"
        ) else SongInfo(cleanTitle, cleanArtist, localAlbum, localYear?.takeIf { it > 0 }?.toString(), null, null, null, "Arquivo local")
        cache[key] = info
        info
    }
}
