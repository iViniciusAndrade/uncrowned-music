package com.uncrowned.music

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.tanh

/* Slowed / Nightcore: cria versões das suas músicas offline com outra velocidade e tom (os dois juntos,
   igual disco em outra rotação), com reverb, graves e som K7/vinil opcionais. Salva em Música/Uncrowned. */
object Exporter {
    data class Fx(val rate: Double, val reverb: Boolean, val bass: Boolean, val retro: String, val label: String)
    var running by mutableStateOf(false); private set
    var index by mutableIntStateOf(0); private set
    var total by mutableIntStateOf(0); private set
    var pct by mutableFloatStateOf(0f); private set
    var current by mutableStateOf(""); private set
    @Volatile private var stopFlag = false
    fun stop() { stopFlag = true }

    fun run(ctx: Context, songs: List<Song>, fx: Fx) {
        if (running) return
        val app = ctx.applicationContext
        running = true; stopFlag = false; total = songs.size; index = 0
        CoroutineScope(Dispatchers.Default).launch {
            var ok = 0; var err = ""
            for ((i, s) in songs.withIndex()) {
                if (stopFlag) break
                withContext(Dispatchers.Main) { index = i; pct = 0f; current = s.title }
                runCatching { one(app, s, fx) }.onSuccess { ok++ }.onFailure { err = it.message ?: "erro" }
            }
            withContext(Dispatchers.Main) {
                running = false
                Toast.makeText(app, if (ok > 0) "$ok ${if (ok == 1) "música salva" else "músicas salvas"} em Música/Uncrowned como \"${fx.label}\"" else "Não deu pra exportar: $err", Toast.LENGTH_LONG).show()
                Library.load(app)
            }
        }
    }

    private class Biquad(f: Double, gainDb: Double, sr: Int) { // grave extra (low shelf)
        private val a = Math.pow(10.0, gainDb / 40); private val w = 2 * Math.PI * f / sr; private val cw = Math.cos(w); private val sw = Math.sin(w)
        private val al = sw / 2 * Math.sqrt(2.0); private val s2 = 2 * Math.sqrt(a) * al
        private val a0 = (a + 1) + (a - 1) * cw + s2
        private val b0 = a * ((a + 1) - (a - 1) * cw + s2) / a0; private val b1 = 2 * a * ((a - 1) - (a + 1) * cw) / a0; private val b2 = a * ((a + 1) - (a - 1) * cw - s2) / a0
        private val a1 = -2 * ((a - 1) + (a + 1) * cw) / a0; private val a2 = ((a + 1) + (a - 1) * cw - s2) / a0
        private val z1 = DoubleArray(8); private val z2 = DoubleArray(8)
        fun run(x: Double, c: Int): Double { val y = b0 * x + z1[c]; z1[c] = b1 * x - a1 * y + z2[c]; z2[c] = b2 * x - a2 * y; return y }
    }

    private fun one(ctx: Context, s: Song, fx: Fx) {
        val ex = MediaExtractor(); ex.setDataSource(ctx, s.uri, null)
        val ti = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: error("arquivo sem áudio")
        ex.selectTrack(ti)
        val inFmt = ex.getTrackFormat(ti)
        val durUs = if (inFmt.containsKey(MediaFormat.KEY_DURATION)) inFmt.getLong(MediaFormat.KEY_DURATION) else s.duration * 1000
        val dec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(inFmt, null, null, 0); dec.start()
        val tmp = File(ctx.cacheDir, "export_${System.nanoTime()}.m4a")
        var enc: MediaCodec? = null; var mux: MediaMuxer? = null; var muxTrack = -1; var muxOn = false
        var sr = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var chIn = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT); var ch = minOf(2, chIn)
        // estado do processamento
        var src = Array(2) { DoubleArray(0) }; var srcLen = 0; var pos = 0.0
        var bass: Biquad? = null; var retro: RetroDsp? = null
        var rev = Array(2) { DoubleArray(1) }; var rp = 0; val taps = intArrayOf(60, 110, 170); val decays = doubleArrayOf(0.32, 0.22, 0.12)
        var outFrames = 0L; var pend = ShortArray(1 shl 16); var pendN = 0
        val info = MediaCodec.BufferInfo()
        var inDone = false; var decDone = false; var encInDone = false; var encDone = false

