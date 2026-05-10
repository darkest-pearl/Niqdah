package com.musab.niqdah.domain.finance

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

object DisciplineCalculator {
    private const val DUE_SOON_DAYS = 7L

    fun status(
        data: FinanceData,
        yearMonth: String,
        nowMillis: Long = System.currentTimeMillis()
    ): DisciplineStatus {
        val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val savingsTarget = savingsTargetStatus(data, yearMonth)
        val categorySpending = categorySpending(data, yearMonth)
        val primaryGoal = data.primaryGoal
        val currentSaved = primaryGoal?.savedAmount ?: 0.0
        val targetAmount = data.reminderSettings.januaryFundTargetAmount.takeIf { it > 0.0 }
            ?: primaryGoal?.targetAmount
            ?: 0.0

        return DisciplineStatus(
            savingsTarget = savingsTarget,
            categoryWarnings = categoryWarnings(categorySpending),
            necessaryItemsDueSoon = necessaryItemsDueSoon(data.necessaryItems, today),
            avoidSpendingThisMonth = avoidSpendingThisMonth(data, yearMonth),
            safeToSpendAmount = safeToSpend(data, yearMonth, savingsTarget.targetAmount),
            januaryCountdown = januaryCountdown(
                targetDateInput = data.reminderSettings.januaryTargetDate,
                goalName = primaryGoal?.name ?: "Savings goal",
                currentSaved = currentSaved,
                targetAmount = targetAmount,
                today = today
            )
        )
    }

    fun savingsTargetStatus(data: FinanceData, yearMonth: String): SavingsTargetStatus {
        val target = data.reminderSettings.monthlySavingsTargetAmount.takeIf { it > 0.0 }
            ?: data.profile.monthlySavingsTarget
        val saved = savingsProgress(data, yearMonth)
        return SavingsTargetStatus(
            targetAmount = target,
            savedThisMonth = saved,
            shortfall = savingsShortfall(target, saved),
            progress = ratio(saved, target)
        )
    }

    fun savingsProgress(data: FinanceData, yearMonth: String): Double {
        val categoryById = data.categories.associateBy { it.id }
        return data.transactions
            .filter { transaction ->
                transaction.yearMonth == yearMonth &&
                    categoryById[transaction.categoryId]?.type == CategoryType.SAVINGS
            }
            .sumOf { it.amount }
    }

    fun savingsShortfall(targetAmount: Double, savedThisMonth: Double): Double =
        max(0.0, targetAmount - savedThisMonth)

    fun categorySpending(data: FinanceData, yearMonth: String): List<CategorySpend> =
        data.categories.map { category ->
            val spent = data.transactions
                .filter { it.yearMonth == yearMonth && it.categoryId == category.id }
                .sumOf { it.amount }
            CategorySpend(
                category = category,
                spent = spent,
                remaining = category.monthlyBudget - spent,
                isOverspent = spent > category.monthlyBudget
            )
        }

    fun categoryWarnings(categorySpending: List<CategorySpend>): List<CategoryBudgetWarning> =
        categorySpending
            .filter { spend ->
                spend.category.monthlyBudget > 0.0 &&
                    spend.category.type == CategoryType.VARIABLE &&
                    spend.spent / spend.category.monthlyBudget >= 0.75
            }
            .map { spend ->
                val percentUsed = spend.spent / spend.category.monthlyBudget
                val level = when {
                    percentUsed > 1.0 -> CategoryBudgetWarningLevel.OVER_LIMIT
                    percentUsed >= 1.0 -> CategoryBudgetWarningLevel.AT_LIMIT
                    else -> CategoryBudgetWarningLevel.NEAR_LIMIT
                }
                CategoryBudgetWarning(
                    category = spend.category,
                    spent = spend.spent,
                    budget = spend.category.monthlyBudget,
                    percentUsed = percentUsed,
                    level = level,
                    message = warningMessage(spend.category.name, level)
                )
            }
            .sortedWith(
                compareByDescending<CategoryBudgetWarning> { it.level.ordinal }
                    .thenByDescending { it.percentUsed }
            )

    fun safeToSpend(data: FinanceData, yearMonth: String, savingsTarget: Double): Double {
        val categoryById = data.categories.associateBy { it.id }
        val monthTransactions = data.transactions.filter { it.yearMonth == yearMonth }
        val monthIncomeTransactions = data.incomeTransactions.filter { it.yearMonth == yearMonth }
        val totalIncome = data.profile.salary + data.profile.extraIncome + monthIncomeTransactions.sumOf { it.amount }
        val totalSpent = monthTransactions
            .filter { transaction -> categoryById[transaction.categoryId]?.type != CategoryType.SAVINGS }
            .sumOf { it.amount }
        val fixedReserveRemaining = data.categories
            .filter { it.type == CategoryType.FIXED }
            .sumOf { category ->
                val spent = monthTransactions
                    .filter { it.categoryId == category.id }
                    .sumOf { it.amount }
                max(0.0, category.monthlyBudget - spent)
            }

        return totalIncome - totalSpent - fixedReserveRemaining - savingsTarget - data.debt.monthlyAutoReduction
    }

