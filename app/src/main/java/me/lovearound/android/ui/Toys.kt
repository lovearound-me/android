package me.lovearound.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.lovearound.android.ApiError
import me.lovearound.android.AppModel
import me.lovearound.android.Config
import me.lovearound.android.Device
import me.lovearound.android.DeviceChange
import me.lovearound.android.PairingCard
import me.lovearound.android.QuietHours
import me.lovearound.android.tr
import java.time.Instant
import java.time.ZoneId

/**
 * The person's own devices: a watch, a lamp, a board. A device joins by the
 * six digits on its screen, or a board by its token. Pages open on top, by
 * path: "<id>", "<id>/who", "<id>/does", "<id>/name", "new-board".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToysScreen(model: AppModel) {
    val element = model.toysPath.lastOrNull()
    if (element != null) {
        val back = { model.toysPath = model.toysPath.dropLast(1) }
        androidx.activity.compose.BackHandler(onBack = back)
        val id = element.substringBefore("/")
        when {
            element == "new-board" -> BoardPage(model, back)
            element.endsWith("/who") -> DeviceWhoPage(model, id, back)
            element.endsWith("/does") -> DeviceDoesPage(model, id, back)
            element.endsWith("/name") -> DeviceNamePage(model, id, back)
            else -> DeviceCardPage(model, id, back)
        }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { model.loadDevices() }
    PullToRefreshBox(refreshing, onRefresh = { scope.launch { refreshing = true; model.loadDevices(); refreshing = false } },
        modifier = Modifier.fillMaxSize().background(C.paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text(tr("Игрушки"), color = C.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            Text(tr("Игрушка, лампа или своя плата, которая светится, когда о тебе думают, или отправляет сердечко по кнопке."),
                color = C.muted, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 4.dp))
            Group(header = tr("Устройства")) {
                when {
                    model.devicesProblem != null -> GroupRow { Text(model.devicesProblem!!, color = C.danger) }
                    !model.devicesLoaded -> GroupRow { CircularProgressIndicator(color = C.link, modifier = Modifier.size(22.dp)) }
                    model.devices.isEmpty() -> GroupRow { Text(tr("Пока ни одного."), color = C.muted) }
                }
                model.devices.forEachIndexed { i, device ->
                    if (i > 0) RowDivider()
                    GroupRow(onClick = { model.toysPath = model.toysPath + device.id }) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(C.well), contentAlignment = Alignment.Center) {
                            Icon(deviceIcon(device.kind), null, tint = C.ink, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(device.name, color = C.ink, fontSize = 17.sp)
                            Text(doesLine(device) + seen(device), color = C.muted, fontSize = 13.sp)
                        }
                    }
                }
            }
            Group(footer = tr("Код на шесть цифр показывают часы и лампы. Своей плате нужен токен: его вставляют в прошивку.")) {
                GroupRow(onClick = { model.pairing = "" }) {
                    Icon(Icons.Filled.Pin, null, tint = C.link); Spacer(Modifier.width(12.dp))
                    Text(tr("Подключить по коду"), color = C.link, fontSize = 16.sp)
                }
                RowDivider()
                GroupRow(onClick = { model.toysPath = model.toysPath + "new-board" }) {
                    Icon(Icons.Filled.Memory, null, tint = C.link); Spacer(Modifier.width(12.dp))
                    Text(tr("Своя плата"), color = C.link, fontSize = 16.sp)
                }
            }
            Group {
                GroupRow(onClick = { openPage(context, Config.SITE + "/docs", Color(0xFFFFFBF6)) }) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = C.link); Spacer(Modifier.width(12.dp))
                    Text(tr("Как подключить свою плату"), color = C.link, fontSize = 16.sp)
                }
            }
        }
    }
}

fun deviceIcon(kind: String?): ImageVector = when (kind) {
    "watch" -> Icons.Filled.Watch
    "lamp" -> Icons.Filled.TableRestaurant
    "custom" -> Icons.Filled.Memory
    else -> Icons.Filled.Lightbulb
}

fun kindName(kind: String?): String = when (kind) {
    "watch" -> tr("Часы")
    "lamp" -> tr("Лампа")
    "custom" -> tr("Своя плата")
    "toy" -> tr("Игрушка")
    else -> tr("Устройство")
}

/** « · был онлайн 5 минут назад», or that it never connected. */
fun seen(device: Device): String {
    val at = device.lastSeenAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return tr(" · ещё не подключался")
    val ago = DateUtils.getRelativeTimeSpanString(at.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    return tr(" · был онлайн {ago}", "ago" to ago)
}

/** «Книга и кто-нибудь на свете · тихо 23:00–08:00». */
fun reachLine(device: Device): String {
    if (!device.receives) return tr("Не принимает сердечки")
    val who = if (device.acceptAnyone == true) tr("Книга и кто-нибудь на свете") else tr("Только книга")
    val quiet = device.quietHours ?: return who + " · " + tr("круглые сутки")
    return who + " · " + tr("тихо {from}–{to}", "from" to quiet.from, "to" to quiet.to)
}

fun doesLine(device: Device): String = when {
    device.receives && device.sends -> tr("Принимает и отправляет")
    device.receives -> tr("Принимает")
    device.sends -> tr("Отправляет")
    else -> tr("Ничего не делает")
}

/** A device's card: who can reach it, what it does, its name; a board's new token; disconnecting. */
@Composable
private fun DeviceCardPage(model: AppModel, id: String, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val device = model.devices.firstOrNull { it.id == id }
    var confirmingToken by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(C.paper)) {
        PageBar("", back)
        if (device == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("Этого устройства уже нет"), color = C.muted) }
            return@Column
        }
        token?.let { NewToken(tr("Новый токен для «{name}»", "name" to device.name), it,
            tr("Старый уже не работает. Мы покажем новый только сейчас: скопируй и вставь в прошивку вместо старого. С ним можно отправлять сердечки от твоего имени, так что никому не показывай.")) { token = null }
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(76.dp).clip(CircleShape).background(C.heartSoft), contentAlignment = Alignment.Center) {
                    Icon(deviceIcon(device.kind), null, tint = C.heart, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(device.name, color = C.ink, fontSize = 25.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(kindName(device.kind) + seen(device), color = C.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            Group {
                SettingsRow(Icons.Filled.Favorite, C.link, tr("Кто может достучаться"), reachLine(device), { model.toysPath = model.toysPath + "$id/who" })
                RowDivider()
                SettingsRow(Icons.Filled.SwapHoriz, C.ok, tr("Что делает"), doesLine(device), { model.toysPath = model.toysPath + "$id/does" })
                RowDivider()
                SettingsRow(Icons.Filled.Edit, C.muted, tr("Название"), device.name, { model.toysPath = model.toysPath + "$id/name" })
            }
            if (!device.paired) {
                Group(footer = tr("Токен показывают один раз. Потерялся — выпусти новый: старый перестанет работать сразу, и новый надо будет вставить в прошивку.")) {
                    GroupRow(onClick = { confirmingToken = true }) {
                        Icon(Icons.Filled.Refresh, null, tint = C.link); Spacer(Modifier.width(12.dp))
                        Text(tr("Выпустить новый токен"), color = C.link, fontSize = 16.sp)
                    }
                }
            }
            Group(footer = if (device.paired) tr("Вернуть его можно по коду с экрана устройства.") else null) {
                GroupRow(onClick = { removing = true }) { Text(tr("Отключить"), color = C.danger, fontSize = 16.sp) }
            }
        }
        if (confirmingToken) {
            AlertDialog({ confirmingToken = false }, containerColor = C.paper,
                title = { Text(tr("Выпустить новый токен?")) },
                text = { Text(tr("Старый перестанет работать сразу, и «{name}» отключится, пока ты не вставишь новый в прошивку.", "name" to device.name)) },
                confirmButton = {
                    TextButton({
                        confirmingToken = false
                        scope.launch {
                            try { token = model.newDeviceToken(device) } catch (e: Exception) { model.show(tr("Не получилось, попробуй ещё раз"), failed = true) }
                        }
                    }) { Text(tr("Выпустить"), color = C.danger) }
                },
                dismissButton = { TextButton({ confirmingToken = false }) { Text(tr("Отмена"), color = C.ink) } })
        }
        if (removing) {
            AlertDialog({ removing = false }, containerColor = C.paper,
                title = { Text(tr("Отключить {name}?", "name" to device.name)) },
                text = { Text(tr("Оно перестанет получать и отправлять сердечки. Подключить снова можно в любой момент.")) },
                confirmButton = { TextButton({ removing = false; scope.launch { model.removeDevice(device) } }) { Text(tr("Отключить"), color = C.danger) } },
                dismissButton = { TextButton({ removing = false }) { Text(tr("Отмена"), color = C.ink) } })
        }
    }
}

/** «Кто может достучаться» on a device: kept by the server, so it works with any firmware. */
@Composable
private fun DeviceWhoPage(model: AppModel, id: String, back: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val device = model.devices.firstOrNull { it.id == id }
    var from by remember { mutableStateOf(device?.quietHours?.from ?: "23:00") }
    var to by remember { mutableStateOf(device?.quietHours?.to ?: "08:00") }
    fun change(c: DeviceChange) { scope.launch { model.changeDevice(id, c) } }
    fun pick(current: String, set: (String) -> Unit) {
        val m = QuietHours.minutes(current) ?: 0
        android.app.TimePickerDialog(context, { _, h, min ->
            set(String.format(java.util.Locale.ROOT, "%02d:%02d", h, min))
            if (from != to) change(DeviceChange(setsQuietHours = true, quietHours = QuietHours(from, to, ZoneId.systemDefault().id)))
        }, m / 60, m % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
    }
    Column(Modifier.fillMaxSize().background(C.paper)) {
        PageBar(tr("Кто может достучаться"), back)
        if (device == null) return@Column
        val note = run {
            val base = tr("В эти часы сердечки на устройство не приходят и потом не придут. На телефон и другие устройства — как обычно.")
            val tz = device.quietHours?.tz
            if (tz != null && tz != ZoneId.systemDefault().id) base + " " + tr("Время — по часовому поясу {tz}.", "tz" to tz)
            else base + " " + tr("Время — как на этом телефоне.")
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Group(footer = tr("Сердечки от людей из книги приходят всегда, кроме тихих часов.")) {
                SwitchRow(tr("Кому-нибудь на свете"), device.acceptAnyone ?: false, { change(DeviceChange(acceptAnyone = it)) },
                    sub = tr("Сердечко от случайного человека"), tint = C.link)
            }
            Group(footer = note) {
                SwitchRow(tr("Тихие часы"), device.quietHours != null, { on ->
                    change(DeviceChange(setsQuietHours = true, quietHours = if (on) QuietHours(from, to, ZoneId.systemDefault().id) else null))
                }, tint = C.link)
                if (device.quietHours != null) {
                    RowDivider()
                    GroupRow(onClick = { pick(from) { from = it } }) {
                        Text(tr("с"), color = C.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        TimeChip(from)
                    }
                    RowDivider()
                    GroupRow(onClick = { pick(to) { to = it } }) {
                        Text(tr("до"), color = C.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        TimeChip(to)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeChip(value: String) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(C.well).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(value, color = C.ink, fontSize = 16.sp)
    }
}

/** «Что делает»: receives, sends; a paired device only what it asked for. */
@Composable
private fun DeviceDoesPage(model: AppModel, id: String, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val device = model.devices.firstOrNull { it.id == id }
    Column(Modifier.fillMaxSize().background(C.paper)) {
        PageBar(tr("Что делает"), back)
        if (device == null) return@Column
        fun toggle(capability: String, on: Boolean) {
            val caps = device.capabilities.toMutableSet()
            if (on) caps += capability else caps -= capability
            scope.launch { model.changeDevice(id, DeviceChange(capabilities = caps.sorted())) }
        }
        val sendNote = when {
            !device.may("send") -> tr("Не просило при подключении")
            device.sends -> tr("Для этого видит твоих людей")
            else -> tr("Включишь — увидит твоих людей")
        }
        Column(Modifier.padding(16.dp)) {
            Group(footer = if (device.paired) tr("Можно выключить то, что устройство просило при подключении, и включить обратно. Выключишь всё — оно останется в аккаунте, но ничего не будет делать.")
            else tr("Своей плате можно включить что угодно. Выключишь всё — она останется в аккаунте, но ничего не будет делать.")) {
                SwitchRow(tr("Принимает сердечки"), device.receives, { toggle("receive", it) },
                    sub = if (device.may("receive")) null else tr("Не просило при подключении"), enabled = device.may("receive"), tint = C.link)
                RowDivider()
                SwitchRow(tr("Отправляет сердечки"), device.sends, { toggle("send", it) }, sub = sendNote, enabled = device.may("send"), tint = C.link)
            }
        }
    }
}

/** «Название»: what the device is called in every app; only its owner sees it. */
@Composable
private fun DeviceNamePage(model: AppModel, id: String, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(model.devices.firstOrNull { it.id == id }?.name ?: "") }
    var busy by remember { mutableStateOf(false) }
    fun save() {
        val new = name.trim().take(64)
        if (new.isEmpty()) return
        if (new == model.devices.firstOrNull { it.id == id }?.name) return back()
        busy = true
        scope.launch {
            if (model.changeDevice(id, DeviceChange(name = new))) back()
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().background(C.paper).imePadding()) {
        PageBar(tr("Название"), back) {
            TextButton(::save, enabled = name.isNotBlank() && !busy) { Text(tr("Готово"), color = C.link, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.padding(16.dp)) {
            Group(footer = tr("Так устройство называется во всех твоих приложениях. Видишь название только ты.")) {
                OutlinedTextField(name, { name = it }, placeholder = { Text(tr("Например, Котик на полке"), color = C.faint) }, singleLine = true,
                    colors = fieldColors(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** A token shown once, to paste into a board's firmware. */
@Composable
private fun NewToken(title: String, token: String, note: String, done: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = done)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Group(header = title, footer = note) {
            GroupRow {
                SelectionContainer { Text(token, color = C.ink, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp)) }
            }
            RowDivider()
            GroupRow(onClick = {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("token", token))
                copied = true
            }) {
                Icon(if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy, null, tint = C.link); Spacer(Modifier.width(12.dp))
                Text(if (copied) tr("Скопировано") else tr("Скопировать токен"), color = C.link, fontSize = 16.sp)
            }
        }
        Group {
            GroupRow(onClick = { openPage(context, Config.SITE + "/docs", Color(0xFFFFFBF6)) }) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = C.link); Spacer(Modifier.width(12.dp))
                Text(tr("Как подключить свою плату"), color = C.link, fontSize = 16.sp)
            }
        }
        WideButton(tr("Готово"), done)
    }
}

/** A board of one's own: a name and what it does, then its token, shown once. */
@Composable
private fun BoardPage(model: AppModel, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(if (model.demo != null) me.lovearound.android.Demo.devices[1].name else "") }
    var receives by remember { mutableStateOf(true) }
    var sends by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf(if (model.demo != null) "dev_demo_7Hq2kLx9WbN4sP0aZr" else null) }
    val ready = !busy && name.isNotBlank() && (receives || sends)
    fun create() {
        busy = true
        problem = null
        val caps = listOfNotNull("receive".takeIf { receives }, "send".takeIf { sends })
        scope.launch {
            try {
                token = model.addBoard(name.trim(), caps)
            } catch (e: ApiError) {
                problem = if (e.status == 409 && (e as? ApiError.Status)?.error == "too many devices") tr("Устройств уже много, отключи лишние") else e.message
            } catch (e: Exception) {
                problem = tr("Не получилось, попробуй ещё раз")
            }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().background(C.paper).imePadding()) {
        PageBar(tr("Своя плата"), back) {
            if (token == null) TextButton(::create, enabled = ready) {
                Text(tr("Подключить"), color = if (ready) C.link else C.faint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        val shown = token
        if (shown != null) {
            NewToken(tr("«{name}» подключено", "name" to name), shown,
                tr("Это токен устройства. Мы покажем его только сейчас: скопируй и вставь в прошивку. С ним можно отправлять сердечки от твоего имени, так что никому не показывай."), back)
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Group(header = tr("Название")) {
                OutlinedTextField(name, { name = it }, placeholder = { Text(tr("Например, Котик на полке"), color = C.faint) }, singleLine = true,
                    colors = fieldColors(), modifier = Modifier.fillMaxWidth())
            }
            Group {
                SwitchRow(tr("Принимает сердечки"), receives, { receives = it }, tint = C.link)
                RowDivider()
                SwitchRow(tr("Отправляет сердечки"), sends, { sends = it }, tint = C.link)
            }
            problem?.let { Text(it, color = C.danger, fontSize = 15.sp) }
        }
    }
}

/** «Подключить по коду»: the six digits a watch or a lamp shows. The device gets its token from the server. */
@Composable
fun PairScreen(model: AppModel, initial: String) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(initial) }
    var card by remember { mutableStateOf<PairingCard?>(null) }
    var looking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val dismiss = { model.pairing = null }
    androidx.activity.compose.BackHandler(onBack = dismiss)

    fun message(e: Exception): String = when ((e as? ApiError)?.status) {
        404 -> tr("Такого кода нет или он устарел. Проверь цифры или попроси у устройства новый.")
        429 -> tr("Слишком много попыток, попробуй через час")
        else -> e.message ?: tr("Не получилось, попробуй ещё раз")
    }

    fun look() {
        if (code.length != 6 || looking) return
        val wanted = code
        looking = true
        scope.launch {
            try {
                val found = model.findPairing(wanted)
                if (code == wanted) card = found
            } catch (e: Exception) {
                if (code == wanted) problem = message(e)
            }
            looking = false
        }
    }

    LaunchedEffect(Unit) { if (code.isEmpty()) runCatching { focus.requestFocus() } else look() }

    /** What the card promises, from what the device asked for. */
    fun promises(caps: List<String>): List<String> {
        val receives = "receive" in caps
        val sends = "send" in caps
        return when {
            receives && sends -> listOf(tr("увидит твоих людей"), tr("будет отправлять и получать сигналы"))
            sends -> listOf(tr("увидит твоих людей"), tr("будет отправлять сигналы, получать не будет"))
            else -> listOf(tr("будет получать сигналы"), tr("людей не увидит, отправлять не сможет"))
        }
    }

    Column(Modifier.fillMaxSize().background(C.paper).imePadding()) {
        PageBar(tr("Подключить устройство"), dismiss)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            val finished = done
            if (finished != null) {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = C.ok, modifier = Modifier.size(52.dp))
                    Text(tr("Подключено"), color = C.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(tr("{name} теперь в твоём аккаунте.", "name" to finished), color = C.muted, textAlign = TextAlign.Center)
                }
                WideButton(tr("Готово"), dismiss)
                return@Column
            }
            Group(header = tr("Код с экрана устройства"),
                footer = problem ?: if (looking) tr("Ищем устройство…") else if (card == null) tr("Цифры видны на экране часов, лампы или другого устройства. Код живёт 10 минут.") else null,
                footerColor = if (problem != null) C.danger else null) {
                OutlinedTextField(code, { typed ->
                    val digits = typed.filter { it.isDigit() }.take(6)
                    code = digits
                    card = null
                    problem = null
                    if (digits.length == 6) look()
                }, placeholder = { Text("000000", color = C.faint, fontSize = 30.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                    singleLine = true, colors = fieldColors(),
                    textStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, letterSpacing = 6.sp, color = C.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus))
            }
            card?.let { found ->
                Group {
                    GroupRow {
                        Icon(deviceIcon(found.kind), null, tint = C.ink); Spacer(Modifier.width(12.dp))
                        Text(found.name, color = C.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    for (line in promises(found.capabilities)) {
                        Text("· $line", color = C.muted, fontSize = 15.sp, modifier = Modifier.padding(start = 16.dp, bottom = 10.dp))
                    }
                }
                Group(header = tr("В аккаунт")) { GroupRow { Text(model.email ?: "", color = C.ink) } }
                Group(footer = tr("Подключай только свои устройства. Если код тебе прислали, не вводи его.")) {
                    WideButton(if (busy) tr("Подключаем…") else tr("Подключить"), {
                        busy = true
                        problem = null
                        scope.launch {
                            try {
                                model.confirmPairing(code)
                                done = found.name
                            } catch (e: Exception) {
                                problem = message(e)
                                if ((e as? ApiError)?.status == 404) card = null
                            }
                            busy = false
                        }
                    }, enabled = !busy)
                }
            }
        }
    }
}
