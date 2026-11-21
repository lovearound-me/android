package me.lovearound.android.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import me.lovearound.android.Palette
import me.lovearound.android.tr

/** A small grey line under a setting. */
@Composable
private fun Note(text: String) {
    Text(text, fontSize = 12.sp, color = W.muted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp))
}

@Composable
fun SettingsScreen(model: WatchModel, open: (String) -> Unit) {
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(tr("Настройки")) } }
            model.receiving?.let { on ->
                item {
                    SwitchButton(checked = on, onCheckedChange = { model.setReceiving(it) }, modifier = Modifier.fillMaxWidth()) {
                        Text(tr("Получать сердечки"))
                    }
                }
                item {
                    Note(model.receiveProblem
                        ?: if (on && model.notificationsOff) tr("Уведомления выключены. Включи их в настройках часов.")
                        else tr("Часы получают сердечки сами, даже если телефон их не принимает."))
                }
            }
            item {
                FilledTonalButton(onClick = { open("glow") }, modifier = Modifier.fillMaxWidth(),
                    icon = { Box(Modifier.size(16.dp).background(Palette.color(model.myColor), CircleShape)) }) {
                    Text(tr("Как светится"))
                }
            }
            item {
                FilledTonalButton(onClick = { open("layout") }, modifier = Modifier.fillMaxWidth(),
                    secondaryLabel = { Text(model.layout.title, color = W.muted) }) {
                    Text(tr("Вид"))
                }
            }
            item {
                SwitchButton(checked = model.vibrates, onCheckedChange = { model.updateVibrates(it) }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Вибрация"))
                }
            }
            if (!model.vibrates) {
                item { Note(tr("Сердечко придёт тихо: без тапа и без звука. Его будет видно в уведомлениях.")) }
            }
            model.session?.email?.let { email ->
                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("Аккаунт"), fontSize = 12.sp, color = W.muted)
                        Text(email, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
            item {
                FilledTonalButton(onClick = { open("signout") }, modifier = Modifier.fillMaxWidth()) { Text(tr("Выйти")) }
            }
            item {
                FilledTonalButton(onClick = { open("delete") }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Удалить аккаунт"), color = W.danger)
                }
            }
        }
    }
}

/** «Вид»: bubbles, circles, circles with names or a list. */
@Composable
fun LayoutScreen(model: WatchModel) {
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(tr("Вид")) } }
            WatchLayout.entries.forEach { layout ->
                item {
                    FilledTonalButton(onClick = { model.updateLayout(layout) }, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(layout.title, modifier = Modifier.weight(1f))
                            if (model.layout == layout) Icon(Icons.Filled.Check, null, tint = W.link, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            item { Note(tr("Подержи палец на человеке, и покажется имя.")) }
        }
    }
}

/**
 * «Как светится»: my color, the one the edge glows in when someone thinks
 * of me, and a try of it. Stays on this watch.
 */
@Composable
fun GlowScreen(model: WatchModel) {
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(tr("Как светится")) } }
            Palette.all.indices.chunked(4).forEach { row ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { i ->
                            Box(Modifier.size(34.dp).background(Palette.color(i), CircleShape)
                                .then(if (i == model.myColor) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                .clickable { model.updateColor(i) },
                                contentAlignment = Alignment.Center) {
                                if (i == model.myColor) Icon(Icons.Filled.Check, Palette.all[i].name, tint = Color.White,
                                    modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            item {
                Button(onClick = { model.showSignal() }, modifier = Modifier.padding(top = 6.dp)) { Text(tr("Показать")) }
            }
            item { Note(tr("Так светятся края экрана, когда о тебе думают.")) }
        }
    }
}

/** «Выйти на этих часах?» */
@Composable
fun SignOutScreen(model: WatchModel, back: () -> Unit) {
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                Text(tr("Выйти на этих часах?"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
            item { Note(tr("Часы пропадут из устройств аккаунта. Люди и аккаунт останутся.")) }
            item {
                Button(onClick = { model.signOutHere() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = W.danger, contentColor = Color(0xFF2A211D))) {
                    Text(tr("Выйти"), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
            item {
                FilledTonalButton(onClick = back, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Отмена"), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * The watch's own token can't delete an account, and shouldn't: it says
 * where that is done.
 */
@Composable
fun DeleteAccountScreen() {
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(tr("Удалить аккаунт")) } }
            item {
                Text(tr("Удалятся твои люди, устройства и сам аккаунт. Вернуть нельзя."), fontSize = 13.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
            }
            item {
                Note(tr("Это делается в Love Around на телефоне: Настройки → Аккаунт, или на компьютере: app.lovearound.me."))
            }
        }
    }
}
