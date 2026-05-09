package com.musab.niqdah.ui.shell

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.musab.niqdah.ui.ai.AiChatScreen
import com.musab.niqdah.ui.ai.AiChatUiState
import com.musab.niqdah.ui.ai.AiChatViewModel
import com.musab.niqdah.ui.finance.DashboardScreen
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
    aiChatUiState: AiChatUiState,
    aiChatViewModel: AiChatViewModel,
    openTransactionsRequest: Int,
    onLogout: () -> Unit
) {
    var selectedDestination by rememberSaveable { mutableStateOf(ShellDestination.Dashboard) }

    LaunchedEffect(openTransactionsRequest) {
        if (openTransactionsRequest > 0) {
            selectedDestination = ShellDestination.Transactions
        }
    }

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
                onDeleteIncomeTransaction = financeViewModel::deleteIncomeTransaction,
                onPreviewBankMessage = financeViewModel::previewBankMessage,
                onSaveImportedBankMessage = financeViewModel::saveImportedBankMessage,
                onSavePendingBankImport = financeViewModel::savePendingBankImport,
                onUpdatePendingBankImport = financeViewModel::updatePendingBankImport,
                onDismissPendingBankImport = financeViewModel::dismissPendingBankImport,
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
            ShellDestination.AiChat -> AiChatScreen(
                chatUiState = aiChatUiState,
                financeUiState = financeUiState,
                padding = padding,
                onSendMessage = { text, state ->
                    aiChatViewModel.sendMessage(
                        text = text,
                        financeUiState = state,
                        draftActionFactory = financeViewModel::previewAiFinanceDraftAction
                    )
                },
                onSaveDraft = { messageId, draftAction ->
                    financeViewModel.saveAiFinanceDraftAction(
                        draft = draftAction,
                        onSuccess = { aiChatViewModel.markDraftActionSaved(messageId) },
                        onFailure = { message -> aiChatViewModel.setDraftActionError(messageId, message) }
                    )
                },
                onUpdateDraft = aiChatViewModel::updateDraftAction,
                onCancelDraft = aiChatViewModel::cancelDraftAction,
                onClearError = aiChatViewModel::clearError
            )
            ShellDestination.Settings -> SettingsScreen(
                uiState = financeUiState,
                userEmail = userEmail,
                padding = padding,
                onUpdateProfileAndDebt = financeViewModel::updateProfileAndDebt,
                onUpdateCategoryBudgets = financeViewModel::updateCategoryBudgets,
                onUpdateBankMessageSettings = { automatic, dailySender, dailyEnabled, savingsSender,
                    savingsEnabled, debitKeywords, creditKeywords, savingsKeywords,
                    dailySuffix, savingsSuffix, learnFromEdits ->
                    financeViewModel.updateBankMessageSettings(
                        isAutomaticSmsImportEnabled = automatic,
                        dailySenderName = dailySender,
                        isDailyEnabled = dailyEnabled,
                        savingsSenderName = savingsSender,
                        isSavingsEnabled = savingsEnabled,
                        debitKeywordsInput = debitKeywords,
                        creditKeywordsInput = creditKeywords,
                        savingsTransferKeywordsInput = savingsKeywords,
                        dailyUseAccountSuffix = dailySuffix,
                        savingsAccountSuffix = savingsSuffix,
                        isMerchantLearningEnabled = learnFromEdits
                    )
                },
                onLogout = onLogout,
                onClearError = financeViewModel::clearError
            )
        }
    }
}
