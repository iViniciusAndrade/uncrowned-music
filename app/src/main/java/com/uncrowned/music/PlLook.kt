@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.uncrowned.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/* ---------- Playlists personalizadas: foto, cor, enfeite, estampa, fonte, formato e descrição ----------
   Vale pras playlists online ("op:nome") e pras offline ("lp:nome"). A foto escolhida é recortada
   quadrada e guardada na pasta do app (continua aparecendo sem internet). */
data class Look(
    val img: String = "", val color: Int = 0, val emoji: String = "", val pattern: String = "",
    val font: String = "", val shape: String = "rounded", val desc: String = "", val mosaic: Boolean = false
) {
    fun json() = JSONObject().put("img", img).put("c", color).put("e", emoji).put("p", pattern).put("f", font).put("s", shape).put("d", desc).put("m", mosaic)
    companion object {
        fun from(o: JSONObject?) = if (o == null) Look() else Look(o.optString("img"), o.optInt("c"), o.optString("e"), o.optString("p"), o.optString("f"), o.optString("s", "rounded").ifBlank { "rounded" }, o.optString("d"), o.optBoolean("m"))
    }
}

object PlLook {
    var version by mutableIntStateOf(0)
    private fun all() = runCatching { JSONObject(Prefs.sp.getString("pllook", "{}") ?: "{}") }.getOrDefault(JSONObject())
    fun get(key: String): Look = Look.from(all().optJSONObject(key))
    fun set(key: String, l: Look) { val j = all(); j.put(key, l.json()); Prefs.sp.edit().putString("pllook", j.toString()).apply(); version++ }
    fun update(key: String, f: (Look) -> Look) = set(key, f(get(key)))
    fun move(from: String, to: String) { val j = all(); j.optJSONObject(from)?.let { j.put(to, it); j.remove(from) }; Prefs.sp.edit().putString("pllook", j.toString()).apply(); version++ }
    fun delete(key: String) { val j = all(); j.optJSONObject(key)?.optString("img")?.let { dropImg(it) }; j.remove(key); Prefs.sp.edit().putString("pllook", j.toString()).apply(); version++ }
    fun dropImg(path: String) { if (path.contains("/plcovers/")) runCatching { File(path).delete() } }

    /* recorta no meio (quadrada), reduz pra no máximo 900 px e guarda em JPEG */
    suspend fun saveImage(c: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1; while (minOf(o.outWidth, o.outHeight) / (sample * 2) >= 900) sample *= 2
            val bmp = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
            val side = minOf(bmp.width, bmp.height)
            val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            val out = if (side > 900) Bitmap.createScaledBitmap(sq, 900, 900, true) else sq
            val dir = File(c.filesDir, "plcovers").apply { mkdirs() }
            val f = File(dir, "%016x.jpg".format(Random.nextLong()))
            f.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            f.absolutePath
        }.getOrNull()
    }

    val COLORS = listOf(0xFFFF1F3D, 0xFFFF5FA8, 0xFFC77DFF, 0xFF7B5CFF, 0xFF2B6BFF, 0xFF25C4F4, 0xFF2EC4B6, 0xFF39D353,
        0xFFC6F432, 0xFFFFD23F, 0xFFFF9F1C, 0xFFFF6B35, 0xFFA8763E, 0xFF8E8E93, 0xFFF2F2F2, 0xFF111111).map { it.toInt() }
    val EMOJIS = listOf("✨", "💖", "🖤", "💀", "🔥", "🌙", "⭐", "🌸", "🦋", "🍒", "🎀", "👑", "⚡", "🌈", "💿", "🎧", "🎸", "🥀", "🌹", "🍓",
        "🐱", "🐮", "👻", "🕷️", "🗡️", "⛓️", "🩸", "🌊", "🌴", "☁️", "🍀", "🍄", "💎", "🪐", "🌻", "🍑", "🧸", "😈", "🤍", "💜")
    val PATTERNS = listOf("" to "Nenhuma", "sparkle" to "Brilhinhos", "hearts" to "Corações", "stars" to "Estrelas", "flowers" to "Florzinhas",
        "skulls" to "Caveirinhas", "dots" to "Bolinhas", "stripes" to "Listras", "checker" to "Xadrez", "waves" to "Ondas", "glitter" to "Glitter")
    val FONTS = listOf("" to "Padrão", "black" to "Grossona", "serif" to "Elegante", "hand" to "Escrita à mão", "mono" to "Máquina", "retro" to "Retrô", "gothic" to "Gótica")
    val SHAPES = listOf("rounded" to "Arredondada", "square" to "Quadrada", "circle" to "Círculo", "vinyl" to "Vinil", "polaroid" to "Polaroid", "tilt" to "Torta")
}

