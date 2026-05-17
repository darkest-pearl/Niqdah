package com.musab.niqdah.ui.finance

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musab.niqdah.BuildConfig
import com.musab.niqdah.domain.finance.AccountBalanceStatus
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.BankMessageParserSettings
import com.musab.niqdah.domain.finance.MerchantRule
import com.musab.niqdah.domain.finance.NecessaryItem
import com.musab.niqdah.domain.finance.NecessaryItemRecurrence
import com.musab.niqdah.domain.finance.NecessaryItemStatus
import com.musab.niqdah.domain.finance.ReminderSettings

private enum class SettingsPage(val title: String, val subtitle: String) {
    MAIN("Settings", "Profile, accounts, privacy, and release controls."),
    PROFILE("Profile & Setup", "Personal plan, income, savings target, debt, and currency."),
    ACCOUNTS("Accounts & Bank SMS", "Daily-use account, savings account, SMS senders, suffixes, and permissions."),
    CATEGORIES("Categories & Budgets", "Budget categories, merchant rules, and necessary items."),
    REMINDERS("Reminders & Discipline", "Savings, debt, overspending, and goal countdown reminders."),
    PRIVACY("Privacy & Security", "What Niqdah reads, stores, and sends to AI."),
    APP("App & Release", "Account, version, build, and release notes."),
    QA("QA Diagnostics", "Release readiness, permissions, parser state, balances, and AI health.")
}

