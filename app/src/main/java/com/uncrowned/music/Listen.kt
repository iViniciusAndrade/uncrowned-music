package com.uncrowned.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/* Registro de quanto você ouve de verdade (segundos), igual ao PC:
   por dia, por música e por artista, e o momento em que cada música tocou (pra Momentos). */
data class LTrack(val key: String, val src: String, val id: String, val title: String, val artist: String, val art: String, val url: String) {
    fun json() = JSONObject().put("k", key).put("s", src).put("i", id).put("t", title).put("a", artist).put("art", art).put("u", url)
    fun toYt() = YtVideo(id, title, artist, art, src)
    val isWeb get() = src == "yt" || src == "sc" || src == "vm"
    companion object { fun from(o: JSONObject) = LTrack(o.getString("k"), o.optString("s"), o.optString("i"), o.optString("t"), o.optString("a"), o.optString("art"), o.optString("u")) }
}
class Day(var t: Long = 0, val a: HashMap<String, Long> = HashMap(), val k: HashMap<String, Long> = HashMap())

object Listen {
    private lateinit var file: File
    private val days = HashMap<String, Day>()
    private val tracks = HashMap<String, LTrack>()
    private val moments = ArrayList<Pair<Long, String>>()   // (quando, música)
    var version by mutableIntStateOf(0)
    private var loaded = false
    private var dirty = 0
    private var lastKey: String? = null; private var acc = 0; private var logged = false

    @Synchronized fun init(c: Context) {
        if (loaded) return
        file = File(c.applicationContext.filesDir, "listen.json"); loaded = true
        runCatching {
            val j = JSONObject(file.readText())
            j.optJSONObject("days")?.let { d -> d.keys().forEach { dk -> val o = d.getJSONObject(dk); val day = Day(o.optLong("t"))
                o.optJSONObject("a")?.let { a -> a.keys().forEach { day.a[it] = a.getLong(it) } }
                o.optJSONObject("k")?.let { a -> a.keys().forEach { day.k[it] = a.getLong(it) } }
                days[dk] = day } }
            j.optJSONArray("tr")?.let { a -> for (i in 0 until a.length()) LTrack.from(a.getJSONObject(i)).let { tracks[it.key] = it } }
            j.optJSONArray("mom")?.let { a -> for (i in 0 until a.length()) a.getJSONArray(i).let { moments += it.getLong(0) to it.getString(1) } }
        }
    }

    @Synchronized fun save() {
        if (!loaded) return
        runCatching {
            val d = JSONObject(); days.forEach { (k, v) -> d.put(k, JSONObject().put("t", v.t).put("a", JSONObject(v.a as Map<*, *>)).put("k", JSONObject(v.k as Map<*, *>))) }
            val used = days.values.flatMap { it.k.keys }.toHashSet() + moments.map { it.second }
            val tr = JSONArray(); tracks.values.filter { it.key in used }.forEach { tr.put(it.json()) }
            val mom = JSONArray(); moments.takeLast(6000).forEach { mom.put(JSONArray().put(it.first).put(it.second)) }
            val tmp = File(file.path + ".tmp"); tmp.writeText(JSONObject().put("days", d).put("tr", tr).put("mom", mom).toString()); tmp.renameTo(file)
        }
        dirty = 0
    }

    fun dayKey(c: Calendar = Calendar.getInstance()) = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    /* chamado a cada poucos segundos enquanto toca. t == null = rádio ao vivo (conta só o tempo total) */
    @Synchronized fun tick(t: LTrack?, sec: Int) {
        if (!loaded) return
        val dk = dayKey(); val day = days.getOrPut(dk) { Day() }
        day.t += sec
        if (t != null) {
            if (t.artist.isNotBlank()) day.a[t.artist] = (day.a[t.artist] ?: 0) + sec
            day.k[t.key] = (day.k[t.key] ?: 0) + sec
            tracks[t.key] = t
            if (t.key != lastKey) { lastKey = t.key; acc = 0; logged = false }
            acc += sec
            if (acc >= 30 && !logged) { logged = true; moments += System.currentTimeMillis() / 1000 to t.key }
        }
        if (days.size > 430) days.keys.sorted().take(days.size - 430).forEach { days.remove(it) }
        if (++dirty >= (if (Eco.on) 24 else 12)) { save(); version++ }
    }

