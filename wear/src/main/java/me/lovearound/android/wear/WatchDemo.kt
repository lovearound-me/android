package me.lovearound.android.wear

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.lovearound.android.Contact
import me.lovearound.android.Strings
import me.lovearound.android.tr

/**
 * Screens for screenshots: `--es demo <name>` fills the app with made-up
 * people and never touches the network. The Robolectric test
 * (WatchScreensTest) draws them all.
 */
class WatchDemo(val name: String) {
    companion object {
        val names = listOf("signin", "code", "expired", "computer", "connected",
            "hive", "circles", "named", "list", "peek", "sent", "anyone", "empty", "signal",
            "settings", "layout", "glow", "delete")

        fun said(russian: String, english: String) = if (Strings.lang == "ru") russian else english

        val session = Session("demo", "anna@example.com")

        val code = WatchApi.PairingCode("482913", "", "https://lovearound.me/login/devices#482913", 600, 3)

        val contacts: List<Contact>
            get() = listOf(
                Contact("1", said("Мама", "Mom"), 0, true, 1),
                Contact("2", said("Аня", "Anna"), 1, true, 2),
                Contact("3", said("Оля", "Olivia"), 2, true, 3),
                Contact("4", said("Лёша", "Alex"), 3, true, 5),
                Contact("5", said("Петя", "Peter"), 4, true, 4),
                Contact("6", said("Ваня", "John"), 5, false, 10),
                Contact("7", said("Дима", "Dan"), 6, false, 6),
                Contact("8", said("Света", "Lucy"), 7, false, 8),
            )
    }

    /** A screen reached by navigation, shown on its own. */
    val page: String? get() = name.takeIf { it in setOf("computer", "settings", "layout", "glow", "delete") }

    fun apply(model: WatchModel) {
        val people = contacts
        when (name) {
            "signin" -> model.demoSet(null, emptyList(), WatchModel.Screen.SignIn)
            "code", "computer" -> model.demoSet(null, emptyList(), WatchModel.Screen.Code, pairing = code)
            "expired" -> model.demoSet(null, emptyList(), WatchModel.Screen.Code, pairing = code, expired = true)
            "connected" -> model.demoSet(session, people, WatchModel.Screen.Connected)
            "sent" -> model.demoSet(session, people, WatchModel.Screen.Main, justSent = "2",
                caption = tr("{name} — улетело ♥", "name" to people[1].label))
            "anyone" -> model.demoSet(session, people, WatchModel.Screen.Main, justSent = WatchModel.ANYONE,
                caption = tr("Кому-нибудь на свете — улетело ✨"))
            "circles" -> model.demoSet(session, people, WatchModel.Screen.Main, layout = WatchLayout.Circles)
            "named" -> model.demoSet(session, people, WatchModel.Screen.Main, layout = WatchLayout.Named)
            "list" -> model.demoSet(session, people, WatchModel.Screen.Main, layout = WatchLayout.List)
            "peek" -> model.demoSet(session, people, WatchModel.Screen.Main, peeking = Target.Person(people[1]))
            "empty" -> model.demoSet(session, emptyList(), WatchModel.Screen.Main)
            "signal" -> {
                model.demoSet(session, people, WatchModel.Screen.Main)
                model.scope.launch { delay(400); model.showSignal() }
            }
            else -> model.demoSet(session, people, WatchModel.Screen.Main)
        }
    }
}
