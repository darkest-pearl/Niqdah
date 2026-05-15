package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OnboardingPlannerTest {
    @Test
    fun onboardingRequiredWhenProfileIsNotCompleted() {
        val data = FinanceData.empty(uid = "uid").copy(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L).copy(onboardingCompleted = false)
        )

        assertTrue(OnboardingPlanner.requiresOnboarding(data))
    }

    @Test
    fun onboardingWritesUserProfileAndPlanFromAnswers() {
        val state = sampleState()

        val plan = OnboardingPlanner.buildPlan(
            uid = "uid",
            state = state,
            now = 123L,
            today = LocalDate.parse("2026-05-10")
        )

        assertTrue(plan.profile.onboardingCompleted)
        assertEquals(8_000.0, plan.profile.salary, 0.001)
        assertEquals("AED", plan.profile.currency)
        assertEquals(25, plan.profile.salaryDayOfMonth)
        assertEquals(FinanceDefaults.PRIMARY_GOAL_ID, plan.profile.primaryGoalId)
        assertEquals("Car", plan.goals.single().name)
        assertEquals(2_000.0, plan.profile.monthlySavingsTarget, 0.001)
        assertEquals("BANK1", plan.bankMessageSettings.dailyUseSource.senderName)
        assertFalse(plan.bankMessageSettings.isAutomaticSmsImportEnabled)
    }

    @Test
    fun onboardingSalaryDoesNotCreateDailyUseBalance() {
        val plan = OnboardingPlanner.buildPlan(
            uid = "uid",
            state = sampleState().copy(monthlyIncome = 5_000.0),
            now = 123L,
            today = LocalDate.parse("2026-05-10")
        )
        val data = FinanceData(
            profile = plan.profile,
            financialProfile = plan.financialProfile,
            categories = plan.categories,
            transactions = emptyList(),
            incomeTransactions = emptyList(),
            pendingBankImports = emptyList(),
            accountBalanceSnapshots = emptyList(),
            accountLedgerEntries = emptyList(),
            internalTransferRecords = emptyList(),
            merchantRules = emptyList(),
            goals = plan.goals,
            debt = plan.debt,
            bankMessageSettings = plan.bankMessageSettings,
            reminderSettings = plan.reminderSettings,
            necessaryItems = plan.necessaryItems
        )

        assertEquals(5_000.0, data.profile.salary, 0.001)
        assertNull(data.latestDailyUseBalanceStatus)
    }

    @Test
    fun debtNoBranchCreatesZeroDebt() {
        val plan = OnboardingPlanner.buildPlan(
            uid = "uid",
            state = sampleState().copy(debtProfile = DebtProfile(hasDebt = false)),
            today = LocalDate.parse("2026-05-10")
        )

        assertEquals(0.0, plan.debt.startingAmount, 0.001)
        assertEquals(0.0, plan.debt.monthlyAutoReduction, 0.001)
        assertFalse(plan.categories.any { it.id == FinanceDefaults.DEBT_PAYMENT_CATEGORY_ID })
    }

    @Test
    fun debtYesBranchCreatesDebtTrackerAndDebtCategory() {
        val plan = OnboardingPlanner.buildPlan(
            uid = "uid",
            state = sampleState(),
            today = LocalDate.parse("2026-05-10")
        )

        assertEquals(7_000.0, plan.debt.startingAmount, 0.001)
        assertEquals(500.0, plan.debt.monthlyAutoReduction, 0.001)
        assertTrue(plan.categories.any { it.id == FinanceDefaults.DEBT_PAYMENT_CATEGORY_ID })
    }

    @Test
    fun monthlySavingsTargetCalculationUsesGoalAmountAndDate() {
        val target = OnboardingPlanner.monthlySavingsTarget(
            targetAmount = 12_000.0,
            targetDateInput = "2026-11-01",
            today = LocalDate.parse("2026-05-10")
        )

        assertEquals(2_000.0, target, 0.001)
    }

    @Test
    fun hardcodedMarriageDefaultsAreNotAppliedToNewUsers() {
        val plan = OnboardingPlanner.buildPlan(
            uid = "uid",
            state = sampleState().copy(
                monthlyIncome = 6_200.0,
                debtProfile = DebtProfile(
                    hasDebt = true,
                    totalDebtAmount = 3_200.0,
                    lenderType = DebtLenderType.BANK,
                    pressureLevel = DebtPressureLevel.FIXED_MONTHLY_PAYMENT,
                    monthlyInstallmentAmount = 300.0,
                    dueDayOfMonth = 10
                ),
                primarySavingsGoal = PrimarySavingsGoal(
                    name = "Car",
                    purpose = GoalPurpose.SAVE_FOR_GOAL,
                    targetAmount = 12_000.0,
                    targetDate = "2026-11-01"
                )
            ),
            today = LocalDate.parse("2026-05-10")
        )

        assertEquals("Car", plan.goals.single().name)
        assertFalse(plan.goals.any { it.name.contains("Marriage", ignoreCase = true) })
        assertEquals(6_200.0, plan.profile.salary, 0.001)
        assertEquals(3_200.0, plan.debt.startingAmount, 0.001)
    }

    @Test
    fun existingUserDataTriggersMigrationPreservation() {
        val data = FinanceData.empty(uid = "uid").copy(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L).copy(onboardingCompleted = false),
            goals = listOf(
                SavingsGoal(
                    id = "legacy_goal",
                    name = "Existing goal",
                    targetAmount = 1_000.0
                )
            )
        )

        assertTrue(OnboardingPlanner.requiresOnboarding(data))
        assertTrue(OnboardingPlanner.hasExistingPlan(data))
    }

    @Test
    fun privacyDefaultsRemainReviewOnlyAndNoSmsContentLeavesPlan() {
        val plan = OnboardingPlanner.buildPlan(uid = "uid", state = sampleState())

        assertFalse(plan.bankMessageSettings.isAutomaticSmsImportEnabled)
        assertTrue(plan.bankMessageSettings.requireReviewBeforeSaving)
        assertEquals("BANK1", plan.bankMessageSettings.dailyUseSource.senderName)
        assertEquals("", plan.bankMessageSettings.lastIgnoredReason)
    }

    private fun sampleState(): OnboardingState =
        OnboardingState(
            controlFocus = GoalPurpose.SAVE_FOR_GOAL,
            monthlyIncome = 8_000.0,
            currency = "aed",
            salaryDayOfMonth = 25,
            fixedExpenses = listOf(
                FixedExpense(id = "rent", name = "Rent", amount = 2_000.0, dueDayOfMonth = 1)
            ),
            debtProfile = DebtProfile(
                hasDebt = true,
                totalDebtAmount = 7_000.0,
                lenderType = DebtLenderType.FRIEND_FAMILY,
                pressureLevel = DebtPressureLevel.FIXED_MONTHLY_PAYMENT,
                monthlyInstallmentAmount = 500.0,
                dueDayOfMonth = 5
            ),
            primarySavingsGoal = PrimarySavingsGoal(
                name = "Car",
                purpose = GoalPurpose.SAVE_FOR_GOAL,
                targetAmount = 12_000.0,
                targetDate = "2026-11-01"
            ),
            preferences = UserPreferenceSetup(
                categoryBudgets = listOf(
                    CategoryBudgetSetup(
                        id = FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID,
                        name = "Food/transport",
                        monthlyBudget = 900.0,
                        isEnabled = true
                    )
                ),
                dailyUseBankSender = "BANK1",
                savingsBankSender = "BANK2",
                dailyUseAccountSuffix = "1234",
                savingsAccountSuffix = "9876",
                monthlySavingsReminderEnabled = true,
                debtPaymentReminderEnabled = true,
                overspendingWarningEnabled = true,
                necessaryItemReminderEnabled = true
            )
        )
}
