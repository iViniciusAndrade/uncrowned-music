package com.uncrowned.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/* Sincronizar: deixa a música no celular pra ouvir sem internet.
   Só usa fontes que liberam o arquivo (Audius, SoundCloud com download do artista, Internet Archive,
   ccMixter e Wikimedia Commons). Todo arquivo é conferido antes de salvar: só entra áudio de verdade. */
object Sync {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"
    private const val MIN = 60 * 1024L
    private const val MAX = 400L * 1024 * 1024
    val FROM = mapOf("au" to "Audius", "sc" to "SoundCloud", "ia" to "Internet Archive", "cc" to "ccMixter", "wm" to "Wikimedia Commons")
    lateinit var app: Context
    var version by mutableIntStateOf(0)      // telas se atualizam quando muda
    var playLocal: ((String, YtVideo) -> Unit)? = null

    fun init(c: Context) { if (!::app.isInitialized) app = c.applicationContext }

    data class Cand(val src: String, val id: String, val title: String, val artist: String, val url: String, val art: String = "", val score: Int = 0) {
        val from get() = FROM[src] ?: src
        fun toTrack() = Track("${src}_$id".take(80), title, artist, from, 0, art, true, url)
    }
    data class Res(val ok: Boolean, val from: String = "", val alt: Boolean = false, val error: String = "")

