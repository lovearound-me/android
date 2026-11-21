package me.lovearound.android

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Screens for screenshots and UI checks: `--es demo <name>` fills the app
 * with made-up people and never touches the network. scripts/screenshots.sh
 * walks them all.
 */
class Demo(val name: String) {
    companion object {
        val names = listOf("signin", "home", "sent", "anyone", "empty", "signal", "paused", "offHere",
            "book", "contact", "add", "choose", "toys", "board", "pair", "device", "deviceWho", "deviceDoes",
            "deviceBoard", "settings", "settingsAccount", "settingsWho", "notifyLook", "notifyIntro", "how", "start")

        /** A made-up name: the Russian one in Russian, the English one elsewhere. */
        fun said(russian: String, english: String) = if (Strings.lang == "ru") russian else english

        val contacts: List<Contact>
            get() = listOf(
                Contact("1", said("Мама", "Mom"), 0, true, 1, "https://lovearound.me/i/K7MXR-2QHP9"),
                Contact("2", said("Аня", "Anna"), 1, true, 2, "https://lovearound.me/i/B3NWT-8FJD4"),
                Contact("3", said("Оля", "Olivia"), 2, true, 3),
                Contact("4", said("Лёша", "Alex"), 3, true, 5),
                Contact("5", said("Петя", "Peter"), 4, true, 4),
                Contact("6", said("Бабушка", "Grandma"), 5, true, 0),
                Contact("7", said("Ваня", "John"), 6, false, 10),
                Contact("8", said("Дима", "Dan"), 7, false, 6),
                Contact("9", said("Света", "Lucy"), 8, false, 8),
            )

        val devices: List<Device>
            get() = listOf(
                Device("d1", "watch", "Galaxy Watch", listOf("receive", "send"),
                    Instant.now().minusSeconds(300).toString(), listOf("receive", "send")),
                Device("d2", "lamp", said("Котик на полке", "Kitty on the shelf"), listOf("receive"),
                    Instant.now().minusSeconds(7200).toString(), listOf("receive"), acceptAnyone = true,
                    quietHours = QuietHours("23:00", "08:00", ZoneId.systemDefault().id)),
                Device("d3", "custom", said("Кнопка на столе", "Button on the desk"), listOf("send")),
            )
    }

    fun apply(model: AppModel) {
        val people = contacts
        when (name) {
            "signin" -> model.demoSet(signedIn = false, contacts = emptyList())
            "sent" -> {
                model.demoSet(signedIn = true, contacts = people)
                model.scope.launch { delay(1600); model.send(people[1]) }
            }
            "anyone" -> {
                model.demoSet(signedIn = true, contacts = people)
                model.scope.launch { delay(1600); model.sendToAnyone(androidx.compose.ui.graphics.Color(0xFFD99A1E)) }
            }
            "empty" -> model.demoSet(signedIn = true, contacts = emptyList())
            "start" -> model.demoSet(signedIn = true, contacts = listOf(people[1]), starting = true, sharedLoveId = true)
            "how" -> { model.demoSet(signedIn = true, contacts = people); model.demoShowHowItWorks() }
            "paused" -> model.demoSet(signedIn = true, contacts = people, pausedUntil = AppModel.FOREVER)
            "offHere" -> model.demoSet(signedIn = true, contacts = people, receiveHere = false)
            "signal" -> {
                model.demoSet(signedIn = true, contacts = people)
                model.scope.launch { delay(400); model.showSignal() }
            }
            "notifyIntro" -> { model.demoSet(signedIn = true, contacts = people); model.demoShowNotifyIntro() }
            "book" -> model.demoSet(signedIn = true, contacts = people, tab = AppModel.Tab.Book)
            "contact" -> { model.demoSet(signedIn = true, contacts = people, tab = AppModel.Tab.Book); model.editing = people[1] }
            "add" -> { model.demoSet(signedIn = true, contacts = people, tab = AppModel.Tab.Book); model.adding = "https://lovearound.me/i/B3NWT-8FJD4" }
            "choose" -> { model.demoSet(signedIn = true, contacts = people); model.choosingClose = true }
            "toys" -> model.demoSet(signedIn = true, contacts = people, devices = devices, tab = AppModel.Tab.Toys)
            "board" -> { model.demoSet(signedIn = true, contacts = people, devices = devices, tab = AppModel.Tab.Toys); model.toysPath = listOf("new-board") }
            "pair" -> { model.demoSet(signedIn = true, contacts = people, devices = devices, tab = AppModel.Tab.Toys); model.pairing = "482913" }
            "device", "deviceWho", "deviceDoes" -> {
                model.demoSet(signedIn = true, contacts = people, devices = devices, tab = AppModel.Tab.Toys)
                model.toysPath = listOf("d2") + when (name) { "deviceWho" -> listOf("d2/who"); "deviceDoes" -> listOf("d2/does"); else -> emptyList() }
            }
            "deviceBoard" -> {
                model.demoSet(signedIn = true, contacts = people, devices = devices, tab = AppModel.Tab.Toys)
                model.toysPath = listOf("d3")
            }
            "settings", "settingsAccount", "settingsWho", "notifyLook" -> {
                val quiet = if (name == "settingsWho") QuietHours("23:00", "08:00", ZoneId.systemDefault().id) else null
                model.demoSet(signedIn = true, contacts = people, tab = AppModel.Tab.Settings, quietHours = quiet)
                model.settingsPath = when (name) {
                    "settingsWho" -> listOf(AppModel.SettingsPage.Who)
                    "notifyLook" -> listOf(AppModel.SettingsPage.Look)
                    "settingsAccount" -> listOf(AppModel.SettingsPage.Account)
                    else -> emptyList()
                }
            }
            else -> model.demoSet(signedIn = true, contacts = people)
        }
    }
}
