package com.uncrowned.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/* YouTube Music (mesma lógica da versão de PC): paradas, playlists, busca de artistas/álbuns, páginas */
data class YtmList(val id: String, val title: String, val sub: String, val art: String, val shelf: String)
data class YtmArtistHit(val id: String, val name: String, val sub: String, val art: String)
data class YtmAlbumHit(val id: String, val title: String, val type: String, val artist: String, val year: String, val art: String)
data class YtmArtist(val name: String, val art: String, val subs: String, val top: List<YtVideo>, val albums: List<YtmList>, val singles: List<YtmList>, val videos: List<YtVideo>)
data class YtmAlbum(val title: String, val artist: String, val year: String, val type: String, val art: String, val tracks: List<YtVideo>)

object Ytm {
    private const val VER = "1.20250101.01.00"
    private val cache = HashMap<String, Pair<Long, Any>>()
    @Suppress("UNCHECKED_CAST")
    private fun <T> cached(k: String, ttl: Long, f: () -> T): T {
        val hit = cache[k]
        if (hit != null && System.currentTimeMillis() - hit.first < ttl) return hit.second as T
        val v = f()
        if (v != null && !(v is List<*> && v.isEmpty())) cache[k] = System.currentTimeMillis() to (v as Any)
        return v
    }

    private fun post(endpoint: String, body: JSONObject): JSONObject {
        body.put("context", JSONObject().put("client", JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", VER).put("hl", "pt").put("gl", "BR")))
        val c = URL("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Origin", "https://music.youtube.com"); c.setRequestProperty("Referer", "https://music.youtube.com/")
        c.setRequestProperty("X-Youtube-Client-Name", "67"); c.setRequestProperty("X-Youtube-Client-Version", VER)
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36")
        return try { c.outputStream.use { it.write(body.toString().toByteArray()) }; if (c.responseCode !in 200..299) error("YTM ${c.responseCode}"); JSONObject(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
    }
    private fun browse(id: String) = post("browse", JSONObject().put("browseId", id))

    private fun runs(o: JSONObject?): String {
        if (o == null) return ""
        o.optJSONArray("runs")?.let { r -> return (0 until r.length()).joinToString("") { r.getJSONObject(it).optString("text") } }
        return o.optString("simpleText")
    }
    private fun find(o: Any?, key: String, depth: Int = 0): Any? {
        if (depth > 14) return null
        when (o) {
            is JSONObject -> { if (o.has(key)) return o.get(key); for (k in o.keys()) find(o.get(k), key, depth + 1)?.let { return it } }
            is JSONArray -> for (i in 0 until o.length()) find(o.get(i), key, depth + 1)?.let { return it }
        }
        return null
    }
    private fun thumb(o: Any?): String { val t = find(o, "thumbnails") as? JSONArray ?: return ""; return if (t.length() == 0) "" else t.getJSONObject(t.length() - 1).optString("url").replace(Regex("=w\\d+-h\\d+"), "=w544-h544") }
    private fun pageType(o: Any?): String = ((find(o, "browseEndpointContextMusicConfig") as? JSONObject)?.optString("pageType")) ?: ""
    private fun yt(id: String, title: String, artist: String) = YtVideo(id, title, artist, "https://i.ytimg.com/vi/$id/hqdefault.jpg")

    /* lê músicas e playlists de qualquer página do YouTube Music */
    private fun parse(j: Any): Pair<List<Pair<YtVideo, String>>, List<YtmList>> {
        val songs = ArrayList<Pair<YtVideo, String>>(); val lists = ArrayList<YtmList>()
        fun walk(x: Any?, shelf: String) {
            when (x) {
                is JSONArray -> for (i in 0 until x.length()) walk(x.get(i), shelf)
                is JSONObject -> {
                    val sh = x.optJSONObject("musicCarouselShelfRenderer") ?: x.optJSONObject("musicShelfRenderer") ?: x.optJSONObject("musicPlaylistShelfRenderer")
                    if (sh != null) {
                        val t = runs(sh.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("title") ?: sh.optJSONObject("title")).ifBlank { shelf }
                        walk(sh.opt("contents"), t); return
                    }
                    x.optJSONObject("musicResponsiveListItemRenderer")?.let { r ->
                        val fc = r.optJSONArray("flexColumns"); val cols = (0 until (fc?.length() ?: 0)).map { runs(fc!!.getJSONObject(it).optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")) }
                        val id = r.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() } ?: (find(r, "watchEndpoint") as? JSONObject)?.optString("videoId")
                        if (!id.isNullOrBlank() && cols.isNotEmpty() && cols[0].isNotBlank()) songs += yt(id, cols[0], cols.getOrElse(1) { "" }.split(" • ").first()) to shelf
                        else (find(r, "browseEndpoint") as? JSONObject)?.optString("browseId")?.takeIf { Regex("^(VL|PL|RD|OL)").containsMatchIn(it) }?.let { lists += YtmList(it, cols.getOrElse(0) { "" }, cols.getOrElse(1) { "" }, thumb(r.opt("thumbnail")), shelf) }
                        return
                    }
                    x.optJSONObject("musicTwoRowItemRenderer")?.let { r ->
                        val ne = r.optJSONObject("navigationEndpoint"); val title = runs(r.optJSONObject("title")); val sub = runs(r.optJSONObject("subtitle"))
                        val w = ne?.optJSONObject("watchEndpoint")?.optString("videoId"); val b = ne?.optJSONObject("browseEndpoint")?.optString("browseId")
                        if (!w.isNullOrBlank()) songs += yt(w, title, sub.split(" • ").first()) to shelf
                        else if (!b.isNullOrBlank()) lists += YtmList(b, title, sub, thumb(r.opt("thumbnailRenderer")), shelf)
                        return
                    }
                    for (k in x.keys()) walk(x.get(k), shelf)
                }
            }
        }
        walk(j, "")
        return songs.distinctBy { it.first.id } to lists.distinctBy { it.id }
    }

    suspend fun playlist(id: String): List<YtVideo> = withContext(Dispatchers.IO) {
        runCatching { cached("pl|$id", 3 * 3600_000L) { parse(browse(if (Regex("^(VL|MPRE|OLAK)").containsMatchIn(id)) id else "VL$id")).first.map { it.first } } }.getOrDefault(emptyList())
    }
    data class Charts(val title: String, val top: List<YtVideo>, val videos: List<YtVideo>, val lists: List<YtmList>)
    suspend fun charts(gl: String): Charts = withContext(Dispatchers.IO) {
        runCatching {
            cached("charts|$gl", 3 * 3600_000L) {
                val (songs, lists) = parse(post("browse", JSONObject().put("browseId", "FEmusic_charts").put("formData", JSONObject().put("selectedValues", JSONArray().put(gl)))))
                val pl = lists.firstOrNull { Regex("(?i)top|100|mais").containsMatchIn(it.title) && Regex("(?i)m[uú]sica|song|faixa").containsMatchIn(it.title + " " + it.shelf) } ?: lists.firstOrNull { Regex("(?i)top|100").containsMatchIn(it.title) } ?: lists.firstOrNull()
                var top = pl?.let { runCatching { parse(browse(if (it.id.startsWith("VL")) it.id else "VL${it.id}")).first.map { s -> s.first } }.getOrDefault(emptyList()) } ?: emptyList()
                if (top.isEmpty()) top = songs.map { it.first }
                Charts(pl?.title ?: "Mais ouvidas da semana", top.take(50), songs.filter { Regex("(?i)v[ií]deo").containsMatchIn(it.second) }.map { it.first }.take(20), lists.filter { it != pl })
            }
        }.getOrDefault(Charts("", emptyList(), emptyList(), emptyList()))
    }
    /* playlists mais populares (página inicial + paradas), por prateleira */
    suspend fun popularLists(gl: String): List<Pair<String, List<YtmList>>> = withContext(Dispatchers.IO) {
        runCatching {
            cached("lists|$gl", 6 * 3600_000L) {
                val ok = Regex("^(VL)?(PL|RDCLAK|OLAK)")
                val home = runCatching { parse(browse("FEmusic_home")).second }.getOrDefault(emptyList())
                val ch = runCatching { parse(post("browse", JSONObject().put("browseId", "FEmusic_charts").put("formData", JSONObject().put("selectedValues", JSONArray().put(gl))))).second }.getOrDefault(emptyList())
                val groups = LinkedHashMap<String, MutableList<YtmList>>()
                ch.filter { ok.containsMatchIn(it.id) }.forEach { groups.getOrPut("Paradas do YouTube Music") { mutableListOf() } += it }
                home.filter { ok.containsMatchIn(it.id) }.forEach { groups.getOrPut(it.shelf.ifBlank { "Playlists em destaque" }) { mutableListOf() } += it }
                val seen = HashSet<String>()
                groups.map { (k, v) -> k to v.filter { seen.add(it.id) }.take(20) }.filter { it.second.size >= 2 }.take(6)
            }
        }.getOrDefault(emptyList())
    }

    /* playlist inteira (segue as páginas), com nome e capa — usado no Importar */
    data class Full(val title: String, val cover: String, val tracks: List<YtVideo>)
    private fun contTok(j: Any?): String? = (find(j, "continuationCommand") as? JSONObject)?.optString("token")?.ifBlank { null }
        ?: (find(j, "nextContinuationData") as? JSONObject)?.optString("continuation")?.ifBlank { null }
    suspend fun fullPlaylist(id: String): Full = withContext(Dispatchers.IO) {
        val bid = if (Regex("^(VL|MPRE|OLAK)").containsMatchIn(id)) id else "VL$id"
        val j = browse(bid)
        val head = (find(j, "musicResponsiveHeaderRenderer") ?: find(j, "musicDetailHeaderRenderer") ?: find(j, "musicEditablePlaylistDetailHeaderRenderer")) as? JSONObject
        val title = runs(head?.optJSONObject("title")).ifBlank { runs(find(head, "title") as? JSONObject) }
        val cover = thumb(head?.opt("thumbnail") ?: head)
        val songs = ArrayList(parse(j).first.map { it.first })
        var tok = contTok(j); var n = 0
        while (tok != null && n++ < 40) {
            val k = runCatching { post("browse", JSONObject().put("continuation", tok)) }.getOrNull() ?: break
            val more = parse(k).first.map { it.first }; if (more.isEmpty()) break
            songs += more; tok = contTok(k)
        }
        Full(title, cover, songs.distinctBy { it.id })
    }
    /* Mix do YouTube (lista RD...): o YouTube monta na hora a partir de um vídeo */
    suspend fun mix(videoId: String): List<YtVideo> = withContext(Dispatchers.IO) {
        runCatching {
            val ver = "2.20250101.00.00"
            val body = JSONObject().put("context", JSONObject().put("client", JSONObject().put("clientName", "WEB").put("clientVersion", ver).put("hl", "pt").put("gl", "BR")))
                .put("videoId", videoId).put("playlistId", "RD$videoId")
            val c = URL("https://www.youtube.com/youtubei/v1/next?prettyPrint=false").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 10000; c.readTimeout = 15000
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Origin", "https://www.youtube.com")
            c.setRequestProperty("X-Youtube-Client-Name", "1"); c.setRequestProperty("X-Youtube-Client-Version", ver)
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Safari/537.36")
            val j = try { c.outputStream.use { it.write(body.toString().toByteArray()) }; JSONObject(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
            val out = ArrayList<YtVideo>()
            fun walk(x: Any?) {
                when (x) {
                    is JSONArray -> for (i in 0 until x.length()) walk(x.get(i))
                    is JSONObject -> {
                        val v = x.optJSONObject("playlistPanelVideoRenderer")
                        if (v != null) { val id = v.optString("videoId"); if (id.isNotBlank()) out += YtVideo(id, runs(v.optJSONObject("title")), runs(v.optJSONObject("longBylineText") ?: v.optJSONObject("shortBylineText")).replace(Regex(" - Topic$"), ""), "https://i.ytimg.com/vi/$id/hqdefault.jpg") }
                        else for (k in x.keys()) walk(x.get(k))
                    }
                }
            }
            walk(j); out.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    data class Search(val artists: List<YtmArtistHit>, val albums: List<YtmAlbumHit>, val songs: List<YtVideo>)
    suspend fun search(q: String): Search = withContext(Dispatchers.IO) {
        runCatching {
            cached("s|${q.lowercase()}", 15 * 60_000L) {
                val j = post("search", JSONObject().put("query", q))
                val ar = ArrayList<YtmArtistHit>(); val al = ArrayList<YtmAlbumHit>(); val so = ArrayList<YtVideo>()
                fun walk(x: Any?) {
                    when (x) {
                        is JSONArray -> for (i in 0 until x.length()) walk(x.get(i))
                        is JSONObject -> {
                            val card = x.optJSONObject("musicCardShelfRenderer"); val r = x.optJSONObject("musicResponsiveListItemRenderer") ?: card
                            if (r != null) {
                                val cols = if (card != null) listOf(runs(r.optJSONObject("title")), runs(r.optJSONObject("subtitle"))) else { val fc = r.optJSONArray("flexColumns"); (0 until (fc?.length() ?: 0)).map { runs(fc!!.getJSONObject(it).optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")) } }
                                val nav = if (card != null) r.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint") else r.optJSONObject("navigationEndpoint")
                                val sid = r.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() } ?: nav?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.isNotBlank() }
                                val be = nav?.optJSONObject("browseEndpoint"); val pt = if (be != null) pageType(be) else ""
                                val c1 = cols.getOrElse(1) { "" }.split(" • ")
                                when {
                                    // artista: pula o tipo ("Música"), a duração ("2:25") e contagens ("1 mi de visualizações")
                                    sid != null && cols.firstOrNull().orEmpty().isNotBlank() -> so += yt(sid, cols[0], c1.map { it.trim() }.firstOrNull { s -> s.isNotBlank() && !Regex("(?i)^(m[uú]sica|song|v[ií]deo|video|single|ep|[aá]lbum)$").matches(s) && !Regex("^\\d{1,2}(:\\d{2}){1,2}$").matches(s) && !Regex("(?i)(visualiza|views|plays|reprodu|ouvintes)").containsMatchIn(s) } ?: c1.getOrElse(0) { "" })
                                    be != null && pt.contains("ARTIST") -> ar += YtmArtistHit(be.optString("browseId"), cols.getOrElse(0) { "" }, c1.drop(1).joinToString(" • ").ifBlank { "Artista" }, thumb(r.opt("thumbnail") ?: r))
                                    be != null && pt.contains("ALBUM") -> al += YtmAlbumHit(be.optString("browseId"), cols.getOrElse(0) { "" }, c1.getOrElse(0) { "Álbum" }, c1.getOrElse(1) { "" }, c1.firstOrNull { Regex("^\\d{4}$").matches(it) } ?: "", thumb(r.opt("thumbnail") ?: r))
                                }
                                if (card != null) walk(r.opt("contents"))
                                return
                            }
                            for (k in x.keys()) walk(x.get(k))
                        }
                    }
                }
                walk(j)
                Search(ar.distinctBy { it.id }.take(10), al.distinctBy { it.id }.take(12), so.distinctBy { it.id }.take(10))
            }
        }.getOrDefault(Search(emptyList(), emptyList(), emptyList()))
    }

    suspend fun artist(id: String): YtmArtist? = withContext(Dispatchers.IO) {
        runCatching {
            cached("a|$id", 3 * 3600_000L) {
                val j = browse(id)
                val head = (find(j, "musicImmersiveHeaderRenderer") ?: find(j, "musicVisualHeaderRenderer") ?: find(j, "musicHeaderRenderer")) as? JSONObject ?: JSONObject()
                val name = runs(head.optJSONObject("title"))
                val art = thumb(head.opt("thumbnail") ?: head.opt("foregroundThumbnail") ?: head)
                val subs = runs(find(head.opt("subscriptionButton"), "subscriberCountText") as? JSONObject)
                data class Sh(val title: String, val more: String?, val songs: List<YtVideo>, val items: List<YtmList>)
                val shelves = ArrayList<Sh>()
                fun walk(x: Any?) {
                    when (x) {
                        is JSONArray -> for (i in 0 until x.length()) walk(x.get(i))
                        is JSONObject -> {
                            val sh = x.optJSONObject("musicShelfRenderer") ?: x.optJSONObject("musicCarouselShelfRenderer")
                            if (sh != null) {
                                val hdr = sh.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                                val title = runs(hdr?.optJSONObject("title") ?: sh.optJSONObject("title"))
                                val more = ((find(sh.opt("bottomEndpoint") ?: hdr?.opt("moreContentButton"), "browseEndpoint")) as? JSONObject)?.optString("browseId")
                                val p = parse(JSONObject().put("contents", sh.opt("contents") ?: JSONArray()))
                                shelves += Sh(title, more, p.first.map { it.first.copy(channel = it.first.channel.ifBlank { name }) }, p.second)
                                return
                            }
                            for (k in x.keys()) walk(x.get(k))
                        }
                    }
                }
                walk(j)
                val songShelf = shelves.firstOrNull { it.songs.isNotEmpty() && !Regex("(?i)v[ií]deo").containsMatchIn(it.title) } ?: shelves.firstOrNull { it.songs.isNotEmpty() }
                var top = songShelf?.songs ?: emptyList()
                songShelf?.more?.let { m -> val all = runCatching { parse(browse(if (m.startsWith("VL")) m else "VL$m")).first.map { it.first } }.getOrDefault(emptyList()); if (all.size > top.size) top = all }
                fun pick(re: Regex) = shelves.filter { re.containsMatchIn(it.title) }.flatMap { it.items }
                YtmArtist(name, art, subs, top.take(50), pick(Regex("(?i)[áa]lbu|album")), pick(Regex("(?i)single|ep")), shelves.filter { Regex("(?i)v[ií]deo").containsMatchIn(it.title) }.flatMap { it.songs }.take(12))
            }
        }.getOrNull()
    }

    suspend fun album(id: String): YtmAlbum? = withContext(Dispatchers.IO) {
        runCatching {
            cached("al|$id", 6 * 3600_000L) {
                val j = browse(id)
                val head = (find(j, "musicResponsiveHeaderRenderer") ?: find(j, "musicDetailHeaderRenderer")) as? JSONObject ?: JSONObject()
                val title = runs(head.optJSONObject("title")); val sub = runs(head.optJSONObject("subtitle")); val strap = runs(head.optJSONObject("straplineTextOne"))
                val artist = strap.ifBlank { sub.split(" • ").getOrElse(1) { "" } }
                val art = thumb(head.opt("thumbnail") ?: head)
                val tracks = parse(j).first.map { (v, _) -> v.copy(channel = v.channel.takeIf { it.isNotBlank() && !it[0].isDigit() } ?: artist, thumb = art.ifBlank { v.thumb }) }
                YtmAlbum(title, artist, Regex("\\b(19|20)\\d{2}\\b").find(sub)?.value ?: "", sub.split(" • ").firstOrNull() ?: "Álbum", art, tracks)
            }
        }.getOrNull()
    }
}
