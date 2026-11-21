package me.lovearound.android.wear

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.lovearound.android.ApiError
import me.lovearound.android.Contact
import me.lovearound.android.WidgetData
import me.lovearound.android.tr
import kotlin.random.Random

/** What a bubble, circle or line sends to: «Кому-нибудь на свете» or a person. */
sealed class Target {
    data object Anyone : Target()
    data class Person(val contact: Contact) : Target()

    val id: String get() = when (this) { Anyone -> WatchModel.ANYONE; is Person -> contact.id }

    /** The whole name, as the held finger shows it. */
    val label: String get() = when (this) { Anyone -> tr("Кому-нибудь на свете"); is Person -> contact.label }

    /** Under a circle, where there is room for a word or two. */
    val short: String get() = when (this) { Anyone -> tr("Кому-нибудь"); is Person -> contact.label }

    val initial: String get() = when (this) {
        Anyone -> ""
        is Person -> contact.label.trim().take(1).uppercase()
    }
}

/**
 * Everything the watch shows, and the few things it does: sign in by a
 * code, load the people, send a heart, play an incoming one. The iPhone
 * repo's Watch/AppModel.swift, for Wear OS.
 */
class WatchModel(private val context: Context, val demo: WatchDemo?) {
    enum class Screen { SignIn, Code, Connected, Main }

    companion object {
        /** The bubble for «Кому-нибудь на свете» in `justSent`. */
        const val ANYONE = "anyone"
    }

    val scope: CoroutineScope = MainScope()

    var screen by mutableStateOf(Screen.SignIn)
    var session by mutableStateOf<Session?>(null); private set
    /** A line under the sign-in button: why the last try failed, or that the watch was disconnected. */
    var notice by mutableStateOf<String?>(null); private set

    // Code sign-in
    var pairing by mutableStateOf<WatchApi.PairingCode?>(null); private set
    var pairingExpired by mutableStateOf(false); private set
    var pairingError by mutableStateOf<String?>(null); private set
    private var pairingJob: Job? = null

    // The people
    var contacts by mutableStateOf<List<Contact>>(emptyList()); private set
    var loaded by mutableStateOf(false); private set
    /** Sending from this watch is switched off on the phone: no people to show. */
    var sendingOff by mutableStateOf(false); private set
    /** The circle that just sent, filled with a heart for a moment. */
    var justSent by mutableStateOf<String?>(null); private set
    var caption by mutableStateOf<String?>(null); private set
    /** The one a finger holds: its whole name over the screen. */
    var peeking by mutableStateOf<Target?>(null); private set
    private var holdCancelled = false
    private var sentGeneration = 0

    var layout by mutableStateOf(if (demo == null) WatchSettings.layout(context) else WatchLayout.Hive)
        private set
    var vibrates by mutableStateOf(demo != null || WatchSettings.vibrates(context))
        private set
    var myColor by mutableIntStateOf(if (demo == null) WatchSettings.myColor(context) else 1)
        private set

    // An incoming heart
    var incoming by mutableStateOf(false); private set
    /** Picks which emoji float up this time. */
    var signalSeed by mutableIntStateOf(0); private set

    // Receiving on this watch
    /** Its «Получать сердечки», as the server has it; null until known. */
    var receiving by mutableStateOf<Boolean?>(null); private set
    var notificationsOff by mutableStateOf(false); private set
    /** Why the last switch didn't take. */
    var receiveProblem by mutableStateOf<String?>(null); private set
    private var capabilities: List<String> = emptyList()

    /** The app is on screen: a heart glows instead of a notification. */
    var inFront = false

    init {
        val saved = if (demo == null) Session.load(context) else null
        session = saved
        screen = if (saved != null) Screen.Main else Screen.SignIn
        demo?.apply(this)
        if (demo == null) WidgetData.setMyColor(context, myColor)
        if (demo == null && saved != null) scope.launch { startReceiving() }
    }

    /** Close people first, each group in the owner's order. */
    val circles: List<Contact>
        get() = contacts.sortedWith(compareBy<Contact>({ !it.isClose }, { it.position }))

    /** What the main screen shows: «Кому-нибудь на свете», then the people. */
    val targets: List<Target> get() = listOf(Target.Anyone) + circles.map { Target.Person(it) }

    // Sign in with a code

    fun startCode() {
        screen = Screen.Code
        pairing = null
        pairingExpired = false
        pairingError = null
        pairingJob?.cancel()
        if (demo != null) return
        pairingJob = scope.launch { runPairing() }
    }

