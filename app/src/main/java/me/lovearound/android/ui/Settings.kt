package me.lovearound.android.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.lovearound.android.AppModel
import me.lovearound.android.AppModel.SettingsPage
import me.lovearound.android.Config
import me.lovearound.android.NotifyLook
import me.lovearound.android.Palette
import me.lovearound.android.QuietHours
import me.lovearound.android.tr
import java.time.ZoneId

/** One block of rows like a device's card, the account first; each row opens its own page, «Пауза» a menu. */
@Composable
fun SettingsScreen(model: AppModel, askNotifications: () -> Unit) {
    val page = model.settingsPath.lastOrNull()
    if (page != null) {
        androidx.activity.compose.BackHandler { model.settingsPath = model.settingsPath.dropLast(1) }
        val back = { model.settingsPath = model.settingsPath.dropLast(1) }
        when (page) {
            SettingsPage.Account -> AccountPage(model, back)
            SettingsPage.Who -> WhoPage(model, back)
            SettingsPage.Glow -> GlowPage(model, back)
            SettingsPage.Look -> LookPage(model, back, askNotifications)
            SettingsPage.Does -> DoesPage(model, back)
        }
        return
    }
    val context = LocalContext.current
    var showingHow by remember { mutableStateOf(false) }
    if (showingHow) {
        androidx.activity.compose.BackHandler { showingHow = false }
        HowItWorks(tr("Понятно")) { showingHow = false }
        return
    }
    fun open(p: SettingsPage) { model.settingsPath = model.settingsPath + p }
    Column(Modifier.fillMaxSize().background(C.paper).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text(tr("Настройки"), color = C.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Group {
            SettingsRow(Icons.Filled.AccountCircle, C.link, tr("Аккаунт"), shownEmail(model.email), { open(SettingsPage.Account) })
            RowDivider()
            SettingsRow(Icons.Filled.Favorite, C.heart, tr("Кто может достучаться"), whoLine(model), { open(SettingsPage.Who) })
            RowDivider()
            SettingsRow(Icons.Filled.WbSunny, Palette.color(model.myColor), tr("Как светится"), Palette.all[Palette.index(model.myColor)].name, { open(SettingsPage.Glow) })
            RowDivider()
            SettingsRow(Icons.Filled.Notifications, C.star, tr("Уведомление"), lookLine(model), { open(SettingsPage.Look) })
            RowDivider()
            SettingsRow(Icons.Filled.SwapHoriz, C.ok, tr("Что делает"),
                if (model.receiveHere) tr("Принимает и отправляет") else tr("Отправляет"), { open(SettingsPage.Does) })
            RowDivider()
            PauseRow(model)
        }
        Group(footer = "Love Around ${model.version}") {
            GroupRow(onClick = { showingHow = true }) {
                Text(tr("Как это работает"), color = C.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = C.faint)
            }
            RowDivider()
            PageRow(tr("Помощь")) { openPage(context, Config.SUPPORT_PAGE, Color(0xFFFFFBF6)) }
            RowDivider()
            PageRow(tr("Конфиденциальность")) { openPage(context, Config.PRIVACY_PAGE, Color(0xFFFFFBF6)) }
        }
    }
}

@Composable
private fun PageRow(title: String, onClick: () -> Unit) {
    GroupRow(onClick = onClick) {
        Text(title, color = C.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = C.faint, modifier = Modifier.size(18.dp))
    }
}

/** A menu right on the row: three lengths, or «Снять паузу». */
@Composable
private fun PauseRow(model: AppModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        SettingsRow(Icons.Filled.Bedtime, C.indigo, tr("Пауза"), if (model.isPaused) model.pauseText else tr("Нет"),
            { open = true }, enabled = model.receiveHere) {
            Icon(Icons.Filled.UnfoldMore, null, tint = C.faint)
        }
        DropdownMenu(open, { open = false }, modifier = Modifier.background(C.paper)) {
            if (model.isPaused) {
                DropdownMenuItem({ Text(tr("Снять паузу")) }, { open = false; model.resume() })
            } else {
                DropdownMenuItem({ Text(tr("На час")) }, { open = false; model.pause(AppModel.PauseLength.Hour) })
                DropdownMenuItem({ Text(tr("До утра")) }, { open = false; model.pause(AppModel.PauseLength.UntilMorning) })
                DropdownMenuItem({ Text(tr("Пока не сниму")) }, { open = false; model.pause(AppModel.PauseLength.UntilResumed) })
            }
        }
    }
}

/** «Книга и кто-нибудь на свете · тихо 23:00–08:00», as a device's. */
private fun whoLine(model: AppModel): String {
    if (!model.receiveHere) return tr("Не принимает сердечки")
    val who = if (model.acceptAnyone) tr("Книга и кто-нибудь на свете") else tr("Только книга")
    val quiet = model.quietHours ?: return who + " · " + tr("круглые сутки")
    return who + " · " + tr("тихо {from}–{to}", "from" to quiet.from, "to" to quiet.to)
}

