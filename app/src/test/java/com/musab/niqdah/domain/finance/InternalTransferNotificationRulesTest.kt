package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternalTransferNotificationRulesTest {
    private val parser = BankMessageParser()
    private val categories = FinanceDefaults.budgetCategories(now = 0L)
    private val settings = FinanceDefaults.bankMessageParserSettings().copy(
        dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
        dailyUseAccountSuffix = "4052",
        savingsAccountSuffix = "4146"
    )
    private val now = 1_800_000_000_000L

    @Test
    fun debitFirstCreatesWaitingNotificationWithoutSaveAction() {
        val debit = debitPendingImport()
        val plan = InternalTransferNotificationRules.notificationPlan(
            pendingImport = debit,
            candidates = listOf(debit),
            nowMillis = now,
            reminderThresholdMinutes = 10
        )

        assertEquals(InternalTransferNotificationState.WAITING, plan?.state)
        assertEquals("Internal transfer pending", plan?.title)
        assertTrue(plan?.text.orEmpty().contains("AED 1,000"))
        assertTrue(plan?.text.orEmpty().contains("4052"))
        assertFalse(plan?.actions.orEmpty().contains(InternalTransferNotificationAction.SAVE))
        assertTrue(plan?.actions.orEmpty().contains(InternalTransferNotificationAction.REVIEW))
        assertTrue(plan?.actions.orEmpty().contains(InternalTransferNotificationAction.DISMISS))
    }

    @Test
    fun creditAfterDebitUpdatesSameNotificationWithSaveAction() {
        val debit = debitPendingImport()
        val credit = creditPendingImport()
        val waiting = InternalTransferNotificationRules.notificationPlan(
            pendingImport = debit,
            candidates = listOf(debit),
            nowMillis = now,
            reminderThresholdMinutes = 10
        )
        val ready = InternalTransferNotificationRules.notificationPlan(
            pendingImport = credit,
            candidates = listOf(debit, credit),
            nowMillis = now + 60_000L,
            reminderThresholdMinutes = 10
        )

        assertEquals(waiting?.notificationId, ready?.notificationId)
        assertEquals(InternalTransferNotificationState.READY, ready?.state)
        assertEquals("Internal transfer ready", ready?.title)
        assertTrue(ready?.text.orEmpty().contains("from 4052 to 4146"))
        assertTrue(ready?.actions.orEmpty().contains(InternalTransferNotificationAction.SAVE))
        assertTrue(ready?.actions.orEmpty().contains(InternalTransferNotificationAction.EDIT))
        assertTrue(ready?.actions.orEmpty().contains(InternalTransferNotificationAction.DISMISS))
    }

    @Test
    fun reminderTriggersOnlyWhenUnmatchedDebitPassesThreshold() {
        val debit = debitPendingImport().agedBy(minutes = 11)
        val plan = InternalTransferNotificationRules.notificationPlan(
            pendingImport = debit,
            candidates = listOf(debit),
            nowMillis = now,
            reminderThresholdMinutes = 10
        )
        val notYet = InternalTransferNotificationRules.notificationPlan(
            pendingImport = debitPendingImport().agedBy(minutes = 5),
            candidates = listOf(debit),
            nowMillis = now,
            reminderThresholdMinutes = 10
        )

        assertEquals(InternalTransferNotificationState.REMINDER, plan?.state)
        assertEquals("Transfer still waiting for matching credit", plan?.title)
        assertEquals(InternalTransferNotificationState.WAITING, notYet?.state)
    }

    @Test
    fun matchedDebitBeforeThresholdDoesNotTriggerReminder() {
        val debit = debitPendingImport().agedBy(minutes = 11)
        val credit = creditPendingImport()
        val plan = InternalTransferNotificationRules.notificationPlan(
            pendingImport = debit,
            candidates = listOf(debit, credit),
            nowMillis = now,
            reminderThresholdMinutes = 10
        )

        assertEquals(InternalTransferNotificationState.READY, plan?.state)
        assertEquals("Internal transfer ready", plan?.title)
    }

    @Test
    fun creditAfterReminderUsesSameNotificationIdAndReadyState() {
        val debit = debitPendingImport().agedBy(minutes = 11)
        val credit = creditPendingImport()
        val reminder = InternalTransferNotificationRules.notificationPlan(
            pendingImport = debit,
            candidates = listOf(debit),
            nowMillis = now,
            reminderThresholdMinutes = 10
        )
        val ready = InternalTransferNotificationRules.notificationPlan(
            pendingImport = credit,
            candidates = listOf(debit, credit),
            nowMillis = now + 60_000L,
            reminderThresholdMinutes = 10
        )

        assertEquals(InternalTransferNotificationState.REMINDER, reminder?.state)
        assertEquals(reminder?.notificationId, ready?.notificationId)
        assertEquals(InternalTransferNotificationState.READY, ready?.state)
    }

    @Test
    fun matchedPairSaveButtonLabelIsPlainSave() {
        assertEquals("Save", PendingBankImportSaveRules.saveButtonLabel(isSaving = false))
        assertEquals("Saving...", PendingBankImportSaveRules.saveButtonLabel(isSaving = true))
    }

    private fun debitPendingImport(): PendingBankImport =
        parser.parsePendingImport(
            rawMessage = "Amount of AED 1000.00 has been debited from your Mashreq account no. XXXXXXXX4052 for Account to Account Transfer. Login to Online Banking for details.",
            senderName = "BANKTEST",
            settings = settings,
            categories = categories,
            messageHash = "debit-hash",
            receivedAtMillis = now,
            nowMillis = now
        )

    private fun creditPendingImport(): PendingBankImport =
        parser.parsePendingImport(
            rawMessage = "Your Ac No. XXXXXXXX4146 is credited with AED 1000.00 as Account to Account Transfer. Login to Online Banking for details.",
            senderName = "BANKTEST",
            settings = settings,
            categories = categories,
            messageHash = "credit-hash",
            receivedAtMillis = now + 60_000L,
            nowMillis = now + 60_000L
        )

    private fun PendingBankImport.agedBy(minutes: Int): PendingBankImport {
        val ageMillis = minutes * 60_000L
        return copy(
            receivedAtMillis = now - ageMillis,
            createdAtMillis = now - ageMillis
        )
    }
}
