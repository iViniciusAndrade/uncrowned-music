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

    suspend fun search(q: String): Result<List<YtVideo>> = withContext(Dispatchers.IO) {
        runCatching {
            val api = runCatching { apiSearch(q, false) }.getOrNull()
            (if (!api.isNullOrEmpty()) api else htmlSearch(q)).distinctBy { it.id }
                .ifEmpty { error("Nada encontrado no SoundCloud") }
        }
    }
}