@Composable
fun lookColor(l: Look): Color = if (l.color != 0) Color(l.color) else MaterialTheme.colorScheme.primary

fun lookFont(f: String): Pair<FontFamily?, FontWeight> = when (f) {
    "black" -> null to FontWeight.Black
    "serif" -> FontFamily.Serif to FontWeight.Bold
    "hand" -> FontFamily.Cursive to FontWeight.Bold
    "mono", "retro" -> FontFamily.Monospace to FontWeight.Black
    "gothic" -> FontFamily.Serif to FontWeight.Black
    else -> null to FontWeight.Bold
}

/* capa da playlist: foto escolhida > capa que veio > mosaico com as 4 primeiras capas */
@Composable
fun PlCover(look: Look, arts: List<Any?>, size: Dp, cover: String = "") {
    val col = lookColor(look)
    val shape: Shape = when (look.shape) { "square" -> RoundedCornerShape(3.dp); "circle", "vinyl" -> CircleShape; "tilt" -> RoundedCornerShape(size * 0.12f); else -> RoundedCornerShape(size * 0.08f) }
    val img: Any? = look.img.ifBlank { null }?.let { File(it) } ?: cover.ifBlank { null }?.takeIf { !look.mosaic }
    val real = arts.filterNotNull().distinct()
    var m = Modifier.size(size)
    if (look.shape == "tilt") m = m.rotate(-6f)
    if (look.shape == "polaroid") m = m.rotate(-3f)
    Box(m) {
        val inner = if (look.shape == "polaroid") Modifier.fillMaxSize().shadow(10.dp, RoundedCornerShape(4.dp)).background(Color(0xFFFBFAF6), RoundedCornerShape(4.dp)).padding(start = size * 0.05f, end = size * 0.05f, top = size * 0.05f, bottom = size * 0.17f)
            else if (look.shape == "vinyl") Modifier.fillMaxSize().shadow(12.dp, CircleShape).background(Color(0xFF0B0B0B), CircleShape).padding(size * 0.06f)
            else Modifier.fillMaxSize().shadow(12.dp, shape)
        Box(inner) {
            val box = Modifier.fillMaxSize().clip(if (look.shape == "polaroid") RoundedCornerShape(2.dp) else shape).background(col.copy(alpha = .35f).compositeOver(Color(0xFF111111)))
            when {
                img != null -> Art(img, Modifier.fillMaxSize(), if (look.shape == "polaroid") RoundedCornerShape(2.dp) else shape)
                real.size >= 4 -> Column(box) {
                    Row(Modifier.weight(1f)) { Art(real[0], Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(0.dp)); Art(real[1], Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(0.dp)) }
                    Row(Modifier.weight(1f)) { Art(real[2], Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(0.dp)); Art(real[3], Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(0.dp)) }
                }
                real.isNotEmpty() -> Art(real[0], Modifier.fillMaxSize(), shape)
                else -> Box(box, Alignment.Center) {
                    if (look.emoji.isNotBlank()) Text(look.emoji, fontSize = (size.value * 0.4f).sp)
                    else Icon(Icons.Rounded.QueueMusic, null, Modifier.fillMaxSize(0.42f), tint = Color.White.copy(alpha = .85f))
                }
            }
            if (look.shape == "vinyl") Box(Modifier.align(Alignment.Center).size(size * 0.14f).clip(CircleShape).background(Color(0xFF0B0B0B)).border(2.dp, Color.White.copy(alpha = .15f), CircleShape))
        }
        if (look.emoji.isNotBlank() && (img != null || real.isNotEmpty())) Text(look.emoji, Modifier.align(Alignment.BottomEnd).offset(x = size * 0.08f, y = size * 0.08f).rotate(12f), fontSize = (size.value * 0.2f).coerceIn(12f, 44f).sp)
    }
}

