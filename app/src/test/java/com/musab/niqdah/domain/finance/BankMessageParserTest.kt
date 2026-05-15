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
        assertEquals(FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID, parsed.suggestedCategoryId)
        assertEquals("Savings goal", parsed.suggestedCategoryName)
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
        assertEquals(FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID, parsed.suggestedCategoryId)
        assertEquals("Savings goal", parsed.suggestedCategoryName)
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
        assertEquals("AED debit inferred from balance change; review before saving.", parsed.reviewNote)
    }

    @Test
    fun mashreqStyleCardExpenseExtractsMerchantDateAndBalance() {
        val parsed = parser.parse(
            rawMessage = "Thank you for using Al Islami Neo Debit Card Card ending 8251 for AED 17.25 at Aman Taxi on 18-JAN-2026 08:55 AM. Available Balance is AED 3,482.75",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
                dailyUseAccountSuffix = "8251"
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.EXPENSE, parsed.type)
        assertEquals(17.25, parsed.amount ?: 0.0, 0.001)
        assertEquals("AED", parsed.currency)
        assertEquals("Aman Taxi", parsed.merchantName)
        assertEquals("8251", parsed.sourceAccountSuffix)
        assertEquals(3_482.75, parsed.availableBalance ?: 0.0, 0.001)
        assertEquals(FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID, parsed.suggestedCategoryId)
        assertEquals("2026-01-18", FinanceDates.dateInputFromMillis(parsed.occurredAtMillis))
        assertEquals(ParsedBankMessageConfidence.HIGH, parsed.confidence)
    }

    @Test
    fun mashreqStyleGroceryUsesFoodTransportFallback() {
        val parsed = parser.parse(
            rawMessage = "Thank you for using Al Islami Neo Debit Card Card ending 8251 for AED 10.00 at FRESH CHOICE on 18-JAN-2026 08:58 AM. Available Balance is AED 3,472.75",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
                dailyUseAccountSuffix = "8251"
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.EXPENSE, parsed.type)
        assertEquals(10.00, parsed.amount ?: 0.0, 0.001)
        assertEquals("FRESH CHOICE", parsed.merchantName)
        assertEquals(FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID, parsed.suggestedCategoryId)
        assertEquals(ParsedBankMessageConfidence.HIGH, parsed.confidence)
    }

    @Test
    fun mashreqStyleForeignPurchaseCanInferAedDebit() {
        val parsed = parser.parse(
            rawMessage = "Thank you for using Al Islami Neo Debit Card Card ending 8251 for EUR .93 at ORACLE IRELAND on 19-JAN-2026 01:22 AM. Available Balance is AED 3,442.64",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
                dailyUseAccountSuffix = "8251"
            ),
            categories = categories,
            latestBalances = listOf(
                AccountBalanceSnapshot(
                    accountKind = AccountKind.DAILY_USE,
                    sender = "BANKTEST",
                    availableBalance = 3_472.75,
                    currency = "AED",
                    messageTimestampMillis = 1_800_000_000_000L,
                    sourceMessageHash = "previous",
                    createdAtMillis = 1_800_000_000_000L
                )
            ),
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.EXPENSE, parsed.type)
        assertEquals(0.93, parsed.originalForeignAmount ?: 0.0, 0.001)
        assertEquals("EUR", parsed.originalForeignCurrency)
        assertEquals("ORACLE IRELAND", parsed.merchantName)
        assertEquals(3_442.64, parsed.availableBalance ?: 0.0, 0.001)
        assertEquals(30.11, parsed.inferredAccountDebit ?: 0.0, 0.001)
        assertEquals(ParsedBankMessageConfidence.MEDIUM, parsed.confidence)
        assertEquals("AED debit inferred from balance change; review before saving.", parsed.reviewNote)
    }

    @Test
    fun debitInternalTransferIsNotNormalExpense() {
        val parsed = parser.parse(
            rawMessage = "Amount of AED 1000.00 has been debited from your Mashreq account no. XXXXXXXX4052 for Account to Account Transfer. Login to Online Banking for details.",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
                dailyUseAccountSuffix = "4052",
                savingsAccountSuffix = "4146"
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.INTERNAL_TRANSFER_OUT, parsed.type)
        assertEquals(1_000.0, parsed.amount ?: 0.0, 0.001)
        assertEquals("4052", parsed.sourceAccountSuffix)
        assertEquals("Transfer to savings / needs matching credit.", parsed.reviewNote)
    }

    @Test
    fun creditInternalTransferToSavingsIsSavingsTransfer() {
        val parsed = parser.parse(
            rawMessage = "Your Ac No. XXXXXXXX4146 is credited with AED 1000.00 as Account to Account Transfer. Login to Online Banking for details.",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
                dailyUseAccountSuffix = "4052",
                savingsAccountSuffix = "4146"
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.SAVINGS_TRANSFER, parsed.type)
        assertEquals(1_000.0, parsed.amount ?: 0.0, 0.001)
        assertEquals("4146", parsed.targetAccountSuffix)
        assertEquals(FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID, parsed.suggestedCategoryId)
        assertEquals(ParsedBankMessageConfidence.HIGH, parsed.confidence)
    }

    @Test
    fun accountToAccountCreditToDailyUseIsNotSavingsTransfer() {
        val parsed = parser.parse(
            rawMessage = "Your Ac No. XXXXXXXX4052 is credited with AED 1000.00 as Account to Account Transfer. Login to Online Banking for details.",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
                dailyUseAccountSuffix = "4052",
                savingsAccountSuffix = "4146"
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.INCOME, parsed.type)
        assertEquals(BankMessageSourceType.DAILY_USE, parsed.sourceType)
        assertEquals(DepositType.TRANSFER, parsed.depositType)
        assertEquals("4052", parsed.targetAccountSuffix)
        assertEquals(FinanceDefaults.UNCATEGORIZED_CATEGORY_ID, parsed.suggestedCategoryId)
    }

    @Test
    fun accountToAccountCreditToUnconfiguredSuffixIsNotSavingsTransfer() {
        val parsed = parser.parse(
            rawMessage = "Your Ac No. XXXXXXXX9999 is credited with AED 1000.00 as Account to Account Transfer. Login to Online Banking for details.",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
                dailyUseAccountSuffix = "4052",
                savingsAccountSuffix = "4146"
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.INCOME, parsed.type)
        assertEquals(DepositType.TRANSFER, parsed.depositType)
        assertEquals("9999", parsed.targetAccountSuffix)
        assertEquals(FinanceDefaults.UNCATEGORIZED_CATEGORY_ID, parsed.suggestedCategoryId)
    }

    @Test
    fun depositedToDailyUseAccountBecomesGeneralDepositWithUnconfirmedBalance() {
        val parsed = parser.parse(
            rawMessage = "AED 3500.00 has been deposited to your account no. XXXXXXXX4052. Login to Mobile or Online Banking for details.",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
                dailyUseAccountSuffix = "4052",
                savingsAccountSuffix = "4146"
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.INCOME, parsed.type)
        assertEquals(BankMessageSourceType.DAILY_USE, parsed.sourceType)
        assertEquals(3_500.0, parsed.amount ?: 0.0, 0.001)
        assertEquals(350_000L, parsed.amountMinor)
        assertEquals("4052", parsed.targetAccountSuffix)
        assertNull(parsed.availableBalance)
        assertNull(parsed.availableBalanceMinor)
        assertEquals(DepositType.OTHER_INCOME, parsed.depositType)
        assertEquals(ParsedBankMessageConfidence.HIGH, parsed.confidence)
    }

    @Test
    fun salaryKeywordDepositIsSalaryDeposit() {
        val parsed = parser.parse(
            rawMessage = "Monthly salary AED 3500.00 has been deposited to your account no. XXXXXXXX4052.",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseAccountSuffix = "4052"
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.INCOME, parsed.type)
        assertEquals(DepositType.SALARY, parsed.depositType)
        assertEquals(350_000L, parsed.amountMinor)
    }

    @Test
    fun refundCreditIsRefundNotSalary() {
        val parsed = parser.parse(
            rawMessage = "Refund credited AED 12.25 to your account no. XXXXXXXX4052.",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseAccountSuffix = "4052"
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.INCOME, parsed.type)
        assertEquals(DepositType.REFUND, parsed.depositType)
        assertEquals(1_225L, parsed.amountMinor)
    }

    @Test
    fun accountToAccountCreditToSavingsHasTransferDepositTypeAndNoConfirmedBalance() {
        val parsed = parser.parse(
            rawMessage = "Your Ac No. XXXXXXXX4146 is credited with AED 1000.00 as Account to Account Transfer. Login to Online Banking for details.",
            manualSenderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseAccountSuffix = "4052",
                savingsAccountSuffix = "4146"
            ),
            categories = categories,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals(ParsedBankMessageType.SAVINGS_TRANSFER, parsed.type)
        assertEquals(BankMessageSourceType.SAVINGS, parsed.sourceType)
        assertEquals(DepositType.TRANSFER, parsed.depositType)
        assertEquals(100_000L, parsed.amountMinor)
        assertNull(parsed.availableBalance)
        assertNull(parsed.availableBalanceMinor)
    }

    @Test
    fun informationalBankMessagesDoNotBecomeExpenseDrafts() {
        val samples = listOf(
            "The OTP is 123456 for EUR 0.93 txn at Oracle Ireland on your card 8251. Do not share OTP with anyone.",
            "Your beneficiary has been added. It will be activated within the next 04:00 hour(s).",
            "Dear Customer, your Aani payment request of AED 100.00 to someone could not be processed. Please try again."
        )

        samples.forEach { sample ->
            val parsed = parser.parse(
                rawMessage = sample,
                manualSenderName = "BANKTEST",
                settings = FinanceDefaults.bankMessageParserSettings(),
                categories = categories,
                nowMillis = 1_800_000_000_000L
            )

            assertEquals(sample, ParsedBankMessageType.INFORMATIONAL, parsed.type)
            assertEquals(sample, "Ignored informational bank message.", parsed.ignoredReason)
        }
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
        assertEquals(FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID, pendingImport.suggestedCategoryId)
        assertEquals("Savings goal", pendingImport.suggestedCategoryName)
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
