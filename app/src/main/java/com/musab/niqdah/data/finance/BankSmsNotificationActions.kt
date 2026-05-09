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
    fun canSaveFromNotification(pendingImport: PendingBankImport): Boolean {
        val amount = pendingImport.amount
        return pendingImport.type != ParsedBankMessageType.UNKNOWN &&
            amount != null &&
            amount > 0.0 &&
            (pendingImport.type != ParsedBankMessageType.EXPENSE ||
                !pendingImport.suggestedCategoryId.isNullOrBlank())
    }
}