    /* ---------- rede ---------- */
    fun online(): Boolean = runCatching {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrDefault(true)

    private fun get(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", UA); c.connectTimeout = 10000; c.readTimeout = 12000
        return try { if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}"); c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /* ---------- listas guardadas ---------- */
    private fun vids(k: String) = runCatching { JSONArray(Prefs.sp.getString(k, "[]")).let { a -> List(a.length()) { YtVideo.from(a.getJSONObject(it)) } } }.getOrDefault(emptyList())
    private fun saveVids(k: String, l: List<YtVideo>) { Prefs.sp.edit().putString(k, JSONArray(l.map { it.json() }).toString()).apply(); version++ }
    fun key(v: YtVideo) = "${v.src}:${v.id}"
    fun pending() = vids("syncpend")
    @Synchronized fun addPending(v: YtVideo) { if (pending().none { key(it) == key(v) }) saveVids("syncpend", pending() + v) }
    @Synchronized fun removePending(v: YtVideo) = saveVids("syncpend", pending().filter { key(it) != key(v) })
    @Synchronized fun clearPending() = saveVids("syncpend", emptyList())

    private fun syncedMap() = runCatching { JSONObject(Prefs.sp.getString("synced", "{}")) }.getOrDefault(JSONObject())
    fun syncedUri(v: YtVideo): String? = syncedMap().optString(key(v)).ifBlank { null }
    // o arquivo salvo ainda existe? (você pode ter apagado da pasta Música)
    fun uriOk(u: String): Boolean = runCatching {
        if (u.startsWith("file:")) File(android.net.Uri.parse(u).path ?: "").exists()
        else app.contentResolver.openFileDescriptor(android.net.Uri.parse(u), "r")?.use { true } ?: false
    }.getOrDefault(false)
    @Synchronized private fun markSynced(v: YtVideo, uri: String) { val m = syncedMap(); m.put(key(v), uri); Prefs.sp.edit().putString("synced", m.toString()).apply(); version++ }

    // músicas guardadas dentro do app (ouvir offline sem virar arquivo)
    fun kept(): List<Track> = runCatching { JSONArray(Prefs.sp.getString("offapp", "[]")).let { a -> List(a.length()) { Track.from(a.getJSONObject(it)) } } }.getOrDefault(emptyList())
        .filter { File(android.net.Uri.parse(it.url).path ?: "").exists() }
    fun isKept(id: String) = kept().any { it.id == id }
    fun keptUrl(id: String): String? = kept().firstOrNull { it.id == id }?.url
    /* cópia no celular de uma música do player (Audius guardada no app ou SoundCloud/YouTube salva), se tiver */
    fun localFor(mediaId: String?): String? = runCatching {
        val m = mediaId ?: return@runCatching null
        when {
            m.startsWith("au:") -> keptUrl(m.removePrefix("au:"))
            m.startsWith("sc:") -> syncedUri(YtVideo(m.removePrefix("sc:"), "", "", "", "sc"))?.takeIf { uriOk(it) }
            else -> null
        }
    }.getOrNull()
    @Synchronized private fun saveKept(l: List<Track>) { Prefs.sp.edit().putString("offapp", JSONArray(l.map { it.json() }).toString()).apply(); version++ }
    @Synchronized fun unkeep(t: Track) { runCatching { File(android.net.Uri.parse(t.url).path!!).delete() }; saveKept(kept().filter { it.id != t.id }) }

    var autoWifi: Boolean get() = Prefs.sp.getBoolean("syncwifi", true); set(v) { Prefs.sp.edit().putBoolean("syncwifi", v).apply(); schedule(app) }

    /* ---------- comparar nomes ---------- */
    private fun nrm(x: String) = Normalizer.normalize(x.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
        .replace(Regex("\\b(official|oficial|video|audio|lyrics?|letra|clipe|hd|4k|visualizer|prod\\.?.*|ft\\.?.*|feat\\.?.*)\\b"), " ")
        .replace(Regex("[^a-z0-9]+"), " ").trim()
    private fun ref(v: YtVideo): Pair<String, String> {
        var title = v.title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim()
        var artist = v.channel.replace(Regex("(?i) - Topic$"), "").replace(Regex("(?i)VEVO$"), "").replace(Regex("(?i)official$"), "").trim()
        if (" - " in title) {
            val a = title.substringBefore(" - ").trim(); val b = title.substringAfter(" - ").trim()
            if (artist.isBlank() || nrm(a).contains(nrm(artist)) || nrm(artist).contains(nrm(a)) || v.src == "yt") { artist = a; title = b }
        }
        return title to artist
    }
    private fun same(title: String, artist: String, c: Cand): Int {
        val ta = nrm(title); val tb = nrm(c.title); val aa = nrm(artist); val ab = nrm(c.artist)
        if (ta.isBlank() || tb.isBlank()) return 0
        if (!(ta == tb || ta.contains(tb) || tb.contains(ta))) return 0
        val aOk = aa.isBlank() || ab.isBlank() || aa.contains(ab) || ab.contains(aa) || nrm(c.title + " " + c.artist).contains(aa)
        return if (aOk) (if (ta == tb) 3 else 2) else 1
    }

    /* ---------- fontes ---------- */
    private suspend fun audius(q: String) = Audius.search(q).getOrDefault(emptyList()).filter { it.downloadable }
        .map { Cand("au", it.id, it.title, it.artist, "${Audius.BASE}/tracks/${it.id}/download?app_name=${Audius.APP}", it.art) }
    private suspend fun archive(q: String) = Archive.search(q).getOrDefault(emptyList()).map { Cand("ia", it.id, it.title, it.artist, it.url, it.art) }
    private suspend fun soundcloud(q: String) = SoundCloudApi.downloadables(q).map { (v, _) -> Cand("sc", v.id, v.title, v.channel, "", v.thumb) }
    private fun ccmixter(q: String): List<Cand> = runCatching {
        val a = JSONArray(get("https://ccmixter.org/api/query?f=json&search=${enc(q)}&search_type=all&limit=15"))
        (0 until a.length()).mapNotNull { i ->
            val u = a.getJSONObject(i); val files = u.optJSONArray("files") ?: return@mapNotNull null
            val f = (0 until files.length()).map { files.getJSONObject(it) }
                .firstOrNull { it.optJSONObject("file_format_info")?.optString("mime_type").orEmpty().startsWith("audio") } ?: files.optJSONObject(0) ?: return@mapNotNull null
            val url = f.optString("download_url").replace(Regex("^http:"), "https:").ifBlank { return@mapNotNull null }
            Cand("cc", "cc" + u.optString("upload_id"), u.optString("upload_name"), u.optString("user_real_name").ifBlank { u.optString("user_name") }, url)
        }
    }.getOrDefault(emptyList())
    private fun commons(q: String): List<Cand> = runCatching {
        val j = JSONObject(get("https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrnamespace=6&gsrlimit=15&gsrsearch=${enc("$q filemime:audio")}&prop=imageinfo&iiprop=url|mime|extmetadata&format=json"))
        val pages = j.optJSONObject("query")?.optJSONObject("pages") ?: return@runCatching emptyList()
        val strip = { s: String -> s.replace(Regex("<[^>]+>"), "").trim() }
        pages.keys().asSequence().mapNotNull { k ->
            val p = pages.getJSONObject(k); val ii = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@mapNotNull null
            val mime = ii.optString("mime"); val url = ii.optString("url")
            if (!(mime.startsWith("audio/") || mime == "application/ogg") || !url.startsWith("https://upload.wikimedia.org/")) return@mapNotNull null
            val m = ii.optJSONObject("extmetadata")
            val title = strip(m?.optJSONObject("ObjectName")?.optString("value").orEmpty()).ifBlank { p.optString("title").removePrefix("File:").substringBeforeLast('.') }
            Cand("wm", "wm" + p.optString("pageid"), title, strip(m?.optJSONObject("Artist")?.optString("value").orEmpty()).take(80), url)
        }.toList()
    }.getOrDefault(emptyList())

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<Cand>>>()
    suspend fun candidates(v: YtVideo): List<Cand> = withContext(Dispatchers.IO) {
        val (title, artist) = ref(v)
        val k = nrm(artist) + "|" + nrm(title)
        cache[k]?.let { if (System.currentTimeMillis() - it.first < 3600_000) return@withContext it.second }
        val q = "$artist $title".trim()
        val all = coroutineScope {
            listOf(async { audius(q) }, async { audius(title) }, async { archive(q) }, async { runCatching { soundcloud(q) }.getOrDefault(emptyList()) },
                async { ccmixter(q) }, async { ccmixter(title) }, async { commons(q) }).flatMap { it.await() }
        }
        val out = all.distinctBy { it.src + it.id }.map { it.copy(score = same(title, artist, it)) }.sortedByDescending { it.score }
        cache[k] = System.currentTimeMillis() to out
        out
    }

    // parecidas: remix, cover, ao vivo e outras do mesmo artista com download liberado
    suspend fun similar(v: YtVideo): List<Cand> = withContext(Dispatchers.IO) {
        val (_, artist) = ref(v)
        val near = candidates(v).filter { it.score >= 1 }
        val byArtist = if (artist.isBlank()) emptyList() else coroutineScope {
            listOf(async { audius(artist) }, async { ccmixter(artist) }, async { archive(artist) }).flatMap { it.await() }
        }.filter { nrm(it.artist).contains(nrm(artist)) }
        (near + byArtist).distinctBy { it.src + it.id }.take(30)
    }

    /* ---------- salvar ---------- */
    suspend fun pick(c: Cand, nameHint: String = ""): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (c.src == "sc") SoundCloudApi.downloadUrl(c.id) ?: error("o artista não liberou o download") else c.url
            save(url, nameHint.ifBlank { listOf(c.artist, c.title).filter { it.isNotBlank() }.joinToString(" - ") }, c.title, c.artist, public = true)
        }
    }

    suspend fun syncOne(v: YtVideo): Res = withContext(Dispatchers.IO) {
        if (!online()) return@withContext Res(false, error = "Sem internet agora. Ela ficou em Pra sincronizar e baixa sozinha quando tiver Wi-Fi.").also { addPending(v) }
        // a própria faixa do SoundCloud com download do artista
        if (v.src == "sc") SoundCloudApi.downloadUrl(v.id)?.let { url ->
            runCatching { save(url, "${v.channel} - ${v.title}", v.title, v.channel, true) }.getOrNull()?.let { markSynced(v, it); removePending(v); return@withContext Res(true, "SoundCloud") }
        }
        for (c in candidates(v).filter { it.score >= 2 }.take(4)) {
            val r = pick(c, "${c.artist.ifBlank { v.channel }} - ${c.title}")
            r.getOrNull()?.let { markSynced(v, it); removePending(v); return@withContext Res(true, c.from, alt = true) }
        }
        addPending(v)
        Res(false)
    }

    // faixa de fonte aberta que o artista não liberou como arquivo: fica guardada só dentro do app
    suspend fun keep(t: Track): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (isKept(t.id)) return@runCatching
            val f = save(t.stream, hash(t.id), t.title, t.artist, public = false)
            saveKept(kept().filter { it.id != t.id } + t.copy(url = f))
        }
    }

    private fun hash(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)

    private fun open(u0: String): HttpURLConnection {
        var u = u0
        repeat(7) {
            if (!u.startsWith("https://", true)) error("link inseguro")
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false; c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("User-Agent", UA)
            val code = c.responseCode
            if (code in 300..399) { val loc = c.getHeaderField("Location") ?: error("sem destino"); c.disconnect(); u = URL(URL(u), loc).toString(); return@repeat }
            if (code !in 200..299) { c.disconnect(); error("HTTP $code") }
            return c
        }
        error("redirecionamentos demais")
    }

    private fun kind(b: ByteArray, n: Int): String? {
        if (n < 12) return null
        fun s(a: Int, z: Int) = String(b, a, z - a, Charsets.ISO_8859_1)
        val b0 = b[0].toInt() and 0xFF; val b1 = b[1].toInt() and 0xFF
        return when {
            s(0, 3) == "ID3" -> "mp3"
            b0 == 0xFF && (b1 and 0xE0) == 0xE0 -> if ((b1 and 0x06) == 0) "aac" else "mp3"
            s(0, 4) == "OggS" -> "ogg"
            s(0, 4) == "fLaC" -> "flac"
            s(0, 4) == "RIFF" && s(8, 12) == "WAVE" -> "wav"
            s(4, 8) == "ftyp" -> "m4a"
            b0 == 0x1A && b1 == 0x45 && (b[2].toInt() and 0xFF) == 0xDF && (b[3].toInt() and 0xFF) == 0xA3 -> "webm"
            else -> null
        }
    }
    private val MIME = mapOf("mp3" to "audio/mpeg", "aac" to "audio/aac", "ogg" to "audio/ogg", "flac" to "audio/flac", "wav" to "audio/wav", "m4a" to "audio/mp4", "webm" to "audio/webm")

    /* baixa pra um arquivo temporário conferindo tudo; só no fim vira música de verdade */
    private fun save(url: String, name: String, title: String, artist: String, public: Boolean): String {
        val c = open(url)
        val ct = (c.contentType ?: "").lowercase()
        if (Regex("text/|html|json|javascript|msdownload|dosexec|zip|rar|7z|android.package").containsMatchIn(ct)) { c.disconnect(); error("o arquivo não é áudio") }
        if (c.contentLengthLong > MAX) { c.disconnect(); error("arquivo grande demais") }
        val tmp = File(app.cacheDir, "sync_${System.nanoTime()}.part")
        var ext: String? = null
        try {
            var total = 0L
            BufferedInputStream(c.inputStream).use { inp ->
                tmp.outputStream().use { o ->
                    val head = ByteArray(16); var n = 0
                    while (n < 16) { val r = inp.read(head, n, 16 - n); if (r < 0) break; n += r }
                    ext = kind(head, n) ?: error("o arquivo não é áudio")
                    o.write(head, 0, n); total = n.toLong()
                    val buf = ByteArray(32 * 1024)
                    while (true) { val r = inp.read(buf); if (r < 0) break; total += r; if (total > MAX) error("arquivo grande demais"); o.write(buf, 0, r) }
                }
            }
            if (total < MIN) error("arquivo incompleto")
        } catch (e: Throwable) { tmp.delete(); throw e } finally { c.disconnect() }
        val e = ext!!
        val clean = name.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "").trim().trimEnd('.').take(120).ifBlank { "musica" }
        try {
            if (!public) {
                val dir = File(app.filesDir, "offline").apply { mkdirs() }
                val f = File(dir, "$clean.$e"); tmp.copyTo(f, true)
                return android.net.Uri.fromFile(f).toString()
            }
            if (Build.VERSION.SDK_INT >= 29) {
                val r = app.contentResolver
                val v = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$clean.$e"); put(MediaStore.Audio.Media.MIME_TYPE, MIME[e])
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Uncrowned")
                    if (title.isNotBlank()) put(MediaStore.Audio.Media.TITLE, title)
                    if (artist.isNotBlank()) put(MediaStore.Audio.Media.ARTIST, artist)
                    put(MediaStore.Audio.Media.IS_MUSIC, 1); put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = r.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v) ?: error("não deu pra criar o arquivo")
                try {
                    (r.openOutputStream(uri) ?: error("não deu pra abrir o arquivo")).use { o -> tmp.inputStream().use { it.copyTo(o) } }
                    r.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
                } catch (x: Throwable) { r.delete(uri, null, null); throw x }
                return uri.toString()
            }
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Uncrowned").apply { mkdirs() }
            var f = File(dir, "$clean.$e"); var i = 2
            while (f.exists()) f = File(dir, "$clean (${i++}).$e")
            tmp.copyTo(f)
            MediaScannerConnection.scanFile(app, arrayOf(f.absolutePath), arrayOf(MIME[e]), null)
            return android.net.Uri.fromFile(f).toString()
        } finally { tmp.delete() }
    }

    /* ---------- pendentes: tentam de novo sozinhos no Wi-Fi ---------- */
    suspend fun retryPending(max: Int = 25): List<YtVideo> {
        val found = ArrayList<YtVideo>()
        for (v in pending().take(max)) { if (!online()) break; if (runCatching { syncOne(v) }.getOrNull()?.ok == true) found += v }
        Prefs.sp.edit().putLong("pendtry", System.currentTimeMillis()).apply()
        return found
    }

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!autoWifi) { wm.cancelUniqueWork("syncpend"); return }
        val cons = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build()
        wm.enqueueUniquePeriodicWork("syncpend", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(cons).build())
    }
    // sincronizar a fila inteira (ou várias) em segundo plano, mesmo com o app fechado
    fun runNow(ctx: Context) {
        val cons = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("syncnow", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(cons).build())
    }

    fun notifyFound(ctx: Context, l: List<YtVideo>) {
        if (l.isEmpty()) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("sync", "Sincronizar", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, "sync").setSmallIcon(R.drawable.ic_note)
            .setContentTitle(if (l.size == 1) "\"${l[0].title}\" já está offline" else "${l.size} músicas pendentes agora estão offline")
            .setContentText("Salvas na pasta Música/Uncrowned").setContentIntent(open).setAutoCancel(true).build()
        runCatching { nm.notify(4242, n) }
    }
}

class SyncWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        Prefs.init(applicationContext); Sync.init(applicationContext)
        val found = Sync.retryPending(40)
        Sync.notifyFound(applicationContext, found)
        return Result.success()
    }
}
