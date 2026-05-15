package com.musab.niqdah.domain.ai

import com.musab.niqdah.domain.finance.BankMessageParser
import com.musab.niqdah.domain.finance.DepositType
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFinanceDraftGateTest {
    private val parser = BankMessageParser()
    private val categories = FinanceDefaults.budgetCategories(now = 0L)
    private val settings = FinanceDefaults.bankMessageParserSettings()
    private val now = 1_800_000_000_000L

    @Test
    fun randomAdviceQuestionCreatesNoDraft() {
        val message = "What should I do this month?"
        val parsed = parse(message)

        assertFalse(AiFinanceDraftGate.shouldCreateDraft(message, parsed))
    }

    @Test
    fun affordabilityQuestionWithAmountCreatesNoDraft() {
        val message = "Can I buy AED 20 burger?"
        val parsed = parse(message)

        assertFalse(AiFinanceDraftGate.shouldCreateDraft(message, parsed))
    }

    @Test
    fun explicitLogRequestCreatesExpenseDraft() {
        val message = "Log AED 20 burger"
        val parsed = parse(message)

        assertTrue(AiFinanceDraftGate.shouldCreateDraft(message, parsed))
        assertEquals(ParsedBankMessageType.EXPENSE, AiFinanceDraftGate.draftTypeFor(message, parsed))
    }

    @Test
    fun pastedBankDebitSmsCreatesDraft() {
        val message = "AED 42.50 debited at Burger King on 08/05/2026. Available balance AED 1,234.00"
        val parsed = parse(message)

        assertTrue(AiFinanceDraftGate.shouldCreateDraft(message, parsed))
        assertEquals(ParsedBankMessageType.EXPENSE, AiFinanceDraftGate.draftTypeFor(message, parsed))
    }

    @Test
    fun salaryDepositSmsCreatesDepositDraft() {
        val message = "Monthly salary AED 3500.00 has been deposited to your account no. XXXXXXXX4052."
        val parsed = parse(message)

        assertTrue(AiFinanceDraftGate.shouldCreateDraft(message, parsed))
        assertEquals(ParsedBankMessageType.INCOME, AiFinanceDraftGate.draftTypeFor(message, parsed))
        assertEquals(DepositType.SALARY, parsed.depositType)
    }

    private fun parse(message: String) =
        parser.parse(
            rawMessage = message,
            manualSenderName = "",
            settings = settings,
            categories = categories,
            nowMillis = now
        )
}
