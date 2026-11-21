package me.lovearound.android.widget

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.lovearound.android.AppModel
import me.lovearound.android.LoveAround
import me.lovearound.android.MainActivity
import me.lovearound.android.R
import me.lovearound.android.WidgetData
import me.lovearound.android.tr

/**
 * Quick settings, the iPhone's Control Center buttons: «♥ <person>» (who is
 * picked by a long press) and «Кому-нибудь». A tap sends; the app stays closed.
 */
object HeartTiles {
    /** The person tile's pick in WidgetData.chosen. */
    const val PERSON_KEY = "tile"

    fun refresh(context: Context) {
        for (cls in listOf(PersonTile::class.java, AnyoneTile::class.java)) {
            runCatching { TileService.requestListeningState(context, ComponentName(context, cls)) }
        }
    }
}

/** What both tiles share: a tap sends, the subtitle says so for a moment. */
abstract class HeartTile : TileService() {
    /** Who a tap sends to now; null opens the app (signed out, nobody in the book). */
    abstract fun target(): String?
    abstract fun label(): String
    abstract val icon: Int
    open fun idleSubtitle(): String = "Love Around"

    private var saying: String? = null

    override fun onStartListening() = draw()

    override fun onClick() {
        val id = target() ?: return openApp()
        val app = applicationContext
        Widgets.scope.launch {
            val problem = Sender.send(app, id)
            withContext(Dispatchers.Main) {
                if (problem != null) {
                    Toast.makeText(app, problem, Toast.LENGTH_SHORT).show()
                } else {
                    saying = if (id == WidgetData.ANYONE) tr("Улетело ✨") else tr("Улетело ♥")
                    draw()
                }
            }
            if (problem == null) {
                delay(WidgetData.SENT_FOR)
                withContext(Dispatchers.Main) { saying = null; draw() }
            }
        }
    }

    private fun draw() {
        val tile = qsTile ?: return
        tile.label = label()
        tile.icon = Icon.createWithResource(this, icon)
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = saying ?: idleSubtitle()
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/** «♥ Маме»: the person picked by a long press, or the first close one. */
class PersonTile : HeartTile() {
    override val icon = R.drawable.ic_heart
    override fun target() = WidgetData.personFor(this, HeartTiles.PERSON_KEY)?.id
    override fun label() = WidgetData.personFor(this, HeartTiles.PERSON_KEY)?.label ?: tr("Отправить сердечко")
    override fun idleSubtitle() =
        if (WidgetData.people(this) == null) tr("Войди в Love Around на этом телефоне") else "Love Around"
}

/** «Кому-нибудь на свете»: once an hour. */
class AnyoneTile : HeartTile() {
    override val icon = R.drawable.ic_sparkles
    override fun target() = if (WidgetData.people(this) == null) null else WidgetData.ANYONE
    override fun label() = tr("Кому-нибудь")
    override fun idleSubtitle(): String {
        val wait = LoveAround.model?.takeIf { it.demo == null }?.anyoneWait() ?: AppModel.anyoneWait(this)
        return wait?.let { tr("через {n} мин", "n" to "$it") } ?: "Love Around"
    }
}
