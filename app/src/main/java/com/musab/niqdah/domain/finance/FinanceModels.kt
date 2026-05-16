package com.musab.niqdah.domain.finance

data class UserProfile(
    val uid: String,
    val currency: String = "AED",
    val salary: Double = 0.0,
    val extraIncome: Double = 0.0,
    val monthlySavingsTarget: Double = 0.0,
    val salaryDayOfMonth: Int = 1,
    val onboardingCompleted: Boolean = false,
    val primaryGoalId: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val salaryMinor: Long = 0L,
    val extraIncomeMinor: Long = 0L,
    val monthlySavingsTargetMinor: Long = 0L
)

data class BudgetCategory(
    val id: String,
    val name: String,
    val monthlyBudget: Double,
    val type: CategoryType,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val monthlyBudgetMinor: Long = 0L
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
    val updatedAtMillis: Long = 0L,
    val amountMinor: Long = 0L
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
    val updatedAtMillis: Long = 0L,
    val amountMinor: Long = 0L,
    val depositType: DepositType = DepositType.OTHER_INCOME
)

data class SavingsGoal(
    val id: String,
    val name: String,
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val targetDate: String = "",
    val purpose: GoalPurpose = GoalPurpose.CUSTOM,
    val isPrimary: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val targetAmountMinor: Long = 0L,
    val savedAmountMinor: Long = 0L,
    val isArchived: Boolean = false
)

data class DebtTracker(
    val startingAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val monthlyAutoReduction: Double = 0.0,
    val lenderType: DebtLenderType = DebtLenderType.OTHER,
    val pressureLevel: DebtPressureLevel = DebtPressureLevel.FLEXIBLE,
    val dueDayOfMonth: Int? = null,
    val updatedAtMillis: Long = 0L,
    val startingAmountMinor: Long = 0L,
    val remainingAmountMinor: Long = 0L,
    val monthlyAutoReductionMinor: Long = 0L
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
    val financialProfile: UserFinancialProfile? = null,
    val categories: List<BudgetCategory>,
    val transactions: List<ExpenseTransaction>,
    val incomeTransactions: List<IncomeTransaction>,
    val salaryCycles: List<SalaryCycle> = emptyList(),
    val pendingBankImports: List<PendingBankImport>,
    val accountBalanceSnapshots: List<AccountBalanceSnapshot>,
    val accountLedgerEntries: List<AccountLedgerEntry> = emptyList(),
    val internalTransferRecords: List<InternalTransferRecord>,
    val merchantRules: List<MerchantRule>,
    val goals: List<SavingsGoal>,
    val debt: DebtTracker,
    val bankMessageSettings: BankMessageParserSettings,
    val reminderSettings: ReminderSettings = FinanceDefaults.reminderSettings(),
    val necessaryItems: List<NecessaryItem> = emptyList()
) {
    val visibleGoals: List<SavingsGoal>
        get() {
            val hasSpecificGoal = goals.any { goal ->
                !goal.isArchived &&
                    !goal.isEmptyGenericSavingsPlan() &&
                    (goal.isPrimary || goal.id == profile.primaryGoalId || goal.hasContributions())
            }
            return goals.filterNot { goal ->
                goal.isArchived ||
                    (
                        hasSpecificGoal &&
                            goal.isEmptyGenericSavingsPlan() &&
                            !goal.isPrimary &&
                            goal.id != profile.primaryGoalId
                        )
            }
        }

    val primaryGoal: SavingsGoal?
        get() = visibleGoals.firstOrNull { it.isPrimary }
            ?: profile.primaryGoalId.takeIf { it.isNotBlank() }?.let { id ->
                visibleGoals.firstOrNull { it.id == id }
            }
            ?: visibleGoals.firstOrNull()

    val latestDailyUseBalance: AccountBalanceSnapshot?
        get() = latestBalance(AccountKind.DAILY_USE)

    val latestSavingsBalance: AccountBalanceSnapshot?
        get() = latestBalance(AccountKind.SAVINGS)

    val latestDailyUseBalanceStatus: AccountBalanceStatus?
        get() = latestBalanceStatus(AccountKind.DAILY_USE)

    val latestSavingsBalanceStatus: AccountBalanceStatus?
        get() = latestBalanceStatus(AccountKind.SAVINGS)

    val lastBalanceUpdateMillis: Long
        get() = maxOf(
            accountBalanceSnapshots.maxOfOrNull { it.createdAtMillis } ?: 0L,
            accountLedgerEntries.maxOfOrNull { it.createdAtMillis } ?: 0L
        )

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
                financialProfile = null,
                categories = emptyList(),
                transactions = emptyList(),
                incomeTransactions = emptyList(),
                salaryCycles = emptyList(),
                pendingBankImports = emptyList(),
                accountBalanceSnapshots = emptyList(),
                accountLedgerEntries = emptyList(),
                internalTransferRecords = emptyList(),
                merchantRules = emptyList(),
                goals = emptyList(),
                debt = FinanceDefaults.debtTracker(),
                bankMessageSettings = FinanceDefaults.bankMessageParserSettings(),
                reminderSettings = FinanceDefaults.reminderSettings(),
                necessaryItems = emptyList()
            )
    }

    private fun SavingsGoal.hasContributions(): Boolean =
        effectiveMinorUnits(savedAmountMinor, savedAmount) > 0L

    private fun SavingsGoal.isEmptyGenericSavingsPlan(): Boolean {
        val normalizedName = name.trim().lowercase()
        val normalizedId = id.trim().lowercase()
        val isGeneric = normalizedName in setOf("savings plan", "savings goal", "monthly savings") ||
            normalizedId in setOf("savings_plan", "savings-goal", FinanceDefaults.SAVINGS_GOAL_CATEGORY_ID)
        return isGeneric && !hasContributions()
    }

    private fun latestBalanceStatus(accountKind: AccountKind): AccountBalanceStatus? {
        val ledgerStatus = accountLedgerEntries
            .filter { it.accountKind == accountKind && it.balanceAfterMinor != null }
            .maxByOrNull { it.createdAtMillis }
            ?.let { entry ->
                AccountBalanceStatus(
                    accountKind = entry.accountKind,
                    accountSuffix = entry.accountSuffix,
                    amountMinor = entry.balanceAfterMinor ?: 0L,
                    currency = entry.currency,
                    confidence = entry.confidence,
                    lastUpdatedMillis = entry.createdAtMillis,
                    source = entry.source,
                    note = entry.note
                )
            }
        if (ledgerStatus != null) return ledgerStatus

        return latestBalance(accountKind)?.let { snapshot ->
            AccountBalanceStatus(
                accountKind = snapshot.accountKind,
                accountSuffix = "",
                amountMinor = effectiveMinorUnits(snapshot.availableBalanceMinor, snapshot.availableBalance),
                currency = snapshot.currency,
                confidence = AccountBalanceConfidence.CONFIRMED,
                lastUpdatedMillis = snapshot.createdAtMillis,
                source = AccountLedgerSource.SMS,
                note = "Balance confirmed by bank SMS."
            )
        }
    }
}

