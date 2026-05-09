package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceCalculatorTest {
    @Test
    fun savingsTransfersContributeToSavingsProgressWithoutCountingAsSpent() {
        val categories = FinanceDefaults.budgetCategories(now = 0L)
        val occurredAt = FinanceDates.parseDateInput("2026-05-08") ?: error("Invalid test date")
        val data = FinanceData(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L),
            categories = categories,
            transactions = listOf(
                ExpenseTransaction(
                    id = "expense",
                    categoryId = FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID,
                    amount = 42.50,
                    currency = "AED",
                    occurredAtMillis = occurredAt,
                    yearMonth = "2026-05"
                ),
                ExpenseTransaction(
                    id = "savings",
                    categoryId = FinanceDefaults.MARRIAGE_SAVINGS_CATEGORY_ID,
                    amount = 1_700.0,
                    currency = "AED",
                    occurredAtMillis = occurredAt,
                    yearMonth = "2026-05"
                )
            ),
            incomeTransactions = emptyList(),
            pendingBankImports = emptyList(),
            accountBalanceSnapshots = emptyList(),
            merchantRules = emptyList(),
            goals = FinanceDefaults.savingsGoals(now = 0L),
            debt = FinanceDefaults.debtTracker(now = 0L),
            bankMessageSettings = FinanceDefaults.bankMessageParserSettings()
        )

        val dashboard = FinanceCalculator.dashboard(data, yearMonth = "2026-05", now = 0L)

        assertEquals(42.50, dashboard.totalSpent, 0.001)
        assertEquals(1.0, dashboard.savingsTargetProgress, 0.001)
    }
}
