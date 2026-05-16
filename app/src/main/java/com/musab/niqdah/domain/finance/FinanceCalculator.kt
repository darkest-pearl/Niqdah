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

        val categorySpendingBreakdowns = categorySpendingBreakdowns(data, yearMonth)
        val categorySpending = categorySpendingBreakdowns.map { breakdown ->
            CategorySpend(
                category = breakdown.category,
                spent = breakdown.spent,
                remaining = breakdown.remaining,
                isOverspent = breakdown.isOverspent
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
            categorySpendingBreakdowns = categorySpendingBreakdowns,
            overspendingAlerts = overspendingAlerts,
            healthSummary = healthSummary,
            snapshot = snapshot,
            disciplineStatus = disciplineStatus.copy(safeToSpendAmount = minorUnitsToMajor(remainingSafeToSpendMinor))
        )
    }

    fun categorySpendingBreakdowns(data: FinanceData, yearMonth: String): List<CategorySpendingBreakdown> {
        val monthTransactions = data.transactions.filter { it.yearMonth == yearMonth }
        return data.categories
            .filter { it.isExpenseBudgetCategory() }
            .map { category ->
                val transactions = monthTransactions.filter { it.categoryId == category.id }
                val necessaryMinor = transactions
                    .filter { it.qualityBucket(category) == NecessityLevel.NECESSARY }
                    .sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }
                val optionalMinor = transactions
                    .filter { it.qualityBucket(category) == NecessityLevel.OPTIONAL }
                    .sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }
                val avoidMinor = transactions
                    .filter { it.qualityBucket(category) == NecessityLevel.AVOID }
                    .sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }
                val spentMinor = necessaryMinor + optionalMinor + avoidMinor
                val budgetMinor = effectiveMinorUnits(category.monthlyBudgetMinor, category.monthlyBudget)
                val rawProgress = if (budgetMinor <= 0L) 0.0 else spentMinor.toDouble() / budgetMinor.toDouble()
                CategorySpendingBreakdown(
                    category = category,
                    budget = minorUnitsToMajor(budgetMinor),
                    spent = minorUnitsToMajor(spentMinor),
                    necessarySpent = minorUnitsToMajor(necessaryMinor),
                    optionalSpent = minorUnitsToMajor(optionalMinor),
                    avoidSpent = minorUnitsToMajor(avoidMinor),
                    remaining = minorUnitsToMajor(budgetMinor - spentMinor),
                    isOverspent = budgetMinor > 0L && spentMinor > budgetMinor,
                    overBudgetAmount = minorUnitsToMajor(max(0L, spentMinor - budgetMinor)),
                    rawProgress = rawProgress,
                    visualProgress = rawProgress.coerceIn(0.0, 1.0),
                    budgetMinor = budgetMinor,
                    spentMinor = spentMinor,
                    necessaryMinor = necessaryMinor,
                    optionalMinor = optionalMinor,
                    avoidMinor = avoidMinor
                )
            }
    }

    private fun ratio(value: Double, target: Double): Double =
        if (target <= 0.0) 0.0 else (value / target).coerceIn(0.0, 1.0)

    private fun ratio(valueMinor: Long, targetMinor: Long): Double =
        if (targetMinor <= 0L) 0.0 else (valueMinor.toDouble() / targetMinor.toDouble()).coerceIn(0.0, 1.0)

    private fun BudgetCategory.isExpenseBudgetCategory(): Boolean =
        type != CategoryType.SAVINGS && type != CategoryType.DEBT

    private fun ExpenseTransaction.qualityBucket(category: BudgetCategory): NecessityLevel =
        when {
            category.id == FinanceDefaults.AVOID_CATEGORY_ID -> NecessityLevel.AVOID
            necessity == NecessityLevel.AVOID -> NecessityLevel.AVOID
            necessity == NecessityLevel.OPTIONAL -> NecessityLevel.OPTIONAL
            else -> NecessityLevel.NECESSARY
        }

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
