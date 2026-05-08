package com.musab.niqdah.domain.finance

import kotlin.math.max

object FinanceCalculator {
    fun dashboard(data: FinanceData, yearMonth: String, now: Long = System.currentTimeMillis()): DashboardMetrics {
        val monthTransactions = data.transactions.filter { it.yearMonth == yearMonth }
        val categoryById = data.categories.associateBy { it.id }
        val totalIncome = data.profile.salary + data.profile.extraIncome
        val totalSpent = monthTransactions.sumOf { it.amount }

        val categorySpending = data.categories.map { category ->
            val spent = monthTransactions
                .filter { it.categoryId == category.id }
                .sumOf { it.amount }
            CategorySpend(
                category = category,
                spent = spent,
                remaining = category.monthlyBudget - spent,
                isOverspent = spent > category.monthlyBudget
            )
        }

        val fixedReserveRemaining = categorySpending
            .filter { it.category.type == CategoryType.FIXED }
            .sumOf { max(0.0, it.remaining) }

        val remainingSafeToSpend = totalIncome -
            totalSpent -
            fixedReserveRemaining -
            data.profile.monthlySavingsTarget -
            data.debt.monthlyAutoReduction

        val marriageGoal = data.goals.firstOrNull { it.id == FinanceDefaults.MARRIAGE_GOAL_ID }
        val marriageSaved = marriageGoal?.savedAmount ?: 0.0
        val marriageTarget = marriageGoal?.targetAmount ?: 13600.0
        val savingsThisMonth = monthTransactions
            .filter { transaction -> categoryById[transaction.categoryId]?.type == CategoryType.SAVINGS }
            .sumOf { it.amount }

        val debtPaid = max(0.0, data.debt.startingAmount - data.debt.remainingAmount)
        val debtProgress = ratio(debtPaid, data.debt.startingAmount)
        val overspendingAlerts = categorySpending.filter { it.isOverspent }
        val healthSummary = buildHealthSummary(
            safeToSpend = remainingSafeToSpend,
            overspendingAlerts = overspendingAlerts,
            savingsProgress = ratio(savingsThisMonth, data.profile.monthlySavingsTarget)
        )

        val snapshot = MonthlySnapshot(
            yearMonth = yearMonth,
            totalIncome = totalIncome,
            totalSpent = totalSpent,
            remainingSafeToSpend = remainingSafeToSpend,
            marriageFundSaved = marriageSaved,
            marriageFundTarget = marriageTarget,
            debtRemaining = data.debt.remainingAmount,
            debtStarting = data.debt.startingAmount,
            healthSummary = healthSummary,
            generatedAtMillis = now
        )

        return DashboardMetrics(
            totalMonthlyIncome = totalIncome,
            totalSpent = totalSpent,
            fixedReserveRemaining = fixedReserveRemaining,
            remainingSafeToSpend = remainingSafeToSpend,
            marriageFundProgress = ratio(marriageSaved, marriageTarget),
            savingsTargetProgress = ratio(savingsThisMonth, data.profile.monthlySavingsTarget),
            debtProgress = debtProgress,
            categorySpending = categorySpending,
            overspendingAlerts = overspendingAlerts,
            healthSummary = healthSummary,
            snapshot = snapshot
        )
    }

    private fun ratio(value: Double, target: Double): Double =
        if (target <= 0.0) 0.0 else (value / target).coerceIn(0.0, 1.0)

    private fun buildHealthSummary(
        safeToSpend: Double,
        overspendingAlerts: List<CategorySpend>,
        savingsProgress: Double
    ): String = when {
        safeToSpend < 0.0 ->
            "You are over the safe-to-spend line. Pause optional purchases and protect the essentials."
        overspendingAlerts.isNotEmpty() ->
            "One or more categories are above plan. Trim optional spending before it leaks into savings."
        savingsProgress < 0.5 ->
            "Spending is controlled, but the savings target needs attention this month."
        else ->
            "Your plan is steady. Keep savings and debt payments moving before optional spending."
    }
}
