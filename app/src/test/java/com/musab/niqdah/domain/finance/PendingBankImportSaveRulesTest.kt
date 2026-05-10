package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingBankImportSaveRulesTest {
    private val parser = BankMessageParser()
    private val categories = FinanceDefaults.budgetCategories(now = 0L)
    private val settings = FinanceDefaults.bankMessageParserSettings().copy(
        dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
        dailyUseAccountSuffix = "4052",
        savingsAccountSuffix = "4146"
    )
    private val occurredAt = 1_800_000_000_000L

    @Test
    fun internalTransferDebitCreatesVisibleNeutralRecord() {
        val pending = debitPendingImport()

        val record = PendingBankImportSaveRules.internalTransferRecord(
            debitImport = pending,
            pairedCreditImport = null,
            nowMillis = occurredAt
        )
        val before = dashboardWith(internalTransferRecords = emptyList())
        val after = dashboardWith(internalTransferRecords = listOf(record))

        assertEquals(ParsedBankMessageType.INTERNAL_TRANSFER_OUT, pending.type)
        assertEquals(1_000.0, record.amount, 0.001)
        assertEquals("AED", record.currency)
        assertEquals("4052", record.sourceAccountSuffix)
        assertEquals(InternalTransferDirection.OUT, record.direction)
        assertEquals(InternalTransferType.ACCOUNT_TO_ACCOUNT, record.transferType)
        assertEquals(InternalTransferStatus.NEEDS_MATCHING_CREDIT, record.status)
        assertTrue(record.note.contains("Not counted as spending."))
        assertEquals(before.totalSpent, after.totalSpent, 0.001)
        assertEquals(before.totalMonthlyIncome, after.totalMonthlyIncome, 0.001)
        assertEquals(
            "Internal transfer saved. Waiting for matching credit.",
            PendingBankImportSaveRules.internalTransferSavedMessage(pending, latestDailyUseBalance = null)
        )
    }

    @Test
    fun internalTransferDebitSavesWithOutdatedBalanceWarningWhenRecordedBalanceIsLower() {
        val pending = debitPendingImport()
        val latestBalance = AccountBalanceSnapshot(
            accountKind = AccountKind.DAILY_USE,
            sender = "BANKTEST",
            availableBalance = 500.0,
            currency = "AED",
            messageTimestampMillis = occurredAt - 1_000L,
            sourceMessageHash = "previous",
            createdAtMillis = occurredAt - 1_000L
        )

        assertEquals(
            "Saved. Your recorded balance may be outdated.",
            PendingBankImportSaveRules.internalTransferSavedMessage(pending, latestBalance)
        )
    }

    @Test
    fun creditSideSavingsTransferIsNotNormalIncome() {
        val pending = creditPendingImport()

        assertEquals(ParsedBankMessageType.SAVINGS_TRANSFER, pending.type)
        assertEquals(FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID, pending.suggestedCategoryId)
        assertNull(PendingBankImportSaveRules.validate(pending))
    }

    @Test
    fun debitAndCreditPairCreatesOnePairedRecordAndOneSavingsContributionWhenSavingDebitFirst() {
        val debit = debitPendingImport()
        val credit = creditPendingImport()
        val paired = PendingBankImportSaveRules.findMatchingTransferCounterpart(
            pendingImport = debit,
            candidates = listOf(debit, credit)
        )
        val record = PendingBankImportSaveRules.internalTransferRecord(
            debitImport = debit,
            pairedCreditImport = paired,
            nowMillis = occurredAt
        )
        val savingsContribution = ExpenseTransaction(
            id = "bank-message-savings-2027-01",
            categoryId = FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID,
            amount = 1_000.0,
            currency = "AED",
            occurredAtMillis = occurredAt,
            yearMonth = FinanceDates.yearMonthFromMillis(occurredAt)
        )
        val dashboard = dashboardWith(
            transactions = listOf(savingsContribution),
            incomeTransactions = emptyList(),
            internalTransferRecords = listOf(record)
        )

        assertEquals(credit.id, paired?.id)
        assertEquals(InternalTransferStatus.PAIRED, record.status)
        assertEquals("4146", record.targetAccountSuffix)
        assertEquals(listOf(debit.id, credit.id), PendingBankImportSaveRules.idsToRemoveAfterSuccessfulSave(debit, paired))
        assertEquals(
            "Paired internal transfer saved. Savings contribution recorded.",
            PendingBankImportSaveRules.pairedInternalTransferSavedMessage()
        )
        assertEquals(0.0, dashboard.totalSpent, 0.001)
        assertEquals(5_500.0, dashboard.totalMonthlyIncome, 0.001)
        assertEquals(1_000.0 / 1_700.0, dashboard.savingsTargetProgress, 0.001)
    }

    @Test
    fun debitAndCreditPairCreatesSamePlanWhenSavingCreditFirst() {
        val debit = debitPendingImport()
        val credit = creditPendingImport()
        val paired = PendingBankImportSaveRules.findMatchingTransferCounterpart(
            pendingImport = credit,
            candidates = listOf(debit, credit)
        )
        val record = PendingBankImportSaveRules.internalTransferRecord(
            debitImport = paired ?: error("Missing debit pair"),
            pairedCreditImport = credit,
            nowMillis = occurredAt
        )

        assertEquals(debit.id, paired.id)
        assertEquals(InternalTransferStatus.PAIRED, record.status)
        assertEquals("4052", record.sourceAccountSuffix)
        assertEquals("4146", record.targetAccountSuffix)
        assertEquals(listOf(credit.id, debit.id), PendingBankImportSaveRules.idsToRemoveAfterSuccessfulSave(credit, paired))
    }

    @Test
    fun duplicateReimportIsAllowedForStalePendingOrLinkedHistoryWithoutSavedRecord() {
        assertTrue(
            BankMessageImportRules.shouldAllowReimport(
                historyStatus = BankMessageImportStatus.PENDING,
                hasSavedMatchingRecord = false
            )
        )
        assertTrue(
            BankMessageImportRules.shouldAllowReimport(
                historyStatus = BankMessageImportStatus.LINKED,
                hasSavedMatchingRecord = false
            )
        )
        assertEquals(
            false,
            BankMessageImportRules.shouldAllowReimport(
                historyStatus = BankMessageImportStatus.LINKED,
                hasSavedMatchingRecord = true
            )
        )
    }

    @Test
    fun creditAloneSavesAsSavingsTransferNotIncome() {
        val credit = creditPendingImport()
        val dashboard = dashboardWith(
            transactions = listOf(
                ExpenseTransaction(
                    id = "bank-message-savings-2027-01",
                    categoryId = FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID,
                    amount = 1_000.0,
                    currency = "AED",
                    occurredAtMillis = occurredAt,
                    yearMonth = FinanceDates.yearMonthFromMillis(occurredAt)
                )
            ),
            incomeTransactions = emptyList(),
            internalTransferRecords = emptyList()
        )

        assertEquals(ParsedBankMessageType.SAVINGS_TRANSFER, credit.type)
        assertEquals("Savings transfer saved. Debit side was not found.", PendingBankImportSaveRules.savingsTransferSavedMessage(credit))
        assertEquals(0.0, dashboard.totalSpent, 0.001)
        assertEquals(5_500.0, dashboard.totalMonthlyIncome, 0.001)
    }

    @Test
    fun unsupportedTypesReturnVisibleSaveResult() {
        val unknown = debitPendingImport().copy(type = ParsedBankMessageType.UNKNOWN)
        val informational = debitPendingImport().copy(type = ParsedBankMessageType.INFORMATIONAL)

        assertTrue(PendingBankImportSaveRules.validate(unknown) is SaveResult.NeedsReview)
        assertEquals(
            SaveResult.Ignored("This message is informational and was not saved as a transaction."),
            PendingBankImportSaveRules.validate(informational)
        )
    }

    private fun debitPendingImport(): PendingBankImport =
        parser.parsePendingImport(
            rawMessage = "Amount of AED 1000.00 has been debited from your Mashreq account no. XXXXXXXX4052 for Account to Account Transfer. Login to Online Banking for details.",
            senderName = "BANKTEST",
            settings = settings,
            categories = categories,
            messageHash = "debit-hash",
            receivedAtMillis = occurredAt,
            nowMillis = occurredAt
        )

    private fun creditPendingImport(): PendingBankImport =
        parser.parsePendingImport(
            rawMessage = "Your Ac No. XXXXXXXX4146 is credited with AED 1000.00 as Account to Account Transfer. Login to Online Banking for details.",
            senderName = "BANKTEST",
            settings = settings,
            categories = categories,
            messageHash = "credit-hash",
            receivedAtMillis = occurredAt + 60_000L,
            nowMillis = occurredAt + 60_000L
        )

    private fun dashboardWith(
        transactions: List<ExpenseTransaction> = emptyList(),
        incomeTransactions: List<IncomeTransaction> = emptyList(),
        internalTransferRecords: List<InternalTransferRecord>
    ): DashboardMetrics {
        val data = FinanceData(
            profile = FinanceDefaults.userProfile(uid = "uid", now = 0L).copy(
                salary = 5_000.0,
                extraIncome = 500.0,
                monthlySavingsTarget = 1_700.0,
                onboardingCompleted = true
            ),
            categories = categories,
            transactions = transactions,
            incomeTransactions = incomeTransactions,
            pendingBankImports = emptyList(),
            accountBalanceSnapshots = emptyList(),
            internalTransferRecords = internalTransferRecords,
            merchantRules = emptyList(),
            goals = FinanceDefaults.savingsGoals(now = 0L),
            debt = FinanceDefaults.debtTracker(now = 0L),
            bankMessageSettings = settings,
            reminderSettings = FinanceDefaults.reminderSettings(now = 0L).copy(
                monthlySavingsTargetAmount = 1_700.0
            )
        )
        return FinanceCalculator.dashboard(
            data = data,
            yearMonth = FinanceDates.yearMonthFromMillis(occurredAt),
            now = 0L
        )
    }
}
