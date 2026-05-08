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
import com.musab.niqdah.domain.finance.NecessityLevel

@Composable
fun TransactionsScreen(
    uiState: FinanceUiState,
    padding: PaddingValues,
    onSaveTransaction: (ExpenseTransaction?, String, String, String, NecessityLevel, String) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onMonthSelected: (String) -> Unit,
    onCategoryFilterSelected: (String?) -> Unit,
    onClearError: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<ExpenseTransaction?>(null) }
    val categories = uiState.data.categories
    val categoryById = categories.associateBy { it.id }
    val currency = uiState.data.profile.currency

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

        if (uiState.filteredTransactions.isEmpty()) {
            item {
                FinanceMetricCard(
                    title = "No transactions",
                    value = "Ready",
                    subtitle = "Add a manual transaction to start tracking this month."
                )
            }
        } else {
            items(uiState.filteredTransactions, key = { it.id }) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    categoryName = categoryById[transaction.categoryId]?.name ?: "Uncategorized",
                    currency = currency,
                    onEdit = {
                        editingTransaction = transaction
                        showDialog = true
                    },
                    onDelete = { onDeleteTransaction(transaction.id) }
                )
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
                        text = "${transaction.necessity.label} • ${formatTransactionDate(transaction.occurredAtMillis)}",
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
                    onValueChange = { amount = it },
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
                    onValueChange = { dateInput = it },
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
            }
        },
        confirmButton = {
            Button(
                enabled = categories.isNotEmpty(),
                onClick = { onSave(existing, amount, categoryId, note, necessity, dateInput) }
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
