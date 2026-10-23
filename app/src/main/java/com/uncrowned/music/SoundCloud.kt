package com.uncrowned.music

import androidx.core.text.HtmlCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/* Busca do SoundCloud: usa a mesma busca interna do site (api-v2) com o client_id público
   que o próprio site carrega. Se falhar, cai pra página HTML simples. Quem toca é o player oficial. */
object SoundCloudApi {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36"
    private var clientId: String? = null

    private fun http(u: String): Pair<Int, String> {
        val c = URL(u).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
        c.connectTimeout = 10000; c.readTimeout = 10000
        return try {
            val code = c.responseCode
            code to ((if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "")
        } finally { c.disconnect() }
    }

    private fun findClientId(force: Boolean): String? {
        if (!force) (clientId ?: Prefs.sp.getString("scid", null))?.let { clientId = it; return it }
        val home = http("https://soundcloud.com/").second
        val scripts = Regex("""<script[^>]+src="(https://a-v2\.sndcdn\.com/assets/[^"]+\.js)"""").findAll(home).map { it.groupValues[1] }.toList()
        val re = Regex("""client_id\s*[:=]\s*"?([a-zA-Z0-9]{32})""")
        for (s in scripts.reversed()) {
            val id = runCatching { re.find(http(s).second)?.groupValues?.get(1) }.getOrNull()
            if (id != null) { clientId = id; Prefs.sp.edit().putString("scid", id).apply(); return id }
        }
        return null
    }

    private fun apiSearch(q: String, force: Boolean): List<YtVideo>? {
        val id = findClientId(force) ?: return null
        val (code, body) = http("https://api-v2.soundcloud.com/search/tracks?q=${URLEncoder.encode(q, "UTF-8")}&client_id=$id&limit=30&linked_partitioning=1")
        if (code == 401 || code == 403) return if (force) null else apiSearch(q, true)
        if (code !in 200..299) return null
        val col = JSONObject(body).optJSONArray("collection") ?: return emptyList()
        return (0 until col.length()).mapNotNull { i ->
            val t = col.getJSONObject(i)
            val link = t.optString("permalink_url").substringAfter("soundcloud.com/", "")
            if (link.isBlank()) return@mapNotNull null
            val user = t.optJSONObject("user")
            val art = t.optString("artwork_url").takeIf { it.isNotBlank() && it != "null" }
                ?: user?.optString("avatar_url").orEmpty()
            YtVideo(link, t.optString("title"), user?.optString("username").orEmpty(), art.replace("-large.", "-t500x500."), "sc")
        }
    }

    private fun htmlSearch(q: String): List<YtVideo> {
        val html = http("https://soundcloud.com/search/sounds?q=${URLEncoder.encode(q, "UTF-8")}").second
        return Regex("""<h2>\s*<a href="/([^"/]+)/([^"]+)"[^>]*>([^<]+)</a>\s*</h2>""").findAll(html).map { m ->
            val (user, track, title) = m.destructured
            YtVideo("$user/$track", HtmlCompat.fromHtml(title, HtmlCompat.FROM_HTML_MODE_LEGACY).toString(), user, "", "sc")
        }.filter { !it.id.contains("/sets/") }.distinctBy { it.id }.take(30).toList()
    }

    private fun mapTrack(t: JSONObject): YtVideo? {
        val link = t.optString("permalink_url").substringAfter("soundcloud.com/", ""); if (link.isBlank()) return null
        val user = t.optJSONObject("user")
        val art = t.optString("artwork_url").takeIf { it.isNotBlank() && it != "null" } ?: user?.optString("avatar_url").orEmpty()
        val artist = t.optJSONObject("publisher_metadata")?.optString("artist")?.takeIf { it.isNotBlank() } ?: user?.optString("username").orEmpty()
        return YtVideo(link, t.optString("title"), artist, art.replace("-large.", "-t500x500."), "sc")
    }

    /* mais tocadas da semana no SoundCloud (paradas oficiais; renova o código se vencer) */
    suspend fun charts(region: String): List<YtVideo> = withContext(Dispatchers.IO) {
        val reg = if (region == "ZZ") "" else "&region=soundcloud:regions:$region"
        val tries = listOf("top" to reg, "trending" to reg, "top" to "", "trending" to "")
        var id = runCatching { findClientId(false) }.getOrNull()
        var renewed = false
        for ((kind, r) in tries) {
            if (id == null) break
            val url = { cid: String -> "https://api-v2.soundcloud.com/charts?kind=$kind&genre=soundcloud:genres:all-music$r&high_tier_only=false&limit=50&client_id=$cid" }
            var (code, body) = runCatching { http(url(id!!)) }.getOrDefault(0 to "")
            if ((code == 401 || code == 403) && !renewed) { renewed = true; id = runCatching { findClientId(true) }.getOrNull() ?: break; val res = runCatching { http(url(id!!)) }.getOrDefault(0 to ""); code = res.first; body = res.second }
            if (code in 200..299) {
                val col = JSONObject(body).optJSONArray("collection") ?: continue
                val out = (0 until col.length()).mapNotNull { i -> val o = col.getJSONObject(i); mapTrack(o.optJSONObject("track") ?: o) }
                if (out.size >= 10) return@withContext out.distinctBy { it.id }
            }
        }
        emptyList()
    }

    /* faixas do SoundCloud com download liberado pelo próprio artista */
    suspend fun downloadables(q: String): List<Pair<YtVideo, Boolean>> = withContext(Dispatchers.IO) {
        runCatching {
            var id = findClientId(false) ?: return@runCatching emptyList()
            var (code, body) = http("https://api-v2.soundcloud.com/search/tracks?q=${URLEncoder.encode(q, "UTF-8")}&client_id=$id&limit=30")
            if (code == 401 || code == 403) { id = findClientId(true) ?: return@runCatching emptyList(); val r = http("https://api-v2.soundcloud.com/search/tracks?q=${URLEncoder.encode(q, "UTF-8")}&client_id=$id&limit=30"); code = r.first; body = r.second }
            if (code !in 200..299) return@runCatching emptyList()
            val col = JSONObject(body).optJSONArray("collection") ?: return@runCatching emptyList()
            (0 until col.length()).mapNotNull { i -> val o = col.getJSONObject(i); if (!o.optBoolean("downloadable")) null else mapTrack(o)?.let { it to true } }
        }.getOrDefault(emptyList())
    }

    /* link do arquivo original (só quando o artista liberou o download) */
    suspend fun downloadUrl(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val id = findClientId(false) ?: return@runCatching null
            val (c1, b1) = http("https://api-v2.soundcloud.com/resolve?url=${URLEncoder.encode("https://soundcloud.com/$path", "UTF-8")}&client_id=$id")
            if (c1 !in 200..299) return@runCatching null
            val tr = JSONObject(b1); if (!tr.optBoolean("downloadable")) return@runCatching null
            val (c2, b2) = http("https://api-v2.soundcloud.com/tracks/${tr.optLong("id")}/download?client_id=$id")
            if (c2 !in 200..299) null else JSONObject(b2).optString("redirectUri").ifBlank { null }
        }.getOrNull()
    }

    /* link do áudio pra tocar no player do app (com equalizador, velocidade, K7/vinil).
       Prefere o MP3 direto; se não tiver, usa o HLS. Faixa só com prévia de 30s (SNIP) não serve. */
    data class Stream(val url: String, val hls: Boolean, val duration: Long, val art: String)
    fun stream(path: String): Stream? = runCatching {
        var id = findClientId(false) ?: return null
        val res = { cid: String -> http("https://api-v2.soundcloud.com/resolve?url=${URLEncoder.encode("https://soundcloud.com/$path", "UTF-8")}&client_id=$cid") }
        var (c1, b1) = res(id)
        if (c1 == 401 || c1 == 403) { id = findClientId(true) ?: return null; val r = res(id); c1 = r.first; b1 = r.second }
        if (c1 !in 200..299) return null
        val tr = JSONObject(b1)
        if (tr.optString("kind") != "track" || tr.optString("policy") == "SNIP" || tr.optString("policy") == "BLOCK") return null
        val tcs = tr.optJSONObject("media")?.optJSONArray("transcodings") ?: return null
        val all = (0 until tcs.length()).map { tcs.getJSONObject(it) }
        fun proto(o: JSONObject) = o.optJSONObject("format")?.optString("protocol").orEmpty()
        fun mime(o: JSONObject) = o.optJSONObject("format")?.optString("mime_type").orEmpty()
        val pick = all.firstOrNull { proto(it) == "progressive" }
            ?: all.firstOrNull { proto(it) == "hls" && mime(it).contains("mp4") }
            ?: all.firstOrNull { proto(it) == "hls" && mime(it).contains("mpeg") } ?: return null
        val auth = tr.optString("track_authorization").takeIf { it.isNotBlank() }?.let { "&track_authorization=$it" } ?: ""
        val (c2, b2) = http("${pick.optString("url")}?client_id=$id$auth")
        if (c2 !in 200..299) return null
        val url = JSONObject(b2).optString("url").ifBlank { return null }
        Stream(url, proto(pick) == "hls", tr.optLong("duration"), tr.optString("artwork_url").replace("-large.", "-t500x500."))
    }.getOrNull()

    suspend fun search(q: String): Result<List<YtVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            val api = runCatching { apiSearch(q, false) }.getOrNull()
            (if (!api.isNullOrEmpty()) api else htmlSearch(q)).distinctBy { it.id }
                .ifEmpty { error("Nada encontrado no SoundCloud") }
        }
    }
}
