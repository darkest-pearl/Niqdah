package com.musab.niqdah.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musab.niqdah.domain.finance.BankMessageParser
import com.musab.niqdah.domain.finance.BankMessageParserSettings
import com.musab.niqdah.domain.finance.BankMessageSourceSettings
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.DashboardMetrics
import com.musab.niqdah.domain.finance.DebtTracker
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.FinanceCalculator
import com.musab.niqdah.domain.finance.FinanceData
import com.musab.niqdah.domain.finance.FinanceDates
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.FinanceRepository
import com.musab.niqdah.domain.finance.IncomeTransaction
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.ParsedBankMessage
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import com.musab.niqdah.domain.finance.SavingsGoal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

data class FinanceUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val data: FinanceData = FinanceData.empty(),
    val dashboard: DashboardMetrics = FinanceCalculator.dashboard(FinanceData.empty(), FinanceDates.currentYearMonth()),
    val selectedTransactionMonth: String = FinanceDates.currentYearMonth(),
    val selectedTransactionCategoryId: String? = null
) {
    val filteredTransactions: List<ExpenseTransaction>
        get() = data.transactions.filter { transaction ->
            transaction.yearMonth == selectedTransactionMonth &&
                (selectedTransactionCategoryId == null || transaction.categoryId == selectedTransactionCategoryId)
        }

    val filteredIncomeTransactions: List<IncomeTransaction>
        get() = if (selectedTransactionCategoryId == null) {
            data.incomeTransactions.filter { transaction -> transaction.yearMonth == selectedTransactionMonth }
        } else {
            emptyList()
        }

    val availableMonths: List<String>
        get() = (
            data.transactions.map { it.yearMonth } +
                data.incomeTransactions.map { it.yearMonth } +
                FinanceDates.currentYearMonth()
            )
            .filter { it.isNotBlank() }
            .distinct()
            .sortedDescending()
}

