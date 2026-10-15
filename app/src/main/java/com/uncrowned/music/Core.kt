package com.uncrowned.music

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/* ---------------- Configurações (tudo personalizável) ---------------- */
object Prefs {
    lateinit var sp: SharedPreferences
    val version = mutableIntStateOf(0)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version.intValue++ }

    fun init(c: Context) {
        if (::sp.isInitialized) return
        sp = c.applicationContext.getSharedPreferences("uncrowned", Context.MODE_PRIVATE)
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun ps(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun pi(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    private fun pf(k: String, v: Float) = sp.edit().putFloat(k, v).apply()
    private fun pb(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    // Aparência
    var themeMode: String get() = s("theme", "amoled"); set(v) = ps("theme", v)          // system/light/dark/amoled
    var accent: Int get() = sp.getInt("accent", 0xFF9D4EDD.toInt()); set(v) = pi("accent", v)
    var dynamicColor: Boolean get() = sp.getBoolean("dynamic", false); set(v) = pb("dynamic", v)
    var corner: Float get() = sp.getFloat("corner", 16f); set(v) = pf("corner", v)
    var fontScale: Float get() = sp.getFloat("fontscale", 1f); set(v) = pf("fontscale", v)
    var gridColumns: Int get() = sp.getInt("gridcols", 2); set(v) = pi("gridcols", v)
    var showArtInList: Boolean get() = sp.getBoolean("listart", true); set(v) = pb("listart", v)
    var startTab: Int get() = sp.getInt("starttab", 0); set(v) = pi("starttab", v)

    // Tela do player
    var artShape: String get() = s("artshape", "rounded"); set(v) = ps("artshape", v)  // square/rounded/circle/disc
    var playerBg: String get() = s("playerbg", "blur"); set(v) = ps("playerbg", v)      // solid/blur/gradient
    var blurAmount: Float get() = sp.getFloat("blur", 60f); set(v) = pf("blur", v)
    var artSize: Float get() = sp.getFloat("artsize", 0.85f); set(v) = pf("artsize", v)
    var miniStyle: String get() = s("ministyle", "card"); set(v) = ps("ministyle", v)    // card/flat/floating
    var showProgressMini: Boolean get() = sp.getBoolean("miniprog", true); set(v) = pb("miniprog", v)

    // Letras
    var lyricsSize: Float get() = sp.getFloat("lyricsize", 22f); set(v) = pf("lyricsize", v)
    var lyricsAlign: String get() = s("lyricsalign", "center"); set(v) = ps("lyricsalign", v)

    // Notificação
    var notifBtn1: String get() = s("nb1", "shuffle"); set(v) = ps("nb1", v)
    var notifBtn2: String get() = s("nb2", "like"); set(v) = ps("nb2", v)
    var notifIcon: String get() = s("nicon", "crown"); set(v) = ps("nicon", v)            // crown/note/disc
    var seekStep: Int get() = sp.getInt("seekstep", 10); set(v) = pi("seekstep", v)

    // Reprodução
    // limitados a 0.5–2.0 e arredondados de 0.05 em 0.05 (valores quebrados travavam o áudio)
    private fun safe(v: Float) = (Math.round(v.coerceIn(0.5f, 2f) * 20f) / 20f)
    var speed: Float get() = safe(sp.getFloat("speed", 1f)); set(v) = pf("speed", safe(v))
    var pitch: Float get() = safe(sp.getFloat("pitch", 1f)); set(v) = pf("pitch", safe(v))
    var skipSilence: Boolean get() = sp.getBoolean("skipsilence", false); set(v) = pb("skipsilence", v)
    var minDurationSec: Int get() = sp.getInt("mindur", 30); set(v) = pi("mindur", v)

    // Equalizador
    var eqEnabled: Boolean get() = sp.getBoolean("eq", false); set(v) = pb("eq", v)
    var bass: Int get() = sp.getInt("bass", 0); set(v) = pi("bass", v)
    var virtualizer: Int get() = sp.getInt("virt", 0); set(v) = pi("virt", v)
    fun band(i: Int) = sp.getInt("eq_$i", 0)
    fun setBand(i: Int, v: Int) = pi("eq_$i", v)

    // Favoritas e playlists
    fun favs(): Set<String> = sp.getStringSet("favs", emptySet()) ?: emptySet()
    fun isFav(id: String?) = id != null && id in favs()
    fun toggleFav(id: String?) {
        id ?: return
        val f = favs().toMutableSet(); if (!f.add(id)) f.remove(id)
        sp.edit().putStringSet("favs", f).apply()
    }

    fun playlists(): Map<String, List<String>> {
        val o = JSONObject(s("playlists", "{}"))
        return o.keys().asSequence().associateWith { k ->
            val a = o.getJSONArray(k); List(a.length()) { a.getString(it) }
        }
    }
    private fun savePlaylists(m: Map<String, List<String>>) {
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k, JSONArray(v)) }; ps("playlists", o.toString())
    }
    fun createPlaylist(name: String) { val m = playlists().toMutableMap(); if (name !in m) m[name] = emptyList(); savePlaylists(m) }
    fun deletePlaylist(name: String) { savePlaylists(playlists() - name) }
    fun addToPlaylist(name: String, id: String) {
        val m = playlists().toMutableMap(); val l = m[name].orEmpty()
        if (id !in l) m[name] = l + id; savePlaylists(m)
    }
    fun removeFromPlaylist(name: String, id: String) {
        val m = playlists().toMutableMap(); m[name] = m[name].orEmpty() - id; savePlaylists(m)
    }
}

/* ---------------- Biblioteca de músicas ---------------- */
data class Song(
    val id: Long, val title: String, val artist: String, val album: String,
    val albumId: Long, val duration: Long, val uri: Uri, val dateAdded: Long
) {
    val art: Uri get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
    fun toItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album)
                .setArtworkUri(art).build()
        ).build()
}

