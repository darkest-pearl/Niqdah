package com.musab.niqdah.data.finance

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.musab.niqdah.domain.finance.DisciplineReminderScheduler
import com.musab.niqdah.domain.finance.FinanceData
import com.musab.niqdah.domain.finance.FinanceDates
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
            settings.isPostSalarySavingsFollowUpEnabled,
            settings.postSalarySavingsFollowUpIntervalDays,
            settings.suppressedPostSalarySavingsFollowUpCycleMonth,
            data.salaryCycles.count { it.isActive },
            data.salaryCycles.maxOfOrNull { it.lastSavingsFollowUpReminderAtMillis } ?: 0L,
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
        val hasActiveCurrentCycle =
            data.salaryCycles.any { it.isActive && it.cycleMonth == FinanceDates.currentYearMonth() }
        val postSalaryFollowUpEnabled = settings.isPostSalarySavingsFollowUpEnabled && hasActiveCurrentCycle
        scheduleOrCancel(
            type = DisciplineReminderWorker.TYPE_POST_SALARY_SAVINGS_FOLLOW_UP,
            enabled = postSalaryFollowUpEnabled,
            hour = 9,
            minute = 5
        )
        scheduleImmediatePostSalaryFollowUpIfNeeded(enabled = postSalaryFollowUpEnabled)
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

    private fun scheduleImmediatePostSalaryFollowUpIfNeeded(enabled: Boolean) {
        val workName = "${DisciplineReminderWorker.workName(uid, DisciplineReminderWorker.TYPE_POST_SALARY_SAVINGS_FOLLOW_UP)}-immediate"
        val workManager = WorkManager.getInstance(appContext)
        if (!enabled) {
            workManager.cancelUniqueWork(workName)
            return
        }
        val request = OneTimeWorkRequestBuilder<DisciplineReminderWorker>()
            .setInitialDelay(15, TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    DisciplineReminderWorker.KEY_UID to uid,
                    DisciplineReminderWorker.KEY_TYPE to DisciplineReminderWorker.TYPE_POST_SALARY_SAVINGS_FOLLOW_UP
                )
            )
            .build()
        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
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