/* estampa desenhada por cima do fundo colorido */
@Composable
fun PatternLayer(pattern: String, tint: Color, modifier: Modifier) {
    if (pattern.isBlank()) return
    val glyph = when (pattern) { "sparkle" -> listOf("✨", "✦", "✧"); "hearts" -> listOf("💖", "🤍"); "stars" -> listOf("★", "☆"); "flowers" -> listOf("🌸", "🌼"); "skulls" -> listOf("💀", "✝"); else -> emptyList() }
    Canvas(modifier) {
        val w = size.width; val h = size.height; val a = 0.30f
        when (pattern) {
            "dots" -> { val step = 22.dp.toPx(); var y = step / 2; while (y < h) { var x = step / 2; while (x < w) { drawCircle(Color.White.copy(alpha = a + .1f), 2.5.dp.toPx(), Offset(x, y)); x += step }; y += step } }
            "stripes" -> rotate(45f) { val step = 26.dp.toPx(); var x = -h * 2; while (x < w * 2) { drawRect(Color.White.copy(alpha = a), Offset(x, -h * 2), androidx.compose.ui.geometry.Size(10.dp.toPx(), h * 5)); x += step } }
            "checker" -> { val s = 18.dp.toPx(); var y = 0f; var r = 0; while (y < h) { var x = if (r % 2 == 0) 0f else s; while (x < w) { drawRect(Color.White.copy(alpha = .14f), Offset(x, y), androidx.compose.ui.geometry.Size(s, s)); x += s * 2 }; y += s; r++ } }
            "waves" -> { val step = 24.dp.toPx(); var y = step; while (y < h) { val p = androidx.compose.ui.graphics.Path(); p.moveTo(0f, y); var x = 0f; while (x < w) { p.quadraticTo(x + 15.dp.toPx(), y - 12.dp.toPx(), x + 30.dp.toPx(), y); p.quadraticTo(x + 45.dp.toPx(), y + 12.dp.toPx(), x + 60.dp.toPx(), y); x += 60.dp.toPx() }; drawPath(p, Color.White.copy(alpha = a), style = Stroke(2.dp.toPx())); y += step } }
            "glitter" -> { val r = Random(7); repeat(160) { drawCircle((if (it % 2 == 0) Color.White else tint).copy(alpha = .35f + r.nextFloat() * .4f), (0.8f + r.nextFloat() * 1.6f).dp.toPx(), Offset(r.nextFloat() * w, r.nextFloat() * h)) } }
            else -> {
                if (glyph.isEmpty()) return@Canvas
                val paint = android.graphics.Paint().apply { isAntiAlias = true; textSize = 20.dp.toPx(); color = android.graphics.Color.WHITE; alpha = (255 * 0.45f).toInt() }
                val step = 46.dp.toPx(); var y = step * 0.6f; var row = 0
                while (y < h + step) { var x = if (row % 2 == 0) 6.dp.toPx() else step / 2; var i = row
                    while (x < w) { paint.textSize = (if (i % 2 == 0) 20 else 13).dp.toPx(); drawContext.canvas.nativeCanvas.drawText(glyph[i % glyph.size], x, y, paint); x += step; i++ }
                    y += step * 0.75f; row++ }
            }
        }
    }
}