private fun lookLine(model: AppModel): String {
    if (!model.notificationsAllowed) return tr("Выключены в настройках телефона")
    return if (model.notifyWords) model.notifyLook.title + " · " + tr("со словами") else model.notifyLook.title
}

/** Apple's relay address means nothing to the person. */
fun shownEmail(email: String?): String {
    if (email.isNullOrEmpty()) return "Apple ID"
    return if (email.endsWith("@privaterelay.appleid.com")) tr("Почту скрыл Apple") else email
}

/** «Аккаунт»: who is signed in and how, the Love ID, the ways in, signing out, and at the bottom deleting it all. */
@Composable
private fun AccountPage(model: AppModel, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val signedInWith = when (model.provider) {
        "apple.com" -> tr("Вход через Apple")
        "google.com" -> tr("Вход через Google")
        "password" -> tr("Вход по почте")
        else -> tr("Книга хранится в аккаунте")
    }
    Column(Modifier.fillMaxSize().background(C.paper)) {
        PageBar(tr("Аккаунт"), back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Group(footer = tr("Love ID — твоя ссылка: по ней тебя добавляют в книгу. Нажми, чтобы отправить или скопировать.")) {
                GroupRow {
                    Column {
                        Text(shownEmail(model.email), color = C.ink, fontSize = 16.sp)
                        Text(signedInWith, color = C.muted, fontSize = 13.sp)
                    }
                }
                model.inviteUrl?.let { link ->
                    RowDivider()
                    GroupRow(onClick = { share(context, model.shareText) }) {
                        Column(Modifier.weight(1f)) {
                            Text("Love ID", color = C.ink, fontSize = 16.sp)
                            Text(link.removePrefix("https://"), color = C.muted, fontSize = 13.sp)
                        }
                        Icon(Icons.Filled.Share, tr("Поделиться своим Love ID"), tint = C.link)
                    }
                }
                RowDivider()
                PageRow(tr("Способы входа")) { openPage(context, "https://${Config.WEB_APP}/#/settings/signin", Color(0xFFFFFBF6)) }
                RowDivider()
                GroupRow(onClick = { confirmSignOut = true }) { Text(tr("Выйти"), color = C.danger, fontSize = 16.sp) }
            }
            Group(footer = listOfNotNull(problem, tr("Вместе с книгой, устройствами и Love ID.")).joinToString("\n")) {
                GroupRow(onClick = { confirmDelete = true }, enabled = !deleting) {
                    Text(if (deleting) tr("Удаляем…") else tr("Удалить аккаунт"), color = C.danger, fontSize = 16.sp)
                }
            }
        }
    }
    if (confirmSignOut) {
        AlertDialog({ confirmSignOut = false }, containerColor = C.paper,
            title = { Text(tr("Выйти на этом телефоне?")) },
            text = { Text(tr("Книга останется в аккаунте.")) },
            confirmButton = { TextButton({ confirmSignOut = false; scope.launch { model.signOutHere() } }) { Text(tr("Выйти"), color = C.danger) } },
            dismissButton = { TextButton({ confirmSignOut = false }) { Text(tr("Отмена"), color = C.ink) } })
    }
    if (confirmDelete) {
        AlertDialog({ confirmDelete = false }, containerColor = C.paper,
            title = { Text(tr("Удалить аккаунт и весь список? Это навсегда.")) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    deleting = true
                    problem = null
                    scope.launch {
                        try { model.deleteAccount() } catch (e: Exception) { problem = tr("Не получилось удалить") }
                        deleting = false
                    }
                }) { Text(tr("Удалить навсегда"), color = C.danger) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(tr("Не удалять"), color = C.ink) } })
    }
}

/** «Кто может достучаться» on this phone: hearts from anyone in the world, and quiet hours. */
@Composable
private fun WhoPage(model: AppModel, back: () -> Unit) {
    val context = LocalContext.current
    var from by remember { mutableStateOf(model.quietHours?.from ?: "23:00") }
    var to by remember { mutableStateOf(model.quietHours?.to ?: "08:00") }
    fun save() {
        if (model.quietHours == null || from == to) return
        model.updateQuietHours(QuietHours(from, to, ZoneId.systemDefault().id))
    }
    fun pick(current: String, set: (String) -> Unit) {
        val m = QuietHours.minutes(current) ?: 0
        TimePickerDialog(context, { _, h, min -> set(String.format(java.util.Locale.ROOT, "%02d:%02d", h, min)); save() }, m / 60, m % 60,
            android.text.format.DateFormat.is24HourFormat(context)).show()
    }
    val note = run {
        val base = tr("В эти часы на этот телефон сердечки не приходят и потом не придут: ни свечения, ни уведомления. Компьютер, часы и игрушки — как обычно, у них тихие часы свои.")
        val tz = model.quietHours?.tz
        if (tz != null && tz != ZoneId.systemDefault().id) base + " " + tr("Время — по часовому поясу {tz}.", "tz" to tz)
        else base + " " + tr("Время — как на этом телефоне.")
    }
    Column(Modifier.fillMaxSize().background(C.paper)) {
        PageBar(tr("Кто может достучаться"), back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Group(footer = tr("Сердечки от людей из книги приходят всегда, кроме тихих часов и паузы.")) {
                SwitchRow(tr("Кому-нибудь на свете"), model.acceptAnyone, { model.updateAcceptAnyone(it) },
                    sub = tr("Сердечко от случайного человека"), enabled = model.receiveHere)
            }
            Group(footer = note) {
                SwitchRow(tr("Тихие часы"), model.quietHours != null, { on ->
                    model.updateQuietHours(if (on) QuietHours(from, to, ZoneId.systemDefault().id) else null)
                }, enabled = model.receiveHere)
                if (model.quietHours != null) {
                    RowDivider()
                    TimeRow(tr("с"), from) { pick(from) { from = it } }
                    RowDivider()
                    TimeRow(tr("до"), to) { pick(to) { to = it } }
                }
            }
        }
    }
}

