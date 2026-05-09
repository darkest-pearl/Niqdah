package com.musab.niqdah.domain.finance

data class UserProfile(
    val uid: String,
    val currency: String = "AED",
    val salary: Double = 5000.0,
    val extraIncome: Double = 500.0,
    val monthlySavingsTarget: Double = 1700.0,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class BudgetCategory(
    val id: String,
    val name: String,
    val monthlyBudget: Double,
    val type: CategoryType,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

enum class CategoryType(val label: String) {
    FIXED("Fixed"),
    VARIABLE("Variable"),
    SAVINGS("Savings"),
    DEBT("Debt")
}

data class ExpenseTransaction(
    val id: String,
    val categoryId: String,
    val amount: Double,
    val currency: String = "",
    val note: String = "",
    val necessity: NecessityLevel = NecessityLevel.NECESSARY,
    val occurredAtMillis: Long,
    val yearMonth: String,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

enum class NecessityLevel(val label: String) {
    NECESSARY("Necessary"),
    OPTIONAL("Optional"),
    AVOID("Avoid")
}

data class IncomeTransaction(
    val id: String,
    val amount: Double,
    val currency: String = "",
    val source: String = "",
    val note: String = "",
    val occurredAtMillis: Long,
    val yearMonth: String,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class SavingsGoal(
    val id: String,
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class DebtTracker(
    val startingAmount: Double = 7000.0,
    val remainingAmount: Double = 7000.0,
    val monthlyAutoReduction: Double = 500.0,
    val updatedAtMillis: Long = 0L
)

data class MonthlySnapshot(
    val yearMonth: String,
    val totalIncome: Double,
    val totalSpent: Double,
    val remainingSafeToSpend: Double,
    val marriageFundSaved: Double,
    val marriageFundTarget: Double,
    val debtRemaining: Double,
    val debtStarting: Double,
    val healthSummary: String,
    val generatedAtMillis: Long
)

data class FinanceData(
    val profile: UserProfile,
    val categories: List<BudgetCategory>,
    val transactions: List<ExpenseTransaction>,
    val incomeTransactions: List<IncomeTransaction>,
    val pendingBankImports: List<PendingBankImport>,
    val accountBalanceSnapshots: List<AccountBalanceSnapshot>,
    val merchantRules: List<MerchantRule>,
    val goals: List<SavingsGoal>,
    val debt: DebtTracker,
    val bankMessageSettings: BankMessageParserSettings
) {
    val latestDailyUseBalance: AccountBalanceSnapshot?
        get() = latestBalance(AccountKind.DAILY_USE)

    val latestSavingsBalance: AccountBalanceSnapshot?
        get() = latestBalance(AccountKind.SAVINGS)

    val lastBalanceUpdateMillis: Long
        get() = accountBalanceSnapshots.maxOfOrNull { it.createdAtMillis } ?: 0L

    private fun latestBalance(accountKind: AccountKind): AccountBalanceSnapshot? =
        accountBalanceSnapshots
            .filter { it.accountKind == accountKind }
            .maxWithOrNull(
                compareBy<AccountBalanceSnapshot> { it.messageTimestampMillis }
                    .thenBy { it.createdAtMillis }
            )

    companion object {
        fun empty(uid: String = ""): FinanceData =
            FinanceData(
                profile = FinanceDefaults.userProfile(uid),
                categories = emptyList(),
                transactions = emptyList(),
                incomeTransactions = emptyList(),
                pendingBankImports = emptyList(),
                accountBalanceSnapshots = emptyList(),
                merchantRules = emptyList(),
                goals = emptyList(),
                debt = FinanceDefaults.debtTracker(),
                bankMessageSettings = FinanceDefaults.bankMessageParserSettings()
            )
    }
}

enum class AccountKind(val label: String) {
    DAILY_USE("Daily-use"),
    SAVINGS("Savings")
}

data class AccountBalanceSnapshot(
    val accountKind: AccountKind,
    val sender: String,
    val availableBalance: Double,
    val currency: String,
    val messageTimestampMillis: Long,
    val sourceMessageHash: String,
    val createdAtMillis: Long
)

data class MerchantRule(
    val normalizedMerchantName: String,
    val merchantName: String,
    val categoryId: String,
    val categoryName: String,
    val necessity: NecessityLevel,
    val lastUpdatedMillis: Long,
    val timesConfirmed: Int = 1
)

data class BankMessageParserSettings(
    val dailyUseSource: BankMessageSourceSettings = BankMessageSourceSettings(),
    val savingsSource: BankMessageSourceSettings = BankMessageSourceSettings(),
    val isAutomaticSmsImportEnabled: Boolean = false,
    val requireReviewBeforeSaving: Boolean = true,
    val dailyUseAccountSuffix: String = "",
    val savingsAccountSuffix: String = "",
    val isMerchantLearningEnabled: Boolean = true,
    val lastIgnoredSender: String = "",
    val lastParsedBankMessageAtMillis: Long = 0L,
    val lastIgnoredReason: String = "",
    val debitKeywords: List<String> = FinanceDefaults.DEFAULT_DEBIT_KEYWORDS,
    val creditKeywords: List<String> = FinanceDefaults.DEFAULT_CREDIT_KEYWORDS,
    val savingsTransferKeywords: List<String> = FinanceDefaults.DEFAULT_SAVINGS_TRANSFER_KEYWORDS
)

data class BankMessageSourceSettings(
    val senderName: String = "",
    val isEnabled: Boolean = true
)

enum class BankMessageSourceType(val label: String) {
    DAILY_USE("Daily-use bank"),
    SAVINGS("Savings bank"),
    UNKNOWN("Unknown source")
}

enum class ParsedBankMessageType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    SAVINGS_TRANSFER("Savings transfer"),
    INTERNAL_TRANSFER_OUT("Internal transfer out"),
    INFORMATIONAL("Informational"),
    UNKNOWN("Unknown")
}

enum class ParsedBankMessageConfidence(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low")
}

data class ParsedBankMessage(
    val rawMessage: String,
    val senderName: String = "",
    val sourceType: BankMessageSourceType = BankMessageSourceType.UNKNOWN,
    val type: ParsedBankMessageType = ParsedBankMessageType.UNKNOWN,
    val amount: Double? = null,
    val currency: String = FinanceDefaults.DEFAULT_CURRENCY,
    val availableBalance: Double? = null,
    val availableBalanceCurrency: String = FinanceDefaults.DEFAULT_CURRENCY,
    val originalForeignAmount: Double? = null,
    val originalForeignCurrency: String = "",
    val inferredAccountDebit: Double? = null,
    val isAmountInferredFromBalance: Boolean = false,
    val reviewNote: String = "",
    val merchantName: String = "",
    val sourceAccountSuffix: String = "",
    val targetAccountSuffix: String = "",
    val ignoredReason: String = "",
    val pairedTransferStatus: String = "",
    val description: String = "",
    val occurredAtMillis: Long,
    val suggestedCategoryId: String? = null,
    val suggestedCategoryName: String = "Uncategorized",
    val suggestedNecessity: NecessityLevel = NecessityLevel.OPTIONAL,
    val confidence: ParsedBankMessageConfidence = ParsedBankMessageConfidence.LOW
)

data class PendingBankImport(
    val id: String,
    val messageHash: String,
    val senderName: String,
    val rawMessage: String,
    val sourceType: BankMessageSourceType,
    val type: ParsedBankMessageType,
    val amount: Double?,
    val currency: String,
    val availableBalance: Double?,
    val availableBalanceCurrency: String,
    val originalForeignAmount: Double?,
    val originalForeignCurrency: String,
    val inferredAccountDebit: Double?,
    val isAmountInferredFromBalance: Boolean,
    val reviewNote: String,
    val merchantName: String,
    val sourceAccountSuffix: String,
    val targetAccountSuffix: String,
    val ignoredReason: String,
    val pairedTransferStatus: String,
    val description: String,
    val occurredAtMillis: Long,
    val suggestedCategoryId: String?,
    val suggestedCategoryName: String,
    val suggestedNecessity: NecessityLevel,
    val confidence: ParsedBankMessageConfidence,
    val receivedAtMillis: Long,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class BankMessageImportHistory(
    val messageHash: String,
    val status: BankMessageImportStatus,
    val senderName: String,
    val updatedAtMillis: Long
)

enum class BankMessageImportStatus {
    PENDING,
    SAVED,
    DISMISSED,
    IGNORED,
    LINKED
}

data class CategorySpend(
    val category: BudgetCategory,
    val spent: Double,
    val remaining: Double,
    val isOverspent: Boolean
)

data class DashboardMetrics(
    val totalMonthlyIncome: Double,
    val totalSpent: Double,
    val fixedReserveRemaining: Double,
    val remainingSafeToSpend: Double,
    val marriageFundProgress: Double,
    val savingsTargetProgress: Double,
    val debtProgress: Double,
    val categorySpending: List<CategorySpend>,
    val overspendingAlerts: List<CategorySpend>,
    val healthSummary: String,
    val snapshot: MonthlySnapshot
)
