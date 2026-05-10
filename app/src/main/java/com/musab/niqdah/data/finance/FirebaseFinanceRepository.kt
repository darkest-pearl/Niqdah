package com.musab.niqdah.data.finance

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.musab.niqdah.core.firebase.FirebaseProvider
import com.musab.niqdah.data.firestore.FirestoreCollections
import com.musab.niqdah.domain.finance.AccountBalanceSnapshot
import com.musab.niqdah.domain.finance.AccountKind
import com.musab.niqdah.domain.finance.BankMessageImportHistory
import com.musab.niqdah.domain.finance.BankMessageImportStatus
import com.musab.niqdah.domain.finance.BankMessageParserSettings
import com.musab.niqdah.domain.finance.BankMessageSourceSettings
import com.musab.niqdah.domain.finance.BankMessageSourceType
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.CategoryType
import com.musab.niqdah.domain.finance.CategoryBudgetSetup
import com.musab.niqdah.domain.finance.DebtLenderType
import com.musab.niqdah.domain.finance.DebtPressureLevel
import com.musab.niqdah.domain.finance.DebtProfile
import com.musab.niqdah.domain.finance.DebtTracker
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.FinanceData
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.FinanceRepository
import com.musab.niqdah.domain.finance.IncomeTransaction
import com.musab.niqdah.domain.finance.InternalTransferDirection
import com.musab.niqdah.domain.finance.InternalTransferRecord
import com.musab.niqdah.domain.finance.InternalTransferStatus
import com.musab.niqdah.domain.finance.InternalTransferType
import com.musab.niqdah.domain.finance.MerchantRule
import com.musab.niqdah.domain.finance.MonthlySnapshot
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.NecessaryItem
import com.musab.niqdah.domain.finance.NecessaryItemRecurrence
import com.musab.niqdah.domain.finance.NecessaryItemStatus
import com.musab.niqdah.domain.finance.FixedExpense
import com.musab.niqdah.domain.finance.GoalPurpose
import com.musab.niqdah.domain.finance.OnboardingPlan
import com.musab.niqdah.domain.finance.ParsedBankMessageConfidence
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import com.musab.niqdah.domain.finance.PendingBankImport
import com.musab.niqdah.domain.finance.PrimarySavingsGoal
import com.musab.niqdah.domain.finance.ReminderSettings
import com.musab.niqdah.domain.finance.SavingsGoal
import com.musab.niqdah.domain.finance.UserFinancialProfile
import com.musab.niqdah.domain.finance.UserPreferenceSetup
import com.musab.niqdah.domain.finance.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseFinanceRepository(
    context: Context,
    private val uid: String
) : FinanceRepository {
    private val appContext = context.applicationContext
    private val firestore: FirebaseFirestore? by lazy { FirebaseProvider.firestore(appContext) }

    override suspend fun ensureDefaults() {
        val db = requireFirestore()
        val now = System.currentTimeMillis()

        if (!profileDocument(db).get().awaitValue().exists()) {
            profileDocument(db).set(FinanceDefaults.userProfile(uid, now).toFirestore()).awaitValue()
        }

        if (!bankMessageSettingsDocument(db).get().awaitValue().exists()) {
            bankMessageSettingsDocument(db)
                .set(FinanceDefaults.bankMessageParserSettings().toFirestore())
                .awaitValue()
        }

        if (!reminderSettingsDocument(db).get().awaitValue().exists()) {
            reminderSettingsDocument(db)
                .set(FinanceDefaults.reminderSettings(now).toFirestore())
                .awaitValue()
        }
    }

    override fun observeFinanceData(): Flow<FinanceData> {
        val db = firestore ?: return flowOf(FinanceData.empty(uid))

        val profileFlow = combine(
            observeProfile(db),
            observeUserFinancialProfile(db)
        ) { profile, financialProfile ->
            ProfileCore(profile = profile, financialProfile = financialProfile)
        }

        val coreFlow = combine(
            profileFlow,
            observeCategories(db),
            observeTransactions(db),
            observeIncomeTransactions(db),
            observePendingBankImports(db)
        ) { profileCore, categories, transactions, incomeTransactions, pendingBankImports ->
            FinanceCore(
                profile = profileCore.profile,
                financialProfile = profileCore.financialProfile,
                categories = categories,
                transactions = transactions,
                incomeTransactions = incomeTransactions,
                pendingBankImports = pendingBankImports
            )
        }

        val bankExtrasFlow = combine(
            observeBankMessageSettings(db),
            observeAccountBalanceSnapshots(db),
            observeInternalTransferRecords(db),
            observeMerchantRules(db)
        ) { bankMessageSettings, accountBalanceSnapshots, internalTransferRecords, merchantRules ->
            BankExtras(
                bankMessageSettings = bankMessageSettings,
                accountBalanceSnapshots = accountBalanceSnapshots,
                internalTransferRecords = internalTransferRecords,
                merchantRules = merchantRules
            )
        }

        val disciplineExtrasFlow = combine(
            observeReminderSettings(db),
            observeNecessaryItems(db)
        ) { reminderSettings, necessaryItems ->
            DisciplineExtras(
                reminderSettings = reminderSettings,
                necessaryItems = necessaryItems
            )
        }

        return combine(
            coreFlow,
            observeGoals(db),
            observeDebt(db),
            bankExtrasFlow,
            disciplineExtrasFlow
        ) { core, goals, debt, bankExtras, disciplineExtras ->
            FinanceData(
                profile = core.profile,
                financialProfile = core.financialProfile,
                categories = core.categories,
                transactions = core.transactions,
                incomeTransactions = core.incomeTransactions,
                pendingBankImports = core.pendingBankImports,
                accountBalanceSnapshots = bankExtras.accountBalanceSnapshots,
                internalTransferRecords = bankExtras.internalTransferRecords,
                merchantRules = bankExtras.merchantRules,
                goals = goals,
                debt = debt,
                bankMessageSettings = bankExtras.bankMessageSettings,
                reminderSettings = disciplineExtras.reminderSettings,
                necessaryItems = disciplineExtras.necessaryItems
            )
        }
    }

    override suspend fun saveOnboardingPlan(plan: OnboardingPlan) {
        val db = requireFirestore()
        profileDocument(db).set(plan.profile.toFirestore()).awaitValue()
        userFinancialProfileDocument(db).set(plan.financialProfile.toFirestore()).awaitValue()
        plan.categories.forEach { category ->
            categoriesCollection(db).document(category.id).set(category.toFirestore()).awaitValue()
        }
        plan.goals.forEach { goal ->
            goalsCollection(db).document(goal.id).set(goal.toFirestore()).awaitValue()
        }
        debtDocument(db).set(plan.debt.toFirestore()).awaitValue()
        bankMessageSettingsDocument(db).set(plan.bankMessageSettings.toFirestore()).awaitValue()
        reminderSettingsDocument(db).set(plan.reminderSettings.toFirestore()).awaitValue()
        plan.necessaryItems.forEach { item ->
            necessaryItemsCollection(db).document(item.id).set(item.toFirestore()).awaitValue()
        }
    }

    override suspend fun upsertProfile(profile: UserProfile) {
        profileDocument(requireFirestore()).set(profile.toFirestore()).awaitValue()
    }

    override suspend fun upsertUserFinancialProfile(profile: UserFinancialProfile) {
        userFinancialProfileDocument(requireFirestore()).set(profile.toFirestore()).awaitValue()
    }

    override suspend fun upsertCategory(category: BudgetCategory) {
        categoriesCollection(requireFirestore()).document(category.id).set(category.toFirestore()).awaitValue()
    }

    override suspend fun upsertTransaction(transaction: ExpenseTransaction) {
        transactionsCollection(requireFirestore())
            .document(transaction.id)
            .set(transaction.toFirestore())
            .awaitValue()
    }

    override suspend fun deleteTransaction(transactionId: String) {
        transactionsCollection(requireFirestore()).document(transactionId).delete().awaitValue()
    }

    override suspend fun upsertIncomeTransaction(transaction: IncomeTransaction) {
        incomeTransactionsCollection(requireFirestore())
            .document(transaction.id)
            .set(transaction.toFirestore())
            .awaitValue()
    }

    override suspend fun deleteIncomeTransaction(transactionId: String) {
        incomeTransactionsCollection(requireFirestore()).document(transactionId).delete().awaitValue()
    }

    override suspend fun upsertPendingBankImport(pendingImport: PendingBankImport) {
        pendingBankImportsCollection(requireFirestore())
            .document(pendingImport.id)
            .set(pendingImport.toFirestore())
            .awaitValue()
    }

    override suspend fun deletePendingBankImport(importId: String) {
        pendingBankImportsCollection(requireFirestore()).document(importId).delete().awaitValue()
    }

    override suspend fun upsertBankMessageImportHistory(history: BankMessageImportHistory) {
        bankMessageImportHistoryCollection(requireFirestore())
            .document(history.messageHash)
            .set(history.toFirestore())
            .awaitValue()
    }

    override suspend fun upsertAccountBalanceSnapshot(snapshot: AccountBalanceSnapshot) {
        accountBalanceSnapshotsCollection(requireFirestore())
            .document(snapshot.documentId())
            .set(snapshot.toFirestore())
            .awaitValue()
    }

    override suspend fun upsertInternalTransferRecord(record: InternalTransferRecord) {
        internalTransferRecordsCollection(requireFirestore())
            .document(record.id)
            .set(record.toFirestore())
            .awaitValue()
    }

    override suspend fun upsertMerchantRule(rule: MerchantRule) {
        merchantRulesCollection(requireFirestore())
            .document(rule.normalizedMerchantName)
            .set(rule.toFirestore())
            .awaitValue()
    }

    override suspend fun upsertGoal(goal: SavingsGoal) {
        goalsCollection(requireFirestore()).document(goal.id).set(goal.toFirestore()).awaitValue()
    }

    override suspend fun upsertDebt(debt: DebtTracker) {
        debtDocument(requireFirestore()).set(debt.toFirestore()).awaitValue()
    }

    override suspend fun upsertBankMessageSettings(settings: BankMessageParserSettings) {
        bankMessageSettingsDocument(requireFirestore()).set(settings.toFirestore()).awaitValue()
    }

    override suspend fun upsertReminderSettings(settings: ReminderSettings) {
        reminderSettingsDocument(requireFirestore()).set(settings.toFirestore()).awaitValue()
    }

    override suspend fun upsertNecessaryItem(item: NecessaryItem) {
        necessaryItemsCollection(requireFirestore()).document(item.id).set(item.toFirestore()).awaitValue()
    }

    override suspend fun deleteNecessaryItem(itemId: String) {
        necessaryItemsCollection(requireFirestore()).document(itemId).delete().awaitValue()
    }

    override suspend fun saveMonthlySnapshot(snapshot: MonthlySnapshot) {
        snapshotsCollection(requireFirestore())
            .document(snapshot.yearMonth)
            .set(snapshot.toFirestore())
            .awaitValue()
    }

    private fun observeProfile(db: FirebaseFirestore): Flow<UserProfile> =
        callbackFlow {
            val registration = profileDocument(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toUserProfile(uid) ?: FinanceDefaults.userProfile(uid))
            }
            awaitClose { registration.remove() }
        }

    private fun observeUserFinancialProfile(db: FirebaseFirestore): Flow<UserFinancialProfile?> =
        callbackFlow {
            val registration = userFinancialProfileDocument(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.toUserFinancialProfile(uid))
            }
            awaitClose { registration.remove() }
        }

    private fun observeCategories(db: FirebaseFirestore): Flow<List<BudgetCategory>> =
        callbackFlow {
            val registration = categoriesCollection(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val categories = snapshot?.documents
                    ?.map { it.toBudgetCategory() }
                    ?.sortedWith(compareBy<BudgetCategory> { it.type.ordinal }.thenBy { it.name })
                    ?: emptyList()
                trySend(categories)
            }
            awaitClose { registration.remove() }
        }

    private fun observeTransactions(db: FirebaseFirestore): Flow<List<ExpenseTransaction>> =
        callbackFlow {
            val registration = transactionsCollection(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val transactions = snapshot?.documents
                    ?.map { it.toExpenseTransaction() }
                    ?.sortedByDescending { it.occurredAtMillis }
                    ?: emptyList()
                trySend(transactions)
            }
            awaitClose { registration.remove() }
        }

    private fun observeIncomeTransactions(db: FirebaseFirestore): Flow<List<IncomeTransaction>> =
        callbackFlow {
            val registration = incomeTransactionsCollection(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val transactions = snapshot?.documents
                    ?.map { it.toIncomeTransaction() }
                    ?.sortedByDescending { it.occurredAtMillis }
                    ?: emptyList()
                trySend(transactions)
            }
            awaitClose { registration.remove() }
        }

    private fun observePendingBankImports(db: FirebaseFirestore): Flow<List<PendingBankImport>> =
        callbackFlow {
            val registration = pendingBankImportsCollection(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val pendingImports = snapshot?.documents
                    ?.map { it.toPendingBankImport() }
                    ?.sortedByDescending { it.receivedAtMillis }
                    ?: emptyList()
                trySend(pendingImports)
            }
            awaitClose { registration.remove() }
        }

    private fun observeAccountBalanceSnapshots(db: FirebaseFirestore): Flow<List<AccountBalanceSnapshot>> =
        callbackFlow {
            val registration = accountBalanceSnapshotsCollection(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val snapshots = snapshot?.documents
                    ?.mapNotNull { it.toAccountBalanceSnapshot() }
                    ?.sortedWith(
                        compareByDescending<AccountBalanceSnapshot> { it.messageTimestampMillis }
                            .thenByDescending { it.createdAtMillis }
                    )
                    ?: emptyList()
                trySend(snapshots)
            }
            awaitClose { registration.remove() }
        }

    private fun observeInternalTransferRecords(db: FirebaseFirestore): Flow<List<InternalTransferRecord>> =
        callbackFlow {
            val registration = internalTransferRecordsCollection(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val records = snapshot?.documents
                    ?.mapNotNull { it.toInternalTransferRecord() }
                    ?.sortedByDescending { it.messageTimestampMillis }
                    ?: emptyList()
                trySend(records)
            }
            awaitClose { registration.remove() }
        }

    private fun observeMerchantRules(db: FirebaseFirestore): Flow<List<MerchantRule>> =
        callbackFlow {
            val registration = merchantRulesCollection(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val rules = snapshot?.documents
                    ?.mapNotNull { it.toMerchantRule() }
                    ?.sortedByDescending { it.lastUpdatedMillis }
                    ?: emptyList()
                trySend(rules)
            }
            awaitClose { registration.remove() }
        }

    private fun observeGoals(db: FirebaseFirestore): Flow<List<SavingsGoal>> =
        callbackFlow {
            val registration = goalsCollection(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val defaultsOrder = FinanceDefaults.savingsGoals().mapIndexed { index, goal -> goal.id to index }.toMap()
                val goals = snapshot?.documents
                    ?.map { it.toSavingsGoal() }
                    ?.sortedBy { defaultsOrder[it.id] ?: Int.MAX_VALUE }
                    ?: emptyList()
                trySend(goals)
            }
            awaitClose { registration.remove() }
        }

    private fun observeDebt(db: FirebaseFirestore): Flow<DebtTracker> =
        callbackFlow {
            val registration = debtDocument(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toDebtTracker() ?: FinanceDefaults.debtTracker())
            }
            awaitClose { registration.remove() }
        }

    private fun observeBankMessageSettings(db: FirebaseFirestore): Flow<BankMessageParserSettings> =
        callbackFlow {
            val registration = bankMessageSettingsDocument(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toBankMessageParserSettings() ?: FinanceDefaults.bankMessageParserSettings())
            }
            awaitClose { registration.remove() }
        }

    private fun observeReminderSettings(db: FirebaseFirestore): Flow<ReminderSettings> =
        callbackFlow {
            val registration = reminderSettingsDocument(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toReminderSettings() ?: FinanceDefaults.reminderSettings())
            }
            awaitClose { registration.remove() }
        }

    private fun observeNecessaryItems(db: FirebaseFirestore): Flow<List<NecessaryItem>> =
        callbackFlow {
            val registration = necessaryItemsCollection(db).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents
                    ?.map { it.toNecessaryItem() }
                    ?.sortedWith(compareBy<NecessaryItem> { it.status.ordinal }.thenBy { it.title })
                    ?: emptyList()
                trySend(items)
            }
            awaitClose { registration.remove() }
        }

    private fun userDocument(db: FirebaseFirestore): DocumentReference =
        db.collection(FirestoreCollections.USERS).document(uid)

    private fun financeCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.FINANCE)

    private fun profileDocument(db: FirebaseFirestore): DocumentReference =
        financeCollection(db).document("profile")

    private fun userFinancialProfileDocument(db: FirebaseFirestore): DocumentReference =
        financeCollection(db).document("userFinancialProfile")

    private fun debtDocument(db: FirebaseFirestore): DocumentReference =
        financeCollection(db).document("debt")

    private fun bankMessageSettingsDocument(db: FirebaseFirestore): DocumentReference =
        financeCollection(db).document("bankMessageSettings")

    private fun reminderSettingsDocument(db: FirebaseFirestore): DocumentReference =
        financeCollection(db).document("reminderSettings")

    private fun categoriesCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.BUDGET_CATEGORIES)

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

    private fun internalTransferRecordsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.INTERNAL_TRANSFER_RECORDS)

    private fun merchantRulesCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.MERCHANT_RULES)

    private fun goalsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.SAVINGS_GOALS)

    private fun necessaryItemsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.NECESSARY_ITEMS)

    private fun snapshotsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.MONTHLY_SNAPSHOTS)

    private fun requireFirestore(): FirebaseFirestore =
        firestore ?: error("Firebase is not configured yet. Add app/google-services.json and sync the project.")

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

    private fun UserProfile.toFirestore(): Map<String, Any> =
        mapOf(
            "uid" to uid,
            "currency" to currency,
            "salary" to salary,
            "extraIncome" to extraIncome,
            "monthlySavingsTarget" to monthlySavingsTarget,
            "salaryDayOfMonth" to salaryDayOfMonth,
            "onboardingCompleted" to onboardingCompleted,
            "primaryGoalId" to primaryGoalId,
            "createdAtMillis" to createdAtMillis,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun UserFinancialProfile.toFirestore(): Map<String, Any?> =
        mapOf(
            "uid" to uid,
            "controlFocus" to controlFocus.name,
            "monthlyIncome" to monthlyIncome,
            "currency" to currency,
            "salaryDayOfMonth" to salaryDayOfMonth,
            "fixedExpenses" to fixedExpenses.map { it.toFirestore() },
            "debtProfile" to debtProfile.toFirestore(),
            "primarySavingsGoal" to primarySavingsGoal?.toFirestore(),
            "preferences" to preferences.toFirestore(),
            "onboardingCompleted" to onboardingCompleted,
            "createdAtMillis" to createdAtMillis,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun FixedExpense.toFirestore(): Map<String, Any> =
        mapOf(
            "id" to id,
            "name" to name,
            "amount" to amount,
            "dueDayOfMonth" to dueDayOfMonth
        )

    private fun DebtProfile.toFirestore(): Map<String, Any?> =
        mapOf(
            "hasDebt" to hasDebt,
            "totalDebtAmount" to totalDebtAmount,
            "lenderType" to lenderType.name,
            "pressureLevel" to pressureLevel.name,
            "monthlyInstallmentAmount" to monthlyInstallmentAmount,
            "dueDayOfMonth" to dueDayOfMonth
        )

    private fun PrimarySavingsGoal.toFirestore(): Map<String, Any> =
        mapOf(
            "name" to name,
            "purpose" to purpose.name,
            "targetAmount" to targetAmount,
            "targetDate" to targetDate,
            "monthlyTargetSuggestion" to monthlyTargetSuggestion
        )

    private fun UserPreferenceSetup.toFirestore(): Map<String, Any> =
        mapOf(
            "categoryBudgets" to categoryBudgets.map { it.toFirestore() },
            "dailyUseBankSender" to dailyUseBankSender,
            "savingsBankSender" to savingsBankSender,
            "dailyUseAccountSuffix" to dailyUseAccountSuffix,
            "savingsAccountSuffix" to savingsAccountSuffix,
            "monthlySavingsReminderEnabled" to monthlySavingsReminderEnabled,
            "debtPaymentReminderEnabled" to debtPaymentReminderEnabled,
            "overspendingWarningEnabled" to overspendingWarningEnabled,
            "necessaryItemReminderEnabled" to necessaryItemReminderEnabled
        )

    private fun CategoryBudgetSetup.toFirestore(): Map<String, Any> =
        mapOf(
            "id" to id,
            "name" to name,
            "monthlyBudget" to monthlyBudget,
            "isEnabled" to isEnabled
        )

    private fun BudgetCategory.toFirestore(): Map<String, Any> =
        mapOf(
            "name" to name,
            "monthlyBudget" to monthlyBudget,
            "type" to type.name,
            "createdAtMillis" to createdAtMillis,
            "updatedAtMillis" to updatedAtMillis
        )

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

    private fun PendingBankImport.toFirestore(): Map<String, Any?> =
        mapOf(
            "messageHash" to messageHash,
            "senderName" to senderName,
            "rawMessage" to "",
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

    private fun InternalTransferRecord.toFirestore(): Map<String, Any?> =
        mapOf(
            "amount" to amount,
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

    private fun MerchantRule.toFirestore(): Map<String, Any> =
        mapOf(
            "normalizedMerchantName" to normalizedMerchantName,
            "merchantName" to merchantName,
            "categoryId" to categoryId,
            "categoryName" to categoryName,
            "necessity" to necessity.name,
            "lastUpdatedMillis" to lastUpdatedMillis,
            "timesConfirmed" to timesConfirmed
        )

    private fun SavingsGoal.toFirestore(): Map<String, Any> =
        mapOf(
            "name" to name,
            "targetAmount" to targetAmount,
            "savedAmount" to savedAmount,
            "targetDate" to targetDate,
            "purpose" to purpose.name,
            "isPrimary" to isPrimary,
            "createdAtMillis" to createdAtMillis,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun DebtTracker.toFirestore(): Map<String, Any?> =
        mapOf(
            "startingAmount" to startingAmount,
            "remainingAmount" to remainingAmount,
            "monthlyAutoReduction" to monthlyAutoReduction,
            "lenderType" to lenderType.name,
            "pressureLevel" to pressureLevel.name,
            "dueDayOfMonth" to dueDayOfMonth,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun MonthlySnapshot.toFirestore(): Map<String, Any> =
        mapOf(
            "yearMonth" to yearMonth,
            "totalIncome" to totalIncome,
            "totalSpent" to totalSpent,
            "remainingSafeToSpend" to remainingSafeToSpend,
            "marriageFundSaved" to marriageFundSaved,
            "marriageFundTarget" to marriageFundTarget,
            "debtRemaining" to debtRemaining,
            "debtStarting" to debtStarting,
            "healthSummary" to healthSummary,
            "generatedAtMillis" to generatedAtMillis
        )

    private fun ReminderSettings.toFirestore(): Map<String, Any> =
        mapOf(
            "isMonthlySavingsReminderEnabled" to isMonthlySavingsReminderEnabled,
            "monthlySavingsReminderDay" to monthlySavingsReminderDay,
            "monthlySavingsReminderHour" to monthlySavingsReminderHour,
            "monthlySavingsReminderMinute" to monthlySavingsReminderMinute,
            "monthlySavingsTargetAmount" to monthlySavingsTargetAmount,
            "isMissedSavingsReminderEnabled" to isMissedSavingsReminderEnabled,
            "missedSavingsCheckDay" to missedSavingsCheckDay,
            "missedSavingsReminderHour" to missedSavingsReminderHour,
            "missedSavingsReminderMinute" to missedSavingsReminderMinute,
            "areOverspendingWarningsEnabled" to areOverspendingWarningsEnabled,
            "isAvoidCategoryWarningEnabled" to isAvoidCategoryWarningEnabled,
            "januaryTargetDate" to januaryTargetDate,
            "januaryFundTargetAmount" to januaryFundTargetAmount,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun NecessaryItem.toFirestore(): Map<String, Any?> =
        mapOf(
            "title" to title,
            "amount" to amount,
            "dueDayOfMonth" to dueDayOfMonth,
            "dueDateMillis" to dueDateMillis,
            "recurrence" to recurrence.name,
            "status" to status.name,
            "isNotificationEnabled" to isNotificationEnabled,
            "createdAtMillis" to createdAtMillis,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun BankMessageParserSettings.toFirestore(): Map<String, Any> =
        mapOf(
            "dailyUseSource" to dailyUseSource.toFirestore(),
            "savingsSource" to savingsSource.toFirestore(),
            "isAutomaticSmsImportEnabled" to isAutomaticSmsImportEnabled,
            "requireReviewBeforeSaving" to requireReviewBeforeSaving,
            "dailyUseAccountSuffix" to dailyUseAccountSuffix,
            "savingsAccountSuffix" to savingsAccountSuffix,
            "isMerchantLearningEnabled" to isMerchantLearningEnabled,
            "isInternalTransferReminderEnabled" to isInternalTransferReminderEnabled,
            "internalTransferReminderThresholdMinutes" to internalTransferReminderThresholdMinutes,
            "lastIgnoredSender" to lastIgnoredSender,
            "lastParsedBankMessageAtMillis" to lastParsedBankMessageAtMillis,
            "lastIgnoredReason" to lastIgnoredReason,
            "debitKeywords" to debitKeywords,
            "creditKeywords" to creditKeywords,
            "savingsTransferKeywords" to savingsTransferKeywords
        )

    private fun BankMessageSourceSettings.toFirestore(): Map<String, Any> =
        mapOf(
            "senderName" to senderName,
            "isEnabled" to isEnabled
        )

    private fun DocumentSnapshot.toUserProfile(uid: String): UserProfile =
        UserProfile(
            uid = getString("uid") ?: uid,
            currency = getString("currency") ?: FinanceDefaults.DEFAULT_CURRENCY,
            salary = double("salary", 0.0),
            extraIncome = double("extraIncome", 0.0),
            monthlySavingsTarget = double("monthlySavingsTarget", 0.0),
            salaryDayOfMonth = int("salaryDayOfMonth", 1).coerceIn(1, 31),
            onboardingCompleted = getBoolean("onboardingCompleted") ?: false,
            primaryGoalId = getString("primaryGoalId") ?: "",
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toUserFinancialProfile(uid: String): UserFinancialProfile {
        val preferences = (get("preferences") as? Map<String, Any>)?.toUserPreferenceSetup()
            ?: UserPreferenceSetup()
        return UserFinancialProfile(
            uid = getString("uid") ?: uid,
            controlFocus = goalPurpose(getString("controlFocus")),
            monthlyIncome = double("monthlyIncome", 0.0),
            currency = getString("currency") ?: FinanceDefaults.DEFAULT_CURRENCY,
            salaryDayOfMonth = int("salaryDayOfMonth", 1).coerceIn(1, 31),
            fixedExpenses = (get("fixedExpenses") as? List<*>)
                ?.mapNotNull { it as? Map<String, Any> }
                ?.map { it.toFixedExpense() }
                ?: emptyList(),
            debtProfile = (get("debtProfile") as? Map<String, Any>)?.toDebtProfile() ?: DebtProfile(),
            primarySavingsGoal = (get("primarySavingsGoal") as? Map<String, Any>)?.toPrimarySavingsGoal(),
            preferences = preferences,
            onboardingCompleted = getBoolean("onboardingCompleted") ?: false,
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )
    }

    private fun DocumentSnapshot.toBudgetCategory(): BudgetCategory =
        BudgetCategory(
            id = id,
            name = getString("name") ?: id,
            monthlyBudget = double("monthlyBudget"),
            type = categoryType(getString("type")),
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

    private fun DocumentSnapshot.toExpenseTransaction(): ExpenseTransaction =
        ExpenseTransaction(
            id = id,
            categoryId = getString("categoryId") ?: "",
            amount = double("amount"),
            currency = getString("currency") ?: "",
            note = getString("note") ?: "",
            necessity = necessityLevel(getString("necessity")),
            occurredAtMillis = long("occurredAtMillis"),
            yearMonth = getString("yearMonth") ?: "",
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

    private fun DocumentSnapshot.toIncomeTransaction(): IncomeTransaction =
        IncomeTransaction(
            id = id,
            amount = double("amount"),
            currency = getString("currency") ?: "",
            source = getString("source") ?: "",
            note = getString("note") ?: "",
            occurredAtMillis = long("occurredAtMillis"),
            yearMonth = getString("yearMonth") ?: "",
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

    private fun DocumentSnapshot.toPendingBankImport(): PendingBankImport =
        PendingBankImport(
            id = id,
            messageHash = getString("messageHash") ?: id,
            senderName = getString("senderName") ?: "",
            rawMessage = getString("rawMessage") ?: "",
            sourceType = bankMessageSourceType(getString("sourceType")),
            type = parsedBankMessageType(getString("type")),
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
            suggestedNecessity = necessityLevel(getString("suggestedNecessity")),
            confidence = parsedBankMessageConfidence(getString("confidence")),
            receivedAtMillis = long("receivedAtMillis"),
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

    private fun DocumentSnapshot.toMerchantRule(): MerchantRule? {
        val normalizedName = getString("normalizedMerchantName") ?: id
        if (normalizedName.isBlank()) return null
        return MerchantRule(
            normalizedMerchantName = normalizedName,
            merchantName = getString("merchantName") ?: normalizedName,
            categoryId = getString("categoryId") ?: FinanceDefaults.UNCATEGORIZED_CATEGORY_ID,
            categoryName = getString("categoryName") ?: "Uncategorized",
            necessity = necessityLevel(getString("necessity")),
            lastUpdatedMillis = long("lastUpdatedMillis"),
            timesConfirmed = ((get("timesConfirmed") as? Number)?.toInt() ?: 1).coerceAtLeast(1)
        )
    }

    private fun DocumentSnapshot.toAccountBalanceSnapshot(): AccountBalanceSnapshot? {
        val accountKind = accountKind(getString("accountKind")) ?: return null
        return AccountBalanceSnapshot(
            accountKind = accountKind,
            sender = getString("sender") ?: "",
            availableBalance = double("availableBalance"),
            currency = getString("currency") ?: FinanceDefaults.DEFAULT_CURRENCY,
            messageTimestampMillis = long("messageTimestampMillis"),
            sourceMessageHash = getString("sourceMessageHash") ?: id.substringAfter("-", id),
            createdAtMillis = long("createdAtMillis")
        )
    }

    private fun DocumentSnapshot.toInternalTransferRecord(): InternalTransferRecord? {
        val direction = internalTransferDirection(getString("direction")) ?: return null
        val transferType = internalTransferType(getString("transferType")) ?: return null
        val status = internalTransferStatus(getString("status")) ?: return null
        return InternalTransferRecord(
            id = id,
            amount = double("amount"),
            currency = getString("currency") ?: FinanceDefaults.DEFAULT_CURRENCY,
            sourceAccountSuffix = getString("sourceAccountSuffix") ?: "",
            targetAccountSuffix = getString("targetAccountSuffix"),
            direction = direction,
            transferType = transferType,
            status = status,
            pairedImportId = getString("pairedImportId"),
            createdAtMillis = long("createdAtMillis"),
            messageTimestampMillis = long("messageTimestampMillis"),
            note = getString("note") ?: "",
            sourceMessageHash = getString("sourceMessageHash") ?: id
        )
    }

    private fun DocumentSnapshot.toSavingsGoal(): SavingsGoal =
        SavingsGoal(
            id = id,
            name = getString("name") ?: id,
            targetAmount = double("targetAmount"),
            savedAmount = double("savedAmount"),
            targetDate = getString("targetDate") ?: "",
            purpose = goalPurpose(getString("purpose")),
            isPrimary = getBoolean("isPrimary") ?: false,
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

    private fun DocumentSnapshot.toDebtTracker(): DebtTracker =
        DebtTracker(
            startingAmount = double("startingAmount", 0.0),
            remainingAmount = double("remainingAmount", 0.0),
            monthlyAutoReduction = double("monthlyAutoReduction", 0.0),
            lenderType = debtLenderType(getString("lenderType")),
            pressureLevel = debtPressureLevel(getString("pressureLevel")),
            dueDayOfMonth = nullableInt("dueDayOfMonth")?.coerceIn(1, 31),
            updatedAtMillis = long("updatedAtMillis")
        )

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toBankMessageParserSettings(): BankMessageParserSettings {
        val defaults = FinanceDefaults.bankMessageParserSettings()
        return BankMessageParserSettings(
            dailyUseSource = sourceSettings(
                value = get("dailyUseSource") as? Map<String, Any>,
                default = defaults.dailyUseSource
            ),
            savingsSource = sourceSettings(
                value = get("savingsSource") as? Map<String, Any>,
                default = defaults.savingsSource
            ),
            isAutomaticSmsImportEnabled = getBoolean("isAutomaticSmsImportEnabled")
                ?: defaults.isAutomaticSmsImportEnabled,
            requireReviewBeforeSaving = getBoolean("requireReviewBeforeSaving")
                ?: defaults.requireReviewBeforeSaving,
            dailyUseAccountSuffix = getString("dailyUseAccountSuffix") ?: defaults.dailyUseAccountSuffix,
            savingsAccountSuffix = getString("savingsAccountSuffix") ?: defaults.savingsAccountSuffix,
            isMerchantLearningEnabled = getBoolean("isMerchantLearningEnabled")
                ?: defaults.isMerchantLearningEnabled,
            isInternalTransferReminderEnabled = getBoolean("isInternalTransferReminderEnabled")
                ?: defaults.isInternalTransferReminderEnabled,
            internalTransferReminderThresholdMinutes = (
                (get("internalTransferReminderThresholdMinutes") as? Number)?.toInt()
                    ?: defaults.internalTransferReminderThresholdMinutes
                ).coerceIn(1, 24 * 60),
            lastIgnoredSender = getString("lastIgnoredSender") ?: defaults.lastIgnoredSender,
            lastParsedBankMessageAtMillis = long(
                "lastParsedBankMessageAtMillis",
                defaults.lastParsedBankMessageAtMillis
            ),
            lastIgnoredReason = getString("lastIgnoredReason") ?: defaults.lastIgnoredReason,
            debitKeywords = stringList("debitKeywords", defaults.debitKeywords),
            creditKeywords = stringList("creditKeywords", defaults.creditKeywords),
            savingsTransferKeywords = stringList("savingsTransferKeywords", defaults.savingsTransferKeywords)
        )
    }

    private fun DocumentSnapshot.toReminderSettings(): ReminderSettings {
        val defaults = FinanceDefaults.reminderSettings()
        return ReminderSettings(
            isMonthlySavingsReminderEnabled = getBoolean("isMonthlySavingsReminderEnabled")
                ?: defaults.isMonthlySavingsReminderEnabled,
            monthlySavingsReminderDay = int(
                field = "monthlySavingsReminderDay",
                default = defaults.monthlySavingsReminderDay
            ).coerceIn(1, 31),
            monthlySavingsReminderHour = int(
                field = "monthlySavingsReminderHour",
                default = defaults.monthlySavingsReminderHour
            ).coerceIn(0, 23),
            monthlySavingsReminderMinute = int(
                field = "monthlySavingsReminderMinute",
                default = defaults.monthlySavingsReminderMinute
            ).coerceIn(0, 59),
            monthlySavingsTargetAmount = double(
                "monthlySavingsTargetAmount",
                defaults.monthlySavingsTargetAmount
            ),
            isMissedSavingsReminderEnabled = getBoolean("isMissedSavingsReminderEnabled")
                ?: defaults.isMissedSavingsReminderEnabled,
            missedSavingsCheckDay = int(
                field = "missedSavingsCheckDay",
                default = defaults.missedSavingsCheckDay
            ).coerceIn(1, 31),
            missedSavingsReminderHour = int(
                field = "missedSavingsReminderHour",
                default = defaults.missedSavingsReminderHour
            ).coerceIn(0, 23),
            missedSavingsReminderMinute = int(
                field = "missedSavingsReminderMinute",
                default = defaults.missedSavingsReminderMinute
            ).coerceIn(0, 59),
            areOverspendingWarningsEnabled = getBoolean("areOverspendingWarningsEnabled")
                ?: defaults.areOverspendingWarningsEnabled,
            isAvoidCategoryWarningEnabled = getBoolean("isAvoidCategoryWarningEnabled")
                ?: defaults.isAvoidCategoryWarningEnabled,
            januaryTargetDate = getString("januaryTargetDate") ?: defaults.januaryTargetDate,
            januaryFundTargetAmount = double("januaryFundTargetAmount", defaults.januaryFundTargetAmount),
            updatedAtMillis = long("updatedAtMillis", defaults.updatedAtMillis)
        )
    }

    private fun DocumentSnapshot.toNecessaryItem(): NecessaryItem =
        NecessaryItem(
            id = id,
            title = getString("title") ?: id,
            amount = nullableDouble("amount"),
            dueDayOfMonth = int("dueDayOfMonth", 1).coerceIn(1, 31),
            dueDateMillis = (get("dueDateMillis") as? Number)?.toLong(),
            recurrence = necessaryItemRecurrence(getString("recurrence")),
            status = necessaryItemStatus(getString("status")),
            isNotificationEnabled = getBoolean("isNotificationEnabled") ?: true,
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

    private fun DocumentSnapshot.double(field: String, default: Double = 0.0): Double =
        (get(field) as? Number)?.toDouble() ?: default

    private fun DocumentSnapshot.nullableDouble(field: String): Double? =
        (get(field) as? Number)?.toDouble()

    private fun DocumentSnapshot.nullableInt(field: String): Int? =
        (get(field) as? Number)?.toInt()

    private fun DocumentSnapshot.long(field: String, default: Long = 0L): Long =
        (get(field) as? Number)?.toLong() ?: default

    private fun DocumentSnapshot.int(field: String, default: Int = 0): Int =
        (get(field) as? Number)?.toInt() ?: default

    private fun DocumentSnapshot.stringList(field: String, default: List<String>): List<String> =
        (get(field) as? List<*>)
            ?.mapNotNull { it as? String }
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: default

    private fun sourceSettings(
        value: Map<String, Any>?,
        default: BankMessageSourceSettings
    ): BankMessageSourceSettings =
        BankMessageSourceSettings(
            senderName = value?.get("senderName") as? String ?: default.senderName,
            isEnabled = value?.get("isEnabled") as? Boolean ?: default.isEnabled
        )

    private fun Map<String, Any>.toFixedExpense(): FixedExpense =
        FixedExpense(
            id = this["id"] as? String ?: "",
            name = this["name"] as? String ?: "",
            amount = (this["amount"] as? Number)?.toDouble() ?: 0.0,
            dueDayOfMonth = ((this["dueDayOfMonth"] as? Number)?.toInt() ?: 1).coerceIn(1, 31)
        )

    private fun Map<String, Any>.toDebtProfile(): DebtProfile =
        DebtProfile(
            hasDebt = this["hasDebt"] as? Boolean ?: false,
            totalDebtAmount = (this["totalDebtAmount"] as? Number)?.toDouble() ?: 0.0,
            lenderType = debtLenderType(this["lenderType"] as? String),
            pressureLevel = debtPressureLevel(this["pressureLevel"] as? String),
            monthlyInstallmentAmount = (this["monthlyInstallmentAmount"] as? Number)?.toDouble() ?: 0.0,
            dueDayOfMonth = (this["dueDayOfMonth"] as? Number)?.toInt()?.coerceIn(1, 31)
        )

    private fun Map<String, Any>.toPrimarySavingsGoal(): PrimarySavingsGoal =
        PrimarySavingsGoal(
            name = this["name"] as? String ?: "",
            purpose = goalPurpose(this["purpose"] as? String),
            targetAmount = (this["targetAmount"] as? Number)?.toDouble() ?: 0.0,
            targetDate = this["targetDate"] as? String ?: "",
            monthlyTargetSuggestion = (this["monthlyTargetSuggestion"] as? Number)?.toDouble() ?: 0.0
        )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any>.toUserPreferenceSetup(): UserPreferenceSetup =
        UserPreferenceSetup(
            categoryBudgets = (this["categoryBudgets"] as? List<*>)
                ?.mapNotNull { it as? Map<String, Any> }
                ?.map { it.toCategoryBudgetSetup() }
                ?: FinanceDefaults.onboardingCategoryTemplates(),
            dailyUseBankSender = this["dailyUseBankSender"] as? String ?: "",
            savingsBankSender = this["savingsBankSender"] as? String ?: "",
            dailyUseAccountSuffix = this["dailyUseAccountSuffix"] as? String ?: "",
            savingsAccountSuffix = this["savingsAccountSuffix"] as? String ?: "",
            monthlySavingsReminderEnabled = this["monthlySavingsReminderEnabled"] as? Boolean ?: true,
            debtPaymentReminderEnabled = this["debtPaymentReminderEnabled"] as? Boolean ?: true,
            overspendingWarningEnabled = this["overspendingWarningEnabled"] as? Boolean ?: true,
            necessaryItemReminderEnabled = this["necessaryItemReminderEnabled"] as? Boolean ?: true
        )

    private fun Map<String, Any>.toCategoryBudgetSetup(): CategoryBudgetSetup =
        CategoryBudgetSetup(
            id = this["id"] as? String ?: "",
            name = this["name"] as? String ?: "",
            monthlyBudget = (this["monthlyBudget"] as? Number)?.toDouble() ?: 0.0,
            isEnabled = this["isEnabled"] as? Boolean ?: true
        )

    private fun categoryType(value: String?): CategoryType =
        CategoryType.entries.firstOrNull { it.name == value } ?: CategoryType.VARIABLE

    private fun necessityLevel(value: String?): NecessityLevel =
        NecessityLevel.entries.firstOrNull { it.name == value } ?: NecessityLevel.NECESSARY

    private fun bankMessageSourceType(value: String?): BankMessageSourceType =
        BankMessageSourceType.entries.firstOrNull { it.name == value } ?: BankMessageSourceType.UNKNOWN

    private fun parsedBankMessageType(value: String?): ParsedBankMessageType =
        ParsedBankMessageType.entries.firstOrNull { it.name == value } ?: ParsedBankMessageType.UNKNOWN

    private fun parsedBankMessageConfidence(value: String?): ParsedBankMessageConfidence =
        ParsedBankMessageConfidence.entries.firstOrNull { it.name == value }
            ?: ParsedBankMessageConfidence.LOW

    private fun necessaryItemRecurrence(value: String?): NecessaryItemRecurrence =
        NecessaryItemRecurrence.entries.firstOrNull { it.name == value }
            ?: NecessaryItemRecurrence.MONTHLY

    private fun necessaryItemStatus(value: String?): NecessaryItemStatus =
        NecessaryItemStatus.entries.firstOrNull { it.name == value }
            ?: NecessaryItemStatus.PENDING

    private fun accountKind(value: String?): AccountKind? =
        AccountKind.entries.firstOrNull { it.name == value }

    private fun internalTransferDirection(value: String?): InternalTransferDirection? =
        InternalTransferDirection.entries.firstOrNull { it.name == value }

    private fun internalTransferType(value: String?): InternalTransferType? =
        InternalTransferType.entries.firstOrNull { it.name == value }

    private fun internalTransferStatus(value: String?): InternalTransferStatus? =
        InternalTransferStatus.entries.firstOrNull { it.name == value }

    private fun debtLenderType(value: String?): DebtLenderType =
        DebtLenderType.entries.firstOrNull { it.name == value } ?: DebtLenderType.OTHER

    private fun debtPressureLevel(value: String?): DebtPressureLevel =
        DebtPressureLevel.entries.firstOrNull { it.name == value } ?: DebtPressureLevel.FLEXIBLE

    private fun goalPurpose(value: String?): GoalPurpose =
        GoalPurpose.entries.firstOrNull { it.name == value } ?: GoalPurpose.CUSTOM

    private fun AccountBalanceSnapshot.documentId(): String =
        "${accountKind.name}-$sourceMessageHash"
}

private data class ProfileCore(
    val profile: UserProfile,
    val financialProfile: UserFinancialProfile?
)

private data class FinanceCore(
    val profile: UserProfile,
    val financialProfile: UserFinancialProfile?,
    val categories: List<BudgetCategory>,
    val transactions: List<ExpenseTransaction>,
    val incomeTransactions: List<IncomeTransaction>,
    val pendingBankImports: List<PendingBankImport>
)

private data class BankExtras(
    val bankMessageSettings: BankMessageParserSettings,
    val accountBalanceSnapshots: List<AccountBalanceSnapshot>,
    val internalTransferRecords: List<InternalTransferRecord>,
    val merchantRules: List<MerchantRule>
)

private data class DisciplineExtras(
    val reminderSettings: ReminderSettings,
    val necessaryItems: List<NecessaryItem>
)