@Composable
private fun TimeRow(title: String, value: String, onClick: () -> Unit) {
    GroupRow(onClick = onClick) {
        Text(title, color = C.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(C.well).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(value, color = C.ink, fontSize = 16.sp)
        }
    }
}

/** «Как светится»: the color of this phone's edges when a heart comes. */
@Composable
private fun GlowPage(model: AppModel, back: () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.paper)) {
        PageBar(tr("Как светится"), back)
        Column(Modifier.padding(16.dp)) {
            Group(footer = tr("Так светятся края экрана, когда о тебе думают.")) {
                ColorSwatches(model.myColor, { model.updateMyColor(it) })
            }
        }
    }
}

/** «Что делает»: this phone takes hearts, or only sends them. */
@Composable
private fun DoesPage(model: AppModel, back: () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.paper)) {
        PageBar(tr("Что делает"), back)
        Column(Modifier.padding(16.dp)) {
            Group(footer = tr("Выключишь, и этот телефон не засветится и не пришлёт уведомление. Компьютер, часы и игрушки получают как раньше. Отправлять сердечки он может всегда.")) {
                SwitchRow(tr("Получать сердечки на этом телефоне"), model.receiveHere, { model.updateReceiveHere(it) })
            }
        }
    }
}

/** «Уведомление»: how a heart's notification reads, with a sample as on a lock screen. */
@Composable
private fun LookPage(model: AppModel, back: () -> Unit, askNotifications: () -> Unit) {
    val context = LocalContext.current
    var sample by remember { mutableStateOf(model.notifyLook.text(model.notifyWords)) }
    Column(Modifier.fillMaxSize().background(C.paper)) {
        PageBar(tr("Уведомление"), back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            if (!model.notificationsAllowed && model.demo == null) {
                Group(footer = tr("Уведомления выключены: сердечки видно, только когда приложение открыто.")) {
                    GroupRow(onClick = askNotifications) { Text(tr("Разрешить уведомления"), color = C.link, fontSize = 16.sp) }
                }
            }
            Group(header = tr("Так оно выглядит"), background = Color(0xFF4A342B)) {
                NotificationSample(sample)
            }
            Group {
                NotifyLook.entries.forEachIndexed { i, look ->
                    if (i > 0) RowDivider()
                    GroupRow(onClick = { model.updateNotifyLook(look); sample = look.text(model.notifyWords) }) {
                        Column(Modifier.weight(1f)) {
                            Text(look.title, color = C.ink, fontSize = 16.sp)
                            Text(look.example, color = C.muted, fontSize = 15.sp)
                            look.note?.let { Text(it, color = C.muted, fontSize = 12.5.sp) }
                        }
                        if (model.notifyLook == look) Icon(Icons.Filled.Check, null, tint = C.link)
                    }
                }
            }
            Group(footer = if (!model.vibrates) tr("Сердечко придёт без вибрации и без звука.") else null) {
                SwitchRow(tr("Вибрация"), model.vibrates, { model.updateVibrates(it) })
            }
            Group(footer = tr("Эмодзи выбирает сам телефон, когда приходит сердечко, каждый раз новые. От кого оно, не видно ни в одном варианте. Часы и браслет показывают то же, что телефон.")) {
                SwitchRow(tr("Со словами"), model.notifyWords, { model.updateNotifyWords(it); sample = model.notifyLook.text(it) },
                    sub = tr("После эмодзи: «{words}»", "words" to NotifyLook.words))
            }
        }
    }
}

/** A notification as Android draws it, for the sample. */
@Composable
private fun NotificationSample(text: String) {
    Row(Modifier
        .padding(10.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(22.dp))
        .background(Color(0xFFFFFBF6).copy(alpha = 0.92f))
        .padding(horizontal = 14.dp, vertical = 12.dp)) {
        AppMark(38.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Row {
                Text("Love Around", color = Color(0xFF3B2A23), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(tr("сейчас"), color = Color(0xFF6E554A), fontSize = 13.sp)
            }
            Text(text, color = Color(0xFF3B2A23), fontSize = 15.sp)
        }
    }
}
