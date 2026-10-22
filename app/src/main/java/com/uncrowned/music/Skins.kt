package com.uncrowned.music

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* Temas (estilos de turma): mesmas cores, caveiras e artes da versão de PC.
   O ícone da tela inicial troca junto (atalhos do app, um por tema). */
data class Skin(
    val id: String, val name: String, val desc: String, val accent: Long, val dark: Boolean,
    val bg: Long, val card: Long, val card2: Long, val text: Long, val dim: Long,
    val icon: Int, val art: Int, val artAlpha: Float = 0.28f
)

object Skins {
    val all = listOf(
        Skin("edgy", "Edgy", "Dark, vermelho sangue e glitch", 0xFFFF1F3D, true, 0xFF060607, 0xFF121214, 0xFF1E1E22, 0xFFFFFFFF, 0xFF8E8E93, R.drawable.skin_edgy, R.drawable.skin_edgy_bg),
        Skin("fadinha", "Fadinha", "Pastel, brilhinho e lacinho", 0xFFFF5FA8, false, 0xFFFFF4FA, 0xFFFFFFFF, 0xFFF6E3F4, 0xFF4A2A4F, 0xFFA07A9E, R.drawable.skin_fadinha, R.drawable.skin_fadinha_bg, 0.35f),
        Skin("otaku", "Otaku", "Neon, sakura e anime", 0xFFFF3FA4, true, 0xFF0F0B24, 0xFF1B1538, 0xFF2A2150, 0xFFFFFFFF, 0xFF9B93C9, R.drawable.skin_otaku, R.drawable.skin_otaku_bg),
        Skin("oldmoney", "Old Money", "Linho, marinho, caramelo e cartola", 0xFFA8763E, false, 0xFFF4EEE1, 0xFFFBF8F1, 0xFFEBE3D2, 0xFF1D2B44, 0xFF7D7462, R.drawable.skin_oldmoney, R.drawable.skin_oldmoney_bg, 0.18f),
        Skin("alternativo", "Indie", "Terroso, granulado e fone", 0xFFE76F51, true, 0xFF161F1E, 0xFF1F2B2A, 0xFF2B3A38, 0xFFF4E8C8, 0xFFA8A08A, R.drawable.skin_alternativo, R.drawable.skin_alternativo_bg),
        Skin("punk", "Punk", "Preto, verde neon e retícula", 0xFF39FF14, true, 0xFF0A0A0A, 0xFF141414, 0xFF222222, 0xFFFFFFFF, 0xFF8E8E93, R.drawable.skin_punk, R.drawable.skin_punk_bg),
        Skin("pulseirinhas", "Pulseirinhas", "Glitter colorido e miçangas", 0xFFC77DFF, true, 0xFF1A1030, 0xFF25183F, 0xFF35245A, 0xFFFFFFFF, 0xFFB7A6D9, R.drawable.skin_pulseirinhas, R.drawable.skin_pulseirinhas_bg),
        Skin("ninja", "Ninja Renegado", "Preto, carmesim, shurikens e kunais", 0xFFD62839, true, 0xFF0A0809, 0xFF151114, 0xFF221B20, 0xFFFFFFFF, 0xFFA39599, R.drawable.skin_ninja, R.drawable.skin_ninja_bg),
        Skin("deserto", "Deserto Estelar", "Noite no deserto, ouro e turquesa", 0xFF2EC4B6, true, 0xFF0D1430, 0xFF17204A, 0xFF24305E, 0xFFFFFFFF, 0xFF9FA8CC, R.drawable.skin_deserto, R.drawable.skin_deserto_bg),
        Skin("ouro", "Ouro Italiano", "Dourado, magenta e linhas de mangá", 0xFFF2B632, true, 0xFF1C0718, 0xFF2C0D27, 0xFF3F1538, 0xFFFFFFFF, 0xFFD39AC4, R.drawable.skin_ouro, R.drawable.skin_ouro_bg),
        Skin("cela", "Cela Esmeralda", "Verde-pedra, correntes e jade", 0xFF3DDC97, true, 0xFF0B1D17, 0xFF12281F, 0xFF1B382C, 0xFFFFFFFF, 0xFF8FB5A4, R.drawable.skin_cela, R.drawable.skin_cela_bg),
        Skin("rock", "Rock Tóquio", "Xadrez vermelho, renda e rosas", 0xFFE01E37, true, 0xFF0D0506, 0xFF1A0B0D, 0xFF2A1215, 0xFFFFFFFF, 0xFFB49A9C, R.drawable.skin_rock, R.drawable.skin_rock_bg),
        Skin("pavor", "Pavor", "Terror pesado: olhos no escuro e arame", 0xFFC0001A, true, 0xFF060508, 0xFF121016, 0xFF1D1A22, 0xFFDDD6C6, 0xFF8A8394, R.drawable.skin_pavor, R.drawable.skin_pavor_bg),
        Skin("apagao", "Apagão", "Preto total, minimalista, sem cor", 0xFFE6E6E6, true, 0xFF000000, 0xFF0B0B0B, 0xFF161616, 0xFFE6E6E6, 0xFF6E6E6E, R.drawable.skin_apagao, R.drawable.skin_apagao_bg, 0f),
        Skin("julie", "Julie", "Lilás fofinho, fitinha e florzinha", 0xFF9B6FD6, false, 0xFFF7F0FF, 0xFFFFFFFF, 0xFFEFE4FF, 0xFF3B2352, 0xFF9A84B8, R.drawable.skin_julie, R.drawable.skin_julie_bg, 0.35f),
        Skin("nino", "Nino", "O gatinho vaquinha (milkmoo)", 0xFFFF7AA2, false, 0xFFFFFAFC, 0xFFFFFFFF, 0xFFF3EEF0, 0xFF1B1B1F, 0xFF8B8590, R.drawable.skin_nino, R.drawable.skin_nino_bg, 0.2f),
        Skin("divino", "Divino", "Todo branco com dourado", 0xFFB8902F, false, 0xFFFDFBF5, 0xFFFFFFFF, 0xFFF3ECDC, 0xFF3A2F1A, 0xFF9A8A66, R.drawable.skin_divino, R.drawable.skin_divino_bg, 0.35f)
    )
    val current: Skin? get() = all.firstOrNull { it.id == Prefs.skin }

