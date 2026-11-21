package me.lovearound.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Notifications: Android's permission, this phone's Firebase Cloud Messaging
 * token, and what a heart does. The server sends each heart as a bare data
 * message, only while this phone takes hearts, outside its pause and quiet
 * hours; the phone writes the notification the way Settings say, or glows
 * if the app is in front.
 */
object Push {
    private const val PREFS = "push"
    /** Hearts with the phone's sound and buzz. */
    const val CHANNEL = "hearts"
    /** «Вибрация» off: no sound, no buzz. */
    const val QUIET_CHANNEL = "hearts-quiet"
    const val EXTRA_TAPPED = "me.lovearound.android.TAPPED"

    fun init(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
                .setApplicationId("1:989287714644:android:0da5ca579b89451c2e42c1")
                .setApiKey("AIzaSyB-mXJxxoyPWPgbbr4uDjOy_Inzo63ShRw")
                .setProjectId("needed-app-2026")
                .setGcmSenderId("989287714644")
                .build())
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Сердечки"), NotificationManager.IMPORTANCE_HIGH).apply {
            description = tr("Когда кто-то думает о тебе")
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 60, 120, 60)
        })
        manager.createNotificationChannel(NotificationChannel(QUIET_CHANNEL, tr("Сердечки без вибрации"), NotificationManager.IMPORTANCE_DEFAULT).apply {
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

    /** The token, once Firebase gives it; kept for signing out. */
    fun token(done: () -> Unit) {
        try {
            FirebaseMessaging.getInstance().isAutoInitEnabled = true
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                val context = FirebaseApp.getInstance().applicationContext
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("token", token).apply()
                done()
            }
        } catch (e: Exception) {
            // No Google Play services: the app still glows while open.
        }
    }

    fun lastToken(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("token", null)

    fun gotToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("token", token).apply()
    }

    /** Signed out: Firebase forgets this phone's token, whatever the server still holds. */
    fun forget(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("token").apply()
        try {
            FirebaseMessaging.getInstance().isAutoInitEnabled = false
            FirebaseMessaging.getInstance().deleteToken()
        } catch (e: Exception) {}
    }

    /** A heart as a notification: new emoji each time, never who. */
    fun notify(context: Context) {
        if (!allowed(context)) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_TAPPED, true)
        val tap = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = NotifyLook.current(context).text(NotifyLook.withWords(context))
        val notification = NotificationCompat.Builder(context, if (NotifyLook.vibrates(context)) CHANNEL else QUIET_CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFE0596B.toInt())
            .setContentTitle("Love Around")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 100000).toInt(), notification)
        } catch (e: SecurityException) {}
    }
}

/** Where Firebase Cloud Messaging hands a heart and a new token. */
class Messaging : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Push.gotToken(this, token)
        LoveAround.model?.let { model -> model.scope.launchMain { model.syncPush() } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["kind"] != "signal") return
        val model = LoveAround.model
        if (model != null && model.inFront) {
            // In front: glow instead of a notification.
            model.scope.launchMain { model.receive() }
        } else {
            Push.notify(this)
        }
    }
}