data class ReminderSettings(
    val isMonthlySavingsReminderEnabled: Boolean = true,
    val monthlySavingsReminderDay: Int = 1,
    val monthlySavingsReminderHour: Int = 9,
    val monthlySavingsReminderMinute: Int = 0,
    val monthlySavingsTargetAmount: Double = 0.0,
    val isMissedSavingsReminderEnabled: Boolean = true,
    val missedSavingsCheckDay: Int = 20,
    val missedSavingsReminderHour: Int = 19,
    val missedSavingsReminderMinute: Int = 0,
    val areOverspendingWarningsEnabled: Boolean = true,
    val isAvoidCategoryWarningEnabled: Boolean = true,
    val januaryTargetDate: String = "",
    val januaryFundTargetAmount: Double = 0.0,
    val updatedAtMillis: Long = 0L,
    val monthlySavingsTargetAmountMinor: Long = 0L,
    val januaryFundTargetAmountMinor: Long = 0L,
    val isPostSalarySavingsFollowUpEnabled: Boolean = true,
    val postSalarySavingsFollowUpIntervalDays: Int = 3,
    val suppressedPostSalarySavingsFollowUpCycleMonth: String = ""
)

data class SalaryCycle(
    val id: String,
    val userId: String = "",
    val cycleMonth: String,
    val salaryDepositAmountMinor: Long,
    val openingDailyUseBalanceMinor: Long? = null,
    val salaryDepositDateMillis: Long,
    val currency: String = FinanceDefaults.DEFAULT_CURRENCY,
    val source: SalaryCycleSource = SalaryCycleSource.MANUAL,
    val isOpeningBalanceConfirmed: Boolean = openingDailyUseBalanceMinor != null,
    val isActive: Boolean = true,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val lastSavingsFollowUpReminderAtMillis: Long = 0L
)

