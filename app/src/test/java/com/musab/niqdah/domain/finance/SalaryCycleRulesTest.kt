package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalaryCycleRulesTest {
    @Test
    fun salaryProfileAmountAloneDoesNotCreateSalaryCycleOpeningBalance() {
        val data = FinanceData.empty(uid = "uid").copy(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L).copy(
                salary = 8_000.0,
                salaryMinor = 800_000L,
                onboardingCompleted = true
            )
        )

        assertNull(SalaryCycleRules.activeCycle(data, cycleMonth = "2026-05"))
    }

    @Test
    fun salarySmsWithAvailableBalanceCreatesConfirmedCycleBaseline() {
        val occurredAt = FinanceDates.parseDateInput("2026-05-25") ?: error("Invalid date")

        val cycle = SalaryCycleRules.upsertForSalaryDeposit(
            existingCycles = emptyList(),
            userId = "uid",
            amountMinor = 800_000L,
            currentDailyUseBalanceMinor = 920_000L,
            currency = "AED",
            occurredAtMillis = occurredAt,
            source = SalaryCycleSource.SMS,
            nowMillis = 123L
        )

        assertEquals("2026-05", cycle.cycleMonth)
        assertEquals(800_000L, cycle.salaryDepositAmountMinor)
        assertEquals(920_000L, cycle.openingDailyUseBalanceMinor)
        assertTrue(cycle.isOpeningBalanceConfirmed)
        assertTrue(cycle.isActive)
    }

    @Test
    fun manualSalaryWithCurrentBalanceCreatesConfirmedCycleBaseline() {
        val occurredAt = FinanceDates.parseDateInput("2026-05-25") ?: error("Invalid date")

        val cycle = SalaryCycleRules.upsertForSalaryDeposit(
            existingCycles = emptyList(),
            userId = "uid",
            amountMinor = 8_000_00L,
            currentDailyUseBalanceMinor = 7_250_00L,
            currency = "AED",
            occurredAtMillis = occurredAt,
            source = SalaryCycleSource.MANUAL,
            nowMillis = 456L
        )

        assertEquals(7_250_00L, cycle.openingDailyUseBalanceMinor)
        assertTrue(cycle.isOpeningBalanceConfirmed)
        assertEquals(SalaryCycleSource.MANUAL, cycle.source)
    }

    @Test
    fun salarySmsWithoutAvailableBalanceCreatesUnconfirmedCycle() {
        val occurredAt = FinanceDates.parseDateInput("2026-05-25") ?: error("Invalid date")

        val cycle = SalaryCycleRules.upsertForSalaryDeposit(
            existingCycles = emptyList(),
            userId = "uid",
            amountMinor = 800_000L,
            currentDailyUseBalanceMinor = null,
            currency = "AED",
            occurredAtMillis = occurredAt,
            source = SalaryCycleSource.SMS,
            nowMillis = 789L
        )

        assertEquals(800_000L, cycle.salaryDepositAmountMinor)
        assertNull(cycle.openingDailyUseBalanceMinor)
        assertFalse(cycle.isOpeningBalanceConfirmed)
    }
}
