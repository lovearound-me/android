package me.lovearound.android.widget

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.lovearound.android.MainActivity
import me.lovearound.android.Palette
import me.lovearound.android.R
import me.lovearound.android.WidgetData
import me.lovearound.android.tr
import java.util.concurrent.TimeUnit

/**
 * Home screen widgets, as on the iPhone (ios/Widgets): «Один человек»
 * (one big bubble) and «Близкие» (four in a row, or the soty when tall).
 * A bubble's tap sends a heart without opening the app. Nothing here says
 * who sent a heart, when, or how many.
 */
object Widgets {
    /** Goes up on every change of WidgetData: running widgets draw again. */
    val version = MutableStateFlow(0)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refresh(context: Context) {
        val app = context.applicationContext
        version.value++
        scope.launch { redraw(app) }
        HeartTiles.refresh(app)
    }

    suspend fun redraw(context: Context) {
        runCatching { PersonWidget().updateAll(context) }
        runCatching { CloseWidget().updateAll(context) }
    }

    /** Signed out, or a snapshot for a test: what a widget draws. */
    class Book(
        val people: List<WidgetData.Person>?,
        val sent: String?,
        val sentUntil: Long?,
        val page: Int,
        val pageUntil: Long?,
        val myColor: Int,
    ) {
        companion object {
            fun now(context: Context): Book {
                val sent = WidgetData.justSent(context)
                val page = WidgetData.page(context)
                return Book(WidgetData.people(context), sent?.first, sent?.second, page.first, page.second, WidgetData.myColor(context))
            }
        }
    }

    /** Made-up people for the widget picker's preview: Russian names in Russian. */
    fun sample(ru: Boolean): List<WidgetData.Person> {
        val names = if (ru) listOf("Мама", "Аня", "Оля", "Лёша", "Петя", "Ваня", "Дима", "Света")
        else listOf("Mom", "Anna", "Olivia", "Alex", "Peter", "John", "Dan", "Lucy")
        val colors = listOf(1, 2, 3, 5, 4, 10, 6, 8)
        return names.indices.map { WidgetData.Person("${it + 1}", names[it], colors[it], close = it < 4) }
    }
}

// The app's paper and ink, light and dark (ui/Theme.kt).
internal val paper = ColorProvider(day = Color(0xFFFFFBF6), night = Color(0xFF2A211D))
internal val ink = ColorProvider(day = Color(0xFF3B2A23), night = Color(0xFFF3E6DC))
internal val muted = ColorProvider(day = Color(0xFF6E554A), night = Color(0xFFC7B0A2))
internal val faint = ColorProvider(day = Color(0x2E6E554A), night = Color(0x2EC7B0A2))
private val white = ColorProvider(day = Color.White, night = Color.White)
private val star = Color(0xFFD99A1E)

/** Draws the book now, and again when the bubble's ♥ or the page runs out. */
@Composable
private fun Live(content: @Composable (Widgets.Book) -> Unit) {
    val context = LocalContext.current
    val version by Widgets.version.collectAsState()
    val book = remember(version) { Widgets.Book.now(context) }
    val until = listOfNotNull(book.sentUntil, book.pageUntil).minOrNull()
    if (until != null) {
        LaunchedEffect(until) {
            delay((until - System.currentTimeMillis()).coerceAtLeast(0) + 50)
            Widgets.version.value++
        }
    }
    content(book)
}

