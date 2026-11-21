package me.lovearound.android.wear

import android.content.Context

/** «Вид»: how the people are laid out on the main screen. Stays on this watch. */
enum class WatchLayout(private val russian: String) {
    /** Bubbles packed like the watch's own app screen, smaller at the edges. */
    Hive("Соты"),
    /** Circles with the first letter, three in a row. */
    Circles("Кружочки"),
    /** The same circles with the name under each. */
    Named("Кружочки с именами"),
    /** One person per line, the whole name. */
    List("Список");

    val title: String get() = me.lovearound.android.tr(russian)
}

/** What stays on this watch: the layout, «Вибрация», my glow's color. */
object WatchSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("watch", Context.MODE_PRIVATE)

    fun layout(context: Context): WatchLayout =
        prefs(context).getString("layout", null)?.let { name -> WatchLayout.entries.firstOrNull { it.name == name } } ?: WatchLayout.Hive
    fun setLayout(context: Context, layout: WatchLayout) = prefs(context).edit().putString("layout", layout.name).apply()

    fun vibrates(context: Context): Boolean = prefs(context).getBoolean("vibrates", true)
    fun setVibrates(context: Context, on: Boolean) = prefs(context).edit().putBoolean("vibrates", on).apply()

    fun myColor(context: Context): Int = prefs(context).getInt("myColor", 1)
    fun setMyColor(context: Context, color: Int) = prefs(context).edit().putInt("myColor", color).apply()
}
