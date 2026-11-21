package me.lovearound.android

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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * Every demo screen drawn on the JVM (Robolectric), no emulator: it must
 * open without crashing, and its picture lands in
 * app/build/outputs/roborazzi/<lang>/<name>.png.
 *
 *     ./gradlew :app:testDebugUnitTest --tests '*DemoScreensTest*' -Proborazzi.test.record=true
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DemoScreensTest(private val lang: String, private val name: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}/{1}")
        fun screens(): List<Array<String>> {
            val langs = (System.getProperty("shots.langs") ?: "ru,en").split(",")
            return langs.flatMap { lang -> Demo.names.map { arrayOf(lang, it) } }
        }
    }

    @get:Rule val compose = createEmptyComposeRule()

    @Test fun opens() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra("demo", name).putExtra("lang", lang)
        ActivityScenario.launch<MainActivity>(intent).use {
            // The demo's own timers (a heart sent, one coming in) run on the main looper.
            val wait = when (name) { "sent", "anyone" -> 2600L; "signal" -> 1500L; else -> 600L }
            compose.mainClock.autoAdvance = false
            repeat((wait / 100).toInt()) {
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
                compose.mainClock.advanceTimeBy(100)
            }
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/$lang/$name.png")
        }
    }
}
