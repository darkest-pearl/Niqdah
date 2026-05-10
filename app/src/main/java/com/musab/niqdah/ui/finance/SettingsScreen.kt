package com.musab.niqdah.ui.finance

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.MerchantRule

@Composable
fun SettingsScreen(
    uiState: FinanceUiState,
    userEmail: String?,
    padding: PaddingValues,
    onUpdateProfileAndDebt: (String, String, String, String, String, String) -> Unit,
    onUpdateCategoryBudgets: (Map<String, String>) -> Unit,
    onUpdateBankMessageSettings: (Boolean, String, Boolean, String, Boolean, String, String, String, String, String, Boolean, Boolean, Int) -> Unit,
    onLogout: () -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    val profile = uiState.data.profile
    val debt = uiState.data.debt
    var salary by remember { mutableStateOf("") }
    var extraIncome by remember { mutableStateOf("") }
    var savingsTarget by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var startingDebt by remember { mutableStateOf("") }
    var remainingDebt by remember { mutableStateOf("") }
    var categoryBudgets by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var dailySenderName by remember { mutableStateOf("") }
    var dailyUseAccountSuffix by remember { mutableStateOf("") }
    var isAutomaticSmsImportEnabled by remember { mutableStateOf(false) }
    var isSmsPermissionGranted by remember {
        mutableStateOf(context.hasReceiveSmsPermission())
    }
    var isNotificationPermissionGranted by remember {
        mutableStateOf(context.hasPostNotificationsPermission())
    }
    var isDailyParserEnabled by remember { mutableStateOf(true) }
    var savingsSenderName by remember { mutableStateOf("") }
    var savingsAccountSuffix by remember { mutableStateOf("") }
    var isSavingsParserEnabled by remember { mutableStateOf(true) }
    var isMerchantLearningEnabled by remember { mutableStateOf(true) }
    var isInternalTransferReminderEnabled by remember { mutableStateOf(true) }
    var internalTransferReminderThresholdMinutes by remember { mutableStateOf(10) }
    var debitKeywords by remember { mutableStateOf("") }
    var creditKeywords by remember { mutableStateOf("") }
    var savingsTransferKeywords by remember { mutableStateOf("") }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationPermissionGranted = granted
        isAutomaticSmsImportEnabled = isSmsPermissionGranted
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isSmsPermissionGranted = granted
        if (granted && !isNotificationPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            isAutomaticSmsImportEnabled = granted
        }
    }

    LaunchedEffect(profile, debt) {
        salary = formatInputMoney(profile.salary)
        extraIncome = formatInputMoney(profile.extraIncome)
        savingsTarget = formatInputMoney(profile.monthlySavingsTarget)
        currency = profile.currency
        startingDebt = formatInputMoney(debt.startingAmount)
        remainingDebt = formatInputMoney(debt.remainingAmount)
    }

    LaunchedEffect(uiState.data.categories) {
        categoryBudgets = uiState.data.categories.associate { it.id to formatInputMoney(it.monthlyBudget) }
    }

    LaunchedEffect(uiState.data.bankMessageSettings) {
        val settings = uiState.data.bankMessageSettings
        isAutomaticSmsImportEnabled = settings.isAutomaticSmsImportEnabled
        dailySenderName = settings.dailyUseSource.senderName
        dailyUseAccountSuffix = settings.dailyUseAccountSuffix
        isDailyParserEnabled = settings.dailyUseSource.isEnabled
        savingsSenderName = settings.savingsSource.senderName
        savingsAccountSuffix = settings.savingsAccountSuffix
        isSavingsParserEnabled = settings.savingsSource.isEnabled
        isMerchantLearningEnabled = settings.isMerchantLearningEnabled
        isInternalTransferReminderEnabled = settings.isInternalTransferReminderEnabled
        internalTransferReminderThresholdMinutes = settings.internalTransferReminderThresholdMinutes
        debitKeywords = settings.debitKeywords.joinToString(", ")
        creditKeywords = settings.creditKeywords.joinToString(", ")
        savingsTransferKeywords = settings.savingsTransferKeywords.joinToString(", ")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FinanceHeader(
                title = "Settings",
                subtitle = "Tune the profile, monthly budget, currency, and debt baseline."
            )
        }
        item { ErrorBanner(message = uiState.errorMessage, onDismiss = onClearError) }
        item {
            AccountCard(
                userEmail = userEmail,
                isSaving = uiState.isSaving,
                onLogout = onLogout
            )
        }
        item {
            ProfileSettingsCard(
                salary = salary,
                onSalaryChange = { salary = it },
                extraIncome = extraIncome,
                onExtraIncomeChange = { extraIncome = it },
                savingsTarget = savingsTarget,
                onSavingsTargetChange = { savingsTarget = it },
                currency = currency,
                onCurrencyChange = { currency = it },
                startingDebt = startingDebt,
                onStartingDebtChange = { startingDebt = it },
                remainingDebt = remainingDebt,
                onRemainingDebtChange = { remainingDebt = it },
                isSaving = uiState.isSaving,
                onSave = {
                    onUpdateProfileAndDebt(
                        salary,
                        extraIncome,
                        savingsTarget,
                        currency,
                        startingDebt,
                        remainingDebt
                    )
                }
            )
        }
        item {
            BankMessageSourcesCard(
                isAutomaticSmsImportEnabled = isAutomaticSmsImportEnabled,
                onAutomaticSmsImportEnabledChange = { enabled ->
                    when {
                        !enabled -> isAutomaticSmsImportEnabled = false
                        !isSmsPermissionGranted -> smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        !isNotificationPermissionGranted &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else -> isAutomaticSmsImportEnabled = true
                    }
                },
                isSmsPermissionGranted = isSmsPermissionGranted,
                isNotificationPermissionGranted = isNotificationPermissionGranted,
                dailyUseBalance = uiState.data.latestDailyUseBalance?.let {
                    "${formatMoney(it.availableBalance, it.currency)} at ${formatTransactionDateTime(it.messageTimestampMillis)}"
                } ?: "Not known",
                savingsBalance = uiState.data.latestSavingsBalance?.let {
                    "${formatMoney(it.availableBalance, it.currency)} at ${formatTransactionDateTime(it.messageTimestampMillis)}"
                } ?: "Not known",
                lastIgnoredSender = uiState.data.bankMessageSettings.lastIgnoredSender,
                lastIgnoredReason = uiState.data.bankMessageSettings.lastIgnoredReason,
                lastParsedBankMessageAtMillis = uiState.data.bankMessageSettings.lastParsedBankMessageAtMillis,
                dailySenderName = dailySenderName,
                onDailySenderNameChange = { dailySenderName = it },
                dailyUseAccountSuffix = dailyUseAccountSuffix,
                onDailyUseAccountSuffixChange = { dailyUseAccountSuffix = it.filter { char -> char.isDigit() }.takeLast(4) },
                isDailyParserEnabled = isDailyParserEnabled,
                onDailyParserEnabledChange = { isDailyParserEnabled = it },
                savingsSenderName = savingsSenderName,
                onSavingsSenderNameChange = { savingsSenderName = it },
                savingsAccountSuffix = savingsAccountSuffix,
                onSavingsAccountSuffixChange = { savingsAccountSuffix = it.filter { char -> char.isDigit() }.takeLast(4) },
                isSavingsParserEnabled = isSavingsParserEnabled,
                onSavingsParserEnabledChange = { isSavingsParserEnabled = it },
                isMerchantLearningEnabled = isMerchantLearningEnabled,
                onMerchantLearningEnabledChange = { isMerchantLearningEnabled = it },
                isInternalTransferReminderEnabled = isInternalTransferReminderEnabled,
                onInternalTransferReminderEnabledChange = { isInternalTransferReminderEnabled = it },
                internalTransferReminderThresholdMinutes = internalTransferReminderThresholdMinutes,
                onInternalTransferReminderThresholdChange = { internalTransferReminderThresholdMinutes = it },
                merchantRules = uiState.data.merchantRules,
                debitKeywords = debitKeywords,
                onDebitKeywordsChange = { debitKeywords = it },
                creditKeywords = creditKeywords,
                onCreditKeywordsChange = { creditKeywords = it },
                savingsTransferKeywords = savingsTransferKeywords,
                onSavingsTransferKeywordsChange = { savingsTransferKeywords = it },
                isSaving = uiState.isSaving,
                onSave = {
                    onUpdateBankMessageSettings(
                        isAutomaticSmsImportEnabled && isSmsPermissionGranted,
                        dailySenderName,
                        isDailyParserEnabled,
                        savingsSenderName,
                        isSavingsParserEnabled,
                        debitKeywords,
                        creditKeywords,
                        savingsTransferKeywords,
                        dailyUseAccountSuffix,
                        savingsAccountSuffix,
                        isMerchantLearningEnabled,
                        isInternalTransferReminderEnabled,
                        internalTransferReminderThresholdMinutes
                    )
                }
            )
        }
        item {
            Text(text = "Category budgets", style = MaterialTheme.typography.titleLarge)
        }
        items(uiState.data.categories, key = { it.id }) { category ->
            CategoryBudgetField(
                category = category,
                value = categoryBudgets[category.id].orEmpty(),
                onValueChange = { value ->
                    categoryBudgets = categoryBudgets + (category.id to value)
                }
            )
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving,
                shape = MaterialTheme.shapes.medium,
                onClick = { onUpdateCategoryBudgets(categoryBudgets) }
            ) {
                Text(if (uiState.isSaving) "Saving..." else "Save category budgets")
            }
        }
    }
}

