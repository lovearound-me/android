package me.lovearound.android.wear

import android.app.Application
import me.lovearound.android.Strings
import me.lovearound.android.WidgetData

/** The process: the one model the screen and the FCM service share. */
class WatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Strings.init(this)
        WatchPush.init(this)
        WidgetData.onChange = { Surfaces.refresh(it) }
    }

    companion object {
        /** Made by the activity; null while only the FCM service runs. */
        var model: WatchModel? = null
    }
}