    fun apply(ctx: Context, s: Skin) {
        Prefs.skin = s.id
        Prefs.accent = s.accent.toInt()
        Prefs.themeMode = if (s.dark) "amoled" else "light"
        Prefs.dynamicColor = false
        setLauncherIcon(ctx, s.id)
    }

    // troca o ícone da tela inicial: liga o atalho do tema e desliga os outros
    fun setLauncherIcon(ctx: Context, id: String) {
        val pm = ctx.packageManager
        all.forEach { s ->
            val cn = ComponentName(ctx.packageName, "com.uncrowned.music.Skin_${s.id}")
            val want = if (s.id == id) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            runCatching { if (pm.getComponentEnabledSetting(cn) != want) pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP) }
        }
    }
}

/* fundo do tema: arte bem suave atrás das telas */
@Composable
fun SkinBackground() {
    val s = Skins.current ?: return
    if (s.artAlpha <= 0f) return
    Image(painterResource(s.art), null, Modifier.fillMaxSize().alpha(s.artAlpha), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
}

/* galeria de temas (Ajustes) */
@Composable
fun SkinGallery() {
    val ctx = LocalContext.current
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("TEMA", Modifier.padding(start = 20.dp, bottom = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Skins.all, key = { it.id }) { s ->
                val on = Prefs.skin == s.id
                Column(
                    Modifier.width(118.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(2.dp, if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { Skins.apply(ctx, s) }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(painterResource(s.icon), null, Modifier.size(84.dp).clip(RoundedCornerShape(18.dp)))
                    Spacer(Modifier.height(8.dp))
                    Text(s.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.desc, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(s.bg, s.card2, s.accent).forEach { c -> Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c)).border(0.5.dp, Color.Gray.copy(alpha = .4f), CircleShape)) }
                    }
                }
            }
        }
    }
}
