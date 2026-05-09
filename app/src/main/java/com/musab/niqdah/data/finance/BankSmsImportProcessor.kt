package com.musab.niqdah.data.finance

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.musab.niqdah.MainActivity
import com.musab.niqdah.R
import com.musab.niqdah.core.firebase.FirebaseProvider
import com.musab.niqdah.data.firestore.FirestoreCollections
import com.musab.niqdah.domain.finance.BankMessageImportHistory
import com.musab.niqdah.domain.finance.BankMessageImportRules
import com.musab.niqdah.domain.finance.BankMessageImportStatus
import com.musab.niqdah.domain.finance.BankMessageParser
import com.musab.niqdah.domain.finance.BankMessageParserSettings
import com.musab.niqdah.domain.finance.BankMessageSourceSettings
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.CategoryType
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.PendingBankImport
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BankSmsImportProcessor(
    private val context: Context,
    private val uid: String
) {
    private val appContext = context.applicationContext
    private val parser = BankMessageParser()

    suspend fun processIncomingSms(
        senderName: String,
        messageBody: String,
        receivedAtMillis: Long
    ): SmsImportProcessResult {
        val db = FirebaseProvider.firestore(appContext) ?: return SmsImportProcessResult.FirebaseUnavailable
        val settings = fetchSettings(db)
        if (!settings.isAutomaticSmsImportEnabled) return SmsImportProcessResult.Disabled

        if (!BankMessageImportRules.isSenderWhitelisted(senderName, settings)) {
            updateSmsStatus(db, mapOf("lastIgnoredSender" to senderName))
            return SmsImportProcessResult.IgnoredSender
        }

        val hash = BankMessageImportRules.hashFor(senderName, messageBody, receivedAtMillis)
        if (pendingBankImportsCollection(db).document(hash).get().awaitValue().exists() ||
            bankMessageImportHistoryCollection(db).document(hash).get().awaitValue().exists()
        ) {
            return SmsImportProcessResult.Duplicate
        }

        val categories = fetchCategories(db)
        val now = System.currentTimeMillis()
        val pendingImport = parser.parsePendingImport(
            rawMessage = messageBody,
            senderName = senderName,
            settings = settings,
            categories = categories,
            messageHash = hash,
            receivedAtMillis = receivedAtMillis,
            nowMillis = now
        )

        pendingBankImportsCollection(db)
            .document(hash)
            .set(pendingImport.toFirestore())
            .awaitValue()
        bankMessageImportHistoryCollection(db)
            .document(hash)
            .set(
                BankMessageImportHistory(
                    messageHash = hash,
                    status = BankMessageImportStatus.PENDING,
                    senderName = senderName,
                    updatedAtMillis = now
                ).toFirestore()
            )
            .awaitValue()
        updateSmsStatus(db, mapOf("lastParsedBankMessageAtMillis" to receivedAtMillis))
        showReviewNotification(hash)
        return SmsImportProcessResult.PendingCreated
    }

    private suspend fun fetchSettings(db: FirebaseFirestore): BankMessageParserSettings {
        val defaults = FinanceDefaults.bankMessageParserSettings()
        val snapshot = financeCollection(db).document("bankMessageSettings").get().awaitValue()
        if (!snapshot.exists()) return defaults

        @Suppress("UNCHECKED_CAST")
        return BankMessageParserSettings(
            dailyUseSource = sourceSettings(
                value = snapshot.get("dailyUseSource") as? Map<String, Any>,
                default = defaults.dailyUseSource
            ),
            savingsSource = sourceSettings(
                value = snapshot.get("savingsSource") as? Map<String, Any>,
                default = defaults.savingsSource
            ),
            isAutomaticSmsImportEnabled = snapshot.getBoolean("isAutomaticSmsImportEnabled")
                ?: defaults.isAutomaticSmsImportEnabled,
            requireReviewBeforeSaving = true,
            lastIgnoredSender = snapshot.getString("lastIgnoredSender") ?: "",
            lastParsedBankMessageAtMillis =
                (snapshot.get("lastParsedBankMessageAtMillis") as? Number)?.toLong() ?: 0L,
            debitKeywords = snapshot.stringList("debitKeywords", defaults.debitKeywords),
            creditKeywords = snapshot.stringList("creditKeywords", defaults.creditKeywords),
            savingsTransferKeywords = snapshot.stringList(
                "savingsTransferKeywords",
                defaults.savingsTransferKeywords
            )
        )
    }

    private suspend fun fetchCategories(db: FirebaseFirestore): List<BudgetCategory> {
        val documents = categoriesCollection(db).get().awaitValue().documents
        return documents
            .map { snapshot ->
                BudgetCategory(
                    id = snapshot.id,
                    name = snapshot.getString("name") ?: snapshot.id,
                    monthlyBudget = (snapshot.get("monthlyBudget") as? Number)?.toDouble() ?: 0.0,
                    type = CategoryType.entries.firstOrNull { it.name == snapshot.getString("type") }
                        ?: CategoryType.VARIABLE,
                    createdAtMillis = (snapshot.get("createdAtMillis") as? Number)?.toLong() ?: 0L,
                    updatedAtMillis = (snapshot.get("updatedAtMillis") as? Number)?.toLong() ?: 0L
                )
            }
            .ifEmpty { FinanceDefaults.budgetCategories() }
    }

    private fun showReviewNotification(importId: String) {
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Bank import reviews",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TRANSACTIONS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            importId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Niqdah found a bank message")
            .setContentText("Review import.")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            notificationManager.notify(importId.hashCode(), notification)
        }
    }

    private suspend fun updateSmsStatus(db: FirebaseFirestore, updates: Map<String, Any>) {
        financeCollection(db)
            .document("bankMessageSettings")
            .set(updates, SetOptions.merge())
            .awaitValue()
    }

    private fun userDocument(db: FirebaseFirestore) =
        db.collection(FirestoreCollections.USERS).document(uid)

    private fun financeCollection(db: FirebaseFirestore) =
        userDocument(db).collection(FirestoreCollections.FINANCE)

    private fun categoriesCollection(db: FirebaseFirestore) =
        userDocument(db).collection(FirestoreCollections.BUDGET_CATEGORIES)

    private fun pendingBankImportsCollection(db: FirebaseFirestore) =
        userDocument(db).collection(FirestoreCollections.PENDING_BANK_IMPORTS)

    private fun bankMessageImportHistoryCollection(db: FirebaseFirestore) =
        userDocument(db).collection(FirestoreCollections.BANK_MESSAGE_IMPORT_HISTORY)

    private fun sourceSettings(
        value: Map<String, Any>?,
        default: BankMessageSourceSettings
    ): BankMessageSourceSettings =
        BankMessageSourceSettings(
            senderName = value?.get("senderName") as? String ?: default.senderName,
            isEnabled = value?.get("isEnabled") as? Boolean ?: default.isEnabled
        )

    private fun com.google.firebase.firestore.DocumentSnapshot.stringList(
        field: String,
        default: List<String>
    ): List<String> =
        (get(field) as? List<*>)
            ?.mapNotNull { it as? String }
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: default

    private fun PendingBankImport.toFirestore(): Map<String, Any?> =
        mapOf(
            "messageHash" to messageHash,
            "senderName" to senderName,
            "rawMessage" to rawMessage,
            "sourceType" to sourceType.name,
            "type" to type.name,
            "amount" to amount,
            "currency" to currency,
            "availableBalance" to availableBalance,
            "description" to description,
            "occurredAtMillis" to occurredAtMillis,
            "suggestedCategoryId" to suggestedCategoryId,
            "suggestedCategoryName" to suggestedCategoryName,
            "suggestedNecessity" to suggestedNecessity.name,
            "confidence" to confidence.name,
            "receivedAtMillis" to receivedAtMillis,
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

    private companion object {
        const val CHANNEL_ID = "bank_import_reviews"
    }
}

enum class SmsImportProcessResult {
    PendingCreated,
    Disabled,
    IgnoredSender,
    Duplicate,
    FirebaseUnavailable
}
