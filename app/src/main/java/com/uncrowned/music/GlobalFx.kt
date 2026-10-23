package com.uncrowned.music

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/* Efeitos feitos pelo próprio celular, por fora do player.
   Servem pras músicas que tocam no player oficial do SoundCloud (áudio protegido),
   onde não dá pra mexer no som por dentro:
   - equalizador e "tom" de K7/vinil: equalizador do sistema na saída de som do celular
   - chiado da fita / estalos do vinil: o celular gera e mistura por cima, ao vivo
   - oscilação da fita (wow): a velocidade varia de leve, igual fita de verdade */
object GlobalFx {
    private var eq: Equalizer? = null
    @Volatile private var noiseMode = ""
    private var thread: Thread? = null
    var webPlaying = false; private set

    /* chamado quando o player da web começa/para e quando muda K7/vinil ou o equalizador */
    fun sync(playing: Boolean) {
        webPlaying = playing
        val retro = if (playing) Retro.mode else ""
        applyEq(playing, retro)
        noise(retro)
    }

    private fun applyEq(playing: Boolean, retro: String) {
        if (!playing || (retro.isEmpty() && !Prefs.eqEnabled)) { runCatching { eq?.enabled = false }; return }
        val e = eq ?: runCatching { Equalizer(1000, 0) }.getOrNull()?.also { eq = it } ?: return
        runCatching {
            val n = e.numberOfBands.toInt(); val (lo, hi) = e.bandLevelRange.let { it[0] to it[1] }
            for (b in 0 until n) {
                val f = e.getCenterFreq(b.toShort()) / 1000   // Hz
                var mb = if (Prefs.eqEnabled) Prefs.band(b) else 0
                // som de aparelho antigo: corta graves fundos e agudos, sobe o meio
                if (retro == "k7") mb += when { f < 90 -> -900; f < 250 -> -400; f in 1000..2500 -> 250; f > 9000 -> -1500; f > 6000 -> -900; else -> 0 }
                if (retro == "vinil") mb += when { f < 50 -> -500; f < 250 -> -200; f in 1000..2500 -> 150; f > 12000 -> -900; else -> 0 }
                e.setBandLevel(b.toShort(), mb.coerceIn(lo.toInt(), hi.toInt()).toShort())
            }
            e.enabled = true
        }
    }

    /* chiado/estalos gerados no celular e tocados junto com a música */
    private fun noise(mode: String) {
        noiseMode = mode
        if (mode.isEmpty() || thread?.isAlive == true) return
        thread = Thread {
            val sr = 44100
            val buf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(8192)
            val t = runCatching {
                AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(buf * 2).setTransferMode(AudioTrack.MODE_STREAM).build()
            }.getOrNull() ?: return@Thread
            t.play()
            val rnd = Random(11); val out = ShortArray(1024)
            var brown = 0.0; var crack = 0; var z1 = 0.0; var z2 = 0.0; var hp = 0.0; var lastIn = 0.0
            while (noiseMode.isNotEmpty()) {
                val tape = noiseMode == "k7"
                for (i in out.indices) {
                    var nz: Double
                    if (tape) { val w = rnd.nextDouble() * 2 - 1; brown = 0.97 * brown + 0.03 * w; nz = (w * 0.6 + brown * 2) * 0.5
                        // passa-banda leve em torno de 4 kHz (chiado de fita)
                        z1 += 0.45 * (nz - z1); z2 += 0.08 * (z1 - z2); nz = (z1 - z2) * 0.9 * 0.022 * 3.2 }
                    else {
                        nz = (rnd.nextDouble() * 2 - 1) * 0.06
                        if (rnd.nextDouble() < 0.0003) nz += (if (rnd.nextBoolean()) -1 else 1) * (0.4 + rnd.nextDouble() * 0.6)
                        if (crack > 0) { nz += (rnd.nextDouble() * 2 - 1) * 0.6 * (crack / 40.0); crack-- } else if (rnd.nextDouble() < 0.00002) crack = 40
                        // tira o grave do ruído (fica só o estalo/agulha)
                        hp = 0.92 * (hp + nz - lastIn); lastIn = nz; nz = hp * 0.05 * 1.6
                    }
                    out[i] = (nz.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                }
                if (t.write(out, 0, out.size) < 0) break
            }
            runCatching { t.stop() }; runCatching { t.release() }
        }.apply { isDaemon = true; start() }
    }

    /* oscilação da fita no player da web: velocidade varia de leve (wow 0,55 Hz) */
    fun wowRate(base: Float, tMs: Long): Float {
        val d = when (Retro.mode) { "k7" -> 0.004; "vinil" -> 0.0015; else -> return base }
        return (base * (1 + d * sin(2 * PI * 0.55 * tMs / 1000.0))).toFloat()
    }

    fun release() { noiseMode = ""; runCatching { eq?.release() }; eq = null }
}
