package com.musab.niqdah.domain.finance

object PendingBankImportSaveRules {
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    fun validate(pendingImport: PendingBankImport): SaveResult? {
        val amount = pendingImport.amount
        return when {
            pendingImport.type == ParsedBankMessageType.INFORMATIONAL ->
                SaveResult.Ignored("This message is informational and was not saved as a transaction.")
            pendingImport.type == ParsedBankMessageType.UNKNOWN ->
                SaveResult.NeedsReview("Choose a message type before saving.")
            amount == null || amount <= 0.0 ->
                SaveResult.Blocked("Enter a valid imported amount.")
            pendingImport.type == ParsedBankMessageType.EXPENSE &&
                pendingImport.suggestedCategoryId.isNullOrBlank() ->
                SaveResult.Blocked("Choose a category.")
            else -> null
        }
    }

    fun findMatchingTransferCounterpart(
        pendingImport: PendingBankImport,
        candidates: List<PendingBankImport>
    ): PendingBankImport? {
        val amount = pendingImport.amount ?: return null
        if (pendingImport.type != ParsedBankMessageType.SAVINGS_TRANSFER &&
            pendingImport.type != ParsedBankMessageType.INTERNAL_TRANSFER_OUT
        ) {
            return null
        }

        return candidates.firstOrNull { candidate ->
            candidate.id != pendingImport.id &&
                candidate.amount == amount &&
                candidate.currency == pendingImport.currency &&
                kotlin.math.abs(candidate.occurredAtMillis - pendingImport.occurredAtMillis) <= DAY_MILLIS &&
                setOf(candidate.type, pendingImport.type) == INTERNAL_TRANSFER_PAIR_TYPES &&
                hasOwnSavingsTransferSuffixProof(pendingImport, candidate)
        }
    }

    fun isMatchedInternalTransferPair(
        pendingImport: PendingBankImport,
        pairedImport: PendingBankImport?
    ): Boolean =
        pairedImport != null &&
            setOf(pendingImport.type, pairedImport.type) == INTERNAL_TRANSFER_PAIR_TYPES &&
            hasOwnSavingsTransferSuffixProof(pendingImport, pairedImport)

    fun idsToRemoveAfterSuccessfulSave(
        pendingImport: PendingBankImport,
        pairedImport: PendingBankImport?
    ): List<String> =
        listOfNotNull(pendingImport.id, pairedImport?.id).distinct()

    fun internalTransferRecord(
        debitImport: PendingBankImport,
        pairedCreditImport: PendingBankImport?,
        nowMillis: Long
    ): InternalTransferRecord {
        val noteParts = listOf(
            "Not counted as spending.",
            debitImport.noteWithReviewContext()
        ).filter { it.isNotBlank() }

        return InternalTransferRecord(
            id = "internal-transfer-${debitImport.messageHash}",
            amount = debitImport.amount ?: 0.0,
            currency = debitImport.currency,
            sourceAccountSuffix = debitImport.sourceAccountSuffix,
            targetAccountSuffix = pairedCreditImport?.targetAccountSuffix
                ?: debitImport.targetAccountSuffix.takeIf { it.isNotBlank() },
            direction = InternalTransferDirection.OUT,
            transferType = InternalTransferType.ACCOUNT_TO_ACCOUNT,
            status = if (pairedCreditImport == null) {
                InternalTransferStatus.NEEDS_MATCHING_CREDIT
            } else {
                InternalTransferStatus.PAIRED
            },
            pairedImportId = pairedCreditImport?.id,
            createdAtMillis = nowMillis,
            messageTimestampMillis = debitImport.occurredAtMillis,
            note = noteParts.joinToString("\n"),
            sourceMessageHash = debitImport.messageHash,
            amountMinor = effectiveMinorUnits(debitImport.amountMinor, debitImport.amount) ?: 0L
        )
    }

    fun internalTransferSavedMessage(
        pendingImport: PendingBankImport,
        latestDailyUseBalance: AccountBalanceSnapshot?,
        notificationStyle: Boolean = false
    ): String {
        val amount = pendingImport.amount ?: 0.0
        val recordedBalanceMayBeOutdated = latestDailyUseBalance != null &&
            latestDailyUseBalance.currency == pendingImport.currency &&
            latestDailyUseBalance.availableBalance < amount

        return when {
            notificationStyle -> "Internal transfer saved. Balance may be outdated."
            recordedBalanceMayBeOutdated -> "Saved. Your recorded balance may be outdated."
            pendingImport.availableBalance == null -> "Internal transfer saved. Waiting for matching credit."
            else -> "Saved successfully."
        }
    }

    fun pairedInternalTransferSavedMessage(): String =
        "Paired internal transfer saved. Savings contribution recorded."

    fun saveButtonLabel(isSaving: Boolean): String =
        if (isSaving) "Saving..." else "Save"

    fun savingsTransferSavedMessage(pendingImport: PendingBankImport): String =
        if (pendingImport.availableBalance == null) {
            "Savings transfer saved. Debit side was not found."
        } else {
            "Saved successfully."
        }

    private fun PendingBankImport.noteWithReviewContext(): String =
        listOf(description.trim(), reviewNote.trim())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")

    private fun hasOwnSavingsTransferSuffixProof(
        first: PendingBankImport,
        second: PendingBankImport
    ): Boolean {
        val debit = listOf(first, second).firstOrNull { it.type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT }
            ?: return false
        val credit = listOf(first, second).firstOrNull { it.type == ParsedBankMessageType.SAVINGS_TRANSFER }
            ?: return false
        return debit.sourceAccountSuffix.filter { it.isDigit() }.length == 4 &&
            credit.targetAccountSuffix.filter { it.isDigit() }.length == 4
    }

    private val INTERNAL_TRANSFER_PAIR_TYPES = setOf(
        ParsedBankMessageType.SAVINGS_TRANSFER,
        ParsedBankMessageType.INTERNAL_TRANSFER_OUT
    )
}
