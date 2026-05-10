package com.musab.niqdah.domain.ai

import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.ParsedBankMessageConfidence
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPrivacySanitizerTest {
    @Test
    fun bankSmsTextIsWithheldFromBackendPayload() {
        val rawSms = "AED 42.50 debited at Burger King on 08/05/2026. Available balance AED 1,234.00"
        val draft = AiFinanceDraftAction(
            type = ParsedBankMessageType.EXPENSE,
            amount = 42.50,
            currency = "AED",
            categoryId = "food",
            categoryName = "Food/transport",
            necessity = NecessityLevel.NECESSARY,
            description = "Burger King",
            dateInput = "2026-05-08",
            confidence = ParsedBankMessageConfidence.HIGH,
            senderName = "BANKTEST",
            originalText = rawSms
        )

        val sanitized = AiPrivacySanitizer.sanitizeUserMessage(rawSms, draft)

        assertTrue(sanitized.wasSanitized)
        assertFalse(sanitized.backendText.contains("Burger King"))
        assertFalse(sanitized.backendText.contains("Available balance"))
        assertTrue(sanitized.backendText.contains("type=Expense"))
        assertTrue(sanitized.displayText.contains("Raw SMS was not sent to AI"))
    }

    @Test
    fun normalPurchaseQuestionIsNotSanitized() {
        val message = "Can I spend AED 35 on dinner tonight?"

        val sanitized = AiPrivacySanitizer.sanitizeUserMessage(message, draftAction = null)

        assertFalse(sanitized.wasSanitized)
        assertTrue(sanitized.backendText == message)
    }
}
