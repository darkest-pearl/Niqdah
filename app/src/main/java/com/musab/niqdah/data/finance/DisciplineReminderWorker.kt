package com.musab.niqdah.data.finance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.musab.niqdah.core.firebase.FirebaseProvider
import com.musab.niqdah.data.firestore.FirestoreCollections
import com.musab.niqdah.domain.finance.AccountBalanceSnapshot
import com.musab.niqdah.domain.finance.BankMessageParserSettings
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.CategoryBudgetWarningLevel
import com.musab.niqdah.domain.finance.CategoryType
import com.musab.niqdah.domain.finance.DebtTracker
import com.musab.niqdah.domain.finance.DisciplineCalculator
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.FinanceData
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.IncomeTransaction
import com.musab.niqdah.domain.finance.InternalTransferRecord
import com.musab.niqdah.domain.finance.MerchantRule
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.NecessaryItem
import com.musab.niqdah.domain.finance.NecessaryItemRecurrence
import com.musab.niqdah.domain.finance.NecessaryItemStatus
import com.musab.niqdah.domain.finance.PendingBankImport
import com.musab.niqdah.domain.finance.ReminderSettings
import com.musab.niqdah.domain.finance.SavingsGoal
import com.musab.niqdah.domain.finance.UserProfile
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DisciplineReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString(KEY_UID).orEmpty()
        val type = inputData.getString(KEY_TYPE).orEmpty()
        if (uid.isBlank() || type.isBlank()) return Result.success()
        val db = FirebaseProvider.firestore(applicationContext) ?: return Result.success()
        val data = fetchFinanceData(db, uid)
        val today = LocalDate.now()
        val yearMonth = YearMonth.from(today).toString()

        when (type) {
            TYPE_MONTHLY_SAVINGS -> handleMonthlySavings(data, today, yearMonth)
            TYPE_MISSED_SAVINGS -> handleMissedSavings(data, today, yearMonth)
            TYPE_OVERSPENDING -> handleOverspending(data, yearMonth)
            TYPE_NECESSARY_ITEMS -> handleNecessaryItems(data, today)
        }
        return Result.success()
    }

    private fun handleMonthlySavings(data: FinanceData, today: LocalDate, yearMonth: String) {
        val settings = data.reminderSettings
        if (!settings.isMonthlySavingsReminderEnabled) return
        if (today.dayOfMonth != settings.monthlySavingsReminderDay.coerceAtMost(YearMonth.from(today).lengthOfMonth())) {
            return
        }
        val key = "monthly-savings-$yearMonth"
        if (alreadyNotified(key)) return
        DisciplineNotificationPublisher.show(
            context = applicationContext,
            notificationId = key.hashCode(),
            title = "Marriage savings",
            text = "Remember to move ${
                formatNotificationMoney(settings.monthlySavingsTargetAmount, data.profile.currency)
            } to your marriage savings fund."
        )
    }

    private fun handleMissedSavings(data: FinanceData, today: LocalDate, yearMonth: String) {
        val settings = data.reminderSettings
        if (!settings.isMissedSavingsReminderEnabled) return
        if (today.dayOfMonth != settings.missedSavingsCheckDay.coerceAtMost(YearMonth.from(today).lengthOfMonth())) {
            return
        }
        val savingsTarget = DisciplineCalculator.savingsTargetStatus(data, yearMonth)
        if (savingsTarget.shortfall <= 0.0) return
        val key = "missed-savings-$yearMonth"
        if (alreadyNotified(key)) return
        DisciplineNotificationPublisher.show(
            context = applicationContext,
            notificationId = key.hashCode(),
            title = "Savings target",
            text = "You are ${
                formatNotificationMoney(savingsTarget.shortfall, data.profile.currency)
            } short of this month's marriage savings target."
        )
    }

    private fun handleOverspending(data: FinanceData, yearMonth: String) {
        if (!data.reminderSettings.areOverspendingWarningsEnabled) return
        val warnings = DisciplineCalculator.status(data, yearMonth).categoryWarnings
        warnings.forEach { warning ->
            val key = "category-${yearMonth}-${warning.category.id}-${warning.level.name}"
            if (!alreadyNotified(key)) {
                DisciplineNotificationPublisher.show(
                    context = applicationContext,
                    notificationId = key.hashCode(),
                    title = when (warning.level) {
                        CategoryBudgetWarningLevel.NEAR_LIMIT -> "Budget heads-up"
                        CategoryBudgetWarningLevel.AT_LIMIT -> "Budget limit reached"
                        CategoryBudgetWarningLevel.OVER_LIMIT -> "Budget review"
                    },
                    text = warning.message
                )
            }
        }
    }

    private fun handleNecessaryItems(data: FinanceData, today: LocalDate) {
        val dueItems = DisciplineCalculator.necessaryItemsDueSoon(data.necessaryItems, today)
            .filter { it.item.isNotificationEnabled }
        dueItems.forEach { due ->
            val key = "necessary-${due.item.id}-${due.dueDateMillis}"
            if (!alreadyNotified(key)) {
                val timing = when (due.daysUntilDue) {
                    0L -> "today"
                    1L -> "tomorrow"
                    else -> "in ${due.daysUntilDue} days"
                }
                DisciplineNotificationPublisher.show(
                    context = applicationContext,
                    notificationId = key.hashCode(),
                    title = due.item.title,
                    text = listOfNotNull(
                        "Necessary item due $timing.",
                        due.item.amount?.let { formatNotificationMoney(it, data.profile.currency) }
                    ).joinToString(" ")
                )
            }
        }
    }

    private fun alreadyNotified(key: String): Boolean {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val scopedKey = "${inputData.getString(KEY_UID).orEmpty()}:$key"
        if (prefs.getBoolean(scopedKey, false)) return true
        prefs.edit().putBoolean(scopedKey, true).apply()
        return false
    }

    private suspend fun fetchFinanceData(db: FirebaseFirestore, uid: String): FinanceData {
        val userDocument = db.collection(FirestoreCollections.USERS).document(uid)
        val financeCollection = userDocument.collection(FirestoreCollections.FINANCE)
        val profile = financeCollection.document("profile").get().awaitValue().toUserProfile(uid)
        val categories = userDocument.collection(FirestoreCollections.BUDGET_CATEGORIES)
            .get()
            .awaitValue()
            .documents
            .map { it.toBudgetCategory() }
            .ifEmpty { FinanceDefaults.budgetCategories() }
        val transactions = userDocument.collection(FirestoreCollections.TRANSACTIONS)
            .get()
            .awaitValue()
            .documents
            .map { it.toExpenseTransaction() }
        val incomeTransactions = userDocument.collection(FirestoreCollections.INCOME_TRANSACTIONS)
            .get()
            .awaitValue()
            .documents
            .map { it.toIncomeTransaction() }
        val goals = userDocument.collection(FirestoreCollections.SAVINGS_GOALS)
            .get()
            .awaitValue()
            .documents
            .map { it.toSavingsGoal() }
            .ifEmpty { FinanceDefaults.savingsGoals() }
        val debt = financeCollection.document("debt").get().awaitValue().toDebtTracker()
        val reminderSettings = financeCollection.document("reminderSettings")
            .get()
            .awaitValue()
            .toReminderSettings()
        val necessaryItems = userDocument.collection(FirestoreCollections.NECESSARY_ITEMS)
            .get()
            .awaitValue()
            .documents
            .map { it.toNecessaryItem() }
            .ifEmpty { FinanceDefaults.necessaryItems() }

        return FinanceData(
            profile = profile,
            categories = categories,
            transactions = transactions,
            incomeTransactions = incomeTransactions,
            pendingBankImports = emptyList<PendingBankImport>(),
            accountBalanceSnapshots = emptyList<AccountBalanceSnapshot>(),
            internalTransferRecords = emptyList<InternalTransferRecord>(),
            merchantRules = emptyList<MerchantRule>(),
            goals = goals,
            debt = debt,
            bankMessageSettings = BankMessageParserSettings(),
            reminderSettings = reminderSettings,
            necessaryItems = necessaryItems
        )
    }

    private fun DocumentSnapshot.toUserProfile(uid: String): UserProfile =
        UserProfile(
            uid = getString("uid") ?: uid,
            currency = getString("currency") ?: FinanceDefaults.DEFAULT_CURRENCY,
            salary = double("salary", 5000.0),
            extraIncome = double("extraIncome", 500.0),
            monthlySavingsTarget = double("monthlySavingsTarget", FinanceDefaults.DEFAULT_MONTHLY_SAVINGS_TARGET),
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

    private fun DocumentSnapshot.toBudgetCategory(): BudgetCategory =
        BudgetCategory(
            id = id,
            name = getString("name") ?: id,
            monthlyBudget = double("monthlyBudget"),
            type = CategoryType.entries.firstOrNull { it.name == getString("type") } ?: CategoryType.VARIABLE,
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

    private fun DocumentSnapshot.toExpenseTransaction(): ExpenseTransaction =
        ExpenseTransaction(
            id = id,
            categoryId = getString("categoryId") ?: "",
            amount = double("amount"),
            currency = getString("currency") ?: FinanceDefaults.DEFAULT_CURRENCY,
            note = getString("note") ?: "",
            necessity = NecessityLevel.entries.firstOrNull { it.name == getString("necessity") }
                ?: NecessityLevel.NECESSARY,
            occurredAtMillis = long("occurredAtMillis"),
            yearMonth = getString("yearMonth") ?: "",
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

    private fun DocumentSnapshot.toIncomeTransaction(): IncomeTransaction =
        IncomeTransaction(
            id = id,
            amount = double("amount"),
            currency = getString("currency") ?: FinanceDefaults.DEFAULT_CURRENCY,
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

    private fun DocumentSnapshot.toReminderSettings(): ReminderSettings {
        val defaults = FinanceDefaults.reminderSettings()
        return ReminderSettings(
            isMonthlySavingsReminderEnabled = getBoolean("isMonthlySavingsReminderEnabled")
                ?: defaults.isMonthlySavingsReminderEnabled,
            monthlySavingsReminderDay = int("monthlySavingsReminderDay", defaults.monthlySavingsReminderDay)
                .coerceIn(1, 31),
            monthlySavingsReminderHour = int("monthlySavingsReminderHour", defaults.monthlySavingsReminderHour)
                .coerceIn(0, 23),
            monthlySavingsReminderMinute = int(
                "monthlySavingsReminderMinute",
                defaults.monthlySavingsReminderMinute
            ).coerceIn(0, 59),
            monthlySavingsTargetAmount = double(
                "monthlySavingsTargetAmount",
                defaults.monthlySavingsTargetAmount
            ),
            isMissedSavingsReminderEnabled = getBoolean("isMissedSavingsReminderEnabled")
                ?: defaults.isMissedSavingsReminderEnabled,
            missedSavingsCheckDay = int("missedSavingsCheckDay", defaults.missedSavingsCheckDay)
                .coerceIn(1, 31),
            missedSavingsReminderHour = int("missedSavingsReminderHour", defaults.missedSavingsReminderHour)
                .coerceIn(0, 23),
            missedSavingsReminderMinute = int("missedSavingsReminderMinute", defaults.missedSavingsReminderMinute)
                .coerceIn(0, 59),
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
            recurrence = NecessaryItemRecurrence.entries.firstOrNull { it.name == getString("recurrence") }
                ?: NecessaryItemRecurrence.MONTHLY,
            status = NecessaryItemStatus.entries.firstOrNull { it.name == getString("status") }
                ?: NecessaryItemStatus.PENDING,
            isNotificationEnabled = getBoolean("isNotificationEnabled") ?: true,
            createdAtMillis = long("createdAtMillis"),
            updatedAtMillis = long("updatedAtMillis")
        )

    private fun DocumentSnapshot.double(field: String, default: Double = 0.0): Double =
        (get(field) as? Number)?.toDouble() ?: default

    private fun DocumentSnapshot.nullableDouble(field: String): Double? =
        (get(field) as? Number)?.toDouble()

    private fun DocumentSnapshot.long(field: String, default: Long = 0L): Long =
        (get(field) as? Number)?.toLong() ?: default

    private fun DocumentSnapshot.int(field: String, default: Int = 0): Int =
        (get(field) as? Number)?.toInt() ?: default

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

    private fun formatNotificationMoney(amount: Double, currency: String): String =
        if (amount % 1.0 == 0.0) {
            String.format(Locale.US, "%s %,.0f", currency, amount)
        } else {
            String.format(Locale.US, "%s %,.2f", currency, amount)
        }

    companion object {
        const val KEY_UID = "uid"
        const val KEY_TYPE = "type"
        const val TYPE_MONTHLY_SAVINGS = "monthlySavings"
        const val TYPE_MISSED_SAVINGS = "missedSavings"
        const val TYPE_OVERSPENDING = "overspending"
        const val TYPE_NECESSARY_ITEMS = "necessaryItems"
        private const val PREFS_NAME = "discipline_notification_history"

        fun workName(uid: String, type: String): String = "discipline-$uid-$type"
    }
}
