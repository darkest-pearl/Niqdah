package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankMessageFixtureLibraryTest {
    private val parser = BankMessageParser()
    private val categories = FinanceDefaults.budgetCategories(now = 0L)
    private val settings = FinanceDefaults.bankMessageParserSettings().copy(
        dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
        savingsSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
        dailyUseAccountSuffix = "4052",
        savingsAccountSuffix = "4146"
    )
    private val now = 1_800_000_000_000L

    @Test
    fun sanitizedBankMessageFixturesAreClassified() {
        val fixtures = listOf(
            Fixture(
                message = "Thank you for using Al Islami Neo Debit Card Card ending 8251 for AED 17.25 at Aman Taxi on 18-JAN-2026 08:55 AM. Available Balance is AED 3,482.75",
                type = ParsedBankMessageType.EXPENSE,
                amountMinor = 1_725L,
                sourceSuffix = "8251",
                balanceMinor = 348_275L,
                pending = true,
                ignored = false
            ),
            Fixture(
                message = "Thank you for using Al Islami Neo Debit Card Card ending 8251 for EUR .93 at ORACLE IRELAND on 19-JAN-2026 01:22 AM. Available Balance is AED 3,442.64",
                type = ParsedBankMessageType.EXPENSE,
                amountMinor = 93L,
                currency = "EUR",
                sourceSuffix = "8251",
                balanceMinor = 344_264L,
                pending = true,
                ignored = false
            ),
            Fixture(
                message = "AED 5000.00 salary has been deposited to your account no. XXXXXXXX4052. Available Balance is AED 5,000.00",
                type = ParsedBankMessageType.INCOME,
                amountMinor = 500_000L,
                targetSuffix = "4052",
                balanceMinor = 500_000L,
                depositSubtype = DepositSubtype.SALARY,
                pending = true,
                ignored = false
            ),
            Fixture(
                message = "AED 3500.00 has been deposited to your account no. XXXXXXXX4052.",
                type = ParsedBankMessageType.INCOME,
                amountMinor = 350_000L,
                targetSuffix = "4052",
                depositSubtype = DepositSubtype.GENERAL_DEPOSIT,
                pending = true,
                ignored = false
            ),
            Fixture(
                message = "Your AC No: XXXXXXXX4052 is credited with AED 60.03 as Visa Refund.",
                type = ParsedBankMessageType.INCOME,
                amountMinor = 6_003L,
                targetSuffix = "4052",
                depositSubtype = DepositSubtype.REFUND,
                pending = true,
                ignored = false
            ),
            Fixture(
                message = "Amount of AED 1700.00 has been debited from your Mashreq account no. XXXXXXXX4052 for Account to Account Transfer.",
                type = ParsedBankMessageType.INTERNAL_TRANSFER_OUT,
                amountMinor = 170_000L,
                sourceSuffix = "4052",
                depositSubtype = DepositSubtype.UNKNOWN_INCOME,
                pending = true,
                ignored = false
            ),
            Fixture(
                message = "Your Ac No. XXXXXXXX4146 is credited with AED 1700.00 as Account to Account Transfer.",
                type = ParsedBankMessageType.SAVINGS_TRANSFER,
                amountMinor = 170_000L,
                targetSuffix = "4146",
                depositSubtype = DepositSubtype.TRANSFER_IN,
                pending = true,
                ignored = false
            ),
            Fixture(
                message = "Your Ac No. XXXXXXXX9999 is credited with AED 500.00 as Account to Account Transfer.",
                type = ParsedBankMessageType.INCOME,
                amountMinor = 50_000L,
                targetSuffix = "9999",
                depositSubtype = DepositSubtype.TRANSFER_IN,
                pending = true,
                ignored = false
            ),
            Fixture(
                message = "Your AC No:XXXXXXXX4052 is debited with AED 900.00 for Aani Instant Payments Local IPP Transfer.",
                type = ParsedBankMessageType.EXPENSE,
                amountMinor = 90_000L,
                sourceSuffix = "4052",
                pending = true,
                ignored = false
            ),
            Fixture(
                message = "The OTP is 123456 for EUR 0.93 txn at Oracle Ireland on your card 8251.",
                type = ParsedBankMessageType.INFORMATIONAL,
                pending = false,
                ignored = true
            ),
            Fixture(
                message = "Your beneficiary has been added.",
                type = ParsedBankMessageType.INFORMATIONAL,
                pending = false,
                ignored = true
            ),
            Fixture(
                message = "Dear Customer, your Aani payment request of AED 100.00 could not be processed.",
                type = ParsedBankMessageType.INFORMATIONAL,
                pending = false,
                ignored = true
            )
        )

        fixtures.forEach { fixture ->
            val parsed = parser.parse(fixture.message, "BANKTEST", settings, categories, nowMillis = now)
            assertEquals(fixture.message, fixture.type, parsed.type)
            assertEquals(fixture.message, fixture.amountMinor, parsed.amountMinor)
            assertEquals(fixture.message, fixture.currency, parsed.currency)
            assertEquals(fixture.message, fixture.sourceSuffix, parsed.sourceAccountSuffix)
            assertEquals(fixture.message, fixture.targetSuffix, parsed.targetAccountSuffix)
            assertEquals(fixture.message, fixture.balanceMinor, parsed.availableBalanceMinor)
            assertEquals(fixture.message, fixture.depositSubtype, parsed.depositSubtype)
            assertEquals(fixture.message, fixture.ignored, parsed.ignoredReason.isNotBlank())
            assertEquals(fixture.message, fixture.pending, BankMessageParserDecision.shouldCreatePendingImport(parsed))
        }
    }

    @Test
    fun salaryDepositPhrasesMapToSalarySubtype() {
        listOf(
            "You have received your salary of AED 5000.00 in account XXXXXXXX4052.",
            "Your salary has been credited with AED 5000.00 to Ac No XXXXXXXX4052.",
            "AED 5000.00 salary has been deposited to your account no. XXXXXXXX4052. Available Balance is AED 5,000.00"
        ).forEach { message ->
            val parsed = parser.parse(message, "BANKTEST", settings, categories, nowMillis = now)
            assertEquals(message, ParsedBankMessageType.INCOME, parsed.type)
            assertEquals(message, DepositSubtype.SALARY, parsed.depositSubtype)
            assertEquals(message, DepositType.SALARY, parsed.depositType)
            assertEquals(message, 500_000L, parsed.amountMinor)
            assertEquals(message, "4052", parsed.targetAccountSuffix)
        }
    }

    @Test
    fun missingAmountIsIgnoredWithReason() {
        val parsed = parser.parse("Your account XXXXXXXX4052 has been credited.", "BANKTEST", settings, categories, nowMillis = now)

        assertFalse(BankMessageParserDecision.shouldCreatePendingImport(parsed))
        assertEquals("Could not detect amount", BankMessageParserDecision.ignoredReason(parsed))
    }

    private data class Fixture(
        val message: String,
        val type: ParsedBankMessageType,
        val amountMinor: Long? = null,
        val currency: String = "AED",
        val sourceSuffix: String = "",
        val targetSuffix: String = "",
        val balanceMinor: Long? = null,
        val depositSubtype: DepositSubtype = DepositSubtype.UNKNOWN_INCOME,
        val pending: Boolean,
        val ignored: Boolean
    )
}