    /* ---------- leitura ---------- */
    data class Stats(val total: Long, val songs: List<Pair<LTrack, Long>>, val artists: List<Triple<String, Long, String>>, val label: String)
    val PERIODS = listOf("week" to "Esta semana", "month" to "Este mês", "year" to "Este ano", "all" to "Sempre")
    @Synchronized fun stats(p: String): Stats {
        val c = Calendar.getInstance()
        when (p) {
            "week" -> { c.add(Calendar.DAY_OF_MONTH, -((c.get(Calendar.DAY_OF_WEEK) + 5) % 7)) }
            "month" -> c.set(Calendar.DAY_OF_MONTH, 1)
            "year" -> c.set(Calendar.DAY_OF_YEAR, 1)
            else -> c.set(2000, 0, 1)
        }
        val from = dayKey(c)
        var total = 0L; val a = HashMap<String, Long>(); val k = HashMap<String, Long>()
        days.forEach { (dk, d) -> if (dk >= from) { total += d.t; d.a.forEach { (x, v) -> a[x] = (a[x] ?: 0) + v }; d.k.forEach { (x, v) -> k[x] = (k[x] ?: 0) + v } } }
        val songs = k.entries.mapNotNull { e -> tracks[e.key]?.let { it to e.value } }.sortedByDescending { it.second }
        val artists = a.entries.sortedByDescending { it.value }.map { e -> Triple(e.key, e.value, songs.firstOrNull { it.first.artist == e.key }?.first?.art ?: "") }
        return Stats(total, songs, artists, PERIODS.first { it.first == p }.second)
    }
    @Synchronized fun momentList(): List<Pair<Long, String>> = moments.toList()
    @Synchronized fun track(k: String) = tracks[k]

    // músicas mais tocadas nos momentos que passam no filtro
    fun momentTop(f: (Calendar) -> Boolean): List<LTrack> {
        val n = HashMap<String, Int>(); val c = Calendar.getInstance()
        momentList().forEach { (ts, k) -> c.timeInMillis = ts * 1000; if (f(c)) n[k] = (n[k] ?: 0) + 1 }
        return n.entries.sortedByDescending { it.value }.mapNotNull { track(it.key) }.take(60)
    }
}

fun fmtH(s: Long) = if (s >= 3600) "%s h".format(if (s >= 36000) (s / 3600).toString() else "%.1f".format(s / 3600.0).replace('.', ',')) else "${maxOf(1, Math.round(s / 60.0))} min"

/* tocar uma lista misturada (YouTube, SoundCloud, offline, Audius) a partir de uma música */
fun playLTracks(ui: PlayerUi, list: List<LTrack>, i: Int) {
    val t = list.getOrNull(i) ?: return
    if (t.isWeb) {
        val web = list.filter { it.isWeb }
        ui.controller?.pause(); YtPlayer.play(web.map { it.toYt() }, web.indexOf(t).coerceAtLeast(0))
    } else if (t.src == "loc") {
        val songs = list.filter { it.src == "loc" }.mapNotNull { Library.byId(it.id) }
        val s = Library.byId(t.id) ?: return
        YtPlayer.js("player.pauseVideo()"); ui.play(songs, songs.indexOf(s).coerceAtLeast(0))
    } else {
        val tr = list.filter { !it.isWeb && it.src != "loc" && it.url.isNotBlank() }
        ui.playOnline(tr.map { Track(it.id, it.title, it.artist, "", 0, it.art, false, it.url) }, tr.indexOf(t).coerceAtLeast(0))
    }
}
