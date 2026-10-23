package com.uncrowned.music

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/* Economia de bateria: corta o que gasta à toa (fundo com arte, vídeo, animações, fontes, borrão da capa,
   caçador de rádio mais espaçado) sem mexer em tocar, buscar, baixar ou gravar. */
object Eco {
    // "auto" = liga sozinho quando o celular entra em economia de bateria; "on" = sempre; "off" = nunca
    var mode: String get() = Prefs.sp.getString("eco", "auto") ?: "auto"; set(v) { Prefs.sp.edit().putString("eco", v).apply(); refresh() }
    private var systemSaver by mutableStateOf(false)
    private var forced by mutableStateOf(false)
    private var extremeS by mutableStateOf(false)
    /* modo Extremo: só tocar música. Sem capas/fotos, sem paradas e playlists automáticas, sem caçador de rádio,
       sem animação, fundo preto puro (gasta menos em tela OLED) e vídeo do YouTube sempre na menor qualidade */
    val extreme: Boolean get() = extremeS
    val on: Boolean get() = forced || extremeS || (mode == "auto" && systemSaver)
    val bySystem: Boolean get() = mode == "auto" && systemSaver
    private var registered = false
    private lateinit var app: Context

    fun init(c: Context) {
        app = c.applicationContext
        refresh()
        if (!registered) {
            registered = true
            runCatching { app.registerReceiver(object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) = refresh() }, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)) }
        }
    }
    fun refresh() {
        forced = mode == "on"
        extremeS = mode == "extremo"
        if (extremeS) runCatching { Hunter.stop() }
        systemSaver = runCatching { app.getSystemService(PowerManager::class.java).isPowerSaveMode }.getOrDefault(false)
    }
}

/* Opções secretas (salvar música / offline): ficam escondidas.
   Liberar ou esconder de novo: em Ajustes, tocar 5 vezes seguidas no tema que já está em uso. */
object Secret {
    var on by mutableStateOf(false); private set
    private var loaded = false
    private var taps = 0; private var last = 0L
    fun init() { if (!loaded) { loaded = true; on = Prefs.sp.getBoolean("sec", false) } }
    fun tap(c: Context) {
        init()
        val now = System.currentTimeMillis()
        taps = if (now - last < 1500) taps + 1 else 1
        last = now
        if (taps in 3..4) android.widget.Toast.makeText(c, "${5 - taps}…", android.widget.Toast.LENGTH_SHORT).show()
        if (taps >= 5) {
            taps = 0; on = !on; Prefs.sp.edit().putBoolean("sec", on).apply()
            android.widget.Toast.makeText(c, if (on) "🔓 Opções secretas liberadas" else "🔒 Opções secretas escondidas", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
