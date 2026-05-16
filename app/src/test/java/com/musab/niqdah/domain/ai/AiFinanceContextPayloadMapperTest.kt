package com.musab.niqdah.domain.ai

import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.CategoryType
import com.musab.niqdah.domain.finance.AccountBalanceConfidence
import com.musab.niqdah.domain.finance.AccountKind
import com.musab.niqdah.domain.finance.AccountLedgerEntry
import com.musab.niqdah.domain.finance.AccountLedgerEventType
import com.musab.niqdah.domain.finance.AccountLedgerSource
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.FinanceCalculator
import com.musab.niqdah.domain.finance.FinanceData
import com.musab.niqdah.domain.finance.FinanceDates
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.SalaryCycle
import com.musab.niqdah.domain.finance.SalaryCycleSource
import com.musab.niqdah.domain.finance.SavingsGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFinanceContextPayloadMapperTest {
    @Test
    fun payloadSeparatesExpensesSavingsAndSpendingQuality() {
        val occurredAt = FinanceDates.parseDateInput("2026-05-08") ?: error("Invalid test date")
        val data = FinanceData.empty(uid = "uid").copy(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L).copy(
                salary = 5_000.0,
                onboardingCompleted = true
            ),
            categories = listOf(
                BudgetCategory(
                    id = "food",
                    name = "Food",
                    monthlyBudget = 500.0,
                    type = CategoryType.VARIABLE
                ),
                BudgetCategory(
                    id = FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID,
                    name = "Savings goal",
                    monthlyBudget = 1_000.0,
                    type = CategoryType.SAVINGS
                )
            ),
            transactions = listOf(
                ExpenseTransaction(
                    id = "food-necessary",
                    categoryId = "food",
                    amount = 200.0,
                    necessity = NecessityLevel.NECESSARY,
                    note = "Groceries",
                    occurredAtMillis = occurredAt,
                    yearMonth = "2026-05"
                ),
                ExpenseTransaction(
                    id = "food-avoid",
                    categoryId = "food",
                    amount = 50.0,
                    necessity = NecessityLevel.AVOID,
                    note = "Late snack",
                    occurredAtMillis = occurredAt,
                    yearMonth = "2026-05"
                ),
                ExpenseTransaction(
                    id = "savings-transfer",
                    categoryId = FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID,
                    amount = 1_000.0,
                    necessity = NecessityLevel.NECESSARY,
                    note = "Savings transfer",
                    occurredAtMillis = occurredAt,
                    yearMonth = "2026-05"
                )
            ),
            goals = listOf(
                SavingsGoal(
                    id = FinanceDefaults.PRIMARY_GOAL_ID,
                    name = "Marriage savings",
                    targetAmount = 16_000.0,
                    savedAmount = 2_000.0,
                    isPrimary = true
                )
            ),
            salaryCycles = listOf(
                SalaryCycle(
                    id = "salary-cycle-2026-05",
                    userId = "uid",
                    cycleMonth = "2026-05",
                    salaryDepositAmountMinor = 5_000_00L,
                    openingDailyUseBalanceMinor = 5_200_00L,
                    salaryDepositDateMillis = occurredAt,
                    currency = "AED",
                    source = SalaryCycleSource.SMS,
                    isOpeningBalanceConfirmed = true
                )
            ),
            accountLedgerEntries = listOf(
                AccountLedgerEntry(
                    id = "daily-balance",
                    accountKind = AccountKind.DAILY_USE,
                    eventType = AccountLedgerEventType.BALANCE_CONFIRMED_SMS,
                    amountMinor = 0L,
                    balanceAfterMinor = 4_000_00L,
                    currency = "AED",
                    confidence = AccountBalanceConfidence.CONFIRMED,
                    source = AccountLedgerSource.SMS,
                    createdAtMillis = occurredAt
                )
            )
        )
        val dashboard = FinanceCalculator.dashboard(data, yearMonth = "2026-05", now = 0L)

        val payload = AiFinanceContextPayloadMapper.toPayload(
            AiFinanceContext(
                financeData = data,
                currentMonthSnapshot = dashboard.snapshot
            )
        )
        val recentExpenses = payload["recentExpenses"] as List<*>
        val savingsContributions = payload["savingsContributions"] as List<*>
        val spendingQuality = payload["spendingQualityByCategory"] as List<*>
        val foodQuality = spendingQuality.single() as Map<*, *>
        val cashProtection = payload["cashProtection"] as Map<*, *>

        assertEquals(2, recentExpenses.size)
        assertEquals(1, savingsContributions.size)
        assertFalse(recentExpenses.any { (it as Map<*, *>)["categoryName"] == "Savings goal" })
        assertEquals("Food", foodQuality["categoryName"])
        assertEquals(200.0, foodQuality["necessarySpent"])
        assertEquals(50.0, foodQuality["avoidSpent"])
        assertTrue(payload.containsKey("goalProgress"))
        assertTrue(payload.containsKey("accountBalances"))
        assertEquals("HEALTHY", cashProtection["riskLevel"])
        assertTrue(cashProtection.containsKey("unpaidProtectedObligations"))
        assertTrue(cashProtection.containsKey("savingsTransferFollowUp"))
    }
}
