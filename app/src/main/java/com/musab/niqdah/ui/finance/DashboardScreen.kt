package com.musab.niqdah.ui.finance

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
        if (uiState.isLoading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
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
                    subtitle = "Manual transactions recorded this month."
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
                    subtitle = "This month against the ${formatMoney(uiState.data.profile.monthlySavingsTarget, currency)} savings target."
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
                    FinanceMetricCard(
                        title = "No overspending",
                        value = "On plan",
                        subtitle = "Every category is within its monthly budget."
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "$title is over budget by $amount.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
