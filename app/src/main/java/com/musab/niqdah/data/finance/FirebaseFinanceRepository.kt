package com.musab.niqdah.data.finance

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.musab.niqdah.core.firebase.FirebaseProvider
import com.musab.niqdah.data.firestore.FirestoreCollections
import com.musab.niqdah.domain.finance.BankMessageParserSettings
import com.musab.niqdah.domain.finance.BankMessageSourceSettings
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.CategoryType
import com.musab.niqdah.domain.finance.DebtTracker
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.FinanceData
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.FinanceRepository
import com.musab.niqdah.domain.finance.IncomeTransaction
import com.musab.niqdah.domain.finance.MonthlySnapshot
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.SavingsGoal
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

        val existingCategoryIds = categoriesCollection(db).get().awaitValue()
            .documents
            .map { it.id }
            .toSet()
        FinanceDefaults.budgetCategories(now)
            .filterNot { it.id in existingCategoryIds }
            .forEach { category ->
                categoriesCollection(db).document(category.id).set(category.toFirestore()).awaitValue()
            }

        if (goalsCollection(db).get().awaitValue().isEmpty) {
            FinanceDefaults.savingsGoals(now).forEach { goal ->
                goalsCollection(db).document(goal.id).set(goal.toFirestore()).awaitValue()
            }
        }

        if (!debtDocument(db).get().awaitValue().exists()) {
            debtDocument(db).set(FinanceDefaults.debtTracker(now).toFirestore()).awaitValue()
        }

        if (!bankMessageSettingsDocument(db).get().awaitValue().exists()) {
            bankMessageSettingsDocument(db)
                .set(FinanceDefaults.bankMessageParserSettings().toFirestore())
                .awaitValue()
        }
    }

    override fun observeFinanceData(): Flow<FinanceData> {
        val db = firestore ?: return flowOf(FinanceData.empty(uid))

        val coreFlow = combine(
            observeProfile(db),
            observeCategories(db),
            observeTransactions(db),
            observeIncomeTransactions(db)
        ) { profile, categories, transactions, incomeTransactions ->
            FinanceCore(
                profile = profile,
                categories = categories.ifEmpty { FinanceDefaults.budgetCategories() },
                transactions = transactions,
                incomeTransactions = incomeTransactions
            )
        }

        return combine(
            coreFlow,
            observeGoals(db),
            observeDebt(db),
            observeBankMessageSettings(db)
        ) { core, goals, debt, bankMessageSettings ->
            FinanceData(
                profile = core.profile,
                categories = core.categories,
                transactions = core.transactions,
                incomeTransactions = core.incomeTransactions,
                goals = goals.ifEmpty { FinanceDefaults.savingsGoals() },
                debt = debt,
                bankMessageSettings = bankMessageSettings
            )
        }
    }

    override suspend fun upsertProfile(profile: UserProfile) {
        profileDocument(requireFirestore()).set(profile.toFirestore()).awaitValue()
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

    override suspend fun upsertGoal(goal: SavingsGoal) {
        goalsCollection(requireFirestore()).document(goal.id).set(goal.toFirestore()).awaitValue()
    }

    override suspend fun upsertDebt(debt: DebtTracker) {
        debtDocument(requireFirestore()).set(debt.toFirestore()).awaitValue()
    }

    override suspend fun upsertBankMessageSettings(settings: BankMessageParserSettings) {
        bankMessageSettingsDocument(requireFirestore()).set(settings.toFirestore()).awaitValue()
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

    private fun userDocument(db: FirebaseFirestore): DocumentReference =
        db.collection(FirestoreCollections.USERS).document(uid)

    private fun financeCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.FINANCE)

    private fun profileDocument(db: FirebaseFirestore): DocumentReference =
        financeCollection(db).document("profile")

    private fun debtDocument(db: FirebaseFirestore): DocumentReference =
        financeCollection(db).document("debt")

    private fun bankMessageSettingsDocument(db: FirebaseFirestore): DocumentReference =
        financeCollection(db).document("bankMessageSettings")

    private fun categoriesCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.BUDGET_CATEGORIES)

    private fun transactionsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.TRANSACTIONS)

    private fun incomeTransactionsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.INCOME_TRANSACTIONS)

    private fun goalsCollection(db: FirebaseFirestore): CollectionReference =
        userDocument(db).collection(FirestoreCollections.SAVINGS_GOALS)

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
            "createdAtMillis" to createdAtMillis,
            "updatedAtMillis" to updatedAtMillis
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

    private fun SavingsGoal.toFirestore(): Map<String, Any> =
        mapOf(
            "name" to name,
            "targetAmount" to targetAmount,
            "savedAmount" to savedAmount,
            "createdAtMillis" to createdAtMillis,
            "updatedAtMillis" to updatedAtMillis
        )

    private fun DebtTracker.toFirestore(): Map<String, Any> =
        mapOf(
            "startingAmount" to startingAmount,
            "remainingAmount" to remainingAmount,
            "monthlyAutoReduction" to monthlyAutoReduction,
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

    private fun BankMessageParserSettings.toFirestore(): Map<String, Any> =
        mapOf(
            "dailyUseSource" to dailyUseSource.toFirestore(),
            "savingsSource" to savingsSource.toFirestore(),
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
            salary = double("salary", 5000.0),
            extraIncome = double("extraIncome", 500.0),
            monthlySavingsTarget = double("monthlySavingsTarget", 1700.0),
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

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

    private fun DocumentSnapshot.toSavingsGoal(): SavingsGoal =
        SavingsGoal(
            id = id,
            name = getString("name") ?: id,
            targetAmount = double("targetAmount"),
            savedAmount = double("savedAmount"),
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

    private fun DocumentSnapshot.toDebtTracker(): DebtTracker =
        DebtTracker(
            startingAmount = double("startingAmount", 7000.0),
            remainingAmount = double("remainingAmount", 7000.0),
            monthlyAutoReduction = double("monthlyAutoReduction", 500.0),
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
            debitKeywords = stringList("debitKeywords", defaults.debitKeywords),
            creditKeywords = stringList("creditKeywords", defaults.creditKeywords),
            savingsTransferKeywords = stringList("savingsTransferKeywords", defaults.savingsTransferKeywords)
        )
    }

    private fun DocumentSnapshot.double(field: String, default: Double = 0.0): Double =
        (get(field) as? Number)?.toDouble() ?: default

    private fun DocumentSnapshot.long(field: String, default: Long = 0L): Long =
        (get(field) as? Number)?.toLong() ?: default

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

    private fun categoryType(value: String?): CategoryType =
        CategoryType.entries.firstOrNull { it.name == value } ?: CategoryType.VARIABLE

    private fun necessityLevel(value: String?): NecessityLevel =
        NecessityLevel.entries.firstOrNull { it.name == value } ?: NecessityLevel.NECESSARY
}

private data class FinanceCore(
    val profile: UserProfile,
    val categories: List<BudgetCategory>,
    val transactions: List<ExpenseTransaction>,
    val incomeTransactions: List<IncomeTransaction>
)
