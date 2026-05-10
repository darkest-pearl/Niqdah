package com.musab.niqdah.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musab.niqdah.domain.ai.AiFinanceDraftAction
import com.musab.niqdah.domain.finance.AccountBalanceSnapshot
import com.musab.niqdah.domain.finance.AccountKind
import com.musab.niqdah.domain.finance.BankMessageImportHistory
import com.musab.niqdah.domain.finance.BankMessageImportStatus
import com.musab.niqdah.domain.finance.BankMessageParser
import com.musab.niqdah.domain.finance.BankMessageParserSettings
import com.musab.niqdah.domain.finance.BankMessageSourceSettings
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.DashboardMetrics
import com.musab.niqdah.domain.finance.DebtTracker
import com.musab.niqdah.domain.finance.DisciplineReminderScheduler
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.FinanceCalculator
import com.musab.niqdah.domain.finance.FinanceData
import com.musab.niqdah.domain.finance.FinanceDates
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.FinanceRepository
import com.musab.niqdah.domain.finance.IncomeTransaction
import com.musab.niqdah.domain.finance.InternalTransferRecord
import com.musab.niqdah.domain.finance.MerchantRule
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.NecessaryItem
import com.musab.niqdah.domain.finance.NecessaryItemRecurrence
import com.musab.niqdah.domain.finance.NecessaryItemStatus
import com.musab.niqdah.domain.finance.ParsedBankMessage
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import com.musab.niqdah.domain.finance.PendingBankImport
import com.musab.niqdah.domain.finance.PendingBankImportSaveRules
import com.musab.niqdah.domain.finance.ReminderSettings
import com.musab.niqdah.domain.finance.SaveResult
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
    val statusMessage: String? = null,
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

    val filteredInternalTransferRecords: List<InternalTransferRecord>
        get() = if (selectedTransactionCategoryId == null) {
            data.internalTransferRecords.filter { record ->
                FinanceDates.yearMonthFromMillis(record.messageTimestampMillis) == selectedTransactionMonth
            }
        } else {
            emptyList()
        }

    val availableMonths: List<String>
        get() = (
            data.transactions.map { it.yearMonth } +
                data.incomeTransactions.map { it.yearMonth } +
                data.internalTransferRecords.map { FinanceDates.yearMonthFromMillis(it.messageTimestampMillis) } +
                FinanceDates.currentYearMonth()
            )
            .filter { it.isNotBlank() }
            .distinct()
            .sortedDescending()
}

