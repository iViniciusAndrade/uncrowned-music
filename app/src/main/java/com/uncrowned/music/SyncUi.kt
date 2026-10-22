package com.uncrowned.music

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/* Botão Sincronizar do player online: salva a música, a fila, ou mostra o que fazer quando não acha */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSheet(v: YtVideo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Sync.version
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Sync.Res?>(null) }
    val done = Sync.syncedUri(v) != null
    val queue = YtPlayer.queue
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Sincronizar", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Salva no celular pra ouvir sem internet", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            val r = result
            when {
                busy -> Row(Modifier.padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp); Spacer(Modifier.width(14.dp))
                    Text("Procurando \"${v.title}\" em todas as fontes abertas…", fontSize = 14.sp)
                }
                r != null && r.ok -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Salva offline ✓", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(if (r.alt) "Peguei a versão liberada do ${r.from}. Está em Músicas e na pasta Música/Uncrowned." else "Está em Músicas e na pasta Música/Uncrowned.", fontSize = 13.sp)
                    }
                }
                r != null -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(if (r.error.isNotBlank()) r.error else "Não achei liberada pra baixar", fontWeight = FontWeight.Bold)
                        if (r.error.isBlank()) Text("Procurei no Audius, SoundCloud (download do artista), Internet Archive, ccMixter e Wikimedia Commons. Ela ficou em Pra sincronizar e o celular tenta de novo sozinho no Wi-Fi, porque artistas sobem músicas novas toda hora.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { Nav.sim = v; onDismiss() }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Search, null); Text("  Ver parecidas que baixam") }
                        OutlinedButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://bandcamp.com/search?q=" + Uri.encode("${v.channel} ${v.title}")))) }, Modifier.fillMaxWidth()) { Text("Comprar no Bandcamp (vem com o arquivo)") }
                    }
                }
                else -> {
                    SyncOpt(if (done) Icons.Rounded.DownloadDone else Icons.Rounded.Download, if (done) "Essa já está offline" else "Salvar esta música", v.title, !done) {
                        busy = true; scope.launch { result = Sync.syncOne(v); busy = false }
                    }
                    SyncOpt(Icons.Rounded.Search, "Versões parecidas que baixam", "Remix, cover, ao vivo, mesmo artista", true) { Nav.sim = v; onDismiss() }
                    SyncOpt(Icons.Rounded.QueueMusic, "Sincronizar a fila inteira", "${queue.size} músicas • continua mesmo com o app fechado", queue.isNotEmpty()) {
                        queue.forEach { if (Sync.syncedUri(it) == null) Sync.addPending(it) }
                        Sync.runNow(ctx); Toast.makeText(ctx, "Sincronizando ${queue.size} músicas em segundo plano. Te aviso quando terminar.", Toast.LENGTH_LONG).show(); onDismiss()
                    }
                    SyncOpt(Icons.Rounded.History, "Ver Pra sincronizar", "${Sync.pending().size} pendentes", true) { Nav.page = "pend"; onDismiss() }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Salva direto quando o artista libera. Se não liberou, procura a mesma música no Audius, SoundCloud, Internet Archive, ccMixter e Wikimedia Commons. Todo arquivo é conferido antes de salvar: só entra áudio de verdade.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun SyncOpt(icon: ImageVector, title: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 12.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) .18f else .06f)), Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .4f))
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* músicas que não tinham versão liberada */
@Composable
fun PendingScreen(ui: PlayerUi, onBack: () -> Unit) {
    val ctx = LocalContext.current
    Sync.version
    val l = Sync.pending()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Pra sincronizar", onBack = onBack) }
        item {
            Text("Músicas que ainda não tinham versão liberada pra salvar. ${if (Sync.autoWifi) "O celular tenta de novo sozinho quando estiver no Wi-Fi." else "Ligue em Ajustes → Online pra tentar sozinho no Wi-Fi."}",
                Modifier.padding(horizontal = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (l.isNotEmpty()) item {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { Sync.runNow(ctx); Toast.makeText(ctx, "Tentando de novo em segundo plano. Te aviso se achar.", Toast.LENGTH_LONG).show() }) { Icon(Icons.Rounded.Refresh, null); Text(" Tentar agora") }
                OutlinedButton(onClick = { Sync.clearPending() }) { Text("Limpar lista") }
            }
        }
        if (l.isEmpty()) item { Empty("Nada pendente. Tudo que você sincronizou foi salvo.") }
        itemsIndexed(l, key = { _, v -> Sync.key(v) }) { i, v ->
            Row(Modifier.fillMaxWidth().clickable { ui.controller?.pause(); YtPlayer.play(l, i) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(v.thumb.ifBlank { null }, Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(v.channel, maxLines = 1, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { Nav.sim = v }) { Text("Parecidas") }
                IconButton(onClick = { Sync.removePending(v) }) { Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/* parecidas que dá pra baixar */
@Composable
fun SimilarScreen(ui: PlayerUi, v: YtVideo, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember(v) { mutableStateOf<List<Sync.Cand>?>(null) }
    val state = remember(v) { mutableStateMapOf<String, String>() }
    LaunchedEffect(v) { list = Sync.similar(v) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Parecidas que baixam", onBack = onBack) }
        item { Text("Remixes, covers, versões ao vivo e outras do mesmo artista com download liberado, pra \"${v.title}\".", Modifier.padding(horizontal = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val l = list
        when {
            l == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
            l.isEmpty() -> item { Empty("Nenhuma versão liberada por enquanto. Ela continua em Pra sincronizar e o celular tenta de novo sozinho.") }
            else -> itemsIndexed(l, key = { _, c -> c.src + c.id }) { i, c ->
                Row(Modifier.fillMaxWidth().clickable {
                    if (c.src == "sc") { ui.controller?.pause(); YtPlayer.play(listOf(YtVideo(c.id, c.title, c.artist, c.art, "sc")), 0) }
                    else ui.playOnline(l.filter { it.src != "sc" }.map { it.toTrack() }, l.filter { it.src != "sc" }.indexOf(c))
                }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Art(c.art.ifBlank { null }, Modifier.size(48.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text("${c.from} • ${c.artist}", maxLines = 1, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val st = state[c.src + c.id]
                    FilledTonalButton(enabled = st == null || st == "erro", onClick = {
                        state[c.src + c.id] = "…"
                        scope.launch {
                            val r = Sync.pick(c)
                            state[c.src + c.id] = if (r.isSuccess) "ok" else "erro"
                            Toast.makeText(ctx, if (r.isSuccess) "\"${c.title}\" salva em Música/Uncrowned" else "Não deu: ${r.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                            if (r.isSuccess) Library.load(ctx)
                        }
                    }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        when (st) { "…" -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); "ok" -> Text("Salva ✓"); else -> { Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Text(" Baixar") } }
                    }
                }
            }
        }
    }
}

/* guardadas dentro do app (tocam sem internet) */
@Composable
fun KeptScreen(ui: PlayerUi, onBack: () -> Unit) {
    Sync.version
    val l = Sync.kept()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Guardadas no app", onBack = onBack) }
        item { Text("Tocam sem internet. São faixas de fontes abertas que o artista não liberou como arquivo, então ficam só dentro do app.", Modifier.padding(horizontal = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (l.isNotEmpty()) item { Button(onClick = { ui.playOnline(l, 0) }, Modifier.padding(16.dp)) { Icon(Icons.Rounded.PlayArrow, null); Text(" Tocar todas") } }
        if (l.isEmpty()) item { Empty("Nada guardado ainda. Toque uma música do Audius e use o botão Offline no player.") }
        itemsIndexed(l, key = { _, t -> t.id }) { i, t ->
            Row(Modifier.fillMaxWidth().clickable { ui.playOnline(l, i) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(t.art.ifBlank { null }, Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(t.artist, maxLines = 1, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { Sync.unkeep(t) }) { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
