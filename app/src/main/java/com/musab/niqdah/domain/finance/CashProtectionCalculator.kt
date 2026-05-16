package com.musab.niqdah.domain.finance

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max

object SalaryCycleRules {
    fun activeCycle(data: FinanceData, cycleMonth: String): SalaryCycle? =
        data.salaryCycles
            .filter { it.isActive && it.cycleMonth == cycleMonth }
            .maxByOrNull { it.salaryDepositDateMillis }

    fun upsertForSalaryDeposit(
        existingCycles: List<SalaryCycle>,
        userId: String,
        amountMinor: Long,
        currentDailyUseBalanceMinor: Long?,
        currency: String,
        occurredAtMillis: Long,
        source: SalaryCycleSource,
        nowMillis: Long
    ): SalaryCycle {
        val cycleMonth = FinanceDates.yearMonthFromMillis(occurredAtMillis)
        val existing = existingCycles
            .filter { it.cycleMonth == cycleMonth }
            .maxByOrNull { it.updatedAtMillis }
        return SalaryCycle(
            id = existing?.id ?: "salary-cycle-$cycleMonth",
            userId = userId,
            cycleMonth = cycleMonth,
            salaryDepositAmountMinor = amountMinor,
            openingDailyUseBalanceMinor = currentDailyUseBalanceMinor ?: existing?.openingDailyUseBalanceMinor,
            salaryDepositDateMillis = occurredAtMillis,
            currency = currency.trim().uppercase(Locale.US).ifBlank { FinanceDefaults.DEFAULT_CURRENCY },
            source = source,
            isOpeningBalanceConfirmed = currentDailyUseBalanceMinor != null || existing?.isOpeningBalanceConfirmed == true,
            isActive = true,
            createdAtMillis = existing?.createdAtMillis?.takeIf { it > 0L } ?: nowMillis,
            updatedAtMillis = nowMillis,
            lastSavingsFollowUpReminderAtMillis = existing?.lastSavingsFollowUpReminderAtMillis ?: 0L
        )
    }
}

object ProtectedCashObligationCalculator {
    fun obligations(data: FinanceData, yearMonth: String): List<ProtectedCashObligation> {
        val transactions = data.transactions.filter { it.yearMonth == yearMonth }
        val obligations = mutableListOf<ProtectedCashObligation>()

        data.categories
            .filter { it.type == CategoryType.FIXED && effectiveMinorUnits(it.monthlyBudgetMinor, it.monthlyBudget) > 0L }
            .forEach { category ->
                val amountMinor = effectiveMinorUnits(category.monthlyBudgetMinor, category.monthlyBudget)
                val paidMinor = transactions
                    .filter { it.categoryId == category.id }
                    .sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }
                val remainingMinor = max(0L, amountMinor - paidMinor)
                obligations += ProtectedCashObligation(
                    title = category.name,
                    amountMinor = amountMinor,
                    remainingAmountMinor = remainingMinor,
                    categoryId = category.id,
                    status = if (remainingMinor == 0L) ProtectedCashObligationStatus.PAID else ProtectedCashObligationStatus.UNPAID,
                    type = category.protectedType()
                )
            }

        val savingsTargetMinor = data.reminderSettings.monthlySavingsTargetAmountMinor.takeIf { it > 0L }
            ?: effectiveMinorUnits(data.profile.monthlySavingsTargetMinor, data.profile.monthlySavingsTarget)
        if (savingsTargetMinor > 0L) {
            val savedMinor = savingsSavedMinor(data, yearMonth)
            val remainingMinor = max(0L, savingsTargetMinor - savedMinor)
            obligations += ProtectedCashObligation(
                title = data.primaryGoal?.name?.let { "$it savings" } ?: "Savings target",
                amountMinor = savingsTargetMinor,
                remainingAmountMinor = remainingMinor,
                categoryId = FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID,
                status = if (remainingMinor == 0L) ProtectedCashObligationStatus.PAID else ProtectedCashObligationStatus.UNPAID,
                type = ProtectedCashObligationType.SAVINGS_TARGET
            )
        }

