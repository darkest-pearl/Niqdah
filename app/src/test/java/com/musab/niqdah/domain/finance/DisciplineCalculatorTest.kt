package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DisciplineCalculatorTest {
    @Test
    fun savingsShortfallUsesRemainingTargetOnly() {
        assertEquals(500.0, DisciplineCalculator.savingsShortfall(1700.0, 1200.0), 0.001)
        assertEquals(0.0, DisciplineCalculator.savingsShortfall(1700.0, 1900.0), 0.001)
    }

    @Test
    fun categoryWarningsDetectSeventyFiveOneHundredAndOverBudget() {
        val food = BudgetCategory(
            id = "food",
            name = "Food/transport",
            monthlyBudget = 800.0,
            type = CategoryType.VARIABLE
        )
        val clothing = BudgetCategory(
            id = "clothing",
            name = "Clothing",
            monthlyBudget = 200.0,
            type = CategoryType.VARIABLE
        )
        val personal = BudgetCategory(
            id = "personal",
            name = "Personal",
            monthlyBudget = 400.0,
            type = CategoryType.VARIABLE
        )

        val warnings = DisciplineCalculator.categoryWarnings(
            listOf(
                CategorySpend(food, spent = 600.0, remaining = 200.0, isOverspent = false),
                CategorySpend(clothing, spent = 200.0, remaining = 0.0, isOverspent = false),
                CategorySpend(personal, spent = 500.0, remaining = -100.0, isOverspent = true)
            )
        )

        assertEquals(3, warnings.size)
        assertTrue(warnings.any { it.category.id == "food" && it.level == CategoryBudgetWarningLevel.NEAR_LIMIT })
        assertTrue(warnings.any { it.category.id == "clothing" && it.level == CategoryBudgetWarningLevel.AT_LIMIT })
        assertTrue(warnings.any { it.category.id == "personal" && it.level == CategoryBudgetWarningLevel.OVER_LIMIT })
    }

    @Test
    fun safeToSpendSubtractsSavingsTargetAndFixedReserve() {
        val occurredAt = FinanceDates.parseDateInput("2026-05-10") ?: error("Invalid test date")
        val data = FinanceData.empty(uid = "uid").copy(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L),
            categories = FinanceDefaults.budgetCategories(now = 0L),
            transactions = listOf(
                ExpenseTransaction(
                    id = "food",
                    categoryId = FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID,
                    amount = 500.0,
                    currency = "AED",
                    occurredAtMillis = occurredAt,
                    yearMonth = "2026-05"
                )
            ),
            incomeTransactions = emptyList(),
            debt = FinanceDefaults.debtTracker(now = 0L)
        )

        val safeToSpend = DisciplineCalculator.safeToSpend(
            data = data,
            yearMonth = "2026-05",
            savingsTarget = 1700.0
        )

        assertEquals(1500.0, safeToSpend, 0.001)
    }

    @Test
    fun januaryCountdownCalculatesRequiredMonthlySavings() {
        val countdown = DisciplineCalculator.januaryCountdown(
            targetDateInput = "2027-01-01",
            currentSaved = 3400.0,
            targetAmount = 13600.0,
            today = LocalDate.parse("2026-05-10")
        )

        assertEquals(8L, countdown.monthsRemaining)
        assertEquals(1275.0, countdown.requiredMonthlySavings, 0.001)
    }

    @Test
    fun necessaryItemsDueDetectionIncludesPendingItemsDueSoonOnly() {
        val today = LocalDate.parse("2026-05-10")
        val oneTimeDue = FinanceDates.parseDateInput("2026-05-15") ?: error("Invalid test date")
        val items = listOf(
            NecessaryItem(
                id = "monthly-soon",
                title = "Debt payment",
                dueDayOfMonth = 12,
                recurrence = NecessaryItemRecurrence.MONTHLY
            ),
            NecessaryItem(
                id = "monthly-later",
                title = "Rent",
                dueDayOfMonth = 25,
                recurrence = NecessaryItemRecurrence.MONTHLY
            ),
            NecessaryItem(
                id = "one-time-soon",
                title = "Dental",
                dueDateMillis = oneTimeDue,
                recurrence = NecessaryItemRecurrence.ONE_TIME
            ),
            NecessaryItem(
                id = "done",
                title = "Groceries basics",
                dueDayOfMonth = 11,
                recurrence = NecessaryItemRecurrence.MONTHLY,
                status = NecessaryItemStatus.DONE
            )
        )

        val due = DisciplineCalculator.necessaryItemsDueSoon(items, today)

        assertEquals(listOf("monthly-soon", "one-time-soon"), due.map { it.item.id })
    }
}
