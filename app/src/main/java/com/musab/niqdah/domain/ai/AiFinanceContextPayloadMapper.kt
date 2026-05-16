package com.musab.niqdah.domain.ai

import com.musab.niqdah.domain.finance.AccountBalanceStatus
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.CategorySpendingBreakdown
import com.musab.niqdah.domain.finance.CategoryType
import com.musab.niqdah.domain.finance.DepositType
import com.musab.niqdah.domain.finance.DisciplineCalculator
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.FinanceCalculator
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.IncomeTransaction
import com.musab.niqdah.domain.finance.NecessaryItemDue
import com.musab.niqdah.domain.finance.SavingsGoal
import com.musab.niqdah.domain.finance.effectiveMinorUnits
import com.musab.niqdah.domain.finance.formatMinorUnits

object AiFinanceContextPayloadMapper {
    fun toPayload(context: AiFinanceContext): Map<String, Any?> {
        val data = context.financeData
        val categoryById = data.categories.associateBy { it.id }
        val dashboard = FinanceCalculator.dashboard(
            data = data,
            yearMonth = context.currentMonthSnapshot.yearMonth
        )
        val disciplineStatus = DisciplineCalculator.status(
            data = data,
            yearMonth = context.currentMonthSnapshot.yearMonth
        )
        val monthDeposits = data.incomeTransactions
            .filter { it.yearMonth == context.currentMonthSnapshot.yearMonth }
        val salaryDeposits = monthDeposits.filter { it.depositType == DepositType.SALARY }
        val monthTransactions = data.transactions
            .filter { it.yearMonth == context.currentMonthSnapshot.yearMonth }
        val savingsContributions = monthTransactions
            .filter { categoryById[it.categoryId]?.type == CategoryType.SAVINGS }
        val debtPayments = monthTransactions
            .filter { categoryById[it.categoryId]?.type == CategoryType.DEBT }
        val expenses = monthTransactions
            .filter { transaction ->
                val type = categoryById[transaction.categoryId]?.type
                type != CategoryType.SAVINGS && type != CategoryType.DEBT
            }

        return mapOf(
            "profile" to mapOf(
                "currency" to data.profile.currency,
                "salary" to data.profile.salary,
                "extraIncome" to data.profile.extraIncome,
                "monthlySavingsTarget" to data.profile.monthlySavingsTarget
            ),
            "accountBalances" to mapOf(
                "dailyUse" to data.latestDailyUseBalanceStatus?.toPayload(),
                "savings" to data.latestSavingsBalanceStatus?.toPayload()
            ),
            "salaryAndDepositsThisMonth" to mapOf(
                "salaryRecorded" to salaryDeposits.isNotEmpty(),
                "depositCount" to monthDeposits.size,
                "totalDeposits" to monthDeposits.sumOf { it.amount },
                "items" to monthDeposits.map { it.toPayload() }
            ),
            "debt" to mapOf(
                "startingAmount" to data.debt.startingAmount,
                "remainingAmount" to data.debt.remainingAmount,
                "monthlyAutoReduction" to data.debt.monthlyAutoReduction,
                "paymentsThisMonth" to debtPayments.sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }
            ),
            "currentMonthSnapshot" to mapOf(
                "yearMonth" to context.currentMonthSnapshot.yearMonth,
                "totalIncome" to context.currentMonthSnapshot.totalIncome,
                "totalExpenseSpending" to dashboard.totalSpent,
                "remainingSafeToSpend" to context.currentMonthSnapshot.remainingSafeToSpend,
                "debtRemaining" to context.currentMonthSnapshot.debtRemaining,
                "debtStarting" to context.currentMonthSnapshot.debtStarting,
                "healthSummary" to context.currentMonthSnapshot.healthSummary
            ),
            "expenseCategoryBudgets" to data.categories
                .filter { it.type != CategoryType.SAVINGS && it.type != CategoryType.DEBT }
                .map { it.toPayload() },
            "spendingQualityByCategory" to dashboard.categorySpendingBreakdowns
                .filter { it.spentMinor > 0L || it.budgetMinor > 0L }
                .map { it.toPayload() },
            "savingsGoals" to data.visibleGoals.map { it.toPayload() },
            "goalProgress" to data.primaryGoal?.let { goal ->
                mapOf(
                    "name" to goal.name,
                    "savedAmount" to goal.savedAmount,
                    "targetAmount" to goal.targetAmount,
                    "targetDate" to goal.targetDate,
                    "progress" to dashboard.primaryGoalProgress,
                    "requiredMonthlySavings" to disciplineStatus.januaryCountdown.requiredMonthlySavings
                )
            },
            "disciplineStatus" to mapOf(
                "currentSavingsProgress" to mapOf(
                    "savedThisMonth" to disciplineStatus.savingsTarget.savedThisMonth,
                    "targetAmount" to disciplineStatus.savingsTarget.targetAmount,
                    "shortfall" to disciplineStatus.savingsTarget.shortfall,
                    "progress" to disciplineStatus.savingsTarget.progress
                ),
                "categoryWarnings" to disciplineStatus.categoryWarnings.map { it.toPayload() },
                "necessaryItemsDue" to disciplineStatus.necessaryItemsDueSoon.map { it.toPayload() },
                "avoidSpendingThisMonth" to disciplineStatus.avoidSpendingThisMonth,
                "safeToSpendAmount" to disciplineStatus.safeToSpendAmount,
                "januaryCountdown" to mapOf(
                    "targetDate" to disciplineStatus.januaryCountdown.targetDate,
                    "daysRemaining" to disciplineStatus.januaryCountdown.daysRemaining,
                    "monthsRemaining" to disciplineStatus.januaryCountdown.monthsRemaining,
                    "currentSaved" to disciplineStatus.januaryCountdown.currentSaved,
                    "targetAmount" to disciplineStatus.januaryCountdown.targetAmount,
                    "requiredMonthlySavings" to disciplineStatus.januaryCountdown.requiredMonthlySavings
                )
            ),
            "recentExpenses" to expenses
                .sortedByDescending { it.occurredAtMillis }
                .take(context.recentTransactionLimit)
                .map { it.toPayload(categoryById[it.categoryId]?.name ?: "Uncategorized") },
            "savingsContributions" to savingsContributions
                .sortedByDescending { it.occurredAtMillis }
                .take(context.recentTransactionLimit)
                .map { it.toPayload(categoryById[it.categoryId]?.name ?: "Savings") },
            "debtPayments" to debtPayments
                .sortedByDescending { it.occurredAtMillis }
                .take(context.recentTransactionLimit)
                .map { it.toPayload(categoryById[it.categoryId]?.name ?: "Debt payment") },
            "recentIncomeTransactions" to data.incomeTransactions
                .sortedByDescending { it.occurredAtMillis }
                .take(context.recentTransactionLimit)
                .map { it.toPayload() },
            "accountLedgerSummary" to data.accountLedgerEntries
                .sortedByDescending { it.createdAtMillis }
                .take(8)
                .map {
                    mapOf(
                        "accountKind" to it.accountKind.name,
                        "eventType" to it.eventType.name,
                        "amount" to formatMinorUnits(it.amountMinor, it.currency),
                        "balanceAfter" to it.balanceAfterMinor?.let { balance -> formatMinorUnits(balance, it.currency) },
                        "confidence" to it.confidence.name,
                        "source" to it.source.name,
                        "createdAtMillis" to it.createdAtMillis,
                        "note" to it.note
                    )
                }
        )
    }

    private fun BudgetCategory.toPayload(): Map<String, Any> =
        mapOf(
            "id" to id,
            "name" to name,
            "monthlyBudget" to monthlyBudget,
            "type" to type.label
        )

    private fun SavingsGoal.toPayload(): Map<String, Any> =
        mapOf(
            "id" to id,
            "name" to name,
            "targetAmount" to targetAmount,
            "savedAmount" to savedAmount,
            "targetDate" to targetDate,
            "purpose" to purpose.label,
            "isPrimary" to isPrimary
        )

    private fun CategorySpendingBreakdown.toPayload(): Map<String, Any> =
        mapOf(
            "categoryId" to category.id,
            "categoryName" to category.name,
            "spent" to spent,
            "budget" to budget,
            "necessarySpent" to necessarySpent,
            "optionalSpent" to optionalSpent,
            "avoidSpent" to avoidSpent,
            "rawProgress" to rawProgress,
            "visualProgress" to visualProgress,
            "isOverBudget" to isOverspent,
            "overBudgetAmount" to overBudgetAmount
        )

    private fun com.musab.niqdah.domain.finance.CategoryBudgetWarning.toPayload(): Map<String, Any> =
        mapOf(
            "categoryId" to category.id,
            "categoryName" to category.name,
            "spent" to spent,
            "budget" to budget,
            "percentUsed" to percentUsed,
            "level" to level.name,
            "message" to message
        )

    private fun NecessaryItemDue.toPayload(): Map<String, Any?> =
        mapOf(
            "id" to item.id,
            "title" to item.title,
            "amount" to item.amount,
            "dueDateMillis" to dueDateMillis,
            "daysUntilDue" to daysUntilDue,
            "recurrence" to item.recurrence.label,
            "status" to item.status.label
        )

    private fun AccountBalanceStatus.toPayload(): Map<String, Any> =
        mapOf(
            "accountKind" to accountKind.name,
            "accountSuffix" to accountSuffix,
            "amount" to formatMinorUnits(amountMinor, currency),
            "currency" to currency,
            "confidence" to confidence.name,
            "lastUpdatedMillis" to lastUpdatedMillis,
            "source" to source.name,
            "note" to note
        )

    private fun ExpenseTransaction.toPayload(categoryName: String): Map<String, Any> =
        mapOf(
            "categoryId" to categoryId,
            "categoryName" to categoryName,
            "amount" to amount,
            "note" to note,
            "necessity" to necessity.label,
            "yearMonth" to yearMonth,
            "occurredAtMillis" to occurredAtMillis
        )

    private fun IncomeTransaction.toPayload(): Map<String, Any> =
        mapOf(
            "amount" to amount,
            "currency" to currency,
            "source" to source,
            "note" to note,
            "depositType" to depositType.name,
            "yearMonth" to yearMonth,
            "occurredAtMillis" to occurredAtMillis
        )
}
