package com.musab.niqdah.data.finance

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.musab.niqdah.MainActivity
import com.musab.niqdah.R
import com.musab.niqdah.core.firebase.FirebaseProvider
import com.musab.niqdah.data.firestore.FirestoreCollections
import com.musab.niqdah.domain.finance.AccountBalanceSnapshot
import com.musab.niqdah.domain.finance.AccountKind
import com.musab.niqdah.domain.finance.BankMessageImportHistory
import com.musab.niqdah.domain.finance.BankMessageImportRules
import com.musab.niqdah.domain.finance.BankMessageImportStatus
import com.musab.niqdah.domain.finance.BankMessageParser
import com.musab.niqdah.domain.finance.BankMessageParserSettings
import com.musab.niqdah.domain.finance.BankMessageSourceType
import com.musab.niqdah.domain.finance.BankMessageSourceSettings
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.CategoryType
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import com.musab.niqdah.domain.finance.PendingBankImport
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Locale

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
        val latestBalances = fetchAccountBalanceSnapshots(db)
        val now = System.currentTimeMillis()
        val pendingImport = parser.parsePendingImport(
            rawMessage = messageBody,
            senderName = senderName,
            settings = settings,
            categories = categories,
            messageHash = hash,
            receivedAtMillis = receivedAtMillis,
            latestBalances = latestBalances,
            nowMillis = now
        )

        pendingBankImportsCollection(db)
            .document(hash)
            .set(pendingImport.toFirestore())
            .awaitValue()
        pendingImport.toBalanceSnapshot(now)?.let { snapshot ->
            accountBalanceSnapshotsCollection(db)
                .document(snapshot.documentId())
                .set(snapshot.toFirestore())
                .awaitValue()
        }
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
        showReviewNotification(pendingImport)
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

    private suspend fun fetchAccountBalanceSnapshots(db: FirebaseFirestore): List<AccountBalanceSnapshot> =
        accountBalanceSnapshotsCollection(db).get().awaitValue().documents
            .mapNotNull { snapshot ->
                val accountKind = AccountKind.entries.firstOrNull { it.name == snapshot.getString("accountKind") }
                    ?: return@mapNotNull null
                AccountBalanceSnapshot(
                    accountKind = accountKind,
                    sender = snapshot.getString("sender") ?: "",
                    availableBalance = (snapshot.get("availableBalance") as? Number)?.toDouble() ?: 0.0,
                    currency = snapshot.getString("currency") ?: FinanceDefaults.DEFAULT_CURRENCY,
                    messageTimestampMillis = (snapshot.get("messageTimestampMillis") as? Number)?.toLong() ?: 0L,
                    sourceMessageHash = snapshot.getString("sourceMessageHash") ?: snapshot.id.substringAfter("-", snapshot.id),
                    createdAtMillis = (snapshot.get("createdAtMillis") as? Number)?.toLong() ?: 0L
                )
            }

    private fun showReviewNotification(pendingImport: PendingBankImport) {
        if (!canPostNotifications()) return

        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    BankSmsNotificationActions.CHANNEL_ID,
                    "Bank import reviews",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TRANSACTIONS, true)
        }
        val editIntent = PendingIntent.getActivity(
            appContext,
            pendingImport.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val saveIntent = PendingIntent.getBroadcast(
            appContext,
            "${pendingImport.id}-save".hashCode(),
            Intent(appContext, BankSmsNotificationActionReceiver::class.java).apply {
                action = BankSmsNotificationActions.ACTION_SAVE
                putExtra(BankSmsNotificationActions.EXTRA_IMPORT_ID, pendingImport.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = PendingIntent.getBroadcast(
            appContext,
            "${pendingImport.id}-dismiss".hashCode(),
            Intent(appContext, BankSmsNotificationActionReceiver::class.java).apply {
                action = BankSmsNotificationActions.ACTION_DISMISS
                putExtra(BankSmsNotificationActions.EXTRA_IMPORT_ID, pendingImport.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val summary = pendingImport.notificationExpandedText()
        val notification = Notification.Builder(appContext, BankSmsNotificationActions.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(pendingImport.notificationTitle())
            .setContentText(pendingImport.notificationShortText())
            .setStyle(Notification.BigTextStyle().bigText(summary))
            .setContentIntent(editIntent)
            .setAutoCancel(true)
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_launcher_foreground,
                    "Save",
                    saveIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_launcher_foreground,
                    "Edit",
                    editIntent
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_launcher_foreground,
                    "Dismiss",
                    dismissIntent
                ).build()
            )
            .build()

        runCatching {
            notificationManager.notify(pendingImport.id.hashCode(), notification)
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

    private fun accountBalanceSnapshotsCollection(db: FirebaseFirestore) =
        userDocument(db).collection(FirestoreCollections.ACCOUNT_BALANCE_SNAPSHOTS)

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
            "availableBalanceCurrency" to availableBalanceCurrency,
            "originalForeignAmount" to originalForeignAmount,
            "originalForeignCurrency" to originalForeignCurrency,
            "inferredAccountDebit" to inferredAccountDebit,
            "isAmountInferredFromBalance" to isAmountInferredFromBalance,
            "reviewNote" to reviewNote,
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

    private fun AccountBalanceSnapshot.toFirestore(): Map<String, Any> =
        mapOf(
            "accountKind" to accountKind.name,
            "sender" to sender,
            "availableBalance" to availableBalance,
            "currency" to currency,
            "messageTimestampMillis" to messageTimestampMillis,
            "sourceMessageHash" to sourceMessageHash,
            "createdAtMillis" to createdAtMillis
        )

    private fun PendingBankImport.toBalanceSnapshot(now: Long): AccountBalanceSnapshot? {
        val balance = availableBalance ?: return null
        val accountKind = when (sourceType) {
            BankMessageSourceType.DAILY_USE -> AccountKind.DAILY_USE
            BankMessageSourceType.SAVINGS -> AccountKind.SAVINGS
            BankMessageSourceType.UNKNOWN -> return null
        }
        return AccountBalanceSnapshot(
            accountKind = accountKind,
            sender = senderName,
            availableBalance = balance,
            currency = availableBalanceCurrency.ifBlank { FinanceDefaults.DEFAULT_CURRENCY },
            messageTimestampMillis = occurredAtMillis,
            sourceMessageHash = messageHash,
            createdAtMillis = now
        )
    }

    private fun AccountBalanceSnapshot.documentId(): String =
        "${accountKind.name}-$sourceMessageHash"

    private fun PendingBankImport.notificationTitle(): String =
        when (type) {
            ParsedBankMessageType.EXPENSE -> {
                val amountText = amount?.let { formatNotificationMoney(it, currency) } ?: "amount needed"
                val merchant = description.takeIf { it.isNotBlank() && it != "Imported bank message" }
                if (merchant == null) {
                    "Expense draft: $amountText"
                } else {
                    "Expense draft: $amountText at ${merchant.take(32)}"
                }
            }
            ParsedBankMessageType.SAVINGS_TRANSFER ->
                "Savings draft: ${amount?.let { formatNotificationMoney(it, currency) } ?: "amount needed"}"
            ParsedBankMessageType.INCOME ->
                "Income draft: ${amount?.let { formatNotificationMoney(it, currency) } ?: "amount needed"}"
            ParsedBankMessageType.UNKNOWN -> "Bank message needs review"
        }

    private fun PendingBankImport.notificationShortText(): String =
        listOfNotNull(
            type.label,
            amount?.let { formatNotificationMoney(it, currency) },
            suggestedCategoryName.takeIf { it.isNotBlank() },
            confidence.label
        ).joinToString(" - ").take(96)

    private fun PendingBankImport.notificationExpandedText(): String =
        listOfNotNull(
            "Type: ${type.label}",
            amount?.let { "Amount: ${formatNotificationMoney(it, currency)}" } ?: "Amount: Needs review",
            originalForeignAmount?.takeIf { originalForeignCurrency.isNotBlank() }?.let {
                "Original: ${formatNotificationMoney(it, originalForeignCurrency)}"
            },
            "Category: $suggestedCategoryName",
            "Confidence: ${confidence.label}",
            availableBalance?.let {
                "Balance: ${formatNotificationMoney(it, availableBalanceCurrency)}"
            },
            reviewNote.takeIf { it.isNotBlank() }
        ).joinToString("\n")

    private fun formatNotificationMoney(amount: Double, currency: String): String =
        if (amount % 1.0 == 0.0) {
            String.format(Locale.US, "%s %,.0f", currency, amount)
        } else {
            String.format(Locale.US, "%s %,.2f", currency, amount)
        }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

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

}

enum class SmsImportProcessResult {
    PendingCreated,
    Disabled,
    IgnoredSender,
    Duplicate,
    FirebaseUnavailable
}
