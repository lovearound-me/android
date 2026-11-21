package me.lovearound.android.wear

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import me.lovearound.android.Palette
import me.lovearound.android.Strings

class MainActivity : ComponentActivity() {
    private lateinit var model: WatchModel

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.notificationsAnswered()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Screenshots: `adb shell am start … --es demo hive [--es lang en]`.
        val demo = intent.getStringExtra("demo")?.let { WatchDemo(it) }
        intent.getStringExtra("lang")?.let { Strings.init(this, it) }
        model = WatchApp.model?.takeIf { demo == null && it.demo == null }
            ?: WatchModel(applicationContext, demo).also { WatchApp.model = it }
        handle(intent)
        setContent {
            WatchTheme {
                if (demo?.page != null) DemoPage(model, demo.page!!) else Root(model, ::askNotifications)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.inFront = true
        model.refresh()
    }

    override fun onPause() {
        super.onPause()
        model.inFront = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A tapped heart's notification: the app opens and glows, without another tap. */
    private fun handle(intent: Intent) {
        if (intent.getBooleanExtra(WatchPush.EXTRA_TAPPED, false)) {
            intent.removeExtra(WatchPush.EXTRA_TAPPED)
            model.showSignal(tapping = false)
        }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !WatchPush.allowed(this)) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun Root(model: WatchModel, askNotifications: () -> Unit) {
    AppScaffold(timeText = { TimeText() }) {
        Box(Modifier.fillMaxSize()) {
            when (model.screen) {
                WatchModel.Screen.SignIn -> SignInScreen(model)
                WatchModel.Screen.Code -> CodeScreen(model)
                WatchModel.Screen.Connected -> ConnectedScreen(model)
                WatchModel.Screen.Main -> {
                    // Asked once signed in, as on the iPhone's watch.
                    LaunchedEffect(Unit) { if (model.demo == null) askNotifications() }
                    Pages(model)
                }
            }
            Incoming(model)
        }
    }
}

@Composable
private fun Pages(model: WatchModel) {
    val nav = rememberSwipeDismissableNavController()
    SwipeDismissableNavHost(navController = nav, startDestination = "main") {
        composable("main") { MainScreen(model) { nav.navigate("settings") } }
        composable("settings") { SettingsScreen(model) { nav.navigate(it) } }
        composable("layout") { LayoutScreen(model) }
        composable("glow") { GlowScreen(model) }
        composable("signout") { SignOutScreen(model) { nav.popBackStack() } }
        composable("delete") { DeleteAccountScreen() }
    }
}

@Composable
private fun Incoming(model: WatchModel) {
    AnimatedVisibility(model.incoming, enter = fadeIn(), exit = fadeOut()) {
        SignalOverlay(Palette.color(model.myColor), model.signalSeed) { model.closeSignal() }
    }
}

/** A navigation screen on its own, for screenshots. */
@Composable
private fun DemoPage(model: WatchModel, page: String) {
    AppScaffold(timeText = { TimeText() }) {
        Box(Modifier.fillMaxSize()) {
            when (page) {
                "computer" -> ComputerScreen(WatchDemo.code.code)
                "settings" -> SettingsScreen(model) {}
                "layout" -> LayoutScreen(model)
                "glow" -> GlowScreen(model)
                "delete" -> DeleteAccountScreen()
            }
            Incoming(model)
        }
    }
}
