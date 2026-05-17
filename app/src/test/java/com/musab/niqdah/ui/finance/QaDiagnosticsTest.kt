package com.musab.niqdah.ui.finance

import com.musab.niqdah.domain.finance.AccountBalanceConfidence
import com.musab.niqdah.domain.finance.AccountBalanceStatus
import com.musab.niqdah.domain.finance.AccountKind
import com.musab.niqdah.domain.finance.AccountLedgerSource
import com.musab.niqdah.domain.finance.BankMessageParserSettings
import com.musab.niqdah.domain.finance.BankMessageSourceSettings
import com.musab.niqdah.domain.finance.FinanceData
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.SalaryCycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QaDiagnosticsTest {
    @Test
    fun diagnosticsSummarizeReleaseReadinessWithoutRawSms() {
        val rawSms = "AED 42.50 debited at SECRET MERCHANT. Balance AED 1234."
        val diagnostics = buildQaDiagnostics(
            appVersionName = "0.1.0",
            appVersionCode = 7,
            buildType = "release",
            firebaseAuthStatus = "Signed in",
            isUidPresent = true,
            isSmsPermissionGranted = true,
            isNotificationPermissionGranted = false,
            aiHealthStatus = "Last response received",
            data = FinanceData.empty(uid = "uid-1").copy(
                bankMessageSettings = BankMessageParserSettings(
                    dailyUseSource = BankMessageSourceSettings("ADCB", isEnabled = true),
                    savingsSource = BankMessageSourceSettings("WIO", isEnabled = true),
                    isAutomaticSmsImportEnabled = true,
                    dailyUseAccountSuffix = "4052",
                    savingsAccountSuffix = "1122",
                    lastReceivedSender = "ADCB",
                    lastSenderMatched = true,
                    lastParsedResult = "Expense",
                    lastCreatedPendingImport = true,
                    lastDuplicateBlocked = false,
                    lastParserDecisionAtMillis = 1_800_000_000_000L,
                    lastIgnoredReason = rawSms
                ),
                accountLedgerEntries = listOf(
                    com.musab.niqdah.domain.finance.AccountLedgerEntry(
                        id = "daily-balance",
                        accountKind = AccountKind.DAILY_USE,
                        eventType = com.musab.niqdah.domain.finance.AccountLedgerEventType.BALANCE_CONFIRMED_MANUAL,
                        amountMinor = 0L,
                        balanceAfterMinor = 123_400L,
                        currency = FinanceDefaults.DEFAULT_CURRENCY,
                        confidence = AccountBalanceConfidence.CONFIRMED,
                        source = AccountLedgerSource.MANUAL,
                        createdAtMillis = 1_800_000_000_000L
                    )
                ),
                salaryCycles = listOf(
                    SalaryCycle(
                        id = "cycle",
                        userId = "uid-1",
                        cycleMonth = "2026-05",
                        salaryDepositAmountMinor = 350_000L,
                        salaryDepositDateMillis = 1_800_000_000_000L,
                        isActive = true
                    )
                )
            )
        )

        val renderedText = diagnostics.sections.flatMap { it.rows }.joinToString("\n") { "${it.label}: ${it.value}" }

        assertTrue(renderedText.contains("0.1.0 (7)"))
        assertTrue(renderedText.contains("release"))
        assertTrue(renderedText.contains("Signed in"))
        assertTrue(renderedText.contains("granted"))
        assertTrue(renderedText.contains("not granted"))
        assertTrue(renderedText.contains("ADCB"))
        assertTrue(renderedText.contains("Expense"))
        assertTrue(renderedText.contains("Active: 2026-05"))
        assertFalse(renderedText.contains("SECRET MERCHANT"))
        assertEquals("Raw SMS withheld", diagnostics.sections[2].rows.first { it.label == "Ignored reason" }.value)
    }
}
