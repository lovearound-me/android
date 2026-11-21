package me.lovearound.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.lovearound.android.tr

/** What the app is for, on one page: once after the first sign-in («Начать»), and from Настройки («Понятно»). */
@Composable
fun HowItWorks(button: String, close: () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.paper).systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier
            .weight(1f)
            .widthIn(max = 520.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text(tr("Как это работает"), color = C.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 36.dp, bottom = 4.dp))
            HowRow(Icons.Filled.Favorite, C.heart, tr("Нажми на человека"),
                tr("Он почувствует, что о нём подумали: придёт уведомление, засветится экран или лампа."))
            HowRow(Icons.Filled.VisibilityOff, C.indigo, tr("От кого, не видно"),
                tr("В сердечке нет ни имени, ни текста. Узнать, от кого оно, нельзя."))
            HowRow(Icons.Filled.Link, C.ok, tr("Добавь своих по Love ID"),
                tr("Это твоя ссылка. Отправь её близким и открой их ссылки."))
            HowRow(Icons.Filled.AutoAwesome, C.star, tr("Кому-нибудь на свете"),
                tr("Раз в час можно послать сердечко случайному человеку."))
        }
        Box(Modifier.widthIn(max = 520.dp).padding(horizontal = 28.dp).padding(top = 8.dp, bottom = 20.dp)) {
            WideButton(button, close)
        }
    }
}

@Composable
private fun HowRow(icon: ImageVector, tint: Color, title: String, text: String) {
    Row {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(text, color = C.muted, fontSize = 16.sp, lineHeight = 21.sp)
        }
    }
}

/** Before Android asks about notifications, once after signing in: what they are for. */
@Composable
fun NotifyIntro(allow: () -> Unit, later: () -> Unit) {
    Column(Modifier
        .fillMaxSize()
        .background(C.paper)
        .systemBarsPadding()
        .padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(104.dp).clip(CircleShape).background(C.heartSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.NotificationsActive, null, tint = C.heart, modifier = Modifier.size(48.dp))
        }
        Text(tr("Чтобы сердечко дошло, когда телефон в кармане"), color = C.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, lineHeight = 32.sp, modifier = Modifier.padding(top = 26.dp))
        Text(tr("Разреши уведомления, и сердечко придёт, даже когда приложение закрыто. От кого оно, не видно никогда. Ни о чём другом мы не пишем."),
            color = C.muted, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 23.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.weight(1f))
        Box(Modifier.widthIn(max = 520.dp)) { WideButton(tr("Разрешить уведомления"), allow) }
        Text(tr("Не сейчас"), color = C.link, fontSize = 17.sp, modifier = Modifier
            .padding(top = 8.dp)
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = later)
            .padding(12.dp))
        Text(tr("Включить можно и потом: Настройки → Уведомление."), color = C.faint, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
    }
}
