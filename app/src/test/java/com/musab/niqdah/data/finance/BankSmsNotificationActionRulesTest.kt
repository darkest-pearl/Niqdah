package com.musab.niqdah.data.finance

import com.musab.niqdah.domain.finance.BankMessageSourceType
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.ParsedBankMessageConfidence
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import com.musab.niqdah.domain.finance.PendingBankImport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSmsNotificationActionRulesTest {
    @Test
    fun saveActionAllowsCompleteExpenseDraft() {
        assertTrue(
            BankSmsNotificationActionRules.canSaveFromNotification(
                pendingImport(
                    type = ParsedBankMessageType.EXPENSE,
                    amount = 42.50,
                    categoryId = FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID
                )
            )
        )
    }

    @Test
    fun saveActionRejectsMissingAmountAndMissingExpenseCategory() {
        assertFalse(
            BankSmsNotificationActionRules.canSaveFromNotification(
                pendingImport(
                    type = ParsedBankMessageType.EXPENSE,
                    amount = null,
                    categoryId = FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID
                )
            )
        )
        assertFalse(
            BankSmsNotificationActionRules.canSaveFromNotification(
                pendingImport(
                    type = ParsedBankMessageType.EXPENSE,
                    amount = 42.50,
                    categoryId = null
                )
            )
        )
    }

    @Test
    fun saveActionAllowsSavingsDraftWithoutExpenseCategory() {
        assertTrue(
            BankSmsNotificationActionRules.canSaveFromNotification(
                pendingImport(
                    type = ParsedBankMessageType.SAVINGS_TRANSFER,
                    amount = 1_700.0,
                    categoryId = null
                )
            )
        )
    }

    private fun pendingImport(
        type: ParsedBankMessageType,
        amount: Double?,
        categoryId: String?
    ): PendingBankImport =
        PendingBankImport(
            id = "import",
            messageHash = "hash",
            senderName = "BANKTEST",
            rawMessage = "hidden",
            sourceType = BankMessageSourceType.DAILY_USE,
            type = type,
            amount = amount,
            currency = FinanceDefaults.DEFAULT_CURRENCY,
            availableBalance = null,
            availableBalanceCurrency = FinanceDefaults.DEFAULT_CURRENCY,
            originalForeignAmount = null,
            originalForeignCurrency = "",
            inferredAccountDebit = null,
            isAmountInferredFromBalance = false,
            reviewNote = "",
            description = "Draft",
            occurredAtMillis = 1_800_000_000_000L,
            suggestedCategoryId = categoryId,
            suggestedCategoryName = "Category",
            suggestedNecessity = NecessityLevel.NECESSARY,
            confidence = ParsedBankMessageConfidence.HIGH,
            receivedAtMillis = 1_800_000_000_000L
        )
}
