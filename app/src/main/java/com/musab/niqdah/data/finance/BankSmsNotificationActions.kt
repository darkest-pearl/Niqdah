package com.musab.niqdah.data.finance

import com.musab.niqdah.domain.finance.ParsedBankMessageType
import com.musab.niqdah.domain.finance.PendingBankImport

internal object BankSmsNotificationActions {
    const val ACTION_SAVE = "com.musab.niqdah.bank_sms.SAVE"
    const val ACTION_DISMISS = "com.musab.niqdah.bank_sms.DISMISS"
    const val EXTRA_IMPORT_ID = "com.musab.niqdah.bank_sms.IMPORT_ID"
    const val CHANNEL_ID = "bank_import_reviews"
}

internal object BankSmsNotificationActionRules {
    fun saveDecision(pendingImport: PendingBankImport): SaveDecision {
        val amount = pendingImport.amount
        return when {
            pendingImport.type == ParsedBankMessageType.INFORMATIONAL ->
                SaveDecision.Blocked("This message is informational and was not saved as a transaction.")
            pendingImport.type == ParsedBankMessageType.UNKNOWN ->
                SaveDecision.Blocked("This message needs review before saving.")
            amount == null || amount <= 0.0 ->
                SaveDecision.Blocked("Enter a valid imported amount.")
            pendingImport.type == ParsedBankMessageType.EXPENSE &&
                pendingImport.suggestedCategoryId.isNullOrBlank() ->
                SaveDecision.Blocked("Choose a category before saving.")
            else -> SaveDecision.Allowed
        }
    }

    fun canSaveFromNotification(pendingImport: PendingBankImport): Boolean =
        saveDecision(pendingImport) == SaveDecision.Allowed

    sealed interface SaveDecision {
        data object Allowed : SaveDecision
        data class Blocked(val reason: String) : SaveDecision
    }
}
