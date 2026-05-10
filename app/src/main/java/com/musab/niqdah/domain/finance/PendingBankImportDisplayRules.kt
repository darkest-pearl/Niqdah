package com.musab.niqdah.domain.finance

sealed interface PendingBankImportDisplayItem {
    val key: String
    val primaryImport: PendingBankImport
    val imports: List<PendingBankImport>
    val isPrimarySaveVisible: Boolean
    val isManualSaveVisible: Boolean

    data class SinglePendingImport(
        val pendingImport: PendingBankImport
    ) : PendingBankImportDisplayItem {
        override val key: String = "single-${pendingImport.id}"
        override val primaryImport: PendingBankImport = pendingImport
        override val imports: List<PendingBankImport> = listOf(pendingImport)
        override val isPrimarySaveVisible: Boolean = true
        override val isManualSaveVisible: Boolean = false
    }

    data class InternalTransferWaiting(
        val debitImport: PendingBankImport
    ) : PendingBankImportDisplayItem {
        override val key: String = "internal-waiting-${debitImport.id}"
        override val primaryImport: PendingBankImport = debitImport
        override val imports: List<PendingBankImport> = listOf(debitImport)
        override val isPrimarySaveVisible: Boolean = false
        override val isManualSaveVisible: Boolean = true
    }

    data class InternalTransferReadyPair(
        val debitImport: PendingBankImport,
        val creditImport: PendingBankImport
    ) : PendingBankImportDisplayItem {
        override val key: String = "internal-pair-${debitImport.id}-${creditImport.id}"
        override val primaryImport: PendingBankImport = debitImport
        override val imports: List<PendingBankImport> = listOf(debitImport, creditImport)
        override val isPrimarySaveVisible: Boolean = true
        override val isManualSaveVisible: Boolean = false
    }

    data class CreditOnlySavingsTransfer(
        val creditImport: PendingBankImport
    ) : PendingBankImportDisplayItem {
        override val key: String = "savings-credit-only-${creditImport.id}"
        override val primaryImport: PendingBankImport = creditImport
        override val imports: List<PendingBankImport> = listOf(creditImport)
        override val isPrimarySaveVisible: Boolean = true
        override val isManualSaveVisible: Boolean = false
    }
}

object PendingBankImportDisplayRules {
    fun group(pendingImports: List<PendingBankImport>): List<PendingBankImportDisplayItem> {
        val seenIds = mutableSetOf<String>()
        return pendingImports
            .sortedByDescending { it.displaySortMillis() }
            .mapNotNull { pendingImport ->
                if (!seenIds.add(pendingImport.id)) return@mapNotNull null
                when (pendingImport.type) {
                    ParsedBankMessageType.INTERNAL_TRANSFER_OUT -> {
                        val paired = PendingBankImportSaveRules.findMatchingTransferCounterpart(
                            pendingImport = pendingImport,
                            candidates = pendingImports
                        )
                        if (paired?.type == ParsedBankMessageType.SAVINGS_TRANSFER) {
                            seenIds.add(paired.id)
                            PendingBankImportDisplayItem.InternalTransferReadyPair(
                                debitImport = pendingImport,
                                creditImport = paired
                            )
                        } else {
                            PendingBankImportDisplayItem.InternalTransferWaiting(pendingImport)
                        }
                    }
                    ParsedBankMessageType.SAVINGS_TRANSFER -> {
                        val paired = PendingBankImportSaveRules.findMatchingTransferCounterpart(
                            pendingImport = pendingImport,
                            candidates = pendingImports
                        )
                        if (paired?.type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT) {
                            seenIds.add(paired.id)
                            PendingBankImportDisplayItem.InternalTransferReadyPair(
                                debitImport = paired,
                                creditImport = pendingImport
                            )
                        } else if (pendingImport.targetAccountSuffix.isNotBlank()) {
                            PendingBankImportDisplayItem.CreditOnlySavingsTransfer(pendingImport)
                        } else {
                            PendingBankImportDisplayItem.SinglePendingImport(pendingImport)
                        }
                    }
                    ParsedBankMessageType.EXPENSE,
                    ParsedBankMessageType.INCOME,
                    ParsedBankMessageType.INFORMATIONAL,
                    ParsedBankMessageType.UNKNOWN ->
                        PendingBankImportDisplayItem.SinglePendingImport(pendingImport)
                }
            }
    }

    fun idsToDismiss(displayItem: PendingBankImportDisplayItem): List<String> =
        displayItem.imports.map { it.id }.distinct()

    private fun PendingBankImport.displaySortMillis(): Long =
        maxOf(createdAtMillis, receivedAtMillis, occurredAtMillis)
}
