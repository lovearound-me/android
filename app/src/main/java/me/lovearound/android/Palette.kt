package me.lovearound.android

import androidx.compose.ui.graphics.Color

/**
 * The twelve warm colors of every lovearound app, in the same order: a
 * contact's `color` is an index here. Colors may repeat between contacts:
 * they are labels, a signal never says who sent it.
 */
object Palette {
    class Entry(private val russian: String, val argb: Long) {
        val name: String get() = tr(russian)
        val color: Color get() = Color(argb)
    }

    val all = listOf(
        Entry("Коралловый", 0xFFF07A62),
        Entry("Розовый", 0xFFE86A9A),
        Entry("Голубой", 0xFF4E9BD8),
        Entry("Шалфей", 0xFF6FAE7C),
        Entry("Лавандовый", 0xFF9B83DD),
        Entry("Медовый", 0xFFE5A93B),
        Entry("Бирюзовый", 0xFF3FB3A8),
        // Appended, not merged in: indices are what the server stores.
        Entry("Вишнёвый", 0xFFC94A5E),
        Entry("Карамельный", 0xFFC98B5E),
        Entry("Оливковый", 0xFF9DA24A),
        Entry("Индиго", 0xFF6474D6),
        Entry("Грифельный", 0xFF7E8C99),
    )

    fun index(i: Int?): Int = (((i ?: 0) % all.size) + all.size) % all.size
    fun color(i: Int?): Color = all[index(i)].color
}
