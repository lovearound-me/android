package me.lovearound.android

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * The app's text, keyed by the Russian, as in the Mac and iPhone apps:
 * assets/i18n/<lang>.json maps the Russian to the language's own. A missing
 * line falls back to English, then to the Russian itself.
 *
 *     tr("Кто-то думает о тебе")
 *     tr("{name} — улетело ♥", "name" to "Аня")
 */
object Strings {
    /** The site's languages, in its order. */
    val languages = listOf("en", "es", "pt", "de", "fr", "it", "pl", "tr", "uk", "ru", "id", "ko", "zh")

    var lang: String = "en"
        private set
    private var table: Map<String, String> = emptyMap()
    private var english: Map<String, String> = emptyMap()

    fun init(context: Context, forced: String? = null) {
        val wanted = forced ?: run {
            val locales = context.resources.configuration.locales
            (0 until locales.size()).map { locales[it].language }
                .firstOrNull { it in languages } ?: "en"
        }
        lang = wanted
        table = load(context, wanted)
        english = if (wanted == "en" || wanted == "ru") emptyMap() else load(context, "en")
    }

    private fun load(context: Context, lang: String): Map<String, String> {
        if (lang == "ru") return emptyMap()
        return try {
            val text = context.assets.open("i18n/$lang.json").bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            json.keys().asSequence().mapNotNull { key -> json.optString(key, null)?.let { key to it } }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun get(key: String): String = table[key] ?: english[key] ?: key

    val locale: Locale get() = Locale.forLanguageTag(lang)
}

fun tr(key: String, vararg values: Pair<String, String>): String {
    var text = Strings.get(key)
    for ((name, value) in values) text = text.replace("{$name}", value)
    return text
}