@Composable
fun SettingsScreen(
    uiState: FinanceUiState,
    userEmail: String?,
    userUid: String,
    aiHealthStatus: String,
    padding: PaddingValues,
    onUpdateProfileAndDebt: (String, String, String, String, String, String) -> Unit,
    onUpdateCategoryBudgets: (Map<String, String>) -> Unit,
    onUpdateBankMessageSettings: (Boolean, String, Boolean, String, Boolean, String, String, String, String, String, Boolean, Boolean, Int) -> Unit,
    onUpdateReminderSettings: (Boolean, String, String, String, String, Boolean, String, String, String, Boolean, Boolean, String, String, Boolean, String) -> Unit,
    onSaveNecessaryItem: (NecessaryItem?, String, String, String, String, NecessaryItemRecurrence, NecessaryItemStatus, Boolean) -> Unit,
    onUpdateNecessaryItemStatus: (NecessaryItem, NecessaryItemStatus) -> Unit,
    onDeleteNecessaryItem: (String) -> Unit,
    onLogout: () -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    var currentPage by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
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
    var isMonthlySavingsReminderEnabled by remember { mutableStateOf(true) }
    var monthlySavingsReminderDay by remember { mutableStateOf("1") }
    var monthlySavingsReminderHour by remember { mutableStateOf("9") }
    var monthlySavingsReminderMinute by remember { mutableStateOf("0") }
    var reminderSavingsTarget by remember { mutableStateOf("") }
    var isMissedSavingsReminderEnabled by remember { mutableStateOf(true) }
    var missedSavingsCheckDay by remember { mutableStateOf("20") }
    var missedSavingsReminderHour by remember { mutableStateOf("19") }
    var missedSavingsReminderMinute by remember { mutableStateOf("0") }
    var areOverspendingWarningsEnabled by remember { mutableStateOf(true) }
    var isAvoidCategoryWarningEnabled by remember { mutableStateOf(true) }
    var januaryTargetDate by remember { mutableStateOf("") }
    var januaryFundTarget by remember { mutableStateOf("") }
    var isPostSalarySavingsFollowUpEnabled by remember { mutableStateOf(true) }
    var postSalarySavingsFollowUpIntervalDays by remember { mutableStateOf("3") }
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
    val openNotificationSettings: () -> Unit = {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
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

    LaunchedEffect(uiState.data.reminderSettings) {
        val settings = uiState.data.reminderSettings
        isMonthlySavingsReminderEnabled = settings.isMonthlySavingsReminderEnabled
        monthlySavingsReminderDay = settings.monthlySavingsReminderDay.toString()
        monthlySavingsReminderHour = settings.monthlySavingsReminderHour.toString()
        monthlySavingsReminderMinute = settings.monthlySavingsReminderMinute.toString()
        reminderSavingsTarget = formatInputMoney(settings.monthlySavingsTargetAmount)
        isMissedSavingsReminderEnabled = settings.isMissedSavingsReminderEnabled
        missedSavingsCheckDay = settings.missedSavingsCheckDay.toString()
        missedSavingsReminderHour = settings.missedSavingsReminderHour.toString()
        missedSavingsReminderMinute = settings.missedSavingsReminderMinute.toString()
        areOverspendingWarningsEnabled = settings.areOverspendingWarningsEnabled
        isAvoidCategoryWarningEnabled = settings.isAvoidCategoryWarningEnabled
        januaryTargetDate = settings.januaryTargetDate
        januaryFundTarget = formatInputMoney(settings.januaryFundTargetAmount)
        isPostSalarySavingsFollowUpEnabled = settings.isPostSalarySavingsFollowUpEnabled
        postSalarySavingsFollowUpIntervalDays = settings.postSalarySavingsFollowUpIntervalDays.toString()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            FinanceHeader(title = currentPage.title, subtitle = currentPage.subtitle)
        }
        if (currentPage != SettingsPage.MAIN) {
            item {
                OutlinedButton(onClick = { currentPage = SettingsPage.MAIN }) {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    Text(text = "Back to Settings", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        item { ErrorBanner(message = uiState.errorMessage, onDismiss = onClearError) }
        item { StatusBanner(message = uiState.statusMessage, onDismiss = onClearError) }
        if (uiState.isLoading) {
            item { LoadingStateCard(message = "Loading settings...") }
        }

        when (currentPage) {
            SettingsPage.MAIN -> {
                item {
                    AccountCard(userEmail = userEmail, isSaving = uiState.isSaving, onLogout = onLogout)
                }
                item {
                    SetupChecklistCard(
                        userEmail = userEmail,
                        bankMessageSettings = uiState.data.bankMessageSettings,
                        reminderSettings = uiState.data.reminderSettings,
                        isSmsPermissionGranted = isSmsPermissionGranted,
                        isNotificationPermissionGranted = isNotificationPermissionGranted
                    )
                }
                item {
                    SettingsMenuCard(
                        title = SettingsPage.PROFILE.title,
                        body = "Personal plan, income, fixed expenses, debt, savings goals, and currency.",
                        onClick = { currentPage = SettingsPage.PROFILE }
                    )
                }
                item {
                    SettingsMenuCard(
                        title = SettingsPage.ACCOUNTS.title,
                        body = "Daily-use and savings account setup, senders, suffixes, and permissions.",
                        onClick = { currentPage = SettingsPage.ACCOUNTS }
                    )
                }
                item {
                    SettingsMenuCard(
                        title = SettingsPage.CATEGORIES.title,
                        body = "Budgets, learned merchant rules, and necessary items.",
                        onClick = { currentPage = SettingsPage.CATEGORIES }
                    )
                }
                item {
                    SettingsMenuCard(
                        title = SettingsPage.REMINDERS.title,
                        body = "Savings reminders, missed savings checks, overspending warnings, and goal countdown.",
                        onClick = { currentPage = SettingsPage.REMINDERS }
                    )
                }
                item {
                    SettingsMenuCard(
                        title = SettingsPage.PRIVACY.title,
                        body = "SMS privacy, AI context rules, and Firebase data notes.",
                        onClick = { currentPage = SettingsPage.PRIVACY }
                    )
                }
                item {
                    SettingsMenuCard(
                        title = SettingsPage.APP.title,
                        body = "Version, build type, account, and release/debug information.",
                        onClick = { currentPage = SettingsPage.APP }
                    )
                }
            }
            SettingsPage.PROFILE -> {
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
            }
            SettingsPage.ACCOUNTS -> {
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
                        dailyUseBalance = balanceStatusText(uiState.data.latestDailyUseBalanceStatus),
                        savingsBalance = balanceStatusText(uiState.data.latestSavingsBalanceStatus),
                        lastReceivedSender = uiState.data.bankMessageSettings.lastReceivedSender,
                        lastSenderMatched = uiState.data.bankMessageSettings.lastSenderMatched,
                        lastIgnoredSender = uiState.data.bankMessageSettings.lastIgnoredSender,
                        lastIgnoredReason = uiState.data.bankMessageSettings.lastIgnoredReason,
                        lastParsedResult = uiState.data.bankMessageSettings.lastParsedResult,
                        lastCreatedPendingImport = uiState.data.bankMessageSettings.lastCreatedPendingImport,
                        lastDuplicateBlocked = uiState.data.bankMessageSettings.lastDuplicateBlocked,
                        lastDuplicateReason = uiState.data.bankMessageSettings.lastDuplicateReason,
                        lastParserDecisionAtMillis = uiState.data.bankMessageSettings.lastParserDecisionAtMillis,
                        lastParsedBankMessageAtMillis = uiState.data.bankMessageSettings.lastParsedBankMessageAtMillis,
                        onRequestSmsPermission = { smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                isNotificationPermissionGranted = true
                            }
                        },
                        onOpenNotificationSettings = openNotificationSettings,
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
            }
            SettingsPage.CATEGORIES -> {
                item {
                    SectionHeader(
                        title = "Category budgets",
                        subtitle = "Set monthly limits with exact cents when needed."
                    )
                }
                items(uiState.data.categories, key = { it.id }) { category ->
                    CategoryBudgetField(
                        category = category,
                        value = categoryBudgets[category.id].orEmpty(),
                        onValueChange = { value -> categoryBudgets = categoryBudgets + (category.id to value) }
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
                item {
                    NecessaryItemsCard(
                        items = uiState.data.necessaryItems,
                        currency = uiState.data.profile.currency,
                        isSaving = uiState.isSaving,
                        onSaveNecessaryItem = onSaveNecessaryItem,
                        onUpdateNecessaryItemStatus = onUpdateNecessaryItemStatus,
                        onDeleteNecessaryItem = onDeleteNecessaryItem
                    )
                }
            }
            SettingsPage.REMINDERS -> {
                item {
                    ReminderSettingsCard(
                        isMonthlySavingsReminderEnabled = isMonthlySavingsReminderEnabled,
                        onMonthlySavingsReminderEnabledChange = { isMonthlySavingsReminderEnabled = it },
                        monthlySavingsReminderDay = monthlySavingsReminderDay,
                        onMonthlySavingsReminderDayChange = { value -> monthlySavingsReminderDay = value.filter { char -> char.isDigit() }.take(2) },
                        monthlySavingsReminderHour = monthlySavingsReminderHour,
                        onMonthlySavingsReminderHourChange = { value -> monthlySavingsReminderHour = value.filter { char -> char.isDigit() }.take(2) },
                        monthlySavingsReminderMinute = monthlySavingsReminderMinute,
                        onMonthlySavingsReminderMinuteChange = { value -> monthlySavingsReminderMinute = value.filter { char -> char.isDigit() }.take(2) },
                        reminderSavingsTarget = reminderSavingsTarget,
                        onReminderSavingsTargetChange = { reminderSavingsTarget = it },
                        isMissedSavingsReminderEnabled = isMissedSavingsReminderEnabled,
                        onMissedSavingsReminderEnabledChange = { isMissedSavingsReminderEnabled = it },
                        missedSavingsCheckDay = missedSavingsCheckDay,
                        onMissedSavingsCheckDayChange = { value -> missedSavingsCheckDay = value.filter { char -> char.isDigit() }.take(2) },
                        missedSavingsReminderHour = missedSavingsReminderHour,
                        onMissedSavingsReminderHourChange = { value -> missedSavingsReminderHour = value.filter { char -> char.isDigit() }.take(2) },
                        missedSavingsReminderMinute = missedSavingsReminderMinute,
                        onMissedSavingsReminderMinuteChange = { value -> missedSavingsReminderMinute = value.filter { char -> char.isDigit() }.take(2) },
                        areOverspendingWarningsEnabled = areOverspendingWarningsEnabled,
                        onOverspendingWarningsEnabledChange = { areOverspendingWarningsEnabled = it },
                        isAvoidCategoryWarningEnabled = isAvoidCategoryWarningEnabled,
                        onAvoidCategoryWarningEnabledChange = { isAvoidCategoryWarningEnabled = it },
                        januaryTargetDate = januaryTargetDate,
                        onJanuaryTargetDateChange = { januaryTargetDate = it },
                        januaryFundTarget = januaryFundTarget,
                        onJanuaryFundTargetChange = { januaryFundTarget = it },
                        isPostSalarySavingsFollowUpEnabled = isPostSalarySavingsFollowUpEnabled,
                        onPostSalarySavingsFollowUpEnabledChange = { isPostSalarySavingsFollowUpEnabled = it },
                        postSalarySavingsFollowUpIntervalDays = postSalarySavingsFollowUpIntervalDays,
                        onPostSalarySavingsFollowUpIntervalDaysChange = { value ->
                            postSalarySavingsFollowUpIntervalDays = value.filter { char -> char.isDigit() }.take(2)
                        },
                        isSaving = uiState.isSaving,
                        onSave = {
                            onUpdateReminderSettings(
                                isMonthlySavingsReminderEnabled,
                                monthlySavingsReminderDay,
                                monthlySavingsReminderHour,
                                monthlySavingsReminderMinute,
                                reminderSavingsTarget,
                                isMissedSavingsReminderEnabled,
                                missedSavingsCheckDay,
                                missedSavingsReminderHour,
                                missedSavingsReminderMinute,
                                areOverspendingWarningsEnabled,
                                isAvoidCategoryWarningEnabled,
                                januaryTargetDate,
                                januaryFundTarget,
                                isPostSalarySavingsFollowUpEnabled,
                                postSalarySavingsFollowUpIntervalDays
                            )
                        }
                    )
                }
            }
            SettingsPage.PRIVACY -> {
                item {
                    InfoNoteCard(
                        title = "Privacy commitments",
                        lines = listOf(
                            "Niqdah reads only new bank SMS after permission and only from configured senders.",
                            "Old inbox SMS is not read and READ_SMS is not requested.",
                            "Automatic imports store rawMessage as an empty string.",
                            "Raw SMS content is not sent to AI. AI receives financial summaries only.",
                            "Firebase stores your account data under your signed-in user ID."
                        )
                    )
                }
            }
            SettingsPage.APP -> {
                item {
                    AccountCard(userEmail = userEmail, isSaving = uiState.isSaving, onLogout = onLogout)
                }
                item {
                    InfoNoteCard(
                        title = "Release info",
                        lines = listOf(
                            "Niqdah finance build: debug/release from Gradle variant.",
                            "Bank SMS import keeps automatic rawMessage empty.",
                            "OpenAI keys are not stored in the Android app."
                        )
                    )
                }
                item {
                    SettingsMenuCard(
                        title = SettingsPage.QA.title,
                        body = "Version, auth, permissions, SMS parser decisions, balances, salary cycle, and AI status.",
                        onClick = { currentPage = SettingsPage.QA },
                        trailingIcon = Icons.Rounded.HealthAndSafety
                    )
                }
            }
            SettingsPage.QA -> {
                item {
                    QaDiagnosticsCard(
                        diagnostics = buildQaDiagnostics(
                            appVersionName = BuildConfig.VERSION_NAME,
                            appVersionCode = BuildConfig.VERSION_CODE,
                            buildType = if (BuildConfig.DEBUG) "debug" else "release",
                            firebaseAuthStatus = if (userUid.isBlank()) "Not signed in" else "Signed in",
                            isUidPresent = userUid.isNotBlank(),
                            isSmsPermissionGranted = isSmsPermissionGranted,
                            isNotificationPermissionGranted = isNotificationPermissionGranted,
                            aiHealthStatus = aiHealthStatus,
                            data = uiState.data
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuCard(
    title: String,
    body: String,
    onClick: () -> Unit,
    trailingIcon: ImageVector = Icons.Rounded.ChevronRight
) {
    PremiumCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    text = title.firstOrNull()?.toString().orEmpty(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Icon(imageVector = trailingIcon, contentDescription = null)
        }
    }
}

private fun balanceStatusText(status: AccountBalanceStatus?): String =
    status?.let {
        "${formatMoneyMinor(it.amountMinor, it.currency)} - ${it.confidence.label} at ${
            formatTransactionDateTime(it.lastUpdatedMillis)
        }"
    } ?: "Not known"

@Composable
private fun QaDiagnosticsCard(diagnostics: QaDiagnostics) {
    PremiumCard {
        Text(text = "QA Diagnostics", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "No raw SMS body, API key, password, or signing secret is shown here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        diagnostics.sections.forEach { section ->
            Text(text = section.title, style = MaterialTheme.typography.titleSmall)
            section.rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        modifier = Modifier.weight(0.42f),
                        text = row.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        modifier = Modifier.weight(0.58f),
                        text = row.value,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCard(userEmail: String?, isSaving: Boolean, onLogout: () -> Unit) {
    PremiumCard {
            Text(text = "Signed in", style = MaterialTheme.typography.titleMedium)
            Text(
                text = userEmail ?: "Niqdah account",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            SecondaryActionButton(
                modifier = Modifier.fillMaxWidth(),
                label = "Log out",
                icon = Icons.AutoMirrored.Rounded.Logout,
                enabled = !isSaving,
                onClick = onLogout
            )
    }
}

@Composable
private fun SetupChecklistCard(
    userEmail: String?,
    bankMessageSettings: BankMessageParserSettings,
    reminderSettings: ReminderSettings,
    isSmsPermissionGranted: Boolean,
    isNotificationPermissionGranted: Boolean
) {
    val rows = listOf(
        "Firebase login" to !userEmail.isNullOrBlank(),
        "Bank sender configured" to (
            bankMessageSettings.dailyUseSource.senderName.isNotBlank() ||
                bankMessageSettings.savingsSource.senderName.isNotBlank()
            ),
        "Daily account suffix" to (bankMessageSettings.dailyUseAccountSuffix.length == 4),
        "Savings account suffix" to (bankMessageSettings.savingsAccountSuffix.length == 4),
        "SMS permission" to isSmsPermissionGranted,
        "Notification permission" to isNotificationPermissionGranted,
        "Monthly savings target" to (reminderSettings.monthlySavingsTargetAmount > 0.0),
        "Goal target" to (
            reminderSettings.januaryTargetDate.isNotBlank() &&
                reminderSettings.januaryFundTargetAmount > 0.0
            )
    )

    PremiumCard {
            Text(text = "Setup checklist", style = MaterialTheme.typography.titleMedium)
            rows.forEach { (label, isComplete) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isComplete) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = if (isComplete) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (isComplete) "Done" else "Needs setup",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
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
    lastReceivedSender: String,
    lastSenderMatched: Boolean,
    lastIgnoredSender: String,
    lastIgnoredReason: String,
    lastParsedResult: String,
    lastCreatedPendingImport: Boolean,
    lastDuplicateBlocked: Boolean,
    lastDuplicateReason: String,
    lastParserDecisionAtMillis: Long,
    lastParsedBankMessageAtMillis: Long,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
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
            Text(text = "Bank message sources", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Niqdah can read new bank SMS messages from your selected senders to prepare expense and savings drafts.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(text = "Permission recovery", style = MaterialTheme.typography.titleSmall)
            listOf(
                "If SMS import stops working, turn SMS permission off and on again.",
                "Ensure automatic SMS import is enabled.",
                "Ensure sender names match the bank SMS sender exactly.",
                "Enable notification permission for review alerts."
            ).forEach { line ->
                Text(
                    text = line,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRequestSmsPermission
                ) {
                    Text("Request SMS")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRequestNotificationPermission
                ) {
                    Text("Request notifications")
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenNotificationSettings
            ) {
                Text("Open app notification settings")
            }
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
                text = "Last SMS received from sender: ${lastReceivedSender.ifBlank { "None" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Matched configured sender: ${if (lastSenderMatched) "yes" else "no"}",
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
                text = "Last parsed result: ${lastParsedResult.ifBlank { "None" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Created pending import: ${if (lastCreatedPendingImport) "yes" else "no"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Duplicate blocked: ${if (lastDuplicateBlocked) "yes" else "no"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Last duplicate reason: ${lastDuplicateReason.ifBlank { "None" }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Last parser decision: ${
                    lastParserDecisionAtMillis.takeIf { it > 0L }?.let { formatTransactionDateTime(it) } ?: "None"
                }",
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
private fun ReminderSettingsCard(
    isMonthlySavingsReminderEnabled: Boolean,
    onMonthlySavingsReminderEnabledChange: (Boolean) -> Unit,
    monthlySavingsReminderDay: String,
    onMonthlySavingsReminderDayChange: (String) -> Unit,
    monthlySavingsReminderHour: String,
    onMonthlySavingsReminderHourChange: (String) -> Unit,
    monthlySavingsReminderMinute: String,
    onMonthlySavingsReminderMinuteChange: (String) -> Unit,
    reminderSavingsTarget: String,
    onReminderSavingsTargetChange: (String) -> Unit,
    isMissedSavingsReminderEnabled: Boolean,
    onMissedSavingsReminderEnabledChange: (Boolean) -> Unit,
    missedSavingsCheckDay: String,
    onMissedSavingsCheckDayChange: (String) -> Unit,
    missedSavingsReminderHour: String,
    onMissedSavingsReminderHourChange: (String) -> Unit,
    missedSavingsReminderMinute: String,
    onMissedSavingsReminderMinuteChange: (String) -> Unit,
    areOverspendingWarningsEnabled: Boolean,
    onOverspendingWarningsEnabledChange: (Boolean) -> Unit,
    isAvoidCategoryWarningEnabled: Boolean,
    onAvoidCategoryWarningEnabledChange: (Boolean) -> Unit,
    januaryTargetDate: String,
    onJanuaryTargetDateChange: (String) -> Unit,
    januaryFundTarget: String,
    onJanuaryFundTargetChange: (String) -> Unit,
    isPostSalarySavingsFollowUpEnabled: Boolean,
    onPostSalarySavingsFollowUpEnabledChange: (Boolean) -> Unit,
    postSalarySavingsFollowUpIntervalDays: String,
    onPostSalarySavingsFollowUpIntervalDaysChange: (String) -> Unit,
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
            Text(text = "Reminder settings", style = MaterialTheme.typography.titleMedium)
            SourceToggleRow(
                title = "Monthly savings transfer reminder",
                isEnabled = isMonthlySavingsReminderEnabled,
                onEnabledChange = onMonthlySavingsReminderEnabledChange
            )
            MoneyField(
                label = "Monthly savings target",
                value = reminderSavingsTarget,
                onValueChange = onReminderSavingsTargetChange
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    modifier = Modifier.weight(1f),
                    label = "Day",
                    value = monthlySavingsReminderDay,
                    onValueChange = onMonthlySavingsReminderDayChange
                )
                NumberField(
                    modifier = Modifier.weight(1f),
                    label = "Hour",
                    value = monthlySavingsReminderHour,
                    onValueChange = onMonthlySavingsReminderHourChange
                )
                NumberField(
                    modifier = Modifier.weight(1f),
                    label = "Minute",
                    value = monthlySavingsReminderMinute,
                    onValueChange = onMonthlySavingsReminderMinuteChange
                )
            }
            SourceToggleRow(
                title = "Missed savings reminder",
                isEnabled = isMissedSavingsReminderEnabled,
                onEnabledChange = onMissedSavingsReminderEnabledChange
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    modifier = Modifier.weight(1f),
                    label = "Check day",
                    value = missedSavingsCheckDay,
                    onValueChange = onMissedSavingsCheckDayChange
                )
                NumberField(
                    modifier = Modifier.weight(1f),
                    label = "Hour",
                    value = missedSavingsReminderHour,
                    onValueChange = onMissedSavingsReminderHourChange
                )
                NumberField(
                    modifier = Modifier.weight(1f),
                    label = "Minute",
                    value = missedSavingsReminderMinute,
                    onValueChange = onMissedSavingsReminderMinuteChange
                )
            }
            SourceToggleRow(
                title = "Overspending warnings",
                isEnabled = areOverspendingWarningsEnabled,
                onEnabledChange = onOverspendingWarningsEnabledChange
            )
            SourceToggleRow(
                title = "Avoid-category warning",
                isEnabled = isAvoidCategoryWarningEnabled,
                onEnabledChange = onAvoidCategoryWarningEnabledChange
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = januaryTargetDate,
                onValueChange = onJanuaryTargetDateChange,
                label = { Text("Goal target date") },
                supportingText = { Text("YYYY-MM-DD") },
                singleLine = true
            )
            MoneyField(
                label = "Total fund target",
                value = januaryFundTarget,
                onValueChange = onJanuaryFundTargetChange
            )
            SourceToggleRow(
                title = "Post-salary savings follow-up",
                isEnabled = isPostSalarySavingsFollowUpEnabled,
                onEnabledChange = onPostSalarySavingsFollowUpEnabledChange
            )
            NumberField(
                modifier = Modifier.fillMaxWidth(),
                label = "Follow-up interval days",
                value = postSalarySavingsFollowUpIntervalDays,
                onValueChange = onPostSalarySavingsFollowUpIntervalDaysChange
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                shape = MaterialTheme.shapes.medium,
                onClick = onSave
            ) {
                Text(if (isSaving) "Saving..." else "Save reminders")
            }
        }
    }
}

@Composable
private fun NecessaryItemsCard(
    items: List<NecessaryItem>,
    currency: String,
    isSaving: Boolean,
    onSaveNecessaryItem: (NecessaryItem?, String, String, String, String, NecessaryItemRecurrence, NecessaryItemStatus, Boolean) -> Unit,
    onUpdateNecessaryItemStatus: (NecessaryItem, NecessaryItemStatus) -> Unit,
    onDeleteNecessaryItem: (String) -> Unit
) {
    var editingItem by remember { mutableStateOf<NecessaryItem?>(null) }
    var isDialogOpen by remember { mutableStateOf(false) }

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
            Text(text = "Necessary items", style = MaterialTheme.typography.titleMedium)
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                shape = MaterialTheme.shapes.medium,
                onClick = {
                    editingItem = null
                    isDialogOpen = true
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Add necessary item")
            }
            if (items.isEmpty()) {
                Text(
                    text = "No necessary reminders yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { item ->
                    NecessaryItemRow(
                        item = item,
                        currency = currency,
                        isSaving = isSaving,
                        onEdit = {
                            editingItem = item
                            isDialogOpen = true
                        },
                        onStatusChange = { status -> onUpdateNecessaryItemStatus(item, status) },
                        onDelete = { onDeleteNecessaryItem(item.id) }
                    )
                }
            }
        }
    }

    if (isDialogOpen) {
        NecessaryItemDialog(
            existing = editingItem,
            currency = currency,
            onDismiss = { isDialogOpen = false },
            onSave = { existing, title, amount, dueDay, dueDate, recurrence, status, notifications ->
                onSaveNecessaryItem(existing, title, amount, dueDay, dueDate, recurrence, status, notifications)
                isDialogOpen = false
            }
        )
    }
}

@Composable
private fun NecessaryItemRow(
    item: NecessaryItem,
    currency: String,
    isSaving: Boolean,
    onEdit: () -> Unit,
    onStatusChange: (NecessaryItemStatus) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = necessaryItemDueText(item),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    item.amount?.let {
                        Text(
                            text = formatMoney(it, currency),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit, enabled = !isSaving) {
                        Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Edit necessary item")
                    }
                    IconButton(onClick = onDelete, enabled = !isSaving) {
                        Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Delete necessary item")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NecessaryItemStatus.entries.forEach { status ->
                    FilterChip(
                        selected = item.status == status,
                        onClick = { onStatusChange(status) },
                        label = { Text(status.label) },
                        enabled = !isSaving
                    )
                }
            }
        }
    }
}

@Composable
private fun NecessaryItemDialog(
    existing: NecessaryItem?,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (NecessaryItem?, String, String, String, String, NecessaryItemRecurrence, NecessaryItemStatus, Boolean) -> Unit
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var amount by remember(existing?.id) {
        mutableStateOf(existing?.amount?.let { formatInputMoney(it) }.orEmpty())
    }
    var dueDay by remember(existing?.id) {
        mutableStateOf(existing?.dueDayOfMonth?.toString() ?: "1")
    }
    var dueDate by remember(existing?.id) {
        mutableStateOf(existing?.dueDateMillis?.let { formatTransactionDate(it) }.orEmpty())
    }
    var recurrence by remember(existing?.id) {
        mutableStateOf(existing?.recurrence ?: NecessaryItemRecurrence.MONTHLY)
    }
    var status by remember(existing?.id) {
        mutableStateOf(existing?.status ?: NecessaryItemStatus.PENDING)
    }
    var notifications by remember(existing?.id) {
        mutableStateOf(existing?.isNotificationEnabled ?: true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add necessary item" else "Edit necessary item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true
                )
                MoneyField(label = "Amount ($currency, optional)", value = amount, onValueChange = { amount = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NecessaryItemRecurrence.entries.forEach { option ->
                        FilterChip(
                            selected = recurrence == option,
                            onClick = { recurrence = option },
                            label = { Text(option.label) }
                        )
                    }
                }
                if (recurrence == NecessaryItemRecurrence.MONTHLY) {
                    NumberField(
                        label = "Due day",
                        value = dueDay,
                        onValueChange = { value -> dueDay = value.filter { char -> char.isDigit() }.take(2) }
                    )
                } else {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = dueDate,
                        onValueChange = { dueDate = it },
                        label = { Text("Due date") },
                        supportingText = { Text("YYYY-MM-DD") },
                        singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NecessaryItemStatus.entries.forEach { option ->
                        FilterChip(
                            selected = status == option,
                            onClick = { status = option },
                            label = { Text(option.label) }
                        )
                    }
                }
                SourceToggleRow(
                    title = "Notification",
                    isEnabled = notifications,
                    onEnabledChange = { notifications = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(existing, title, amount, dueDay, dueDate, recurrence, status, notifications)
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

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

private fun necessaryItemDueText(item: NecessaryItem): String =
    when (item.recurrence) {
        NecessaryItemRecurrence.MONTHLY -> "Monthly on day ${item.dueDayOfMonth}"
        NecessaryItemRecurrence.ONE_TIME -> item.dueDateMillis
            ?.let { "Due ${formatTransactionDate(it)}" }
            ?: "One-time"
    } + if (item.isNotificationEnabled) " - notification on" else " - notification off"