@Composable
private fun AccountCard(userEmail: String?, isSaving: Boolean, onLogout: () -> Unit) {
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
            Text(text = "Signed in", style = MaterialTheme.typography.titleMedium)
            Text(
                text = userEmail ?: "Niqdah account",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                shape = MaterialTheme.shapes.medium,
                onClick = onLogout
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Log out")
            }
        }
    }
}

@Composable
private fun ProfileSettingsCard(
    salary: String,
    onSalaryChange: (String) -> Unit,
    extraIncome: String,
    onExtraIncomeChange: (String) -> Unit,
    savingsTarget: String,
    onSavingsTargetChange: (String) -> Unit,
    currency: String,
    onCurrencyChange: (String) -> Unit,
    startingDebt: String,
    onStartingDebtChange: (String) -> Unit,
    remainingDebt: String,
    onRemainingDebtChange: (String) -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit
) {
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
            Text(text = "Financial profile", style = MaterialTheme.typography.titleMedium)
            MoneyField(label = "Salary", value = salary, onValueChange = onSalaryChange)
            MoneyField(label = "Extra income", value = extraIncome, onValueChange = onExtraIncomeChange)
            MoneyField(label = "Monthly savings target", value = savingsTarget, onValueChange = onSavingsTargetChange)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = currency,
                onValueChange = onCurrencyChange,
                label = { Text("Currency") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
            )
            MoneyField(label = "Starting debt", value = startingDebt, onValueChange = onStartingDebtChange)
            MoneyField(label = "Remaining debt", value = remainingDebt, onValueChange = onRemainingDebtChange)
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                shape = MaterialTheme.shapes.medium,
                onClick = onSave
            ) {
                Text(if (isSaving) "Saving..." else "Save profile")
            }
        }
    }
}

