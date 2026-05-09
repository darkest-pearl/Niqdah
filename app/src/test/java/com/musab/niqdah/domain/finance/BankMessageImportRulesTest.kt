package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankMessageImportRulesTest {
    @Test
    fun senderWhitelistMatchesConfiguredBankSendersOnly() {
        val settings = FinanceDefaults.bankMessageParserSettings().copy(
            dailyUseSource = BankMessageSourceSettings(senderName = "DailyBank", isEnabled = true),
            savingsSource = BankMessageSourceSettings(senderName = "SavingsBank", isEnabled = true)
        )

        assertTrue(BankMessageImportRules.isSenderWhitelisted("AD-DailyBank", settings))
        assertTrue(BankMessageImportRules.isSenderWhitelisted("SavingsBank", settings))
        assertFalse(BankMessageImportRules.isSenderWhitelisted("RandomShop", settings))
    }

    @Test
    fun disabledSenderDoesNotMatchWhitelist() {
        val settings = FinanceDefaults.bankMessageParserSettings().copy(
            dailyUseSource = BankMessageSourceSettings(senderName = "DailyBank", isEnabled = false),
            savingsSource = BankMessageSourceSettings(senderName = "SavingsBank", isEnabled = true)
        )

        assertFalse(BankMessageImportRules.isSenderWhitelisted("DailyBank", settings))
    }

    @Test
    fun duplicateHashUsesSenderBodyAndRoundedTimestamp() {
        val first = BankMessageImportRules.hashFor(
            senderName = "DailyBank",
            messageBody = "AED 42 debited",
            receivedAtMillis = 1_800_000_001_000L
        )
        val sameBucket = BankMessageImportRules.hashFor(
            senderName = "AD-DailyBank",
            messageBody = "AED 42 debited",
            receivedAtMillis = 1_800_000_090_000L
        )
        val differentBody = BankMessageImportRules.hashFor(
            senderName = "DailyBank",
            messageBody = "AED 43 debited",
            receivedAtMillis = 1_800_000_001_000L
        )

        assertNotEquals(first, sameBucket)
        assertNotEquals(first, differentBody)
        assertEquals(
            first,
            BankMessageImportRules.hashFor("DailyBank", "AED 42 debited", 1_800_000_050_000L)
        )
    }
}
