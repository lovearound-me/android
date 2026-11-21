package me.lovearound.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.random.Random

/**
 * What the phone shows and does: the account, the book, the devices,
 * signals out and in. The logic is the iPhone app's (ios/Phone/AppModel.swift).
 */
class AppModel(private val context: Context, val demo: Demo?) {
    enum class Tab { Hearts, Book, Toys, Settings }
    enum class PauseLength { Hour, UntilMorning, UntilResumed }
    enum class SettingsPage { Account, Who, Glow, Look, Does }

    /** A line over the tab bar for a moment: what failed, or «Скопировано». */
    data class Toast(val text: String, val failed: Boolean = false)

    /** A signal just went: the edges glow in the person's color, emoji float up. */
    data class Outgoing(val color: Color, val stars: Boolean, val seed: Int, val text: String, val id: Long = System.nanoTime())

    companion object {
        /** The circle for «Кому-нибудь на свете» in `justSent`. */
        const val ANYONE_ID = "anyone"
        /** «Рядом» holds 12 at most: the server refuses a 13th. */
        const val MAX_CLOSE = 12
        /** A paused-until-resumed phone: far enough. */
        val FOREVER: Instant = Instant.parse("9999-01-01T00:00:00Z")
        const val SEND_GLOW_MS = 4200L

        /** The code of an invite link (or the bare code), as typed; null if it can't be one. */
        fun inviteId(input: String): String? {
            var text = input.trim()
            for (marker in listOf("/i/", "lovearound://add/", "needed://add/")) {
                val at = text.lastIndexOf(marker)
                if (at >= 0) text = text.substring(at + marker.length)
            }
            // The link ends at a space, a new line or a dot after it in a message.
            text = text.split(Regex("[/?#\\s]")).firstOrNull() ?: ""
            text = text.trim { !it.isLetterOrDigit() && it != '-' }.trim('-')
            // 10 characters of the server's alphabet, dashes anywhere. The server
            // never says whether a code is taken, so this is the only check a
            // typo gets: no 0, 1, I, L, O or U.
            val alphabet = "23456789ABCDEFGHJKMNPQRSTVWXYZ"
            val code = text.uppercase().filter { it != '-' }
            if (code.length != 10 || !code.all { it in alphabet }) return null
            return text
        }

        /** The six digits of lovearound.me/login/devices#482913 or lovearound://pair/482913. */
        fun pairCode(uri: Uri): String? {
            val candidate = if (uri.scheme == "https") {
                if (uri.path?.startsWith("/login/devices") != true) return null
                uri.fragment
            } else uri.lastPathSegment
            val code = candidate?.filter { it != ' ' } ?: return null
            return if (code.length == 6 && code.all { it in '0'..'9' }) code else null
        }
    }

    private val prefs = context.getSharedPreferences(if (demo != null) "demo" else "app", Context.MODE_PRIVATE)
    val scope: CoroutineScope = MainScope()
    val auth = Auth(context)
    val api = Api(auth)
    val stream = Stream(context, auth, scope)

    var signedIn by mutableStateOf(false); private set
    var email by mutableStateOf<String?>(null); private set
    var provider by mutableStateOf<String?>(null); private set
    var inviteUrl by mutableStateOf<String?>(null); private set
    var contacts by mutableStateOf<List<Contact>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    var devices by mutableStateOf<List<Device>>(emptyList()); private set
    var devicesLoaded by mutableStateOf(false); private set
    var devicesProblem by mutableStateOf<String?>(null); private set

    var tab by mutableStateOf(Tab.Hearts)
    /** The add page, opened by «+» or a Love ID link: the link to fill in. */
    var adding by mutableStateOf<String?>(null)
    /** The pairing page, opened by «Подключить по коду» or a device's QR. */
    var pairing by mutableStateOf<String?>(null)
    /** «＋» among the circles: who of the book goes into «Рядом». */
    var choosingClose by mutableStateOf(false)
    /** The person whose card is open. */
    var editing by mutableStateOf<Contact?>(null)
    /** The pages open in «Настройки». */
    var settingsPath by mutableStateOf<List<SettingsPage>>(emptyList())
    /** The device cards open in «Игрушки», by id, and a sub-page after a slash. */
    var toysPath by mutableStateOf<List<String>>(emptyList())

