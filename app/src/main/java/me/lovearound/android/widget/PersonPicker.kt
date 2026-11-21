package me.lovearound.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.lovearound.android.MainActivity
import me.lovearound.android.Palette
import me.lovearound.android.Strings
import me.lovearound.android.WidgetData
import me.lovearound.android.tr
import me.lovearound.android.ui.C
import me.lovearound.android.ui.Group
import me.lovearound.android.ui.GroupRow
import me.lovearound.android.ui.LoveAroundTheme
import me.lovearound.android.ui.Note
import me.lovearound.android.ui.PageBar
import me.lovearound.android.ui.RowDivider

/**
 * Who a «Один человек» widget or the «♥» quick setting sends to: opened
 * when the widget is placed or changed, and by a long press on the tile.
 */
class PersonPicker : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent.getStringExtra("lang")?.let { Strings.init(this, it) }
        // A long press on «Кому-нибудь»: nothing to pick, the app opens.
        @Suppress("DEPRECATION")
        val tile = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_COMPONENT_NAME)
        if (tile != null && tile.className != PersonTile::class.java.name) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        val widget = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val key = if (widget != AppWidgetManager.INVALID_APPWIDGET_ID) "widget-$widget" else HeartTiles.PERSON_KEY
        // Leaving without a pick keeps the widget, with the first close person.
        if (widget != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget))
        }
        val people = WidgetData.people(this)
        val chosen = WidgetData.personFor(this, key)?.id
        setContent {
            LoveAroundTheme {
                Picker(people, chosen, onBack = ::finish) { id ->
                    WidgetData.choose(this, key, id)
                    finish()
                }
            }
        }
    }
}

@Composable
fun Picker(people: List<WidgetData.Person>?, chosen: String?, onBack: () -> Unit, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(C.paper).safeDrawingPadding()) {
        PageBar(tr("Кому улетит сердечко?"), onBack)
        if (people.isNullOrEmpty()) {
            Note(if (people == null) tr("Войди в Love Around на этом телефоне") else tr("Пока никого нет"),
                modifier = Modifier.padding(24.dp))
            return@Column
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp)) {
            item {
                Group {
                    for ((i, person) in people.withIndex()) {
                        if (i > 0) RowDivider()
                        GroupRow(onClick = { onPick(person.id) }) {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Palette.color(person.color)),
                                contentAlignment = Alignment.Center) {
                                Text(person.initial, color = Color.White, fontSize = 15.sp)
                            }
                            Spacer(Modifier.size(12.dp))
                            Text(person.label, color = C.ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            if (person.id == chosen) Icon(Icons.Filled.Check, null, tint = C.link)
                        }
                    }
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}
