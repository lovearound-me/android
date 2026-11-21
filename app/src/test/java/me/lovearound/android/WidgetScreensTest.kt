package me.lovearound.android

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import me.lovearound.android.widget.CloseWidget
import me.lovearound.android.widget.PersonWidget
import me.lovearound.android.widget.Widgets
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home screen widgets drawn on the JVM, as the launcher would: each
 * case's picture lands in app/build/outputs/roborazzi/widgets/<lang>/.
 * The ru/en «person» and «close-hive» ones are the widget picker's previews
 * (res/drawable-*nodpi, copied by scripts/widget-previews.sh).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetScreensTest(private val lang: String, private val name: String) {
    companion object {
        val cases = listOf("person", "person-sent", "close-row", "close-paged", "close-paged-2", "close-hive",
            "close-hive-sent", "close-hive-night", "signed-out", "empty-book")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}")
        fun screens(): List<Array<String>> = listOf("ru", "en").flatMap { lang -> cases.map { arrayOf(lang, it) } }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun draws() {
        if (name.endsWith("night")) RuntimeEnvironment.setQualifiers("+night")
        Strings.init(context, lang)
        // No launcher here: nothing to redraw.
        WidgetData.onChange = {}
        val people = Widgets.sample(lang == "ru")
        when (name) {
            "signed-out" -> WidgetData.save(context, null)
            "empty-book" -> WidgetData.save(context, emptyList())
            // Six close people: «›» pages through them.
            "close-paged", "close-paged-2" -> WidgetData.save(context, people.mapIndexed { i, p -> p.copy(close = i < 6) })
            else -> WidgetData.save(context, people)
        }
        if (name == "close-paged-2") WidgetData.nextPage(context)
        if (name == "person-sent") WidgetData.sent(context, "1")
        if (name == "close-hive-sent") WidgetData.sent(context, WidgetData.ANYONE)

        val (widget, size) = when {
            name.startsWith("person") -> PersonWidget() to DpSize(150.dp, 150.dp)
            name.startsWith("close-hive") -> CloseWidget() to DpSize(320.dp, 330.dp)
            name == "signed-out" || name == "empty-book" -> CloseWidget() to DpSize(320.dp, 140.dp)
            else -> CloseWidget() to DpSize(320.dp, 140.dp)
        }
        Widgets.version.value++
        draw(widget, size, "build/outputs/roborazzi/widgets/$lang/$name.png")
    }

    private fun draw(widget: GlanceAppWidget, size: DpSize, path: String) {
        val views = runBlocking { widget.compose(context, size = size) }
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val density = activity.resources.displayMetrics.density
        val frame = FrameLayout(activity)
        // Clear around the widget (the picker shows these), dark behind the night one.
        frame.setBackgroundColor(if (name.endsWith("night")) Color.rgb(40, 44, 52) else Color.TRANSPARENT)
        val pad = (12 * density).toInt()
        frame.setPadding(pad, pad, pad, pad)
        val view: View = views.apply(activity, frame)
        frame.addView(view, FrameLayout.LayoutParams((size.width.value * density).toInt(), (size.height.value * density).toInt()))
        activity.setContentView(frame, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.captureRoboImage(path)
    }
}
