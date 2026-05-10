package com.musab.niqdah.domain.finance

fun interface DisciplineReminderScheduler {
    fun schedule(data: FinanceData)
}
