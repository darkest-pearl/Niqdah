package com.musab.niqdah.data.finance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.musab.niqdah.core.firebase.FirebaseProvider
import com.musab.niqdah.data.firestore.FirestoreCollections
import com.musab.niqdah.domain.finance.BankMessageSourceType
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.InternalTransferNotificationRules
import com.musab.niqdah.domain.finance.InternalTransferNotificationState
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.ParsedBankMessageConfidence
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import com.musab.niqdah.domain.finance.PendingBankImport
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class InternalTransferReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID).orEmpty()
        val importId = inputData.getString(KEY_IMPORT_ID).orEmpty()
        if (uid.isBlank() || importId.isBlank()) return Result.success()
        val db = FirebaseProvider.firestore(applicationContext) ?: return Result.success()
        val thresholdMinutes = fetchReminderThreshold(db, uid)
            ?: inputData.getInt(KEY_THRESHOLD_MINUTES, FinanceDefaults.DEFAULT_INTERNAL_TRANSFER_REMINDER_MINUTES)
        if (!fetchReminderEnabled(db, uid)) return Result.success()

        val pendingImports = db.collection(FirestoreCollections.USERS)
            .document(uid)
            .collection(FirestoreCollections.PENDING_BANK_IMPORTS)
            .get()
            .awaitValue()
            .documents
            .map { it.toPendingBankImport() }
        val pendingImport = pendingImports.firstOrNull { it.id == importId } ?: return Result.success()
        val plan = InternalTransferNotificationRules.notificationPlan(
            pendingImport = pendingImport,
            candidates = pendingImports,
            nowMillis = System.currentTimeMillis(),
            reminderThresholdMinutes = thresholdMinutes
        )
        if (plan?.state == InternalTransferNotificationState.REMINDER) {
            InternalTransferNotificationPublisher.show(applicationContext, plan)
        }
        return Result.success()
    }

    private suspend fun fetchReminderEnabled(db: FirebaseFirestore, uid: String): Boolean {
        val snapshot = bankMessageSettings(db, uid)
        return snapshot.getBoolean("isInternalTransferReminderEnabled") ?: true
    }

    private suspend fun fetchReminderThreshold(db: FirebaseFirestore, uid: String): Int? {
        val snapshot = bankMessageSettings(db, uid)
        return (snapshot.get("internalTransferReminderThresholdMinutes") as? Number)
            ?.toInt()
            ?.coerceIn(1, 24 * 60)
    }

    private suspend fun bankMessageSettings(db: FirebaseFirestore, uid: String): DocumentSnapshot =
        db.collection(FirestoreCollections.USERS)
            .document(uid)
            .collection(FirestoreCollections.FINANCE)
            .document("bankMessageSettings")
            .get()
            .awaitValue()

    private fun DocumentSnapshot.toPendingBankImport(): PendingBankImport =
        PendingBankImport(
            id = id,
            messageHash = getString("messageHash") ?: id,
            senderName = getString("senderName") ?: "",
            rawMessage = getString("rawMessage") ?: "",
            sourceType = BankMessageSourceType.entries.firstOrNull { it.name == getString("sourceType") }
                ?: BankMessageSourceType.UNKNOWN,
            type = ParsedBankMessageType.entries.firstOrNull { it.name == getString("type") }
                ?: ParsedBankMessageType.UNKNOWN,
            amount = (get("amount") as? Number)?.toDouble(),
            currency = getString("currency") ?: FinanceDefaults.DEFAULT_CURRENCY,
            availableBalance = (get("availableBalance") as? Number)?.toDouble(),
            availableBalanceCurrency = getString("availableBalanceCurrency") ?: FinanceDefaults.DEFAULT_CURRENCY,
            originalForeignAmount = (get("originalForeignAmount") as? Number)?.toDouble(),
            originalForeignCurrency = getString("originalForeignCurrency") ?: "",
            inferredAccountDebit = (get("inferredAccountDebit") as? Number)?.toDouble(),
            isAmountInferredFromBalance = getBoolean("isAmountInferredFromBalance") ?: false,
            reviewNote = getString("reviewNote") ?: "",
            merchantName = getString("merchantName") ?: "",
            sourceAccountSuffix = getString("sourceAccountSuffix") ?: "",
            targetAccountSuffix = getString("targetAccountSuffix") ?: "",
            ignoredReason = getString("ignoredReason") ?: "",
            pairedTransferStatus = getString("pairedTransferStatus") ?: "",
            description = getString("description") ?: "",
            occurredAtMillis = (get("occurredAtMillis") as? Number)?.toLong() ?: 0L,
            suggestedCategoryId = getString("suggestedCategoryId"),
            suggestedCategoryName = getString("suggestedCategoryName") ?: "Uncategorized",
            suggestedNecessity = NecessityLevel.entries.firstOrNull {
                it.name == getString("suggestedNecessity")
            } ?: NecessityLevel.NECESSARY,
            confidence = ParsedBankMessageConfidence.entries.firstOrNull { it.name == getString("confidence") }
                ?: ParsedBankMessageConfidence.LOW,
            receivedAtMillis = (get("receivedAtMillis") as? Number)?.toLong() ?: 0L,
            createdAtMillis = (get("createdAtMillis") as? Number)?.toLong() ?: 0L,
            updatedAtMillis = (get("updatedAtMillis") as? Number)?.toLong() ?: 0L
        )

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

    companion object {
        const val KEY_UID = "uid"
        const val KEY_IMPORT_ID = "importId"
        const val KEY_THRESHOLD_MINUTES = "thresholdMinutes"

        fun workName(importId: String): String = "internal-transfer-reminder-$importId"
    }
}
