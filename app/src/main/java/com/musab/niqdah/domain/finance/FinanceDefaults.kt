package com.musab.niqdah.domain.finance

object FinanceDefaults {
    const val PRIMARY_GOAL_ID = "primary_goal"
    const val SAVINGS_GOAL_CATEGORY_ID = "savings_goal"
    const val DEBT_PAYMENT_CATEGORY_ID = "debt_payment"

    // Legacy IDs are kept so existing users and historical documents continue to load.
    const val MARRIAGE_GOAL_ID = "marriage_fund"
    const val MARRIAGE_SAVINGS_CATEGORY_ID = "marriage_savings"

    const val DEFAULT_CURRENCY = "AED"
    const val UNCATEGORIZED_CATEGORY_ID = "uncategorized"
    const val FOOD_TRANSPORT_CATEGORY_ID = "food_transport"
    const val RENT_CATEGORY_ID = "rent"
    const val MEDICAL_CATEGORY_ID = "medical"
    const val CLOTHING_CATEGORY_ID = "clothing"
    const val FIANCEE_CATEGORY_ID = "fiancee"
    const val FAMILY_GIFTS_CATEGORY_ID = "family_gifts_expense"
    const val AVOID_CATEGORY_ID = "avoid"
    const val DEFAULT_INTERNAL_TRANSFER_REMINDER_MINUTES = 10
    const val DEFAULT_JANUARY_TARGET_DATE = ""
    const val DEFAULT_MARRIAGE_FUND_TARGET = 0.0
    const val DEFAULT_MONTHLY_SAVINGS_TARGET = 0.0

    val DEFAULT_DEBIT_KEYWORDS = listOf(
        "debited",
        "debit",
        "spent",
        "purchase",
        "pos",
        "paid",
        "card transaction",
        "atm withdrawal",
        "deducted",
        "withdrawn",
        "charged"
    )

    val DEFAULT_CREDIT_KEYWORDS = listOf(
        "credited",
        "credit",
        "received",
        "salary",
        "deposited",
        "refund",
        "cash deposit",
        "transfer received",
        "deposit"
    )

    val DEFAULT_SAVINGS_TRANSFER_KEYWORDS = listOf(
        "transferred to savings",
        "transfer to savings",
        "saving account",
        "savings account",
        "moved to savings",
        "goal savings",
        "goal account",
        "reserve account",
        "saved to",
        "deposited to savings"
    )

    fun userProfile(uid: String, now: Long = System.currentTimeMillis()): UserProfile =
        UserProfile(
            uid = uid,
            currency = DEFAULT_CURRENCY,
            salary = 0.0,
            extraIncome = 0.0,
            monthlySavingsTarget = 0.0,
            salaryDayOfMonth = 1,
            onboardingCompleted = false,
            primaryGoalId = "",
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
                monthlyBudget = 0.0,
                type = CategoryType.FIXED,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = FOOD_TRANSPORT_CATEGORY_ID,
                name = "Food/transport",
                monthlyBudget = 0.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = MEDICAL_CATEGORY_ID,
                name = "Medical",
                monthlyBudget = 0.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = CLOTHING_CATEGORY_ID,
                name = "Clothing",
                monthlyBudget = 0.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = "family",
                name = "Family",
                monthlyBudget = 0.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = "subscriptions",
                name = "Subscriptions",
                monthlyBudget = 0.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = "shopping",
                name = "Shopping",
                monthlyBudget = 0.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = "emergency",
                name = "Emergency",
                monthlyBudget = 0.0,
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
                id = SAVINGS_GOAL_CATEGORY_ID,
                name = "Savings goal",
                monthlyBudget = 0.0,
                type = CategoryType.SAVINGS,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = DEBT_PAYMENT_CATEGORY_ID,
                name = "Debt payment",
                monthlyBudget = 0.0,
                type = CategoryType.DEBT,
                createdAtMillis = now,
                updatedAtMillis = now
            ),
            BudgetCategory(
                id = "other",
                name = "Other",
                monthlyBudget = 0.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )

    @Suppress("UNUSED_PARAMETER")
    fun savingsGoals(now: Long = System.currentTimeMillis()): List<SavingsGoal> = emptyList()

    fun debtTracker(now: Long = System.currentTimeMillis()): DebtTracker =
        DebtTracker(
            startingAmount = 0.0,
            remainingAmount = 0.0,
            monthlyAutoReduction = 0.0,
            lenderType = DebtLenderType.OTHER,
            pressureLevel = DebtPressureLevel.FLEXIBLE,
            dueDayOfMonth = null,
            updatedAtMillis = now
        )

    fun bankMessageParserSettings(): BankMessageParserSettings =
        BankMessageParserSettings(
            dailyUseSource = BankMessageSourceSettings(senderName = "", isEnabled = true),
            savingsSource = BankMessageSourceSettings(senderName = "", isEnabled = true),
            isAutomaticSmsImportEnabled = false,
            requireReviewBeforeSaving = true,
            isInternalTransferReminderEnabled = true,
            internalTransferReminderThresholdMinutes = DEFAULT_INTERNAL_TRANSFER_REMINDER_MINUTES,
            debitKeywords = DEFAULT_DEBIT_KEYWORDS,
            creditKeywords = DEFAULT_CREDIT_KEYWORDS,
            savingsTransferKeywords = DEFAULT_SAVINGS_TRANSFER_KEYWORDS
        )

    fun reminderSettings(now: Long = System.currentTimeMillis()): ReminderSettings =
        ReminderSettings(
            isMonthlySavingsReminderEnabled = true,
            monthlySavingsReminderDay = 1,
            monthlySavingsReminderHour = 9,
            monthlySavingsReminderMinute = 0,
            monthlySavingsTargetAmount = 0.0,
            isMissedSavingsReminderEnabled = true,
            missedSavingsCheckDay = 20,
            missedSavingsReminderHour = 19,
            missedSavingsReminderMinute = 0,
            areOverspendingWarningsEnabled = true,
            isAvoidCategoryWarningEnabled = true,
            januaryTargetDate = "",
            januaryFundTargetAmount = 0.0,
            updatedAtMillis = now
        )

    @Suppress("UNUSED_PARAMETER")
    fun necessaryItems(now: Long = System.currentTimeMillis()): List<NecessaryItem> = emptyList()

    fun onboardingFixedExpenseTemplates(): List<FixedExpense> =
        listOf(
            FixedExpense(id = "rent", name = "Rent", amount = 0.0, dueDayOfMonth = 1),
            FixedExpense(id = FOOD_TRANSPORT_CATEGORY_ID, name = "Food/transport", amount = 0.0, dueDayOfMonth = 1),
            FixedExpense(id = "phone_internet", name = "Phone/internet", amount = 0.0, dueDayOfMonth = 1),
            FixedExpense(id = "family_support", name = "Family support", amount = 0.0, dueDayOfMonth = 1),
            FixedExpense(id = "other_fixed", name = "Other", amount = 0.0, dueDayOfMonth = 1)
        )

    fun onboardingCategoryTemplates(): List<CategoryBudgetSetup> =
        budgetCategories().filter { category ->
            category.id != UNCATEGORIZED_CATEGORY_ID &&
                category.id != SAVINGS_GOAL_CATEGORY_ID &&
                category.id != DEBT_PAYMENT_CATEGORY_ID &&
                category.id != AVOID_CATEGORY_ID
        }.map { category ->
            CategoryBudgetSetup(
                id = category.id,
                name = category.name,
                monthlyBudget = 0.0,
                isEnabled = true
            )
        }
}
