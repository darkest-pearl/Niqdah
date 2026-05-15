package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FinanceCalculatorTest {
    @Test
    fun savingsTransfersContributeToSavingsProgressWithoutCountingAsSpent() {
        val categories = FinanceDefaults.budgetCategories(now = 0L)
        val occurredAt = FinanceDates.parseDateInput("2026-05-08") ?: error("Invalid test date")
        val data = FinanceData(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L).copy(
                salary = 5_000.0,
                monthlySavingsTarget = 1_700.0,
                onboardingCompleted = true
            ),
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
                    categoryId = FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID,
                    amount = 1_700.0,
                    currency = "AED",
                    occurredAtMillis = occurredAt,
                    yearMonth = "2026-05"
                )
            ),
            incomeTransactions = emptyList(),
            pendingBankImports = emptyList(),
            accountBalanceSnapshots = emptyList(),
            internalTransferRecords = emptyList(),
            merchantRules = emptyList(),
            goals = FinanceDefaults.savingsGoals(now = 0L),
            debt = FinanceDefaults.debtTracker(now = 0L),
            bankMessageSettings = FinanceDefaults.bankMessageParserSettings(),
            reminderSettings = FinanceDefaults.reminderSettings(now = 0L).copy(
                monthlySavingsTargetAmount = 1_700.0
            )
        )

        val dashboard = FinanceCalculator.dashboard(data, yearMonth = "2026-05", now = 0L)

        assertEquals(42.50, dashboard.totalSpent, 0.001)
        assertEquals(1.0, dashboard.savingsTargetProgress, 0.001)
    }

    @Test
    fun dashboardCalculationsKeepCentPrecision() {
        val categories = FinanceDefaults.budgetCategories(now = 0L).map { category ->
            when (category.id) {
                FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID -> category.copy(
                    monthlyBudget = 100.00,
                    monthlyBudgetMinor = 10_000L
                )
                else -> category.copy(monthlyBudget = 0.0, monthlyBudgetMinor = 0L)
            }
        }
        val occurredAt = FinanceDates.parseDateInput("2026-05-08") ?: error("Invalid test date")
        val data = FinanceData(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L).copy(
                salary = 1_499.23,
                salaryMinor = 149_923L,
                extraIncome = 0.93,
                extraIncomeMinor = 93L,
                monthlySavingsTarget = 17.25,
                monthlySavingsTargetMinor = 1_725L,
                onboardingCompleted = true
            ),
            categories = categories,
            transactions = listOf(
                ExpenseTransaction(
                    id = "expense",
                    categoryId = FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID,
                    amount = 17.25,
                    amountMinor = 1_725L,
                    currency = "AED",
                    occurredAtMillis = occurredAt,
                    yearMonth = "2026-05"
                )
            ),
            incomeTransactions = listOf(
                IncomeTransaction(
                    id = "income",
                    amount = 0.93,
                    amountMinor = 93L,
                    currency = "AED",
                    occurredAtMillis = occurredAt,
                    yearMonth = "2026-05"
                )
            ),
            pendingBankImports = emptyList(),
            accountBalanceSnapshots = emptyList(),
            accountLedgerEntries = emptyList(),
            internalTransferRecords = emptyList(),
            merchantRules = emptyList(),
            goals = emptyList(),
            debt = FinanceDefaults.debtTracker(now = 0L),
            bankMessageSettings = FinanceDefaults.bankMessageParserSettings(),
            reminderSettings = FinanceDefaults.reminderSettings(now = 0L).copy(
                monthlySavingsTargetAmount = 17.25,
                monthlySavingsTargetAmountMinor = 1_725L
            )
        )

        val dashboard = FinanceCalculator.dashboard(data, yearMonth = "2026-05", now = 0L)

        assertEquals(1_501.09, dashboard.totalMonthlyIncome, 0.001)
        assertEquals(17.25, dashboard.totalSpent, 0.001)
        assertEquals(1_466.59, dashboard.remainingSafeToSpend, 0.001)
    }

    @Test
    fun savingsAccountBalanceAndGoalProgressRemainDistinct() {
        val data = FinanceData(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L).copy(onboardingCompleted = true),
            categories = FinanceDefaults.budgetCategories(now = 0L),
            transactions = emptyList(),
            incomeTransactions = emptyList(),
            pendingBankImports = emptyList(),
            accountBalanceSnapshots = emptyList(),
            accountLedgerEntries = listOf(
                AccountLedgerEntry(
                    id = "savings-balance",
                    accountKind = AccountKind.SAVINGS,
                    accountSuffix = "4146",
                    eventType = AccountLedgerEventType.BALANCE_CONFIRMED_SMS,
                    amountMinor = 0L,
                    balanceAfterMinor = 200_000L,
                    currency = "AED",
                    confidence = AccountBalanceConfidence.CONFIRMED,
                    source = AccountLedgerSource.SMS,
                    createdAtMillis = 123L,
                    note = "Confirmed savings balance"
                )
            ),
            internalTransferRecords = emptyList(),
            merchantRules = emptyList(),
            goals = listOf(
                SavingsGoal(
                    id = FinanceDefaults.PRIMARY_GOAL_ID,
                    name = "Marriage",
                    targetAmount = 5_000.0,
                    savedAmount = 1_000.0,
                    isPrimary = true,
                    targetAmountMinor = 500_000L,
                    savedAmountMinor = 100_000L
                )
            ),
            debt = FinanceDefaults.debtTracker(now = 0L),
            bankMessageSettings = FinanceDefaults.bankMessageParserSettings(),
            reminderSettings = FinanceDefaults.reminderSettings(now = 0L)
        )

        val savingsBalance = data.latestSavingsBalanceStatus
        val dashboard = FinanceCalculator.dashboard(data, yearMonth = "2026-05", now = 0L)

        assertNotNull(savingsBalance)
        assertEquals(200_000L, savingsBalance?.amountMinor)
        assertEquals(0.2, dashboard.primaryGoalProgress, 0.001)
    }
}
