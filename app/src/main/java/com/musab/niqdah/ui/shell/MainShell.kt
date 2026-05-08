package com.musab.niqdah.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musab.niqdah.ui.finance.DashboardScreen
import com.musab.niqdah.ui.finance.FinanceHeader
import com.musab.niqdah.ui.finance.FinanceMetricCard
import com.musab.niqdah.ui.finance.FinanceUiState
import com.musab.niqdah.ui.finance.FinanceViewModel
import com.musab.niqdah.ui.finance.GoalsScreen
import com.musab.niqdah.ui.finance.SettingsScreen
import com.musab.niqdah.ui.finance.TransactionsScreen

private enum class ShellDestination(
    val label: String,
    val icon: ImageVector
) {
    Dashboard("Dashboard", Icons.Rounded.Dashboard),
    Transactions("Transactions", Icons.AutoMirrored.Rounded.ReceiptLong),
    Goals("Goals", Icons.Rounded.Flag),
    AiChat("AI Chat", Icons.Rounded.AutoAwesome),
    Settings("Settings", Icons.Rounded.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    userEmail: String?,
    financeUiState: FinanceUiState,
    financeViewModel: FinanceViewModel,
    onLogout: () -> Unit
) {
    var selectedDestination by rememberSaveable { mutableStateOf(ShellDestination.Dashboard) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(text = "Niqdah", fontWeight = FontWeight.SemiBold)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                ShellDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = selectedDestination == destination,
                        onClick = { selectedDestination = destination },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = {
                            Text(
                                text = destination.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        when (selectedDestination) {
            ShellDestination.Dashboard -> DashboardScreen(
                uiState = financeUiState,
                padding = padding,
                onClearError = financeViewModel::clearError
            )
            ShellDestination.Transactions -> TransactionsScreen(
                uiState = financeUiState,
                padding = padding,
                onSaveTransaction = financeViewModel::saveTransaction,
                onDeleteTransaction = financeViewModel::deleteTransaction,
                onMonthSelected = financeViewModel::setTransactionMonth,
                onCategoryFilterSelected = financeViewModel::setTransactionCategoryFilter,
                onClearError = financeViewModel::clearError
            )
            ShellDestination.Goals -> GoalsScreen(
                uiState = financeUiState,
                padding = padding,
                onUpdateGoalSaved = financeViewModel::updateGoalSavedAmount,
                onRecordDebtPayment = financeViewModel::recordDebtPayment,
                onClearError = financeViewModel::clearError
            )
            ShellDestination.AiChat -> AiChatPlaceholderScreen(padding = padding)
            ShellDestination.Settings -> SettingsScreen(
                uiState = financeUiState,
                userEmail = userEmail,
                padding = padding,
                onUpdateProfileAndDebt = financeViewModel::updateProfileAndDebt,
                onUpdateCategoryBudgets = financeViewModel::updateCategoryBudgets,
                onLogout = onLogout,
                onClearError = financeViewModel::clearError
            )
        }
    }
}

@Composable
private fun AiChatPlaceholderScreen(padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FinanceHeader(
                title = "AI Chat",
                subtitle = "Phase 3 will connect this to a backend assistant."
            )
        }
        item {
            FinanceMetricCard(
                title = "Future assistant",
                value = "Phase 3",
                subtitle = "Chat with Niqdah to set budgets, classify expenses, and ask whether a purchase fits your plan."
            )
        }
    }
}
