package com.uncrowned.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import kotlin.random.Random

/* ---------- Descobertas da Semana v3 (mesma lógica da versão de PC) ----------
   1) monta o perfil do seu gosto: o que você mais ouve (tempo ouvido), favorita, guarda em playlist, e o que você pula
   2) ~60% artistas NOVOS parecidos com os seus (Deezer diz quem é parecido; quanto mais dos seus apontam pro mesmo, mais forte)
   3) ~25% achados nos Mixes do YouTube das suas mais tocadas (só de artistas que você ainda não ouve)
   4) ~15% "lado B" de quem você já curte (músicas deles que você nunca ouviu)
   Nada que você já ouviu entra, no máximo 2 por artista, e cada música vem com o motivo. Renova toda segunda. */
data class WeeklyItem(val v: YtVideo, val why: String)
data class WeeklyArtist(val name: String, val pic: String, val because: String)

object Weekly {
    private fun n(x: String) = Normalizer.normalize(x.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace(Regex("[^a-z0-9]+"), " ").trim()
    fun cleanArtist(a: String) = a.replace(Regex("(?i)\\s*-\\s*topic$"), "").replace(Regex("(?i)vevo$"), "")
        .replace(Regex("(?i)\\s*\\(?(official|oficial)\\)?\\s*$"), "").split(Regex("(?i)\\s*(?:,|&| feat\\.?| ft\\.?| x |/)\\s*")).first().trim()
    private fun tKey(artist: String, title: String) = n(cleanArtist(artist)) + "|" + n(title)

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 12000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
        return try { if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}"); c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /* ---------- Deezer (grátis, sem chave): quem é parecido com quem ---------- */
    data class DzArtist(val id: Long, val name: String, val pic: String, val fans: Int)
    data class DzTrack(val title: String, val artist: String, val dur: Int, val art: String)
    private val artistCache = java.util.concurrent.ConcurrentHashMap<String, DzArtist?>()
    private fun dzArtist(name: String): DzArtist? {
        val k = name.lowercase(); if (artistCache.containsKey(k)) return artistCache[k]
        val a = runCatching {
            val d = JSONObject(get("https://api.deezer.com/search/artist?q=${enc(name)}&limit=3")).optJSONArray("data") ?: JSONArray()
            val l = (0 until d.length()).map { d.getJSONObject(it) }
            (l.firstOrNull { it.optString("name").equals(name, true) } ?: l.firstOrNull())?.let { DzArtist(it.optLong("id"), it.optString("name"), it.optString("picture_medium"), it.optInt("nb_fan")) }
        }.getOrNull()
        if (artistCache.size > 600) artistCache.clear()
        artistCache[k] = a; return a
    }
    private fun dzRelated(name: String): List<DzArtist> {
        val a = dzArtist(name) ?: return emptyList()
        return runCatching {
            val d = JSONObject(get("https://api.deezer.com/artist/${a.id}/related?limit=22")).optJSONArray("data") ?: JSONArray()
            (0 until d.length()).map { d.getJSONObject(it) }.map { DzArtist(it.optLong("id"), it.optString("name"), it.optString("picture_medium"), it.optInt("nb_fan")) }
        }.getOrDefault(emptyList())
    }
    private fun dzTop(id: Long, limit: Int): List<DzTrack> = runCatching {
        val d = JSONObject(get("https://api.deezer.com/artist/$id/top?limit=$limit")).optJSONArray("data") ?: JSONArray()
        (0 until d.length()).map { d.getJSONObject(it) }.map { DzTrack(it.optString("title_short").ifBlank { it.optString("title") }, it.optJSONObject("artist")?.optString("name") ?: "", it.optInt("duration"), it.optJSONObject("album")?.optString("cover_xl") ?: "") }
    }.getOrDefault(emptyList())

    /* acha a música no YouTube: primeiro o YouTube Music (versão de áudio oficial), depois a busca normal */
    private suspend fun toYt(t: DzTrack): YtVideo? {
        val want = n(t.title).split(" ").take(3).joinToString(" ")
        val a0 = n(t.artist).split(" ").firstOrNull().orEmpty()
        Ytm.search("${t.artist} ${t.title}").songs.firstOrNull { n(it.title).contains(want) && (a0.isBlank() || n(it.channel).contains(a0)) }
            ?.let { return it.copy(title = t.title, channel = t.artist) }
        return YtApi.search("${t.artist} ${t.title}", YtStore.apiKey).getOrDefault(emptyList())
            .firstOrNull { n(it.title).contains(want) }?.copy(channel = t.artist)
    }

    private class Cand(val a: DzArtist, var score: Double, var hits: Int, val because: MutableList<String>)

    /* ---------- perfil do gosto ---------- */
    data class Profile(val seeds: List<Pair<String, Double>>, val known: Set<String>, val dislikes: Set<String>, val heardIds: Set<String>, val heardT: Set<String>, val mixFrom: List<String>)
    fun profile(): Profile {
        val w = HashMap<String, Double>()
        fun add(a: String, v: Double) { val c = cleanArtist(a); if (c.isBlank() || Regex("(?i)desconhecido|unknown|<unknown>|youtube").containsMatchIn(c)) return; w[c] = (w[c] ?: 0.0) + v }
        val year = runCatching { Listen.stats("year") }.getOrNull(); val month = runCatching { Listen.stats("month") }.getOrNull()
        year?.artists?.forEach { (a, sec, _) -> add(a, sec / 900.0) }
        month?.artists?.forEach { (a, sec, _) -> add(a, sec / 450.0) }
        YtStore.favs().forEach { add(it.channel, 4.0) }
        YtStore.history().forEach { add(it.channel, 1.0) }
        runCatching { OPlaylists.list().forEach { p -> p.items.forEach { add(it.channel, 1.2) } } }
        Prefs.favs().forEach { id -> Library.byId(id)?.let { add(it.artist, 3.0) } }
        Taste.artists().forEach { (a, v) -> add(a, v * 0.6) }
        val list = w.entries.sortedByDescending { it.value }.map { it.key to it.value }
        val all = runCatching { Listen.stats("all").songs }.getOrDefault(emptyList())
        val heardIds = HashSet<String>(); val heardT = HashSet<String>()
        all.forEach { (t, _) -> heardIds += "${t.src}:${t.id}"; heardT += tKey(t.artist, t.title) }
        (YtStore.history() + YtStore.favs()).forEach { heardIds += "${it.src}:${it.id}"; heardT += tKey(it.channel, it.title) }
        // pontos de partida do Mix: as mais ouvidas do YouTube
        val mixFrom = all.filter { it.first.src == "yt" }.take(15).map { it.first.id }.ifEmpty { YtStore.history().filter { it.src == "yt" }.take(10).map { it.id } }
        return Profile(list.filter { it.second > 0.8 }.take(12), list.filter { it.second >= 1.5 }.map { n(it.first) }.toSet(),
            Taste.dislikes().map { n(cleanArtist(it)) }.toSet(), heardIds, heardT, mixFrom)
    }

    /* ---------- cache da semana ---------- */
    data class Result(val items: List<WeeklyItem>, val artists: List<WeeklyArtist>)
    private fun load(): Pair<JSONObject, Result>? = runCatching {
        val o = JSONObject(Prefs.sp.getString("weekly3", "{}") ?: "{}"); if (!o.has("w")) return null
        val a = o.getJSONArray("i"); val r = o.optJSONArray("a") ?: JSONArray()
        o to Result(List(a.length()) { a.getJSONObject(it).let { x -> WeeklyItem(YtVideo.from(x.getJSONObject("v")), x.optString("why")) } },
            List(r.length()) { r.getJSONObject(it).let { x -> WeeklyArtist(x.optString("n"), x.optString("p"), x.optString("b")) } })
    }.getOrNull()
    private fun store(w: Int, r: Result) = Prefs.sp.edit().putString("weekly3", JSONObject().put("w", w).put("at", System.currentTimeMillis())
        .put("i", JSONArray(r.items.map { JSONObject().put("v", it.v.json()).put("why", it.why) }))
        .put("a", JSONArray(r.artists.map { JSONObject().put("n", it.name).put("p", it.pic).put("b", it.because) }))).apply()
    fun cached(): Result? = load()?.second

    suspend fun get(force: Boolean = false): Result = withContext(Dispatchers.IO) {
        val w = Taste.weekKey()
        load()?.let { (o, r) -> if (!force && o.optInt("w") == w && (r.items.size >= 12 || System.currentTimeMillis() - o.optLong("at") < 3600_000L)) return@withContext r }
        val salt = if (force) Random.nextInt(10000) else 0
        val r = runCatching { build(w + salt) }.getOrDefault(Result(emptyList(), emptyList()))
        // quase sem histórico ainda: completa com o que está em alta nos estilos que você escolheu
        val items = if (r.items.size >= 10) r.items else r.items + fallback(w + salt, 30 - r.items.size)
        Result(items, r.artists).also { if (it.items.isNotEmpty()) store(w, it) }
    }

    private suspend fun fallback(seed: Int, n: Int): List<WeeklyItem> {
        val genres = Taste.genres().take(3).map { it.first }.ifEmpty { listOf("Pop", "Hip-Hop/Rap", "Electronic") }
        val pool = genres.flatMap { g -> Ytm.search("$g em alta").songs.take(8).map { WeeklyItem(it, "Em alta em $g") } }
        return pool.distinctBy { it.v.id }.shuffled(Random(seed)).take(n)
    }

    private suspend fun build(seed: Int): Result = coroutineScope {
        val p = profile()
        if (p.seeds.isEmpty()) return@coroutineScope Result(emptyList(), emptyList())
        val rnd = Random(seed)
        val gate = Semaphore(4)
        fun fresh(v: YtVideo) = "${v.src}:${v.id}" !in p.heardIds && tKey(v.channel, v.title) !in p.heardT && n(cleanArtist(v.channel)) !in p.dislikes

        // 1) artistas parecidos, pontuados
        val rel = p.seeds.map { s -> async(Dispatchers.IO) { gate.withPermit { s to dzRelated(s.first) } } }.awaitAll()
        val cand = HashMap<Long, Cand>()
        rel.forEach { (s, list) -> list.forEachIndexed { r, a ->
            val nm = n(a.name); if (nm in p.known || nm in p.dislikes) return@forEachIndexed
            val c = cand.getOrPut(a.id) { Cand(a, 0.0, 0, mutableListOf()) }
            c.score += s.second * (1 - r / 22.0); c.hits++
            if (c.because.size < 2 && s.first !in c.because) c.because += s.first
        } }
        val artists = cand.values.map { c ->
            val pop = when { c.a.fans > 8_000_000 -> 0.8; c.a.fans < 3000 -> 0.75; else -> 1.0 }
            c to c.score * (1 + 0.45 * (c.hits - 1)) * pop * (0.8 + rnd.nextDouble() * 0.4)
        }.sortedByDescending { it.second }.take(18).map { it.first }

        val wanted = ArrayList<Pair<DzTrack, String>>()
        val tops = artists.map { c -> async(Dispatchers.IO) { gate.withPermit { dzTop(c.a.id, 6) } } }.awaitAll()
        artists.forEachIndexed { i, c -> tops[i].filter { tKey(it.artist, it.title) !in p.heardT }.shuffled(rnd).take(if (i < 8) 2 else 1)
            .forEach { wanted += it to "new|Porque você ouve ${c.because.first()}" } }
        // lado B de quem você já curte
        val deep = p.seeds.take(5).map { s -> async(Dispatchers.IO) { gate.withPermit { s.first to (dzArtist(s.first)?.let { dzTop(it.id, 15) } ?: emptyList()) } } }.awaitAll()
        deep.forEach { (name, l) -> l.drop(3).filter { tKey(it.artist, it.title) !in p.heardT }.shuffled(rnd).take(2).forEach { wanted += it to "deep|Mais de $name que você ainda não ouviu" } }

        val resolved = wanted.map { (t, why) -> async(Dispatchers.IO) { gate.withPermit { runCatching { toYt(t) }.getOrNull()?.let { v -> WeeklyItem(v.copy(thumb = t.art.ifBlank { v.thumb }), why) } } } }
            .awaitAll().filterNotNull().filter { fresh(it.v) }
        // Mix do YouTube das suas mais tocadas (só artistas novos)
        val mixes = p.mixFrom.shuffled(rnd).take(5).map { id -> async(Dispatchers.IO) { gate.withPermit { Ytm.mix(id).drop(1).take(25) } } }.awaitAll()
        val fromMix = mixes.flatten().filter { fresh(it) && n(cleanArtist(it.channel)) !in p.known }.shuffled(rnd)
            .map { WeeklyItem(it, "Achado no Mix do YouTube das suas mais tocadas") }

        val nw = resolved.filter { it.why.startsWith("new|") }.map { it.copy(why = it.why.substringAfter('|')) }
        val dp = resolved.filter { it.why.startsWith("deep|") }.map { it.copy(why = it.why.substringAfter('|')) }
        val out = ArrayList<WeeklyItem>(); val per = HashMap<String, Int>(); val seen = HashSet<String>()
        fun push(x: WeeklyItem): Boolean {
            if (out.size >= 30) return false
            val ak = n(cleanArtist(x.v.channel)); val k = tKey(x.v.channel, x.v.title)
            if (x.v.id in seen || k in seen || (per[ak] ?: 0) >= 2) return false
            seen += x.v.id; seen += k; per[ak] = (per[ak] ?: 0) + 1; out += x; return true
        }
        var a = 0; var b = 0; var c = 0; var nNew = 0; var nMix = 0
        for (round in 0 until 200) {
            if (out.size >= 30) break
            var moved = false
            repeat(2) { while (a < nw.size && nNew < 18) { moved = true; if (push(nw[a++])) { nNew++; break } } }
            while (b < fromMix.size && nMix < 8) { moved = true; if (push(fromMix[b++])) { nMix++; break } }
            if (round % 3 == 2) while (c < dp.size) { moved = true; if (push(dp[c++])) break }
            if (!moved) break
        }
        (nw.drop(a) + fromMix.drop(b) + dp.drop(c)).forEach { push(it) }
        Result(out, artists.take(12).map { WeeklyArtist(it.a.name, it.a.pic, it.because.firstOrNull() ?: "") })
    }
}
