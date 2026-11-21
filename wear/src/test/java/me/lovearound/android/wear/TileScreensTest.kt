package me.lovearound.android.wear

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.renderer.TileRenderer
import com.github.takahirom.roborazzi.captureRoboImage
import me.lovearound.android.Strings
import me.lovearound.android.WidgetData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executor

/**
 * The «Близкие» tile drawn on a round watch, as the tile carousel would:
 * wear/build/outputs/roborazzi/tile/<lang>/<case>.png. The ru «people» one
 * and the en one are the tile list's previews (res/drawable*-nodpi).
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class TileScreensTest(private val lang: String, private val name: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}")
        fun screens(): List<Array<String>> =
            listOf("ru", "en", "ru-pixel").flatMap { lang -> listOf("people", "two", "signed-out", "empty-book").map { arrayOf(lang, it) } }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun draws() {
        if (lang.endsWith("-pixel")) RuntimeEnvironment.setQualifiers("w192dp-h192dp-round-watch-xhdpi")
        Strings.init(context, lang.removeSuffix("-pixel"))
        val people = WatchDemo.contacts.let { WidgetData.from(it) }
        val shown = when (name) {
            "signed-out" -> null
            "empty-book" -> emptyList()
            "two" -> people.take(2)
            else -> people
        }
        val layout = LayoutElementBuilders.Layout.Builder().setRoot(Surfaces.tileLayout(context, shown, 1, if (lang.endsWith("-pixel")) 192 else 227)).build()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val frame = FrameLayout(activity)
        frame.setBackgroundColor(Color.BLACK)
        activity.setContentView(frame, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val renderer = TileRenderer(activity, Executor { it.run() }) {}
        val view = renderer.inflateAsync(layout, ResourceBuilders.Resources.Builder().setVersion(Surfaces.RESOURCES).build(), frame)
        shadowOf(Looper.getMainLooper()).idle()
        view.get()
        shadowOf(Looper.getMainLooper()).idle()
        frame.captureRoboImage("build/outputs/roborazzi/tile/$lang/$name.png")
    }
}
