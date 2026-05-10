package com.musab.niqdah.ui.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.FinanceDates
import com.musab.niqdah.domain.finance.IncomeTransaction
import com.musab.niqdah.domain.finance.InternalTransferRecord
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.ParsedBankMessage
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import com.musab.niqdah.domain.finance.PendingBankImport
import com.musab.niqdah.domain.finance.PendingBankImportDisplayItem
import com.musab.niqdah.domain.finance.PendingBankImportDisplayRules
import com.musab.niqdah.domain.finance.PendingBankImportSaveRules

private sealed interface TransactionTimelineItem {
    val key: String
    val occurredAtMillis: Long

    data class ExpenseItem(val transaction: ExpenseTransaction) : TransactionTimelineItem {
        override val key: String = "expense-${transaction.id}"
        override val occurredAtMillis: Long = transaction.occurredAtMillis
    }

    data class IncomeItem(val transaction: IncomeTransaction) : TransactionTimelineItem {
        override val key: String = "income-${transaction.id}"
        override val occurredAtMillis: Long = transaction.occurredAtMillis
    }

    data class InternalTransferItem(val record: InternalTransferRecord) : TransactionTimelineItem {
        override val key: String = "internal-transfer-${record.id}"
        override val occurredAtMillis: Long = record.messageTimestampMillis
    }
}

@Composable
fun TransactionsScreen(
    uiState: FinanceUiState,
    padding: PaddingValues,
    onSaveTransaction: (ExpenseTransaction?, String, String, String, NecessityLevel, String) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onDeleteIncomeTransaction: (String) -> Unit,
    onPreviewBankMessage: (String, String) -> ParsedBankMessage,
    onSaveImportedBankMessage: (ParsedBankMessageType, String, String, String?, String, NecessityLevel, String, String) -> Unit,
    onSavePendingBankImport: (PendingBankImport) -> Unit,
    onUpdatePendingBankImport: (PendingBankImport) -> Unit,
    onDismissPendingBankImport: (PendingBankImport) -> Unit,
    onMonthSelected: (String) -> Unit,
    onCategoryFilterSelected: (String?) -> Unit,
    onClearError: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<ExpenseTransaction?>(null) }
    val categories = uiState.data.categories
    val categoryById = categories.associateBy { it.id }
    val currency = uiState.data.profile.currency
    val pendingDisplayItems = PendingBankImportDisplayRules.group(uiState.data.pendingBankImports)
    val timelineItems = (
        uiState.filteredTransactions.map { TransactionTimelineItem.ExpenseItem(it) } +
            uiState.filteredIncomeTransactions.map { TransactionTimelineItem.IncomeItem(it) } +
            uiState.filteredInternalTransferRecords.map { TransactionTimelineItem.InternalTransferItem(it) }
        ).sortedByDescending { it.occurredAtMillis }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FinanceHeader(
                title = "Transactions",
                subtitle = "Manual tracking for expenses, savings transfers, and debt payments."
            )
        }
        item { ErrorBanner(message = uiState.errorMessage, onDismiss = onClearError) }
        item { StatusBanner(message = uiState.statusMessage, onDismiss = onClearError) }
        if (uiState.isLoading) {
            item { LoadingStateCard(message = "Loading transactions and pending imports...") }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MonthFilter(
                        selectedMonth = uiState.selectedTransactionMonth,
                        months = uiState.availableMonths,
                        onMonthSelected = onMonthSelected,
                        modifier = Modifier.weight(1f)
                    )
                    CategoryFilter(
                        selectedCategoryId = uiState.selectedTransactionCategoryId,
                        categories = categories,
                        onCategorySelected = onCategoryFilterSelected,
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = categories.isNotEmpty(),
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        editingTransaction = null
                        showDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp)
                    )
                    Text(text = "Add transaction")
                }
            }
        }
        item {
            ImportBankMessageCard(
                categories = categories,
                profileCurrency = currency,
                isSaving = uiState.isSaving,
                onPreviewBankMessage = onPreviewBankMessage,
                onSaveImportedBankMessage = onSaveImportedBankMessage
            )
        }
        if (pendingDisplayItems.isNotEmpty()) {
            item {
                Text(text = "Pending bank imports", style = MaterialTheme.typography.titleLarge)
            }
            items(pendingDisplayItems, key = { it.key }) { displayItem ->
                PendingBankImportCard(
                    displayItem = displayItem,
                    reminderThresholdMinutes = uiState.data.bankMessageSettings.internalTransferReminderThresholdMinutes,
                    categories = categories,
                    isSaving = uiState.isSaving,
                    onSave = onSavePendingBankImport,
                    onUpdate = onUpdatePendingBankImport,
                    onDismiss = onDismissPendingBankImport
                )
            }
        }

        if (timelineItems.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No transactions yet",
                    body = "Add a manual expense or preview a pasted bank message when you are ready."
                )
            }
        } else {
            items(timelineItems, key = { it.key }) { item ->
                when (item) {
                    is TransactionTimelineItem.ExpenseItem -> TransactionCard(
                        transaction = item.transaction,
                        categoryName = categoryById[item.transaction.categoryId]?.name ?: "Uncategorized",
                        currency = item.transaction.currency.ifBlank { currency },
                        onEdit = {
                            editingTransaction = item.transaction
                            showDialog = true
                        },
                        onDelete = { onDeleteTransaction(item.transaction.id) }
                    )
                    is TransactionTimelineItem.IncomeItem -> IncomeTransactionCard(
                        transaction = item.transaction,
                        fallbackCurrency = currency,
                        onDelete = { onDeleteIncomeTransaction(item.transaction.id) }
                    )
                    is TransactionTimelineItem.InternalTransferItem -> InternalTransferRecordCard(
                        record = item.record,
                        fallbackCurrency = currency
                    )
                }
            }
        }
    }

    if (showDialog) {
        TransactionDialog(
            existing = editingTransaction,
            categories = categories,
            currency = currency,
            onDismiss = { showDialog = false },
            onSave = { existing, amount, categoryId, note, necessity, dateInput ->
                onSaveTransaction(existing, amount, categoryId, note, necessity, dateInput)
                showDialog = false
            }
        )
    }
}

