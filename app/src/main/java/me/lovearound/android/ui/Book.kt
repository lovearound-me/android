package me.lovearound.android.ui

import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import me.lovearound.android.AppModel
import me.lovearound.android.Contact
import me.lovearound.android.tr
import kotlin.math.roundToInt

/**
 * The whole book: «Рядом» in the order set by hand (drag to change it), the
 * others the most recently signalled first. A tap sends a heart; the
 * pencil opens the person's card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(model: AppModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var reordering by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    fun matches(c: Contact) = search.isBlank() || c.label.contains(search.trim(), ignoreCase = true)
    val close = model.closeContacts.filter(::matches)
    val others = model.otherContacts.filter(::matches)

    Column(Modifier.fillMaxSize().background(C.paper)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Книга"), color = C.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (model.closeContacts.size > 1 && search.isBlank()) {
                TextButton({ reordering = !reordering }) {
                    Text(if (reordering) tr("Готово") else tr("Порядок"), color = C.link, fontSize = 16.sp)
                }
            }
            IconButton({ model.adding = "" }) { Icon(Icons.Filled.Add, tr("Добавить по ссылке"), tint = C.link) }
        }
        OutlinedTextField(search, { search = it }, placeholder = { Text(tr("Найти"), color = C.faint) }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = C.faint) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = C.well, unfocusedContainerColor = C.well,
                focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, cursorColor = C.link),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        PullToRefreshBox(refreshing, onRefresh = { scope.launch { refreshing = true; model.reload(); refreshing = false } },
            modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                if (model.contacts.isEmpty() && model.loaded) EmptyBook(model)
                if (close.isNotEmpty()) {
                    Group(header = tr("Рядом"), footer = if (search.isBlank()) tr("Кружки на главном экране, в этом порядке.") else null) {
                        if (reordering && search.isBlank()) ReorderList(model, close)
                        else close.forEachIndexed { i, c -> if (i > 0) RowDivider(); BookRow(model, c) }
                    }
                }
                if (others.isNotEmpty()) {
                    Group(header = tr("Остальные")) {
                        others.forEachIndexed { i, c -> if (i > 0) RowDivider(); BookRow(model, c) }
                    }
                }
                if (model.inviteUrl != null) {
                    Group(footer = tr("Отправь его тем, кто хочет нажимать, когда думает о тебе.")) {
                        GroupRow(onClick = { share(context, model.shareText) }) {
                            Icon(Icons.Filled.Share, null, tint = C.link, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(tr("Поделиться своим Love ID"), color = C.link, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRow(model: AppModel, contact: Contact) {
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier
            .weight(1f)
            .fillMaxSize()
            .clickable(role = Role.Button) { model.send(contact) }
            .semantics { contentDescription = contact.label + ", " + tr("Отправить сердечко") }
            .padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonCircle(contact, sent = model.justSent == contact.id, size = 40.dp, named = false)
            Spacer(Modifier.width(14.dp))
            Text(contact.label, color = C.ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.FavoriteBorder, null, tint = C.heart, modifier = Modifier.size(22.dp))
        }
        IconButton({ model.editing = contact }) { Icon(Icons.Filled.Edit, tr("Изменить"), tint = C.muted, modifier = Modifier.size(20.dp)) }
    }
}

/** «Порядок»: rows held by the handle and dragged up or down; saved a moment after the last move. */
@Composable
private fun ReorderList(model: AppModel, close: List<Contact>) {
    val rowPx = with(LocalDensity.current) { 60.dp.toPx() }
    var dragging by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    close.forEachIndexed { i, contact ->
        if (i > 0) RowDivider()
        val lifted = dragging == i
        Row(Modifier
            .zIndex(if (lifted) 1f else 0f)
            .offset { IntOffset(0, if (lifted) offset.roundToInt() else 0) }
            .then(if (lifted) Modifier.shadow(8.dp).background(C.field) else Modifier)
            .fillMaxWidth()
            .height(60.dp)
            .padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PersonCircle(contact, size = 40.dp, named = false)
            Spacer(Modifier.width(14.dp))
            Text(contact.label, color = C.ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Box(Modifier
                .size(52.dp)
                .pointerInput(close.size) {
                    detectDragGestures(
                        onDragStart = { dragging = i; offset = 0f },
                        onDragEnd = { dragging = -1; offset = 0f },
                        onDragCancel = { dragging = -1; offset = 0f },
                    ) { change, amount ->
                        change.consume()
                        offset += amount.y
                        val from = dragging
                        if (from < 0) return@detectDragGestures
                        val to = (from + (offset / rowPx).roundToInt()).coerceIn(0, close.size - 1)
                        if (to != from) {
                            model.moveClose(from, to)
                            offset -= (to - from) * rowPx
                            dragging = to
                        }
                    }
                }, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.DragHandle, tr("Перетащить"), tint = C.faint)
            }
        }
    }
}

