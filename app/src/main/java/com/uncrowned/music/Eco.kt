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
    val on: Boolean get() = forced || (mode == "auto" && systemSaver)
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
        systemSaver = runCatching { app.getSystemService(PowerManager::class.java).isPowerSaveMode }.getOrDefault(false)
    }
}
