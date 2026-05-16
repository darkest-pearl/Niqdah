package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavingsFollowUpReminderRulesTest {
    @Test
    fun salaryRecordedTriggersSavingsReminderIfTargetIsNotMet() {
        val data = baseData(savingsSavedMinor = 0L)

        val result = SavingsFollowUpReminderRules.evaluate(
            data = data,
            cycleMonth = "2026-05",
            today = LocalDate.parse("2026-05-25")
        )

        assertTrue(result.shouldNotify)
        assertEquals(150_000L, result.remainingSavingsTargetMinor)
    }

    @Test
    fun noReminderIfSavingsAlreadyMet() {
        val data = baseData(savingsSavedMinor = 150_000L)

        val result = SavingsFollowUpReminderRules.evaluate(
            data = data,
            cycleMonth = "2026-05",
            today = LocalDate.parse("2026-05-25")
        )

        assertFalse(result.shouldNotify)
        assertEquals(SavingsFollowUpState.COMPLETED, result.state)
    }

    @Test
    fun reminderRepeatsEveryConfiguredIntervalIfUnmet() {
        val data = baseData(savingsSavedMinor = 50_000L).copy(
            salaryCycles = listOf(
                cycle(lastReminderAtMillis = FinanceDates.parseDateInput("2026-05-25") ?: error("Invalid date"))
            )
        )

        val tooSoon = SavingsFollowUpReminderRules.evaluate(
            data = data,
            cycleMonth = "2026-05",
            today = LocalDate.parse("2026-05-27")
        )
        val dueAgain = SavingsFollowUpReminderRules.evaluate(
            data = data,
            cycleMonth = "2026-05",
            today = LocalDate.parse("2026-05-28")
        )

        assertFalse(tooSoon.shouldNotify)
        assertTrue(dueAgain.shouldNotify)
    }

    @Test
    fun doNotRemindThisMonthSuppressesCurrentCycleOnly() {
        val data = baseData(savingsSavedMinor = 0L).copy(
            reminderSettings = FinanceDefaults.reminderSettings(now = 0L).copy(
                monthlySavingsTargetAmount = 1_500.0,
                monthlySavingsTargetAmountMinor = 150_000L,
                suppressedPostSalarySavingsFollowUpCycleMonth = "2026-05"
            )
        )

        val result = SavingsFollowUpReminderRules.evaluate(
            data = data,
            cycleMonth = "2026-05",
            today = LocalDate.parse("2026-05-28")
        )

        assertFalse(result.shouldNotify)
        assertEquals(SavingsFollowUpState.SUPPRESSED_FOR_CYCLE, result.state)
    }

    private fun baseData(savingsSavedMinor: Long): FinanceData {
        val savedAt = FinanceDates.parseDateInput("2026-05-26") ?: error("Invalid date")
        return FinanceData.empty(uid = "uid").copy(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L).copy(
                monthlySavingsTarget = 1_500.0,
                monthlySavingsTargetMinor = 150_000L,
                onboardingCompleted = true
            ),
            categories = listOf(
                BudgetCategory(
                    id = FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID,
                    name = "Savings goal",
                    monthlyBudget = 1_500.0,
                    monthlyBudgetMinor = 150_000L,
                    type = CategoryType.SAVINGS
                )
            ),
            transactions = if (savingsSavedMinor > 0L) {
                listOf(
                    ExpenseTransaction(
                        id = "savings",
                        categoryId = FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID,
                        amount = minorUnitsToMajor(savingsSavedMinor),
                        amountMinor = savingsSavedMinor,
                        occurredAtMillis = savedAt,
                        yearMonth = "2026-05"
                    )
                )
            } else {
                emptyList()
            },
            salaryCycles = listOf(cycle()),
            reminderSettings = FinanceDefaults.reminderSettings(now = 0L).copy(
                monthlySavingsTargetAmount = 1_500.0,
                monthlySavingsTargetAmountMinor = 150_000L,
                isPostSalarySavingsFollowUpEnabled = true,
                postSalarySavingsFollowUpIntervalDays = 3
            )
        )
    }

    private fun cycle(lastReminderAtMillis: Long = 0L): SalaryCycle =
        SalaryCycle(
            id = "salary-cycle-2026-05",
            userId = "uid",
            cycleMonth = "2026-05",
            salaryDepositAmountMinor = 800_000L,
            openingDailyUseBalanceMinor = 900_000L,
            salaryDepositDateMillis = FinanceDates.parseDateInput("2026-05-25") ?: error("Invalid date"),
            currency = "AED",
            source = SalaryCycleSource.SMS,
            isOpeningBalanceConfirmed = true,
            isActive = true,
            createdAtMillis = 0L,
            updatedAtMillis = 0L,
            lastSavingsFollowUpReminderAtMillis = lastReminderAtMillis
        )
}