class FinanceViewModel(
    private val financeRepository: FinanceRepository
) : ViewModel() {
    private val dashboardMonth = FinanceDates.currentYearMonth()
    private val bankMessageParser = BankMessageParser()
    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                financeRepository.ensureDefaults()
                financeRepository.observeFinanceData().collect { data ->
                    val dashboard = FinanceCalculator.dashboard(data, dashboardMonth)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            data = data,
                            dashboard = dashboard,
                            errorMessage = null
                        )
                    }
                    runCatching { financeRepository.saveMonthlySnapshot(dashboard.snapshot) }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.friendlyFinanceMessage()
                    )
                }
            }
        }
    }

    fun setTransactionMonth(month: String) {
        _uiState.update { it.copy(selectedTransactionMonth = month) }
    }

    fun setTransactionCategoryFilter(categoryId: String?) {
        _uiState.update { it.copy(selectedTransactionCategoryId = categoryId) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun saveTransaction(
        existing: ExpenseTransaction?,
        amountInput: String,
        categoryId: String,
        note: String,
        necessity: NecessityLevel,
        dateInput: String
    ) {
        val amount = amountInput.toMoneyOrNull()
        val occurredAt = FinanceDates.parseDateInput(dateInput)
        val validationError = when {
            amount == null || amount <= 0.0 -> "Enter a valid transaction amount."
            categoryId.isBlank() -> "Choose a category."
            occurredAt == null -> "Enter the date as YYYY-MM-DD."
            else -> null
        }

        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        val parsedAmount = amount ?: return
        val parsedOccurredAt = occurredAt ?: return
        val now = System.currentTimeMillis()
        val transaction = ExpenseTransaction(
            id = existing?.id ?: UUID.randomUUID().toString(),
            categoryId = categoryId,
            amount = parsedAmount,
            currency = existing?.currency?.ifBlank { _uiState.value.data.profile.currency }
                ?: _uiState.value.data.profile.currency,
            note = note.trim(),
            necessity = necessity,
            occurredAtMillis = parsedOccurredAt,
            yearMonth = FinanceDates.yearMonthFromMillis(parsedOccurredAt),
            createdAtMillis = existing?.createdAtMillis?.takeIf { it > 0L } ?: now,
            updatedAtMillis = now
        )

        runSave { financeRepository.upsertTransaction(transaction) }
    }

    fun deleteTransaction(transactionId: String) {
        runSave { financeRepository.deleteTransaction(transactionId) }
    }

    fun deleteIncomeTransaction(transactionId: String) {
        runSave { financeRepository.deleteIncomeTransaction(transactionId) }
    }

    fun previewBankMessage(senderInput: String, messageInput: String): ParsedBankMessage {
        val state = _uiState.value
        return bankMessageParser.parse(
            rawMessage = messageInput,
            manualSenderName = senderInput,
            settings = state.data.bankMessageSettings,
            categories = state.data.categories
        )
    }

    fun saveImportedBankMessage(
        type: ParsedBankMessageType,
        amountInput: String,
        currencyInput: String,
        categoryId: String?,
        description: String,
        necessity: NecessityLevel,
        dateInput: String,
        senderName: String
    ) {
        val amount = amountInput.toMoneyOrNull()
        val occurredAt = FinanceDates.parseDateInput(dateInput)
        val currency = currencyInput.trim().uppercase(Locale.US).ifBlank { _uiState.value.data.profile.currency }
        val validationError = when {
            type == ParsedBankMessageType.UNKNOWN -> "Choose a message type before saving."
            amount == null || amount <= 0.0 -> "Enter a valid imported amount."
            occurredAt == null -> "Enter the import date as YYYY-MM-DD."
            type == ParsedBankMessageType.EXPENSE && categoryId.isNullOrBlank() -> "Choose a category."
            else -> null
        }

        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        val parsedAmount = amount ?: return
        val parsedOccurredAt = occurredAt ?: return
        val now = System.currentTimeMillis()
        runSave {
            when (type) {
                ParsedBankMessageType.EXPENSE -> {
                    financeRepository.upsertTransaction(
                        ExpenseTransaction(
                            id = UUID.randomUUID().toString(),
                            categoryId = categoryId.orEmpty(),
                            amount = parsedAmount,
                            currency = currency,
                            note = description.trim(),
                            necessity = necessity,
                            occurredAtMillis = parsedOccurredAt,
                            yearMonth = FinanceDates.yearMonthFromMillis(parsedOccurredAt),
                            createdAtMillis = now,
                            updatedAtMillis = now
                        )
                    )
                }
                ParsedBankMessageType.INCOME -> {
                    financeRepository.upsertIncomeTransaction(
                        IncomeTransaction(
                            id = UUID.randomUUID().toString(),
                            amount = parsedAmount,
                            currency = currency,
                            source = senderName.trim(),
                            note = description.trim(),
                            occurredAtMillis = parsedOccurredAt,
                            yearMonth = FinanceDates.yearMonthFromMillis(parsedOccurredAt),
                            createdAtMillis = now,
                            updatedAtMillis = now
                        )
                    )
                }
                ParsedBankMessageType.SAVINGS_TRANSFER -> {
                    saveSavingsContribution(
                        amount = parsedAmount,
                        currency = currency,
                        description = description,
                        occurredAtMillis = parsedOccurredAt,
                        now = now
                    )
                }
                ParsedBankMessageType.UNKNOWN -> Unit
            }
        }
    }

    fun updateGoalSavedAmount(goal: SavingsGoal, savedAmountInput: String) {
        val savedAmount = savedAmountInput.toMoneyOrNull()
        if (savedAmount == null || savedAmount < 0.0) {
            _uiState.update { it.copy(errorMessage = "Enter a valid saved amount.") }
            return
        }

        runSave {
            financeRepository.upsertGoal(
                goal.copy(
                    savedAmount = savedAmount,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun recordDebtPayment(amountInput: String) {
        val payment = amountInput.toMoneyOrNull()
        val debt = _uiState.value.data.debt
        if (payment == null || payment <= 0.0) {
            _uiState.update { it.copy(errorMessage = "Enter a valid debt payment amount.") }
            return
        }

        runSave {
            financeRepository.upsertDebt(
                debt.copy(
                    remainingAmount = (debt.remainingAmount - payment).coerceAtLeast(0.0),
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateProfileAndDebt(
        salaryInput: String,
        extraIncomeInput: String,
        monthlySavingsTargetInput: String,
        currencyInput: String,
        startingDebtInput: String,
        remainingDebtInput: String
    ) {
        val salary = salaryInput.toMoneyOrNull()
        val extraIncome = extraIncomeInput.toMoneyOrNull()
        val monthlySavingsTarget = monthlySavingsTargetInput.toMoneyOrNull()
        val startingDebt = startingDebtInput.toMoneyOrNull()
        val remainingDebt = remainingDebtInput.toMoneyOrNull()

        val validationError = when {
            salary == null || salary < 0.0 -> "Enter a valid salary."
            extraIncome == null || extraIncome < 0.0 -> "Enter a valid extra income amount."
            monthlySavingsTarget == null || monthlySavingsTarget < 0.0 -> "Enter a valid savings target."
            currencyInput.trim().isBlank() -> "Enter a currency."
            startingDebt == null || startingDebt < 0.0 -> "Enter a valid starting debt."
            remainingDebt == null || remainingDebt < 0.0 -> "Enter a valid remaining debt."
            else -> null
        }

        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        val parsedSalary = salary ?: return
        val parsedExtraIncome = extraIncome ?: return
        val parsedSavingsTarget = monthlySavingsTarget ?: return
        val parsedStartingDebt = startingDebt ?: return
        val parsedRemainingDebt = remainingDebt ?: return
        val now = System.currentTimeMillis()
        val state = _uiState.value
        val profile = state.data.profile.copy(
            salary = parsedSalary,
            extraIncome = parsedExtraIncome,
            monthlySavingsTarget = parsedSavingsTarget,
            currency = currencyInput.trim().uppercase(Locale.US),
            updatedAtMillis = now
        )
        val debt = DebtTracker(
            startingAmount = parsedStartingDebt,
            remainingAmount = parsedRemainingDebt,
            monthlyAutoReduction = parsedExtraIncome,
            updatedAtMillis = now
        )

        runSave {
            financeRepository.upsertProfile(profile)
            financeRepository.upsertDebt(debt)
        }
    }

    fun updateCategoryBudgets(inputs: Map<String, String>) {
        val categories = _uiState.value.data.categories
        val updates = categories.map { category ->
            val amount = inputs[category.id].orEmpty().toMoneyOrNull()
            if (amount == null || amount < 0.0) {
                _uiState.update { it.copy(errorMessage = "Enter a valid budget for ${category.name}.") }
                return
            }
            category.copy(monthlyBudget = amount, updatedAtMillis = System.currentTimeMillis())
        }

        runSave {
            updates.forEach { financeRepository.upsertCategory(it) }
        }
    }

    fun updateBankMessageSettings(
        dailySenderName: String,
        isDailyEnabled: Boolean,
        savingsSenderName: String,
        isSavingsEnabled: Boolean,
        debitKeywordsInput: String,
        creditKeywordsInput: String,
        savingsTransferKeywordsInput: String
    ) {
        val settings = BankMessageParserSettings(
            dailyUseSource = BankMessageSourceSettings(
                senderName = dailySenderName.trim(),
                isEnabled = isDailyEnabled
            ),
            savingsSource = BankMessageSourceSettings(
                senderName = savingsSenderName.trim(),
                isEnabled = isSavingsEnabled
            ),
            debitKeywords = keywordListOrDefault(
                debitKeywordsInput,
                FinanceDefaults.DEFAULT_DEBIT_KEYWORDS
            ),
            creditKeywords = keywordListOrDefault(
                creditKeywordsInput,
                FinanceDefaults.DEFAULT_CREDIT_KEYWORDS
            ),
            savingsTransferKeywords = keywordListOrDefault(
                savingsTransferKeywordsInput,
                FinanceDefaults.DEFAULT_SAVINGS_TRANSFER_KEYWORDS
            )
        )

        runSave { financeRepository.upsertBankMessageSettings(settings) }
    }

    private suspend fun saveSavingsContribution(
        amount: Double,
        currency: String,
        description: String,
        occurredAtMillis: Long,
        now: Long
    ) {
        val state = _uiState.value
        val yearMonth = FinanceDates.yearMonthFromMillis(occurredAtMillis)
        val existingContribution = state.data.transactions.firstOrNull {
            it.id == savingsImportTransactionId(yearMonth)
        }
        financeRepository.upsertTransaction(
            ExpenseTransaction(
                id = existingContribution?.id ?: savingsImportTransactionId(yearMonth),
                categoryId = FinanceDefaults.MARRIAGE_SAVINGS_CATEGORY_ID,
                amount = (existingContribution?.amount ?: 0.0) + amount,
                currency = currency,
                note = description.trim().ifBlank { "Imported savings transfer" },
                necessity = NecessityLevel.NECESSARY,
                occurredAtMillis = existingContribution?.occurredAtMillis ?: occurredAtMillis,
                yearMonth = yearMonth,
                createdAtMillis = existingContribution?.createdAtMillis?.takeIf { it > 0L } ?: now,
                updatedAtMillis = now
            )
        )

        val marriageGoal = state.data.goals.firstOrNull { it.id == FinanceDefaults.MARRIAGE_GOAL_ID }
            ?: FinanceDefaults.savingsGoals(now).first { it.id == FinanceDefaults.MARRIAGE_GOAL_ID }
        financeRepository.upsertGoal(
            marriageGoal.copy(
                savedAmount = marriageGoal.savedAmount + amount,
                updatedAtMillis = now
            )
        )
    }

    private fun runSave(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching { block() }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.friendlyFinanceMessage()) }
                }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun String.toMoneyOrNull(): Double? =
        trim().replace(",", "").toDoubleOrNull()

    private fun keywordListOrDefault(input: String, default: List<String>): List<String> =
        input.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { default }

    private fun savingsImportTransactionId(yearMonth: String): String =
        "bank-message-savings-$yearMonth"

    private fun Throwable.friendlyFinanceMessage(): String =
        message?.takeIf { it.isNotBlank() }
            ?: "Niqdah could not save the finance update. Please try again."

    class Factory(
        private val financeRepository: FinanceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
                return FinanceViewModel(financeRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