@Composable
private fun BankMessageSourcesCard(
    isAutomaticSmsImportEnabled: Boolean,
    onAutomaticSmsImportEnabledChange: (Boolean) -> Unit,
    isSmsPermissionGranted: Boolean,
    isNotificationPermissionGranted: Boolean,
    dailyUseBalance: String,
    savingsBalance: String,
    lastIgnoredSender: String,
    lastIgnoredReason: String,
    lastParsedBankMessageAtMillis: Long,
    dailySenderName: String,
    onDailySenderNameChange: (String) -> Unit,
    dailyUseAccountSuffix: String,
    onDailyUseAccountSuffixChange: (String) -> Unit,
    isDailyParserEnabled: Boolean,
    onDailyParserEnabledChange: (Boolean) -> Unit,
    savingsSenderName: String,
    onSavingsSenderNameChange: (String) -> Unit,
    savingsAccountSuffix: String,
    onSavingsAccountSuffixChange: (String) -> Unit,
    isSavingsParserEnabled: Boolean,
    onSavingsParserEnabledChange: (Boolean) -> Unit,
    isMerchantLearningEnabled: Boolean,
    onMerchantLearningEnabledChange: (Boolean) -> Unit,
    isInternalTransferReminderEnabled: Boolean,
    onInternalTransferReminderEnabledChange: (Boolean) -> Unit,
    internalTransferReminderThresholdMinutes: Int,
    onInternalTransferReminderThresholdChange: (Int) -> Unit,
    merchantRules: List<MerchantRule>,
    debitKeywords: String,
    onDebitKeywordsChange: (String) -> Unit,
    creditKeywords: String,
    onCreditKeywordsChange: (String) -> Unit,
    savingsTransferKeywords: String,
    onSavingsTransferKeywordsChange: (String) -> Unit,
    isSaving: Boolean,
    onSave: () -> Unit
) {
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
            Text(text = "Bank Message Sources", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Niqdah can read new bank SMS messages from your selected senders to prepare expense and savings drafts.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            SourceToggleRow(
                title = "Enable automatic SMS import",
                isEnabled = isAutomaticSmsImportEnabled,
                onEnabledChange = onAutomaticSmsImportEnabledChange
            )
            SourceToggleRow(
                title = "Require review before saving",
                isEnabled = true,
                isLocked = true,
                onEnabledChange = {}
            )
            Text(
                text = "SMS permission: ${if (isSmsPermissionGranted) "granted" else "not granted"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Notification permission: ${if (isNotificationPermissionGranted) "granted" else "not granted"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Automatic SMS import: ${if (isAutomaticSmsImportEnabled) "enabled" else "disabled"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Daily-use latest balance: $dailyUseBalance",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Savings latest balance: $savingsBalance",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Last ignored sender: ${lastIgnoredSender.ifBlank { "None" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Last ignored reason: ${lastIgnoredReason.ifBlank { "None" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Last parsed bank message: ${
                    lastParsedBankMessageAtMillis.takeIf { it > 0L }?.let { formatTransactionDateTime(it) } ?: "None"
                }",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            SourceToggleRow(
                title = "Daily-use parser",
                isEnabled = isDailyParserEnabled,
                onEnabledChange = onDailyParserEnabledChange
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = dailySenderName,
                onValueChange = onDailySenderNameChange,
                label = { Text("Daily-use sender name") },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = dailyUseAccountSuffix,
                onValueChange = onDailyUseAccountSuffixChange,
                label = { Text("Daily-use account suffix") },
                supportingText = { Text("Last 4 digits only") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            SourceToggleRow(
                title = "Savings parser",
                isEnabled = isSavingsParserEnabled,
                onEnabledChange = onSavingsParserEnabledChange
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = savingsSenderName,
                onValueChange = onSavingsSenderNameChange,
                label = { Text("Savings sender name") },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = savingsAccountSuffix,
                onValueChange = onSavingsAccountSuffixChange,
                label = { Text("Savings account suffix") },
                supportingText = { Text("Last 4 digits only") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            SourceToggleRow(
                title = "Learn from merchant edits",
                isEnabled = isMerchantLearningEnabled,
                onEnabledChange = onMerchantLearningEnabledChange
            )
            SourceToggleRow(
                title = "Internal transfer safety reminder",
                isEnabled = isInternalTransferReminderEnabled,
                onEnabledChange = onInternalTransferReminderEnabledChange
            )
            Text(
                text = "Reminder threshold",
                style = MaterialTheme.typography.titleSmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 30, 60).forEach { minutes ->
                    FilterChip(
                        selected = internalTransferReminderThresholdMinutes == minutes,
                        onClick = { onInternalTransferReminderThresholdChange(minutes) },
                        label = { Text("$minutes min") }
                    )
                }
            }
            if (merchantRules.isNotEmpty()) {
                Text(text = "Merchant rules", style = MaterialTheme.typography.titleSmall)
                merchantRules.take(8).forEach { rule ->
                    Text(
                        text = "${rule.merchantName}: ${rule.categoryName} / ${rule.necessity.label}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = debitKeywords,
                onValueChange = onDebitKeywordsChange,
                label = { Text("Debit keywords") },
                minLines = 2
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = creditKeywords,
                onValueChange = onCreditKeywordsChange,
                label = { Text("Credit keywords") },
                minLines = 2
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = savingsTransferKeywords,
                onValueChange = onSavingsTransferKeywordsChange,
                label = { Text("Savings transfer keywords") },
                minLines = 2
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                shape = MaterialTheme.shapes.medium,
                onClick = onSave
            ) {
                Text(if (isSaving) "Saving..." else "Save bank message sources")
            }
        }
    }
}

@Composable
private fun SourceToggleRow(
    title: String,
    isEnabled: Boolean,
    isLocked: Boolean = false,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = isEnabled,
            enabled = !isLocked,
            onCheckedChange = onEnabledChange
        )
    }
}

private fun android.content.Context.hasReceiveSmsPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

private fun android.content.Context.hasPostNotificationsPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

@Composable
private fun CategoryBudgetField(
    category: BudgetCategory,
    value: String,
    onValueChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = category.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = category.type.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            MoneyField(label = "Monthly budget", value = value, onValueChange = onValueChange)
        }
    }
}

@Composable
private fun MoneyField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}
