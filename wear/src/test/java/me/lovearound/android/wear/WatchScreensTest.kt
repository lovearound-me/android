package me.lovearound.android.wear

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Every demo screen of the watch, drawn on the JVM (Robolectric) on a round
 * 454 px watch: it must open without crashing, and its picture lands in
 * wear/build/outputs/roborazzi/<lang>/<name>.png.
 *
 *     ./gradlew :wear:testDebugUnitTest -Proborazzi.test.record=true
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class WatchScreensTest(private val lang: String, private val name: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}")
        fun screens(): List<Array<String>> =
            listOf("ru", "en", "ru-pixel").flatMap { lang -> WatchDemo.names.map { arrayOf(lang, it) } }
    }

    @get:Rule val compose = createEmptyComposeRule()

    @Test fun opens() {
        // «-pixel»: a 41 mm Pixel Watch, 384 px (192 dp), the smallest round one about.
        if (lang.endsWith("-pixel")) RuntimeEnvironment.setQualifiers("w192dp-h192dp-round-watch-xhdpi")
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("demo", name).putExtra("lang", lang.removeSuffix("-pixel"))
        ActivityScenario.launch<MainActivity>(intent).use {
            compose.mainClock.autoAdvance = false
            val wait = if (name == "signal") 1500L else 600L
            repeat((wait / 100).toInt()) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                compose.mainClock.advanceTimeBy(100)
            }
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/$lang/$name.png")
        }
    }
}
