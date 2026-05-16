package com.musab.niqdah.data.finance

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
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
import com.musab.niqdah.domain.finance.BankMessageParserDecision
import com.musab.niqdah.domain.finance.BankMessageParserSettings
import com.musab.niqdah.domain.finance.BankMessageSourceType
import com.musab.niqdah.domain.finance.BankMessageSourceSettings
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.CategoryType
import com.musab.niqdah.domain.finance.DepositType
import com.musab.niqdah.domain.finance.DepositSubtype
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.InternalTransferNotificationRules
import com.musab.niqdah.domain.finance.InternalTransferNotificationState
import com.musab.niqdah.domain.finance.MerchantRule
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import com.musab.niqdah.domain.finance.ParsedBankMessage
import com.musab.niqdah.domain.finance.PendingBankImport
import com.musab.niqdah.domain.finance.PendingBankImportSaveRules
import com.musab.niqdah.domain.finance.majorToMinorUnits
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
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
        val now = System.currentTimeMillis()

        if (!BankMessageImportRules.isSenderWhitelisted(senderName, settings)) {
            updateParserDecision(
                db = db,
                senderName = senderName,
                senderMatched = false,
                ignoredReason = BankMessageParserDecision.REASON_SENDER_NOT_WHITELISTED,
                parsedResult = "",
                createdPendingImport = false,
                duplicateBlocked = false,
                duplicateReason = "",
                timestampMillis = now
            )
            return SmsImportProcessResult.IgnoredSender
        }

        val hash = BankMessageImportRules.hashFor(senderName, messageBody, receivedAtMillis)
        if (pendingBankImportsCollection(db).document(hash).get().awaitValue().exists()) {
            updateParserDecision(
                db = db,
                senderName = senderName,
                senderMatched = true,
                ignoredReason = BankMessageParserDecision.REASON_DUPLICATE,
                parsedResult = "",
                createdPendingImport = false,
                duplicateBlocked = true,
                duplicateReason = BankMessageParserDecision.REASON_DUPLICATE,
                timestampMillis = now
            )
            return SmsImportProcessResult.Duplicate
        }
        val historySnapshot = bankMessageImportHistoryCollection(db).document(hash).get().awaitValue()
        val historyStatus = historySnapshot.getString("status")?.let { value ->
            BankMessageImportStatus.entries.firstOrNull { it.name == value }
        }

        val categories = fetchCategories(db)
        val latestBalances = fetchAccountBalanceSnapshots(db)
        val merchantRules = fetchMerchantRules(db)
        val pendingImport = parser.parsePendingImport(
            rawMessage = messageBody,
            senderName = senderName,
            settings = settings,
            categories = categories,
            messageHash = hash,
            receivedAtMillis = receivedAtMillis,
            latestBalances = latestBalances,
            merchantRules = merchantRules,
            nowMillis = now
        )

        val hasSavedMatchingRecord = hasSavedMatchingRecord(db, pendingImport)
        if (!BankMessageImportRules.shouldAllowReimport(historyStatus, hasSavedMatchingRecord)) {
            updateParserDecision(
                db = db,
                senderName = senderName,
                senderMatched = true,
                ignoredReason = BankMessageParserDecision.REASON_ALREADY_HANDLED,
                parsedResult = pendingImport.type.label,
                createdPendingImport = false,
                duplicateBlocked = true,
                duplicateReason = BankMessageParserDecision.REASON_ALREADY_HANDLED,
                timestampMillis = now
            )
            return SmsImportProcessResult.Duplicate
        }

        val ignoredReason = BankMessageParserDecision.ignoredReason(pendingImport.toParsedDecisionMessage())
        if (ignoredReason.isNotBlank()) {
            bankMessageImportHistoryCollection(db)
                .document(hash)
                .set(
                    BankMessageImportHistory(
                        messageHash = hash,
                        status = BankMessageImportStatus.IGNORED,
                        senderName = senderName,
                        updatedAtMillis = now
                    ).toFirestore()
                )
                .awaitValue()
            updateParserDecision(
                db = db,
                senderName = senderName,
                senderMatched = true,
                ignoredReason = ignoredReason,
                parsedResult = pendingImport.type.label,
                createdPendingImport = false,
                duplicateBlocked = false,
                duplicateReason = "",
                timestampMillis = now
            )
            return if (pendingImport.type == ParsedBankMessageType.INFORMATIONAL) {
                SmsImportProcessResult.IgnoredInformational
            } else {
                SmsImportProcessResult.IgnoredUnsupported
            }
        }

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
        updateParserDecision(
            db = db,
            senderName = senderName,
            senderMatched = true,
            ignoredReason = "",
            parsedResult = pendingImport.type.label,
            createdPendingImport = true,
            duplicateBlocked = false,
            duplicateReason = "",
            timestampMillis = receivedAtMillis
        )
        val pendingImports = fetchPendingBankImports(db)
        val pairedImport = PendingBankImportSaveRules.findMatchingTransferCounterpart(pendingImport, pendingImports)
        val internalTransferPlan = InternalTransferNotificationRules.notificationPlan(
            pendingImport = pendingImport,
            candidates = pendingImports,
            nowMillis = now,
            reminderThresholdMinutes = settings.internalTransferReminderThresholdMinutes
        )
        if (internalTransferPlan != null) {
            InternalTransferNotificationPublisher.show(appContext, internalTransferPlan)
            cancelReviewNotification(pendingImport.id)
            pairedImport?.let { cancelReviewNotification(it.id) }
            if (internalTransferPlan.state == InternalTransferNotificationState.WAITING &&
                settings.isInternalTransferReminderEnabled
            ) {
                scheduleInternalTransferReminder(
                    pendingImport = pendingImport,
                    thresholdMinutes = settings.internalTransferReminderThresholdMinutes
                )
            }
        } else {
            showReviewNotification(pendingImport)
        }
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
            dailyUseAccountSuffix = snapshot.getString("dailyUseAccountSuffix") ?: defaults.dailyUseAccountSuffix,
            savingsAccountSuffix = snapshot.getString("savingsAccountSuffix") ?: defaults.savingsAccountSuffix,
            isMerchantLearningEnabled = snapshot.getBoolean("isMerchantLearningEnabled")
                ?: defaults.isMerchantLearningEnabled,
            isInternalTransferReminderEnabled = snapshot.getBoolean("isInternalTransferReminderEnabled")
                ?: defaults.isInternalTransferReminderEnabled,
            internalTransferReminderThresholdMinutes =
                ((snapshot.get("internalTransferReminderThresholdMinutes") as? Number)?.toInt()
                    ?: defaults.internalTransferReminderThresholdMinutes).coerceIn(1, 24 * 60),
            lastIgnoredSender = snapshot.getString("lastIgnoredSender") ?: "",
            lastReceivedSender = snapshot.getString("lastReceivedSender") ?: "",
            lastSenderMatched = snapshot.getBoolean("lastSenderMatched") ?: false,
            lastParsedResult = snapshot.getString("lastParsedResult") ?: "",
            lastCreatedPendingImport = snapshot.getBoolean("lastCreatedPendingImport") ?: false,
            lastDuplicateBlocked = snapshot.getBoolean("lastDuplicateBlocked") ?: false,
            lastDuplicateReason = snapshot.getString("lastDuplicateReason") ?: "",
            lastParserDecisionAtMillis =
                (snapshot.get("lastParserDecisionAtMillis") as? Number)?.toLong() ?: 0L,
            lastParsedBankMessageAtMillis =
                (snapshot.get("lastParsedBankMessageAtMillis") as? Number)?.toLong() ?: 0L,
            lastIgnoredReason = snapshot.getString("lastIgnoredReason") ?: "",
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

    private suspend fun fetchMerchantRules(db: FirebaseFirestore): List<MerchantRule> =
        userDocument(db)
            .collection(FirestoreCollections.MERCHANT_RULES)
            .get()
            .awaitValue()
            .documents
            .mapNotNull { snapshot ->
                val normalizedName = snapshot.getString("normalizedMerchantName") ?: snapshot.id
                if (normalizedName.isBlank()) return@mapNotNull null
                MerchantRule(
                    normalizedMerchantName = normalizedName,
                    merchantName = snapshot.getString("merchantName") ?: normalizedName,
                    categoryId = snapshot.getString("categoryId") ?: FinanceDefaults.UNCATEGORIZED_CATEGORY_ID,
                    categoryName = snapshot.getString("categoryName") ?: "Uncategorized",
                    necessity = com.musab.niqdah.domain.finance.NecessityLevel.entries.firstOrNull {
                        it.name == snapshot.getString("necessity")
                    } ?: com.musab.niqdah.domain.finance.NecessityLevel.OPTIONAL,
                    lastUpdatedMillis = (snapshot.get("lastUpdatedMillis") as? Number)?.toLong() ?: 0L,
                    timesConfirmed = ((snapshot.get("timesConfirmed") as? Number)?.toInt() ?: 1).coerceAtLeast(1)
                )
            }

    private suspend fun fetchPendingBankImports(db: FirebaseFirestore): List<PendingBankImport> =
        pendingBankImportsCollection(db)
            .get()
            .awaitValue()
            .documents
            .map { snapshot ->
                PendingBankImport(
                    id = snapshot.id,
                    messageHash = snapshot.getString("messageHash") ?: snapshot.id,
                    senderName = snapshot.getString("senderName") ?: "",
                    rawMessage = snapshot.getString("rawMessage") ?: "",
                    sourceType = BankMessageSourceType.entries.firstOrNull {
                        it.name == snapshot.getString("sourceType")
                    } ?: BankMessageSourceType.UNKNOWN,
                    type = ParsedBankMessageType.entries.firstOrNull {
                        it.name == snapshot.getString("type")
                    } ?: ParsedBankMessageType.UNKNOWN,
                    amount = (snapshot.get("amount") as? Number)?.toDouble(),
                    currency = snapshot.getString("currency") ?: FinanceDefaults.DEFAULT_CURRENCY,
                    availableBalance = (snapshot.get("availableBalance") as? Number)?.toDouble(),
                    availableBalanceCurrency = snapshot.getString("availableBalanceCurrency")
                        ?: FinanceDefaults.DEFAULT_CURRENCY,
                    originalForeignAmount = (snapshot.get("originalForeignAmount") as? Number)?.toDouble(),
                    originalForeignCurrency = snapshot.getString("originalForeignCurrency") ?: "",
                    inferredAccountDebit = (snapshot.get("inferredAccountDebit") as? Number)?.toDouble(),
                    isAmountInferredFromBalance = snapshot.getBoolean("isAmountInferredFromBalance") ?: false,
                    reviewNote = snapshot.getString("reviewNote") ?: "",
                    merchantName = snapshot.getString("merchantName") ?: "",
                    sourceAccountSuffix = snapshot.getString("sourceAccountSuffix") ?: "",
                    targetAccountSuffix = snapshot.getString("targetAccountSuffix") ?: "",
                    ignoredReason = snapshot.getString("ignoredReason") ?: "",
                    pairedTransferStatus = snapshot.getString("pairedTransferStatus") ?: "",
                    description = snapshot.getString("description") ?: "",
                    occurredAtMillis = (snapshot.get("occurredAtMillis") as? Number)?.toLong() ?: 0L,
                    suggestedCategoryId = snapshot.getString("suggestedCategoryId"),
                    suggestedCategoryName = snapshot.getString("suggestedCategoryName") ?: "Uncategorized",
                    suggestedNecessity = com.musab.niqdah.domain.finance.NecessityLevel.entries.firstOrNull {
                        it.name == snapshot.getString("suggestedNecessity")
                    } ?: com.musab.niqdah.domain.finance.NecessityLevel.NECESSARY,
                    confidence = com.musab.niqdah.domain.finance.ParsedBankMessageConfidence.entries.firstOrNull {
                        it.name == snapshot.getString("confidence")
                    } ?: com.musab.niqdah.domain.finance.ParsedBankMessageConfidence.LOW,
                    receivedAtMillis = (snapshot.get("receivedAtMillis") as? Number)?.toLong() ?: 0L,
                    createdAtMillis = (snapshot.get("createdAtMillis") as? Number)?.toLong() ?: 0L,
                    updatedAtMillis = (snapshot.get("updatedAtMillis") as? Number)?.toLong() ?: 0L,
                    amountMinor = (snapshot.get("amountMinor") as? Number)?.toLong(),
                    availableBalanceMinor = (snapshot.get("availableBalanceMinor") as? Number)?.toLong(),
                    originalForeignAmountMinor = (snapshot.get("originalForeignAmountMinor") as? Number)?.toLong(),
                    inferredAccountDebitMinor = (snapshot.get("inferredAccountDebitMinor") as? Number)?.toLong(),
                    depositType = DepositType.entries.firstOrNull {
                        it.name == snapshot.getString("depositType")
                    } ?: DepositType.OTHER_INCOME,
                    depositSubtype = DepositSubtype.entries.firstOrNull {
                        it.name == snapshot.getString("depositSubtype")
                    } ?: DepositSubtype.UNKNOWN_INCOME
                )
            }

    private fun scheduleInternalTransferReminder(
        pendingImport: PendingBankImport,
        thresholdMinutes: Int
    ) {
        val request = OneTimeWorkRequestBuilder<InternalTransferReminderWorker>()
            .setInitialDelay(thresholdMinutes.toLong().coerceAtLeast(1L), TimeUnit.MINUTES)
            .setInputData(
                workDataOf(
                    InternalTransferReminderWorker.KEY_UID to uid,
                    InternalTransferReminderWorker.KEY_IMPORT_ID to pendingImport.id,
                    InternalTransferReminderWorker.KEY_THRESHOLD_MINUTES to thresholdMinutes
                )
            )
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            InternalTransferReminderWorker.workName(pendingImport.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private suspend fun hasSavedMatchingRecord(
        db: FirebaseFirestore,
        pendingImport: PendingBankImport
    ): Boolean {
        if (pendingImport.type != ParsedBankMessageType.INTERNAL_TRANSFER_OUT &&
            pendingImport.type != ParsedBankMessageType.SAVINGS_TRANSFER
        ) {
            return true
        }
        return internalTransferRecordsCollection(db)
            .get()
            .awaitValue()
            .documents
            .any { snapshot ->
                snapshot.getString("sourceMessageHash") == pendingImport.messageHash ||
                    snapshot.getString("pairedImportId") == pendingImport.id ||
                    snapshot.getString("pairedImportId") == pendingImport.messageHash
            }
    }

    private fun showReviewNotification(pendingImport: PendingBankImport) {
        if (!canPostNotifications()) return

        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val channelId = NiqdahNotificationChannels.ensureBankImportReviews(appContext)

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
        val notification = Notification.Builder(appContext, channelId)
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

    private fun cancelReviewNotification(importId: String) {
        runCatching {
            appContext.getSystemService(NotificationManager::class.java).cancel(importId.hashCode())
        }
    }

    private suspend fun updateSmsStatus(db: FirebaseFirestore, updates: Map<String, Any>) {
        financeCollection(db)
            .document("bankMessageSettings")
            .set(updates, SetOptions.merge())
            .awaitValue()
    }

    private suspend fun updateParserDecision(
        db: FirebaseFirestore,
        senderName: String,
        senderMatched: Boolean,
        ignoredReason: String,
        parsedResult: String,
        createdPendingImport: Boolean,
        duplicateBlocked: Boolean,
        duplicateReason: String,
        timestampMillis: Long
    ) {
        updateSmsStatus(
            db,
            mapOf(
                "lastReceivedSender" to senderName,
                "lastSenderMatched" to senderMatched,
                "lastIgnoredSender" to if (ignoredReason.isNotBlank()) senderName else "",
                "lastIgnoredReason" to ignoredReason,
                "lastParsedResult" to parsedResult,
                "lastCreatedPendingImport" to createdPendingImport,
                "lastDuplicateBlocked" to duplicateBlocked,
                "lastDuplicateReason" to duplicateReason,
                "lastParserDecisionAtMillis" to timestampMillis,
                "lastParsedBankMessageAtMillis" to timestampMillis
            )
        )
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

    private fun internalTransferRecordsCollection(db: FirebaseFirestore) =
        userDocument(db).collection(FirestoreCollections.INTERNAL_TRANSFER_RECORDS)

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
            "rawMessage" to "",
            "sourceType" to sourceType.name,
            "type" to type.name,
            "amount" to amount,
            "amountMinor" to amountMinor,
            "currency" to currency,
            "availableBalance" to availableBalance,
            "availableBalanceMinor" to availableBalanceMinor,
            "availableBalanceCurrency" to availableBalanceCurrency,
            "originalForeignAmount" to originalForeignAmount,
            "originalForeignAmountMinor" to originalForeignAmountMinor,
            "originalForeignCurrency" to originalForeignCurrency,
            "inferredAccountDebit" to inferredAccountDebit,
            "inferredAccountDebitMinor" to inferredAccountDebitMinor,
            "isAmountInferredFromBalance" to isAmountInferredFromBalance,
            "reviewNote" to reviewNote,
            "merchantName" to merchantName,
            "sourceAccountSuffix" to sourceAccountSuffix,
            "targetAccountSuffix" to targetAccountSuffix,
            "ignoredReason" to ignoredReason,
            "pairedTransferStatus" to pairedTransferStatus,
            "description" to description,
            "occurredAtMillis" to occurredAtMillis,
            "suggestedCategoryId" to suggestedCategoryId,
            "suggestedCategoryName" to suggestedCategoryName,
            "suggestedNecessity" to suggestedNecessity.name,
            "confidence" to confidence.name,
            "depositType" to depositType.name,
            "depositSubtype" to depositSubtype.name,
            "receivedAtMillis" to receivedAtMillis,
            "createdAtMillis" to createdAtMillis,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun AccountBalanceSnapshot.toFirestore(): Map<String, Any> =
        mapOf(
            "accountKind" to accountKind.name,
            "sender" to sender,
            "availableBalance" to availableBalance,
            "availableBalanceMinor" to availableBalanceMinor,
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
            createdAtMillis = now,
            availableBalanceMinor = availableBalanceMinor ?: majorToMinorUnits(balance)
        )
    }

    private fun AccountBalanceSnapshot.documentId(): String =
        "${accountKind.name}-$sourceMessageHash"

    private fun PendingBankImport.toParsedDecisionMessage(): ParsedBankMessage =
        ParsedBankMessage(
            rawMessage = "",
            senderName = senderName,
            sourceType = sourceType,
            type = type,
            amount = amount,
            currency = currency,
            ignoredReason = ignoredReason,
            occurredAtMillis = occurredAtMillis,
            amountMinor = amountMinor,
            depositType = depositType,
            depositSubtype = depositSubtype
        )

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
            ParsedBankMessageType.INTERNAL_TRANSFER_OUT ->
                "Transfer draft: ${amount?.let { formatNotificationMoney(it, currency) } ?: "amount needed"}"
            ParsedBankMessageType.INFORMATIONAL -> "Bank message ignored"
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
            merchantName.takeIf { it.isNotBlank() }?.let { "Merchant: $it" },
            sourceAccountSuffix.takeIf { it.isNotBlank() }?.let { "Source acct: *$it" },
            targetAccountSuffix.takeIf { it.isNotBlank() }?.let { "Target acct: *$it" },
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
    IgnoredInformational,
    IgnoredUnsupported,
    Duplicate,
    FirebaseUnavailable
}
