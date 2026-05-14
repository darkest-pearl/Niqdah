package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountLedgerRulesTest {
    @Test
    fun depositWithoutBalanceUsesEstimatedStatusWhenPreviousBalanceExists() {
        val entry = AccountLedgerRules.pendingImportEntry(
            pendingImport = depositPendingImport(availableBalanceMinor = null),
            previousBalance = AccountBalanceStatus(
                accountKind = AccountKind.DAILY_USE,
                accountSuffix = "4052",
                amountMinor = 100_000L,
                currency = "AED",
                confidence = AccountBalanceConfidence.CONFIRMED,
                lastUpdatedMillis = 1_000L,
                source = AccountLedgerSource.SMS,
                note = "Confirmed by SMS"
            ),
            nowMillis = 2_000L
        )

        assertEquals(AccountLedgerEventType.ESTIMATED_DEPOSIT, entry.eventType)
        assertEquals(AccountBalanceConfidence.ESTIMATED, entry.confidence)
        assertEquals(450_000L, entry.balanceAfterMinor)
    }

    @Test
    fun depositWithoutBalanceNeedsReviewWhenNoPreviousBalanceExists() {
        val entry = AccountLedgerRules.pendingImportEntry(
            pendingImport = depositPendingImport(availableBalanceMinor = null),
            previousBalance = null,
            nowMillis = 2_000L
        )

        assertEquals(AccountLedgerEventType.ESTIMATED_DEPOSIT, entry.eventType)
        assertEquals(AccountBalanceConfidence.NEEDS_REVIEW, entry.confidence)
        assertNull(entry.balanceAfterMinor)
    }

    @Test
    fun manualDepositWithBalanceConfirmsAccountBalance() {
        val entry = AccountLedgerRules.manualDepositEntry(
            accountKind = AccountKind.DAILY_USE,
            accountSuffix = "4052",
            amountMinor = 350_000L,
            balanceAfterMinor = 352_500L,
            currency = "AED",
            depositType = DepositType.SALARY,
            relatedTransactionId = "income-1",
            occurredAtMillis = 3_000L,
            nowMillis = 4_000L
        )

        assertEquals(AccountLedgerEventType.BALANCE_CONFIRMED_MANUAL, entry.eventType)
        assertEquals(AccountBalanceConfidence.CONFIRMED, entry.confidence)
        assertEquals(AccountLedgerSource.MANUAL, entry.source)
        assertEquals(352_500L, entry.balanceAfterMinor)
    }

    private fun depositPendingImport(availableBalanceMinor: Long?): PendingBankImport =
        PendingBankImport(
            id = "deposit-hash",
            messageHash = "deposit-hash",
            senderName = "BANKTEST",
            rawMessage = "",
            sourceType = BankMessageSourceType.DAILY_USE,
            type = ParsedBankMessageType.INCOME,
            amount = 3_500.0,
            currency = "AED",
            availableBalance = availableBalanceMinor?.let { minorUnitsToMajor(it) },
            availableBalanceCurrency = "AED",
            originalForeignAmount = null,
            originalForeignCurrency = "",
            inferredAccountDebit = null,
            isAmountInferredFromBalance = false,
            reviewNote = "",
            merchantName = "",
            sourceAccountSuffix = "",
            targetAccountSuffix = "4052",
            ignoredReason = "",
            pairedTransferStatus = "",
            description = "Bank deposit",
            occurredAtMillis = 1_500L,
            suggestedCategoryId = null,
            suggestedCategoryName = "Uncategorized",
            suggestedNecessity = NecessityLevel.OPTIONAL,
            confidence = ParsedBankMessageConfidence.HIGH,
            receivedAtMillis = 1_500L,
            amountMinor = 350_000L,
            availableBalanceMinor = availableBalanceMinor,
            depositType = DepositType.OTHER_INCOME
        )
}