        fun setup() {
            ch = minOf(2, chIn)
            bass = if (fx.bass) Biquad(90.0, 5.0, sr) else null
            retro = if (fx.retro.isNotEmpty()) RetroDsp(sr, ch).also { it.setMode(fx.retro) } else null
            rev = Array(2) { DoubleArray((sr * 0.2).toInt() + 2) }; rp = 0
            src = Array(2) { DoubleArray(sr * 4) }; srcLen = 0; pos = 0.0
            val f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, ch)
            f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC); f.setInteger(MediaFormat.KEY_BIT_RATE, 256_000); f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply { configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); start() }
            mux = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
        // muda a rotação (velocidade + tom), aplica efeitos e guarda as amostras prontas
        fun process(final: Boolean) {
            val r = fx.rate
            while (pos + 1 < srcLen) {
                val i = pos.toInt(); val fr = pos - i
                val x = DoubleArray(ch) { c -> src[c][i] * (1 - fr) + src[c][i + 1] * fr }
                for (c in 0 until ch) {
                    var v = x[c]
                    bass?.let { v = it.run(v, c) }
                    if (fx.reverb) { val L = rev[c].size; var e = 0.0
                        for (k in taps.indices) { var j = rp - taps[k] * sr / 1000; while (j < 0) j += L; e += rev[c][j] * decays[k] }
                        rev[c][rp] = v; v = v * 0.8 + e * 0.88 }
                    x[c] = v
                }
                if (fx.reverb) rp = (rp + 1) % rev[0].size
                retro?.frame(x)
                if (pendN + ch > pend.size) pend = pend.copyOf(pend.size * 2)
                for (c in 0 until ch) pend[pendN++] = (tanh(x[c] * 1.05) / tanh(1.05) * 32767).toInt().coerceIn(-32768, 32767).toShort()
                pos += r
            }
            val drop = pos.toInt().coerceAtMost(srcLen)
            if (drop > 0) { for (c in 0 until ch) System.arraycopy(src[c], drop, src[c], 0, srcLen - drop); srcLen -= drop; pos -= drop }
            if (final) srcLen = 0
        }
        fun drainEnc(wait: Boolean) {
            val e = enc ?: return
            while (true) {
                val o = e.dequeueOutputBuffer(info, if (wait) 10_000 else 0)
                when {
                    o == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { muxTrack = mux!!.addTrack(e.outputFormat); mux!!.start(); muxOn = true }
                    o >= 0 -> {
                        val b = e.getOutputBuffer(o)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxOn) { b.position(info.offset); b.limit(info.offset + info.size); mux!!.writeSampleData(muxTrack, b, info) }
                        e.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) { encDone = true; return }
                    }
                }
            }
        }
        fun feedEnc(eos: Boolean) {
            val e = enc ?: return
            while (pendN >= ch * 1024 || (eos && !encInDone)) {
                val i = e.dequeueInputBuffer(10_000); if (i < 0) { drainEnc(false); continue }
                val b = e.getInputBuffer(i)!!.order(ByteOrder.LITTLE_ENDIAN); b.clear()
                val n = minOf(pendN, b.capacity() / 2 / ch * ch)
                b.asShortBuffer().put(pend, 0, n)
                if (n > 0) { System.arraycopy(pend, n, pend, 0, pendN - n); pendN -= n }
                val last = eos && pendN == 0
                e.queueInputBuffer(i, 0, n * 2, outFrames * 1_000_000L / sr, if (last) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                outFrames += n / ch
                if (last) { encInDone = true; break }
                drainEnc(false)
            }
        }
        try {
            var ready = false
            while (!decDone && !stopFlag) {
                if (!inDone) {
                    val i = dec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = ex.readSampleData(dec.getInputBuffer(i)!!, 0)
                        if (n < 0) { dec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { dec.queueInputBuffer(i, 0, n, ex.sampleTime, 0); ex.advance()
                            if (durUs > 0) pct = (ex.sampleTime.toFloat() / durUs).coerceIn(0f, .99f) }
                    }
                }
                val o = dec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val f = dec.outputFormat; sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); chIn = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (!ready) { setup(); ready = true }
                } else if (o >= 0) {
                    if (!ready) { setup(); ready = true }
                    val b = dec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN)
                    b.position(info.offset); b.limit(info.offset + info.size)
                    val sb = b.asShortBuffer(); val frames = sb.remaining() / chIn
                    if (srcLen + frames > src[0].size) src = Array(2) { c -> src[c].copyOf(srcLen + frames + sr) }
                    for (f in 0 until frames) { val l = sb.get(f * chIn) / 32768.0; val rr = if (chIn > 1) sb.get(f * chIn + 1) / 32768.0 else l
                        src[0][srcLen + f] = l; src[1][srcLen + f] = rr }
                    srcLen += frames
                    dec.releaseOutputBuffer(o, false)
                    process(false); feedEnc(false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) decDone = true
                }
            }
            if (stopFlag) error("parado")
            if (!ready) error("não deu pra ler o áudio")
            process(true); feedEnc(true)
            while (!encDone) drainEnc(true)
        } finally {
            runCatching { dec.stop() }; runCatching { dec.release() }; runCatching { ex.release() }
            runCatching { enc?.stop() }; runCatching { enc?.release() }
            runCatching { if (muxOn) mux?.stop() }; runCatching { mux?.release() }
        }
        // move pra pasta Música/Uncrowned
        val name = "${if (s.artist.isNotBlank() && s.artist != "Artista desconhecido") s.artist + " - " else ""}${s.title} (${fx.label})".replace(Regex("[\\\\/:*?\"<>|]"), "").take(150)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.m4a"); put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Uncrowned")
                    put(MediaStore.Audio.Media.TITLE, "${s.title} (${fx.label})"); put(MediaStore.Audio.Media.ARTIST, s.artist); put(MediaStore.Audio.Media.ALBUM, s.album)
                    put(MediaStore.Audio.Media.IS_MUSIC, 1); put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val r = ctx.contentResolver
                val u = r.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v) ?: error("sem espaço")
                r.openOutputStream(u)!!.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                r.update(u, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Uncrowned").apply { mkdirs() }
                val f = File(dir, "$name.m4a"); tmp.copyTo(f, true)
                android.media.MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), arrayOf("audio/mp4"), null)
            }
        } finally { tmp.delete() }
    }
}

