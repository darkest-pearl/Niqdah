package com.musab.niqdah.data.finance

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.musab.niqdah.MainActivity
import com.musab.niqdah.R
import com.musab.niqdah.core.firebase.FirebaseProvider
import com.musab.niqdah.data.firestore.FirestoreCollections
import com.musab.niqdah.domain.finance.AccountBalanceSnapshot
import com.musab.niqdah.domain.finance.AccountLedgerEntry
import com.musab.niqdah.domain.finance.AccountLedgerRules
import com.musab.niqdah.domain.finance.AccountKind
import com.musab.niqdah.domain.finance.BankMessageImportHistory
import com.musab.niqdah.domain.finance.BankMessageImportStatus
import com.musab.niqdah.domain.finance.BankMessageSourceType
import com.musab.niqdah.domain.finance.DepositType
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.FinanceDates
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.IncomeTransaction
import com.musab.niqdah.domain.finance.InternalTransferRecord
import com.musab.niqdah.domain.finance.InternalTransferNotificationRules
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.ParsedBankMessageConfidence
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import com.musab.niqdah.domain.finance.PendingBankImport
import com.musab.niqdah.domain.finance.PendingBankImportSaveRules
import com.musab.niqdah.domain.finance.SaveResult
import com.musab.niqdah.domain.finance.SavingsGoal
import com.musab.niqdah.domain.finance.effectiveMinorUnits
import com.musab.niqdah.domain.finance.majorToMinorUnits
import com.musab.niqdah.domain.finance.minorUnitsToMajor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BankSmsNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BankSmsNotificationActions.ACTION_SAVE &&
            action != BankSmsNotificationActions.ACTION_DISMISS
        ) {
            return
        }

        val importId = intent.getStringExtra(BankSmsNotificationActions.EXTRA_IMPORT_ID).orEmpty()
        if (importId.isBlank()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        receiverScope.launch {
            try {
                val uid = FirebaseProvider.auth(appContext)?.currentUser?.uid
                if (uid.isNullOrBlank()) {
                    appContext.showActionResultNotification("Sign in to save or dismiss this import.")
                    appContext.openTransactions()
                    return@launch
                }

                val handler = PendingBankImportNotificationHandler(appContext, uid)
                when (action) {
                    BankSmsNotificationActions.ACTION_SAVE -> {
                        val result = handler.save(importId)
                        if (result is SaveResult.Saved) {
                            appContext.cancelImportNotification(importId)
                        }
                        appContext.showActionResultNotification(result.notificationMessage())
                    }
                    BankSmsNotificationActions.ACTION_DISMISS -> {
                        if (handler.dismiss(importId)) {
                            appContext.cancelImportNotification(importId)
                            appContext.showActionResultNotification("Pending import dismissed.")
                        } else {
                            appContext.showActionResultNotification(
                                "Could not dismiss this import. Open Niqdah and try again."
                            )
                        }
                    }
                }
            } catch (error: Throwable) {
                appContext.showActionResultNotification(
                    "Could not update this import: ${error.friendlyNotificationError()}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

private class PendingBankImportNotificationHandler(
    private val context: Context,
    private val uid: String
) {
    private val appContext = context.applicationContext

    suspend fun save(importId: String): SaveResult {
        val db = FirebaseProvider.firestore(appContext)
            ?: return SaveResult.Error("Firebase is unavailable.")
        val pendingImport = pendingBankImportsCollection(db)
            .document(importId)
            .get()
            .awaitValue()
            .toPendingBankImport()
            ?: return SaveResult.Error("The pending import no longer exists.")
        PendingBankImportSaveRules.validate(pendingImport)?.let { return it }
        val amount = pendingImport.amount ?: return SaveResult.Blocked("The amount is missing.")

        val now = System.currentTimeMillis()
        val paired = findTransferCounterpart(db, pendingImport)
        val notificationPlan = InternalTransferNotificationRules.notificationPlan(
            pendingImport = pendingImport,
            candidates = listOfNotNull(pendingImport, paired),
            nowMillis = now
        )
        val isPairedTransfer = PendingBankImportSaveRules.isMatchedInternalTransferPair(pendingImport, paired)
        try {
            when (pendingImport.type) {
                ParsedBankMessageType.EXPENSE -> saveExpense(db, pendingImport, amount, now)
                ParsedBankMessageType.INCOME -> saveIncome(db, pendingImport, amount, now)
                ParsedBankMessageType.SAVINGS_TRANSFER -> {
                    saveSavingsContribution(db, pendingImport, amount, now)
                    if (paired?.type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT) {
                        saveInternalTransferRecord(db, paired, pendingImport, now)
                    }
                }
                ParsedBankMessageType.INTERNAL_TRANSFER_OUT -> {
                    saveInternalTransferRecord(
                        db = db,
                        debitImport = pendingImport,
                        pairedCreditImport = paired?.takeIf { it.type == ParsedBankMessageType.SAVINGS_TRANSFER },
                        now = now
                    )
                    if (paired?.type == ParsedBankMessageType.SAVINGS_TRANSFER) {
                        saveSavingsContribution(db, paired, amount, now)
                    }
                }
                ParsedBankMessageType.INFORMATIONAL,
                ParsedBankMessageType.UNKNOWN ->
                    return SaveResult.NeedsReview("Choose a message type before saving.")
            }
        } catch (error: Throwable) {
            return if (isPairedTransfer) {
                SaveResult.Error("Could not save paired transfer: ${error.message?.takeIf { it.isNotBlank() } ?: "Unknown error"}")
            } else {
                SaveResult.Error(error.message?.takeIf { it.isNotBlank() } ?: "Could not save import.")
            }
        }
        upsertBalanceSnapshotIfPresent(db, pendingImport, now)
        upsertAccountLedgerEntry(db, pendingImport, now)
        if (paired != null) {
            upsertBalanceSnapshotIfPresent(db, paired, now)
            upsertAccountLedgerEntry(db, paired, now)
        }
        PendingBankImportSaveRules.idsToRemoveAfterSuccessfulSave(pendingImport, paired).forEach { id ->
            pendingBankImportsCollection(db).document(id).delete().awaitValue()
        }
        if (paired != null) {
            bankMessageImportHistoryCollection(db)
                .document(paired.messageHash)
                .set(
                    BankMessageImportHistory(
                        messageHash = paired.messageHash,
                        status = BankMessageImportStatus.LINKED,
                        senderName = paired.senderName,
                        updatedAtMillis = now
                    ).toFirestore()
                )
                .awaitValue()
        }
        bankMessageImportHistoryCollection(db)
            .document(pendingImport.messageHash)
            .set(
                BankMessageImportHistory(
                    messageHash = pendingImport.messageHash,
                    status = BankMessageImportStatus.SAVED,
                    senderName = pendingImport.senderName,
                    updatedAtMillis = now
                ).toFirestore()
            )
            .awaitValue()
        notificationPlan?.let { InternalTransferNotificationPublisher.cancel(appContext, it.notificationId) }
        appContext.cancelImportNotification(pendingImport.id)
        paired?.let { appContext.cancelImportNotification(it.id) }
        return SaveResult.Saved(pendingImport.saveSuccessMessage(db, paired))
    }

    suspend fun dismiss(importId: String): Boolean {
        val db = FirebaseProvider.firestore(appContext) ?: return false
        val pendingImport = pendingBankImportsCollection(db)
            .document(importId)
            .get()
            .awaitValue()
            .toPendingBankImport()
            ?: return false
        val now = System.currentTimeMillis()
        val paired = findTransferCounterpart(db, pendingImport)
        val notificationPlan = InternalTransferNotificationRules.notificationPlan(
            pendingImport = pendingImport,
            candidates = listOfNotNull(pendingImport, paired),
            nowMillis = now
        )
        PendingBankImportSaveRules.idsToRemoveAfterSuccessfulSave(pendingImport, paired).forEach { id ->
            pendingBankImportsCollection(db).document(id).delete().awaitValue()
        }
        bankMessageImportHistoryCollection(db)
            .document(pendingImport.messageHash)
            .set(
                BankMessageImportHistory(
                    messageHash = pendingImport.messageHash,
                    status = BankMessageImportStatus.DISMISSED,
                    senderName = pendingImport.senderName,
                    updatedAtMillis = now
                ).toFirestore()
            )
            .awaitValue()
        if (paired != null) {
            bankMessageImportHistoryCollection(db)
                .document(paired.messageHash)
                .set(
                    BankMessageImportHistory(
                        messageHash = paired.messageHash,
                        status = BankMessageImportStatus.DISMISSED,
                        senderName = paired.senderName,
                        updatedAtMillis = now
                    ).toFirestore()
                )
                .awaitValue()
        }
        notificationPlan?.let { InternalTransferNotificationPublisher.cancel(appContext, it.notificationId) }
        appContext.cancelImportNotification(pendingImport.id)
        paired?.let { appContext.cancelImportNotification(it.id) }
        return true
    }

    private suspend fun saveExpense(
        db: FirebaseFirestore,
        pendingImport: PendingBankImport,
        amount: Double,
        now: Long
    ) {
        transactionsCollection(db)
            .document(UUID.randomUUID().toString())
            .set(
                ExpenseTransaction(
                    id = "",
                    categoryId = pendingImport.suggestedCategoryId.orEmpty(),
                    amount = amount,
                    amountMinor = pendingImport.amountMinor ?: majorToMinorUnits(amount),
                    currency = pendingImport.currency,
                    note = pendingImport.noteWithReviewContext(),
                    necessity = pendingImport.suggestedNecessity,
                    occurredAtMillis = pendingImport.occurredAtMillis,
                    yearMonth = FinanceDates.yearMonthFromMillis(pendingImport.occurredAtMillis),
                    createdAtMillis = now,
                    updatedAtMillis = now
                ).toFirestore()
            )
            .awaitValue()
    }

    private suspend fun saveIncome(
        db: FirebaseFirestore,
        pendingImport: PendingBankImport,
        amount: Double,
        now: Long
    ) {
        incomeTransactionsCollection(db)
            .document(UUID.randomUUID().toString())
            .set(
                IncomeTransaction(
                    id = "",
                    amount = amount,
                    amountMinor = pendingImport.amountMinor ?: majorToMinorUnits(amount),
                    currency = pendingImport.currency,
                    source = pendingImport.senderName.trim(),
                    note = pendingImport.noteWithReviewContext(),
                    depositType = pendingImport.depositType,
                    occurredAtMillis = pendingImport.occurredAtMillis,
                    yearMonth = FinanceDates.yearMonthFromMillis(pendingImport.occurredAtMillis),
                    createdAtMillis = now,
                    updatedAtMillis = now
                ).toFirestore()
            )
            .awaitValue()
    }

    private suspend fun saveSavingsContribution(
        db: FirebaseFirestore,
        pendingImport: PendingBankImport,
        amount: Double,
        now: Long
    ) {
        val yearMonth = FinanceDates.yearMonthFromMillis(pendingImport.occurredAtMillis)
        val transactionId = "bank-message-savings-$yearMonth"
        val transactionSnapshot = transactionsCollection(db).document(transactionId).get().awaitValue()
        val existingAmount = (transactionSnapshot.get("amount") as? Number)?.toDouble() ?: 0.0
        val existingAmountMinor = (transactionSnapshot.get("amountMinor") as? Number)?.toLong()
            ?: majorToMinorUnits(existingAmount)
        val amountMinor = pendingImport.amountMinor ?: majorToMinorUnits(amount)
        val existingCreatedAt = (transactionSnapshot.get("createdAtMillis") as? Number)?.toLong() ?: now
        val existingOccurredAt = (transactionSnapshot.get("occurredAtMillis") as? Number)?.toLong()
            ?: pendingImport.occurredAtMillis

        transactionsCollection(db)
            .document(transactionId)
            .set(
                ExpenseTransaction(
                    id = transactionId,
                    categoryId = FinanceDefaults.MARRIAGE_SAVINGS_CATEGORY_ID,
                    amount = minorUnitsToMajor(existingAmountMinor + amountMinor),
                    amountMinor = existingAmountMinor + amountMinor,
                    currency = pendingImport.currency,
                    note = pendingImport.noteWithReviewContext().ifBlank { "Imported savings transfer" },
                    necessity = NecessityLevel.NECESSARY,
                    occurredAtMillis = existingOccurredAt,
                    yearMonth = yearMonth,
                    createdAtMillis = existingCreatedAt,
                    updatedAtMillis = now
                ).toFirestore()
            )
            .awaitValue()

        val goalSnapshot = goalsCollection(db).document(FinanceDefaults.MARRIAGE_GOAL_ID).get().awaitValue()
        val defaultGoal = FinanceDefaults.savingsGoals(now).first { it.id == FinanceDefaults.MARRIAGE_GOAL_ID }
        val goal = goalSnapshot.toSavingsGoal(defaultGoal)
        goalsCollection(db)
            .document(FinanceDefaults.MARRIAGE_GOAL_ID)
            .set(
                run {
                    val savedAmountMinor = effectiveMinorUnits(goal.savedAmountMinor, goal.savedAmount) + amountMinor
                    goal.copy(
                        savedAmount = minorUnitsToMajor(savedAmountMinor),
                        savedAmountMinor = savedAmountMinor,
                        updatedAtMillis = now
                    )
                }.toFirestore()
            )
            .awaitValue()
    }

    private suspend fun upsertAccountLedgerEntry(
        db: FirebaseFirestore,
        pendingImport: PendingBankImport,
        now: Long
    ) {
        if (pendingImport.type == ParsedBankMessageType.INFORMATIONAL ||
            pendingImport.type == ParsedBankMessageType.UNKNOWN
        ) {
            return
        }
        val entry = AccountLedgerRules.pendingImportEntry(
            pendingImport = pendingImport,
            previousBalance = null,
            nowMillis = now
        )
        accountLedgerEntriesCollection(db)
            .document(entry.id)
            .set(entry.toFirestore())
            .awaitValue()
    }

    private suspend fun saveInternalTransferRecord(
        db: FirebaseFirestore,
        debitImport: PendingBankImport,
        pairedCreditImport: PendingBankImport?,
        now: Long
    ) {
        val record = PendingBankImportSaveRules.internalTransferRecord(
            debitImport = debitImport,
            pairedCreditImport = pairedCreditImport,
            nowMillis = now
        )
        internalTransferRecordsCollection(db)
            .document(record.id)
            .set(record.toFirestore())
            .awaitValue()
    }

    private suspend fun upsertBalanceSnapshotIfPresent(
        db: FirebaseFirestore,
        pendingImport: PendingBankImport,
        now: Long
    ) {
        val availableBalance = pendingImport.availableBalance ?: return
        val accountKind = when (pendingImport.sourceType) {
            BankMessageSourceType.DAILY_USE -> AccountKind.DAILY_USE
            BankMessageSourceType.SAVINGS -> AccountKind.SAVINGS
            BankMessageSourceType.UNKNOWN -> {
                if (pendingImport.type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT) {
                    AccountKind.DAILY_USE
                } else {
                    return
                }
            }
        }
        val snapshot = AccountBalanceSnapshot(
            accountKind = accountKind,
            sender = pendingImport.senderName,
            availableBalance = availableBalance,
            currency = pendingImport.availableBalanceCurrency.ifBlank { pendingImport.currency },
            messageTimestampMillis = pendingImport.occurredAtMillis,
            sourceMessageHash = pendingImport.messageHash,
            createdAtMillis = now,
            availableBalanceMinor = pendingImport.availableBalanceMinor ?: majorToMinorUnits(availableBalance)
        )
        accountBalanceSnapshotsCollection(db)
            .document(snapshot.documentId())
            .set(snapshot.toFirestore())
            .awaitValue()
    }

    private suspend fun latestDailyUseBalance(db: FirebaseFirestore): AccountBalanceSnapshot? =
        accountBalanceSnapshotsCollection(db)
            .get()
            .awaitValue()
            .documents
            .mapNotNull { it.toAccountBalanceSnapshot() }
            .filter { it.accountKind == AccountKind.DAILY_USE }
            .maxWithOrNull(
                compareBy<AccountBalanceSnapshot> { it.messageTimestampMillis }
                    .thenBy { it.createdAtMillis }
            )

    private suspend fun findTransferCounterpart(
        db: FirebaseFirestore,
        pendingImport: PendingBankImport
    ): PendingBankImport? {
        if (pendingImport.type != ParsedBankMessageType.SAVINGS_TRANSFER &&
            pendingImport.type != ParsedBankMessageType.INTERNAL_TRANSFER_OUT
        ) {
            return null
        }
        val amount = pendingImport.amount ?: return null
        val dayMillis = 24 * 60 * 60 * 1000L
        return pendingBankImportsCollection(db)
            .get()
            .awaitValue()
            .documents
            .mapNotNull { it.toPendingBankImport() }
            .firstOrNull { candidate ->
                candidate.id != pendingImport.id &&
                    candidate.currency == pendingImport.currency &&
                    candidate.amount == amount &&
                    kotlin.math.abs(candidate.occurredAtMillis - pendingImport.occurredAtMillis) <= dayMillis &&
                    setOf(candidate.type, pendingImport.type) == setOf(
                        ParsedBankMessageType.SAVINGS_TRANSFER,
                        ParsedBankMessageType.INTERNAL_TRANSFER_OUT
                    )
            }
    }

    private fun userDocument(db: FirebaseFirestore) =
        db.collection(FirestoreCollections.USERS).document(uid)

    private fun transactionsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.TRANSACTIONS)

    private fun incomeTransactionsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.INCOME_TRANSACTIONS)

    private fun pendingBankImportsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.PENDING_BANK_IMPORTS)

    private fun bankMessageImportHistoryCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.BANK_MESSAGE_IMPORT_HISTORY)

    private fun accountBalanceSnapshotsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.ACCOUNT_BALANCE_SNAPSHOTS)

    private fun accountLedgerEntriesCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.ACCOUNT_LEDGER_ENTRIES)

    private fun internalTransferRecordsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.INTERNAL_TRANSFER_RECORDS)

    private fun goalsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.SAVINGS_GOALS)

    private fun DocumentSnapshot.toPendingBankImport(): PendingBankImport? {
        if (!exists()) return null
        return PendingBankImport(
            id = id,
            messageHash = getString("messageHash") ?: id,
            senderName = getString("senderName") ?: "",
            rawMessage = getString("rawMessage") ?: "",
            sourceType = BankMessageSourceType.entries.firstOrNull { it.name == getString("sourceType") }
                ?: BankMessageSourceType.UNKNOWN,
            type = ParsedBankMessageType.entries.firstOrNull { it.name == getString("type") }
                ?: ParsedBankMessageType.UNKNOWN,
            amount = nullableDouble("amount"),
            currency = getString("currency") ?: FinanceDefaults.DEFAULT_CURRENCY,
            availableBalance = nullableDouble("availableBalance"),
            availableBalanceCurrency = getString("availableBalanceCurrency") ?: FinanceDefaults.DEFAULT_CURRENCY,
            originalForeignAmount = nullableDouble("originalForeignAmount"),
            originalForeignCurrency = getString("originalForeignCurrency") ?: "",
            inferredAccountDebit = nullableDouble("inferredAccountDebit"),
            isAmountInferredFromBalance = getBoolean("isAmountInferredFromBalance") ?: false,
            reviewNote = getString("reviewNote") ?: "",
            merchantName = getString("merchantName") ?: "",
            sourceAccountSuffix = getString("sourceAccountSuffix") ?: "",
            targetAccountSuffix = getString("targetAccountSuffix") ?: "",
            ignoredReason = getString("ignoredReason") ?: "",
            pairedTransferStatus = getString("pairedTransferStatus") ?: "",
            description = getString("description") ?: "",
            occurredAtMillis = long("occurredAtMillis"),
            suggestedCategoryId = getString("suggestedCategoryId"),
            suggestedCategoryName = getString("suggestedCategoryName") ?: "Uncategorized",
            suggestedNecessity = NecessityLevel.entries.firstOrNull {
                it.name == getString("suggestedNecessity")
            } ?: NecessityLevel.NECESSARY,
            confidence = ParsedBankMessageConfidence.entries.firstOrNull { it.name == getString("confidence") }
                ?: ParsedBankMessageConfidence.LOW,
            receivedAtMillis = long("receivedAtMillis"),
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis"),
            amountMinor = nullableLong("amountMinor") ?: nullableDouble("amount")?.let { majorToMinorUnits(it) },
            availableBalanceMinor = nullableLong("availableBalanceMinor")
                ?: nullableDouble("availableBalance")?.let { majorToMinorUnits(it) },
            originalForeignAmountMinor = nullableLong("originalForeignAmountMinor")
                ?: nullableDouble("originalForeignAmount")?.let { majorToMinorUnits(it) },
            inferredAccountDebitMinor = nullableLong("inferredAccountDebitMinor")
                ?: nullableDouble("inferredAccountDebit")?.let { majorToMinorUnits(it) },
            depositType = DepositType.entries.firstOrNull { it.name == getString("depositType") }
                ?: DepositType.OTHER_INCOME
        )
    }

    private fun DocumentSnapshot.toSavingsGoal(default: SavingsGoal): SavingsGoal =
        if (!exists()) {
            default
        } else {
            SavingsGoal(
                id = id,
                name = getString("name") ?: default.name,
                targetAmount = nullableDouble("targetAmount") ?: default.targetAmount,
                savedAmount = nullableDouble("savedAmount") ?: default.savedAmount,
                createdAtMillis = long("createdAtMillis", default.createdAtMillis),
                updatedAtMillis = long("updatedAtMillis", default.updatedAtMillis),
                targetAmountMinor = long(
                    "targetAmountMinor",
                    majorToMinorUnits(nullableDouble("targetAmount") ?: default.targetAmount)
                ),
                savedAmountMinor = long(
                    "savedAmountMinor",
                    majorToMinorUnits(nullableDouble("savedAmount") ?: default.savedAmount)
                )
            )
        }

    private fun ExpenseTransaction.toFirestore(): Map<String, Any> =
        mapOf(
            "categoryId" to categoryId,
            "amount" to amount,
            "amountMinor" to effectiveMinorUnits(amountMinor, amount),
            "currency" to currency,
            "note" to note,
            "necessity" to necessity.name,
            "occurredAtMillis" to occurredAtMillis,
            "yearMonth" to yearMonth,
            "createdAtMillis" to createdAtMillis,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun IncomeTransaction.toFirestore(): Map<String, Any> =
        mapOf(
            "amount" to amount,
            "amountMinor" to effectiveMinorUnits(amountMinor, amount),
            "currency" to currency,
            "source" to source,
            "note" to note,
            "depositType" to depositType.name,
            "occurredAtMillis" to occurredAtMillis,
            "yearMonth" to yearMonth,
            "createdAtMillis" to createdAtMillis,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun BankMessageImportHistory.toFirestore(): Map<String, Any> =
        mapOf(
            "messageHash" to messageHash,
            "status" to status.name,
            "senderName" to senderName,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun AccountBalanceSnapshot.toFirestore(): Map<String, Any> =
        mapOf(
            "accountKind" to accountKind.name,
            "sender" to sender,
            "availableBalance" to availableBalance,
            "availableBalanceMinor" to effectiveMinorUnits(availableBalanceMinor, availableBalance),
            "currency" to currency,
            "messageTimestampMillis" to messageTimestampMillis,
            "sourceMessageHash" to sourceMessageHash,
            "createdAtMillis" to createdAtMillis
        )

    private fun AccountLedgerEntry.toFirestore(): Map<String, Any?> =
        mapOf(
            "accountKind" to accountKind.name,
            "accountSuffix" to accountSuffix,
            "eventType" to eventType.name,
            "amountMinor" to amountMinor,
            "balanceAfterMinor" to balanceAfterMinor,
            "currency" to currency,
            "confidence" to confidence.name,
            "source" to source.name,
            "relatedTransactionId" to relatedTransactionId,
            "createdAtMillis" to createdAtMillis,
            "note" to note
        )

    private fun InternalTransferRecord.toFirestore(): Map<String, Any?> =
        mapOf(
            "amount" to amount,
            "amountMinor" to effectiveMinorUnits(amountMinor, amount),
            "currency" to currency,
            "sourceAccountSuffix" to sourceAccountSuffix,
            "targetAccountSuffix" to targetAccountSuffix,
            "direction" to direction.name,
            "transferType" to transferType.name,
            "status" to status.name,
            "pairedImportId" to pairedImportId,
            "createdAtMillis" to createdAtMillis,
            "messageTimestampMillis" to messageTimestampMillis,
            "note" to note,
            "sourceMessageHash" to sourceMessageHash
        )

    private fun SavingsGoal.toFirestore(): Map<String, Any> =
        mapOf(
            "name" to name,
            "targetAmount" to targetAmount,
            "targetAmountMinor" to effectiveMinorUnits(targetAmountMinor, targetAmount),
            "savedAmount" to savedAmount,
            "savedAmountMinor" to effectiveMinorUnits(savedAmountMinor, savedAmount),
            "createdAtMillis" to createdAtMillis,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun DocumentSnapshot.toAccountBalanceSnapshot(): AccountBalanceSnapshot? {
        val accountKind = AccountKind.entries.firstOrNull { it.name == getString("accountKind") }
            ?: return null
        return AccountBalanceSnapshot(
            accountKind = accountKind,
            sender = getString("sender") ?: "",
            availableBalance = nullableDouble("availableBalance") ?: 0.0,
            currency = getString("currency") ?: FinanceDefaults.DEFAULT_CURRENCY,
            messageTimestampMillis = long("messageTimestampMillis"),
            sourceMessageHash = getString("sourceMessageHash") ?: id.substringAfter("-", id),
            createdAtMillis = long("createdAtMillis"),
            availableBalanceMinor = long(
                "availableBalanceMinor",
                majorToMinorUnits(nullableDouble("availableBalance") ?: 0.0)
            )
        )
    }

    private fun PendingBankImport.noteWithReviewContext(): String =
        listOf(description.trim(), reviewNote.trim())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")

    private suspend fun PendingBankImport.saveSuccessMessage(
        db: FirebaseFirestore,
        paired: PendingBankImport?
    ): String =
        when {
            PendingBankImportSaveRules.isMatchedInternalTransferPair(this, paired) ->
                PendingBankImportSaveRules.pairedInternalTransferSavedMessage()
            type == ParsedBankMessageType.SAVINGS_TRANSFER ->
                PendingBankImportSaveRules.savingsTransferSavedMessage(this)
            type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT ->
                PendingBankImportSaveRules.internalTransferSavedMessage(
                    pendingImport = this,
                    latestDailyUseBalance = latestDailyUseBalance(db),
                    notificationStyle = true
                )
            else -> "Saved to Niqdah."
        }

    private fun AccountBalanceSnapshot.documentId(): String =
        "${accountKind.name}-$sourceMessageHash"

    private fun DocumentSnapshot.nullableDouble(field: String): Double? =
        (get(field) as? Number)?.toDouble()

    private fun DocumentSnapshot.nullableLong(field: String): Long? =
        (get(field) as? Number)?.toLong()

    private fun DocumentSnapshot.long(field: String, default: Long = 0L): Long =
        (get(field) as? Number)?.toLong() ?: default

    private suspend fun <T> Task<T>.awaitValue(): T =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("Firestore request failed.")
                    )
                }
            }
        }
}

private fun SaveResult.notificationMessage(): String =
    when (this) {
        is SaveResult.Saved -> message
        is SaveResult.NeedsReview -> "Could not save: $message"
        is SaveResult.Blocked -> "Could not save: $reason"
        is SaveResult.Ignored -> reason
        is SaveResult.Error -> "Could not save: $reason"
    }

private fun Context.openTransactions() {
    val intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(MainActivity.EXTRA_OPEN_TRANSACTIONS, true)
    }
    startActivity(intent)
}

private fun Context.cancelImportNotification(importId: String) {
    getSystemService(NotificationManager::class.java).cancel(importId.hashCode())
}

private fun Context.showActionResultNotification(message: String) {
    if (!canPostNotifications()) return
    val notificationManager = getSystemService(NotificationManager::class.java)
    val channelId = NiqdahNotificationChannels.ensureBankImportReviews(this)
    val notification = Notification.Builder(this, channelId)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Niqdah")
        .setContentText(message)
        .setAutoCancel(true)
        .build()
    notificationManager.notify(message.hashCode(), notification)
}

private fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

private fun Throwable.friendlyNotificationError(): String =
    message?.takeIf { it.isNotBlank() }
        ?: "Open Niqdah and try again."
