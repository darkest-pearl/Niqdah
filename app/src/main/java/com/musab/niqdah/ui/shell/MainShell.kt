package com.musab.niqdah.ui.shell

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musab.niqdah.ui.ai.AiChatScreen
import com.musab.niqdah.ui.ai.AiChatUiState
import com.musab.niqdah.ui.ai.AiChatViewModel
import com.musab.niqdah.ui.finance.DashboardScreen
import com.musab.niqdah.ui.finance.FinanceUiState
import com.musab.niqdah.ui.finance.FinanceViewModel
import com.musab.niqdah.ui.finance.GoalsScreen
import com.musab.niqdah.ui.finance.PremiumTopBar
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

private val NavCoral = Color(0xFFFF5A5F)
private val NavMuted = Color(0xFF657084)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(
    userEmail: String?,
    userUid: String,
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
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (selectedDestination != ShellDestination.Dashboard) {
                PremiumTopBar(title = "Niqdah")
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp
            ) {
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
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NavCoral,
                            selectedTextColor = NavCoral,
                            indicatorColor = Color(0xFFFFECEC),
                            unselectedIconColor = NavMuted,
                            unselectedTextColor = NavMuted
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (selectedDestination) {
            ShellDestination.Dashboard -> DashboardScreen(
                uiState = financeUiState,
                padding = padding,
                onClearError = financeViewModel::clearError,
                userEmail = userEmail
            )
            ShellDestination.Transactions -> TransactionsScreen(
                uiState = financeUiState,
                padding = padding,
                onSaveTransaction = financeViewModel::saveTransaction,
                onDeleteTransaction = financeViewModel::deleteTransaction,
                onDeleteIncomeTransaction = financeViewModel::deleteIncomeTransaction,
                onPreviewBankMessage = financeViewModel::previewBankMessage,
                onSaveImportedBankMessage = financeViewModel::saveImportedBankMessage,
                onRecordManualDeposit = financeViewModel::recordManualDeposit,
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
                userUid = userUid,
                aiHealthStatus = aiHealthStatus(aiChatUiState),
                padding = padding,
                onUpdateProfileAndDebt = financeViewModel::updateProfileAndDebt,
                onUpdateCategoryBudgets = financeViewModel::updateCategoryBudgets,
                onUpdateBankMessageSettings = { automatic, dailySender, dailyEnabled, savingsSender,
                    savingsEnabled, debitKeywords, creditKeywords, savingsKeywords,
                    dailySuffix, savingsSuffix, learnFromEdits, reminderEnabled, reminderMinutes ->
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
                        isMerchantLearningEnabled = learnFromEdits,
                        isInternalTransferReminderEnabled = reminderEnabled,
                        internalTransferReminderThresholdMinutes = reminderMinutes
                    )
                },
                onUpdateReminderSettings = financeViewModel::updateReminderSettings,
                onSaveNecessaryItem = financeViewModel::saveNecessaryItem,
                onUpdateNecessaryItemStatus = financeViewModel::updateNecessaryItemStatus,
                onDeleteNecessaryItem = financeViewModel::deleteNecessaryItem,
                onLogout = onLogout,
                onClearError = financeViewModel::clearError
            )
        }
    }
}

private fun aiHealthStatus(uiState: AiChatUiState): String =
    when {
        uiState.isSending -> "Request in progress"
        uiState.errorMessage != null -> "Last error: ${uiState.errorMessage}"
        uiState.messages.size > 1 -> "Last response received"
        else -> "Not checked this session"
    }
