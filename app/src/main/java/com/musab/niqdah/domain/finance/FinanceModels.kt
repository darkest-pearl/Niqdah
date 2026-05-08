package com.musab.niqdah.domain.finance

data class UserProfile(
    val uid: String,
    val currency: String = "AED",
    val salary: Double = 5000.0,
    val extraIncome: Double = 500.0,
    val monthlySavingsTarget: Double = 1700.0,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class BudgetCategory(
    val id: String,
    val name: String,
    val monthlyBudget: Double,
    val type: CategoryType,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

enum class CategoryType(val label: String) {
    FIXED("Fixed"),
    VARIABLE("Variable"),
    SAVINGS("Savings"),
    DEBT("Debt")
}

data class ExpenseTransaction(
    val id: String,
    val categoryId: String,
    val amount: Double,
    val note: String = "",
    val necessity: NecessityLevel = NecessityLevel.NECESSARY,
    val occurredAtMillis: Long,
    val yearMonth: String,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

enum class NecessityLevel(val label: String) {
    NECESSARY("Necessary"),
    OPTIONAL("Optional"),
    AVOID("Avoid")
}

data class SavingsGoal(
    val id: String,
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class DebtTracker(
    val startingAmount: Double = 7000.0,
    val remainingAmount: Double = 7000.0,
    val monthlyAutoReduction: Double = 500.0,
    val updatedAtMillis: Long = 0L
)

data class MonthlySnapshot(
    val yearMonth: String,
    val totalIncome: Double,
    val totalSpent: Double,
    val remainingSafeToSpend: Double,
    val marriageFundSaved: Double,
    val marriageFundTarget: Double,
    val debtRemaining: Double,
    val debtStarting: Double,
    val healthSummary: String,
    val generatedAtMillis: Long
)

data class FinanceData(
    val profile: UserProfile,
    val categories: List<BudgetCategory>,
    val transactions: List<ExpenseTransaction>,
    val goals: List<SavingsGoal>,
    val debt: DebtTracker
) {
    companion object {
        fun empty(uid: String = ""): FinanceData =
            FinanceData(
                profile = FinanceDefaults.userProfile(uid),
                categories = emptyList(),
                transactions = emptyList(),
                goals = emptyList(),
                debt = FinanceDefaults.debtTracker()
            )
    }
}

data class CategorySpend(
    val category: BudgetCategory,
    val spent: Double,
    val remaining: Double,
    val isOverspent: Boolean
)

data class DashboardMetrics(
    val totalMonthlyIncome: Double,
    val totalSpent: Double,
    val fixedReserveRemaining: Double,
    val remainingSafeToSpend: Double,
    val marriageFundProgress: Double,
    val savingsTargetProgress: Double,
    val debtProgress: Double,
    val categorySpending: List<CategorySpend>,
    val overspendingAlerts: List<CategorySpend>,
    val healthSummary: String,
    val snapshot: MonthlySnapshot
)