@Composable
private fun Paper(content: @Composable () -> Unit) {
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(22.dp).background(paper).padding(12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun send(id: String): Action = actionRunCallback<SendHeart>(actionParametersOf(SendHeart.who to id))

/** A person's bubble in their color, the first letter on it; a ♥ for a moment after a tap. */
@Composable
fun Bubble(person: WidgetData.Person, size: Dp, sent: Boolean, nameWidth: Dp = size * 1.18f) {
    BubbleOf(Palette.color(person.color), person.label, size, sent, nameWidth, send(person.id)) {
        Text(person.initial, style = TextStyle(color = white, fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.Medium))
    }
}

/** «Кому-нибудь на свете»: gold, with sparkles. */
@Composable
fun AnyoneBubble(size: Dp, sent: Boolean, nameWidth: Dp = size * 1.18f) {
    BubbleOf(star, tr("Кому-нибудь"), size, sent, nameWidth, send(WidgetData.ANYONE)) {
        Image(ImageProvider(R.drawable.ic_sparkles), tr("Кому-нибудь на свете"), GlanceModifier.size(size * 0.42f),
            colorFilter = ColorFilter.tint(white))
    }
}

@Composable
private fun BubbleOf(color: Color, label: String, size: Dp, sent: Boolean, nameWidth: Dp, onTap: Action, mark: @Composable () -> Unit) {
    val ring = maxOf(2.dp, size * 0.05f)
    Column(GlanceModifier.clickable(onTap), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
            if (sent) Image(ImageProvider(R.drawable.widget_circle), null, GlanceModifier.size(size), colorFilter = ColorFilter.tint(white))
            Image(ImageProvider(R.drawable.widget_circle), label, GlanceModifier.size(if (sent) size - ring * 2 else size),
                colorFilter = ColorFilter.tint(ColorProvider(day = color, night = color)))
            if (sent) {
                Image(ImageProvider(R.drawable.ic_heart), null, GlanceModifier.size(size * 0.42f), colorFilter = ColorFilter.tint(white))
            } else mark()
        }
        Text(label, maxLines = 1, modifier = GlanceModifier.width(nameWidth).padding(top = 4.dp),
            style = TextStyle(color = ink, fontSize = maxOf(11f, size.value * 0.2f).sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center))
    }
}

/** Signed out, or nobody in the book yet: a tap opens the app. */
@Composable
private fun EmptyBook(signedOut: Boolean, myColor: Int) {
    Column(GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.ic_heart), null, GlanceModifier.size(30.dp),
            colorFilter = ColorFilter.tint(ColorProvider(day = Palette.color(myColor), night = Palette.color(myColor))))
        Text(if (signedOut) tr("Войди в Love Around на этом телефоне") else tr("Пока никого нет"),
            modifier = GlanceModifier.padding(top = 6.dp),
            style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center))
    }
}

// «Один человек»

/** One big bubble: the person picked in its settings, or the first close one. */
class PersonWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val key = key(context, id)
        provideContent { Live { book -> PersonContent(book, key) } }
    }

    companion object {
        fun key(context: Context, id: GlanceId): String =
            runCatching { "widget-${GlanceAppWidgetManager(context).getAppWidgetId(id)}" }.getOrDefault("widget")
    }
}

@Composable
fun PersonContent(book: Widgets.Book, key: String) {
    val context = LocalContext.current
    val people = book.people
    val chosen = WidgetData.chosen(context, key)
    val person = people?.let { list -> list.firstOrNull { it.id == chosen } ?: list.firstOrNull() }
    val size = LocalSize.current
    Paper {
        if (person != null) {
            val bubble = minOf(84.dp, minOf(size.width, size.height) - 52.dp).coerceAtLeast(40.dp)
            Bubble(person, bubble, book.sent == person.id)
        } else EmptyBook(people == null, book.myColor)
    }
}

class PersonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PersonWidget()
}

// «Близкие»

/** Four in a row; tall, the soty: eight people and «Кому-нибудь». */
class CloseWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(ROW, HIVE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Live { book -> CloseContent(book) } }
    }

    companion object {
        val ROW = DpSize(240.dp, 100.dp)
        val HIVE = DpSize(240.dp, 250.dp)
    }
}

@Composable
fun CloseContent(book: Widgets.Book) {
    val people = book.people
    val tall = LocalSize.current.height >= CloseWidget.HIVE.height
    Paper {
        when {
            people.isNullOrEmpty() -> EmptyBook(people == null, book.myColor)
            tall -> Hive(people, book.sent)
            else -> CloseRow(people, book)
        }
    }
}