    fun cancelCode() {
        pairingJob?.cancel()
        pairingJob = null
        pairing = null
        screen = Screen.SignIn
    }

    private suspend fun runPairing() {
        val code = try {
            WatchApi.startPairing()
        } catch (e: ApiError) {
            pairingError = if (e.status == 429) tr("Слишком много кодов. Попробуй через час.") else tr("Нет связи, проверь интернет")
            return
        } catch (e: Exception) {
            pairingError = tr("Нет связи, проверь интернет")
            return
        }
        pairing = code
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
        val pause = maxOf(code.pollEvery, 2) * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(pause)
            // A failed poll (no network for a moment) just waits for the next one.
            val state = try { WatchApi.pollPairing(code.secret) } catch (e: Exception) { continue }
            when (state) {
                WatchApi.PairingState.Waiting -> continue
                is WatchApi.PairingState.Paired -> { signedIn(state.session); return }
                WatchApi.PairingState.Gone -> { pairingExpired = true; return }
            }
        }
        pairingExpired = true
    }

    private fun signedIn(session: Session) {
        session.save(context)
        this.session = session
        notice = null
        pairing = null
        scope.launch { startReceiving() }
        screen = Screen.Connected
        scope.launch {
            delay(1600)
            if (screen == Screen.Connected) screen = Screen.Main
        }
    }

    /** «Выйти»: the watch leaves the account's devices first, while its token can still say so. */
    fun signOutHere() {
        val session = session
        scope.launch {
            if (demo == null && session != null) {
                try { WatchApi(session.token).removeSelf() } catch (e: Exception) {}
            }
            signOut()
        }
    }

    fun signOut(notice: String? = null) {
        // A demo's «Выйти» only shows the sign-in screen.
        if (demo == null) {
            Session.clear(context)
            WatchPush.forget(context)
            WidgetData.clear(context)
        }
        session = null
        contacts = emptyList()
        loaded = false
        sendingOff = false
        receiving = null
        receiveProblem = null
        capabilities = emptyList()
        this.notice = notice
        screen = Screen.SignIn
    }

    // Receiving

    /** After sign-in and at each launch: FCM's token for this watch to the server, and the switch's state. */
    suspend fun startReceiving() {
        if (demo != null || session == null) return
        notificationsOff = !WatchPush.allowed(context)
        WatchPush.token(context) { registerPush(it) }
        loadReceiving()
    }

    /** Called again when the person answers Android's question about notifications. */
    fun notificationsAnswered() {
        notificationsOff = !WatchPush.allowed(context)
    }

    fun registerPush(fcmToken: String) {
        val session = session ?: return
        if (demo != null) return
        scope.launch {
            try { WatchApi(session.token).registerPush(fcmToken, silent = !vibrates) } catch (e: Exception) {}
        }
    }

    private suspend fun loadReceiving() {
        val session = session ?: return
        val me = try { WatchApi(session.token).me() } catch (e: Exception) { return }
        capabilities = me.capabilities
        receiving = me.receives
    }

    /** «Получать сердечки»: the same as «Принимает» in the watch's card on the phone. */
    fun setReceiving(on: Boolean) {
        if (receiving == on) return
        receiveProblem = null
        val before = receiving
        receiving = on
        val session = session ?: return
        if (demo != null) return
        val wanted = capabilities.filter { it != "receive" } + if (on) listOf("receive") else emptyList()
        scope.launch {
            try {
                val me = WatchApi(session.token).setCapabilities(wanted)
                capabilities = me.capabilities
                receiving = me.receives
                if (on && WatchPush.lastToken(context) == null) startReceiving()
            } catch (e: ApiError) {
                if (e.sessionGone) {
                    signOut(tr("Часы отключены в «Устройствах». Войди снова."))
                    return@launch
                }
                receiving = before
                receiveProblem = when {
                    e.status == 400 -> tr("Эти часы вошли только для отправки. Выйди и войди снова.")
                    e is ApiError.Network -> tr("Нет связи, проверь интернет")
                    else -> tr("Не получилось. Попробуй ещё раз.")
                }
            }
        }
    }

    /** «Вибрация»: off, hearts come quietly; the server is told, to send them so. */
    fun updateVibrates(on: Boolean) {
        if (vibrates == on) return
        vibrates = on
        if (demo != null) return
        WatchSettings.setVibrates(context, on)
        WatchPush.lastToken(context)?.let { registerPush(it) }
    }

    fun updateLayout(layout: WatchLayout) {
        this.layout = layout
        if (demo == null) WatchSettings.setLayout(context, layout)
    }

    fun updateColor(index: Int) {
        myColor = index
        if (demo == null) {
            WatchSettings.setMyColor(context, index)
            WidgetData.setMyColor(context, index)
        }
    }

    // An incoming heart

    /** The wrist's tap, the glowing edges and the emoji. */
    fun showSignal(tapping: Boolean = true) {
        signalSeed = Random.nextInt(1000)
        incoming = true
        if (tapping && demo == null) WatchPush.tap(context)
        val seed = signalSeed
        scope.launch {
            delay(5500)
            if (signalSeed == seed) incoming = false
        }
    }

    fun closeSignal() { incoming = false }

    // The people

    fun refresh() {
        val session = session ?: return
        if (demo != null) return
        scope.launch {
            loadReceiving()
            try {
                contacts = WatchApi(session.token).contacts()
                loaded = true
                sendingOff = false
                WidgetData.save(context, WidgetData.from(contacts))
            } catch (e: ApiError) {
                when {
                    e.sessionGone -> signOut(tr("Часы отключены в «Устройствах». Войди снова."))
                    e.switchedOff -> {
                        contacts = emptyList(); loaded = true; sendingOff = true
                        WidgetData.save(context, emptyList())
                    }
                    // Offline: keep what is on screen and try on the next refresh.
                }
            }
        }
    }

    fun send(target: Target) {
        when (target) {
            Target.Anyone -> sent(ANYONE, tr("Кому-нибудь на свете — улетело ✨")) { it.signalAnyone() }
            is Target.Person -> sent(target.contact.id, tr("{name} — улетело ♥", "name" to target.contact.label)) { it.signal(target.contact.id) }
        }
    }

    // A held finger

    /** A finger is down on someone, not yet long enough to show the name. */
    fun pressStarted() {
        if (peeking == null) holdCancelled = false
    }

    /** Held long enough: the whole name shows. Slid away: it hides, and letting go sends nothing. */
    fun holding(target: Target, movedDp: Float) {
        if (holdCancelled) return
        if (movedDp > 28) {
            holdCancelled = true
            peeking = null
            flash(tr("Отменено"))
            return
        }
        if (peeking != target) {
            peeking = target
            if (demo == null) WatchPush.click(context)
        }
    }

    /** The finger let go: what it held goes, unless it slid away. */
    fun released(target: Target) {
        val cancelled = holdCancelled
        holdCancelled = false
        if (cancelled || peeking != target) return
        peeking = null
        send(target)
    }

    /** A tap on the shown name, if the finger's own gesture was lost. */
    fun sendPeeked() {
        val target = peeking ?: return
        peeking = null
        send(target)
    }

    fun cancelPeek() {
        if (peeking == null) return
        peeking = null
        flash(tr("Отменено"))
    }

    /** A caption for a moment, nothing sent. */
    private fun flash(text: String) {
        val generation = ++sentGeneration
        caption = text
        scope.launch {
            delay(1500)
            if (generation == sentGeneration) caption = null
        }
    }

    private fun sent(id: String, text: String, call: suspend (WatchApi) -> Unit) {
        if (demo == null) WatchPush.click(context)
        val generation = ++sentGeneration
        justSent = id
        caption = text
        scope.launch {
            val session = session
            if (demo == null && session != null) {
                try {
                    call(WatchApi(session.token))
                } catch (e: ApiError) {
                    when {
                        e.sessionGone -> { signOut(tr("Часы отключены в «Устройствах». Войди снова.")); return@launch }
                        e.switchedOff -> { sendingOff = true; caption = tr("Отправка с часов выключена") }
                        e.status == 429 && id == ANYONE -> caption = tr("Кому-нибудь на свете уже улетело в этот час.")
                        else -> caption = tr("Не улетело, проверь интернет")
                    }
                    justSent = null
                }
            }
            delay(2500)
            if (generation != sentGeneration) return@launch
            justSent = null
            caption = null
        }
    }

    // Demo

    fun demoSet(
        session: Session?, contacts: List<Contact>, screen: Screen,
        pairing: WatchApi.PairingCode? = null, expired: Boolean = false,
        justSent: String? = null, caption: String? = null,
        layout: WatchLayout = WatchLayout.Hive, peeking: Target? = null,
    ) {
        this.layout = layout
        this.peeking = peeking
        this.session = session
        this.receiving = if (session == null) null else true
        this.contacts = contacts
        this.loaded = true
        this.pairing = pairing
        this.pairingExpired = expired
        this.justSent = justSent
        this.caption = caption
        this.screen = screen
    }
}