    fun januaryCountdown(
        targetDateInput: String,
        goalName: String = "Savings goal",
        currentSaved: Double,
        targetAmount: Double,
        today: LocalDate
    ): JanuaryCountdownStatus {
        val targetDate = runCatching { LocalDate.parse(targetDateInput) }
            .getOrElse { today }
        val daysRemaining = max(0L, ChronoUnit.DAYS.between(today, targetDate))
        val monthsRemaining = monthsRemainingForSavings(today, targetDate)
        val remainingAmount = max(0.0, targetAmount - currentSaved)
        val requiredMonthlySavings = if (monthsRemaining <= 0L) {
            remainingAmount
        } else {
            remainingAmount / monthsRemaining
        }

        return JanuaryCountdownStatus(
            targetDate = targetDate.toString(),
            goalName = goalName.ifBlank { "Savings goal" },
            daysRemaining = daysRemaining,
            monthsRemaining = monthsRemaining,
            currentSaved = currentSaved,
            targetAmount = targetAmount,
            requiredMonthlySavings = requiredMonthlySavings
        )
    }

    fun necessaryItemsDueSoon(
        items: List<NecessaryItem>,
        today: LocalDate,
        daysAhead: Long = DUE_SOON_DAYS
    ): List<NecessaryItemDue> =
        items
            .filter { it.status == NecessaryItemStatus.PENDING }
            .mapNotNull { item ->
                val dueDate = nextDueDate(item, today) ?: return@mapNotNull null
                val daysUntilDue = ChronoUnit.DAYS.between(today, dueDate)
                if (daysUntilDue in 0..daysAhead) {
                    NecessaryItemDue(
                        item = item,
                        dueDateMillis = dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        daysUntilDue = daysUntilDue
                    )
                } else {
                    null
                }
            }
            .sortedWith(compareBy<NecessaryItemDue> { it.daysUntilDue }.thenBy { it.item.title })

    fun nextDueDate(item: NecessaryItem, today: LocalDate): LocalDate? =
        when (item.recurrence) {
            NecessaryItemRecurrence.ONE_TIME ->
                item.dueDateMillis?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                }?.takeIf { !it.isBefore(today) }
            NecessaryItemRecurrence.MONTHLY -> {
                val currentMonth = YearMonth.from(today)
                val currentDueDate = dueDateInMonth(currentMonth, item.dueDayOfMonth)
                if (!currentDueDate.isBefore(today)) {
                    currentDueDate
                } else {
                    dueDateInMonth(currentMonth.plusMonths(1), item.dueDayOfMonth)
                }
            }
        }

    private fun avoidSpendingThisMonth(data: FinanceData, yearMonth: String): Double =
        data.transactions
            .filter { transaction ->
                transaction.yearMonth == yearMonth &&
                    (transaction.necessity == NecessityLevel.AVOID ||
                        transaction.categoryId == FinanceDefaults.AVOID_CATEGORY_ID)
            }
            .sumOf { it.amount }

    private fun warningMessage(categoryName: String, level: CategoryBudgetWarningLevel): String =
        when (level) {
            CategoryBudgetWarningLevel.NEAR_LIMIT ->
                "$categoryName is at 75% of its monthly budget."
            CategoryBudgetWarningLevel.AT_LIMIT ->
                "$categoryName reached its monthly budget."
            CategoryBudgetWarningLevel.OVER_LIMIT ->
                "You passed your $categoryName budget. Review before buying more."
        }

    private fun monthsRemainingForSavings(today: LocalDate, targetDate: LocalDate): Long {
        if (!today.isBefore(targetDate)) return 0L
        val currentMonth = YearMonth.from(today)
        val lastSavingMonth = YearMonth.from(targetDate.minusDays(1))
        return (ChronoUnit.MONTHS.between(currentMonth, lastSavingMonth) + 1L).coerceAtLeast(1L)
    }

    private fun dueDateInMonth(month: YearMonth, dayOfMonth: Int): LocalDate =
        month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

    private fun ratio(value: Double, target: Double): Double =
        if (target <= 0.0) 0.0 else (value / target).coerceIn(0.0, 1.0)
}
