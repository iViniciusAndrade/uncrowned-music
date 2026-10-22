package com.uncrowned.music

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/* ======================= MAIS OUVIDAS ======================= */
@Composable
fun StatsScreen(ui: PlayerUi, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Listen.version
    var p by remember { mutableStateOf("month") }
    val st = remember(p, Listen.version) { Listen.stats(p) }
    var card by remember { mutableStateOf<Bitmap?>(null) }
    var making by remember { mutableStateOf(false) }
    fun make(kind: String, artist: Triple<String, Long, String>?) {
        if (making) return; making = true
        scope.launch { card = runCatching { ShareCard.make(ctx, kind, st, artist) }.getOrNull(); making = false
            if (card == null) Toast.makeText(ctx, "Não deu pra montar o card", Toast.LENGTH_SHORT).show() }
    }
    val top = st.songs.take(10); val arts = st.artists.take(10); val maxA = arts.firstOrNull()?.second ?: 1L
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Mais ouvidas", onBack = onBack) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Listen.PERIODS.forEach { (k, n) -> FilterChip(p == k, { p = k }, { Text(n) }) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(fmtH(st.total) to "ouvindo música", st.songs.size.toString() to "músicas", st.artists.size.toString() to "artistas").forEach { (b, s) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp)) {
                        Text(b, fontSize = 24.sp, fontWeight = FontWeight.Black, fontFamily = Skins.font, maxLines = 1)
                        Text(s, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (st.total < 60) { item { Empty("Ainda não deu tempo de contar. Ouça umas músicas que seus números aparecem aqui.") }; return@LazyColumn }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { make("fan", arts.firstOrNull()) }, enabled = arts.isNotEmpty()) { Icon(Icons.Rounded.Person, null); Text(" Meu card de fã") }
                Button(onClick = { make("wrap", null) }) { Icon(Icons.Rounded.BarChart, null); Text(" Card da retrospectiva") }
                if (top.isNotEmpty()) OutlinedButton(onClick = { playLTracks(ui, st.songs.take(50).map { it.first }, 0) }) { Icon(Icons.Rounded.PlayArrow, null); Text(" Tocar") }
            }
            if (making) LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        }
        item { Section("Top artistas") }
        itemsIndexed(arts, key = { _, a -> "a" + a.first }) { i, a ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.width(26.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Art(a.third.ifBlank { null }, Modifier.size(46.dp), CircleShape)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(a.first, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Box(Modifier.fillMaxWidth((a.second.toFloat() / maxA).coerceIn(0.04f, 1f)).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                }
                Text(fmtH(a.second), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { make("fan", a) }) { Icon(Icons.Rounded.IosShare, "Card de fã", tint = MaterialTheme.colorScheme.primary) }
            }
        }
        item { Section("Top músicas") }
        itemsIndexed(top, key = { _, s -> "s" + s.first.key }) { i, s ->
            Row(Modifier.fillMaxWidth().clickable { playLTracks(ui, top.map { it.first }, i) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", Modifier.width(26.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Art(s.first.art.ifBlank { null }, Modifier.size(46.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(s.first.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.first.artist, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(fmtH(s.second), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    card?.let { bmp -> CardDialog(bmp) { card = null } }
}

@Composable
private fun CardDialog(bmp: Bitmap, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onClose) {
        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(14.dp)))
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { ShareCard.save(ctx, bmp)?.let { u -> ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Compartilhar card")) } }) {
                    Icon(Icons.Rounded.Share, null); Text(" Compartilhar") }
                OutlinedButton(onClick = { if (ShareCard.save(ctx, bmp) != null) Toast.makeText(ctx, "Salvo em Imagens/Uncrowned", Toast.LENGTH_SHORT).show() }) { Text("Salvar") }
            }
            Text("Formato de story (Instagram, TikTok, WhatsApp)", Modifier.padding(top = 6.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* card 1080x1920 igual ao do PC */
object ShareCard {
    private suspend fun img(ctx: Context, src: String?): Bitmap? = if (src.isNullOrBlank()) null else withContext(Dispatchers.IO) {
        runCatching {
            val r = ImageLoader(ctx).execute(ImageRequest.Builder(ctx).data(src).allowHardware(false).size(900).build())
            (r as? SuccessResult)?.drawable?.toBitmap()
        }.getOrNull()
    }
    private fun cover(c: Canvas, b: Bitmap, l: Float, t: Float, w: Float, h: Float) {
        val s = maxOf(w / b.width, h / b.height); val bw = b.width * s; val bh = b.height * s
        c.save(); c.clipRect(l, t, l + w, t + h)
        c.drawBitmap(b, null, RectF(l + (w - bw) / 2, t + (h - bh) / 2, l + (w + bw) / 2, t + (h + bh) / 2), Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
    }
    private fun dom(b: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(b, 24, 24, true); var r = 0.0; var g = 0.0; var bl = 0.0; var w = 0.0
        for (y in 0 until 24) for (x in 0 until 24) { val p = s.getPixel(x, y); val R = (p shr 16) and 255; val G = (p shr 8) and 255; val B = p and 255
            val sat = (maxOf(R, G, B) - minOf(R, G, B) + 8).toDouble(); r += R * sat; g += G * sat; bl += B * sat; w += sat }
        return android.graphics.Color.rgb((r / w).toInt(), (g / w).toInt(), (bl / w).toInt())
    }
    private fun ell(p: Paint, t: String, max: Float): String { if (p.measureText(t) <= max) return t; var s = t; while (s.length > 1 && p.measureText("$s…") > max) s = s.dropLast(1); return "$s…" }

    suspend fun make(ctx: Context, kind: String, st: Listen.Stats, artist: Triple<String, Long, String>?): Bitmap = withContext(Dispatchers.Default) {
        val W = 1080f; val H = 1920f
        val bmp = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888); val c = Canvas(bmp)
        val main = img(ctx, if (kind == "fan") artist?.third else st.songs.firstOrNull()?.first?.art ?: st.artists.firstOrNull()?.third)
        val acc = main?.let { dom(it) } ?: (Skins.current?.accent?.toInt() ?: 0xFFFA2D48.toInt())
        val ar = android.graphics.Color.red(acc); val ag = android.graphics.Color.green(acc); val ab = android.graphics.Color.blue(acc)
        c.drawColor(0xFF07070A.toInt())
        // fundo: capa borrada (reduz e amplia) + gradiente
        main?.let { m -> val tiny = Bitmap.createScaledBitmap(m, 12, 20, true); val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 150 }; c.drawBitmap(tiny, null, RectF(-100f, -100f, W + 100, H + 100), p) }
        c.drawRect(0f, 0f, W, H, Paint().apply { shader = LinearGradient(0f, 0f, 0f, H, intArrayOf(android.graphics.Color.argb(90, ar, ag, ab), 0x8C08080C.toInt(), 0xF5050508.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP) })
        val bold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val tf = Typeface.create("sans-serif-black", Typeface.NORMAL)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; typeface = bold }
        p.textSize = 34f; p.alpha = 230; c.drawText(if (kind == "fan") "CARD DE FÃ" else "MINHA RETROSPECTIVA", 80f, 130f, p)
        p.textSize = 30f; p.alpha = 150; c.drawText(st.label.uppercase(), 80f, 175f, p); p.alpha = 255
        if (kind == "fan" && artist != null) {
            val sz = 660f; val ax = (W - sz) / 2; val ay = 260f
            c.drawCircle(W / 2, ay + sz / 2, sz / 2 + 30, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = acc; alpha = 110 })
            c.save(); c.clipPath(Path().apply { addCircle(W / 2, ay + sz / 2, sz / 2, Path.Direction.CW) })
            if (main != null) cover(c, main, ax, ay, sz, sz) else c.drawColor(0xFF222222.toInt()); c.restore()
            c.drawCircle(W / 2, ay + sz / 2, sz / 2 + 4, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 10f; color = 0xE6FFFFFF.toInt() })
            p.textAlign = Paint.Align.CENTER; p.typeface = tf
            var fs = 130f; p.textSize = fs; while (p.measureText(artist.first) > W - 140 && fs > 34) { fs -= 4; p.textSize = fs }
            c.drawText(artist.first, W / 2, 1060f, p)
            val songsA = st.songs.filter { it.first.artist == artist.first }
            listOf(fmtH(artist.second) to "ouvindo", songsA.size.toString() to if (songsA.size == 1) "música no seu top" else "músicas diferentes",
                "#" + (st.artists.indexOfFirst { it.first == artist.first } + 1) to "no seu ranking").forEachIndexed { i, (big, small) ->
                val cx = 180f + i * 360f
                p.typeface = tf; p.textSize = 80f; p.alpha = 255; c.drawText(big, cx, 1220f, p)
                p.typeface = bold; p.textSize = 28f; p.alpha = 170; c.drawText(small, cx, 1268f, p); p.alpha = 255
            }
            songsA.firstOrNull()?.let { s ->
                c.drawRoundRect(RectF(80f, 1350f, W - 80, 1550f), 36f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x14FFFFFF })
                img(ctx, s.first.art)?.let { b -> c.save(); c.clipPath(Path().apply { addRoundRect(RectF(110f, 1380f, 250f, 1520f), 20f, 20f, Path.Direction.CW) }); cover(c, b, 110f, 1380f, 140f, 140f); c.restore() }
                p.textAlign = Paint.Align.LEFT; p.typeface = bold; p.textSize = 26f; p.alpha = 160; c.drawText("A QUE VOCÊ MAIS OUVIU", 280f, 1430f, p)
                p.alpha = 255; p.textSize = 46f; c.drawText(ell(p, s.first.title, W - 400), 280f, 1490f, p)
            }
        } else {
            p.textAlign = Paint.Align.LEFT; p.typeface = tf; p.textSize = 180f; c.drawText(fmtH(st.total), 72f, 420f, p)
            p.typeface = bold; p.textSize = 38f; p.alpha = 180; c.drawText("ouvindo música", 82f, 480f, p); p.alpha = 255
            suspend fun sec(title: String, y0: Float, items: List<Triple<String, String, String>>, round: Boolean) {
                p.color = acc; p.textSize = 30f; c.drawText(title, 82f, y0, p); p.color = android.graphics.Color.WHITE
                items.forEachIndexed { i, (name, sub, art) ->
                    val y = y0 + 40 + i * 104
                    p.typeface = tf; p.textSize = 52f; p.alpha = 90; c.drawText("${i + 1}", 82f, y + 70, p); p.alpha = 255
                    val r = RectF(157f, y + 2, 253f, y + 98)
                    c.save(); c.clipPath(Path().apply { if (round) addCircle(205f, y + 50, 48f, Path.Direction.CW) else addRoundRect(r, 14f, 14f, Path.Direction.CW) })
                    val b = img(ctx, art); if (b != null) cover(c, b, r.left, r.top, 96f, 96f) else c.drawColor(0xFF333333.toInt()); c.restore()
                    p.typeface = bold; p.textSize = 40f; c.drawText(ell(p, name, 600f), 280f, y + 45, p)
                    p.textSize = 28f; p.alpha = 155; c.drawText(ell(p, sub, 600f), 280f, y + 88, p); p.alpha = 255
                }
            }
            sec("TOP MÚSICAS", 600f, st.songs.take(5).map { Triple(it.first.title, "${it.first.artist} · ${fmtH(it.second)}", it.first.art) }, false)
            sec("TOP ARTISTAS", 1200f, st.artists.take(4).map { Triple(it.first, fmtH(it.second), it.third) }, true)
        }
        // rodapé com a marca e a caveira do tema
        p.textAlign = Paint.Align.LEFT
        Skins.current?.let { sk -> runCatching { android.graphics.BitmapFactory.decodeResource(ctx.resources, sk.icon) }.getOrNull() }?.let { lg ->
            c.save(); c.clipPath(Path().apply { addRoundRect(RectF(80f, H - 170, 164f, H - 86), 20f, 20f, Path.Direction.CW) }); c.drawBitmap(lg, null, RectF(80f, H - 170, 164f, H - 86), Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
        }
        p.typeface = tf; p.textSize = 40f; c.drawText("Uncrowned Music", 186f, H - 122, p)
        p.typeface = bold; p.textSize = 26f; p.alpha = 140; c.drawText(SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")).format(Date()), 186f, H - 84, p)
        c.drawRect(W - 240, H - 132, W - 80, H - 122, Paint().apply { color = acc })
        bmp
    }

    fun save(ctx: Context, b: Bitmap): Uri? = runCatching {
        val name = "Uncrowned ${System.currentTimeMillis()}.png"
        val r = ctx.contentResolver
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Uncrowned")
        }
        val u = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: error("sem espaço")
        r.openOutputStream(u)!!.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        u
    }.getOrNull()
}

/* ======================= MOMENTOS ======================= */
private val DIAS = listOf("domingo", "segunda", "terça", "quarta", "quinta", "sexta", "sábado")
private val MESES = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
private val PERIODOS = listOf("madrugada" to "de madrugada", "manha" to "de manhã", "tarde" to "à tarde", "noite" to "à noite")
private fun periodOf(h: Int) = when { h < 6 -> 0; h < 12 -> 1; h < 18 -> 2; else -> 3 }

@Composable
fun MomentsScreen(ui: PlayerUi, onBack: () -> Unit) {
    Listen.version
    var open by remember { mutableStateOf<Pair<String, (Calendar) -> Boolean>?>(null) }
    open?.let { (t, f) -> MomentList(ui, t, f) { open = null }; return }
    val now = Calendar.getInstance(); val per = periodOf(now.get(Calendar.HOUR_OF_DAY)); val dow = now.get(Calendar.DAY_OF_WEEK) - 1
    val moms = remember(Listen.version) { Listen.momentList() }
    val grid = remember(moms) { Array(7) { IntArray(4) }.also { g -> val c = Calendar.getInstance(); moms.forEach { (ts, _) -> c.timeInMillis = ts * 1000; g[c.get(Calendar.DAY_OF_WEEK) - 1][periodOf(c.get(Calendar.HOUR_OF_DAY))]++ } } }
    val gmax = grid.maxOf { r -> r.max() }.coerceAtLeast(1)
    val months = remember(moms) { val c = Calendar.getInstance(); moms.map { (ts, _) -> c.timeInMillis = ts * 1000; c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) }.distinct().sortedDescending().take(12) }
    val prev = (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
    val ym = { c: Calendar, y: Int, m: Int -> c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m }
    val cards: List<Triple<String, String, (Calendar) -> Boolean>> = listOf(
        Triple("Suas músicas de ${DIAS[dow]} ${PERIODOS[per].second}", "O que você costuma ouvir nesse dia e horário") { c -> c.get(Calendar.DAY_OF_WEEK) - 1 == dow && periodOf(c.get(Calendar.HOUR_OF_DAY)) == per },
        Triple("O que eu ouvia em ${MESES[now.get(Calendar.MONTH)]}", "As mais ouvidas deste mês") { c -> ym(c, now.get(Calendar.YEAR), now.get(Calendar.MONTH)) },
        Triple("O que eu ouvia em ${MESES[prev.get(Calendar.MONTH)]}", "As mais ouvidas do mês passado") { c -> ym(c, prev.get(Calendar.YEAR), prev.get(Calendar.MONTH)) },
        Triple("Fins de semana", "Sábados e domingos") { c -> c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY },
        Triple("Madrugadas", "Da meia-noite às 6h") { c -> c.get(Calendar.HOUR_OF_DAY) < 6 },
        Triple("Manhãs", "Pra começar o dia") { c -> periodOf(c.get(Calendar.HOUR_OF_DAY)) == 1 })
    val acc = MaterialTheme.colorScheme.primary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Música do momento", onBack = onBack) }
        item { Text("O app lembra quando você ouviu cada música e monta playlists dos seus momentos. ${if (moms.isNotEmpty()) "${moms.size} músicas registradas." else "Começa a contar a partir de agora."}",
            Modifier.padding(horizontal = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(cards.chunked(2).size) { r ->
            Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                cards.chunked(2)[r].forEachIndexed { j, (t, sub, f) ->
                    val i = r * 2 + j; val hue = listOf(0f, 40f, 160f, 210f, 280f, 320f)[i % 6]
                    Box(Modifier.weight(1f).height(120.dp).clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(acc.copy(alpha = .9f), Color.hsv(hue, .55f, .35f))))
                        .clickable { open = t to f }.padding(14.dp)) {
                        Column { Text(t, fontWeight = FontWeight.Black, fontFamily = Skins.font, color = Color.White, fontSize = 16.sp, lineHeight = 19.sp, maxLines = 3)
                            Text(sub, color = Color.White.copy(alpha = .75f), fontSize = 11.sp, maxLines = 2) }
                        Icon(Icons.Rounded.PlayCircle, null, Modifier.align(Alignment.BottomEnd).size(30.dp), tint = Color.White)
                    }
                }
                if (cards.chunked(2)[r].size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (months.isNotEmpty()) {
            item { Section("Por mês") }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    months.forEach { k -> val y = k / 100; val m = k % 100
                        AssistChip({ open = "O que eu ouvia em ${MESES[m]}${if (y != now.get(Calendar.YEAR)) " de $y" else ""}" to { c -> ym(c, y, m) } },
                            { Text(MESES[m].replaceFirstChar { it.uppercase() } + if (y != now.get(Calendar.YEAR)) " $y" else "") }) }
                }
            }
        }
        item { Section("Dia e horário") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row { Spacer(Modifier.width(44.dp)); listOf("madrug.", "manhã", "tarde", "noite").forEach { Text(it, Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                for (d in 0 until 7) Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(DIAS[d].take(3), Modifier.width(44.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (pi in 0 until 4) { val v = grid[d][pi]
                        Box(Modifier.weight(1f).padding(horizontal = 3.dp).height(34.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (v == 0) MaterialTheme.colorScheme.surfaceContainer else acc.copy(alpha = .15f + .85f * v / gmax))
                            .clickable(enabled = v > 0) { open = "Suas músicas de ${DIAS[d]} ${PERIODOS[pi].second}" to { c -> c.get(Calendar.DAY_OF_WEEK) - 1 == d && periodOf(c.get(Calendar.HOUR_OF_DAY)) == pi } },
                            contentAlignment = Alignment.Center) { if (v > 0) Text("$v", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MomentList(ui: PlayerUi, title: String, f: (Calendar) -> Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    val l = remember(title) { Listen.momentTop(f) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header(title, onBack = onBack) }
        if (l.isEmpty()) item { Empty("Nada registrado nesse momento ainda.") }
        else item { Button(onClick = { playLTracks(ui, l, 0) }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) { Icon(Icons.Rounded.PlayArrow, null); Text(" Tocar") } }
        itemsIndexed(l, key = { _, t -> t.key }) { i, t ->
            Row(Modifier.fillMaxWidth().clickable { playLTracks(ui, l, i) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(t.art.ifBlank { null }, Modifier.size(46.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, fontSize = 12.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
