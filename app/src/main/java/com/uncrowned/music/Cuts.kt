package com.uncrowned.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/* Recortes: você marca o começo e o fim de um trecho de um vídeo (ex.: o remix de um montage)
   e ele vira um item da sua coleção. Tocar o recorte toca só aquele pedaço, direto do vídeo original. */
object Cuts {
    data class Cut(val id: String, val vid: String, val name: String, val artist: String, val thumb: String, val start: Float, val end: Float) {
        fun json() = JSONObject().put("id", id).put("v", vid).put("n", name).put("a", artist).put("t", thumb).put("s", start.toDouble()).put("e", end.toDouble())
        companion object { fun from(o: JSONObject) = Cut(o.getString("id"), o.getString("v"), o.optString("n"), o.optString("a"), o.optString("t"), o.optDouble("s").toFloat(), o.optDouble("e").toFloat()) }
    }
    var version by mutableIntStateOf(0)
    fun list(): List<Cut> = runCatching { JSONArray(Prefs.sp.getString("cuts", "[]")).let { a -> List(a.length()) { Cut.from(a.getJSONObject(it)) } } }.getOrDefault(emptyList())
    private fun save(l: List<Cut>) { Prefs.sp.edit().putString("cuts", JSONArray(l.map { it.json() }).toString()).apply(); version++ }
    fun add(c: Cut) = save(listOf(c) + list())
    fun remove(id: String) = save(list().filter { it.id != id })
    fun rename(id: String, n: String) = save(list().map { if (it.id == id) it.copy(name = n) else it })
}

private fun mmss(s: Float) = "%d:%02d".format((s / 60).toInt(), (s % 60).toInt())

/* tela de marcar o trecho (abre pelo botão Recorte do player do YouTube) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CutSheet(v: YtVideo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val dur = YtPlayer.duration.coerceAtLeast(1f)
    val now = YtPlayer.time.coerceIn(0f, dur)
    var range by remember { mutableStateOf(now..(now + 90f).coerceAtMost(dur)) }
    var name by remember { mutableStateOf("Trecho de ${v.title}".take(80)) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Recorte", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = Skins.font)
            Text("Marque onde o trecho começa e termina. Ele fica salvo nos seus Recortes e toca só essa parte.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            RangeSlider(range, { range = it }, valueRange = 0f..dur, modifier = Modifier.padding(top = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Começo ${mmss(range.start)}", fontWeight = FontWeight.SemiBold); Spacer(Modifier.weight(1f))
                Text("Duração ${mmss(range.endInclusive - range.start)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.weight(1f))
                Text("Fim ${mmss(range.endInclusive)}", fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { val t = YtPlayer.time.coerceIn(0f, dur); range = t..maxOf(t + 1f, range.endInclusive).coerceAtMost(dur) }) { Text("Começo = agora") }
                OutlinedButton(onClick = { val t = YtPlayer.time.coerceIn(0f, dur); range = minOf(range.start, (t - 1f).coerceAtLeast(0f))..t }) { Text("Fim = agora") }
            }
            TextButton(onClick = { YtPlayer.seek(range.start) }) { Icon(Icons.Rounded.PlayArrow, null); Text(" Ouvir do começo do trecho") }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome do recorte") }, singleLine = true, shape = RoundedCornerShape(12.dp))
            Button(onClick = {
                Cuts.add(Cuts.Cut(System.currentTimeMillis().toString(), v.id, name.ifBlank { v.title }, v.channel, v.thumb, range.start, range.endInclusive))
                android.widget.Toast.makeText(ctx, "Recorte salvo em Biblioteca → Recortes", android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = range.endInclusive - range.start >= 1f) { Icon(Icons.Rounded.ContentCut, null); Text(" Salvar recorte") }
        }
    }
}

/* coleção de recortes */
@Composable
fun CutsScreen(ui: PlayerUi, onBack: () -> Unit) {
    Cuts.version
    val l = Cuts.list()
    var edit by remember { mutableStateOf<Cuts.Cut?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Recortes", onBack = onBack) }
        item { Text("Trechos que você marcou nos vídeos. Tocam só a parte escolhida, um atrás do outro.", Modifier.padding(horizontal = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (l.isNotEmpty()) item {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { ui.controller?.pause(); YtPlayer.playCuts(l, 0) }) { Icon(Icons.Rounded.PlayArrow, null); Text(" Tocar todos") }
                OutlinedButton(onClick = { ui.controller?.pause(); YtPlayer.playCuts(l.shuffled(), 0) }) { Icon(Icons.Rounded.Shuffle, null) }
            }
        }
        if (l.isEmpty()) item { Empty("Nenhum recorte ainda. No player do YouTube, toque em Recorte pra marcar um trecho.") }
        itemsIndexed(l, key = { _, c -> c.id }) { i, c ->
            Row(Modifier.fillMaxWidth().clickable { ui.controller?.pause(); YtPlayer.playCuts(l, i) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(c.thumb.ifBlank { null }, Modifier.size(width = 72.dp, height = 44.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("${mmss(c.start)} – ${mmss(c.end)} • ${mmss(c.end - c.start)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { edit = c }) { Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { Cuts.remove(c.id) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    edit?.let { c ->
        var n by remember(c.id) { mutableStateOf(c.name) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text("Renomear recorte") },
            text = { OutlinedTextField(n, { n = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { Cuts.rename(c.id, n.ifBlank { c.name }); edit = null }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancelar") } })
    }
}
