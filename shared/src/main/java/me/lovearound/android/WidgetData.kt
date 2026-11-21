package me.lovearound.android

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * What the widgets, tiles and the watch face's ♥ draw, kept by the app on
 * this device: the people (for the bubbles), my color and what a widget
 * just sent. As on the iPhone (ios/WidgetShared/WidgetData.swift): nothing
 * is counted, nothing says who sent a heart.
 */
object WidgetData {
    /** How long a bubble shows its ♥ after a tap. */
    const val SENT_FOR = 2_000L
    /** «Кому-нибудь на свете» in `sent`. */
    const val ANYONE = "anyone"
    const val PAGE_SIZE = 4
    /** A minute after the last «›», the first four again. */
    const val PAGE_FOR = 60_000L

    @Serializable
    data class Person(val id: String, val label: String, val color: Int? = null, val close: Boolean = false) {
        /** The first letter, whole even when it is an emoji. */
        val initial: String
            get() {
                val t = label.trim()
                if (t.isEmpty()) return "♥"
                return String(Character.toChars(t.codePointAt(0))).uppercase()
            }
    }

    /** Set by each app: redraw its widgets, tiles, complications. */
    var onChange: (Context) -> Unit = {}

    private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private val list = ListSerializer(Person.serializer())

    /** Close people first, each group in the owner's order (see [from]). */
    fun people(context: Context): List<Person>? =
        prefs(context).getString("people", null)?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() }

    fun save(context: Context, people: List<Person>?) {
        if (people == people(context)) return
        prefs(context).edit().putString("people", people?.let { json.encodeToString(list, it) }).apply()
        onChange(context)
    }

    /** The book as the widgets show it: close first, each group in the owner's order. */
    fun from(contacts: List<Contact>): List<Person> = contacts
        .sortedWith(compareBy<Contact>({ !it.isClose }, { it.position }))
        .map { Person(it.id, it.label, it.color, it.isClose) }

    /** The color the screen glows in for me. */
    fun myColor(context: Context): Int = prefs(context).getInt("myColor", 1)

    fun setMyColor(context: Context, color: Int) {
        if (color == myColor(context)) return
        prefs(context).edit().putInt("myColor", color).apply()
        onChange(context)
    }

    /** A widget's bubble sent to `id` (or failed: null): it shows a ♥ for a moment. */
    fun sent(context: Context, id: String?, at: Long = System.currentTimeMillis()) {
        prefs(context).edit().putString("sentId", id).putLong("sentAt", at).apply()
        onChange(context)
    }

    /** The bubble showing its ♥ now, and until when. */
    fun justSent(context: Context, now: Long = System.currentTimeMillis()): Pair<String, Long>? {
        val p = prefs(context)
        val id = p.getString("sentId", null) ?: return null
        val until = p.getLong("sentAt", 0) + SENT_FOR
        return if (until > now) id to until else null
    }

    // «Близкие» in a row: «›» shows the next four

    /** Who «›» pages through: the close people, when there are more than four; otherwise nobody. */
    fun pagedPeople(people: List<Person>): List<Person>? = people.filter { it.close }.takeIf { it.size > PAGE_SIZE }

    fun pageCount(people: List<Person>): Int = pagedPeople(people)?.let { (it.size + PAGE_SIZE - 1) / PAGE_SIZE } ?: 1

    /** The four shown now (0 is the first), and until when. */
    fun page(context: Context, now: Long = System.currentTimeMillis()): Pair<Int, Long?> {
        val p = prefs(context)
        val index = p.getInt("page", 0)
        val until = p.getLong("pageAt", 0) + PAGE_FOR
        val pages = pageCount(people(context) ?: emptyList())
        return if (index > 0 && until > now && index < pages) index to until else 0 to null
    }

    fun nextPage(context: Context, now: Long = System.currentTimeMillis()) {
        val pages = pageCount(people(context) ?: emptyList())
        val next = if (pages > 1) (page(context, now).first + 1) % pages else 0
        prefs(context).edit().putInt("page", next).putLong("pageAt", now).apply()
        onChange(context)
    }

    /** Soty: rows of 3, 4 and 2 of the first eight, «Кому-нибудь» (null) second in the first. */
    fun hiveRows(people: List<Person>): List<List<Person?>> {
        val slots: MutableList<Person?> = people.take(8).toMutableList()
        slots.add(minOf(1, slots.size), null)
        return listOf(slots.take(3), slots.drop(3).take(4), slots.drop(7)).filter { it.isNotEmpty() }
    }

    // The person a «Один человек» widget or a quick setting sends to

    fun chosen(context: Context, key: String): String? = prefs(context).getString("chosen.$key", null)

    fun choose(context: Context, key: String, id: String?) {
        prefs(context).edit().putString("chosen.$key", id).apply()
        onChange(context)
    }

    /** The chosen one if still in the book, otherwise the first. */
    fun personFor(context: Context, key: String): Person? {
        val people = people(context) ?: return null
        val id = chosen(context, key)
        return people.firstOrNull { it.id == id } ?: people.firstOrNull()
    }

    /** Signed out: the widgets forget everyone. */
    fun clear(context: Context) {
        val p = prefs(context)
        val myColor = p.getInt("myColor", 1)
        p.edit().clear().putInt("myColor", myColor).apply()
        onChange(context)
    }
}
