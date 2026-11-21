package me.lovearound.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import me.lovearound.android.ui.LoveAroundTheme
import me.lovearound.android.ui.Root

class MainActivity : ComponentActivity() {
    private lateinit var model: AppModel

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        model.notificationsAnswered()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Screenshots and checks: `adb shell am start … --es demo home [--es lang en]`.
        val demo = intent.getStringExtra("demo")?.let { Demo(it) }
        intent.getStringExtra("lang")?.let { Strings.init(this, it) }
        model = LoveAround.model?.takeIf { demo == null && it.demo == null } ?: AppModel(applicationContext, demo).also { LoveAround.model = it }
        // Debug builds: sign in for an hour with a test account's ID token
        // (`--es token $(tools/test-id-token.py)`), nothing saved.
        if (BuildConfig.DEBUG) intent.getStringExtra("token")?.let { if (!model.signedIn) model.borrow(it) }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = model.becameActive()
            override fun onStop(owner: LifecycleOwner) = model.wentToBackground()
        })
        handle(intent)
        setContent {
            LoveAroundTheme {
                Root(model, askNotifications = ::askNotifications)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.getBooleanExtra(Push.EXTRA_TAPPED, false)) {
            intent.removeExtra(Push.EXTRA_TAPPED)
            model.tapped()
        }
        intent.data?.let { model.handle(it) }
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else model.notificationsAnswered()
    }
}
