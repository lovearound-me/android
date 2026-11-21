package me.lovearound.android.wear

import android.content.Context

/**
 * Who this watch is signed in as: a device token (`dev_…`) made for it,
 * which «Устройства» → «Убрать» on the phone or the web cuts off on its own.
 * Kept in the app's own storage, out of backups.
 */
data class Session(val token: String, val email: String?) {
    fun save(context: Context) {
        prefs(context).edit().putString("token", token).putString("email", email).apply()
    }

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("session", Context.MODE_PRIVATE)

        fun load(context: Context): Session? {
            val p = prefs(context)
            return p.getString("token", null)?.let { Session(it, p.getString("email", null)) }
        }

        fun clear(context: Context) {
            prefs(context).edit().clear().apply()
        }
    }
}
