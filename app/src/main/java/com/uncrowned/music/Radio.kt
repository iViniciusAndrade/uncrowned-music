package com.uncrowned.music

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/* ---------- Rádio ao vivo (Radio Browser: catálogo aberto e gratuito) + rádio FM do celular ---------- */
object RadioApi {
    private val servers = listOf("https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info", "https://at1.api.radio-browser.info", "https://fi1.api.radio-browser.info")

    private fun get(path: String): String? {
        for (s in servers) try {
            val c = URL(s + path).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 12000
            c.setRequestProperty("User-Agent", "UncrownedMusic/1.0")
            if (c.responseCode == 200) return c.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {}
        return null
    }

    suspend fun stations(q: String, tag: String, country: String): List<Track> = withContext(Dispatchers.IO) {
        val p = StringBuilder("/json/stations/search?hidebroken=true&order=clickcount&reverse=true&limit=80")
        if (q.isNotBlank()) p.append("&name=").append(URLEncoder.encode(q, "UTF-8"))
        if (tag.isNotBlank()) p.append("&tag=").append(URLEncoder.encode(tag, "UTF-8"))
        if (country != "ZZ") p.append("&countrycode=").append(country)
        val body = get(p.toString()) ?: return@withContext emptyList()
        val a = JSONArray(body); val seen = HashSet<String>()
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val url = o.optString("url_resolved").ifBlank { o.optString("url") }
            val name = o.optString("name").trim()
            if (o.optInt("hls") == 1 || url.isBlank() || name.isBlank() || !seen.add(name.lowercase())) null
            else {
                val sub = listOf(o.optString("state").ifBlank { o.optString("country") }, o.optString("tags").split(",").filter { it.isNotBlank() }.take(2).joinToString(", "))
                    .filter { it.isNotBlank() }.joinToString(" · ")
                Track("radio-" + o.optString("stationuuid"), name, sub, "Rádio", 0, o.optString("favicon"), false, url)
            }
        }
    }

    fun favs(): List<Track> = JSONArray(Prefs.sp.getString("radiofavs", "[]")).let { a -> List(a.length()) { Track.from(a.getJSONObject(it)) } }
    fun toggleFav(t: Track) {
        val f = favs(); val n = if (f.any { it.id == t.id }) f.filter { it.id != t.id } else listOf(t) + f
        Prefs.sp.edit().putString("radiofavs", JSONArray(n.map { it.json() }).toString()).apply()
    }

    /* Rádio FM sem internet: o Android não deixa outros apps controlarem o FM, então abrimos o app de rádio FM
       que vem no celular (Samsung, Motorola, Xiaomi, LG...). Precisa de fone com fio, que serve de antena. */
    private val fmPackages = listOf(
        "com.sec.android.app.fm", "com.samsung.android.app.fm", "com.motorola.fmplayer", "com.motorola.android.fmradio",
        "com.miui.fm", "com.android.fmradio", "com.caf.fmradio", "com.lge.fmradio", "com.huawei.android.FMRadio",
        "com.sonyericsson.fmradio", "com.sony.fmradio", "com.oppo.fmradio", "com.coloros.fmradio", "com.realme.fmradio",
        "com.vivo.FMRadio", "com.asus.fmradio", "com.lenovo.fmradio", "com.mediatek.FMRadio", "com.tcl.fmradio", "com.qualcomm.fmradio"
    )
    fun openFm(ctx: Context): Boolean {
        val pm = ctx.packageManager
        for (p in fmPackages) pm.getLaunchIntentForPackage(p)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true }
        // procura qualquer app instalado com "FM" ou "Rádio" no nome
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hit = pm.queryIntentActivities(main, 0).firstOrNull {
            val label = it.loadLabel(pm).toString()
            it.activityInfo.packageName != ctx.packageName && Regex("(?i)(\\bfm\\b|fm radio|r[aá]dio fm|fmradio)").containsMatchIn(label + " " + it.activityInfo.packageName)
        }
        if (hit != null) { ctx.startActivity(Intent(main).setClassName(hit.activityInfo.packageName, hit.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true }
        return false
    }
}

private val RTAGS = listOf("" to "Mais ouvidas", "funk" to "Funk", "sertanejo" to "Sertanejo", "pagode" to "Pagode", "pop" to "Pop", "rock" to "Rock",
    "hip hop" to "Rap", "electronic" to "Eletrônica", "gospel" to "Gospel", "mpb" to "MPB", "news" to "Notícias", "sports" to "Esportes")

@Composable
fun RadioScreen(ui: PlayerUi, onBack: () -> Unit) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    var country by remember { mutableStateOf("BR") }
    var tag by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<Track>?>(null) }
    var favs by remember { mutableStateOf(RadioApi.favs()) }
    LaunchedEffect(country, tag, search) { list = null; list = RadioApi.stations(search, tag, country) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Rádio", onBack = onBack) }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Radio, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Rádio FM sem internet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Abre o rádio FM do seu celular. Conecte um fone com fio, ele funciona como antena.", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        ui.controller?.pause()
                        if (!RadioApi.openFm(ctx)) Toast.makeText(ctx, "Esse celular não tem rádio FM. Use as rádios pela internet abaixo.", Toast.LENGTH_LONG).show()
                    }) { Text("Abrir") }
                }
            }
        }
        item {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Procurar rádio (Jovem Pan, Antena 1...)") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true, shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search = q.trim() }))
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("BR" to "Brasil", "ZZ" to "Mundo").forEach { (k, n) -> FilterChip(country == k, { country = k }, { Text(n) }) }
                Spacer(Modifier.width(6.dp))
                RTAGS.forEach { (k, n) -> FilterChip(tag == k && search.isBlank(), { tag = k; search = ""; q = "" }, { Text(n) }) }
            }
        }
        if (favs.isNotEmpty()) {
            item { Text("Suas rádios", Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            itemsIndexed(favs, key = { _, t -> "f" + t.id }) { i, t -> StationRow(t, true, ui.mediaId == t.mediaId, { ui.playOnline(favs, i) }) { RadioApi.toggleFav(t); favs = RadioApi.favs() } }
        }
        item {
            Text(if (search.isNotBlank()) "Resultados pra \"$search\"" else (RTAGS.firstOrNull { it.first == tag }?.second ?: "") + if (country == "BR") " no Brasil" else " no mundo",
                Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        val l = list
        when {
            l == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
            l.isEmpty() -> item { Text("Nenhuma rádio encontrada. Precisa de internet pra essa lista.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else -> itemsIndexed(l, key = { _, t -> "l" + t.id }) { i, t ->
                StationRow(t, favs.any { it.id == t.id }, ui.mediaId == t.mediaId, { ui.playOnline(l, i) }) { RadioApi.toggleFav(t); favs = RadioApi.favs() }
            }
        }
    }
}

@Composable
private fun StationRow(t: Track, fav: Boolean, playing: Boolean, onPlay: () -> Unit, onFav: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onPlay() }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(t.art.ifBlank { null }, Modifier.size(52.dp), RoundedCornerShape(10.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (playing) MaterialTheme.colorScheme.primary else Color.Unspecified)
            Text(if (playing) "AO VIVO · ${t.artist}" else t.artist, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onFav) { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = MaterialTheme.colorScheme.primary) }
    }
}
