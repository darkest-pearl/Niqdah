package com.musab.niqdah.domain.finance

import java.util.UUID

object AccountLedgerRules {
    fun pendingImportEntry(
        pendingImport: PendingBankImport,
        previousBalance: AccountBalanceStatus?,
        nowMillis: Long
    ): AccountLedgerEntry {
        val accountKind = pendingImport.accountKindForLedger()
        val amountMinor = effectiveMinorUnits(pendingImport.amountMinor, pendingImport.amount) ?: 0L
        val balanceAfterMinor = effectiveMinorUnits(
            pendingImport.availableBalanceMinor,
            pendingImport.availableBalance
        )
        val eventType = when {
            balanceAfterMinor != null -> AccountLedgerEventType.BALANCE_CONFIRMED_SMS
            pendingImport.type == ParsedBankMessageType.EXPENSE -> AccountLedgerEventType.ESTIMATED_DEBIT
            pendingImport.type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT -> AccountLedgerEventType.TRANSFER_OUT
            pendingImport.type == ParsedBankMessageType.SAVINGS_TRANSFER -> AccountLedgerEventType.TRANSFER_IN
            else -> AccountLedgerEventType.ESTIMATED_DEPOSIT
        }
        val estimatedBalance = when {
            balanceAfterMinor != null -> balanceAfterMinor
            previousBalance != null && eventType == AccountLedgerEventType.ESTIMATED_DEBIT ->
                previousBalance.amountMinor - amountMinor
            previousBalance != null && eventType in setOf(
                AccountLedgerEventType.ESTIMATED_DEPOSIT,
                AccountLedgerEventType.TRANSFER_IN
            ) -> previousBalance.amountMinor + amountMinor
            previousBalance != null && eventType == AccountLedgerEventType.TRANSFER_OUT ->
                previousBalance.amountMinor - amountMinor
            else -> null
        }
        val confidence = when {
            balanceAfterMinor != null -> AccountBalanceConfidence.CONFIRMED
            estimatedBalance != null -> AccountBalanceConfidence.ESTIMATED
            else -> AccountBalanceConfidence.NEEDS_REVIEW
        }

        return AccountLedgerEntry(
            id = "ledger-${pendingImport.messageHash.ifBlank { UUID.randomUUID().toString() }}",
            accountKind = accountKind,
            accountSuffix = pendingImport.accountSuffixForLedger(),
            eventType = eventType,
            amountMinor = amountMinor,
            balanceAfterMinor = estimatedBalance,
            currency = pendingImport.availableBalanceCurrency.ifBlank { pendingImport.currency },
            confidence = confidence,
            source = AccountLedgerSource.SMS,
            relatedTransactionId = pendingImport.messageHash,
            createdAtMillis = nowMillis,
            note = ledgerNote(confidence, pendingImport.depositType, pendingImport.reviewNote)
        )
    }

    fun manualDepositEntry(
        accountKind: AccountKind,
        accountSuffix: String,
        amountMinor: Long,
        balanceAfterMinor: Long?,
        currency: String,
        depositType: DepositType,
        relatedTransactionId: String?,
        occurredAtMillis: Long,
        nowMillis: Long,
        previousBalance: AccountBalanceStatus? = null
    ): AccountLedgerEntry {
        val estimatedBalanceAfterMinor = balanceAfterMinor ?: previousBalance?.let { it.amountMinor + amountMinor }
        val confidence = when {
            balanceAfterMinor != null -> AccountBalanceConfidence.CONFIRMED
            estimatedBalanceAfterMinor != null -> AccountBalanceConfidence.ESTIMATED
            else -> AccountBalanceConfidence.NEEDS_REVIEW
        }
        return AccountLedgerEntry(
            id = "manual-ledger-${relatedTransactionId ?: UUID.randomUUID().toString()}",
            accountKind = accountKind,
            accountSuffix = accountSuffix.filter { it.isDigit() }.takeLast(4),
            eventType = if (balanceAfterMinor != null) {
                AccountLedgerEventType.BALANCE_CONFIRMED_MANUAL
            } else {
                AccountLedgerEventType.ESTIMATED_DEPOSIT
            },
            amountMinor = amountMinor,
            balanceAfterMinor = estimatedBalanceAfterMinor,
            currency = currency.ifBlank { FinanceDefaults.DEFAULT_CURRENCY },
            confidence = confidence,
            source = AccountLedgerSource.MANUAL,
            relatedTransactionId = relatedTransactionId,
            createdAtMillis = nowMillis,
            note = "${depositType.label} recorded manually on ${FinanceDates.dateInputFromMillis(occurredAtMillis)}."
        )
    }

    private fun PendingBankImport.accountKindForLedger(): AccountKind =
        when {
            type == ParsedBankMessageType.SAVINGS_TRANSFER -> AccountKind.SAVINGS
            sourceType == BankMessageSourceType.SAVINGS -> AccountKind.SAVINGS
            else -> AccountKind.DAILY_USE
        }

    private fun PendingBankImport.accountSuffixForLedger(): String =
        when (accountKindForLedger()) {
            AccountKind.DAILY_USE -> sourceAccountSuffix.ifBlank { targetAccountSuffix }
            AccountKind.SAVINGS -> targetAccountSuffix.ifBlank { sourceAccountSuffix }
        }.filter { it.isDigit() }.takeLast(4)

    private fun ledgerNote(
        confidence: AccountBalanceConfidence,
        depositType: DepositType,
        reviewNote: String
    ): String =
        when (confidence) {
            AccountBalanceConfidence.CONFIRMED -> "Balance confirmed by bank SMS."
            AccountBalanceConfidence.ESTIMATED -> "Estimated balance. Confirm with next bank SMS or manual update."
            AccountBalanceConfidence.NEEDS_REVIEW -> reviewNote.ifBlank {
                "${depositType.label} recorded without an available balance."
            }
        }
}
