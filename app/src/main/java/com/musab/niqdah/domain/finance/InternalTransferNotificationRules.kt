package com.musab.niqdah.domain.finance

import java.util.Locale

object InternalTransferNotificationRules {
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    fun notificationPlan(
        pendingImport: PendingBankImport,
        candidates: List<PendingBankImport>,
        nowMillis: Long,
        reminderThresholdMinutes: Int = FinanceDefaults.DEFAULT_INTERNAL_TRANSFER_REMINDER_MINUTES
    ): InternalTransferNotificationPlan? {
        if (!pendingImport.isInternalTransferSide()) return null
        val paired = PendingBankImportSaveRules.findMatchingTransferCounterpart(pendingImport, candidates)
        val debit = if (pendingImport.type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT) pendingImport else paired
        val credit = if (pendingImport.type == ParsedBankMessageType.SAVINGS_TRANSFER) pendingImport else paired

        return when {
            debit != null && credit != null -> readyPlan(debit, credit)
            pendingImport.type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT &&
                isPastReminderThreshold(pendingImport, nowMillis, reminderThresholdMinutes) ->
                reminderPlan(pendingImport)
            pendingImport.type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT ->
                waitingPlan(pendingImport)
            else -> null
        }
    }

    fun waitingPlan(debitImport: PendingBankImport): InternalTransferNotificationPlan =
        InternalTransferNotificationPlan(
            key = matchKey(debitImport, null),
            notificationId = notificationId(debitImport, null),
            primaryImportId = debitImport.id,
            state = InternalTransferNotificationState.WAITING,
            title = "Internal transfer pending",
            text = "${moneyText(debitImport)} from ${debitImport.sourceAccountSuffix.ifBlank { "source account" }} - waiting for matching credit.",
            actions = listOf(InternalTransferNotificationAction.REVIEW, InternalTransferNotificationAction.DISMISS)
        )

    fun readyPlan(
        debitImport: PendingBankImport,
        creditImport: PendingBankImport
    ): InternalTransferNotificationPlan =
        InternalTransferNotificationPlan(
            key = matchKey(debitImport, creditImport),
            notificationId = notificationId(debitImport, creditImport),
            primaryImportId = debitImport.id,
            state = InternalTransferNotificationState.READY,
            title = "Internal transfer ready",
            text = "${moneyText(debitImport)} from ${debitImport.sourceAccountSuffix.ifBlank { "source" }} to ${creditImport.targetAccountSuffix.ifBlank { "target" }}",
            actions = listOf(
                InternalTransferNotificationAction.SAVE,
                InternalTransferNotificationAction.EDIT,
                InternalTransferNotificationAction.DISMISS
            )
        )

    fun reminderPlan(debitImport: PendingBankImport): InternalTransferNotificationPlan =
        InternalTransferNotificationPlan(
            key = matchKey(debitImport, null),
            notificationId = notificationId(debitImport, null),
            primaryImportId = debitImport.id,
            state = InternalTransferNotificationState.REMINDER,
            title = "Transfer still waiting for matching credit",
            text = "${moneyText(debitImport)} may be a transfer to another account. Review before saving.",
            actions = listOf(InternalTransferNotificationAction.REVIEW, InternalTransferNotificationAction.DISMISS)
        )

    fun isPastReminderThreshold(
        debitImport: PendingBankImport,
        nowMillis: Long,
        reminderThresholdMinutes: Int
    ): Boolean =
        nowMillis - debitImport.createdAtMillis.coerceAtLeast(debitImport.receivedAtMillis) >=
            reminderThresholdMinutes.coerceAtLeast(1) * 60_000L

    fun matchKey(debitImport: PendingBankImport, creditImport: PendingBankImport?): String {
        val amount = debitImport.amount ?: creditImport?.amount ?: 0.0
        val currency = debitImport.currency.ifBlank { creditImport?.currency ?: FinanceDefaults.DEFAULT_CURRENCY }
        val source = debitImport.sourceAccountSuffix.ifBlank { "source" }
        val bucket = debitImport.occurredAtMillis / DAY_MILLIS
        return listOf(
            currency.uppercase(Locale.US),
            "%.2f".format(Locale.US, amount),
            source,
            bucket.toString()
        ).joinToString("|")
    }

    fun notificationId(debitImport: PendingBankImport, creditImport: PendingBankImport?): Int =
        matchKey(debitImport, creditImport).hashCode() and Int.MAX_VALUE

    private fun PendingBankImport.isInternalTransferSide(): Boolean =
        type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT || type == ParsedBankMessageType.SAVINGS_TRANSFER

    private fun moneyText(pendingImport: PendingBankImport): String {
        val amount = pendingImport.amount ?: 0.0
        return if (amount % 1.0 == 0.0) {
            String.format(Locale.US, "%s %,.0f", pendingImport.currency, amount)
        } else {
            String.format(Locale.US, "%s %,.2f", pendingImport.currency, amount)
        }
    }
}

data class InternalTransferNotificationPlan(
    val key: String,
    val notificationId: Int,
    val primaryImportId: String,
    val state: InternalTransferNotificationState,
    val title: String,
    val text: String,
    val actions: List<InternalTransferNotificationAction>
)

enum class InternalTransferNotificationState {
    WAITING,
    READY,
    REMINDER
}

enum class InternalTransferNotificationAction {
    SAVE,
    EDIT,
    REVIEW,
    DISMISS
}
