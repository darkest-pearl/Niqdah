package com.musab.niqdah.domain.finance

import kotlin.math.max

object FinanceCalculator {
    fun dashboard(data: FinanceData, yearMonth: String, now: Long = System.currentTimeMillis()): DashboardMetrics {
        val monthTransactions = data.transactions.filter { it.yearMonth == yearMonth }
        val monthIncomeTransactions = data.incomeTransactions.filter { it.yearMonth == yearMonth }
        val categoryById = data.categories.associateBy { it.id }
        val disciplineStatus = DisciplineCalculator.status(data, yearMonth, now)
        val monthlySavingsTargetMinor = majorToMinorUnits(disciplineStatus.savingsTarget.targetAmount)
        val totalIncomeMinor = effectiveMinorUnits(data.profile.salaryMinor, data.profile.salary) +
            effectiveMinorUnits(data.profile.extraIncomeMinor, data.profile.extraIncome) +
            monthIncomeTransactions.sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }
        val totalSpentMinor = monthTransactions
            .filter { transaction -> categoryById[transaction.categoryId]?.type != CategoryType.SAVINGS }
            .sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }

        val categorySpending = data.categories.map { category ->
            val spentMinor = monthTransactions
                .filter { it.categoryId == category.id }
                .sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }
            val budgetMinor = effectiveMinorUnits(category.monthlyBudgetMinor, category.monthlyBudget)
            CategorySpend(
                category = category,
                spent = minorUnitsToMajor(spentMinor),
                remaining = minorUnitsToMajor(budgetMinor - spentMinor),
                isOverspent = spentMinor > budgetMinor
            )
        }

        val fixedReserveRemainingMinor = data.categories
            .filter { it.type == CategoryType.FIXED }
            .sumOf { category ->
                val spentMinor = monthTransactions
                    .filter { it.categoryId == category.id }
                    .sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }
                val budgetMinor = effectiveMinorUnits(category.monthlyBudgetMinor, category.monthlyBudget)
                max(0L, budgetMinor - spentMinor)
            }

        val remainingSafeToSpendMinor = totalIncomeMinor -
            totalSpentMinor -
            fixedReserveRemainingMinor -
            monthlySavingsTargetMinor -
            effectiveMinorUnits(data.debt.monthlyAutoReductionMinor, data.debt.monthlyAutoReduction)

        val primaryGoal = data.primaryGoal
        val primaryGoalName = primaryGoal?.name ?: "Savings goal"
        val primarySavedMinor = primaryGoal?.let { effectiveMinorUnits(it.savedAmountMinor, it.savedAmount) } ?: 0L
        val primaryTargetMinor = data.reminderSettings.januaryFundTargetAmountMinor.takeIf { it > 0L }
            ?: primaryGoal?.let { effectiveMinorUnits(it.targetAmountMinor, it.targetAmount) }
            ?: 0L
        val savingsThisMonthMinor = monthTransactions
            .filter { transaction -> categoryById[transaction.categoryId]?.type == CategoryType.SAVINGS }
            .sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }

        val debtStartingMinor = effectiveMinorUnits(data.debt.startingAmountMinor, data.debt.startingAmount)
        val debtRemainingMinor = effectiveMinorUnits(data.debt.remainingAmountMinor, data.debt.remainingAmount)
        val debtPaidMinor = max(0L, debtStartingMinor - debtRemainingMinor)
        val debtProgress = ratio(debtPaidMinor, debtStartingMinor)
        val overspendingAlerts = categorySpending.filter { it.isOverspent }
        val healthSummary = buildHealthSummary(
            safeToSpend = minorUnitsToMajor(remainingSafeToSpendMinor),
            overspendingAlerts = overspendingAlerts,
            savingsProgress = ratio(savingsThisMonthMinor, monthlySavingsTargetMinor)
        )

        val snapshot = MonthlySnapshot(
            yearMonth = yearMonth,
            totalIncome = minorUnitsToMajor(totalIncomeMinor),
            totalSpent = minorUnitsToMajor(totalSpentMinor),
            remainingSafeToSpend = minorUnitsToMajor(remainingSafeToSpendMinor),
            marriageFundSaved = minorUnitsToMajor(primarySavedMinor),
            marriageFundTarget = minorUnitsToMajor(primaryTargetMinor),
            debtRemaining = minorUnitsToMajor(debtRemainingMinor),
            debtStarting = minorUnitsToMajor(debtStartingMinor),
            healthSummary = healthSummary,
            generatedAtMillis = now
        )

        return DashboardMetrics(
            totalMonthlyIncome = minorUnitsToMajor(totalIncomeMinor),
            totalSpent = minorUnitsToMajor(totalSpentMinor),
            fixedReserveRemaining = minorUnitsToMajor(fixedReserveRemainingMinor),
            remainingSafeToSpend = minorUnitsToMajor(remainingSafeToSpendMinor),
            marriageFundProgress = ratio(primarySavedMinor, primaryTargetMinor),
            primaryGoalName = primaryGoalName,
            primaryGoalProgress = ratio(primarySavedMinor, primaryTargetMinor),
            savingsTargetProgress = ratio(savingsThisMonthMinor, monthlySavingsTargetMinor),
            debtProgress = debtProgress,
            categorySpending = categorySpending,
            overspendingAlerts = overspendingAlerts,
            healthSummary = healthSummary,
            snapshot = snapshot,
            disciplineStatus = disciplineStatus.copy(safeToSpendAmount = minorUnitsToMajor(remainingSafeToSpendMinor))
        )
    }

    private fun ratio(value: Double, target: Double): Double =
        if (target <= 0.0) 0.0 else (value / target).coerceIn(0.0, 1.0)

    private fun ratio(valueMinor: Long, targetMinor: Long): Double =
        if (targetMinor <= 0L) 0.0 else (valueMinor.toDouble() / targetMinor.toDouble()).coerceIn(0.0, 1.0)

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
