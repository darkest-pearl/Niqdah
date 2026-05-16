package com.musab.niqdah.data.finance

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.musab.niqdah.MainActivity
import com.musab.niqdah.R

internal object DisciplineNotificationPublisher {
    fun show(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        actions: List<DisciplineNotificationAction> = emptyList()
    ) {
        val appContext = context.applicationContext
        if (!appContext.canPostNotifications()) return
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val channelId = NiqdahNotificationChannels.ensureDisciplineReminders(appContext)

        val intent = PendingIntent.getActivity(
            appContext,
            "discipline-$notificationId".hashCode(),
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)

        actions.forEach { action ->
            builder.addAction(
                Notification.Action.Builder(
                    action.iconResId,
                    action.title,
                    action.intent
                ).build()
            )
        }

        notificationManager.notify(notificationId, builder.build())
    }

    private fun Context.canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}

internal data class DisciplineNotificationAction(
    val title: String,
    val intent: PendingIntent,
    val iconResId: Int = R.drawable.ic_launcher_foreground
)
