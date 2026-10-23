package com.uncrowned.music

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

/* Cobrança: se você some do app, ele te chama de volta com umas mensagens sem noção.
   No máximo uma por dia, só entre 10h e 22h, e nunca se você ouviu algo nas últimas 20 horas. */
object Nag {
    private const val GAP = 20L * 3600_000
    private var sp: android.content.SharedPreferences? = null
    private var lastWrite = 0L
    private fun sp(c: Context) = sp ?: c.applicationContext.getSharedPreferences("nag", Context.MODE_PRIVATE).also { sp = it }
    // chamado a cada tique de música ouvida (sem contexto à mão)
    fun touchListen() { val s = sp ?: return; val now = System.currentTimeMillis(); if (now - lastWrite < 300_000) return; lastWrite = now; s.edit().putLong("used", now).apply() }

    fun on(c: Context) = sp(c).getBoolean("on", true)
    fun setOn(c: Context, v: Boolean) { sp(c).edit().putBoolean("on", v).apply(); schedule(c) }

    // marca que o app foi usado (abrir o app ou ouvir música), gravando no máximo a cada 5 min
    fun touch(c: Context) {
        val now = System.currentTimeMillis()
        if (now - lastWrite < 300_000) return
        lastWrite = now
        sp(c).edit().putLong("used", now).apply()
    }

    fun schedule(c: Context) {
        sp(c)
        val wm = WorkManager.getInstance(c)
        if (!on(c)) { wm.cancelUniqueWork("nag"); return }
        wm.enqueueUniquePeriodicWork("nag", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<NagWorker>(3, TimeUnit.HOURS).build())
    }

    val TITLES = listOf("Uncrowned Music", "Ô, sumido(a)", "Alerta de silêncio", "Ei, você", "Saudades 👑", "Plantão do som", "URGENTE", "Notícia triste")
    val MSGS = listOf(
        "VAMO usar droga, desgraçaaaaaaa",
        "Não tá sentindo falta de um som não?",
        "Teus fones tão chorando no canto do quarto. Faz isso com eles não.",
        "Tô aqui parado igual cachorro esperando na porta. Bota uma música, pelo amor.",
        "Silêncio demais faz mal. É ciência. (Não é, mas bota um som aí.)",
        "Tua playlist mandou dizer que tá com saudade e que você é um ingrato.",
        "O grave tá acumulando poeira aqui. Vem soprar.",
        "Faz tempo que você não abre o app. Tá ouvindo música em OUTRO app? Pode falar, eu aguento.",
        "Ô, o cara sumiu igual o pai que foi comprar cigarro.",
        "Cê tá vivo? Pisca duas vezes. Ou dá play, que é mais fácil.",
        "Teu ouvido tá passando fome. Alimenta ele.",
        "Hoje é um ótimo dia pra ouvir aquela música que você já ouviu 400 vezes.",
        "Abre aí rapidinho, só uma musiquinha, ninguém vai saber.",
        "Até o Spotify tá com dó de mim. Volta.",
        "Tô com tanta música parada aqui que já virou acervo de museu.",
        "Alerta: nível de vibe abaixo do permitido. Dê play imediatamente.",
        "O vizinho tá ouvindo pagode alto e você aí no silêncio? Revida.",
        "Lembra de mim? O app que você instalou com tanto carinho? Pois é.",
        "Se você não abrir eu vou começar a tocar sozinho. Mentira, não vou. Mas abre.",
        "Bota um som aí que eu prometo não te julgar pelo gosto musical. Prometo nada, na verdade.",
        "Teu fone de ouvido entrou com pedido de divórcio por abandono.",
        "Faz um favor pra humanidade: aperta o play.",
        "Mais um dia sem música e você vira uma pessoa sem graça oficialmente.",
        "As caixinhas de som do Brasil inteiro tão preocupadas com você.",
        "Pô, nem um lofizinho pra disfarçar? Que fase.",
        "Volta, eu mudo. Eu juro que mudo. (Não mudo nada, continuo perfeito.)",
        "Seu último play foi há tanto tempo que a música já ficou antiga.",
        "Toc toc. Quem é? É o som que você tá devendo pra si mesmo.",
        "Seu dia tá sem trilha sonora. Isso é crime em 14 países.",
        "Bora, bora, bora! Um play só e eu paro de encher o saco. Hoje.",
    )

    fun fire(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("nag", "Chamar pra ouvir música", NotificationManager.IMPORTANCE_DEFAULT))
        val s = sp(c)
        // não repete a última mensagem
        var i = MSGS.indices.random()
        if (i == s.getInt("last", -1)) i = (i + 1) % MSGS.size
        s.edit().putInt("last", i).putLong("nagged", System.currentTimeMillis()).apply()
        val open = PendingIntent.getActivity(c, 7, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(c, "nag").setSmallIcon(R.drawable.ic_note)
            .setContentTitle(TITLES.random()).setContentText(MSGS[i])
            .setStyle(NotificationCompat.BigTextStyle().bigText(MSGS[i]))
            .setContentIntent(open).setAutoCancel(true).build()
        runCatching { nm.notify(5151, n) }
    }

    fun check(c: Context) {
        if (!on(c)) return
        val s = sp(c); val now = System.currentTimeMillis()
        val used = s.getLong("used", 0L)
        if (used == 0L) { s.edit().putLong("used", now).apply(); return }
        if (now - used < GAP || now - s.getLong("nagged", 0L) < GAP) return
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (h < 10 || h >= 22) return
        fire(c)
    }
}

class NagWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result { runCatching { Nag.check(applicationContext) }; return Result.success() }
}
