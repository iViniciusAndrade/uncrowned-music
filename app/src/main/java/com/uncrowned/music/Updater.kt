package com.uncrowned.music

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/* ---------- Atualização automática ----------
   Cada push no GitHub vira um build novo (release "build-N" com o APK). O app confere quando abre
   (e a cada 6 h), avisa que tem versão nova com o que mudou e, num toque, baixa e abre o instalador
   do Android por cima (sem perder nada, porque todo build sai com a mesma chave). */
object Updater {
    private const val REPO = "iViniciusAndrade/uncrowned-music"
    data class Info(val build: Int, val notes: String, val url: String, val size: Long)
    var available by mutableStateOf<Info?>(null); private set
    var progress by mutableStateOf<Float?>(null); private set
    var status by mutableStateOf(""); private set
    var dismissed by mutableStateOf(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, _ -> })

    fun myBuild(c: Context): Int = runCatching {
        val pi = c.packageManager.getPackageInfo(c.packageName, 0)
        androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi).toInt()
    }.getOrDefault(0)
    var auto: Boolean get() = Prefs.sp.getBoolean("updauto", true); set(v) = Prefs.sp.edit().putBoolean("updauto", v).apply()

    private fun http(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("User-Agent", "UncrownedMusic")
        return try { if (c.responseCode !in 200..299) error("GitHub respondeu ${c.responseCode}"); c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }

    /* procura o build mais novo com APK; devolve o número dele (0 = não achou) */
    suspend fun check(c: Context, manual: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (!manual && (!auto || !Sync.online())) return@withContext 0
        if (!manual && System.currentTimeMillis() - Prefs.sp.getLong("updlast", 0) < 6 * 3600_000L && available == null) return@withContext 0
        runCatching {
            val a = JSONArray(http("https://api.github.com/repos/$REPO/releases?per_page=20"))
            var best: Info? = null
            for (i in 0 until a.length()) {
                val r = a.getJSONObject(i); val n = Regex("^build-(\\d+)$").find(r.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
                val assets = r.optJSONArray("assets") ?: continue
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } ?: continue
                if (best == null || n > best.build) best = Info(n, r.optString("body"), apk.optString("browser_download_url"), apk.optLong("size"))
            }
            Prefs.sp.edit().putLong("updlast", System.currentTimeMillis()).apply()
            val mine = myBuild(c)
            withContext(Dispatchers.Main) { available = best?.takeIf { it.build > mine }; if (available != null) dismissed = false }
            best?.build ?: 0
        }.getOrElse { if (manual) withContext(Dispatchers.Main) { status = "Sem conexão com o GitHub agora" }; 0 }
    }

    /* assinatura do APK baixado é a mesma do app instalado? (se não for, o Android recusa instalar por cima) */
    @Suppress("DEPRECATION")
    private fun certs(c: Context, archive: String? = null): Set<String> = runCatching {
        val pm = c.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val pi = if (archive != null) pm.getPackageArchiveInfo(archive, flags) else pm.getPackageInfo(c.packageName, flags)
        val sigs = (if (Build.VERSION.SDK_INT >= 28) pi?.signingInfo?.apkContentsSigners?.toList() else null) ?: pi?.signatures?.toList()
        sigs.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }.getOrDefault(emptySet())

    fun install(c: Context) {
        val info = available ?: return
        if (progress != null) return
        if (Build.VERSION.SDK_INT >= 26 && !c.packageManager.canRequestPackageInstalls()) {
            status = "Libera \"Instalar apps desconhecidos\" pro Uncrowned e toca em Atualizar de novo"
            runCatching { c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            return
        }
        progress = 0f; status = "Baixando o build ${info.build}…"
        scope.launch {
            val dir = File(c.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val f = File(dir, "Uncrowned-build-${info.build}.apk")
            val ok = runCatching {
                var url = info.url; var hops = 0
                var con: HttpURLConnection? = null
                while (con == null) {
                    val k = URL(url).openConnection() as HttpURLConnection
                    k.instanceFollowRedirects = false; k.connectTimeout = 15000; k.readTimeout = 30000
                    k.setRequestProperty("User-Agent", "UncrownedMusic")
                    val code = k.responseCode
                    if (code in 300..399 && hops++ < 6) { url = URL(URL(url), k.getHeaderField("Location")).toString(); k.disconnect(); continue }
                    if (code !in 200..299) { k.disconnect(); error("HTTP $code") }
                    con = k
                }
                val cn = con!!
                val total = cn.contentLengthLong.takeIf { it > 0 } ?: info.size
                cn.inputStream.use { inp -> f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024); var got = 0L; var n: Int; var last = 0L
                    while (inp.read(buf).also { n = it } >= 0) { out.write(buf, 0, n); got += n
                        if (total > 0 && System.currentTimeMillis() - last > 250) { last = System.currentTimeMillis(); val p = got.toFloat() / total; withContext(Dispatchers.Main) { progress = p } } }
                } }
                cn.disconnect()
                if (f.length() < 1_000_000) error("o arquivo veio incompleto")
                true
            }.getOrElse { e -> withContext(Dispatchers.Main) { progress = null; status = "Não deu pra baixar: ${e.message}" }; false }
            if (!ok) return@launch
            val mine = certs(c); val theirs = certs(c, f.absolutePath)
            withContext(Dispatchers.Main) {
                progress = null
                if (mine.isNotEmpty() && theirs.isNotEmpty() && mine.intersect(theirs).isEmpty()) {
                    status = "Esse build foi assinado com a chave nova. Só desta vez: desinstala o Uncrowned e instala esse APK (abrindo agora). Depois disso as atualizações vão por cima, sozinhas."
                } else status = "Abrindo o instalador…"
                runCatching {
                    val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", f)
                    c.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { status = "Não consegui abrir o instalador: ${it.message}" }
            }
        }
    }

    fun notesOf(i: Info): List<String> = i.notes.lines().map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
        .filter { it.isNotBlank() && !Regex("(?i)^(co-authored|claude-session|🤖|https?:|baixe o )").containsMatchIn(it) }.take(8)
}

/* cartão que aparece no topo quando tem versão nova */
@Composable
fun UpdateBanner() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Updater.check(ctx) }
    val a = Updater.available ?: return
    if (Updater.dismissed && Updater.progress == null) return
    val acc = MaterialTheme.colorScheme.primary
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp), color = acc.copy(alpha = .16f)) {
        Column(Modifier.padding(14.dp)) {
            Text("Atualização disponível · build ${a.build}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Updater.notesOf(a).take(4).forEach { Text("• $it", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
            val p = Updater.progress
            if (p != null) LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(4.dp)))
            if (Updater.status.isNotBlank()) Text(Updater.status, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp), color = acc)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { Updater.install(ctx) }, enabled = p == null) { Text(if (p != null) "Baixando ${(p * 100).toInt()}%" else "Atualizar agora") }
                Spacer(Modifier.width(8.dp))
                if (p == null) TextButton(onClick = { Updater.dismissed = true }) { Text("Depois") }
            }
        }
    }
}

