package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CashProtectionCalculatorTest {
    @Test
    fun unknownOpeningBalanceReturnsNeutralState() {
        val result = CashProtectionCalculator.evaluate(
            CashProtectionInput(
                currentDailyUseBalanceMinor = 800_000L,
                salaryCycleOpeningBalanceMinor = null,
                totalProtectedUnpaidObligationsMinor = 300_000L,
                flexibleBudgetMinor = 200_000L,
                spentThisCycleMinor = 100_000L,
                unknownOrUncategorizedSpendingMinor = 0L
            )
        )

        assertEquals(CashProtectionRiskLevel.UNKNOWN_OPENING_BALANCE, result.riskLevel)
        assertEquals(0.0, result.ringProgress, 0.001)
        assertTrue(result.message.contains("Confirm balance after salary"))
    }

    @Test
    fun balanceAboveObligationsAndBufferIsHealthy() {
        val result = CashProtectionCalculator.evaluate(
            CashProtectionInput(
                currentDailyUseBalanceMinor = 700_000L,
                salaryCycleOpeningBalanceMinor = 900_000L,
                totalProtectedUnpaidObligationsMinor = 300_000L,
                flexibleBudgetMinor = 250_000L,
                spentThisCycleMinor = 200_000L,
                unknownOrUncategorizedSpendingMinor = 0L
            )
        )

        assertEquals(CashProtectionRiskLevel.HEALTHY, result.riskLevel)
        assertEquals(400_000L, result.flexibleBufferLeftMinor)
    }

    @Test
    fun balanceAboveObligationsButLowBufferIsWatchOrTight() {
        val watch = CashProtectionCalculator.evaluate(
            CashProtectionInput(
                currentDailyUseBalanceMinor = 400_000L,
                salaryCycleOpeningBalanceMinor = 900_000L,
                totalProtectedUnpaidObligationsMinor = 300_000L,
                flexibleBudgetMinor = 250_000L,
                spentThisCycleMinor = 500_000L,
                unknownOrUncategorizedSpendingMinor = 0L
            )
        )
        val tight = CashProtectionCalculator.evaluate(
            CashProtectionInput(
                currentDailyUseBalanceMinor = 340_000L,
                salaryCycleOpeningBalanceMinor = 900_000L,
                totalProtectedUnpaidObligationsMinor = 300_000L,
                flexibleBudgetMinor = 250_000L,
                spentThisCycleMinor = 560_000L,
                unknownOrUncategorizedSpendingMinor = 0L
            )
        )

        assertEquals(CashProtectionRiskLevel.WATCH, watch.riskLevel)
        assertEquals(CashProtectionRiskLevel.TIGHT, tight.riskLevel)
    }

    @Test
    fun balanceBelowUnpaidObligationsMeansProtectedFundsAtRisk() {
        val result = CashProtectionCalculator.evaluate(
            CashProtectionInput(
                currentDailyUseBalanceMinor = 250_000L,
                salaryCycleOpeningBalanceMinor = 900_000L,
                totalProtectedUnpaidObligationsMinor = 300_000L,
                flexibleBudgetMinor = 250_000L,
                spentThisCycleMinor = 650_000L,
                unknownOrUncategorizedSpendingMinor = 0L
            )
        )

        assertEquals(CashProtectionRiskLevel.PROTECTED_FUNDS_AT_RISK, result.riskLevel)
        assertEquals(0L, result.flexibleBufferLeftMinor)
    }

    @Test
    fun financeDataEvaluationMapsDashboardRingStateFromCycleBalanceAndObligations() {
        val salaryDate = FinanceDates.parseDateInput("2026-05-25") ?: error("Invalid date")
        val data = FinanceData.empty(uid = "uid").copy(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L).copy(onboardingCompleted = true),
            categories = listOf(
                BudgetCategory(
                    id = FinanceDefaults.RENT_CATEGORY_ID,
                    name = "Rent",
                    monthlyBudget = 2_000.0,
                    monthlyBudgetMinor = 200_000L,
                    type = CategoryType.FIXED
                ),
                BudgetCategory(
                    id = "food",
                    name = "Food",
                    monthlyBudget = 1_000.0,
                    monthlyBudgetMinor = 100_000L,
                    type = CategoryType.VARIABLE
                )
            ),
            salaryCycles = listOf(
                SalaryCycle(
                    id = "salary-cycle-2026-05",
                    userId = "uid",
                    cycleMonth = "2026-05",
                    salaryDepositAmountMinor = 800_000L,
                    openingDailyUseBalanceMinor = 900_000L,
                    salaryDepositDateMillis = salaryDate,
                    currency = "AED",
                    source = SalaryCycleSource.SMS,
                    isOpeningBalanceConfirmed = true
                )
            ),
            accountLedgerEntries = listOf(
                AccountLedgerEntry(
                    id = "balance",
                    accountKind = AccountKind.DAILY_USE,
                    eventType = AccountLedgerEventType.BALANCE_CONFIRMED_SMS,
                    amountMinor = 0L,
                    balanceAfterMinor = 260_000L,
                    currency = "AED",
                    confidence = AccountBalanceConfidence.CONFIRMED,
                    source = AccountLedgerSource.SMS,
                    createdAtMillis = salaryDate
                )
            )
        )

        val result = CashProtectionCalculator.evaluate(data, "2026-05")

        assertEquals(CashProtectionRiskLevel.WATCH, result.riskLevel)
        assertEquals(60_000L, result.flexibleBufferLeftMinor)
        assertEquals(200_000L, result.protectedUnpaidObligationsMinor)
    }
}
