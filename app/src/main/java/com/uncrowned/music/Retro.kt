package com.uncrowned.music

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/* Modo retrô (fita K7 / vinil), o mesmo som da versão de PC:
   corta graves e agudos como o aparelho antigo, satura de leve, faz a "oscilação" da fita
   e mistura chiado (K7) ou estalos (vinil). */
object Retro {
    val NAMES = mapOf("" to "Normal", "k7" to "Fita K7", "vinil" to "Vinil")
    var mode: String get() = Prefs.sp.getString("retro", "") ?: ""; set(v) { Prefs.sp.edit().putString("retro", v).apply(); YtPlayer.applyRetro() }
    fun next() { mode = when (mode) { "" -> "k7"; "k7" -> "vinil"; else -> "" } }

    /* mesmo código que roda no PC: entra dentro do player do YouTube/SoundCloud (Web Audio) */
    const val JS_SRC = """function(c, mode) {
  var inp = c.createGain(), out = c.createGain();
  if (!mode) { inp.connect(out); return { input: inp, output: out, mode: '', stop: function(){} }; }
  var tape = mode === 'k7';
  var bq = function(type, f, g, q) { var b = c.createBiquadFilter(); b.type = type; b.frequency.value = f; if (g != null) b.gain.value = g; if (q != null) b.Q.value = q; return b; };
  var hp = bq('highpass', tape ? 70 : 35), ls = bq('lowshelf', 180, tape ? -4 : -2), mid = bq('peaking', 1500, tape ? 2 : 1.5, 0.7), lp = bq('lowpass', tape ? 7000 : 11000, null, 0.5);
  var ws = c.createWaveShaper(), k = tape ? 2.2 : 1.4, n = 1024, cv = new Float32Array(n);
  for (var i = 0; i < n; i++) { var x = i / (n - 1) * 2 - 1; cv[i] = Math.tanh(k * x) / Math.tanh(k); } ws.curve = cv;
  var dl = c.createDelay(0.05); dl.delayTime.value = 0.012;
  var osc = function(f, depth) { var o = c.createOscillator(), g = c.createGain(); o.frequency.value = f; g.gain.value = depth; o.connect(g); g.connect(dl.delayTime); o.start(); return o; };
  var wow = osc(0.55, tape ? 0.0016 : 0.0006), flut = osc(6.5, tape ? 0.00018 : 0.00004);
  inp.connect(hp); hp.connect(ls); ls.connect(mid); mid.connect(ws); ws.connect(lp); lp.connect(dl); dl.connect(out);
  var len = c.sampleRate * 4, buf = c.createBuffer(1, len, c.sampleRate), d = buf.getChannelData(0);
  if (tape) { var b = 0; for (var i = 0; i < len; i++) { var w = Math.random() * 2 - 1; b = 0.97 * b + 0.03 * w; d[i] = (w * 0.6 + b * 2) * 0.5; } }
  else for (var i = 0; i < len; i++) {
    d[i] += (Math.random() * 2 - 1) * 0.06;
    if (Math.random() < 0.0003) d[i] += (Math.random() < 0.5 ? -1 : 1) * (0.4 + Math.random() * 0.6);
    if (Math.random() < 0.00002) for (var j = 0; j < 40 && i + j < len; j++) d[i + j] += (Math.random() * 2 - 1) * 0.6 * (1 - j / 40);
  }
  var ns = c.createBufferSource(); ns.buffer = buf; ns.loop = true;
  var nf = bq(tape ? 'bandpass' : 'highpass', tape ? 4000 : 1200), ng = c.createGain(); ng.gain.value = tape ? 0.022 : 0.05;
  ns.connect(nf); nf.connect(ng); ng.connect(out); ns.start();
  return { input: inp, output: out, mode: mode, stop: function() { try { wow.stop(); flut.stop(); ns.stop(); } catch (e) {} } };
}"""
    // liga/troca o efeito em todos os vídeos/áudios do player embutido
    fun js(mode: String) = """(function(){var R=$JS_SRC;var FX='$mode';
  function run(doc,win){ if(!doc) return; var els=doc.querySelectorAll('video,audio');
    els.forEach(function(m){
      if(!m.__unc){ if(!FX) return; try{var c=win.__uncAC||(win.__uncAC=new (win.AudioContext||win.webkitAudioContext)());var s=c.createMediaElementSource(m);var g=c.createGain();s.connect(g);m.__unc={c:c,g:g,rm:null};m.addEventListener('play',function(){c.resume()});}catch(e){m.__unc={err:1};return} }
      var q=m.__unc; if(q.err) return;
      if(q.rm===null||q.rm.mode!==FX){ try{q.g.disconnect()}catch(e){} if(q.rm){try{q.rm.output.disconnect()}catch(e){} q.rm.stop()} q.rm=R(q.c,FX); q.g.connect(q.rm.input); q.rm.output.connect(q.c.destination); }
      if(q.c.state==='suspended') q.c.resume();
    });
  }
  try{ document.querySelectorAll('iframe').forEach(function(f){ try{ run(f.contentDocument, f.contentWindow) }catch(e){} }); }catch(e){}
  try{ run(document, window) }catch(e){}
})();"""
}

