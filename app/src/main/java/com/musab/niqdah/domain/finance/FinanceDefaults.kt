package com.musab.niqdah.domain.finance

object FinanceDefaults {
    const val MARRIAGE_GOAL_ID = "marriage_fund"
    const val DEFAULT_CURRENCY = "AED"
    const val UNCATEGORIZED_CATEGORY_ID = "uncategorized"
    const val FOOD_TRANSPORT_CATEGORY_ID = "food_transport"
    const val RENT_CATEGORY_ID = "rent"
    const val MEDICAL_CATEGORY_ID = "medical"
    const val CLOTHING_CATEGORY_ID = "clothing"
    const val FIANCEE_CATEGORY_ID = "fiancee"
    const val FAMILY_GIFTS_CATEGORY_ID = "family_gifts_expense"
    const val AVOID_CATEGORY_ID = "avoid"
    const val MARRIAGE_SAVINGS_CATEGORY_ID = "marriage_savings"

    val DEFAULT_DEBIT_KEYWORDS = listOf(
        "debited",
        "debit",
        "spent",
        "purchase",
        "pos",
        "paid",
        "withdrawn",
        "charged"
    )

    val DEFAULT_CREDIT_KEYWORDS = listOf(
        "credited",
        "credit",
        "received",
        "salary",
        "refund",
        "deposit"
    )

    val DEFAULT_SAVINGS_TRANSFER_KEYWORDS = listOf(
        "transfer to savings",
        "saving account",
        "savings account",
        "goal account",
        "marriage savings",
        "saved to"
    )

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
                id = UNCATEGORIZED_CATEGORY_ID,
                name = "Uncategorized",
                monthlyBudget = 0.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = RENT_CATEGORY_ID,
                name = "Rent",
                monthlyBudget = 1300.0,
                type = CategoryType.FIXED,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = FOOD_TRANSPORT_CATEGORY_ID,
                name = "Food/transport",
                monthlyBudget = 800.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = MEDICAL_CATEGORY_ID,
                name = "Medical",
                monthlyBudget = 250.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = CLOTHING_CATEGORY_ID,
                name = "Clothing",
                monthlyBudget = 200.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = FIANCEE_CATEGORY_ID,
                name = "Fiancée",
                monthlyBudget = 300.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = FAMILY_GIFTS_CATEGORY_ID,
                name = "Family gifts",
                monthlyBudget = 200.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = AVOID_CATEGORY_ID,
                name = "Avoid",
                monthlyBudget = 0.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = MARRIAGE_SAVINGS_CATEGORY_ID,
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

    fun bankMessageParserSettings(): BankMessageParserSettings =
        BankMessageParserSettings(
            dailyUseSource = BankMessageSourceSettings(senderName = "", isEnabled = true),
            savingsSource = BankMessageSourceSettings(senderName = "", isEnabled = true),
            isAutomaticSmsImportEnabled = false,
            requireReviewBeforeSaving = true,
            debitKeywords = DEFAULT_DEBIT_KEYWORDS,
            creditKeywords = DEFAULT_CREDIT_KEYWORDS,
            savingsTransferKeywords = DEFAULT_SAVINGS_TRANSFER_KEYWORDS
        )
}
