package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(ParsedBankMessageConfidence.HIGH, parsed.confidence)
    }

    @Test
    fun availableBalancePhrasesAreExtracted() {
        val messages = listOf(
            "AED 1 debited. Available balance AED 1,234.00",
            "AED 1 debited. Avl Bal AED 1,234.00",
            "AED 1 debited. Balance: AED 1,234.00",
            "AED 1 debited. Current balance AED 1,234.00",
            "AED 1 debited. Available limit AED 1,234.00"
        )

        messages.forEach { message ->
            val parsed = parser.parse(
                rawMessage = message,
                manualSenderName = "BANKTEST",
                settings = FinanceDefaults.bankMessageParserSettings(),
                categories = categories,
                nowMillis = 1_800_000_000_000L
            )

            assertEquals(message, 1_234.00, parsed.availableBalance ?: 0.0, 0.001)
            assertEquals(message, "AED", parsed.availableBalanceCurrency)
        }
    }

    @Test
    fun balanceOnlyMessageDoesNotBecomeTransactionAmount() {
        val parsed = parser.parse(
            rawMessage = "Available balance AED 1,234.00",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings(),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertNull(parsed.amount)
        assertEquals(1_234.00, parsed.availableBalance ?: 0.0, 0.001)
        assertEquals(ParsedBankMessageType.UNKNOWN, parsed.type)
    }

    @Test
    fun savingsTransferUsesSavingsCategory() {
        val parsed = parser.parse(
            rawMessage = "AED 1700 transferred to savings account on 2026-05-08",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                savingsSource = BankMessageSourceSettings(senderName = "SavingsBank", isEnabled = true)
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.SAVINGS_TRANSFER, parsed.type)
        assertEquals("AED", parsed.currency)
        assertEquals(FinanceDefaults.MARRIAGE_SAVINGS_CATEGORY_ID, parsed.suggestedCategoryId)
        assertEquals("Marriage savings", parsed.suggestedCategoryName)
        assertEquals(NecessityLevel.NECESSARY, parsed.suggestedNecessity)
        assertEquals(1_700.0, parsed.amount ?: 0.0, 0.001)
        assertEquals("2026-05-08", FinanceDates.dateInputFromMillis(parsed.occurredAtMillis))
        assertEquals(ParsedBankMessageConfidence.HIGH, parsed.confidence)
        assertEquals("Transfer to savings", parsed.description)
        assertEquals(BankMessageSourceType.SAVINGS, parsed.sourceType)
    }

    @Test
    fun exactSavingsTransferOverridesDailyUseSenderProfile() {
        val settings = FinanceDefaults.bankMessageParserSettings().copy(
            dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
            savingsSource = BankMessageSourceSettings(senderName = "SavingsBank", isEnabled = true)
        )

        val parsed = parser.parse(
            rawMessage = "AED 1700 transferred to savings account on 2026-05-08",
            manualSenderName = "BANKTEST",
            settings = settings,
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.SAVINGS_TRANSFER, parsed.type)
        assertEquals(1_700.0, parsed.amount ?: 0.0, 0.001)
        assertEquals("AED", parsed.currency)
        assertEquals(FinanceDefaults.MARRIAGE_SAVINGS_CATEGORY_ID, parsed.suggestedCategoryId)
        assertEquals("Marriage savings", parsed.suggestedCategoryName)
        assertEquals(NecessityLevel.NECESSARY, parsed.suggestedNecessity)
        assertEquals("2026-05-08", FinanceDates.dateInputFromMillis(parsed.occurredAtMillis))
        assertEquals(ParsedBankMessageConfidence.HIGH, parsed.confidence)
        assertEquals(BankMessageSourceType.SAVINGS, parsed.sourceType)
    }

    @Test
    fun foreignCurrencyPurchaseInfersAedDebitFromBalanceChange() {
        val parsed = parser.parse(
            rawMessage = "Card purchase USD 10.00 at Amazon. Available balance AED 1,190.00",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true)
            ),
            categories = categories,
            latestBalances = listOf(
                AccountBalanceSnapshot(
                    accountKind = AccountKind.DAILY_USE,
                    sender = "BANKTEST",
                    availableBalance = 1_234.00,
                    currency = "AED",
                    messageTimestampMillis = 1_799_999_000_000L,
                    sourceMessageHash = "previous",
                    createdAtMillis = 1_799_999_000_000L
                )
            ),
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.EXPENSE, parsed.type)
        assertEquals(44.00, parsed.amount ?: 0.0, 0.001)
        assertEquals("AED", parsed.currency)
        assertEquals(10.00, parsed.originalForeignAmount ?: 0.0, 0.001)
        assertEquals("USD", parsed.originalForeignCurrency)
        assertEquals(44.00, parsed.inferredAccountDebit ?: 0.0, 0.001)
        assertEquals(ParsedBankMessageConfidence.MEDIUM, parsed.confidence)
        assertEquals("AED amount inferred from balance change.", parsed.reviewNote)
    }

    @Test
    fun exactSavingsTransferPendingImportStoresSavingsTransferModel() {
        val pendingImport = parser.parsePendingImport(
            rawMessage = "AED 1700 transferred to savings account on 2026-05-08",
            senderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
                savingsSource = BankMessageSourceSettings(senderName = "SavingsBank", isEnabled = true)
            ),
            categories = categories,
            messageHash = "hash",
            receivedAtMillis = 1_800_000_000_000L,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.SAVINGS_TRANSFER, pendingImport.type)
        assertEquals(1_700.0, pendingImport.amount ?: 0.0, 0.001)
        assertEquals("AED", pendingImport.currency)
        assertEquals(FinanceDefaults.MARRIAGE_SAVINGS_CATEGORY_ID, pendingImport.suggestedCategoryId)
        assertEquals("Marriage savings", pendingImport.suggestedCategoryName)
        assertEquals(NecessityLevel.NECESSARY, pendingImport.suggestedNecessity)
        assertEquals("2026-05-08", FinanceDates.dateInputFromMillis(pendingImport.occurredAtMillis))
        assertEquals(ParsedBankMessageConfidence.HIGH, pendingImport.confidence)
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
    fun exactCreditSalaryMessageBecomesHighConfidenceIncome() {
        val parsed = parser.parse(
            rawMessage = "AED 5000 credited salary on 01/05/2026",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings(),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.INCOME, parsed.type)
        assertEquals(5_000.0, parsed.amount ?: 0.0, 0.001)
        assertEquals("AED", parsed.currency)
        assertEquals(ParsedBankMessageConfidence.HIGH, parsed.confidence)
        assertEquals("2026-05-01", FinanceDates.dateInputFromMillis(parsed.occurredAtMillis))
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

    @Test
    fun configuredDailySenderStillAllowsPastedDebitWithoutSender() {
        val parsed = parser.parse(
            rawMessage = "AED 42.50 debited at Burger King on 08/05/2026. Available balance AED 1,234.00",
            manualSenderName = "",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "DailyBank", isEnabled = true)
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.EXPENSE, parsed.type)
        assertEquals(42.50, parsed.amount ?: 0.0, 0.001)
        assertEquals(FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID, parsed.suggestedCategoryId)
        assertEquals(ParsedBankMessageConfidence.HIGH, parsed.confidence)
    }
}