object Library {
    val songs = mutableStateListOf<Song>()
    fun byId(id: String) = songs.firstOrNull { it.id.toString() == id }

    suspend fun load(c: Context) = withContext(Dispatchers.IO) {
        val out = ArrayList<Song>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED
        )
        val minMs = Prefs.minDurationSec * 1000L
        c.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { cur ->
            while (cur.moveToNext()) {
                val id = cur.getLong(0); val dur = cur.getLong(5)
                if (dur < minMs) continue
                out += Song(
                    id, cur.getString(1) ?: "Sem título",
                    (cur.getString(2) ?: "").let { if (it == "<unknown>" || it.isBlank()) "Artista desconhecido" else it },
                    cur.getString(3) ?: "Álbum", cur.getLong(4), dur,
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id), cur.getLong(6)
                )
            }
        }
        withContext(Dispatchers.Main) { songs.clear(); songs.addAll(out) }
    }
}

/* ---------------- Equalizador / efeitos ---------------- */
object Effects {
    var eq: Equalizer? = null; private set
    private var bassBoost: BassBoost? = null
    private var virt: Virtualizer? = null

    fun attach(sessionId: Int) {
        release()
        try { eq = Equalizer(0, sessionId) } catch (_: Throwable) {}
        try { bassBoost = BassBoost(0, sessionId) } catch (_: Throwable) {}
        try { virt = Virtualizer(0, sessionId) } catch (_: Throwable) {}
        apply()
    }

    fun apply() {
        val on = Prefs.eqEnabled
        eq?.let { e ->
            try {
                e.enabled = on
                for (b in 0 until e.numberOfBands) e.setBandLevel(b.toShort(), Prefs.band(b).toShort())
            } catch (_: Throwable) {}
        }
        bassBoost?.let { try { it.enabled = on; if (it.strengthSupported) it.setStrength(Prefs.bass.toShort()) } catch (_: Throwable) {} }
        virt?.let { try { it.enabled = on; if (it.strengthSupported) it.setStrength(Prefs.virtualizer.toShort()) } catch (_: Throwable) {} }
    }

    fun usePreset(i: Int) {
        val e = eq ?: return
        try {
            e.usePreset(i.toShort())
            for (b in 0 until e.numberOfBands) Prefs.setBand(b, e.getBandLevel(b.toShort()).toInt())
        } catch (_: Throwable) {}
    }

    fun release() {
        eq?.release(); bassBoost?.release(); virt?.release()
        eq = null; bassBoost = null; virt = null
    }
}

/* ---------------- Timer de desligar ---------------- */
object Sleep {
    var player: Player? = null
    val endsAt = mutableLongStateOf(0L)
    private val h = Handler(Looper.getMainLooper())
    private val r = Runnable { player?.pause(); endsAt.longValue = 0L }
    fun start(minutes: Int) { cancel(); endsAt.longValue = System.currentTimeMillis() + minutes * 60_000L; h.postDelayed(r, minutes * 60_000L) }
    fun cancel() { h.removeCallbacks(r); endsAt.longValue = 0L }
}

/* ---------------- Letras (lrclib.net, grátis) ---------------- */
data class Lyrics(val synced: List<Pair<Long, String>>?, val plain: String?)

object LyricsApi {
    private val cache = HashMap<String, Lyrics>()
    private val lrc = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?](.*)""")

    suspend fun get(artist: String, title: String, durationMs: Long): Lyrics = withContext(Dispatchers.IO) {
        val key = "$artist|$title"
        cache[key]?.let { return@withContext it }
        val res = try {
            fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
            val url = URL("https://lrclib.net/api/get?artist_name=${enc(artist)}&track_name=${enc(title)}&duration=${durationMs / 1000}")
            var json = fetch(url)
            if (json == null) {
                val arr = fetch(URL("https://lrclib.net/api/search?q=${enc("$artist $title")}"))?.let { JSONArray(it) }
                json = if (arr != null && arr.length() > 0) arr.getJSONObject(0).toString() else null
            }
            if (json == null) Lyrics(null, null) else {
                val o = JSONObject(json)
                val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }?.lines()?.mapNotNull { l ->
                    lrc.find(l)?.destructured?.let { (m, s, f, t) ->
                        val frac = if (f.isEmpty()) 0L else (f.padEnd(3, '0').take(3)).toLong()
                        (m.toLong() * 60_000 + s.toLong() * 1000 + frac) to t.trim()
                    }
                }
                Lyrics(synced, o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" })
            }
        } catch (_: Throwable) { Lyrics(null, null) }
        cache[key] = res
        res
    }

    private fun fetch(u: URL): String? {
        val c = u.openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "UncrownedMusic/1.0")
        c.connectTimeout = 8000; c.readTimeout = 8000
        return try { if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null } finally { c.disconnect() }
    }
}
