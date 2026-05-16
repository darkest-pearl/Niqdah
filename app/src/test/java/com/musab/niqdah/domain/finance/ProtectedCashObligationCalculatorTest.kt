package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedCashObligationCalculatorTest {
    @Test
    fun unpaidRentRemainsProtectedUntilMatchingTransactionIsRecorded() {
        val data = baseData().copy(
            categories = listOf(
                BudgetCategory(
                    id = FinanceDefaults.RENT_CATEGORY_ID,
                    name = "Rent",
                    monthlyBudget = 2_000.0,
                    monthlyBudgetMinor = 200_000L,
                    type = CategoryType.FIXED
                )
            )
        )

        val obligations = ProtectedCashObligationCalculator.obligations(data, yearMonth = "2026-05")
        val rent = obligations.single { it.type == ProtectedCashObligationType.RENT }

        assertEquals(ProtectedCashObligationStatus.UNPAID, rent.status)
        assertEquals(200_000L, rent.remainingAmountMinor)
        assertTrue(rent.isProtected)
    }

    @Test
    fun paidObligationNoLongerCountsAsProtectedRemainingAmount() {
        val paidAt = FinanceDates.parseDateInput("2026-05-02") ?: error("Invalid date")
        val data = baseData().copy(
            categories = listOf(
                BudgetCategory(
                    id = FinanceDefaults.RENT_CATEGORY_ID,
                    name = "Rent",
                    monthlyBudget = 2_000.0,
                    monthlyBudgetMinor = 200_000L,
                    type = CategoryType.FIXED
                )
            ),
            transactions = listOf(
                ExpenseTransaction(
                    id = "rent-paid",
                    categoryId = FinanceDefaults.RENT_CATEGORY_ID,
                    amount = 2_000.0,
                    amountMinor = 200_000L,
                    occurredAtMillis = paidAt,
                    yearMonth = "2026-05"
                )
            )
        )

        val rent = ProtectedCashObligationCalculator.obligations(data, yearMonth = "2026-05")
            .single { it.type == ProtectedCashObligationType.RENT }

        assertEquals(ProtectedCashObligationStatus.PAID, rent.status)
        assertEquals(0L, rent.remainingAmountMinor)
        assertFalse(rent.isProtected)
    }

    @Test
    fun savingsTargetRemainsProtectedUntilSavingsContributionReachesTarget() {
        val savedAt = FinanceDates.parseDateInput("2026-05-04") ?: error("Invalid date")
        val data = baseData().copy(
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
            transactions = listOf(
                ExpenseTransaction(
                    id = "partial-savings",
                    categoryId = FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID,
                    amount = 900.0,
                    amountMinor = 90_000L,
                    occurredAtMillis = savedAt,
                    yearMonth = "2026-05"
                )
            ),
            reminderSettings = FinanceDefaults.reminderSettings(now = 0L).copy(
                monthlySavingsTargetAmount = 1_500.0,
                monthlySavingsTargetAmountMinor = 150_000L
            )
        )

        val savings = ProtectedCashObligationCalculator.obligations(data, yearMonth = "2026-05")
            .single { it.type == ProtectedCashObligationType.SAVINGS_TARGET }

        assertEquals(ProtectedCashObligationStatus.UNPAID, savings.status)
        assertEquals(60_000L, savings.remainingAmountMinor)
    }

    @Test
    fun debtPaymentClosesWhenDebtPaymentTransactionIsRecorded() {
        val paidAt = FinanceDates.parseDateInput("2026-05-05") ?: error("Invalid date")
        val data = baseData().copy(
            categories = listOf(
                BudgetCategory(
                    id = FinanceDefaults.DEBT_PAYMENT_CATEGORY_ID,
                    name = "Debt payment",
                    monthlyBudget = 500.0,
                    monthlyBudgetMinor = 50_000L,
                    type = CategoryType.DEBT
                )
            ),
            debt = FinanceDefaults.debtTracker(now = 0L).copy(
                startingAmount = 5_000.0,
                startingAmountMinor = 500_000L,
                remainingAmount = 4_500.0,
                remainingAmountMinor = 450_000L,
                monthlyAutoReduction = 500.0,
                monthlyAutoReductionMinor = 50_000L
            ),
            transactions = listOf(
                ExpenseTransaction(
                    id = "debt-paid",
                    categoryId = FinanceDefaults.DEBT_PAYMENT_CATEGORY_ID,
                    amount = 500.0,
                    amountMinor = 50_000L,
                    occurredAtMillis = paidAt,
                    yearMonth = "2026-05"
                )
            )
        )

        val debt = ProtectedCashObligationCalculator.obligations(data, yearMonth = "2026-05")
            .single { it.type == ProtectedCashObligationType.DEBT_PAYMENT }

        assertEquals(ProtectedCashObligationStatus.PAID, debt.status)
        assertEquals(0L, debt.remainingAmountMinor)
    }

    private fun baseData(): FinanceData =
        FinanceData.empty(uid = "uid").copy(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L).copy(onboardingCompleted = true)
        )
}
