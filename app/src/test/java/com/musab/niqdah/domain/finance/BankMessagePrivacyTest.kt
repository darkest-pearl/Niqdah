package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BankMessagePrivacyTest {
    @Test
    fun pendingImportCreatedFromAutomaticSmsDoesNotKeepRawBody() {
        val rawSms = "AED 42.50 debited at Burger King on 08/05/2026. Available balance AED 1,234.00"
        val pending = BankMessageParser().parsePendingImport(
            rawMessage = rawSms,
            senderName = "BANKTEST",
            settings = FinanceDefaults.bankMessageParserSettings().copy(
                dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true)
            ),
            categories = FinanceDefaults.budgetCategories(now = 0L),
            messageHash = "hash",
            receivedAtMillis = 1_800_000_000_000L,
            nowMillis = 1_800_000_000_000L
        )

        assertEquals("", pending.rawMessage)
        assertNotEquals(rawSms, pending.description)
    }
}