private val PRESETS = listOf(Triple("Slowed + reverb", 0.85, true), Triple("Slowed", 0.85, false), Triple("Bem lento", 0.75, true),
    Triple("Nightcore", 1.25, false), Triple("Sped up", 1.15, false))

@Composable
fun ExportScreen(ui: PlayerUi, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val onRadio = ui.mediaId?.startsWith("au:radio-") == true && ui.playing
    var preset by rememberSaveable { mutableStateOf("Slowed + reverb") }
    var rate by rememberSaveable { mutableFloatStateOf(0.85f) }
    var reverb by rememberSaveable { mutableStateOf(true) }
    var bass by rememberSaveable { mutableStateOf(false) }
    var retro by rememberSaveable { mutableStateOf("") }
    var q by rememberSaveable { mutableStateOf("") }
    val sel = remember { mutableStateListOf<Long>() }
    val list = Library.songs.filter { q.isBlank() || (it.title + " " + it.artist + " " + it.album).contains(q, true) }
    val label = if (preset == "Personalizado") (if (rate < 1) "Slowed" else if (rate > 1) "Nightcore" else "Editada") + if (reverb) " + reverb" else "" else preset
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { Header("Slowed / Nightcore", onBack = onBack) }
        if (onRadio) { item { Empty("Desligado enquanto a rádio toca. Pause a rádio ou toque uma música pra usar.") }; return@LazyColumn }
        item { Text("Cria versões das suas músicas offline com outra velocidade e tom, e salva na pasta Música/Uncrowned. Elas aparecem em Músicas depois.",
            Modifier.padding(horizontal = 16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PRESETS.forEach { (n, r, rv) -> FilterChip(preset == n, { preset = n; rate = r.toFloat(); reverb = rv }, { Text(n) }) }
                        FilterChip(preset == "Personalizado", { preset = "Personalizado" }, { Text("Personalizado") })
                    }
                    Text("Velocidade e tom: ${"%.2f".format(rate)}x · ${if (rate < 1) "mais lento e grave" else if (rate > 1) "mais rápido e agudo" else "normal"}", Modifier.padding(top = 10.dp), fontSize = 13.sp)
                    Slider(rate, { rate = (Math.round(it * 100) / 100f); preset = "Personalizado" }, valueRange = 0.5f..1.5f)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(reverb, { reverb = it; if (preset != "Personalizado") preset = "Personalizado" }); Text("Reverb", fontSize = 13.sp)
                        Spacer(Modifier.width(12.dp)); Checkbox(bass, { bass = it }); Text("Graves extra", fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Som:", fontSize = 13.sp)
                        Retro.NAMES.forEach { (k, n) -> FilterChip(retro == k, { retro = k }, { Text(n) }) }
                    }
                    OutlinedButton(onClick = {
                        val s = Library.songs.firstOrNull { it.id in sel } ?: Library.songs.firstOrNull() ?: return@OutlinedButton
                        YtPlayer.js("player.pauseVideo()"); ui.play(listOf(s), 0)
                        scope.launch { delay(700); ui.controller?.playbackParameters = PlaybackParameters(rate, rate) }
                    }) { Icon(Icons.Rounded.Headphones, null); Text(" Ouvir prévia") }
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(q, { q = it }, Modifier.weight(1f), placeholder = { Text("Filtrar músicas offline") }, singleLine = true, shape = RoundedCornerShape(12.dp), leadingIcon = { Icon(Icons.Rounded.Search, null) })
                TextButton(onClick = { list.take(400).forEach { if (it.id !in sel) sel.add(it.id) } }) { Text("Todas") }
                TextButton(onClick = { sel.clear() }) { Text("Nenhuma") }
            }
        }
        if (list.isEmpty()) item { Empty(if (Library.songs.isEmpty()) "Nenhuma música offline ainda." else "Nada com esse nome.") }
        items(list.take(400), key = { it.id }) { s ->
            val on = s.id in sel
            Row(Modifier.fillMaxWidth().clickable { if (on) sel.remove(s.id) else sel.add(s.id) }.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(on, { if (it) sel.add(s.id) else sel.remove(s.id) })
                Art(s.art, Modifier.size(42.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(s.artist, maxLines = 1, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(fmt(s.duration), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    // barra de exportar
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp)) {
            if (Exporter.running) {
                Text("Exportando ${Exporter.index + 1} de ${Exporter.total}: ${Exporter.current}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                LinearProgressIndicator({ Exporter.pct }, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                OutlinedButton(onClick = { Exporter.stop() }, Modifier.fillMaxWidth()) { Text("Parar") }
            } else Button(onClick = {
                val songs = Library.songs.filter { it.id in sel }
                Exporter.run(ctx, songs, Exporter.Fx(rate.toDouble(), reverb, bass, retro, label))
            }, Modifier.fillMaxWidth(), enabled = sel.isNotEmpty()) { Text(if (sel.isEmpty()) "Marque as músicas" else "Exportar ${sel.size} como \"$label\"") }
        }
    }
}
