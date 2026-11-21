package me.lovearound.android.widget

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.lovearound.android.Api
import me.lovearound.android.ApiError
import me.lovearound.android.AppModel
import me.lovearound.android.Auth
import me.lovearound.android.AuthError
import me.lovearound.android.LoveAround
import me.lovearound.android.WidgetData
import me.lovearound.android.tr

/**
 * Sends a heart from outside the app's screens: a widget's bubble, a quick
 * setting. Signs in with the app's own session (its Firebase refresh
 * token), as ios/WidgetShared/HeartSender.swift does. The app doesn't open.
 */
object Sender {
    /** Sends to a contact id or [WidgetData.ANYONE]; null when it went, else what to say. */
    suspend fun send(context: Context, id: String): String? {
        val app = context.applicationContext
        val model = LoveAround.model?.takeIf { it.demo == null }
        val anyone = id == WidgetData.ANYONE
        // Once an hour, as in the app.
        if (anyone && (model?.anyoneWait() ?: AppModel.anyoneWait(app)) != null) {
            return tr("Кому-нибудь на свете уже улетело в этот час.")
        }
        val api = model?.api ?: Api(Auth(app))
        WidgetData.sent(app, id)
        val problem = try {
            if (anyone) api.signalAnyone() else api.signal(id)
            null
        } catch (e: AuthError) {
            if (e.code == "NETWORK") tr("Не улетело, проверь интернет") else tr("Войди в Love Around на этом телефоне")
        } catch (e: ApiError) {
            when {
                e is ApiError.Network -> tr("Не улетело, проверь интернет")
                e.status == 429 && anyone -> tr("Кому-нибудь на свете уже улетело в этот час.")
                e.status == 401 -> tr("Войди в Love Around на этом телефоне")
                else -> tr("Не получилось. Попробуй ещё раз.")
            }
        } catch (e: Exception) {
            tr("Не получилось. Попробуй ещё раз.")
        }
        if (problem == null) {
            withContext(Dispatchers.Main) {
                if (model != null) model.sentElsewhere(id) else AppModel.noteSent(app, id)
            }
            // The bubble's ♥ goes out again.
            Widgets.scope.launch { delay(WidgetData.SENT_FOR + 100); Widgets.refresh(app) }
        } else {
            WidgetData.sent(app, null)
        }
        return problem
    }
}