enum class SalaryCycleSource(val label: String) {
    SMS("SMS"),
    MANUAL("Manual")
}

data class NecessaryItem(
    val id: String,
    val title: String,
    val amount: Double? = null,
    val dueDayOfMonth: Int = 1,
    val dueDateMillis: Long? = null,
    val recurrence: NecessaryItemRecurrence = NecessaryItemRecurrence.MONTHLY,
    val status: NecessaryItemStatus = NecessaryItemStatus.PENDING,
    val isNotificationEnabled: Boolean = true,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val amountMinor: Long? = null
)

enum class NecessaryItemRecurrence(val label: String) {
    MONTHLY("Monthly"),
    ONE_TIME("One-time")
}

enum class NecessaryItemStatus(val label: String) {
    PENDING("Pending"),
    DONE("Done"),
    SKIPPED("Skipped")
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
    val createdAtMillis: Long,
    val availableBalanceMinor: Long = 0L
)

data class AccountLedgerEntry(
    val id: String,
    val accountKind: AccountKind,
    val accountSuffix: String = "",
    val eventType: AccountLedgerEventType,
    val amountMinor: Long,
    val balanceAfterMinor: Long? = null,
    val currency: String = FinanceDefaults.DEFAULT_CURRENCY,
    val confidence: AccountBalanceConfidence,
    val source: AccountLedgerSource,
    val relatedTransactionId: String? = null,
    val createdAtMillis: Long = 0L,
    val note: String = ""
)

data class AccountBalanceStatus(
    val accountKind: AccountKind,
    val accountSuffix: String = "",
    val amountMinor: Long,
    val currency: String = FinanceDefaults.DEFAULT_CURRENCY,
    val confidence: AccountBalanceConfidence,
    val lastUpdatedMillis: Long = 0L,
    val source: AccountLedgerSource = AccountLedgerSource.SYSTEM,
    val note: String = ""
)

enum class AccountLedgerEventType(val label: String) {
    BALANCE_CONFIRMED_SMS("Balance confirmed by SMS"),
    BALANCE_CONFIRMED_MANUAL("Balance confirmed manually"),
    ESTIMATED_DEPOSIT("Estimated deposit"),
    ESTIMATED_DEBIT("Estimated debit"),
    TRANSFER_OUT("Transfer out"),
    TRANSFER_IN("Transfer in"),
    ADJUSTMENT("Adjustment")
}

enum class AccountBalanceConfidence(val label: String) {
    CONFIRMED("Confirmed"),
    ESTIMATED("Estimated"),
    NEEDS_REVIEW("Needs review")
}

enum class AccountLedgerSource(val label: String) {
    SMS("SMS"),
    MANUAL("Manual"),
    IMPORT("Import"),
    SYSTEM("System")
}

data class MerchantRule(
    val normalizedMerchantName: String,
    val merchantName: String,
    val categoryId: String,
    val categoryName: String,
    val necessity: NecessityLevel,
    val lastUpdatedMillis: Long,
    val timesConfirmed: Int = 1
)

data class InternalTransferRecord(
    val id: String,
    val amount: Double,
    val currency: String,
    val sourceAccountSuffix: String,
    val targetAccountSuffix: String? = null,
    val direction: InternalTransferDirection,
    val transferType: InternalTransferType,
    val status: InternalTransferStatus,
    val pairedImportId: String? = null,
    val createdAtMillis: Long,
    val messageTimestampMillis: Long,
    val note: String,
    val sourceMessageHash: String,
    val amountMinor: Long = 0L
)

enum class InternalTransferDirection(val label: String) {
    OUT("Out")
}

enum class InternalTransferType(val label: String) {
    ACCOUNT_TO_ACCOUNT("Account to Account Transfer")
}

enum class InternalTransferStatus(val label: String) {
    NEEDS_MATCHING_CREDIT("Needs matching credit"),
    PAIRED("Paired")
}

