package com.musab.niqdah.ui.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.musab.niqdah.domain.finance.SavingsGoal

@Composable
fun GoalsScreen(
    uiState: FinanceUiState,
    padding: PaddingValues,
    onUpdateGoalSaved: (SavingsGoal, String) -> Unit,
    onRecordDebtPayment: (String) -> Unit,
    onClearError: () -> Unit
) {
    val currency = uiState.data.profile.currency
    val goals = uiState.data.goals.sortedByDescending { it.isPrimary || it.id == uiState.data.profile.primaryGoalId }
    var editingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var showDebtPaymentDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FinanceHeader(
                title = "Goals",
                subtitle = "Primary goal, envelopes, and debt progress."
            )
        }
        item { ErrorBanner(message = uiState.errorMessage, onDismiss = onClearError) }
        item { StatusBanner(message = uiState.statusMessage, onDismiss = onClearError) }
        if (uiState.isLoading) {
            item { LoadingStateCard(message = "Loading goals and debt tracker...") }
        }
        if (!uiState.isLoading && uiState.data.goals.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No savings goals yet",
                    body = "Default envelopes will appear after your finance data finishes setup."
                )
            }
        }
        item { SectionHeader(title = "Primary goal") }
        items(goals.take(1), key = { it.id }) { goal ->
            GoalHeroCard(
                goal = goal,
                currency = currency,
                onUpdate = { editingGoal = goal }
            )
        }
        item {
            SectionHeader(
                title = "Savings account",
                subtitle = "Actual or estimated account money is separate from goal contributions."
            )
        }
        item {
            AccountBalanceGoalContext(uiState = uiState)
        }
        if (goals.size > 1) {
            item { SectionHeader(title = "Other goals") }
            items(goals.drop(1), key = { it.id }) { goal ->
                GoalCard(
                    goal = goal,
                    currency = currency,
                    onUpdate = { editingGoal = goal }
                )
            }
        }
        item { SectionHeader(title = "Debt progress") }
        item {
            DebtCard(
                uiState = uiState,
                onRecordPayment = { showDebtPaymentDialog = true }
            )
        }
    }

    editingGoal?.let { goal ->
        GoalUpdateDialog(
            goal = goal,
            currency = currency,
            onDismiss = { editingGoal = null },
            onSave = { amount ->
                onUpdateGoalSaved(goal, amount)
                editingGoal = null
            }
        )
    }

    if (showDebtPaymentDialog) {
        DebtPaymentDialog(
            currency = currency,
            onDismiss = { showDebtPaymentDialog = false },
            onSave = { amount ->
                onRecordDebtPayment(amount)
                showDebtPaymentDialog = false
            }
        )
    }
}

@Composable
private fun GoalHeroCard(goal: SavingsGoal, currency: String, onUpdate: () -> Unit) {
    val progress = if (goal.targetAmount <= 0.0) 0.0 else goal.savedAmount / goal.targetAmount
    GoalProgressCard(
        title = goal.name,
        saved = formatMoney(goal.savedAmount, currency),
        target = formatMoney(goal.targetAmount, currency),
        progress = progress,
        subtitle = "This progress is the contribution toward the target, not the bank account balance."
    )
    PrimaryActionButton(
        modifier = Modifier.fillMaxWidth(),
        label = "Update saved amount",
        onClick = onUpdate
    )
}

@Composable
private fun AccountBalanceGoalContext(uiState: FinanceUiState) {
    val status = uiState.data.latestSavingsBalanceStatus
    BalanceCard(
        title = "Savings account balance",
        amount = status?.let { formatMoneyMinor(it.amountMinor, it.currency) } ?: "Not known yet",
        confidence = status?.confidence?.label ?: "Awaiting balance",
        note = status?.let {
            "Balance confidence comes from ${it.source.label}. Goal progress is updated separately."
        } ?: "Add a bank SMS or manual balance confirmation to show actual savings account money."
    )
}

@Composable
private fun GoalCard(goal: SavingsGoal, currency: String, onUpdate: () -> Unit) {
    val progress = if (goal.targetAmount <= 0.0) 0.0 else goal.savedAmount / goal.targetAmount

    PremiumCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = goal.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = formatProgress(progress),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            LinearProgressIndicator(
                progress = { progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "${formatMoney(goal.savedAmount, currency)} saved of ${formatMoney(goal.targetAmount, currency)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SecondaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                label = "Update saved amount",
                onClick = onUpdate
            )
    }
}

@Composable
private fun DebtCard(uiState: FinanceUiState, onRecordPayment: () -> Unit) {
    val currency = uiState.data.profile.currency
    val debt = uiState.data.debt
    val progress = uiState.dashboard.debtProgress

    PremiumCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Payments,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Debt tracker",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            LinearProgressIndicator(
                progress = { progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Starting debt: ${formatMoney(debt.startingAmount, currency)}",
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Remaining debt: ${formatMoney(debt.remainingAmount, currency)}",
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Monthly auto reduction: ${formatMoney(debt.monthlyAutoReduction, currency)}",
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            PrimaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                label = "Record debt payment",
                onClick = onRecordPayment
            )
    }
}

@Composable
private fun GoalUpdateDialog(
    goal: SavingsGoal,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var amount by remember(goal.id) { mutableStateOf(formatInputMoney(goal.savedAmount)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update ${goal.name}") },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Saved amount ($currency)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            Button(onClick = { onSave(amount) }) {
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

@Composable
private fun DebtPaymentDialog(
    currency: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record debt payment") },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Payment amount ($currency)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            Button(onClick = { onSave(amount) }) {
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
