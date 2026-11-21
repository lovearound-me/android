package me.lovearound.android.wear

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import me.lovearound.android.Config
import me.lovearound.android.tr

/** The app's heart on its gradient, as on the phone's sign-in screen. */
@Composable
fun AppMark(size: Dp) {
    Box(Modifier.size(size).background(Brush.linearGradient(listOf(Color(0xFFF58578), Color(0xFFD94D6E))), CircleShape),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * The watch signs in by six digits, which the phone app (or the web app
 * on a computer) confirms: no password is ever typed on the wrist.
 */
@Composable
fun SignInScreen(model: WatchModel) {
    val state = rememberScalingLazyListState()
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppMark(26.dp)
                    Text("Love Around", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            item {
                Text(tr("Тихий знак, что о тебе думают"), fontSize = 12.sp, color = W.muted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            }
            item {
                Button(onClick = { model.startCode() }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(tr("Войти по коду"), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
            model.notice?.let { notice ->
                item {
                    Text(notice, fontSize = 12.sp, color = W.link, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
    }
}

/** Six digits with a space in the middle: 482 913. */
fun spaced(code: String): String = if (code.length == 6) code.take(3) + " " + code.takeLast(3) else code

/**
 * The QR and the digits: the phone's camera opens the phone app's «Подключить
 * устройство», which confirms them; the address for a computer is a tap away.
 */
@Composable
fun CodeScreen(model: WatchModel) {
    var computer by rememberSaveable { mutableStateOf(false) }
    if (computer) {
        BackHandler { computer = false }
        ComputerScreen(model.pairing?.code ?: "")
        return
    }
    BackHandler { model.cancelCode() }
    // The QR and the digits in the middle, the rest below.
    val state = rememberScalingLazyListState(initialCenterItemIndex = 0)
    val pairing = model.pairing
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            val error = model.pairingError
            when {
                error != null -> {
                    item { Text(error, fontSize = 14.sp, textAlign = TextAlign.Center) }
                    item { Button(onClick = { model.startCode() }) { Text(tr("Ещё раз")) } }
                }
                pairing == null -> item { CircularProgressIndicator(Modifier.size(36.dp).padding(top = 6.dp)) }
                model.pairingExpired -> {
                    item { Text(tr("Код устарел"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    item { Button(onClick = { model.startCode() }) { Text(tr("Новый код")) } }
                }
                else -> {
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 16.dp)) {
                            QrView(pairing.url, 88.dp)
                            Text(spaced(pairing.code), fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    item {
                        Text(tr("Наведи камеру телефона"), fontSize = 12.sp, color = W.muted, textAlign = TextAlign.Center)
                    }
                    item {
                        FilledTonalButton(onClick = { computer = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(tr("Ввести на компьютере"), fontSize = 13.sp, modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            item {
                Text(tr("Отмена"), color = W.muted, fontSize = 14.sp,
                    modifier = Modifier.clickable { model.cancelCode() }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

/** The address and the digits, big, for typing on a computer. */
@Composable
fun ComputerScreen(code: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("Открой"), fontSize = 13.sp, color = W.muted)
            Text(Config.PAIRING_PAGE.replace("/login", "\n/login"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = W.link, textAlign = TextAlign.Center)
            Text(tr("и введи код"), fontSize = 13.sp, color = W.muted, modifier = Modifier.padding(top = 10.dp))
            Text(spaced(code), fontSize = 30.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun ConnectedScreen(model: WatchModel) {
    Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Favorite, null, tint = W.link, modifier = Modifier.size(52.dp))
            Text(tr("Часы подключены"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            model.session?.email?.let { Text(it, fontSize = 12.sp, color = W.muted, maxLines = 1) }
        }
    }
}
