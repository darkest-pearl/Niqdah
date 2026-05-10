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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musab.niqdah.domain.finance.CategoryBudgetWarning
import com.musab.niqdah.domain.finance.DisciplineStatus
import com.musab.niqdah.domain.finance.NecessaryItemDue

@Composable
fun DashboardScreen(
    uiState: FinanceUiState,
    padding: PaddingValues,
    onClearError: () -> Unit
) {
    val currency = uiState.data.profile.currency
    val dashboard = uiState.dashboard

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FinanceHeader(
                title = "Dashboard",
                subtitle = "Current month plan, spending pressure, savings, and debt."
            )
        }
        item { ErrorBanner(message = uiState.errorMessage, onDismiss = onClearError) }
        item { StatusBanner(message = uiState.statusMessage, onDismiss = onClearError) }
        if (uiState.isLoading) {
            item {
                LoadingStateCard(message = "Loading your finance dashboard...")
            }
        } else {
            item {
                FinanceMetricCard(
                    title = "Daily-use account balance",
                    value = uiState.data.latestDailyUseBalance?.let {
                        formatMoney(it.availableBalance, it.currency)
                    } ?: "Not known yet",
                    subtitle = uiState.data.latestDailyUseBalance?.let {
                        "Last update ${formatTransactionDateTime(it.messageTimestampMillis)} from ${it.sender.ifBlank { "bank SMS" }}."
                    } ?: "Niqdah will show this after a reviewed bank SMS includes an available balance."
                )
            }
            item {
                FinanceMetricCard(
                    title = "Savings account balance",
                    value = uiState.data.latestSavingsBalance?.let {
                        formatMoney(it.availableBalance, it.currency)
                    } ?: "Not known yet",
                    subtitle = uiState.data.latestSavingsBalance?.let {
                        "Last update ${formatTransactionDateTime(it.messageTimestampMillis)} from ${it.sender.ifBlank { "bank SMS" }}."
                    } ?: "Savings balance will appear after a savings bank message includes an available balance."
                )
            }
            item {
                FinanceMetricCard(
                    title = "Last balance update",
                    value = uiState.data.lastBalanceUpdateMillis.takeIf { it > 0L }?.let {
                        formatTransactionDateTime(it)
                    } ?: "No balance updates",
                    subtitle = "Balance snapshots are parsed from bank SMS summaries only."
                )
            }
            item {
                FinanceMetricCard(
                    title = "Total monthly income",
                    value = formatMoney(dashboard.totalMonthlyIncome, currency),
                    subtitle = "Profile income plus imported credits."
                )
            }
            item {
                FinanceMetricCard(
                    title = "Total spent",
                    value = formatMoney(dashboard.totalSpent, currency),
                    subtitle = "Expense transactions this month, excluding savings transfers."
                )
            }
            item {
                FinanceMetricCard(
                    title = "Remaining safe-to-spend",
                    value = formatMoney(dashboard.remainingSafeToSpend, currency),
                    subtitle = "After spending, fixed reserves, savings target, and debt reduction."
                )
            }
            item {
                FinanceProgressCard(
                    title = "Marriage fund progress",
                    value = formatProgress(dashboard.marriageFundProgress),
                    progress = dashboard.marriageFundProgress,
                    subtitle = "Progress toward the January marriage target."
                )
            }
            item {
                FinanceProgressCard(
                    title = "Savings target progress",
                    value = formatProgress(dashboard.savingsTargetProgress),
                    progress = dashboard.savingsTargetProgress,
                    subtitle = "This month against the ${formatMoney(dashboard.disciplineStatus.savingsTarget.targetAmount, currency)} savings target."
                )
            }
            item {
                DisciplineCard(
                    disciplineStatus = dashboard.disciplineStatus,
                    currency = currency
                )
            }
            item {
                JanuaryCountdownCard(
                    disciplineStatus = dashboard.disciplineStatus,
                    currency = currency
                )
            }
            item {
                FinanceProgressCard(
                    title = "Debt progress",
                    value = formatProgress(dashboard.debtProgress),
                    progress = dashboard.debtProgress,
                    subtitle = "${formatMoney(uiState.data.debt.remainingAmount, currency)} remaining from ${formatMoney(uiState.data.debt.startingAmount, currency)}."
                )
            }
            item {
                HealthSummaryCard(summary = dashboard.healthSummary)
            }
            item {
                Text(
                    text = "Category alerts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (dashboard.overspendingAlerts.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No overspending",
                        body = "Every category is within its monthly budget."
                    )
                }
            } else {
                items(dashboard.overspendingAlerts, key = { it.category.id }) { alert ->
                    OverspendingCard(
                        title = alert.category.name,
                        amount = formatMoney(-alert.remaining, currency)
                    )
                }
            }
        }
    }
}

@Composable
private fun DisciplineCard(
    disciplineStatus: DisciplineStatus,
    currency: String
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
            Text(text = "Discipline", style = MaterialTheme.typography.titleMedium)
            DisciplineLine(
                label = "Savings target",
                value = "${formatMoney(disciplineStatus.savingsTarget.savedThisMonth, currency)} of ${
                    formatMoney(disciplineStatus.savingsTarget.targetAmount, currency)
                }"
            )
            DisciplineLine(
                label = "Shortfall",
                value = formatMoney(disciplineStatus.savingsTarget.shortfall, currency)
            )
            DisciplineLine(
                label = "Safe to spend",
                value = formatMoney(disciplineStatus.safeToSpendAmount, currency)
            )
            DisciplineLine(
                label = "Avoid this month",
                value = formatMoney(disciplineStatus.avoidSpendingThisMonth, currency)
            )
            Text(
                text = warningSummary(disciplineStatus.categoryWarnings),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = dueSummary(disciplineStatus.necessaryItemsDueSoon),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun JanuaryCountdownCard(
    disciplineStatus: DisciplineStatus,
    currency: String
) {
    val countdown = disciplineStatus.januaryCountdown
    FinanceMetricCard(
        title = "January countdown",
        value = "${countdown.monthsRemaining} months / ${countdown.daysRemaining} days",
        subtitle = "Saved ${formatMoney(countdown.currentSaved, currency)} of ${
            formatMoney(countdown.targetAmount, currency)
        }. Required monthly savings: ${formatMoney(countdown.requiredMonthlySavings, currency)}."
    )
}

@Composable
private fun DisciplineLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.55f),
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun warningSummary(warnings: List<CategoryBudgetWarning>): String =
    if (warnings.isEmpty()) {
        "No categories are near their warning line."
    } else {
        warnings.take(3).joinToString(separator = "\n") { it.message }
    }

private fun dueSummary(items: List<NecessaryItemDue>): String =
    if (items.isEmpty()) {
        "No necessary items are due soon."
    } else {
        items.take(3).joinToString(separator = "\n") { due ->
            val timing = when (due.daysUntilDue) {
                0L -> "today"
                1L -> "tomorrow"
                else -> "in ${due.daysUntilDue} days"
            }
            "${due.item.title} is due $timing."
        }
    }

@Composable
private fun HealthSummaryCard(summary: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = summary,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun OverspendingCard(title: String, amount: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = "$title is over budget by $amount.",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
