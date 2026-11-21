package me.lovearound.android.wear

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import me.lovearound.android.Config
import me.lovearound.android.Emoji
import me.lovearound.android.Palette
import me.lovearound.android.R
import me.lovearound.android.tr

/**
 * The watch's own notifications. The watch is a device of the account, so a
 * heart comes to it straight from the server through FCM (docs/schema.md),
 * on the watch's own terms: its «Получать сердечки», its quiet hours and
 * «кто-нибудь на свете» in its card on the phone. The phone's switch,
 * pause and quiet hours don't reach it.
 */
object WatchPush {
    private const val PREFS = "push"
    const val CHANNEL = "hearts"
    const val QUIET_CHANNEL = "hearts-quiet"
    const val EXTRA_TAPPED = "me.lovearound.android.TAPPED"

    fun init(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setApplicationId(Config.FIREBASE_ANDROID_APP)
                .setApiKey(Config.FIREBASE_ANDROID_KEY)
                .setProjectId(Config.FIREBASE_PROJECT)
                .setGcmSenderId(Config.FIREBASE_SENDER)
                .build())
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Сердечки"), NotificationManager.IMPORTANCE_HIGH).apply {
            description = tr("Когда кто-то думает о тебе")
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 60, 120, 60)
        })
        manager.createNotificationChannel(NotificationChannel(QUIET_CHANNEL, tr("Сердечки без вибрации"), NotificationManager.IMPORTANCE_LOW).apply {
            description = tr("Когда кто-то думает о тебе")
            setSound(null, null)
            enableVibration(false)
        })
    }

    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** FCM's address of the app on this watch, once Google gives it. */
    fun token(context: Context, done: (String) -> Unit) {
        try {
            FirebaseMessaging.getInstance().isAutoInitEnabled = true
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("token", token).apply()
                done(token)
            }
        } catch (e: Exception) {
            // No Google Play services: the app still glows while open.
        }
    }

    fun lastToken(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("token", null)

    /** Signed out: FCM forgets this watch. */
    fun forget(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        try {
            FirebaseMessaging.getInstance().isAutoInitEnabled = false
            FirebaseMessaging.getInstance().deleteToken()
        } catch (e: Exception) {}
    }

    /** The wrist's tap, if «Вибрация» is on. */
    fun tap(context: Context) {
        if (!WatchSettings.vibrates(context)) return
        context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 120, 60), -1))
    }

    /** A short click under the finger. */
    fun click(context: Context) {
        context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    /**
     * A heart while the app isn't in front: the wave of hearts and «Кто-то
     * думает о тебе», in my color, nothing about who. A tap opens the glow.
     */
    fun notify(context: Context, silent: Boolean) {
        if (!allowed(context)) return
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_TAPPED, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val color = Palette.all[Palette.index(WatchSettings.myColor(context))].argb.toInt()
        val notification = NotificationCompat.Builder(context, if (silent) QUIET_CHANNEL else CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(color)
            .setContentTitle(tr("Кто-то думает о тебе"))
            .setContentText(Emoji.wave())
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(if (silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
            .setAutoCancel(true)
            // Nothing to read or answer: it goes away by itself after a while.
            .setTimeoutAfter(60 * 60 * 1000L)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {}
    }
}

/** FCM's side: a new token for the server, a heart to show. */
class Messaging : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        getSharedPreferences("push", MODE_PRIVATE).edit().putString("token", token).apply()
        WatchApp.model?.registerPush(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["kind"] != "signal") return
        val silent = message.data["silent"] == "1"
        val model = WatchApp.model
        if (model != null && model.inFront) {
            android.os.Handler(mainLooper).post { model.showSignal(tapping = !silent) }
        } else {
            WatchPush.notify(this, silent)
        }
    }
}