        val debtPaymentMinor = effectiveMinorUnits(data.debt.monthlyAutoReductionMinor, data.debt.monthlyAutoReduction)
        if (debtPaymentMinor > 0L) {
            val paidMinor = transactions
                .filter { it.categoryId == FinanceDefaults.DEBT_PAYMENT_CATEGORY_ID }
                .sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }
            val remainingMinor = max(0L, debtPaymentMinor - paidMinor)
            obligations += ProtectedCashObligation(
                title = "Debt payment",
                amountMinor = debtPaymentMinor,
                remainingAmountMinor = remainingMinor,
                categoryId = FinanceDefaults.DEBT_PAYMENT_CATEGORY_ID,
                dueDayOfMonth = data.debt.dueDayOfMonth,
                status = if (remainingMinor == 0L) ProtectedCashObligationStatus.PAID else ProtectedCashObligationStatus.UNPAID,
                type = ProtectedCashObligationType.DEBT_PAYMENT
            )
        }

        data.necessaryItems
            .filter { it.amountMinor != null || it.amount != null }
            .forEach { item ->
                val amountMinor = item.amountMinor ?: item.amount?.let { majorToMinorUnits(it) } ?: 0L
                if (amountMinor <= 0L) return@forEach
                val status = when (item.status) {
                    NecessaryItemStatus.DONE -> ProtectedCashObligationStatus.PAID
                    NecessaryItemStatus.SKIPPED -> ProtectedCashObligationStatus.SKIPPED
                    NecessaryItemStatus.PENDING -> ProtectedCashObligationStatus.UNPAID
                }
                obligations += ProtectedCashObligation(
                    title = item.title,
                    amountMinor = amountMinor,
                    remainingAmountMinor = if (status == ProtectedCashObligationStatus.UNPAID) amountMinor else 0L,
                    dueDateMillis = item.dueDateMillis,
                    dueDayOfMonth = item.dueDayOfMonth,
                    status = status,
                    type = ProtectedCashObligationType.NECESSARY_ITEM
                )
            }

        return obligations
    }

    fun unpaidProtectedTotalMinor(data: FinanceData, yearMonth: String): Long =
        obligations(data, yearMonth)
            .filter { it.isProtected }
            .sumOf { it.remainingAmountMinor }

    fun savingsSavedMinor(data: FinanceData, yearMonth: String): Long {
        val categoryById = data.categories.associateBy { it.id }
        return data.transactions
            .filter { transaction ->
                transaction.yearMonth == yearMonth &&
                    (
                        categoryById[transaction.categoryId]?.type == CategoryType.SAVINGS ||
                            transaction.categoryId in setOf(
                                FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID,
                                FinanceDefaults.MARRIAGE_SAVINGS_CATEGORY_ID
                            )
                        )
            }
            .sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }
    }

    private fun BudgetCategory.protectedType(): ProtectedCashObligationType {
        val key = "${id.lowercase(Locale.US)} ${name.lowercase(Locale.US)}"
        return when {
            "rent" in key -> ProtectedCashObligationType.RENT
            "family" in key -> ProtectedCashObligationType.FAMILY_SUPPORT
            "subscription" in key -> ProtectedCashObligationType.SUBSCRIPTION
            "bill" in key || "phone" in key || "internet" in key || "utility" in key -> ProtectedCashObligationType.BILL
            else -> ProtectedCashObligationType.OTHER
        }
    }
}

object CashProtectionCalculator {
    fun evaluate(input: CashProtectionInput): CashProtectionResult {
        val protectedMinor = input.totalProtectedUnpaidObligationsMinor.coerceAtLeast(0L)
        val flexibleBudgetMinor = input.flexibleBudgetMinor.coerceAtLeast(0L)
        if (input.salaryCycleOpeningBalanceMinor == null || input.currentDailyUseBalanceMinor == null) {
            return CashProtectionResult(
                ringProgress = 0.0,
                riskLevel = CashProtectionRiskLevel.UNKNOWN_OPENING_BALANCE,
                flexibleBufferLeftMinor = 0L,
                protectedUnpaidObligationsMinor = protectedMinor,
                message = "Confirm balance after salary to activate protection."
            )
        }

        val currentBalanceMinor = input.currentDailyUseBalanceMinor.coerceAtLeast(0L)
        val bufferLeftMinor = max(0L, currentBalanceMinor - protectedMinor)
        val riskLevel = when {
            currentBalanceMinor < protectedMinor -> CashProtectionRiskLevel.PROTECTED_FUNDS_AT_RISK
            flexibleBudgetMinor <= 0L || bufferLeftMinor >= flexibleBudgetMinor -> CashProtectionRiskLevel.HEALTHY
            bufferLeftMinor >= flexibleBudgetMinor / 3L -> CashProtectionRiskLevel.WATCH
            else -> CashProtectionRiskLevel.TIGHT
        }
        val ringProgress = when {
            flexibleBudgetMinor <= 0L -> if (currentBalanceMinor >= protectedMinor) 1.0 else 0.0
            currentBalanceMinor < protectedMinor -> 0.0
            else -> (bufferLeftMinor.toDouble() / flexibleBudgetMinor.toDouble()).coerceIn(0.0, 1.0)
        }

        return CashProtectionResult(
            ringProgress = ringProgress,
            riskLevel = riskLevel,
            flexibleBufferLeftMinor = bufferLeftMinor,
            protectedUnpaidObligationsMinor = protectedMinor,
            message = when (riskLevel) {
                CashProtectionRiskLevel.HEALTHY -> "Protected obligations are covered."
                CashProtectionRiskLevel.WATCH -> "Flexible buffer is being used. Keep optional spending light."
                CashProtectionRiskLevel.TIGHT -> "Flexible buffer is low. Review unpaid obligations before spending."
                CashProtectionRiskLevel.PROTECTED_FUNDS_AT_RISK ->
                    "Protected funds may be at risk. Review unpaid obligations before spending more."
                CashProtectionRiskLevel.UNKNOWN_OPENING_BALANCE ->
                    "Confirm balance after salary to activate protection."
            }
        )
    }

