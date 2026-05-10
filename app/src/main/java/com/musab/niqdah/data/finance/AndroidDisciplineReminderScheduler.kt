package com.musab.niqdah.data.finance

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.musab.niqdah.domain.finance.DisciplineReminderScheduler
import com.musab.niqdah.domain.finance.FinanceData
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class AndroidDisciplineReminderScheduler(
    context: Context,
    private val uid: String
) : DisciplineReminderScheduler {
    private val appContext = context.applicationContext
    private var lastScheduleSignature: String? = null

    override fun schedule(data: FinanceData) {
        val settings = data.reminderSettings
        val signature = listOf(
            settings.isMonthlySavingsReminderEnabled,
            settings.monthlySavingsReminderHour,
            settings.monthlySavingsReminderMinute,
            settings.isMissedSavingsReminderEnabled,
            settings.missedSavingsReminderHour,
            settings.missedSavingsReminderMinute,
            settings.areOverspendingWarningsEnabled,
            data.necessaryItems.count { it.isNotificationEnabled }
        ).joinToString("|")
        if (signature == lastScheduleSignature) return
        lastScheduleSignature = signature

        scheduleOrCancel(
            type = DisciplineReminderWorker.TYPE_MONTHLY_SAVINGS,
            enabled = settings.isMonthlySavingsReminderEnabled,
            hour = settings.monthlySavingsReminderHour,
            minute = settings.monthlySavingsReminderMinute
        )
        scheduleOrCancel(
            type = DisciplineReminderWorker.TYPE_MISSED_SAVINGS,
            enabled = settings.isMissedSavingsReminderEnabled,
            hour = settings.missedSavingsReminderHour,
            minute = settings.missedSavingsReminderMinute
        )
        scheduleOrCancel(
            type = DisciplineReminderWorker.TYPE_OVERSPENDING,
            enabled = settings.areOverspendingWarningsEnabled,
            hour = 20,
            minute = 0
        )
        scheduleOrCancel(
            type = DisciplineReminderWorker.TYPE_NECESSARY_ITEMS,
            enabled = data.necessaryItems.any { it.isNotificationEnabled },
            hour = 8,
            minute = 30
        )
    }

    private fun scheduleOrCancel(type: String, enabled: Boolean, hour: Int, minute: Int) {
        val workName = DisciplineReminderWorker.workName(uid, type)
        val workManager = WorkManager.getInstance(appContext)
        if (!enabled) {
            workManager.cancelUniqueWork(workName)
            return
        }

        val request = PeriodicWorkRequestBuilder<DisciplineReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(hour, minute), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    DisciplineReminderWorker.KEY_UID to uid,
                    DisciplineReminderWorker.KEY_TYPE to type
                )
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    private fun delayUntil(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now()
        val targetTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        var next = now.with(targetTime)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis().coerceAtLeast(0L)
    }
}
