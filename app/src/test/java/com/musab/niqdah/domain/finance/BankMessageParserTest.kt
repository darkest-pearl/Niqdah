package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Test

class BankMessageParserTest {
    private val parser = BankMessageParser()
    private val categories = FinanceDefaults.budgetCategories(now = 0L)

    @Test
    fun dailyUseDebitInfersExpenseAmountBalanceDateAndFoodCategory() {
        val settings = FinanceDefaults.bankMessageParserSettings().copy(
            dailyUseSource = BankMessageSourceSettings(senderName = "DailyBank", isEnabled = true)
        )

        val parsed = parser.parse(
            rawMessage = "From: DailyBank\nAED 42.50 debited at Burger King on 08/05/2026. Available balance AED 1,234.00",
            manualSenderName = "",
            settings = settings,
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.EXPENSE, parsed.type)
        assertEquals(BankMessageSourceType.DAILY_USE, parsed.sourceType)
        assertEquals(42.50, parsed.amount ?: 0.0, 0.001)
        assertEquals("AED", parsed.currency)
        assertEquals(1_234.00, parsed.availableBalance ?: 0.0, 0.001)
        assertEquals(FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID, parsed.suggestedCategoryId)
        assertEquals(NecessityLevel.NECESSARY, parsed.suggestedNecessity)
        assertEquals("2026-05-08", FinanceDates.dateInputFromMillis(parsed.occurredAtMillis))
    }

    @Test
    fun savingsTransferUsesSavingsCategory() {
        val parsed = parser.parse(
            rawMessage = "AED 1,700 transferred to savings account on 2026-05-08 09:30",
            manualSenderName = "SavingsBank",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                savingsSource = BankMessageSourceSettings(senderName = "SavingsBank", isEnabled = true)
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.SAVINGS_TRANSFER, parsed.type)
        assertEquals(FinanceDefaults.MARRIAGE_SAVINGS_CATEGORY_ID, parsed.suggestedCategoryId)
        assertEquals(1_700.0, parsed.amount ?: 0.0, 0.001)
        assertEquals("Transfer to savings", parsed.description)
    }

    @Test
    fun creditMessageBecomesIncome() {
        val parsed = parser.parse(
            rawMessage = "Salary credited AED 5,000 on 08/05/2026. Bal AED 5,300",
            manualSenderName = "DailyBank",
            settings = FinanceDefaults.bankMessageParserSettings(),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.INCOME, parsed.type)
        assertEquals(5_000.0, parsed.amount ?: 0.0, 0.001)
        assertEquals(5_300.0, parsed.availableBalance ?: 0.0, 0.001)
    }

    @Test
    fun disabledDailyParserLeavesDebitUnknown() {
        val parsed = parser.parse(
            rawMessage = "AED 25 debited at Metro on 08/05/2026",
            manualSenderName = "DailyBank",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "DailyBank", isEnabled = false),
                savingsSource = BankMessageSourceSettings(senderName = "SavingsBank", isEnabled = true)
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.UNKNOWN, parsed.type)
    }
}