/** Nobody in the book yet: how people get in. */
@Composable
fun EmptyBook(model: AppModel) {
    val context = LocalContext.current
    Column(Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(C.field)
        .padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(tr("Добавь людей по их ссылке или поделись своим Love ID."), color = C.ink, fontSize = 16.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WideButton(tr("Добавить по ссылке"), { model.adding = "" }, modifier = Modifier.weight(1f))
            if (model.inviteUrl != null) {
                WideButton(tr("Мой Love ID"), { share(context, model.shareText) }, modifier = Modifier.weight(1f), filled = false)
            }
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.link, unfocusedBorderColor = Color.Transparent, focusedLabelColor = C.link,
    cursorColor = C.link, focusedContainerColor = C.field, unfocusedContainerColor = C.field,
    disabledContainerColor = C.field, disabledBorderColor = Color.Transparent)

/** A person's card: the name, the color, «Рядом», their Love ID, removing. */
@Composable
fun ContactEditor(model: AppModel, contact: Contact) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf(contact.label) }
    var color by remember { mutableIntStateOf(contact.color ?: 0) }
    var close by remember { mutableStateOf(contact.isClose) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val dismiss = { model.editing = null }
    androidx.activity.compose.BackHandler(onBack = dismiss)

    fun save() {
        busy = true
        scope.launch {
            try {
                model.updateContact(contact, label.trim(), color, close)
                dismiss()
            } catch (e: Exception) {
                problem = e.message ?: tr("Не получилось сохранить")
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(C.paper).imePadding()) {
        PageBar(contact.label, dismiss) {
            TextButton(::save, enabled = !busy && label.isNotBlank()) {
                Text(tr("Готово"), color = if (!busy && label.isNotBlank()) C.link else C.faint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Group(header = tr("Имя")) {
                OutlinedTextField(label, { label = it }, placeholder = { Text(tr("Например, Мама"), color = C.faint) }, singleLine = true,
                    colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth())
            }
            Group(header = tr("Цвет")) { ColorSwatches(color, { color = it }) }
            Group(footer = if (!contact.isClose && model.closeFull) tr("Рядом уже 12 человек: сначала убери кого-нибудь.") else tr("Кружок на главном экране.")) {
                SwitchRow(tr("Рядом"), close, { close = it }, enabled = contact.isClose || !model.closeFull, tint = C.link)
            }
            contact.inviteUrl?.let { link ->
                // Like one's own Love ID in «Аккаунт»: the link itself, not the name.
                Group {
                    GroupRow(onClick = { share(context, link) }) {
                        Column(Modifier.weight(1f)) {
                            Text("Love ID", color = C.ink, fontSize = 16.sp)
                            Text(link.removePrefix("https://"), color = C.muted, fontSize = 13.sp)
                        }
                        Icon(Icons.Filled.Share, tr("Love ID: {name}", "name" to contact.label), tint = C.link)
                    }
                }
            }
            problem?.let { Text(it, color = C.danger, fontSize = 15.sp) }
            Group {
                GroupRow(onClick = { confirmRemove = true }) { Text(tr("Удалить из книги"), color = C.danger, fontSize = 16.sp) }
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(onDismissRequest = { confirmRemove = false },
            title = { Text(tr("Удалить из книги?")) },
            text = { Text(tr("{name} не узнает об этом.", "name" to contact.label)) },
            confirmButton = {
                TextButton({
                    confirmRemove = false
                    busy = true
                    scope.launch {
                        try { model.remove(contact); dismiss() } catch (e: Exception) { problem = tr("Не получилось удалить") }
                        busy = false
                    }
                }) { Text(tr("Удалить"), color = C.danger) }
            },
            dismissButton = { TextButton({ confirmRemove = false }) { Text(tr("Отмена"), color = C.ink) } },
            containerColor = C.paper)
    }
}

/** «Добавить по ссылке»: the person's Love ID link, a name, a color. */
@Composable
fun AddContactScreen(model: AppModel, invite: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf(invite) }
    var label by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(model.freeColor()) }
    var close by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val dismiss = { model.adding = null }
    androidx.activity.compose.BackHandler(onBack = dismiss)

    val code = AppModel.inviteId(link)
    val own = code?.let { model.isOwn(it) } ?: false
    val ready = code != null && !own && label.isNotBlank()

    fun add() {
        val c = code ?: return
        busy = true
        problem = null
        scope.launch {
            try {
                model.addContact(c, label.trim(), color, close ?: !model.closeFull)
                dismiss()
            } catch (e: Exception) {
                problem = e.message ?: tr("Не получилось сохранить")
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(C.paper).imePadding()) {
        PageBar(tr("Добавить по ссылке"), dismiss) {
            TextButton(::add, enabled = ready && !busy) {
                Text(tr("Добавить"), color = if (ready && !busy) C.link else C.faint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Group(header = tr("Ссылка-приглашение"),
                footer = if (link.isNotEmpty() && code == null) tr("Не похоже на ссылку-приглашение") else null, footerColor = C.danger,
                background = if (own) C.dangerSoft else null) {
                OutlinedTextField(link, { link = it }, placeholder = { Text(tr("Вставь ссылку, которую тебе прислали"), color = C.faint) },
                    maxLines = 3, colors = fieldColors().copy(
                        focusedContainerColor = if (own) C.dangerSoft else C.field, unfocusedContainerColor = if (own) C.dangerSoft else C.field,
                        focusedTextColor = if (own) C.danger else C.ink, unfocusedTextColor = if (own) C.danger else C.ink),
                    trailingIcon = {
                        IconButton({
                            val clip = context.getSystemService(ClipboardManager::class.java)
                            clip?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { link = it }
                        }) { Icon(Icons.Filled.ContentPaste, tr("Вставить"), tint = C.link) }
                    },
                    modifier = Modifier.fillMaxWidth())
            }
            if (own) {
                Group(background = C.dangerSoft) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row {
                            Icon(Icons.Filled.ErrorOutline, null, tint = C.danger, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(tr("Это твоя собственная ссылка"), color = C.danger, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(tr("Отправь её тому, кто будет о тебе думать."), color = C.ink, fontSize = 15.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WideButton(tr("Поделиться"), { share(context, model.shareText) }, modifier = Modifier.weight(1f))
                            WideButton(tr("Очистить"), { link = "" }, modifier = Modifier.weight(1f), filled = false)
                        }
                    }
                }
            }
            Box(Modifier.alpha(if (own) 0.4f else 1f)) {
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Group(header = tr("Как назовёшь?")) {
                        OutlinedTextField(label, { label = it }, placeholder = { Text(tr("Например, Мама"), color = C.faint) },
                            singleLine = true, enabled = !own, colors = fieldColors(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (ready && !busy) add() }),
                            modifier = Modifier.fillMaxWidth())
                    }
                    Group(header = tr("Цвет")) { ColorSwatches(color, { color = it }, enabled = !own) }
                    Group(footer = if (model.closeFull) tr("Рядом уже 12 человек: сначала убери кого-нибудь.") else tr("Кружок на главном экране.")) {
                        SwitchRow(tr("Рядом"), close ?: !model.closeFull, { close = it }, enabled = !model.closeFull && !own, tint = C.link)
                    }
                }
            }
            problem?.let { Text(it, color = C.danger, fontSize = 15.sp) }
        }
    }
}