    var toast by mutableStateOf<Toast?>(null); private set
    var outgoing by mutableStateOf<Outgoing?>(null); private set
    var justSent by mutableStateOf<String?>(null); private set
    var signInProblem by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    /** Before Android asks about notifications: what they are for, once after signing in. */
    var notifyIntro by mutableStateOf(false); private set
    /** «Как это работает», once after the first sign-in on this phone. */
    var howItWorks by mutableStateOf(false); private set
    /** «С чего начать» on the home screen: from an empty book until the first heart. */
    var starting by mutableStateOf(prefs.getBoolean("starting", false)); private set
    var sharedLoveId by mutableStateOf(prefs.getBoolean("sharedLoveID", false)); private set
    /** The server has been out of reach for a few seconds: signals don't come. */
    var offline by mutableStateOf(false); private set

    var incoming by mutableStateOf(false); private set
    var signalSeed by mutableStateOf(0); private set

    /** The screen edges glow in it when a signal comes; stays on this phone. */
    var myColor by mutableStateOf(prefs.getInt("myColor", 1))
        private set
    var receiveHere by mutableStateOf(prefs.getBoolean("receiveHere", true)); private set
    var acceptAnyone by mutableStateOf(prefs.getBoolean("acceptAnyone", true)); private set
    var anyoneSentAt by mutableStateOf(prefs.getLong("anyoneSentAt", 0).takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }); private set
    var pausedUntil by mutableStateOf(prefs.getLong("pausedUntil", 0).takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }); private set
    var quietHours by mutableStateOf(prefs.getString("quietHours", null)?.let {
        try { json.decodeFromString(QuietHours.serializer(), it) } catch (e: Exception) { null }
    }); private set
    var notifyLook by mutableStateOf(NotifyLook.current(context)); private set
    var notifyWords by mutableStateOf(NotifyLook.withWords(context)); private set
    var vibrates by mutableStateOf(NotifyLook.vibrates(context)); private set
    /** Android's notification permission, as last seen. */
    var notificationsAllowed by mutableStateOf(demo != null || Push.allowed(context)); private set

    /** When each contact last got a signal from here: the book puts them first. */
    private var lastSent: Map<String, Long> = prefs.getString("lastSent", null)?.let {
        try { json.decodeFromString(MapSerializer(String.serializer(), Long.serializer()), it) } catch (e: Exception) { null }
    } ?: emptyMap()
        set(value) { field = value; prefs.edit().putString("lastSent", json.encodeToString(MapSerializer(String.serializer(), Long.serializer()), value)).apply() }

    private var pendingAdd: String? = null
    private var pendingPair: String? = null
    private var toastJob: Job? = null
    private var outgoingJob: Job? = null
    private var goingOffline: Job? = null
    private var saving: Job? = null
    private var reorderSave: Job? = null
    private var cacheSave: Job? = null
    private var pushSync: Job? = null
    private var pushSent: String? = null
    private var foreground = false

    init {
        if (demo != null) prefs.edit().clear().apply()
        stream.anyone = acceptAnyone
        stream.onSignal = { receive() }
        stream.onAuthLost = { signOut() }
        stream.onOnline = { connectionChanged(it) }
        stream.onSync = {
            scope.launch {
                // Not halfway through saving a new order: that would show it half-applied.
                saving?.join()
                reload()
            }
        }
        if (demo != null) {
            demo.apply(this)
        } else {
            auth.session?.let {
                signedIn = true
                email = it.email
                drawLastSeen()
            }
        }
    }

    // In front and behind

    /** The app came to the front: no socket is kept behind, so connect again. */
    fun becameActive() {
        foreground = true
        if (demo != null || !signedIn) return
        pausedUntil?.let { if (!it.isAfter(Instant.now())) setPaused(null) }
        if (receiveHere) stream.start()
        scope.launch { reload() }
        refreshPush()
    }

    fun wentToBackground() {
        foreground = false
        if (demo != null) return
        stream.stop()
        goingOffline?.cancel()
        offline = false
    }

    val inFront: Boolean get() = foreground

    suspend fun reload() {
        if (demo != null || !signedIn) return
        try {
            inviteUrl = api.me().inviteUrl
            contacts = api.contacts()
            if (contacts.isEmpty() && !starting) markStarting(true)
            loaded = true
            keepForNextStart()
            provider = auth.provider
        } catch (e: AuthError) {
            if (e.endsSession) signOut()
            else if (!loaded) show(tr("Нет связи с сервером. Попробуем ещё раз чуть позже."), failed = true)
        } catch (e: Exception) {
            if (!loaded) show(tr("Нет связи с сервером. Попробуем ещё раз чуть позже."), failed = true)
        }
    }

    private fun connectionChanged(up: Boolean) {
        goingOffline?.cancel()
        if (up) offline = false
        else goingOffline = scope.launch { delay(5000); offline = true }
    }

    // Signals out

    fun send(contact: Contact) {
        lastSent = lastSent + (contact.id to System.currentTimeMillis())
        if (starting) markStarting(false)
        sent(contact.id, Palette.color(contact.color), tr("{name} — улетело ♥", "name" to contact.label)) { it.signal(contact.id) }
    }

    /**
     * One random person who takes them, once an hour. The server spends the
     * hour on the try even with nobody online, so «улетело» is all the
     * sender ever learns.
     */
    fun sendToAnyone(star: Color) {
        if (anyoneWait() != null) return
        markAnyoneSent(Instant.now())
        sent(ANYONE_ID, star, tr("Кому-нибудь на свете — улетело ✨")) { it.signalAnyone() }
    }

    private fun sent(id: String, color: Color, text: String, call: suspend (Api) -> Unit) {
        buzz(30)
        justSent = id
        glow(Outgoing(color, stars = id == ANYONE_ID, seed = Random.nextInt(1000), text = text))
        if (demo != null) return
        scope.launch {
            try {
                call(api)
            } catch (e: AuthError) {
                justSent = null
                if (e.endsSession) signOut() else failedToSend(id)
            } catch (e: ApiError) {
                if (e.status == 429 && id == ANYONE_ID) {
                    // Sent from another device this hour, at a time not known here.
                    justSent = null
                    show(tr("Кому-нибудь на свете уже улетело в этот час."))
                } else failedToSend(id)
            } catch (e: Exception) {
                failedToSend(id)
            }
        }
    }

    private fun failedToSend(id: String) {
        justSent = null
        if (id == ANYONE_ID) markAnyoneSent(null)
        buzz(200)
        show(tr("Не улетело, проверь интернет"), failed = true)
    }

    /** Whole minutes until the next signal to anyone, or null if it can go now. */
    fun anyoneWait(now: Instant = Instant.now()): Int? {
        val at = anyoneSentAt ?: return null
        val left = at.plusSeconds(3600).toEpochMilli() - now.toEpochMilli()
        return if (left > 0) ((left + 59_999) / 60_000).toInt() else null
    }

    fun glow(out: Outgoing) {
        outgoing = out
        outgoingJob?.cancel()
        outgoingJob = scope.launch {
            delay(SEND_GLOW_MS)
            outgoing = null
            justSent = null
        }
    }

    fun show(text: String, failed: Boolean = false) {
        toast = Toast(text, failed)
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(if (failed) 4000 else 2500)
            toast = null
        }
    }

    // Signals in

    /** From the stream, or a notification while the app is in front: the same heart. */
    fun receive() {
        // Not shown, and not kept for later: a signal is about the moment.
        if (!receiveHere || isPaused || isQuiet || incoming) return
        showSignal()
    }

    /** A heart's notification was tapped: the app opens glowing. */
    fun tapped() {
        if (!signedIn || incoming) return
        scope.launch { delay(400); showSignal() }
    }

    /** The glowing edges, the hearts, a buzz in the hand («Вибрация»). */
    fun showSignal() {
        signalSeed = Random.nextInt(1000)
        incoming = true
        if (vibrates) buzzPattern()
        val seed = signalSeed
        scope.launch {
            delay(5500)
            if (signalSeed == seed) incoming = false
        }
    }

    fun dismissSignal() { incoming = false }

    private fun buzz(ms: Long) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun buzzPattern() {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 120, 60), -1))
    }

    // Pause

    val isPaused: Boolean get() = pausedUntil?.isAfter(Instant.now()) == true

    val pauseText: String
        get() {
            val until = pausedUntil ?: return ""
            if (!isPaused) return ""
            if (until == FOREVER) return tr("Пауза, пока не снимешь")
            val local = ZonedDateTime.ofInstant(until, ZoneId.systemDefault())
            val time = local.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Strings.locale))
            return if (local.toLocalDate() == LocalDate.now()) tr("Пауза до {time}", "time" to time)
            else tr("Пауза до завтра, {time}", "time" to time)
        }

    fun pause(length: PauseLength) {
        setPaused(when (length) {
            PauseLength.Hour -> Instant.now().plusSeconds(3600)
            PauseLength.UntilMorning -> {
                val zone = ZoneId.systemDefault()
                val today8 = LocalDate.now().atTime(LocalTime.of(8, 0)).atZone(zone)
                (if (today8.toInstant().isAfter(Instant.now())) today8 else today8.plusDays(1)).toInstant()
            }
            PauseLength.UntilResumed -> FOREVER
        })
    }

    fun resume() = setPaused(null)

    private fun setPaused(until: Instant?) {
        pausedUntil = until
        prefs.edit().putLong("pausedUntil", until?.toEpochMilli() ?: 0).apply()
        syncPush()
    }

    val isQuiet: Boolean get() = quietHours?.contains() == true

    // Settings that stay on this phone

    fun updateMyColor(index: Int) {
        myColor = index
        prefs.edit().putInt("myColor", index).apply()
    }

    fun updateReceiveHere(on: Boolean) {
        receiveHere = on
        prefs.edit().putBoolean("receiveHere", on).apply()
        if (demo != null || !signedIn) return
        if (on) stream.start() else {
            stream.stop()
            goingOffline?.cancel()
            offline = false
        }
        syncPush()
    }

    fun updateAcceptAnyone(on: Boolean) {
        acceptAnyone = on
        prefs.edit().putBoolean("acceptAnyone", on).apply()
        stream.anyone = on
        stream.restart()
        syncPush()
    }

    fun updateQuietHours(hours: QuietHours?) {
        quietHours = hours
        prefs.edit().putString("quietHours", hours?.let { json.encodeToString(QuietHours.serializer(), it) }).apply()
        syncPush()
    }

    fun updateNotifyLook(look: NotifyLook) {
        notifyLook = look
        if (demo == null) NotifyLook.setCurrent(context, look)
    }

    fun updateNotifyWords(on: Boolean) {
        notifyWords = on
        if (demo == null) NotifyLook.setWithWords(context, on)
    }

    fun updateVibrates(on: Boolean) {
        vibrates = on
        if (demo == null) NotifyLook.setVibrates(context, on)
        if (on) buzzPattern()
    }

    private fun markAnyoneSent(at: Instant?) {
        anyoneSentAt = at
        prefs.edit().putLong("anyoneSentAt", at?.toEpochMilli() ?: 0).apply()
    }

    private fun markStarting(on: Boolean) {
        starting = on
        prefs.edit().putBoolean("starting", on).apply()
    }

    // Notifications

    /** What Android allows, then the token; the intro screen comes first, once. */
    fun refreshPush() {
        if (demo != null || !signedIn) return
        notificationsAllowed = Push.allowed(context)
        if (!notificationsAllowed && receiveHere && !prefs.getBoolean("notifyIntroSeen", false) && !howItWorks) {
            notifyIntro = true
        }
        Push.token { syncPush() }
    }

    /** After Android's own question was answered. */
    fun notificationsAnswered() {
        notificationsAllowed = Push.allowed(context)
        syncPush()
    }

    fun closeNotifyIntro() {
        if (demo == null) prefs.edit().putBoolean("notifyIntroSeen", true).apply()
        notifyIntro = false
    }

    fun closeHowItWorks() {
        if (demo == null) prefs.edit().putBoolean("howItWorksSeen", true).apply()
        howItWorks = false
        refreshPush()
    }

    /** «Мой Love ID» of «С чего начать» opened the share sheet. */
    fun openedShare() {
        if (!sharedLoveId) {
            sharedLoveId = true
            prefs.edit().putBoolean("sharedLoveID", true).apply()
        }
    }

    /** «С чего начать» is on the home screen. */
    val showsStart: Boolean get() = loaded && (contacts.isEmpty() || starting)

    /**
     * Tells the server where and when this phone takes notifications, or that
     * it takes none. After a moment, so a turned wheel sends once.
     */
    fun syncPush() {
        if (demo != null || !signedIn) return
        val token = Push.lastToken(context) ?: return
        pushSync?.cancel()
        pushSync = scope.launch {
            delay(600)
            val state = pushState(token)
            if (state == pushSent) return@launch
            try {
                val old = prefs.getString("pushToken", null)
                if (old != null && old != token) runCatching { api.deletePush(old) }
                prefs.edit().putString("pushToken", token).apply()
                if (receiveHere) api.putPush(token, acceptAnyone, if (isPaused) pausedUntil else null, quietHours)
                else api.deletePush(token)
                pushSent = state
            } catch (e: Exception) {
                // Tried again when the app next comes to the front.
            }
        }
    }

    private fun pushState(token: String): String =
        if (!receiveHere) "$token off" else "$token $acceptAnyone ${if (isPaused) pausedUntil else ""} $quietHours"

    // The book

    /** The circles, in the order set by hand. */
    val closeContacts: List<Contact> get() = contacts.filter { it.isClose }

    /** Everyone else, the most recently signalled first, then by name. */
    val otherContacts: List<Contact>
        get() {
            val collator = Collator.getInstance(Strings.locale)
            return contacts.filter { !it.isClose }.sortedWith { a, b ->
                val ta = lastSent[a.id] ?: 0
                val tb = lastSent[b.id] ?: 0
                if (ta != tb) tb.compareTo(ta) else collator.compare(a.label, b.label)
            }
        }

    /** The home screen's circles: the close ones, or, with nobody close yet, the first of the book. */
    val homeCircles: List<Contact>
        get() = closeContacts.ifEmpty { otherContacts.take(9) }

    val closeFull: Boolean get() = closeContacts.size >= MAX_CLOSE

    /** For a new contact, as the server picks: a color nobody in the book has yet. */
    fun freeColor(): Int {
        val used = contacts.mapNotNull { it.color }.toSet()
        val free = Palette.all.indices.filter { it !in used }
        return free.ifEmpty { Palette.all.indices.toList() }.random()
    }

    suspend fun addContact(code: String, label: String, color: Int, close: Boolean) {
        if (demo != null) {
            contacts = contacts + Contact(System.nanoTime().toString(), label, contacts.size, close, color, "https://lovearound.me/i/$code")
            return
        }
        val contact = api.addContact(code, label, close, color)
        contacts = contacts + contact.copy(color = contact.color ?: color)
    }

    suspend fun updateContact(contact: Contact, label: String, color: Int, close: Boolean) {
        val colorChanged = color != (contact.color ?: 0)
        if (demo == null && (label != contact.label || close != contact.isClose || colorChanged)) {
            api.updateContact(contact.id,
                label = label.takeIf { it != contact.label },
                close = close.takeIf { it != contact.isClose },
                color = color.takeIf { colorChanged })
        }
        contacts = contacts.map { if (it.id == contact.id) it.copy(label = label, close = close, color = color) else it }
    }

    suspend fun remove(contact: Contact) {
        if (demo == null) api.deleteContact(contact.id)
        contacts = contacts.filter { it.id != contact.id }
        lastSent = lastSent - contact.id
    }

    /** Into «Рядом» or out of it: shown at once, saved behind. */
    fun setClose(contact: Contact, close: Boolean) {
        if (close && closeFull) {
            show(tr("Рядом уже 12 человек: сначала убери кого-нибудь."), failed = true)
            return
        }
        contacts = contacts.map { if (it.id == contact.id) it.copy(close = close) else it }
        if (demo != null) return
        val previous = saving
        saving = scope.launch {
            previous?.join()
            try {
                api.updateContact(contact.id, close = close)
            } catch (e: Exception) {
                show(if ((e as? ApiError)?.tooManyClose == true) e.message else tr("Не получилось сохранить"), failed = true)
                reload()
            }
        }
    }

    /** «＋» among the circles. With nobody in the book: the add page at once. */
    fun addToClose() {
        if (contacts.isEmpty()) adding = "" else choosingClose = true
    }

    /** A drag among the circles in the book: `from` and `to` are places among the close ones. */
    fun moveClose(from: Int, to: Int) {
        val close = closeContacts.toMutableList()
        if (from !in close.indices || to !in close.indices || from == to) return
        close.add(to, close.removeAt(from))
        // The close ones keep their slots in the whole list, in the new order.
        val list = contacts.toMutableList()
        val slots = list.indices.filter { list[it].isClose }
        for ((slot, person) in slots.zip(close)) list[slot] = person
        contacts = list
        reorderSave?.cancel()
        reorderSave = scope.launch {
            // A moment after the last change, so several moves are one save.
            delay(700)
            savePositions()
        }
    }

    private fun savePositions() {
        val changed = contacts.indices.filter { contacts[it].position != it }
        contacts = contacts.mapIndexed { i, c -> if (c.position != i) c.copy(position = i) else c }
        val updates = changed.map { contacts[it].id to it }
        if (updates.isEmpty() || demo != null) return
        val previous = saving
        saving = scope.launch {
            previous?.join()
            try {
                for ((id, position) in updates) api.updateContact(id, position = position)
            } catch (e: Exception) {
                show(tr("Не получилось сохранить порядок"), failed = true)
                reload()
            }
        }
    }

    /** The person's own Love ID, which can't be added to their own book. */
    fun isOwn(code: String): Boolean {
        val own = inviteUrl?.let { inviteId(it) } ?: return false
        fun bare(s: String) = s.uppercase().filter { it != '-' }
        return bare(own) == bare(code)
    }

    val shareText: String
        get() = tr("Добавь меня по этой ссылке и нажимай сердечко, когда думаешь обо мне ♥") + "\n" + (inviteUrl ?: "")

    // Devices

    suspend fun loadDevices() {
        if (demo != null) return
        try {
            devices = api.devices()
            devicesLoaded = true
            keepForNextStart()
            devicesProblem = null
        } catch (e: AuthError) {
            if (e.endsSession) signOut() else devicesProblem = tr("Не получилось загрузить устройства")
        } catch (e: Exception) {
            devicesProblem = tr("Не получилось загрузить устройства")
        }
    }

    /** A board of one's own: its token comes back once, to paste into the firmware. */
    suspend fun addBoard(name: String, capabilities: List<String>): String {
        if (demo != null) {
            devices = devices + Device(System.nanoTime().toString(), "custom", name, capabilities)
            return "dev_demo_7Hq2kLx9WbN4sP0aZr"
        }
        val (device, token) = api.addDevice(name, capabilities)
        devices = devices + device
        return token
    }

    suspend fun removeDevice(device: Device) {
        if (demo == null) {
            try { api.deleteDevice(device.id) } catch (e: Exception) {
                show(tr("Не получилось отключить, попробуй ещё раз"), failed = true)
                return
            }
        }
        devices = devices.filter { it.id != device.id }
        toysPath = toysPath.filter { !it.startsWith(device.id) }
    }

    /** Saves a change to a device and shows it as the server now has it; false if it didn't take. */
    suspend fun changeDevice(id: String, change: DeviceChange): Boolean {
        val before = devices.firstOrNull { it.id == id } ?: return false
        devices = devices.map { if (it.id == id) change.applied(it) else it }
        if (demo != null) return true
        return try {
            val updated = api.updateDevice(id, change.body())
            devices = devices.map { if (it.id == id) updated else it }
            true
        } catch (e: AuthError) {
            if (e.endsSession) signOut()
            false
        } catch (e: Exception) {
            devices = devices.map { if (it.id == id) before else it }
            show(tr("Не получилось сохранить"), failed = true)
            false
        }
    }

    suspend fun newDeviceToken(device: Device): String =
        if (demo != null) "dev_demo_7Hq2kLx9WbN4sP0aZr4mT8vW3cJ6yQ1n" else api.newDeviceToken(device.id)

    suspend fun findPairing(code: String): PairingCard =
        if (demo != null) PairingCard("Galaxy Watch", "watch", listOf("receive", "send")) else api.findPairing(code)

    suspend fun confirmPairing(code: String) {
        if (demo == null) api.confirmPairing(code)
        loadDevices()
    }

    // Links

    fun handle(uri: Uri) {
        if (uri.scheme == "https" && uri.host == "lovearound.me") {
            val code = pairCode(uri)
            if (code != null) openPairing(code)
            else if (uri.path?.contains("/i/") == true) inviteId(uri.toString())?.let { openAdding(it) }
            return
        }
        if (uri.scheme != Config.URL_SCHEME) return
        when (uri.host) {
            "pair" -> pairCode(uri)?.let { openPairing(it) }
            "add" -> inviteId(uri.toString())?.let { openAdding(it) }
            "signin" -> finishSiteSignIn(uri)
        }
    }

    private fun openAdding(invite: String) {
        if (!signedIn) { pendingAdd = invite; return }
        // One's own Love ID, opened from a chat it was sent to: say what it is instead.
        if (isOwn(invite)) {
            show(tr("Это твоя ссылка: по ней тебя добавляют другие."))
            return
        }
        tab = Tab.Book
        pairing = null
        adding = invite
    }

    private fun openPairing(code: String) {
        if (!signedIn) { pendingPair = code; return }
        tab = Tab.Toys
        adding = null
        pairing = code
    }

    // Account

    /** «Войти через Google»: Google's account chooser over the app. */
    suspend fun signInWithGoogle(idToken: String) {
        signInProblem = null
        busy = true
        try {
            auth.signInWithGoogle(idToken)
            didSignIn()
        } catch (e: AuthError) {
            signInProblem = e.message
        } catch (e: Exception) {
            signInProblem = tr("Не получилось войти. Попробуй ещё раз.")
        } finally {
            busy = false
        }
    }

    /** «Войти по почте»: signs in, or makes the account. What went wrong, or null once in. */
    suspend fun signInWithEmail(email: String, password: String): String? {
        busy = true
        return try {
            auth.signInWithEmail(email, password)
            didSignIn()
            null
        } catch (e: Exception) {
            e.message ?: tr("Не получилось войти. Попробуй ещё раз.")
        } finally {
            busy = false
        }
    }

    /** «Забыли пароль?»: what to tell the person. */
    suspend fun sendPasswordReset(email: String): String = try {
        auth.sendPasswordReset(email)
        tr("Письмо для нового пароля отправлено")
    } catch (e: Exception) {
        e.message ?: tr("Не получилось")
    }

    /** «Войти через Apple»: the site's sign-in page in a browser tab. */
    fun siteSignInUri(): Uri = SiteSignIn.start(context)

    private fun finishSiteSignIn(uri: Uri) {
        val opened = SiteSignIn.finish(context, uri)
        if (opened == null) {
            signInProblem = tr("Не получилось войти. Попробуй ещё раз.")
            return
        }
        try {
            auth.adopt(opened.uid, opened.email, opened.refreshToken)
            didSignIn()
        } catch (e: Exception) {
            signInProblem = tr("Не получилось войти. Попробуй ещё раз.")
        }
    }

    /** Debug builds: a test account's ID token, for an hour. */
    fun borrow(token: String) {
        auth.borrow(token)
        didSignIn()
    }

    private fun didSignIn() {
        signInProblem = null
        signedIn = true
        email = auth.session?.email
        provider = auth.provider
        tab = Tab.Hearts
        if (!prefs.getBoolean("howItWorksSeen", false)) howItWorks = true
        if (receiveHere && foreground) stream.start()
        scope.launch { reload() }
        refreshPush()
        pendingAdd?.let { pendingAdd = null; openAdding(it); return }
        pendingPair?.let { pendingPair = null; openPairing(it) }
    }

    /** «Выйти»: this phone stops taking the account's notifications first, while it still can say so. */
    suspend fun signOutHere() {
        if (demo == null) {
            Push.lastToken(context)?.let { token ->
                pushSync?.cancel()
                runCatching { api.deletePush(token) }
            }
        }
        signOut()
    }

    /** Also when the session is lost: the phone itself stops taking notifications. */
    fun signOut() {
        stream.stop()
        pushSync?.cancel()
        pushSent = null
        cacheSave?.cancel()
        if (demo == null) {
            Push.forget(context)
            cacheFile.delete()
            auth.signOut()
        }
        goingOffline?.cancel()
        offline = false
        signedIn = false
        email = null
        provider = null
        inviteUrl = null
        contacts = emptyList()
        loaded = false
        devices = emptyList()
        devicesLoaded = false
        toysPath = emptyList()
        adding = null
        pairing = null
        editing = null
        choosingClose = false
        outgoing = null
        incoming = false
        notifyIntro = false
        howItWorks = false
        markStarting(false)
        sharedLoveId = false
        prefs.edit().putBoolean("sharedLoveID", false).apply()
        settingsPath = emptyList()
        tab = Tab.Hearts
        // Not carried over to whoever signs in here next.
        lastSent = emptyMap()
        setPaused(null)
        markAnyoneSent(null)
    }

    suspend fun deleteAccount() {
        if (demo == null) api.deleteMe()
        signOut()
    }

    val version: String get() = BuildConfig.VERSION_NAME

    // What was there last time

    /**
     * The book, the devices and the Love ID as the server last gave them:
     * drawn at once on the next start, then replaced by the server's.
     */
    @Serializable
    private data class LastSeen(val invite: String? = null, val contacts: List<Contact>, val devices: List<Device>? = null)

    private val cacheFile: File get() = File(context.filesDir, "last-seen.json")

    private fun drawLastSeen() {
        val last = try { json.decodeFromString(LastSeen.serializer(), cacheFile.readText()) } catch (e: Exception) { return }
        inviteUrl = last.invite
        contacts = last.contacts
        loaded = true
        last.devices?.let { devices = it; devicesLoaded = true }
    }

    private fun keepForNextStart() {
        if (demo != null || !signedIn || !loaded) return
        cacheSave?.cancel()
        val last = LastSeen(inviteUrl, contacts, if (devicesLoaded) devices else null)
        cacheSave = scope.launch {
            delay(500)
            runCatching { cacheFile.writeText(json.encodeToString(LastSeen.serializer(), last)) }
        }
    }

    // Demo

    fun demoSet(
        signedIn: Boolean, contacts: List<Contact>, devices: List<Device> = emptyList(), tab: Tab = Tab.Hearts,
        anyoneSentAt: Instant? = null, pausedUntil: Instant? = null, receiveHere: Boolean = true,
        quietHours: QuietHours? = null, starting: Boolean = false, sharedLoveId: Boolean = false,
    ) {
        this.signedIn = signedIn
        email = if (signedIn) "anna@example.com" else null
        provider = if (signedIn) "google.com" else null
        inviteUrl = if (signedIn) "https://lovearound.me/i/GSSV7-A33HN" else null
        this.contacts = contacts
        loaded = signedIn
        this.devices = devices
        devicesLoaded = signedIn
        this.tab = tab
        this.anyoneSentAt = anyoneSentAt
        this.receiveHere = receiveHere
        this.pausedUntil = pausedUntil
        this.quietHours = quietHours
        this.starting = starting || (signedIn && contacts.isEmpty())
        this.sharedLoveId = sharedLoveId
    }

    fun demoShowNotifyIntro() { notifyIntro = true }
    fun demoShowHowItWorks() { howItWorks = true }
}

/** What changes on a device; null fields stay as they are. */
data class DeviceChange(
    val name: String? = null,
    val capabilities: List<String>? = null,
    val acceptAnyone: Boolean? = null,
    /** Set: `quietHours` (null turns them off). */
    val setsQuietHours: Boolean = false,
    val quietHours: QuietHours? = null,
) {
    fun body(): JsonObject = buildJsonObject {
        name?.let { put("name", it) }
        capabilities?.let { put("capabilities", kotlinx.serialization.json.JsonArray(it.map { c -> JsonPrimitive(c) })) }
        acceptAnyone?.let { put("accept_anyone", it) }
        if (setsQuietHours) put("quiet_hours", quietHours?.let { json.encodeToJsonElement(QuietHours.serializer(), it) } ?: JsonNull)
    }

    fun applied(device: Device): Device = device.copy(
        name = name ?: device.name,
        capabilities = capabilities ?: device.capabilities,
        acceptAnyone = acceptAnyone ?: device.acceptAnyone,
        quietHours = if (setsQuietHours) quietHours else device.quietHours,
    )
}