/* grupo nos Ajustes */
@Composable
fun UpdateSettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var latest by remember { mutableIntStateOf(0) }
    val mine = remember { Updater.myBuild(ctx) }
    Group("Atualizações", footer = "O app procura versão nova sozinho (quando abre e a cada 6 horas) e avisa. Atualizar baixa e abre o instalador do Android por cima, sem perder nada.") {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Versão instalada: build ${if (mine > 1) mine else "antigo"}", fontSize = 16.sp)
                val a = Updater.available
                Text(when { a != null -> "Build ${a.build} disponível"; checking -> "Procurando…"; latest > 0 -> "Você está na mais nova"; else -> Updater.status.ifBlank { "Toque em Procurar" } },
                    fontSize = 13.sp, color = if (a != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (Updater.available != null) Button(onClick = { Updater.dismissed = false; Updater.install(ctx) }, enabled = Updater.progress == null) { Text(Updater.progress?.let { "${(it * 100).toInt()}%" } ?: "Atualizar") }
            else OutlinedButton(onClick = { checking = true; scope.launch { latest = Updater.check(ctx, manual = true); checking = false } }, enabled = !checking) { Text("Procurar") }
        }
        if (Updater.status.isNotBlank() && Updater.available != null) Text(Updater.status, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
        var au by remember { mutableStateOf(Updater.auto) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Avisar quando tiver atualização", Modifier.weight(1f), fontSize = 16.sp)
            Switch(au, { au = it; Updater.auto = it })
        }
    }
}
