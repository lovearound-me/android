package me.lovearound.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.lovearound.android.AppModel
import me.lovearound.android.tr
import java.time.Instant

/**
 * Variant Б of the mockups, as changed on the iPhone: «Кому-нибудь на свете»
 * is a circle like the others, first in «Рядом», and «＋» ends the circles
 * while there is room for more.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(model: AppModel) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(refreshing, onRefresh = {
        scope.launch { refreshing = true; model.reload(); refreshing = false }
    }, modifier = Modifier.fillMaxSize().background(C.paper)) {
        Column(Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Favorite, null, tint = C.heart, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Love Around", color = C.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Banners(model)
                Circles(model)
                AnimatedVisibility(model.showsStart, enter = fadeIn(), exit = fadeOut()) { StartCard(model) }
                if (model.contacts.isNotEmpty()) {
                    Row(Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.field)
                        .clickable(role = Role.Button) { model.tab = AppModel.Tab.Book }
                        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = C.muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Вся книга · {n}", "n" to model.contacts.size.toString()), color = C.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.faint)
                    }
                }
            }
        }
    }
}

@Composable
private fun Banners(model: AppModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!model.receiveHere) {
            Banner(tr("Этот телефон не получает сердечки."), Icons.Filled.HeartBroken, tr("Включить")) { model.updateReceiveHere(true) }
        } else if (model.isPaused) {
            Banner(tr("{pause}. Сигналы не придут.", "pause" to model.pauseText), Icons.Filled.Bedtime, tr("Снять паузу")) { model.resume() }
        }
        if (model.offline) {
            Banner(tr("Нет связи с сервером, сигналы сейчас не приходят. Переподключаемся…"), Icons.Filled.WifiOff)
        }
    }
}

@Composable
private fun Circles(model: AppModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (model.closeContacts.isEmpty() && model.contacts.isNotEmpty()) tr("Книга") else tr("Рядом"),
            color = C.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        val cells = mutableListOf<@Composable () -> Unit>()
        cells += { AnyoneCircle(model) }
        for (contact in model.homeCircles) {
            cells += {
                Pressable({ model.send(contact) }, label = contact.label) {
                    PersonCircle(contact, sent = model.justSent == contact.id)
                }
            }
        }
        if (!model.closeFull) {
            cells += {
                Pressable({ model.addToClose() }, label = tr("Добавить в «Рядом»")) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DashedCircle(76.dp) { Icon(Icons.Filled.Add, null, tint = C.muted, modifier = Modifier.size(26.dp)) }
                        Spacer(Modifier.height(7.dp))
                        Text(tr("Добавить"), color = C.ink, fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            for (row in cells.chunked(3)) {
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until 3) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) { row.getOrNull(i)?.invoke() }
                    }
                }
            }
        }
        Text(tr("Кому-нибудь: сердечко случайному человеку, раз в час. Он не узнает, от кого."), color = C.muted, fontSize = 13.sp)
    }
}

/** «Кому-нибудь на свете»: a star among the people; dimmed, with the minutes left, while it waits. */
@Composable
fun AnyoneCircle(model: AppModel, size: androidx.compose.ui.unit.Dp = 76.dp) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = Instant.now() } }
    val wait = model.anyoneWait(if (model.anyoneSentAt != null) Instant.now() else now)
    val sent = model.justSent == AppModel.ANYONE_ID
    val star = C.star
    val fill by animateColorAsState(if (sent) star else star.copy(alpha = 0.18f), label = "fill")
    Pressable({ model.sendToAnyone(star) }, enabled = wait == null, label = tr("Кому-нибудь на свете")) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier
                .size(size)
                .alpha(if (wait != null && !sent) 0.5f else 1f)
                .clip(CircleShape)
                .background(fill)
                .border(3.dp, star, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.AutoAwesome, null, tint = if (sent) Color.White else star, modifier = Modifier.size(size * 0.42f))
            }
            Spacer(Modifier.height(7.dp))
            Text(tr("Кому-нибудь"), color = C.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Always there, empty while the star is ready: a cell that grows after a send would push the grid.
            Text(wait?.let { tr("через {n} мин", "n" to it.toString()) } ?: " ", color = C.muted, fontSize = 12.sp)
        }
    }
}

/**
 * Variant В of the intro mockups: «С чего начать» on the home screen, from
 * an empty book until the first heart to someone in it.
 */
@Composable
fun StartCard(model: AppModel) {
    val context = LocalContext.current
    val added = model.contacts.isNotEmpty()
    Column(Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(C.field)
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(tr("С чего начать"), color = C.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        StartStep(1, model.sharedLoveId, tr("Поделись своим Love ID."), tr("Это ссылка, по ней тебя добавят."))
        StartStep(2, added, tr("Добавь близкого по его ссылке."), null)
        StartStep(3, false, tr("Нажми на его кружок."), tr("Он почувствует, что о нём подумали, а от кого, не узнает."))
        if (!model.sharedLoveId && model.inviteUrl != null) {
            WideButton(tr("Мой Love ID"), { model.openedShare(); share(context, model.shareText) })
        }
        if (!added) {
            WideButton(tr("Добавить по ссылке"), { model.adding = "" }, filled = model.sharedLoveId)
        }
    }
}

@Composable
private fun StartStep(number: Int, done: Boolean, title: String, text: String?) {
    Row {
        Box(Modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(if (done) Modifier.background(C.ok) else Modifier.border(1.5.dp, C.faint, CircleShape)),
            contentAlignment = Alignment.Center) {
            if (done) Icon(Icons.Filled.Check, null, tint = C.paper, modifier = Modifier.size(15.dp))
            else Text(number.toString(), color = C.faint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.padding(top = 2.dp)) {
            Text(title, color = if (done) C.faint else C.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                textDecoration = if (done) TextDecoration.LineThrough else null)
            if (text != null && !done) Text(text, color = C.muted, fontSize = 15.sp)
        }
    }
}

/** «＋» among the circles: who of the book goes into «Рядом», or someone new. */
@Composable
fun ChooseCloseScreen(model: AppModel) {
    val close = { model.choosingClose = false }
    androidx.activity.compose.BackHandler(onBack = close)
    Column(Modifier.fillMaxSize().background(C.paper)) {
        PageBar(tr("Добавить в «Рядом»"), close) {
            Text(tr("Готово"), color = C.link, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(role = Role.Button, onClick = close).padding(12.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Group {
                GroupRow(onClick = { model.choosingClose = false; model.adding = "" }) {
                    Icon(Icons.Filled.Link, null, tint = C.link)
                    Spacer(Modifier.width(12.dp))
                    Text(tr("Новый человек по ссылке"), color = C.link, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            val others = model.otherContacts
            if (others.isNotEmpty()) {
                Group(header = tr("Из книги"),
                    footer = if (model.closeFull) tr("Рядом уже 12 человек: сначала убери кого-нибудь.")
                    else tr("В «Рядом» {n} из 12.", "n" to model.closeContacts.size.toString())) {
                    others.forEachIndexed { i, contact ->
                        if (i > 0) RowDivider()
                        GroupRow(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                            PersonCircle(contact, size = 36.dp, named = false)
                            Spacer(Modifier.width(14.dp))
                            Text(contact.label, color = C.ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Box(Modifier
                                .size(44.dp)
                                .clickable(enabled = !model.closeFull, role = Role.Button) { model.setClose(contact, true) },
                                contentAlignment = Alignment.Center) {
                                Box(Modifier.size(34.dp).clip(CircleShape).background(C.heartSoft), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Add, tr("Добавить в «Рядом»: {name}", "name" to contact.label), tint = C.link, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
