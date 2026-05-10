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
import com.musab.niqdah.domain.finance.InternalTransferNotificationAction
import com.musab.niqdah.domain.finance.InternalTransferNotificationPlan

internal object InternalTransferNotificationPublisher {
    fun show(context: Context, plan: InternalTransferNotificationPlan) {
        val appContext = context.applicationContext
        if (!appContext.canPostNotifications()) return
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val channelId = NiqdahNotificationChannels.ensureInternalTransferSafety(appContext)

        val builder = Notification.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(plan.title)
            .setContentText(plan.text)
            .setStyle(Notification.BigTextStyle().bigText(plan.text))
            .setContentIntent(reviewIntent(appContext, plan))
            .setAutoCancel(true)

        plan.actions.forEach { action ->
            builder.addAction(
                Notification.Action.Builder(
                    R.drawable.ic_launcher_foreground,
                    action.label,
                    pendingIntentFor(appContext, plan, action)
                ).build()
            )
        }

        notificationManager.notify(plan.notificationId, builder.build())
    }

    fun cancel(context: Context, notificationId: Int) {
        context.applicationContext.getSystemService(NotificationManager::class.java).cancel(notificationId)
    }

    private fun pendingIntentFor(
        context: Context,
        plan: InternalTransferNotificationPlan,
        action: InternalTransferNotificationAction
    ): PendingIntent =
        when (action) {
            InternalTransferNotificationAction.SAVE -> PendingIntent.getBroadcast(
                context,
                "${plan.primaryImportId}-transfer-save".hashCode(),
                Intent(context, BankSmsNotificationActionReceiver::class.java).apply {
                    this.action = BankSmsNotificationActions.ACTION_SAVE
                    putExtra(BankSmsNotificationActions.EXTRA_IMPORT_ID, plan.primaryImportId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            InternalTransferNotificationAction.DISMISS -> PendingIntent.getBroadcast(
                context,
                "${plan.primaryImportId}-transfer-dismiss".hashCode(),
                Intent(context, BankSmsNotificationActionReceiver::class.java).apply {
                    this.action = BankSmsNotificationActions.ACTION_DISMISS
                    putExtra(BankSmsNotificationActions.EXTRA_IMPORT_ID, plan.primaryImportId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            InternalTransferNotificationAction.EDIT,
            InternalTransferNotificationAction.REVIEW -> reviewIntent(context, plan)
        }

    private fun reviewIntent(context: Context, plan: InternalTransferNotificationPlan): PendingIntent =
        PendingIntent.getActivity(
            context,
            "${plan.primaryImportId}-transfer-review".hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_TRANSACTIONS, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private val InternalTransferNotificationAction.label: String
        get() = when (this) {
            InternalTransferNotificationAction.SAVE -> "Save"
            InternalTransferNotificationAction.EDIT -> "Edit"
            InternalTransferNotificationAction.REVIEW -> "Review"
            InternalTransferNotificationAction.DISMISS -> "Dismiss"
        }

    private fun Context.canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}