@Composable
private fun MonthFilter(
    selectedMonth: String,
    months: List<String>,
    onMonthSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            onClick = { expanded = true }
        ) {
            Text(text = selectedMonth, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            months.forEach { month ->
                DropdownMenuItem(
                    text = { Text(month) },
                    onClick = {
                        onMonthSelected(month)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryFilter(
    selectedCategoryId: String?,
    categories: List<BudgetCategory>,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "All categories"

    Box(modifier = modifier) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            onClick = { expanded = true }
        ) {
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All categories") },
                onClick = {
                    onCategorySelected(null)
                    expanded = false
                }
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TransactionCard(
    transaction: ExpenseTransaction,
    categoryName: String,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = categoryName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${transaction.necessity.label} - ${formatTransactionDate(transaction.occurredAtMillis)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = formatMoney(transaction.amount, currency),
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (transaction.note.isNotBlank()) {
                Text(
                    text = transaction.note,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Edit transaction")
                }
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete transaction")
                }
            }
        }
    }
}

@Composable
private fun IncomeTransactionCard(
    transaction: IncomeTransaction,
    fallbackCurrency: String,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.source.ifBlank { "Income" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Income - ${formatTransactionDate(transaction.occurredAtMillis)}",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = "+${formatMoney(transaction.amount, transaction.currency.ifBlank { fallbackCurrency })}",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (transaction.note.isNotBlank()) {
                Text(
                    text = transaction.note,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete income")
                }
            }
        }
    }
}

@Composable
private fun InternalTransferRecordCard(
    record: InternalTransferRecord,
    fallbackCurrency: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Internal transfer out",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "${record.status.label} - ${formatTransactionDate(record.messageTimestampMillis)}",
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = formatMoney(record.amount, record.currency.ifBlank { fallbackCurrency }),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (record.sourceAccountSuffix.isNotBlank()) {
                Text(
                    text = "Source account: *${record.sourceAccountSuffix}",
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            record.targetAccountSuffix?.takeIf { it.isNotBlank() }?.let { suffix ->
                Text(
                    text = "Target account: *$suffix",
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Text(
                text = record.note.ifBlank { "Not counted as spending." },
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun PendingBankImportCard(
    displayItem: PendingBankImportDisplayItem,
    reminderThresholdMinutes: Int,
    categories: List<BudgetCategory>,
    isSaving: Boolean,
    onSave: (PendingBankImport) -> Unit,
    onUpdate: (PendingBankImport) -> Unit,
    onDismiss: (PendingBankImport) -> Unit
) {
    val pendingImport = displayItem.primaryImport
    var showMessage by remember(pendingImport.id) { mutableStateOf(false) }
    var isEditing by remember(pendingImport.id) { mutableStateOf(false) }
    val isUnmatchedInternalDebit = displayItem is PendingBankImportDisplayItem.InternalTransferWaiting
    val hasMatchedPair = displayItem is PendingBankImportDisplayItem.InternalTransferReadyPair
    val isCreditOnlySavingsTransfer = displayItem is PendingBankImportDisplayItem.CreditOnlySavingsTransfer
    val isPastReminderThreshold = isUnmatchedInternalDebit &&
        System.currentTimeMillis() - pendingImport.createdAtMillis.coerceAtLeast(pendingImport.receivedAtMillis) >=
        reminderThresholdMinutes.coerceAtLeast(1) * 60_000L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = displayItem.pendingCardTitle(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            displayItem.pendingCardSummary()?.let { summary ->
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            PendingImportLine("Type", pendingImport.type.label)
            if (hasMatchedPair) {
                Text(
                    text = "Matched pair available",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (isUnmatchedInternalDebit) {
                Text(
                    text = "Waiting for matching credit",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isPastReminderThreshold) {
                        "Matching credit has not arrived yet. Review this transfer."
                    } else {
                        "Wait for matching credit if this was a transfer to savings."
                    },
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (isCreditOnlySavingsTransfer) {
                Text(
                    text = "Debit side was not found.",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            PendingImportLine(
                "Amount",
                pendingImport.amount?.let { formatMoney(it, pendingImport.currency) } ?: "Missing"
            )
            if (pendingImport.merchantName.isNotBlank()) {
                PendingImportLine("Merchant", pendingImport.merchantName)
            }
            if (pendingImport.sourceAccountSuffix.isNotBlank()) {
                PendingImportLine("Source account", "*${pendingImport.sourceAccountSuffix}")
            }
            if (pendingImport.targetAccountSuffix.isNotBlank()) {
                PendingImportLine("Target account", "*${pendingImport.targetAccountSuffix}")
            }
            PendingImportLine("Category", pendingImport.suggestedCategoryName)
            PendingImportLine("Necessity", pendingImport.suggestedNecessity.label)
            PendingImportLine("Date", formatTransactionDate(pendingImport.occurredAtMillis))
            PendingImportLine("Confidence", pendingImport.confidence.label)
            if (pendingImport.availableBalance != null) {
                PendingImportLine(
                    "Available balance",
                    formatMoney(pendingImport.availableBalance, pendingImport.availableBalanceCurrency)
                )
            }
            if (pendingImport.originalForeignAmount != null && pendingImport.originalForeignCurrency.isNotBlank()) {
                PendingImportLine(
                    "Original amount",
                    formatMoney(pendingImport.originalForeignAmount, pendingImport.originalForeignCurrency)
                )
            }
            if (pendingImport.reviewNote.isNotBlank()) {
                Text(
                    text = pendingImport.reviewNote,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (pendingImport.ignoredReason.isNotBlank()) {
                Text(
                    text = pendingImport.ignoredReason,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (pendingImport.pairedTransferStatus.isNotBlank()) {
                PendingImportLine("Pairing", pendingImport.pairedTransferStatus)
            }
            if (pendingImport.rawMessage.isNotBlank()) {
                if (showMessage) {
                    Text(
                        text = pendingImport.rawMessage,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = { showMessage = !showMessage }) {
                    Text(if (showMessage) "Hide message" else "Show message")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (displayItem.isPrimarySaveVisible) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving,
                        onClick = { onSave(pendingImport) }
                    ) {
                        Text(PendingBankImportSaveRules.saveButtonLabel(isSaving))
                    }
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { isEditing = true }
                ) {
                    Text(if (isUnmatchedInternalDebit) "Review" else "Edit")
                }
                TextButton(onClick = { onDismiss(pendingImport) }) {
                    Text("Dismiss")
                }
            }
            if (displayItem.isManualSaveVisible) {
                TextButton(
                    enabled = !isSaving,
                    onClick = { onSave(pendingImport) }
                ) {
                    Text(if (isSaving) "Saving..." else "Save unmatched transfer")
                }
            }
        }
    }

    if (isEditing) {
        PendingBankImportEditDialog(
            pendingImport = pendingImport,
            categories = categories,
            onDismiss = { isEditing = false },
            onSave = { updated ->
                onUpdate(updated)
                isEditing = false
            }
        )
    }
}

private fun PendingBankImportDisplayItem.pendingCardTitle(): String =
    when (this) {
        is PendingBankImportDisplayItem.InternalTransferWaiting -> "Internal transfer pending"
        is PendingBankImportDisplayItem.InternalTransferReadyPair -> "Internal transfer ready"
        is PendingBankImportDisplayItem.CreditOnlySavingsTransfer -> "Savings transfer pending"
        is PendingBankImportDisplayItem.SinglePendingImport ->
            pendingImport.senderName.ifBlank { "Bank SMS" }
    }

private fun PendingBankImportDisplayItem.pendingCardSummary(): String? =
    when (this) {
        is PendingBankImportDisplayItem.InternalTransferWaiting -> {
            val amount = debitImport.amount?.let { formatMoney(it, debitImport.currency) } ?: "Amount missing"
            val source = debitImport.sourceAccountSuffix.ifBlank { "source account" }
            "$amount from $source - waiting for matching credit"
        }
        is PendingBankImportDisplayItem.InternalTransferReadyPair -> {
            val amount = debitImport.amount?.let { formatMoney(it, debitImport.currency) } ?: "Amount missing"
            val source = debitImport.sourceAccountSuffix.ifBlank { "source" }
            val target = creditImport.targetAccountSuffix.ifBlank { "target" }
            "$amount from $source to $target"
        }
        is PendingBankImportDisplayItem.CreditOnlySavingsTransfer -> {
            val amount = creditImport.amount?.let { formatMoney(it, creditImport.currency) } ?: "Amount missing"
            val target = creditImport.targetAccountSuffix.ifBlank { "savings account" }
            "$amount credited to $target"
        }
        is PendingBankImportDisplayItem.SinglePendingImport -> null
    }

@Composable
private fun PendingImportLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.42f),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.58f),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PendingBankImportEditDialog(
    pendingImport: PendingBankImport,
    categories: List<BudgetCategory>,
    onDismiss: () -> Unit,
    onSave: (PendingBankImport) -> Unit
) {
    var amount by remember(pendingImport.id) {
        mutableStateOf(pendingImport.amount?.let { formatInputMoney(it) }.orEmpty())
    }
    var categoryId by remember(pendingImport.id) { mutableStateOf(pendingImport.suggestedCategoryId) }
    var necessity by remember(pendingImport.id) { mutableStateOf(pendingImport.suggestedNecessity) }
    var description by remember(pendingImport.id) { mutableStateOf(pendingImport.description) }
    var dateInput by remember(pendingImport.id) {
        mutableStateOf(FinanceDates.dateInputFromMillis(pendingImport.occurredAtMillis))
    }
    var localError by remember(pendingImport.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit pending import") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = amount,
                    onValueChange = {
                        amount = it
                        localError = null
                    },
                    label = { Text("Amount") },
                    supportingText = {
                        if (pendingImport.reviewNote.isNotBlank()) {
                            Text(pendingImport.reviewNote)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (pendingImport.availableBalance != null) {
                    PreviewLine(
                        label = "Available balance",
                        value = formatMoney(
                            pendingImport.availableBalance,
                            pendingImport.availableBalanceCurrency
                        )
                    )
                }
                if (pendingImport.sourceAccountSuffix.isNotBlank()) {
                    PreviewLine(label = "Source account", value = "*${pendingImport.sourceAccountSuffix}")
                }
                if (pendingImport.targetAccountSuffix.isNotBlank()) {
                    PreviewLine(label = "Target account", value = "*${pendingImport.targetAccountSuffix}")
                }
                if (pendingImport.merchantName.isNotBlank()) {
                    PreviewLine(label = "Merchant", value = pendingImport.merchantName)
                }
                if (pendingImport.originalForeignAmount != null && pendingImport.originalForeignCurrency.isNotBlank()) {
                    PreviewLine(
                        label = "Original amount",
                        value = formatMoney(
                            pendingImport.originalForeignAmount,
                            pendingImport.originalForeignCurrency
                        )
                    )
                }
                if (pendingImport.type == ParsedBankMessageType.EXPENSE) {
                    CategoryPicker(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        onCategorySelected = { categoryId = it }
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = dateInput,
                    onValueChange = {
                        dateInput = it
                        localError = null
                    },
                    label = { Text("Date") },
                    supportingText = { Text("YYYY-MM-DD") },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NecessityLevel.entries.forEach { level ->
                        FilterChip(
                            selected = necessity == level,
                            onClick = { necessity = level },
                            label = { Text(level.label) }
                        )
                    }
                }
                localError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amount.trim().replace(",", "").toDoubleOrNull()
                    val occurredAt = FinanceDates.parseDateInput(dateInput)
                    val error = when {
                        parsedAmount == null || parsedAmount <= 0.0 -> "Enter a valid imported amount."
                        occurredAt == null -> "Enter the import date as YYYY-MM-DD."
                        pendingImport.type == ParsedBankMessageType.EXPENSE && categoryId.isNullOrBlank() ->
                            "Choose a category."
                        else -> null
                    }
                    if (error != null) {
                        localError = error
                        return@Button
                    }
                    val selectedCategory = categories.firstOrNull { it.id == categoryId }
                    onSave(
                        pendingImport.copy(
                            amount = parsedAmount,
                            occurredAtMillis = occurredAt ?: pendingImport.occurredAtMillis,
                            suggestedCategoryId = categoryId,
                            suggestedCategoryName = selectedCategory?.name ?: pendingImport.suggestedCategoryName,
                            suggestedNecessity = necessity,
                            description = description
                        )
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ImportBankMessageCard(
    categories: List<BudgetCategory>,
    profileCurrency: String,
    isSaving: Boolean,
    onPreviewBankMessage: (String, String) -> ParsedBankMessage,
    onSaveImportedBankMessage: (ParsedBankMessageType, String, String, String?, String, NecessityLevel, String, String) -> Unit
) {
    var senderName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ParsedBankMessage?>(null) }
    var type by remember { mutableStateOf(ParsedBankMessageType.UNKNOWN) }
    var amount by remember { mutableStateOf("") }
    var parsedCurrency by remember { mutableStateOf(profileCurrency) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var necessity by remember { mutableStateOf(NecessityLevel.OPTIONAL) }
    var dateInput by remember { mutableStateOf(FinanceDates.todayInput()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Import bank message", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = senderName,
                onValueChange = { senderName = it },
                label = { Text("Sender name") },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = message,
                onValueChange = { message = it },
                label = { Text("Bank message") },
                minLines = 4
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = message.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                onClick = {
                    val parsed = onPreviewBankMessage(senderName, message)
                    preview = parsed
                    senderName = parsed.senderName.ifBlank { senderName }
                    type = parsed.type
                    amount = parsed.amount?.let { formatInputMoney(it) }.orEmpty()
                    parsedCurrency = parsed.currency.ifBlank { profileCurrency }
                    categoryId = parsed.suggestedCategoryId ?: categories.firstOrNull()?.id
                    description = parsed.description
                    necessity = parsed.suggestedNecessity
                    dateInput = FinanceDates.dateInputFromMillis(parsed.occurredAtMillis)
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(18.dp)
                )
                Text("Preview import")
            }

            preview?.let { parsed ->
                ParsedMessagePreview(
                    parsed = parsed,
                    categories = categories,
                    selectedType = type,
                    onTypeSelected = { type = it },
                    amount = amount,
                    onAmountChange = { amount = it },
                    currency = parsedCurrency,
                    onCurrencyChange = { parsedCurrency = it },
                    categoryId = categoryId,
                    onCategorySelected = { categoryId = it },
                    description = description,
                    onDescriptionChange = { description = it },
                    necessity = necessity,
                    onNecessitySelected = { necessity = it },
                    dateInput = dateInput,
                    onDateChange = { dateInput = it }
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    shape = MaterialTheme.shapes.medium,
                    onClick = {
                        onSaveImportedBankMessage(
                            type,
                            amount,
                            parsedCurrency,
                            categoryId,
                            description,
                            necessity,
                            dateInput,
                            senderName
                        )
                    }
                ) {
                    Text(if (isSaving) "Saving..." else "Save import")
                }
            }
        }
    }
}

@Composable
private fun ParsedMessagePreview(
    parsed: ParsedBankMessage,
    categories: List<BudgetCategory>,
    selectedType: ParsedBankMessageType,
    onTypeSelected: (ParsedBankMessageType) -> Unit,
    amount: String,
    onAmountChange: (String) -> Unit,
    currency: String,
    onCurrencyChange: (String) -> Unit,
    categoryId: String?,
    onCategorySelected: (String?) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    necessity: NecessityLevel,
    onNecessitySelected: (NecessityLevel) -> Unit,
    dateInput: String,
    onDateChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PreviewLine(label = "Sender", value = parsed.senderName.ifBlank { "Unknown" })
        PreviewLine(label = "Source", value = parsed.sourceType.label)
        PreviewLine(label = "Confidence", value = parsed.confidence.label)
        if (parsed.merchantName.isNotBlank()) {
            PreviewLine(label = "Merchant", value = parsed.merchantName)
        }
        if (parsed.sourceAccountSuffix.isNotBlank()) {
            PreviewLine(label = "Source account", value = "*${parsed.sourceAccountSuffix}")
        }
        if (parsed.targetAccountSuffix.isNotBlank()) {
            PreviewLine(label = "Target account", value = "*${parsed.targetAccountSuffix}")
        }
        parsed.availableBalance?.let {
            PreviewLine(label = "Available balance", value = formatMoney(it, parsed.availableBalanceCurrency))
        }
        if (parsed.originalForeignAmount != null && parsed.originalForeignCurrency.isNotBlank()) {
            PreviewLine(
                label = "Original amount",
                value = formatMoney(parsed.originalForeignAmount, parsed.originalForeignCurrency)
            )
        }
        if (parsed.reviewNote.isNotBlank()) {
            PreviewLine(label = "Review note", value = parsed.reviewNote)
        }
        if (parsed.ignoredReason.isNotBlank()) {
            PreviewLine(label = "Reason", value = parsed.ignoredReason)
        }
        MessageTypePicker(selectedType = selectedType, onTypeSelected = onTypeSelected)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = amount,
                onValueChange = onAmountChange,
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = currency,
                onValueChange = onCurrencyChange,
                label = { Text("Currency") },
                singleLine = true
            )
        }
        if (selectedType == ParsedBankMessageType.EXPENSE) {
            CategoryPicker(
                categories = categories,
                selectedCategoryId = categoryId,
                onCategorySelected = onCategorySelected
            )
        } else {
            PreviewLine(label = "Suggested category", value = parsed.suggestedCategoryName)
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = dateInput,
            onValueChange = onDateChange,
            label = { Text("Date") },
            supportingText = { Text("YYYY-MM-DD") },
            singleLine = true
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description") },
            minLines = 2
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NecessityLevel.entries.forEach { level ->
                FilterChip(
                    selected = necessity == level,
                    onClick = { onNecessitySelected(level) },
                    label = { Text(level.label) }
                )
            }
        }
    }
}

@Composable
private fun PreviewLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            modifier = Modifier.padding(start = 12.dp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MessageTypePicker(
    selectedType: ParsedBankMessageType,
    onTypeSelected: (ParsedBankMessageType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            onClick = { expanded = true }
        ) {
            Text(
                text = "Type: ${selectedType.label}",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ParsedBankMessageType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    onClick = {
                        onTypeSelected(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryPicker(
    categories: List<BudgetCategory>,
    selectedCategoryId: String?,
    onCategorySelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val categoryName = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Choose category"

    Box {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            onClick = { expanded = true }
        ) {
            Text(
                text = "Category: $categoryName",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text("${category.name} (${category.type.label})") },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TransactionDialog(
    existing: ExpenseTransaction?,
    categories: List<BudgetCategory>,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (ExpenseTransaction?, String, String, String, NecessityLevel, String) -> Unit
) {
    var amount by remember(existing?.id) {
        mutableStateOf(existing?.let { formatInputMoney(it.amount) } ?: "")
    }
    var categoryId by remember(existing?.id, categories) {
        mutableStateOf(existing?.categoryId ?: categories.firstOrNull()?.id.orEmpty())
    }
    var note by remember(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var dateInput by remember(existing?.id) {
        mutableStateOf(existing?.let { FinanceDates.dateInputFromMillis(it.occurredAtMillis) } ?: FinanceDates.todayInput())
    }
    var necessity by remember(existing?.id) {
        mutableStateOf(existing?.necessity ?: NecessityLevel.NECESSARY)
    }
    var localError by remember(existing?.id) { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val categoryName = categories.firstOrNull { it.id == categoryId }?.name ?: "Choose category"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add transaction" else "Edit transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = amount,
                    onValueChange = {
                        amount = it
                        localError = null
                    },
                    label = { Text("Amount ($currency)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Box {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        onClick = { expanded = true }
                    ) {
                        Text(
                            text = categoryName,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text("${category.name} (${category.type.label})") },
                                onClick = {
                                    categoryId = category.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = dateInput,
                    onValueChange = {
                        dateInput = it
                        localError = null
                    },
                    label = { Text("Date") },
                    supportingText = { Text("YYYY-MM-DD") },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    minLines = 2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NecessityLevel.entries.forEach { level ->
                        FilterChip(
                            selected = necessity == level,
                            onClick = { necessity = level },
                            label = { Text(level.label) }
                        )
                    }
                }
                localError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = categories.isNotEmpty(),
                onClick = {
                    val error = when {
                        amount.trim().replace(",", "").toDoubleOrNull()?.let { it > 0.0 } != true ->
                            "Enter a valid transaction amount."
                        categoryId.isBlank() -> "Choose a category."
                        FinanceDates.parseDateInput(dateInput) == null -> "Enter the date as YYYY-MM-DD."
                        else -> null
                    }
                    if (error != null) {
                        localError = error
                    } else {
                        onSave(existing, amount, categoryId, note, necessity, dateInput)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