class FinanceViewModel(
    private val financeRepository: FinanceRepository,
    private val reminderScheduler: DisciplineReminderScheduler? = null
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
                    runCatching { reminderScheduler?.schedule(data) }
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
        _uiState.update { it.copy(errorMessage = null, statusMessage = null) }
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
            showValidationError(validationError)
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

        runSave(
            onSuccess = {
                avoidWarningMessage(categoryId = transaction.categoryId, necessity = transaction.necessity)
                    ?.let { message -> _uiState.update { it.copy(statusMessage = message) } }
            }
        ) {
            financeRepository.upsertTransaction(transaction)
        }
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
            categories = state.data.categories,
            latestBalances = state.data.accountBalanceSnapshots,
            merchantRules = state.data.merchantRules
        )
    }

    fun previewAiFinanceDraftAction(messageInput: String): AiFinanceDraftAction? {
        val parsed = previewBankMessage(senderInput = "", messageInput = messageInput)
        if (parsed.type == ParsedBankMessageType.INFORMATIONAL ||
            (parsed.amount == null && parsed.type == ParsedBankMessageType.UNKNOWN)
        ) {
            return null
        }
        return parsed.toAiFinanceDraftAction()
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
        saveImportedFinanceAction(
            type = type,
            amountInput = amountInput,
            currencyInput = currencyInput,
            categoryId = categoryId,
            description = description,
            necessity = necessity,
            dateInput = dateInput,
            senderName = senderName,
            onSuccess = null,
            onFailure = null
        )
    }

    fun saveAiFinanceDraftAction(
        draft: AiFinanceDraftAction,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        saveImportedFinanceAction(
            type = draft.type,
            amountInput = draft.amount?.let { formatDraftAmount(it) }.orEmpty(),
            currencyInput = draft.currency,
            categoryId = draft.categoryId,
            description = draft.description,
            necessity = draft.necessity,
            dateInput = draft.dateInput,
            senderName = draft.senderName,
            onSuccess = onSuccess,
            onFailure = onFailure
        )
    }

    private fun saveImportedFinanceAction(
        type: ParsedBankMessageType,
        amountInput: String,
        currencyInput: String,
        categoryId: String?,
        description: String,
        necessity: NecessityLevel,
        dateInput: String,
        senderName: String,
        onSuccess: (() -> Unit)?,
        onFailure: ((String) -> Unit)?
    ) {
        val amount = amountInput.toMoneyOrNull()
        val occurredAt = FinanceDates.parseDateInput(dateInput)
        val currency = currencyInput.trim().uppercase(Locale.US).ifBlank { _uiState.value.data.profile.currency }
        val validationError = when {
            type == ParsedBankMessageType.UNKNOWN -> "Choose a message type before saving."
            type == ParsedBankMessageType.INFORMATIONAL ->
                "This message is informational and was not saved as a transaction."
            amount == null || amount <= 0.0 -> "Enter a valid imported amount."
            occurredAt == null -> "Enter the import date as YYYY-MM-DD."
            type == ParsedBankMessageType.EXPENSE && categoryId.isNullOrBlank() -> "Choose a category."
            else -> null
        }

        if (validationError != null) {
            showValidationError(validationError)
            onFailure?.invoke(validationError)
            return
        }

        val parsedAmount = amount ?: return
        val parsedOccurredAt = occurredAt ?: return
        val now = System.currentTimeMillis()
        runSaveResult(onSuccess = onSuccess, onFailure = onFailure) {
            val result = writeImportedFinanceAction(
                type = type,
                amount = parsedAmount,
                currency = currency,
                categoryId = categoryId,
                description = description,
                necessity = necessity,
                occurredAtMillis = parsedOccurredAt,
                senderName = senderName,
                now = now
            )
            if (result is SaveResult.Saved) {
                learnMerchantRuleFromInputs(
                    type = type,
                    merchantName = description,
                    categoryId = categoryId,
                    necessity = necessity
                )
            }
            result
        }
    }

    fun savePendingBankImport(pendingImport: PendingBankImport) {
        val now = System.currentTimeMillis()
        runSaveResult { savePendingBankImportResult(pendingImport, now) }
    }

    fun updatePendingBankImport(pendingImport: PendingBankImport) {
        validatePendingImportEdit(pendingImport)?.let { message ->
            showValidationError(message)
            return
        }
        runSave {
            learnMerchantRuleIfNeeded(pendingImport)
            financeRepository.upsertPendingBankImport(
                pendingImport.copy(updatedAtMillis = System.currentTimeMillis())
            )
        }
    }

    fun dismissPendingBankImport(pendingImport: PendingBankImport) {
        val now = System.currentTimeMillis()
        runSave {
            val paired = PendingBankImportSaveRules.findMatchingTransferCounterpart(
                pendingImport = pendingImport,
                candidates = _uiState.value.data.pendingBankImports
            )
            PendingBankImportSaveRules.idsToRemoveAfterSuccessfulSave(pendingImport, paired).forEach { id ->
                financeRepository.deletePendingBankImport(id)
            }
            financeRepository.upsertBankMessageImportHistory(
                BankMessageImportHistory(
                    messageHash = pendingImport.messageHash,
                    status = BankMessageImportStatus.DISMISSED,
                    senderName = pendingImport.senderName,
                    updatedAtMillis = now
                )
            )
            if (paired != null) {
                financeRepository.upsertBankMessageImportHistory(
                    BankMessageImportHistory(
                        messageHash = paired.messageHash,
                        status = BankMessageImportStatus.DISMISSED,
                        senderName = paired.senderName,
                        updatedAtMillis = now
                    )
                )
            }
        }
    }

    fun updateGoalSavedAmount(goal: SavingsGoal, savedAmountInput: String) {
        val savedAmount = savedAmountInput.toMoneyOrNull()
        if (savedAmount == null || savedAmount < 0.0) {
            showValidationError("Enter a valid saved amount.")
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
            showValidationError("Enter a valid debt payment amount.")
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
            showValidationError(validationError)
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
                showValidationError("Enter a valid budget for ${category.name}.")
                return
            }
            category.copy(monthlyBudget = amount, updatedAtMillis = System.currentTimeMillis())
        }

        runSave {
            updates.forEach { financeRepository.upsertCategory(it) }
        }
    }

    fun updateBankMessageSettings(
        isAutomaticSmsImportEnabled: Boolean,
        dailySenderName: String,
        isDailyEnabled: Boolean,
        savingsSenderName: String,
        isSavingsEnabled: Boolean,
        debitKeywordsInput: String,
        creditKeywordsInput: String,
        savingsTransferKeywordsInput: String,
        dailyUseAccountSuffix: String = "",
        savingsAccountSuffix: String = "",
        isMerchantLearningEnabled: Boolean = true,
        isInternalTransferReminderEnabled: Boolean = true,
        internalTransferReminderThresholdMinutes: Int = FinanceDefaults.DEFAULT_INTERNAL_TRANSFER_REMINDER_MINUTES
    ) {
        val currentSettings = _uiState.value.data.bankMessageSettings
        val settings = BankMessageParserSettings(
            dailyUseSource = BankMessageSourceSettings(
                senderName = dailySenderName.trim(),
                isEnabled = isDailyEnabled
            ),
            savingsSource = BankMessageSourceSettings(
                senderName = savingsSenderName.trim(),
                isEnabled = isSavingsEnabled
            ),
            isAutomaticSmsImportEnabled = isAutomaticSmsImportEnabled,
            requireReviewBeforeSaving = true,
            dailyUseAccountSuffix = dailyUseAccountSuffix.trim().filter { it.isDigit() }.takeLast(4),
            savingsAccountSuffix = savingsAccountSuffix.trim().filter { it.isDigit() }.takeLast(4),
            isMerchantLearningEnabled = isMerchantLearningEnabled,
            isInternalTransferReminderEnabled = isInternalTransferReminderEnabled,
            internalTransferReminderThresholdMinutes = internalTransferReminderThresholdMinutes.coerceIn(1, 24 * 60),
            lastIgnoredSender = currentSettings.lastIgnoredSender,
            lastParsedBankMessageAtMillis = currentSettings.lastParsedBankMessageAtMillis,
            lastIgnoredReason = currentSettings.lastIgnoredReason,
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

    fun updateReminderSettings(
        isMonthlySavingsReminderEnabled: Boolean,
        monthlySavingsReminderDayInput: String,
        monthlySavingsReminderHourInput: String,
        monthlySavingsReminderMinuteInput: String,
        monthlySavingsTargetAmountInput: String,
        isMissedSavingsReminderEnabled: Boolean,
        missedSavingsCheckDayInput: String,
        missedSavingsReminderHourInput: String,
        missedSavingsReminderMinuteInput: String,
        areOverspendingWarningsEnabled: Boolean,
        isAvoidCategoryWarningEnabled: Boolean,
        januaryTargetDateInput: String,
        januaryFundTargetAmountInput: String
    ) {
        val monthlySavingsReminderDay = monthlySavingsReminderDayInput.trim().toIntOrNull()
        val monthlySavingsReminderHour = monthlySavingsReminderHourInput.trim().toIntOrNull()
        val monthlySavingsReminderMinute = monthlySavingsReminderMinuteInput.trim().toIntOrNull()
        val monthlySavingsTargetAmount = monthlySavingsTargetAmountInput.toMoneyOrNull()
        val missedSavingsCheckDay = missedSavingsCheckDayInput.trim().toIntOrNull()
        val missedSavingsReminderHour = missedSavingsReminderHourInput.trim().toIntOrNull()
        val missedSavingsReminderMinute = missedSavingsReminderMinuteInput.trim().toIntOrNull()
        val januaryTargetDate = FinanceDates.parseDateInput(januaryTargetDateInput)
        val januaryFundTargetAmount = januaryFundTargetAmountInput.toMoneyOrNull()

        val validationError = when {
            monthlySavingsReminderDay == null || monthlySavingsReminderDay !in 1..31 ->
                "Enter a monthly savings reminder day from 1 to 31."
            monthlySavingsReminderHour == null || monthlySavingsReminderHour !in 0..23 ->
                "Enter a monthly savings reminder hour from 0 to 23."
            monthlySavingsReminderMinute == null || monthlySavingsReminderMinute !in 0..59 ->
                "Enter a monthly savings reminder minute from 0 to 59."
            monthlySavingsTargetAmount == null || monthlySavingsTargetAmount < 0.0 ->
                "Enter a valid monthly savings target."
            missedSavingsCheckDay == null || missedSavingsCheckDay !in 1..31 ->
                "Enter a missed savings check day from 1 to 31."
            missedSavingsReminderHour == null || missedSavingsReminderHour !in 0..23 ->
                "Enter a missed savings reminder hour from 0 to 23."
            missedSavingsReminderMinute == null || missedSavingsReminderMinute !in 0..59 ->
                "Enter a missed savings reminder minute from 0 to 59."
            januaryTargetDate == null -> "Enter the January target date as YYYY-MM-DD."
            januaryFundTargetAmount == null || januaryFundTargetAmount < 0.0 ->
                "Enter a valid January fund target."
            else -> null
        }

        if (validationError != null) {
            showValidationError(validationError)
            return
        }

        val parsedMonthlySavingsTargetAmount = monthlySavingsTargetAmount ?: return
        val parsedJanuaryFundTargetAmount = januaryFundTargetAmount ?: return
        val now = System.currentTimeMillis()
        val current = _uiState.value.data
        val settings = ReminderSettings(
            isMonthlySavingsReminderEnabled = isMonthlySavingsReminderEnabled,
            monthlySavingsReminderDay = monthlySavingsReminderDay ?: return,
            monthlySavingsReminderHour = monthlySavingsReminderHour ?: return,
            monthlySavingsReminderMinute = monthlySavingsReminderMinute ?: return,
            monthlySavingsTargetAmount = parsedMonthlySavingsTargetAmount,
            isMissedSavingsReminderEnabled = isMissedSavingsReminderEnabled,
            missedSavingsCheckDay = missedSavingsCheckDay ?: return,
            missedSavingsReminderHour = missedSavingsReminderHour ?: return,
            missedSavingsReminderMinute = missedSavingsReminderMinute ?: return,
            areOverspendingWarningsEnabled = areOverspendingWarningsEnabled,
            isAvoidCategoryWarningEnabled = isAvoidCategoryWarningEnabled,
            januaryTargetDate = januaryTargetDateInput.trim(),
            januaryFundTargetAmount = parsedJanuaryFundTargetAmount,
            updatedAtMillis = now
        )

        runSave {
            financeRepository.upsertReminderSettings(settings)
            financeRepository.upsertProfile(
                current.profile.copy(
                    monthlySavingsTarget = parsedMonthlySavingsTargetAmount,
                    updatedAtMillis = now
                )
            )
            current.categories
                .firstOrNull { it.id == FinanceDefaults.MARRIAGE_SAVINGS_CATEGORY_ID }
                ?.let { category ->
                    financeRepository.upsertCategory(
                        category.copy(
                            monthlyBudget = parsedMonthlySavingsTargetAmount,
                            updatedAtMillis = now
                        )
                    )
                }
            current.goals
                .firstOrNull { it.id == FinanceDefaults.MARRIAGE_GOAL_ID }
                ?.let { goal ->
                    financeRepository.upsertGoal(
                        goal.copy(
                            targetAmount = parsedJanuaryFundTargetAmount,
                            updatedAtMillis = now
                        )
                    )
                }
        }
    }

    fun saveNecessaryItem(
        existing: NecessaryItem?,
        titleInput: String,
        amountInput: String,
        dueDayInput: String,
        dueDateInput: String,
        recurrence: NecessaryItemRecurrence,
        status: NecessaryItemStatus,
        isNotificationEnabled: Boolean
    ) {
        val title = titleInput.trim()
        val amount = amountInput.takeIf { it.isNotBlank() }?.toMoneyOrNull()
        val dueDay = dueDayInput.trim().toIntOrNull()
        val dueDate = dueDateInput.takeIf { it.isNotBlank() }?.let { FinanceDates.parseDateInput(it) }
        val validationError = when {
            title.isBlank() -> "Enter a necessary item title."
            amountInput.isNotBlank() && (amount == null || amount < 0.0) ->
                "Enter a valid necessary item amount."
            recurrence == NecessaryItemRecurrence.MONTHLY && (dueDay == null || dueDay !in 1..31) ->
                "Enter a due day from 1 to 31."
            recurrence == NecessaryItemRecurrence.ONE_TIME && dueDate == null ->
                "Enter a due date as YYYY-MM-DD."
            else -> null
        }

        if (validationError != null) {
            showValidationError(validationError)
            return
        }

        val now = System.currentTimeMillis()
        val item = NecessaryItem(
            id = existing?.id ?: UUID.randomUUID().toString(),
            title = title,
            amount = amount,
            dueDayOfMonth = dueDay ?: existing?.dueDayOfMonth ?: 1,
            dueDateMillis = dueDate,
            recurrence = recurrence,
            status = status,
            isNotificationEnabled = isNotificationEnabled,
            createdAtMillis = existing?.createdAtMillis?.takeIf { it > 0L } ?: now,
            updatedAtMillis = now
        )

        runSave { financeRepository.upsertNecessaryItem(item) }
    }

    fun updateNecessaryItemStatus(item: NecessaryItem, status: NecessaryItemStatus) {
        runSave {
            financeRepository.upsertNecessaryItem(
                item.copy(
                    status = status,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteNecessaryItem(itemId: String) {
        runSave { financeRepository.deleteNecessaryItem(itemId) }
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

    private suspend fun writeImportedFinanceAction(
        type: ParsedBankMessageType,
        amount: Double,
        currency: String,
        categoryId: String?,
        description: String,
        necessity: NecessityLevel,
        occurredAtMillis: Long,
        senderName: String,
        now: Long
    ): SaveResult {
        when (type) {
            ParsedBankMessageType.EXPENSE -> {
                financeRepository.upsertTransaction(
                    ExpenseTransaction(
                        id = UUID.randomUUID().toString(),
                        categoryId = categoryId.orEmpty(),
                        amount = amount,
                        currency = currency,
                        note = description.trim(),
                        necessity = necessity,
                        occurredAtMillis = occurredAtMillis,
                        yearMonth = FinanceDates.yearMonthFromMillis(occurredAtMillis),
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
                return SaveResult.Saved(
                    avoidWarningMessage(categoryId = categoryId.orEmpty(), necessity = necessity)
                        ?: "Saved successfully."
                )
            }
            ParsedBankMessageType.INCOME -> {
                financeRepository.upsertIncomeTransaction(
                    IncomeTransaction(
                        id = UUID.randomUUID().toString(),
                        amount = amount,
                        currency = currency,
                        source = senderName.trim(),
                        note = description.trim(),
                        occurredAtMillis = occurredAtMillis,
                        yearMonth = FinanceDates.yearMonthFromMillis(occurredAtMillis),
                        createdAtMillis = now,
                        updatedAtMillis = now
                    )
                )
                return SaveResult.Saved("Saved successfully.")
            }
            ParsedBankMessageType.SAVINGS_TRANSFER -> {
                saveSavingsContribution(
                    amount = amount,
                    currency = currency,
                    description = description,
                    occurredAtMillis = occurredAtMillis,
                    now = now
                )
                return SaveResult.Saved("Saved successfully.")
            }
            ParsedBankMessageType.INTERNAL_TRANSFER_OUT -> {
                financeRepository.upsertInternalTransferRecord(
                    InternalTransferRecord(
                        id = "manual-internal-transfer-${UUID.randomUUID()}",
                        amount = amount,
                        currency = currency,
                        sourceAccountSuffix = "",
                        targetAccountSuffix = null,
                        direction = com.musab.niqdah.domain.finance.InternalTransferDirection.OUT,
                        transferType = com.musab.niqdah.domain.finance.InternalTransferType.ACCOUNT_TO_ACCOUNT,
                        status = com.musab.niqdah.domain.finance.InternalTransferStatus.NEEDS_MATCHING_CREDIT,
                        pairedImportId = null,
                        createdAtMillis = now,
                        messageTimestampMillis = occurredAtMillis,
                        note = listOf(
                            "Not counted as spending.",
                            description.trim().ifBlank { "Imported internal transfer" }
                        ).distinct().joinToString("\n"),
                        sourceMessageHash = ""
                    )
                )
                return SaveResult.Saved(
                    "Saved. Balance was not updated because this SMS did not include available balance."
                )
            }
            ParsedBankMessageType.INFORMATIONAL,
            ParsedBankMessageType.UNKNOWN -> return SaveResult.NeedsReview("Choose a message type before saving.")
        }
    }

    private suspend fun savePendingBankImportResult(
        pendingImport: PendingBankImport,
        now: Long
    ): SaveResult {
        PendingBankImportSaveRules.validate(pendingImport)?.let { return it }
        val amount = pendingImport.amount ?: return SaveResult.Blocked("Enter a valid imported amount.")
        val paired = PendingBankImportSaveRules.findMatchingTransferCounterpart(
            pendingImport = pendingImport,
            candidates = _uiState.value.data.pendingBankImports
        )
        val isPairedTransfer = PendingBankImportSaveRules.isMatchedInternalTransferPair(pendingImport, paired)

        val result = try {
            when (pendingImport.type) {
                ParsedBankMessageType.EXPENSE -> {
                    writeImportedFinanceAction(
                        type = pendingImport.type,
                        amount = amount,
                        currency = pendingImport.currency,
                        categoryId = pendingImport.suggestedCategoryId,
                        description = pendingImport.noteWithReviewContext(),
                        necessity = pendingImport.suggestedNecessity,
                        occurredAtMillis = pendingImport.occurredAtMillis,
                        senderName = pendingImport.senderName,
                        now = now
                    )
                }
                ParsedBankMessageType.INCOME -> {
                    writeImportedFinanceAction(
                        type = pendingImport.type,
                        amount = amount,
                        currency = pendingImport.currency,
                        categoryId = pendingImport.suggestedCategoryId,
                        description = pendingImport.noteWithReviewContext(),
                        necessity = pendingImport.suggestedNecessity,
                        occurredAtMillis = pendingImport.occurredAtMillis,
                        senderName = pendingImport.senderName,
                        now = now
                    )
                }
                ParsedBankMessageType.SAVINGS_TRANSFER -> {
                    saveSavingsContribution(
                        amount = amount,
                        currency = pendingImport.currency,
                        description = pendingImport.noteWithReviewContext(),
                        occurredAtMillis = pendingImport.occurredAtMillis,
                        now = now
                    )
                    if (paired?.type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT) {
                        financeRepository.upsertInternalTransferRecord(
                            PendingBankImportSaveRules.internalTransferRecord(
                                debitImport = paired,
                                pairedCreditImport = pendingImport,
                                nowMillis = now
                            )
                        )
                    }
                    SaveResult.Saved(
                        if (isPairedTransfer) {
                            PendingBankImportSaveRules.pairedInternalTransferSavedMessage()
                        } else {
                            PendingBankImportSaveRules.savingsTransferSavedMessage(pendingImport)
                        }
                    )
                }
                ParsedBankMessageType.INTERNAL_TRANSFER_OUT -> {
                    financeRepository.upsertInternalTransferRecord(
                        PendingBankImportSaveRules.internalTransferRecord(
                            debitImport = pendingImport,
                            pairedCreditImport = paired?.takeIf { it.type == ParsedBankMessageType.SAVINGS_TRANSFER },
                            nowMillis = now
                        )
                    )
                    if (paired?.type == ParsedBankMessageType.SAVINGS_TRANSFER) {
                        saveSavingsContribution(
                            amount = amount,
                            currency = pendingImport.currency,
                            description = paired.noteWithReviewContext(),
                            occurredAtMillis = paired.occurredAtMillis,
                            now = now
                        )
                    }
                    SaveResult.Saved(
                        if (isPairedTransfer) {
                            PendingBankImportSaveRules.pairedInternalTransferSavedMessage()
                        } else {
                            PendingBankImportSaveRules.internalTransferSavedMessage(
                                pendingImport = pendingImport,
                                latestDailyUseBalance = _uiState.value.data.latestDailyUseBalance
                            )
                        }
                    )
                }
                ParsedBankMessageType.INFORMATIONAL ->
                    SaveResult.Ignored("This message is informational and was not saved as a transaction.")
                ParsedBankMessageType.UNKNOWN ->
                    SaveResult.NeedsReview("Choose a message type before saving.")
            }
        } catch (error: Throwable) {
            return if (isPairedTransfer) {
                SaveResult.Error("Could not save paired transfer: ${error.friendlyFinanceMessage()}")
            } else {
                SaveResult.Error(error.friendlyFinanceMessage())
            }
        }

        if (result is SaveResult.Saved) {
            upsertBalanceSnapshotIfPresent(pendingImport, now)
            if (paired != null) {
                upsertBalanceSnapshotIfPresent(paired, now)
            }
            learnMerchantRuleIfNeeded(pendingImport)
            val idsToRemove = PendingBankImportSaveRules.idsToRemoveAfterSuccessfulSave(pendingImport, paired)
            idsToRemove.forEach { financeRepository.deletePendingBankImport(it) }
            financeRepository.upsertBankMessageImportHistory(savedHistory(pendingImport, now))
            if (paired != null) {
                financeRepository.upsertBankMessageImportHistory(savedHistory(paired, now, linked = true))
            }
        }
        return result
    }

    private fun savedHistory(
        pendingImport: PendingBankImport,
        now: Long,
        linked: Boolean = false
    ): BankMessageImportHistory =
        BankMessageImportHistory(
            messageHash = pendingImport.messageHash,
            status = if (linked) BankMessageImportStatus.LINKED else BankMessageImportStatus.SAVED,
            senderName = pendingImport.senderName,
            updatedAtMillis = now
        )

    private suspend fun upsertBalanceSnapshotIfPresent(pendingImport: PendingBankImport, now: Long) {
        val availableBalance = pendingImport.availableBalance ?: return
        val accountKind = when (pendingImport.sourceType) {
            com.musab.niqdah.domain.finance.BankMessageSourceType.DAILY_USE -> AccountKind.DAILY_USE
            com.musab.niqdah.domain.finance.BankMessageSourceType.SAVINGS -> AccountKind.SAVINGS
            com.musab.niqdah.domain.finance.BankMessageSourceType.UNKNOWN -> {
                if (pendingImport.type == ParsedBankMessageType.INTERNAL_TRANSFER_OUT) {
                    AccountKind.DAILY_USE
                } else {
                    return
                }
            }
        }
        financeRepository.upsertAccountBalanceSnapshot(
            AccountBalanceSnapshot(
                accountKind = accountKind,
                sender = pendingImport.senderName,
                availableBalance = availableBalance,
                currency = pendingImport.availableBalanceCurrency.ifBlank { pendingImport.currency },
                messageTimestampMillis = pendingImport.occurredAtMillis,
                sourceMessageHash = pendingImport.messageHash,
                createdAtMillis = now
            )
        )
    }

    private fun runSave(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, statusMessage = null) }
            runCatching { block() }
                .onSuccess { onSuccess?.invoke() }
                .onSuccess {
                    _uiState.update { state ->
                        if (state.statusMessage == null) {
                            state.copy(statusMessage = "Saved successfully.", errorMessage = null)
                        } else {
                            state.copy(errorMessage = null)
                        }
                    }
                }
                .onFailure { error ->
                    val message = error.friendlyFinanceMessage()
                    _uiState.update { it.copy(errorMessage = message) }
                    onFailure?.invoke(message)
                }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun runSaveResult(
        onSuccess: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null,
        block: suspend () -> SaveResult
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, statusMessage = null) }
            val result = runCatching { block() }
                .getOrElse { SaveResult.Error(it.friendlyFinanceMessage()) }
            when (result) {
                is SaveResult.Saved -> {
                    _uiState.update { it.copy(statusMessage = result.message, errorMessage = null) }
                    onSuccess?.invoke()
                }
                is SaveResult.NeedsReview -> {
                    _uiState.update { it.copy(statusMessage = result.message, errorMessage = null) }
                    onFailure?.invoke(result.message)
                }
                is SaveResult.Ignored -> {
                    _uiState.update { it.copy(statusMessage = result.reason, errorMessage = null) }
                    onFailure?.invoke(result.reason)
                }
                is SaveResult.Blocked -> {
                    _uiState.update { it.copy(errorMessage = result.reason, statusMessage = null) }
                    onFailure?.invoke(result.reason)
                }
                is SaveResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.reason, statusMessage = null) }
                    onFailure?.invoke(result.reason)
                }
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun String.toMoneyOrNull(): Double? =
        trim().replace(",", "").toDoubleOrNull()

    private suspend fun learnMerchantRuleIfNeeded(pendingImport: PendingBankImport) {
        val state = _uiState.value
        if (!state.data.bankMessageSettings.isMerchantLearningEnabled) return
        if (pendingImport.type != ParsedBankMessageType.EXPENSE) return
        val merchantName = pendingImport.merchantName.ifBlank { pendingImport.description }
        val normalized = merchantName.normalizedMerchantName()
        if (normalized.isBlank() || pendingImport.suggestedCategoryId.isNullOrBlank()) return
        val category = state.data.categories.firstOrNull { it.id == pendingImport.suggestedCategoryId }
        val existing = state.data.merchantRules.firstOrNull { it.normalizedMerchantName == normalized }
        financeRepository.upsertMerchantRule(
            MerchantRule(
                normalizedMerchantName = normalized,
                merchantName = merchantName.trim(),
                categoryId = pendingImport.suggestedCategoryId,
                categoryName = category?.name ?: pendingImport.suggestedCategoryName,
                necessity = pendingImport.suggestedNecessity,
                lastUpdatedMillis = System.currentTimeMillis(),
                timesConfirmed = (existing?.timesConfirmed ?: 0) + 1
            )
        )
    }

    private suspend fun learnMerchantRuleFromInputs(
        type: ParsedBankMessageType,
        merchantName: String,
        categoryId: String?,
        necessity: NecessityLevel
    ) {
        val state = _uiState.value
        if (!state.data.bankMessageSettings.isMerchantLearningEnabled) return
        if (type != ParsedBankMessageType.EXPENSE || categoryId.isNullOrBlank()) return
        val normalized = merchantName.normalizedMerchantName()
        if (normalized.isBlank()) return
        val category = state.data.categories.firstOrNull { it.id == categoryId }
        val existing = state.data.merchantRules.firstOrNull { it.normalizedMerchantName == normalized }
        financeRepository.upsertMerchantRule(
            MerchantRule(
                normalizedMerchantName = normalized,
                merchantName = merchantName.trim(),
                categoryId = categoryId,
                categoryName = category?.name ?: "Uncategorized",
                necessity = necessity,
                lastUpdatedMillis = System.currentTimeMillis(),
                timesConfirmed = (existing?.timesConfirmed ?: 0) + 1
            )
        )
    }

    private suspend fun pairMatchingInternalTransferIfNeeded(
        pendingImport: PendingBankImport,
        now: Long
    ) {
        if (pendingImport.type != ParsedBankMessageType.SAVINGS_TRANSFER &&
            pendingImport.type != ParsedBankMessageType.INTERNAL_TRANSFER_OUT
        ) {
            return
        }
        val amount = pendingImport.amount ?: return
        val dayMillis = 24 * 60 * 60 * 1000L
        val paired = _uiState.value.data.pendingBankImports.firstOrNull { candidate ->
            candidate.id != pendingImport.id &&
                candidate.amount == amount &&
                candidate.currency == pendingImport.currency &&
                kotlin.math.abs(candidate.occurredAtMillis - pendingImport.occurredAtMillis) <= dayMillis &&
                setOf(candidate.type, pendingImport.type) == setOf(
                    ParsedBankMessageType.SAVINGS_TRANSFER,
                    ParsedBankMessageType.INTERNAL_TRANSFER_OUT
                )
        } ?: return

        financeRepository.deletePendingBankImport(paired.id)
        financeRepository.upsertBankMessageImportHistory(
            BankMessageImportHistory(
                messageHash = paired.messageHash,
                status = BankMessageImportStatus.LINKED,
                senderName = paired.senderName,
                updatedAtMillis = now
            )
        )
    }

    private fun PendingBankImport.saveSuccessMessage(): String =
        if (type == ParsedBankMessageType.SAVINGS_TRANSFER && availableBalance == null) {
            "Saved transfer. Savings balance was not updated because this SMS did not include an available balance."
        } else {
            "Saved successfully."
        }

    private fun keywordListOrDefault(input: String, default: List<String>): List<String> =
        input.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { default }

    private fun avoidWarningMessage(categoryId: String, necessity: NecessityLevel): String? {
        if (!_uiState.value.data.reminderSettings.isAvoidCategoryWarningEnabled) return null
        return if (necessity == NecessityLevel.AVOID || categoryId == FinanceDefaults.AVOID_CATEGORY_ID) {
            "This was marked Avoid. Consider whether it was necessary."
        } else {
            null
        }
    }

    private fun savingsImportTransactionId(yearMonth: String): String =
        "bank-message-savings-$yearMonth"

    private fun String.normalizedMerchantName(): String =
        trim()
            .lowercase(Locale.US)
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

    private fun ParsedBankMessage.toAiFinanceDraftAction(): AiFinanceDraftAction =
        AiFinanceDraftAction(
            type = type,
            amount = amount,
            currency = currency.ifBlank { _uiState.value.data.profile.currency },
            categoryId = suggestedCategoryId,
            categoryName = suggestedCategoryName,
            necessity = suggestedNecessity,
            description = description,
            dateInput = FinanceDates.dateInputFromMillis(occurredAtMillis),
            confidence = confidence,
            senderName = senderName,
            originalText = ""
        )

    private fun validatePendingImportEdit(pendingImport: PendingBankImport): String? =
        when {
            pendingImport.amount == null || pendingImport.amount <= 0.0 -> "Enter a valid imported amount."
            pendingImport.type == ParsedBankMessageType.EXPENSE &&
                pendingImport.suggestedCategoryId.isNullOrBlank() -> "Choose a category."
            else -> null
        }

    private fun PendingBankImport.noteWithReviewContext(): String =
        listOf(description.trim(), reviewNote.trim())
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")

    private fun formatDraftAmount(amount: Double): String =
        if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()

    private fun showValidationError(message: String) {
        _uiState.update { it.copy(errorMessage = message, statusMessage = null) }
    }

    private fun Throwable.friendlyFinanceMessage(): String =
        message?.takeIf { it.isNotBlank() }
            ?: "Niqdah could not save the finance update. Please try again."

    class Factory(
        private val financeRepository: FinanceRepository,
        private val reminderScheduler: DisciplineReminderScheduler? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FinanceViewModel::class.java)) {
                return FinanceViewModel(financeRepository, reminderScheduler) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
