package com.musab.niqdah.data.finance

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
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
import com.musab.niqdah.domain.finance.BankMessageImportHistory
import com.musab.niqdah.domain.finance.BankMessageImportStatus
import com.musab.niqdah.domain.finance.BankMessageSourceType
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.FinanceDates
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.IncomeTransaction
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.ParsedBankMessageConfidence
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import com.musab.niqdah.domain.finance.PendingBankImport
import com.musab.niqdah.domain.finance.SavingsGoal
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
                    appContext.openTransactions()
                    return@launch
                }

                val handler = PendingBankImportNotificationHandler(appContext, uid)
                when (action) {
                    BankSmsNotificationActions.ACTION_SAVE -> {
                        val result = handler.save(importId)
                        if (result.saved) {
                            appContext.cancelImportNotification(importId)
                            appContext.showActionResultNotification(result.message)
                        } else {
                            appContext.showActionResultNotification(result.message)
                        }
                    }
                    BankSmsNotificationActions.ACTION_DISMISS -> {
                        if (handler.dismiss(importId)) {
                            appContext.cancelImportNotification(importId)
                        }
                    }
                }
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

    suspend fun save(importId: String): SaveActionResult {
        val db = FirebaseProvider.firestore(appContext)
            ?: return SaveActionResult(false, "Could not save because Firebase is unavailable.")
        val pendingImport = pendingBankImportsCollection(db)
            .document(importId)
            .get()
            .awaitValue()
            .toPendingBankImport()
            ?: return SaveActionResult(false, "Could not save because the pending import no longer exists.")
        val decision = BankSmsNotificationActionRules.saveDecision(pendingImport)
        if (decision is BankSmsNotificationActionRules.SaveDecision.Blocked) {
            return SaveActionResult(false, "Could not save because ${decision.reason}")
        }
        val amount = pendingImport.amount ?: return SaveActionResult(false, "Could not save because the amount is missing.")

        val now = System.currentTimeMillis()
        when (pendingImport.type) {
            ParsedBankMessageType.EXPENSE -> saveExpense(db, pendingImport, amount, now)
            ParsedBankMessageType.INCOME -> saveIncome(db, pendingImport, amount, now)
            ParsedBankMessageType.SAVINGS_TRANSFER -> saveSavingsContribution(db, pendingImport, amount, now)
            ParsedBankMessageType.INTERNAL_TRANSFER_OUT -> saveSavingsContribution(db, pendingImport, amount, now)
            ParsedBankMessageType.INFORMATIONAL,
            ParsedBankMessageType.UNKNOWN ->
                return SaveActionResult(false, "Could not save because this message is not a transaction.")
        }
        val paired = findTransferCounterpart(db, pendingImport)
        pendingBankImportsCollection(db).document(pendingImport.id).delete().awaitValue()
        if (paired != null) {
            pendingBankImportsCollection(db).document(paired.id).delete().awaitValue()
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
        return SaveActionResult(
            saved = true,
            message = pendingImport.saveSuccessMessage(paired != null)
        )
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
        pendingBankImportsCollection(db).document(pendingImport.id).delete().awaitValue()
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
                    currency = pendingImport.currency,
                    source = pendingImport.senderName.trim(),
                    note = pendingImport.noteWithReviewContext(),
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
        val existingCreatedAt = (transactionSnapshot.get("createdAtMillis") as? Number)?.toLong() ?: now
        val existingOccurredAt = (transactionSnapshot.get("occurredAtMillis") as? Number)?.toLong()
            ?: pendingImport.occurredAtMillis

        transactionsCollection(db)
            .document(transactionId)
            .set(
                ExpenseTransaction(
                    id = transactionId,
                    categoryId = FinanceDefaults.MARRIAGE_SAVINGS_CATEGORY_ID,
                    amount = existingAmount + amount,
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
                goal.copy(
                    savedAmount = goal.savedAmount + amount,
                    updatedAtMillis = now
                ).toFirestore()
            )
            .awaitValue()
    }

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
            updatedAtMillis = long("updatedAtMillis")
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
                updatedAtMillis = long("updatedAtMillis", default.updatedAtMillis)
            )
        }

    private fun ExpenseTransaction.toFirestore(): Map<String, Any> =
        mapOf(
            "categoryId" to categoryId,
            "amount" to amount,
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
            "currency" to currency,
            "source" to source,
            "note" to note,
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

    private fun SavingsGoal.toFirestore(): Map<String, Any> =
        mapOf(
            "name" to name,
            "targetAmount" to targetAmount,
            "savedAmount" to savedAmount,
            "createdAtMillis" to createdAtMillis,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun PendingBankImport.noteWithReviewContext(): String =
        listOf(description.trim(), reviewNote.trim())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")

    private fun PendingBankImport.saveSuccessMessage(wasPaired: Boolean): String =
        when {
            type == ParsedBankMessageType.SAVINGS_TRANSFER && availableBalance == null ->
                "Saved transfer. Savings balance was not updated because this SMS did not include an available balance."
            type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT && wasPaired ->
                "Saved to Niqdah. Paired with matching savings credit."
            else -> "Saved to Niqdah."
        }

    private fun DocumentSnapshot.nullableDouble(field: String): Double? =
        (get(field) as? Number)?.toDouble()

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

private data class SaveActionResult(
    val saved: Boolean,
    val message: String
)

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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                BankSmsNotificationActions.CHANNEL_ID,
                "Bank import reviews",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
    val notification = Notification.Builder(this, BankSmsNotificationActions.CHANNEL_ID)
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