/* o mesmo efeito em Kotlin, pro player principal (músicas offline, Audius, Archive) e pro exportador */
class RetroDsp(private val rate: Int, private val ch: Int) {
    private class Bq(type: String, f: Double, gainDb: Double, q: Double, sr: Int) {
        var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        private val z1 = DoubleArray(8); private val z2 = DoubleArray(8)
        init {
            val w = 2 * PI * f / sr; val cw = cos(w); val sw = sin(w); val A = Math.pow(10.0, gainDb / 40); val al = sw / (2 * q)
            var a0 = 1.0
            when (type) {
                "hp" -> { b0 = (1 + cw) / 2; b1 = -(1 + cw); b2 = (1 + cw) / 2; a0 = 1 + al; a1 = -2 * cw; a2 = 1 - al }
                "lp" -> { b0 = (1 - cw) / 2; b1 = 1 - cw; b2 = (1 - cw) / 2; a0 = 1 + al; a1 = -2 * cw; a2 = 1 - al }
                "bp" -> { b0 = al; b1 = 0.0; b2 = -al; a0 = 1 + al; a1 = -2 * cw; a2 = 1 - al }
                "pk" -> { b0 = 1 + al * A; b1 = -2 * cw; b2 = 1 - al * A; a0 = 1 + al / A; a1 = -2 * cw; a2 = 1 - al / A }
                "ls" -> { val s2 = 2 * sqrt(A) * al
                    b0 = A * ((A + 1) - (A - 1) * cw + s2); b1 = 2 * A * ((A - 1) - (A + 1) * cw); b2 = A * ((A + 1) - (A - 1) * cw - s2)
                    a0 = (A + 1) + (A - 1) * cw + s2; a1 = -2 * ((A - 1) + (A + 1) * cw); a2 = (A + 1) + (A - 1) * cw - s2 }
            }
            b0 /= a0; b1 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
        }
        fun run(x: Double, c: Int): Double { val y = b0 * x + z1[c]; z1[c] = b1 * x - a1 * y + z2[c]; z2[c] = b2 * x - a2 * y; return y }
    }
    var mode = ""; private set
    private var chain = emptyList<Bq>(); private var noiseF: Bq? = null
    private var k = 1.0; private var wowD = 0.0; private var flutD = 0.0; private var noiseG = 0.0
    private val ringLen = (rate * 0.06).toInt() + 4
    private val ring = Array(maxOf(1, ch)) { DoubleArray(ringLen) }
    private var wp = 0; private var n = 0L; private var brown = 0.0; private var crackle = 0
    private val rnd = Random(7)

    fun setMode(m: String) {
        if (m == mode) return
        mode = m; val tape = m == "k7"
        if (m.isEmpty()) return
        chain = listOf(Bq("hp", if (tape) 70.0 else 35.0, 0.0, 0.707, rate), Bq("ls", 180.0, if (tape) -4.0 else -2.0, 0.707, rate),
            Bq("pk", 1500.0, if (tape) 2.0 else 1.5, 0.7, rate), Bq("lp", minOf(rate / 2.2, if (tape) 7000.0 else 11000.0), 0.0, 0.5, rate))
        noiseF = if (tape) Bq("bp", 4000.0, 0.0, 1.0, rate) else Bq("hp", 1200.0, 0.0, 0.707, rate)
        k = if (tape) 2.2 else 1.4; wowD = if (tape) 0.0016 else 0.0006; flutD = if (tape) 0.00018 else 0.00004; noiseG = if (tape) 0.022 else 0.05
    }

    /* processa um quadro (todas as faixas) em -1..1 */
    fun frame(x: DoubleArray) {
        if (mode.isEmpty()) return
        val t = n.toDouble() / rate
        val delay = (0.012 + wowD * sin(2 * PI * 0.55 * t) + flutD * sin(2 * PI * 6.5 * t)) * rate
        // ruído do mesmo jeito do PC
        val tape = mode == "k7"
        var nz: Double
        if (tape) { val w = rnd.nextDouble() * 2 - 1; brown = 0.97 * brown + 0.03 * w; nz = (w * 0.6 + brown * 2) * 0.5 }
        else {
            nz = (rnd.nextDouble() * 2 - 1) * 0.06
            if (rnd.nextDouble() < 0.0003) nz += (if (rnd.nextBoolean()) -1 else 1) * (0.4 + rnd.nextDouble() * 0.6)
            if (crackle > 0) { nz += (rnd.nextDouble() * 2 - 1) * 0.6 * (crackle / 40.0); crackle-- } else if (rnd.nextDouble() < 0.00002) crackle = 40
        }
        nz = noiseF!!.run(nz, 7) * noiseG
        val kk = tanh(k)
        for (c in 0 until minOf(ch, ring.size)) {
            var v = x[c]
            for (b in chain) v = b.run(v, c)
            v = tanh(k * v) / kk
            ring[c][wp] = v
            var rp = wp - delay; while (rp < 0) rp += ringLen
            val i0 = rp.toInt() % ringLen; val i1 = (i0 + 1) % ringLen; val fr = rp - Math.floor(rp)
            x[c] = ring[c][i0] * (1 - fr) + ring[c][i1] * fr + nz
        }
        wp = (wp + 1) % ringLen; n++
    }
}

@UnstableApi
class RetroProcessor : BaseAudioProcessor() {
    private var dsp: RetroDsp? = null
    private var chs = 2
    override fun onConfigure(input: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (input.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(input)
        chs = input.channelCount
        dsp = RetroDsp(input.sampleRate, input.channelCount)
        return input
    }
    override fun queueInput(inputBuffer: ByteBuffer) {
        val len = inputBuffer.remaining()
        if (len == 0) return
        val out = replaceOutputBuffer(len)
        val d = dsp; val m = Retro.mode
        if (d == null || (m.isEmpty() && d.mode.isEmpty())) { out.put(inputBuffer); out.flip(); return }
        d.setMode(m)
        val inS = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val fr = DoubleArray(chs)
        val frames = len / (2 * chs)
        for (f in 0 until frames) {
            for (c in 0 until chs) fr[c] = inS.short / 32768.0
            d.frame(fr)
            for (c in 0 until chs) out.putShort((fr[c].coerceIn(-1.0, 0.99997) * 32767).toInt().toShort())
        }
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }
}
