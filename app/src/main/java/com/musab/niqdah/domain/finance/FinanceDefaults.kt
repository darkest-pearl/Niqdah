package com.musab.niqdah.domain.finance

object FinanceDefaults {
    const val MARRIAGE_GOAL_ID = "marriage_fund"
    const val DEFAULT_CURRENCY = "AED"

    fun userProfile(uid: String, now: Long = System.currentTimeMillis()): UserProfile =
        UserProfile(
            uid = uid,
            currency = DEFAULT_CURRENCY,
            salary = 5000.0,
            extraIncome = 500.0,
            monthlySavingsTarget = 1700.0,
            createdAtMillis = now,
            updatedAtMillis = now
        )

    fun budgetCategories(now: Long = System.currentTimeMillis()): List<BudgetCategory> =
        listOf(
            BudgetCategory(
                id = "rent",
                name = "Rent",
                monthlyBudget = 1300.0,
                type = CategoryType.FIXED,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = "food_transport",
                name = "Food/transport",
                monthlyBudget = 800.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = "marriage_savings",
                name = "Marriage savings",
                monthlyBudget = 1700.0,
                type = CategoryType.SAVINGS,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = "debt_payment",
                name = "Debt payment",
                monthlyBudget = 500.0,
                type = CategoryType.DEBT,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = "personal",
                name = "Personal",
                monthlyBudget = 400.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )

    fun savingsGoals(now: Long = System.currentTimeMillis()): List<SavingsGoal> =
        listOf(
            SavingsGoal(
                id = MARRIAGE_GOAL_ID,
                name = "Marriage fund",
                targetAmount = 13600.0,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            SavingsGoal(
                id = "travel_visa",
                name = "Travel/Visa",
                targetAmount = 3600.0,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            SavingsGoal(
                id = "wedding",
                name = "Wedding",
                targetAmount = 4000.0,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            SavingsGoal(
                id = "bride_package",
                name = "Bride package",
                targetAmount = 4000.0,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            SavingsGoal(
                id = "family_gifts",
                name = "Family gifts",
                targetAmount = 800.0,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            SavingsGoal(
                id = "emergency_reserve",
                name = "Emergency reserve",
                targetAmount = 1200.0,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )

    fun debtTracker(now: Long = System.currentTimeMillis()): DebtTracker =
        DebtTracker(
            startingAmount = 7000.0,
            remainingAmount = 7000.0,
            monthlyAutoReduction = 500.0,
            updatedAtMillis = now
        )
}
