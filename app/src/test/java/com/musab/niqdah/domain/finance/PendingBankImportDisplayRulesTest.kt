package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingBankImportDisplayRulesTest {
    private val parser = BankMessageParser()
    private val categories = FinanceDefaults.budgetCategories(now = 0L)
    private val settings = FinanceDefaults.bankMessageParserSettings().copy(
        dailyUseSource = BankMessageSourceSettings(senderName = "BANKTEST", isEnabled = true),
        dailyUseAccountSuffix = "4052",
        savingsAccountSuffix = "4146"
    )
    private val now = 1_800_000_000_000L

    @Test
    fun debitOnlyGroupsAsWaitingTransferWithoutPrimarySave() {
        val grouped = PendingBankImportDisplayRules.group(listOf(debitPendingImport()))
        val item = grouped.single() as PendingBankImportDisplayItem.InternalTransferWaiting

        assertEquals(ParsedBankMessageType.INTERNAL_TRANSFER_OUT, item.primaryImport.type)
        assertFalse(item.isPrimarySaveVisible)
        assertTrue(item.isManualSaveVisible)
    }

    @Test
    fun matchingDebitAndCreditGroupAsOneReadyPairWithSave() {
        val debit = debitPendingImport()
        val credit = creditPendingImport()
        val grouped = PendingBankImportDisplayRules.group(listOf(debit, credit))
        val item = grouped.single() as PendingBankImportDisplayItem.InternalTransferReadyPair

        assertEquals(debit.id, item.debitImport.id)
        assertEquals(credit.id, item.creditImport.id)
        assertTrue(item.isPrimarySaveVisible)
        assertFalse(item.isManualSaveVisible)
    }

    @Test
    fun creditOnlyGroupsAsSavingsTransferWithSave() {
        val credit = creditPendingImport()
        val grouped = PendingBankImportDisplayRules.group(listOf(credit))
        val item = grouped.single() as PendingBankImportDisplayItem.CreditOnlySavingsTransfer

        assertEquals(credit.id, item.creditImport.id)
        assertTrue(item.isPrimarySaveVisible)
    }

    @Test
    fun unrelatedExpenseAndInternalTransferPairRenderAsTwoCards() {
        val grouped = PendingBankImportDisplayRules.group(
            listOf(expensePendingImport(), debitPendingImport(), creditPendingImport())
        )

        assertEquals(2, grouped.size)
        assertEquals(1, grouped.count { it is PendingBankImportDisplayItem.SinglePendingImport })
        assertEquals(1, grouped.count { it is PendingBankImportDisplayItem.InternalTransferReadyPair })
    }

    @Test
    fun matchedPairDismissIdsIncludeBothPendingImports() {
        val debit = debitPendingImport()
        val credit = creditPendingImport()
        val item = PendingBankImportDisplayRules.group(listOf(debit, credit))
            .single() as PendingBankImportDisplayItem.InternalTransferReadyPair

        assertEquals(
            listOf(debit.id, credit.id),
            PendingBankImportDisplayRules.idsToDismiss(item)
        )
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

    private fun expensePendingImport(): PendingBankImport =
        parser.parsePendingImport(
            rawMessage = "AED 42.50 debited at Burger King on 08/05/2026. Available balance AED 1,234.00",
            senderName = "BANKTEST",
            settings = settings,
            categories = categories,
            messageHash = "expense-hash",
            receivedAtMillis = now + 120_000L,
            nowMillis = now + 120_000L
        )
}
