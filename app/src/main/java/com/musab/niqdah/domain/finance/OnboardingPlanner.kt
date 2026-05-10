package com.musab.niqdah.domain.finance

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

object OnboardingPlanner {
    fun buildPlan(
        uid: String,
        state: OnboardingState,
        now: Long = System.currentTimeMillis(),
        today: LocalDate = LocalDate.now()
    ): OnboardingPlan {
        val currency = state.currency.trim().uppercase(Locale.US).ifBlank { FinanceDefaults.DEFAULT_CURRENCY }
        val goal = state.primarySavingsGoal?.takeIf { it.name.isNotBlank() && it.targetAmount > 0.0 }
            ?.let { input ->
                val suggestion = input.monthlyTargetSuggestion.takeIf { it > 0.0 }
                    ?: monthlySavingsTarget(input.targetAmount, input.targetDate, today)
                input.copy(monthlyTargetSuggestion = suggestion)
            }
        val primaryGoalId = goal?.let { FinanceDefaults.PRIMARY_GOAL_ID }.orEmpty()
        val debtProfile = state.debtProfile.normalized()
        val monthlyDebtPayment = if (debtProfile.hasDebt) debtProfile.monthlyInstallmentAmount else 0.0
        val categories = buildCategories(
            fixedExpenses = state.fixedExpenses,
            categorySetups = state.preferences.categoryBudgets,
            monthlySavingsTarget = goal?.monthlyTargetSuggestion ?: 0.0,
            monthlyDebtPayment = monthlyDebtPayment,
            now = now
        )
        val profile = UserProfile(
            uid = uid,
            currency = currency,
            salary = state.monthlyIncome.coerceAtLeast(0.0),
            extraIncome = 0.0,
            monthlySavingsTarget = goal?.monthlyTargetSuggestion ?: 0.0,
            salaryDayOfMonth = state.salaryDayOfMonth.coerceIn(1, 31),
            onboardingCompleted = true,
            primaryGoalId = primaryGoalId,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        val userFinancialProfile = UserFinancialProfile(
            uid = uid,
            controlFocus = state.controlFocus,
            monthlyIncome = profile.salary,
            currency = currency,
            salaryDayOfMonth = profile.salaryDayOfMonth,
            fixedExpenses = state.fixedExpenses.map { it.copy(amount = it.amount.coerceAtLeast(0.0)) },
            debtProfile = debtProfile,
            primarySavingsGoal = goal,
            preferences = state.preferences,
            onboardingCompleted = true,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        val goals = goal?.let {
            listOf(
                SavingsGoal(
                    id = FinanceDefaults.PRIMARY_GOAL_ID,
                    name = it.name.trim(),
                    targetAmount = it.targetAmount.coerceAtLeast(0.0),
                    savedAmount = 0.0,
                    targetDate = it.targetDate.trim(),
                    purpose = it.purpose,
                    isPrimary = true,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        } ?: emptyList()

        return OnboardingPlan(
            profile = profile,
            financialProfile = userFinancialProfile,
            categories = categories,
            goals = goals,
            debt = DebtTracker(
                startingAmount = if (debtProfile.hasDebt) debtProfile.totalDebtAmount else 0.0,
                remainingAmount = if (debtProfile.hasDebt) debtProfile.totalDebtAmount else 0.0,
                monthlyAutoReduction = monthlyDebtPayment,
                lenderType = debtProfile.lenderType,
                pressureLevel = debtProfile.pressureLevel,
                dueDayOfMonth = debtProfile.dueDayOfMonth,
                updatedAtMillis = now
            ),
            bankMessageSettings = buildBankSettings(state.preferences),
            reminderSettings = buildReminderSettings(state.preferences, goal, now),
            necessaryItems = buildNecessaryItems(state.fixedExpenses, debtProfile, goal, state.preferences, now)
        )
    }

    fun monthlySavingsTarget(
        targetAmount: Double,
        targetDateInput: String,
        today: LocalDate = LocalDate.now()
    ): Double {
        if (targetAmount <= 0.0 || targetDateInput.isBlank()) return 0.0
        val targetDate = runCatching { LocalDate.parse(targetDateInput.trim()) }.getOrNull() ?: return 0.0
        val monthsRemaining = monthsRemainingForSavings(today, targetDate)
        if (monthsRemaining <= 0L) return targetAmount
        return targetAmount / monthsRemaining
    }

    fun requiresOnboarding(data: FinanceData): Boolean =
        !data.profile.onboardingCompleted

    fun hasExistingPlan(data: FinanceData): Boolean =
        data.categories.isNotEmpty() ||
            data.goals.isNotEmpty() ||
            data.transactions.isNotEmpty() ||
            data.incomeTransactions.isNotEmpty() ||
            data.necessaryItems.isNotEmpty() ||
            data.debt.startingAmount > 0.0 ||
            data.debt.remainingAmount > 0.0

    private fun buildCategories(
        fixedExpenses: List<FixedExpense>,
        categorySetups: List<CategoryBudgetSetup>,
        monthlySavingsTarget: Double,
        monthlyDebtPayment: Double,
        now: Long
    ): List<BudgetCategory> {
        val categories = linkedMapOf<String, BudgetCategory>()

        fun put(category: BudgetCategory) {
            categories.putIfAbsent(category.id, category)
        }

        put(
            BudgetCategory(
                id = FinanceDefaults.UNCATEGORIZED_CATEGORY_ID,
                name = "Uncategorized",
                monthlyBudget = 0.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )

        fixedExpenses.filter { it.amount > 0.0 && it.name.isNotBlank() }.forEach { expense ->
            put(
                BudgetCategory(
                    id = expense.id.ifBlank { stableId(expense.name) },
                    name = expense.name.trim(),
                    monthlyBudget = expense.amount,
                    type = CategoryType.FIXED,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }

        categorySetups.filter { it.isEnabled && it.name.isNotBlank() }.forEach { setup ->
            put(
                BudgetCategory(
                    id = setup.id.ifBlank { stableId(setup.name) },
                    name = setup.name.trim(),
                    monthlyBudget = setup.monthlyBudget.coerceAtLeast(0.0),
                    type = CategoryType.VARIABLE,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }

        put(
            BudgetCategory(
                id = FinanceDefaults.AVOID_CATEGORY_ID,
                name = "Avoid",
                monthlyBudget = 0.0,
                type = CategoryType.VARIABLE,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )

        if (monthlySavingsTarget > 0.0) {
            put(
                BudgetCategory(
                    id = FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID,
                    name = "Savings goal",
                    monthlyBudget = monthlySavingsTarget,
                    type = CategoryType.SAVINGS,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }

        if (monthlyDebtPayment > 0.0) {
            put(
                BudgetCategory(
                    id = FinanceDefaults.DEBT_PAYMENT_CATEGORY_ID,
                    name = "Debt payment",
                    monthlyBudget = monthlyDebtPayment,
                    type = CategoryType.DEBT,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        }

        return categories.values.toList()
    }

    private fun buildBankSettings(preferences: UserPreferenceSetup): BankMessageParserSettings =
        FinanceDefaults.bankMessageParserSettings().copy(
            dailyUseSource = BankMessageSourceSettings(
                senderName = preferences.dailyUseBankSender.trim(),
                isEnabled = preferences.dailyUseBankSender.isNotBlank()
            ),
            savingsSource = BankMessageSourceSettings(
                senderName = preferences.savingsBankSender.trim(),
                isEnabled = preferences.savingsBankSender.isNotBlank()
            ),
            isAutomaticSmsImportEnabled = false,
            requireReviewBeforeSaving = true,
            dailyUseAccountSuffix = preferences.dailyUseAccountSuffix.filter { it.isDigit() }.takeLast(4),
            savingsAccountSuffix = preferences.savingsAccountSuffix.filter { it.isDigit() }.takeLast(4)
        )

    private fun buildReminderSettings(
        preferences: UserPreferenceSetup,
        goal: PrimarySavingsGoal?,
        now: Long
    ): ReminderSettings =
        FinanceDefaults.reminderSettings(now).copy(
            isMonthlySavingsReminderEnabled = preferences.monthlySavingsReminderEnabled,
            monthlySavingsTargetAmount = goal?.monthlyTargetSuggestion ?: 0.0,
            isMissedSavingsReminderEnabled = preferences.monthlySavingsReminderEnabled,
            areOverspendingWarningsEnabled = preferences.overspendingWarningEnabled,
            isAvoidCategoryWarningEnabled = preferences.overspendingWarningEnabled,
            januaryTargetDate = goal?.targetDate.orEmpty(),
            januaryFundTargetAmount = goal?.targetAmount ?: 0.0,
            updatedAtMillis = now
        )

    private fun buildNecessaryItems(
        fixedExpenses: List<FixedExpense>,
        debtProfile: DebtProfile,
        goal: PrimarySavingsGoal?,
        preferences: UserPreferenceSetup,
        now: Long
    ): List<NecessaryItem> {
        if (!preferences.necessaryItemReminderEnabled) return emptyList()
        val items = mutableListOf<NecessaryItem>()
        fixedExpenses.filter { it.amount > 0.0 && it.name.isNotBlank() }.forEach { expense ->
            items += NecessaryItem(
                id = "fixed-${expense.id.ifBlank { stableId(expense.name) }}",
                title = expense.name.trim(),
                amount = expense.amount,
                dueDayOfMonth = expense.dueDayOfMonth.coerceIn(1, 31),
                recurrence = NecessaryItemRecurrence.MONTHLY,
                status = NecessaryItemStatus.PENDING,
                isNotificationEnabled = true,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        }
        if (preferences.debtPaymentReminderEnabled && debtProfile.hasDebt && debtProfile.monthlyInstallmentAmount > 0.0) {
            items += NecessaryItem(
                id = "debt-payment",
                title = "Debt payment",
                amount = debtProfile.monthlyInstallmentAmount,
                dueDayOfMonth = debtProfile.dueDayOfMonth ?: 1,
                recurrence = NecessaryItemRecurrence.MONTHLY,
                status = NecessaryItemStatus.PENDING,
                isNotificationEnabled = true,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        }
        if (preferences.monthlySavingsReminderEnabled && goal != null && goal.monthlyTargetSuggestion > 0.0) {
            items += NecessaryItem(
                id = "savings-transfer",
                title = "${goal.name} transfer",
                amount = goal.monthlyTargetSuggestion,
                dueDayOfMonth = 1,
                recurrence = NecessaryItemRecurrence.MONTHLY,
                status = NecessaryItemStatus.PENDING,
                isNotificationEnabled = true,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        }
        return items.distinctBy { it.id }
    }

    private fun DebtProfile.normalized(): DebtProfile =
        if (!hasDebt) {
            DebtProfile()
        } else {
            copy(
                totalDebtAmount = totalDebtAmount.coerceAtLeast(0.0),
                monthlyInstallmentAmount = monthlyInstallmentAmount.coerceAtLeast(0.0),
                dueDayOfMonth = dueDayOfMonth?.coerceIn(1, 31)
            )
        }

    private fun monthsRemainingForSavings(today: LocalDate, targetDate: LocalDate): Long {
        if (!today.isBefore(targetDate)) return 0L
        val currentMonth = YearMonth.from(today)
        val lastSavingMonth = YearMonth.from(targetDate.minusDays(1))
        return (ChronoUnit.MONTHS.between(currentMonth, lastSavingMonth) + 1L).coerceAtLeast(1L)
    }

    private fun stableId(value: String): String =
        value.trim()
            .lowercase(Locale.US)
            .replace(Regex("""[^a-z0-9]+"""), "_")
            .trim('_')
            .ifBlank { "custom_${max(1, value.hashCode())}" }
}