/* topo da playlist: fundo na cor escolhida, estampa, capa, enfeite, nome com a fonte e a descrição */
@Composable
fun PlHero(look: Look, title: String, sub: String, arts: List<Any?>, cover: String = "", onEdit: (() -> Unit)? = null) {
    val col = lookColor(look)
    val (ff, fw) = lookFont(look.font)
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(col.copy(alpha = .9f).compositeOver(Color.Black), col.copy(alpha = .45f).compositeOver(Color.Black), col.copy(alpha = .15f).compositeOver(Color(0xFF070707)))))) {
        PatternLayer(look.pattern, col, Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                PlCover(look, arts, 132.dp, cover)
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    Text("PLAYLIST", color = Color.White.copy(alpha = .8f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                    Text(if (look.font == "retro") title.uppercase() else title, color = Color.White, fontSize = 26.sp, lineHeight = 29.sp, fontWeight = fw, fontFamily = ff,
                        fontStyle = if (look.font == "serif") FontStyle.Italic else FontStyle.Normal, letterSpacing = if (look.font == "retro") 2.sp else 0.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(sub, color = Color.White.copy(alpha = .8f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            if (look.desc.isNotBlank()) Text(look.desc, color = Color.White.copy(alpha = .9f), fontSize = 13.5.sp, modifier = Modifier.padding(top = 14.dp))
        }
        if (onEdit != null) Text("✨ Personalizar", Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .35f))
            .clickable(onClick = onEdit).padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/* miniatura pras listas (Biblioteca / Playlists) */
@Composable
fun PlThumb(key: String, arts: List<Any?>, cover: String = "") {
    PlLook.version
    val l = PlLook.get(key)
    Box(Modifier.size(52.dp)) {
        PlCover(l.copy(emoji = "", shape = if (l.shape in listOf("circle", "vinyl")) "circle" else "rounded"), arts, 52.dp, cover)
        if (l.emoji.isNotBlank()) Text(l.emoji, Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp), fontSize = 16.sp)
    }
}

/* tela de personalizar (folha que sobe de baixo); muda tudo na hora */
@Composable
fun PlEditSheet(key: String, name: String, arts: List<Any?>, cover: String, onRename: (String) -> Boolean, onDismiss: () -> Unit) {
    PlLook.version
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l = PlLook.get(key)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val p = PlLook.saveImage(ctx, uri)
            if (p == null) android.widget.Toast.makeText(ctx, "Não consegui abrir essa imagem", android.widget.Toast.LENGTH_SHORT).show()
            else { PlLook.dropImg(l.img); PlLook.update(key) { it.copy(img = p, mosaic = false) } }
        }
    }
    var nm by remember { mutableStateOf(name) }
    var desc by remember { mutableStateOf(l.desc) }
    var emo by remember { mutableStateOf(if (l.emoji in PlLook.EMOJIS) "" else l.emoji) }
    ModalBottomSheet(onDismissRequest = { if (desc != l.desc) PlLook.update(key) { it.copy(desc = desc.trim()) }; onDismiss() }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 28.dp)) {
            Text("Personalizar playlist", Modifier.padding(horizontal = 20.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            PlHero(l.copy(desc = desc), name, "Prévia", arts, cover)
            @Composable fun T(s: String) = Text(s, Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            T("Nome")
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(nm, { nm = it }, Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(12.dp))
                TextButton(onClick = { if (nm.trim() != name && !onRename(nm.trim())) android.widget.Toast.makeText(ctx, "Já tem uma playlist com esse nome", android.widget.Toast.LENGTH_SHORT).show() }, enabled = nm.trim().isNotBlank() && nm.trim() != name) { Text("Renomear") }
            }
            T("Foto da capa")
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Rounded.AddPhotoAlternate, null); Text(" Escolher foto") }
                OutlinedButton(onClick = { PlLook.dropImg(l.img); PlLook.update(key) { it.copy(img = "", mosaic = true) } }) { Text("Mosaico") }
                if (l.img.isNotBlank() || (cover.isNotBlank() && !l.mosaic)) OutlinedButton(onClick = { PlLook.dropImg(l.img); PlLook.update(key) { it.copy(img = "", mosaic = cover.isNotBlank()) } }) { Text("Tirar foto") }
            }
            T("Cor")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlLook.COLORS.forEach { c ->
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c)).border(if (l.color == c) 3.dp else 1.dp, if (l.color == c) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = .2f), CircleShape).clickable { PlLook.update(key) { it.copy(color = c) } })
                }
                Text("Cor do tema", Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { PlLook.update(key) { it.copy(color = 0) } }.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 12.sp)
            }
            T("Enfeite")
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (PlLook.EMOJIS + "∅").forEach { e ->
                    val on = if (e == "∅") l.emoji.isBlank() else l.emoji == e
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = .3f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { PlLook.update(key) { it.copy(emoji = if (e == "∅") "" else e) } }, Alignment.Center) { Text(e, fontSize = 19.sp) }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(emo, { emo = it.take(8) }, Modifier.width(200.dp), singleLine = true, placeholder = { Text("ou cola qualquer emoji") }, shape = RoundedCornerShape(12.dp))
                TextButton(onClick = { PlLook.update(key) { it.copy(emoji = emo.trim()) } }, enabled = emo.isNotBlank()) { Text("Usar") }
            }
            @Composable fun Chips(opts: List<Pair<String, String>>, cur: String, set: (String) -> Unit) =
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    opts.forEach { (k, n) -> FilterChip(cur == k, { set(k) }, { Text(n) }) }
                }
            T("Estampa do fundo"); Chips(PlLook.PATTERNS, l.pattern) { v -> PlLook.update(key) { it.copy(pattern = v) } }
            T("Fonte do nome")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlLook.FONTS.forEach { (k, n) -> val (ff, fw) = lookFont(k); FilterChip(l.font == k, { PlLook.update(key) { it.copy(font = k) } }, { Text(n, fontFamily = ff, fontWeight = fw) }) }
            }
            T("Formato da capa"); Chips(PlLook.SHAPES, l.shape) { v -> PlLook.update(key) { it.copy(shape = v) } }
            T("Descrição")
            OutlinedTextField(desc, { desc = it.take(240) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), placeholder = { Text("Uma frase sobre essa playlist") }, shape = RoundedCornerShape(12.dp), minLines = 2)
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { PlLook.dropImg(l.img); PlLook.set(key, Look()); desc = "" }) { Text("Voltar ao padrão") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { PlLook.update(key) { it.copy(desc = desc.trim()) }; onDismiss() }) { Text("Pronto") }
            }
        }
    }
}
