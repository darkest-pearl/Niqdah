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
import com.musab.niqdah.domain.finance.AccountBalanceConfidence
import com.musab.niqdah.domain.finance.AccountBalanceStatus
import com.musab.niqdah.domain.finance.CategoryBudgetWarning
import com.musab.niqdah.domain.finance.DepositType
import com.musab.niqdah.domain.finance.DisciplineStatus
import com.musab.niqdah.domain.finance.FinanceDates
import com.musab.niqdah.domain.finance.NecessaryItemDue
import java.time.LocalDate

@Composable
fun DashboardScreen(
    uiState: FinanceUiState,
    padding: PaddingValues,
    onClearError: () -> Unit
) {
    val currency = uiState.data.profile.currency
    val dashboard = uiState.dashboard
    val primaryGoal = uiState.data.primaryGoal

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FinanceHeader(
                title = "Your money control room",
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
                PlanSummaryCard(uiState = uiState)
            }
            item {
                FinanceProgressCard(
                    title = primaryGoal?.name ?: "Savings goal",
                    value = formatProgress(dashboard.primaryGoalProgress),
                    progress = dashboard.primaryGoalProgress,
                    subtitle = primaryGoal?.let {
                        "${formatMoney(it.savedAmount, currency)} saved of ${formatMoney(it.targetAmount, currency)}."
                    } ?: "Create a primary goal in onboarding or settings to track progress here."
                )
            }
            item {
                FinanceMetricCard(
                    title = "Safe to spend",
                    value = formatMoney(dashboard.remainingSafeToSpend, currency),
                    subtitle = "After spending, fixed reserves, savings target, and debt reduction."
                )
            }
            if (uiState.shouldShowSalaryReminder()) {
                item {
                    InsightCard(
                        title = "Salary not recorded yet",
                        body = "Salary has not been recorded yet. Record it manually or wait for bank SMS."
                    )
                }
            }
            item {
                SectionHeader(
                    title = "Account balances",
                    subtitle = "Confirmed when SMS or manual balance includes the actual account balance."
                )
            }
            item {
                AccountBalancesCard(uiState = uiState)
            }
            item {
                DisciplineCard(
                    disciplineStatus = dashboard.disciplineStatus,
                    currency = currency
                )
            }
            item {
                GoalCountdownCard(
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
                FinanceProgressCard(
                    title = "Savings target progress",
                    value = formatProgress(dashboard.savingsTargetProgress),
                    progress = dashboard.savingsTargetProgress,
                    subtitle = "This month against the ${formatMoney(dashboard.disciplineStatus.savingsTarget.targetAmount, currency)} savings target."
                )
            }
            item {
                HealthSummaryCard(summary = dashboard.healthSummary)
            }
            item {
                RecentActivityCard(uiState = uiState)
            }
            item {
                NecessaryRemindersCard(disciplineStatus = dashboard.disciplineStatus)
            }
            item {
                SectionHeader(title = "Category alerts")
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
private fun PlanSummaryCard(uiState: FinanceUiState) {
    val currency = uiState.data.profile.currency
    val dashboard = uiState.dashboard
    PremiumCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text = "This month",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = dashboard.healthSummary,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyLarge
        )
        DisciplineLine(
            label = "Income",
            value = formatMoney(dashboard.totalMonthlyIncome, currency)
        )
        DisciplineLine(
            label = "Spent",
            value = formatMoney(dashboard.totalSpent, currency)
        )
    }
}

@Composable
private fun AccountBalancesCard(uiState: FinanceUiState) {
    PremiumCard {
        BalanceStatusRow(label = "Daily-use", status = uiState.data.latestDailyUseBalanceStatus)
        BalanceStatusRow(label = "Savings", status = uiState.data.latestSavingsBalanceStatus)
    }
}

private fun FinanceUiState.shouldShowSalaryReminder(): Boolean {
    if (data.profile.salary <= 0.0 && data.profile.salaryMinor <= 0L) return false
    val salaryDay = data.profile.salaryDayOfMonth.coerceIn(1, 31)
    val today = LocalDate.now()
    if (today.dayOfMonth < salaryDay) return false
    val currentMonth = FinanceDates.currentYearMonth()
    return data.incomeTransactions.none {
        it.yearMonth == currentMonth && it.depositType == DepositType.SALARY
    }
}

@Composable
private fun BalanceStatusRow(label: String, status: AccountBalanceStatus?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            status?.let {
                StatusPill(
                    text = it.confidence.label,
                    isWarning = it.confidence != AccountBalanceConfidence.CONFIRMED
                )
            }
        }
        Text(
            text = status?.let { formatMoneyMinor(it.amountMinor, it.currency) } ?: "Not known yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = status?.let {
                "Last update ${formatTransactionDateTime(it.lastUpdatedMillis)} from ${it.source.label}. ${it.note}"
            } ?: "Niqdah shows this after a bank SMS or manual balance confirmation.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        if (status?.confidence == AccountBalanceConfidence.ESTIMATED) {
            Text(
                text = "Estimated balance. Confirm with next bank SMS or manual update.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
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
private fun GoalCountdownCard(
    disciplineStatus: DisciplineStatus,
    currency: String
) {
    val countdown = disciplineStatus.januaryCountdown
    FinanceMetricCard(
        title = "${countdown.goalName} countdown",
        value = "${countdown.monthsRemaining} months / ${countdown.daysRemaining} days",
        subtitle = "Saved ${formatMoney(countdown.currentSaved, currency)} of ${
            formatMoney(countdown.targetAmount, currency)
        }. Required monthly savings: ${formatMoney(countdown.requiredMonthlySavings, currency)}."
    )
}

@Composable
private fun RecentActivityCard(uiState: FinanceUiState) {
    val currency = uiState.data.profile.currency
    val recent = (
        uiState.data.transactions.map { "${formatMoney(it.amount, it.currency.ifBlank { currency })} - ${it.note.ifBlank { "Expense" }}" to it.occurredAtMillis } +
            uiState.data.incomeTransactions.map { "${formatMoney(it.amount, it.currency.ifBlank { currency })} - ${it.source.ifBlank { "Income" }}" to it.occurredAtMillis }
        )
        .sortedByDescending { it.second }
        .take(3)
    if (recent.isEmpty()) {
        EmptyStateCard(
            title = "No recent activity",
            body = "Manual expenses, reviewed SMS imports, and income records will appear here."
        )
        return
    }
    PremiumCard {
        SectionHeader(title = "Recent activity")
        recent.forEach { item ->
            Text(text = item.first, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NecessaryRemindersCard(disciplineStatus: DisciplineStatus) {
    if (disciplineStatus.necessaryItemsDueSoon.isEmpty()) {
        EmptyStateCard(
            title = "No necessary reminders due soon",
            body = "Fixed costs and important reminders will show here when they are close."
        )
        return
    }
    PremiumCard {
        SectionHeader(title = "Necessary reminders")
        Text(
            text = dueSummary(disciplineStatus.necessaryItemsDueSoon),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
