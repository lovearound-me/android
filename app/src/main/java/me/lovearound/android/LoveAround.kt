package me.lovearound.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The process: the one model every screen and the notification service share. */
class LoveAround : Application() {
    override fun onCreate() {
        super.onCreate()
        Strings.init(this)
        Push.init(this)
        WidgetData.onChange = { me.lovearound.android.widget.Widgets.refresh(it) }
        app = this
    }

    companion object {
        lateinit var app: LoveAround
            private set
        /** Made by the activity; null while only the notification service runs. */
        var model: AppModel? = null
    }
}

fun CoroutineScope.launchMain(block: () -> Unit) {
    launch { block() }
}
