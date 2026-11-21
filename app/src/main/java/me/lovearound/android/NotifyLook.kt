package me.lovearound.android

import android.content.Context

/**
 * How a heart's notification reads (Настройки → «Уведомление»): emoji, and
 * words after them if «Со словами» is on. The server sends a bare heart and
 * never says who; the phone writes the text, new emoji each time. Watches
 * and bands show the same text as the phone.
 */
enum class NotifyLook(val key: String) {
    Many("many"), Three("three"), One("one"), Plain("plain");

    val title: String
        get() = when (this) {
            Many -> tr("Волна сердечек")
            Three -> tr("Три сердечка")
            One -> tr("Одно сердечко")
            Plain -> tr("Простое ♥") + "\uFE0E"
        }

    /** For the list in Settings: one fixed example each. */
    val example: String
        get() = when (this) {
            Many -> "✨💗💖💞💖💗✨"
            Three -> "💕💖💕"
            One -> "💗"
            Plain -> "♥\uFE0E"
        }

    val note: String? get() = if (this == Plain) tr("Для браслетов, которые не рисуют эмодзи") else null

    /** The notification's text: the wave; three hearts; one heart; «♥» every band has. Then the words, if asked. */
    fun text(words: Boolean): String {
        val emoji = when (this) {
            Many -> Emoji.wave()
            Three -> Emoji.three()
            One -> Emoji.hearts.random()
            Plain -> "♥\uFE0E"
        }
        return if (words) "$emoji ${Companion.words}" else emoji
    }

    companion object {
        private const val PREFS = "notify"
        val words: String get() = tr("Кто-то думает о тебе")

        fun current(context: Context): NotifyLook {
            val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("look", null)
            return entries.firstOrNull { it.key == key } ?: Many
        }
        fun setCurrent(context: Context, look: NotifyLook) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("look", look.key).apply()

        /** «Со словами»: «Кто-то думает о тебе» after the emoji. */
        fun withWords(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("words", false)
        fun setWithWords(context: Context, on: Boolean) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("words", on).apply()

        /** «Вибрация»: off, a heart comes without a sound and without a buzz. */
        fun vibrates(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("vibrates", true)
        fun setVibrates(context: Context, on: Boolean) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("vibrates", on).apply()
    }
}