/** With more than four close people, «›» and dots above them show the next four. */
@Composable
private fun CloseRow(people: List<WidgetData.Person>, book: Widgets.Book) {
    val paged = WidgetData.pagedPeople(people)
    if (paged == null) {
        Bubbles(people.take(WidgetData.PAGE_SIZE), 56.dp, book.sent, keepPlaces = false)
        return
    }
    val pages = WidgetData.pageCount(people)
    val page = minOf(book.page, pages - 1)
    val four = paged.drop(page * WidgetData.PAGE_SIZE).take(WidgetData.PAGE_SIZE)
    Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Близкие"), GlanceModifier.defaultWeight(),
                style = TextStyle(color = muted, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            for (i in 0 until pages) {
                Image(ImageProvider(R.drawable.widget_circle), null, GlanceModifier.size(5.dp).padding(0.dp),
                    colorFilter = ColorFilter.tint(if (i == page) ink else faint))
                Spacer(GlanceModifier.width(4.dp))
            }
            Spacer(GlanceModifier.width(4.dp))
            Box(GlanceModifier.size(28.dp).clickable(actionRunCallback<NextPage>()), contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.widget_circle), null, GlanceModifier.size(28.dp), colorFilter = ColorFilter.tint(faint))
                Image(ImageProvider(R.drawable.ic_chevron), tr("Следующие"), GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(ink))
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        Bubbles(four, 50.dp, book.sent, keepPlaces = true)
    }
}

@Composable
private fun Bubbles(people: List<WidgetData.Person>, size: Dp, sent: String?, keepPlaces: Boolean) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        for (person in people) {
            Box(GlanceModifier.defaultWeight(), contentAlignment = Alignment.TopCenter) { Bubble(person, size, sent == person.id) }
        }
        // A last page of one or two keeps the bubbles where they were.
        if (keepPlaces) repeat(WidgetData.PAGE_SIZE - people.size) { Spacer(GlanceModifier.defaultWeight()) }
    }
}

/** Soty: rows of 3, 4 and 2, «Кому-нибудь» in the middle of the first. */
@Composable
private fun Hive(people: List<WidgetData.Person>, sent: String?) {
    // Four across with their names, as wide as the widget lets them be.
    val cell = (LocalSize.current.width - 24.dp - 12.dp) / 4
    val bubble = (cell / 1.18f).coerceIn(36.dp, 56.dp)
    Column(GlanceModifier.fillMaxSize()) {
        Text(tr("О ком думаешь?"), style = TextStyle(color = muted, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            for ((r, row) in WidgetData.hiveRows(people).withIndex()) {
                if (r > 0) Spacer(GlanceModifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    for ((i, person) in row.withIndex()) {
                        if (i > 0) Spacer(GlanceModifier.width(4.dp))
                        if (person == null) AnyoneBubble(bubble, sent == WidgetData.ANYONE, cell)
                        else Bubble(person, bubble, sent == person.id, cell)
                    }
                }
            }
        }
    }
}

class CloseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CloseWidget()
}

// Taps

/** A bubble's tap: the heart goes, the app stays closed. */
class SendHeart : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[who] ?: return
        val problem = Sender.send(context, id)
        if (problem != null) {
            withContext(Dispatchers.Main) { Toast.makeText(context.applicationContext, problem, Toast.LENGTH_SHORT).show() }
        }
    }

    companion object {
        val who = ActionParameters.Key<String>("who")
    }
}

/** «›»: the next four; a minute later the first four again. */
class NextPage : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetData.nextPage(context)
        WorkManager.getInstance(context).enqueueUniqueWork("widget-first-page", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FirstPage>().setInitialDelay(WidgetData.PAGE_FOR + 500, TimeUnit.MILLISECONDS).build())
    }
}

class FirstPage(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Widgets.version.value++
        Widgets.redraw(applicationContext)
        return Result.success()
    }
}
