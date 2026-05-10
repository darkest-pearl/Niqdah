package com.musab.niqdah.data.finance

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

internal object NiqdahNotificationChannels {
    const val BANK_IMPORT_REVIEWS = "bank_import_reviews"
    const val INTERNAL_TRANSFER_SAFETY = "internal_transfer_safety"
    const val DISCIPLINE_REMINDERS = "discipline_reminders"

    fun ensureBankImportReviews(context: Context): String =
        ensure(
            context = context,
            id = BANK_IMPORT_REVIEWS,
            name = "Bank import reviews",
            description = "Review, save, edit, or dismiss parsed bank SMS drafts."
        )

    fun ensureInternalTransferSafety(context: Context): String =
        ensure(
            context = context,
            id = INTERNAL_TRANSFER_SAFETY,
            name = "Internal transfer safety",
            description = "Review transfer pairs and missing matching credits."
        )

    fun ensureDisciplineReminders(context: Context): String =
        ensure(
            context = context,
            id = DISCIPLINE_REMINDERS,
            name = "Discipline reminders",
            description = "Savings, budget, and necessary-item reminders."
        )

    private fun ensure(
        context: Context,
        id: String,
        name: String,
        description: String
    ): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    id,
                    name,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    this.description = description
                }
            )
        }
        return id
    }
}