data class BankMessageParserSettings(
    val dailyUseSource: BankMessageSourceSettings = BankMessageSourceSettings(),
    val savingsSource: BankMessageSourceSettings = BankMessageSourceSettings(),
    val isAutomaticSmsImportEnabled: Boolean = false,
    val requireReviewBeforeSaving: Boolean = true,
    val dailyUseAccountSuffix: String = "",
    val savingsAccountSuffix: String = "",
    val isMerchantLearningEnabled: Boolean = true,
    val isInternalTransferReminderEnabled: Boolean = true,
    val internalTransferReminderThresholdMinutes: Int = 10,
    val lastIgnoredSender: String = "",
    val lastReceivedSender: String = "",
    val lastSenderMatched: Boolean = false,
    val lastParsedResult: String = "",
    val lastCreatedPendingImport: Boolean = false,
    val lastDuplicateBlocked: Boolean = false,
    val lastDuplicateReason: String = "",
    val lastParserDecisionAtMillis: Long = 0L,
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

enum class DepositType(val label: String) {
    SALARY("Salary"),
    OTHER_INCOME("Other income"),
    REFUND("Refund"),
    TRANSFER("Transfer")
}

enum class DepositSubtype(val label: String) {
    SALARY("Salary deposit"),
    GENERAL_DEPOSIT("General deposit"),
    REFUND("Refund"),
    TRANSFER_IN("Transfer in"),
    UNKNOWN_INCOME("Unknown income")
}

enum class ExternalTransferClassification(val label: String) {
    TRANSFER_TO_MY_SAVINGS("Transfer to my savings"),
    TRANSFER_TO_ANOTHER_PERSON("Transfer to another person"),
    BILL_PAYMENT("Bill/payment"),
    OTHER("Other")
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
    val confidence: ParsedBankMessageConfidence = ParsedBankMessageConfidence.LOW,
    val amountMinor: Long? = null,
    val availableBalanceMinor: Long? = null,
    val originalForeignAmountMinor: Long? = null,
    val inferredAccountDebitMinor: Long? = null,
    val depositType: DepositType = DepositType.OTHER_INCOME,
    val depositSubtype: DepositSubtype = DepositSubtype.UNKNOWN_INCOME
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
    val updatedAtMillis: Long = 0L,
    val amountMinor: Long? = null,
    val availableBalanceMinor: Long? = null,
    val originalForeignAmountMinor: Long? = null,
    val inferredAccountDebitMinor: Long? = null,
    val depositType: DepositType = DepositType.OTHER_INCOME,
    val depositSubtype: DepositSubtype = DepositSubtype.UNKNOWN_INCOME,
    val externalTransferClassification: ExternalTransferClassification? = null
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

sealed interface SaveResult {
    data class Saved(val message: String) : SaveResult
    data class NeedsReview(val message: String) : SaveResult
    data class Blocked(val reason: String) : SaveResult
    data class Ignored(val reason: String) : SaveResult
    data class Error(val reason: String) : SaveResult
}

data class CategorySpend(
    val category: BudgetCategory,
    val spent: Double,
    val remaining: Double,
    val isOverspent: Boolean
)

data class CategorySpendingBreakdown(
    val category: BudgetCategory,
    val budget: Double,
    val spent: Double,
    val necessarySpent: Double,
    val optionalSpent: Double,
    val avoidSpent: Double,
    val remaining: Double,
    val isOverspent: Boolean,
    val overBudgetAmount: Double,
    val rawProgress: Double,
    val visualProgress: Double,
    val budgetMinor: Long,
    val spentMinor: Long,
    val necessaryMinor: Long,
    val optionalMinor: Long,
    val avoidMinor: Long
)

data class ProtectedCashObligation(
    val title: String,
    val amountMinor: Long,
    val remainingAmountMinor: Long,
    val categoryId: String = "",
    val dueDateMillis: Long? = null,
    val dueDayOfMonth: Int? = null,
    val status: ProtectedCashObligationStatus,
    val type: ProtectedCashObligationType
) {
    val isProtected: Boolean
        get() = status == ProtectedCashObligationStatus.UNPAID && remainingAmountMinor > 0L
}

enum class ProtectedCashObligationStatus {
    UNPAID,
    PAID,
    SKIPPED
}

enum class ProtectedCashObligationType {
    RENT,
    SAVINGS_TARGET,
    DEBT_PAYMENT,
    BILL,
    FAMILY_SUPPORT,
    SUBSCRIPTION,
    NECESSARY_ITEM,
    OTHER
}

data class CashProtectionInput(
    val currentDailyUseBalanceMinor: Long?,
    val salaryCycleOpeningBalanceMinor: Long?,
    val totalProtectedUnpaidObligationsMinor: Long,
    val flexibleBudgetMinor: Long,
    val spentThisCycleMinor: Long,
    val unknownOrUncategorizedSpendingMinor: Long
)

data class CashProtectionResult(
    val ringProgress: Double,
    val riskLevel: CashProtectionRiskLevel,
    val flexibleBufferLeftMinor: Long,
    val protectedUnpaidObligationsMinor: Long,
    val message: String
)

enum class CashProtectionRiskLevel(val label: String) {
    UNKNOWN_OPENING_BALANCE("Opening balance not confirmed"),
    HEALTHY("Healthy"),
    WATCH("Watch"),
    TIGHT("Tight"),
    PROTECTED_FUNDS_AT_RISK("Protected funds at risk")
}

data class SavingsFollowUpReminderStatus(
    val shouldNotify: Boolean,
    val state: SavingsFollowUpState,
    val remainingSavingsTargetMinor: Long,
    val nextEligibleReminderMillis: Long?
)

enum class SavingsFollowUpState {
    DISABLED,
    NO_ACTIVE_CYCLE,
    NOT_DUE,
    DUE,
    COMPLETED,
    SUPPRESSED_FOR_CYCLE
}

data class SavingsTargetStatus(
    val targetAmount: Double,
    val savedThisMonth: Double,
    val shortfall: Double,
    val progress: Double
)

data class CategoryBudgetWarning(
    val category: BudgetCategory,
    val spent: Double,
    val budget: Double,
    val percentUsed: Double,
    val level: CategoryBudgetWarningLevel,
    val message: String
)

enum class CategoryBudgetWarningLevel {
    NEAR_LIMIT,
    AT_LIMIT,
    OVER_LIMIT
}

data class NecessaryItemDue(
    val item: NecessaryItem,
    val dueDateMillis: Long,
    val daysUntilDue: Long
)

data class JanuaryCountdownStatus(
    val targetDate: String,
    val goalName: String = "Savings goal",
    val daysRemaining: Long,
    val monthsRemaining: Long,
    val currentSaved: Double,
    val targetAmount: Double,
    val requiredMonthlySavings: Double
)

data class DisciplineStatus(
    val savingsTarget: SavingsTargetStatus,
    val categoryWarnings: List<CategoryBudgetWarning>,
    val necessaryItemsDueSoon: List<NecessaryItemDue>,
    val avoidSpendingThisMonth: Double,
    val safeToSpendAmount: Double,
    val januaryCountdown: JanuaryCountdownStatus
) {
    companion object {
        fun empty(): DisciplineStatus =
            DisciplineStatus(
                savingsTarget = SavingsTargetStatus(
                    targetAmount = 0.0,
                    savedThisMonth = 0.0,
                    shortfall = 0.0,
                    progress = 0.0
                ),
                categoryWarnings = emptyList(),
                necessaryItemsDueSoon = emptyList(),
                avoidSpendingThisMonth = 0.0,
                safeToSpendAmount = 0.0,
                januaryCountdown = JanuaryCountdownStatus(
                    targetDate = "",
                    goalName = "Savings goal",
                    daysRemaining = 0L,
                    monthsRemaining = 0L,
                    currentSaved = 0.0,
                    targetAmount = 0.0,
                    requiredMonthlySavings = 0.0
                )
            )
    }
}

data class DashboardMetrics(
    val totalMonthlyIncome: Double,
    val totalSpent: Double,
    val fixedReserveRemaining: Double,
    val remainingSafeToSpend: Double,
    val marriageFundProgress: Double,
    val primaryGoalName: String,
    val primaryGoalProgress: Double,
    val savingsTargetProgress: Double,
    val debtProgress: Double,
    val categorySpending: List<CategorySpend>,
    val categorySpendingBreakdowns: List<CategorySpendingBreakdown>,
    val overspendingAlerts: List<CategorySpend>,
    val healthSummary: String,
    val snapshot: MonthlySnapshot,
    val disciplineStatus: DisciplineStatus = DisciplineStatus.empty()
)

data class OnboardingState(
    val controlFocus: GoalPurpose = GoalPurpose.GENERAL_BUDGETING,
    val monthlyIncome: Double = 0.0,
    val currency: String = FinanceDefaults.DEFAULT_CURRENCY,
    val salaryDayOfMonth: Int = 1,
    val fixedExpenses: List<FixedExpense> = FinanceDefaults.onboardingFixedExpenseTemplates(),
    val debtProfile: DebtProfile = DebtProfile(),
    val primarySavingsGoal: PrimarySavingsGoal? = null,
    val preferences: UserPreferenceSetup = UserPreferenceSetup()
)

data class UserFinancialProfile(
    val uid: String,
    val controlFocus: GoalPurpose = GoalPurpose.GENERAL_BUDGETING,
    val monthlyIncome: Double = 0.0,
    val currency: String = FinanceDefaults.DEFAULT_CURRENCY,
    val salaryDayOfMonth: Int = 1,
    val fixedExpenses: List<FixedExpense> = emptyList(),
    val debtProfile: DebtProfile = DebtProfile(),
    val primarySavingsGoal: PrimarySavingsGoal? = null,
    val preferences: UserPreferenceSetup = UserPreferenceSetup(),
    val onboardingCompleted: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class FixedExpense(
    val id: String,
    val name: String,
    val amount: Double = 0.0,
    val dueDayOfMonth: Int = 1
)

data class DebtProfile(
    val hasDebt: Boolean = false,
    val totalDebtAmount: Double = 0.0,
    val lenderType: DebtLenderType = DebtLenderType.OTHER,
    val pressureLevel: DebtPressureLevel = DebtPressureLevel.FLEXIBLE,
    val monthlyInstallmentAmount: Double = 0.0,
    val dueDayOfMonth: Int? = null
)

enum class DebtLenderType(val label: String) {
    FRIEND_FAMILY("Friend/family"),
    BANK("Bank"),
    CREDIT_CARD("Credit card"),
    EMPLOYER("Employer"),
    OTHER("Other")
}

enum class DebtPressureLevel(val label: String) {
    FLEXIBLE("Flexible"),
    FIXED_MONTHLY_PAYMENT("Fixed monthly payment"),
    URGENT("Urgent")
}

enum class GoalPurpose(val label: String) {
    SAVE_FOR_GOAL("Save for a goal"),
    PAY_DEBT("Pay debt"),
    CONTROL_DAILY_SPENDING("Control daily spending"),
    MARRIAGE_FAMILY("Prepare for marriage/family"),
    EMERGENCY_FUND("Build emergency fund"),
    GENERAL_BUDGETING("General budgeting"),
    CUSTOM("Custom")
}

data class PrimarySavingsGoal(
    val name: String = "",
    val purpose: GoalPurpose = GoalPurpose.CUSTOM,
    val targetAmount: Double = 0.0,
    val targetDate: String = "",
    val monthlyTargetSuggestion: Double = 0.0
)

data class CategoryBudgetSetup(
    val id: String,
    val name: String,
    val monthlyBudget: Double = 0.0,
    val isEnabled: Boolean = true
)

data class UserPreferenceSetup(
    val categoryBudgets: List<CategoryBudgetSetup> = FinanceDefaults.onboardingCategoryTemplates(),
    val dailyUseBankSender: String = "",
    val savingsBankSender: String = "",
    val dailyUseAccountSuffix: String = "",
    val savingsAccountSuffix: String = "",
    val monthlySavingsReminderEnabled: Boolean = true,
    val debtPaymentReminderEnabled: Boolean = true,
    val overspendingWarningEnabled: Boolean = true,
    val necessaryItemReminderEnabled: Boolean = true
)

data class OnboardingPlan(
    val profile: UserProfile,
    val financialProfile: UserFinancialProfile,
    val categories: List<BudgetCategory>,
    val goals: List<SavingsGoal>,
    val debt: DebtTracker,
    val bankMessageSettings: BankMessageParserSettings,
    val reminderSettings: ReminderSettings,
    val necessaryItems: List<NecessaryItem>
)