    fun evaluate(data: FinanceData, yearMonth: String): CashProtectionResult {
        val cycle = SalaryCycleRules.activeCycle(data, yearMonth)
        val currentBalanceMinor = data.latestDailyUseBalanceStatus?.amountMinor
        val flexibleBudgetMinor = data.categories
            .filter { it.type == CategoryType.VARIABLE && it.id != FinanceDefaults.AVOID_CATEGORY_ID }
            .sumOf { effectiveMinorUnits(it.monthlyBudgetMinor, it.monthlyBudget) }
        val categoryById = data.categories.associateBy { it.id }
        val spentThisCycleMinor = data.transactions
            .filter { transaction ->
                transaction.yearMonth == yearMonth &&
                    categoryById[transaction.categoryId]?.type !in setOf(CategoryType.SAVINGS, CategoryType.DEBT)
            }
            .sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }
        val unknownMinor = data.transactions
            .filter { it.yearMonth == yearMonth && it.categoryId == FinanceDefaults.UNCATEGORIZED_CATEGORY_ID }
            .sumOf { effectiveMinorUnits(it.amountMinor, it.amount) }
        return evaluate(
            CashProtectionInput(
                currentDailyUseBalanceMinor = currentBalanceMinor,
                salaryCycleOpeningBalanceMinor = cycle?.openingDailyUseBalanceMinor,
                totalProtectedUnpaidObligationsMinor = ProtectedCashObligationCalculator.unpaidProtectedTotalMinor(data, yearMonth),
                flexibleBudgetMinor = flexibleBudgetMinor,
                spentThisCycleMinor = spentThisCycleMinor,
                unknownOrUncategorizedSpendingMinor = unknownMinor
            )
        )
    }
}

object SavingsFollowUpReminderRules {
    fun evaluate(data: FinanceData, cycleMonth: String, today: LocalDate): SavingsFollowUpReminderStatus {
        val settings = data.reminderSettings
        if (!settings.isPostSalarySavingsFollowUpEnabled) {
            return SavingsFollowUpReminderStatus(false, SavingsFollowUpState.DISABLED, 0L, null)
        }
        if (settings.suppressedPostSalarySavingsFollowUpCycleMonth == cycleMonth) {
            return SavingsFollowUpReminderStatus(false, SavingsFollowUpState.SUPPRESSED_FOR_CYCLE, 0L, null)
        }
        val cycle = SalaryCycleRules.activeCycle(data, cycleMonth)
            ?: return SavingsFollowUpReminderStatus(false, SavingsFollowUpState.NO_ACTIVE_CYCLE, 0L, null)
        val targetMinor = settings.monthlySavingsTargetAmountMinor.takeIf { it > 0L }
            ?: effectiveMinorUnits(data.profile.monthlySavingsTargetMinor, data.profile.monthlySavingsTarget)
        if (targetMinor <= 0L) {
            return SavingsFollowUpReminderStatus(false, SavingsFollowUpState.COMPLETED, 0L, null)
        }
        val savedMinor = ProtectedCashObligationCalculator.savingsSavedMinor(data, cycleMonth)
        val remainingMinor = max(0L, targetMinor - savedMinor)
        if (remainingMinor == 0L) {
            return SavingsFollowUpReminderStatus(false, SavingsFollowUpState.COMPLETED, 0L, null)
        }

        val intervalDays = settings.postSalarySavingsFollowUpIntervalDays.coerceAtLeast(1)
        val todayMillis = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val lastReminderMillis = cycle.lastSavingsFollowUpReminderAtMillis
        val nextEligibleMillis = if (lastReminderMillis <= 0L) {
            cycle.salaryDepositDateMillis
        } else {
            Instant.ofEpochMilli(lastReminderMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .plusDays(intervalDays.toLong())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
        val shouldNotify = todayMillis >= nextEligibleMillis ||
            (lastReminderMillis <= 0L && !today.isBefore(dateFromMillis(cycle.salaryDepositDateMillis)))
        return SavingsFollowUpReminderStatus(
            shouldNotify = shouldNotify,
            state = if (shouldNotify) SavingsFollowUpState.DUE else SavingsFollowUpState.NOT_DUE,
            remainingSavingsTargetMinor = remainingMinor,
            nextEligibleReminderMillis = nextEligibleMillis
        )
    }

    @Suppress("unused")
    fun daysUntilNextReminder(status: SavingsFollowUpReminderStatus, today: LocalDate): Long? =
        status.nextEligibleReminderMillis?.let { millis ->
            ChronoUnit.DAYS.between(today, dateFromMillis(millis)).coerceAtLeast(0L)
        }

    private fun dateFromMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}
