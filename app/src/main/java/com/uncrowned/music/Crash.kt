package com.uncrowned.music

import android.content.Context
import java.io.File

/* Se o app fechar sozinho, guarda o motivo pra mostrar na próxima abertura (dá pra copiar e mandar). */
object Crash {
    private var installed = false
    fun install(c: Context) {
        if (installed) return; installed = true
        val f = File(c.applicationContext.filesDir, "crash.txt")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = java.io.StringWriter(); e.printStackTrace(java.io.PrintWriter(sw))
                f.writeText("Uncrowned ${runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull()} • Android ${android.os.Build.VERSION.RELEASE} • ${android.os.Build.MODEL}\nthread: ${t.name}\n\n" + sw.toString().take(12000))
            }
            prev?.uncaughtException(t, e)
        }
    }
    fun take(c: Context): String? { val f = File(c.filesDir, "crash.txt"); if (!f.exists()) return null; val s = runCatching { f.readText() }.getOrNull(); f.delete(); return s }
}
