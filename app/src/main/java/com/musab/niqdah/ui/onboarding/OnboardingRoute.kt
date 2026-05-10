package com.musab.niqdah.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musab.niqdah.domain.finance.CategoryBudgetSetup
import com.musab.niqdah.domain.finance.DebtLenderType
import com.musab.niqdah.domain.finance.DebtPressureLevel
import com.musab.niqdah.domain.finance.DebtProfile
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.FixedExpense
import com.musab.niqdah.domain.finance.GoalPurpose
import com.musab.niqdah.domain.finance.OnboardingPlanner
import com.musab.niqdah.domain.finance.OnboardingState
import com.musab.niqdah.domain.finance.PrimarySavingsGoal
import com.musab.niqdah.domain.finance.UserPreferenceSetup
import com.musab.niqdah.ui.finance.ErrorBanner
import com.musab.niqdah.ui.finance.PremiumCard
import com.musab.niqdah.ui.finance.SectionHeader
import com.musab.niqdah.ui.finance.StatusPill
import com.musab.niqdah.ui.finance.formatMoney

private enum class OnboardingStep(val title: String, val subtitle: String) {
    Welcome("Build your control plan", "Choose the money pressure Niqdah should help you manage first."),
    Income("Monthly income", "Set your baseline so every recommendation starts from your real cashflow."),
    FixedExpenses("Fixed costs", "Add the commitments that must be protected before optional spending."),
    Debt("Debt", "Tell Niqdah whether debt needs a repayment lane in your plan."),
    SavingsGoal("Savings goal", "Create the primary goal Niqdah will track on your dashboard."),
    Categories("Spending categories", "Keep only the categories that matter and give each a monthly limit."),
    BankTracking("Bank tracking", "Configure future SMS imports without reading old inbox messages."),
    Reminders("Reminders", "Choose the nudges that should keep the plan alive."),
    Confirmation("Your plan", "Review the first version before opening the app.")
}

@Composable
fun OnboardingRoute(
    uiState: com.musab.niqdah.ui.finance.FinanceUiState,
    onComplete: (OnboardingState) -> Unit,
    onKeepExistingPlan: () -> Unit,
    onClearError: () -> Unit
) {
    if (uiState.hasExistingPlanForMigration) {
        ExistingPlanMigrationScreen(
            isSaving = uiState.isSaving,
            errorMessage = uiState.errorMessage,
            onKeepExistingPlan = onKeepExistingPlan,
            onClearError = onClearError
        )
    } else {
        OnboardingScreen(
            isSaving = uiState.isSaving,
            errorMessage = uiState.errorMessage,
            onComplete = onComplete,
            onClearError = onClearError
        )
    }
}

@Composable
private fun ExistingPlanMigrationScreen(
    isSaving: Boolean,
    errorMessage: String?,
    onKeepExistingPlan: () -> Unit,
    onClearError: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            PremiumCard {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "We found an existing Niqdah plan",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "You can keep your current categories, goals, reminders, SMS settings, balances, and transaction history. Niqdah will mark setup as complete without changing them.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ErrorBanner(message = errorMessage, onDismiss = onClearError)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    onClick = onKeepExistingPlan
                ) {
                    Text(if (isSaving) "Saving..." else "Keep existing plan")
                }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(
    isSaving: Boolean,
    errorMessage: String?,
    onComplete: (OnboardingState) -> Unit,
    onClearError: () -> Unit
) {
    val steps = OnboardingStep.entries
    var stepIndex by remember { mutableIntStateOf(0) }
    var controlFocus by remember { mutableStateOf(GoalPurpose.SAVE_FOR_GOAL) }
    var monthlyIncome by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(FinanceDefaults.DEFAULT_CURRENCY) }
    var salaryDay by remember { mutableStateOf("1") }
    var fixedExpenses by remember { mutableStateOf(FinanceDefaults.onboardingFixedExpenseTemplates()) }
    var hasDebt by remember { mutableStateOf(false) }
    var totalDebt by remember { mutableStateOf("") }
    var lenderType by remember { mutableStateOf(DebtLenderType.FRIEND_FAMILY) }
    var pressureLevel by remember { mutableStateOf(DebtPressureLevel.FLEXIBLE) }
    var installment by remember { mutableStateOf("") }
    var debtDueDay by remember { mutableStateOf("") }
    var hasGoal by remember { mutableStateOf(true) }
    var goalName by remember { mutableStateOf("Emergency fund") }
    var goalAmount by remember { mutableStateOf("") }
    var goalDate by remember { mutableStateOf("") }
    var categoryBudgets by remember { mutableStateOf(FinanceDefaults.onboardingCategoryTemplates()) }
    var dailySender by remember { mutableStateOf("") }
    var savingsSender by remember { mutableStateOf("") }
    var dailySuffix by remember { mutableStateOf("") }
    var savingsSuffix by remember { mutableStateOf("") }
    var savingsReminder by remember { mutableStateOf(true) }
    var debtReminder by remember { mutableStateOf(true) }
    var overspendingWarning by remember { mutableStateOf(true) }
    var necessaryReminder by remember { mutableStateOf(true) }

    val step = steps[stepIndex]
    val state = OnboardingState(
        controlFocus = controlFocus,
        monthlyIncome = monthlyIncome.moneyOrZero(),
        currency = currency,
        salaryDayOfMonth = salaryDay.toIntOrNull()?.coerceIn(1, 31) ?: 1,
        fixedExpenses = fixedExpenses,
        debtProfile = DebtProfile(
            hasDebt = hasDebt,
            totalDebtAmount = totalDebt.moneyOrZero(),
            lenderType = lenderType,
            pressureLevel = pressureLevel,
            monthlyInstallmentAmount = installment.moneyOrZero(),
            dueDayOfMonth = debtDueDay.toIntOrNull()?.coerceIn(1, 31)
        ),
        primarySavingsGoal = if (hasGoal) {
            val suggestion = OnboardingPlanner.monthlySavingsTarget(goalAmount.moneyOrZero(), goalDate)
            PrimarySavingsGoal(
                name = goalName.ifBlank { "Savings goal" },
                purpose = goalPurposeForName(goalName, controlFocus),
                targetAmount = goalAmount.moneyOrZero(),
                targetDate = goalDate,
                monthlyTargetSuggestion = suggestion
            )
        } else {
            null
        },
        preferences = UserPreferenceSetup(
            categoryBudgets = categoryBudgets,
            dailyUseBankSender = dailySender,
            savingsBankSender = savingsSender,
            dailyUseAccountSuffix = dailySuffix,
            savingsAccountSuffix = savingsSuffix,
            monthlySavingsReminderEnabled = savingsReminder,
            debtPaymentReminderEnabled = debtReminder,
            overspendingWarningEnabled = overspendingWarning,
            necessaryItemReminderEnabled = necessaryReminder
        )
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StoryProgress(stepIndex = stepIndex, steps = steps.size)
            ErrorBanner(message = errorMessage, onDismiss = onClearError)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatusPill(text = "Step ${stepIndex + 1} of ${steps.size}")
                Text(text = step.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(text = step.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PremiumCard(
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (step) {
                        OnboardingStep.Welcome -> WelcomeStep(controlFocus) { controlFocus = it }
                        OnboardingStep.Income -> IncomeStep(
                            monthlyIncome = monthlyIncome,
                            onMonthlyIncomeChange = { monthlyIncome = it },
                            currency = currency,
                            onCurrencyChange = { currency = it.uppercase().take(3) },
                            salaryDay = salaryDay,
                            onSalaryDayChange = { salaryDay = it.filter { char -> char.isDigit() }.take(2) }
                        )
                        OnboardingStep.FixedExpenses -> FixedExpensesStep(
                            fixedExpenses = fixedExpenses,
                            onChange = { fixedExpenses = it }
                        )
                        OnboardingStep.Debt -> DebtStep(
                            hasDebt = hasDebt,
                            onHasDebtChange = { hasDebt = it },
                            totalDebt = totalDebt,
                            onTotalDebtChange = { totalDebt = it },
                            lenderType = lenderType,
                            onLenderTypeChange = { lenderType = it },
                            pressureLevel = pressureLevel,
                            onPressureLevelChange = { pressureLevel = it },
                            installment = installment,
                            onInstallmentChange = { installment = it },
                            dueDay = debtDueDay,
                            onDueDayChange = { debtDueDay = it.filter { char -> char.isDigit() }.take(2) }
                        )
                        OnboardingStep.SavingsGoal -> SavingsGoalStep(
                            hasGoal = hasGoal,
                            onHasGoalChange = { hasGoal = it },
                            goalName = goalName,
                            onGoalNameChange = { goalName = it },
                            goalAmount = goalAmount,
                            onGoalAmountChange = { goalAmount = it },
                            goalDate = goalDate,
                            onGoalDateChange = { goalDate = it },
                            currency = currency
                        )
                        OnboardingStep.Categories -> CategoriesStep(
                            categories = categoryBudgets,
                            onChange = { categoryBudgets = it }
                        )
                        OnboardingStep.BankTracking -> BankTrackingStep(
                            dailySender = dailySender,
                            onDailySenderChange = { dailySender = it },
                            savingsSender = savingsSender,
                            onSavingsSenderChange = { savingsSender = it },
                            dailySuffix = dailySuffix,
                            onDailySuffixChange = { dailySuffix = it.filter { char -> char.isDigit() }.takeLast(4) },
                            savingsSuffix = savingsSuffix,
                            onSavingsSuffixChange = { savingsSuffix = it.filter { char -> char.isDigit() }.takeLast(4) }
                        )
                        OnboardingStep.Reminders -> RemindersStep(
                            savingsReminder = savingsReminder,
                            onSavingsReminderChange = { savingsReminder = it },
                            debtReminder = debtReminder,
                            onDebtReminderChange = { debtReminder = it },
                            overspendingWarning = overspendingWarning,
                            onOverspendingWarningChange = { overspendingWarning = it },
                            necessaryReminder = necessaryReminder,
                            onNecessaryReminderChange = { necessaryReminder = it }
                        )
                        OnboardingStep.Confirmation -> ConfirmationStep(state = state)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = stepIndex > 0 && !isSaving,
                    onClick = { stepIndex -= 1 }
                ) {
                    Text("Back")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving,
                    onClick = {
                        if (stepIndex == steps.lastIndex) {
                            onComplete(state)
                        } else {
                            stepIndex += 1
                        }
                    }
                ) {
                    Text(
                        if (isSaving) {
                            "Saving..."
                        } else if (stepIndex == steps.lastIndex) {
                            "Start using Niqdah"
                        } else {
                            "Continue"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryProgress(stepIndex: Int, steps: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(steps) { index ->
            LinearProgressIndicator(
                progress = { if (index <= stepIndex) 1f else 0f },
                modifier = Modifier.weight(1f).height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun WelcomeStep(selected: GoalPurpose, onSelected: (GoalPurpose) -> Unit) {
    SectionHeader("What do you want Niqdah to help you control?")
    GoalPurpose.entries
        .filter { it != GoalPurpose.CUSTOM }
        .forEach { purpose ->
            SelectableRow(
                title = purpose.label,
                selected = selected == purpose,
                onClick = { onSelected(purpose) }
            )
        }
}

@Composable
private fun IncomeStep(
    monthlyIncome: String,
    onMonthlyIncomeChange: (String) -> Unit,
    currency: String,
    onCurrencyChange: (String) -> Unit,
    salaryDay: String,
    onSalaryDayChange: (String) -> Unit
) {
    MoneyField("Monthly income", monthlyIncome, onMonthlyIncomeChange)
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = currency,
        onValueChange = onCurrencyChange,
        label = { Text("Currency") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
    )
    NumberField("Salary day of month", salaryDay, onSalaryDayChange)
}

@Composable
private fun FixedExpensesStep(fixedExpenses: List<FixedExpense>, onChange: (List<FixedExpense>) -> Unit) {
    SectionHeader("What are your fixed monthly costs?")
    fixedExpenses.forEachIndexed { index, expense ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = expense.name,
                onValueChange = { value ->
                    onChange(fixedExpenses.toMutableList().also { it[index] = expense.copy(name = value) })
                },
                label = { Text("Cost") },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.width(120.dp),
                value = expense.amount.takeIf { it > 0.0 }?.let { trimMoney(it) }.orEmpty(),
                onValueChange = { value ->
                    onChange(fixedExpenses.toMutableList().also { it[index] = expense.copy(amount = value.moneyOrZero()) })
                },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            IconButton(
                onClick = { onChange(fixedExpenses.filterIndexed { itemIndex, _ -> itemIndex != index }) }
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Remove fixed expense")
            }
        }
    }
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            onChange(fixedExpenses + FixedExpense(id = "custom_${fixedExpenses.size + 1}", name = "", amount = 0.0))
        }
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Add fixed cost")
    }
}

@Composable
private fun DebtStep(
    hasDebt: Boolean,
    onHasDebtChange: (Boolean) -> Unit,
    totalDebt: String,
    onTotalDebtChange: (String) -> Unit,
    lenderType: DebtLenderType,
    onLenderTypeChange: (DebtLenderType) -> Unit,
    pressureLevel: DebtPressureLevel,
    onPressureLevelChange: (DebtPressureLevel) -> Unit,
    installment: String,
    onInstallmentChange: (String) -> Unit,
    dueDay: String,
    onDueDayChange: (String) -> Unit
) {
    ToggleRow("Do you currently have debt?", hasDebt, onHasDebtChange)
    if (hasDebt) {
        MoneyField("Total debt amount", totalDebt, onTotalDebtChange)
        ChipGroup(DebtLenderType.entries, lenderType, onLenderTypeChange) { it.label }
        ChipGroup(DebtPressureLevel.entries, pressureLevel, onPressureLevelChange) { it.label }
        MoneyField("Monthly installment amount, if any", installment, onInstallmentChange)
        NumberField("Due day, if any", dueDay, onDueDayChange)
    }
}

@Composable
private fun SavingsGoalStep(
    hasGoal: Boolean,
    onHasGoalChange: (Boolean) -> Unit,
    goalName: String,
    onGoalNameChange: (String) -> Unit,
    goalAmount: String,
    onGoalAmountChange: (String) -> Unit,
    goalDate: String,
    onGoalDateChange: (String) -> Unit,
    currency: String
) {
    ToggleRow("Are you saving for something specific?", hasGoal, onHasGoalChange)
    if (hasGoal) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = goalName,
            onValueChange = onGoalNameChange,
            label = { Text("Goal name") },
            singleLine = true
        )
        MoneyField("Target amount", goalAmount, onGoalAmountChange)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = goalDate,
            onValueChange = onGoalDateChange,
            label = { Text("Target date") },
            supportingText = { Text("YYYY-MM-DD") },
            singleLine = true
        )
        val suggestion = OnboardingPlanner.monthlySavingsTarget(goalAmount.moneyOrZero(), goalDate)
        Text(
            text = if (suggestion > 0.0) {
                "Suggested monthly target: ${formatMoney(suggestion, currency)}"
            } else {
                "Add a target date to calculate a monthly target."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text(
            text = "You can still use Niqdah for safe-to-spend and budgeting. Settings can add an emergency fund later.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CategoriesStep(categories: List<CategoryBudgetSetup>, onChange: (List<CategoryBudgetSetup>) -> Unit) {
    categories.forEachIndexed { index, category ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = category.isEnabled,
                onCheckedChange = { enabled ->
                    onChange(categories.toMutableList().also { it[index] = category.copy(isEnabled = enabled) })
                }
            )
            Text(modifier = Modifier.weight(1f), text = category.name)
            OutlinedTextField(
                modifier = Modifier.width(128.dp),
                value = category.monthlyBudget.takeIf { it > 0.0 }?.let { trimMoney(it) }.orEmpty(),
                onValueChange = { value ->
                    onChange(categories.toMutableList().also { it[index] = category.copy(monthlyBudget = value.moneyOrZero()) })
                },
                label = { Text("Budget") },
                enabled = category.isEnabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
    }
}

@Composable
private fun BankTrackingStep(
    dailySender: String,
    onDailySenderChange: (String) -> Unit,
    savingsSender: String,
    onSavingsSenderChange: (String) -> Unit,
    dailySuffix: String,
    onDailySuffixChange: (String) -> Unit,
    savingsSuffix: String,
    onSavingsSuffixChange: (String) -> Unit
) {
    SectionHeader("Do you want Niqdah to help read new bank SMS messages?")
    Text(
        text = "Only new SMS after permission. Only configured senders. No old inbox reading. No SMS sent to AI.",
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = dailySender,
        onValueChange = onDailySenderChange,
        label = { Text("Daily-use bank sender") },
        singleLine = true
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = savingsSender,
        onValueChange = onSavingsSenderChange,
        label = { Text("Savings bank sender") },
        singleLine = true
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = dailySuffix,
            onValueChange = onDailySuffixChange,
            label = { Text("Daily suffix") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = savingsSuffix,
            onValueChange = onSavingsSuffixChange,
            label = { Text("Savings suffix") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
private fun RemindersStep(
    savingsReminder: Boolean,
    onSavingsReminderChange: (Boolean) -> Unit,
    debtReminder: Boolean,
    onDebtReminderChange: (Boolean) -> Unit,
    overspendingWarning: Boolean,
    onOverspendingWarningChange: (Boolean) -> Unit,
    necessaryReminder: Boolean,
    onNecessaryReminderChange: (Boolean) -> Unit
) {
    ToggleRow("Monthly savings reminder", savingsReminder, onSavingsReminderChange)
    ToggleRow("Debt payment reminder", debtReminder, onDebtReminderChange)
    ToggleRow("Overspending warning", overspendingWarning, onOverspendingWarningChange)
    ToggleRow("Necessary item reminders", necessaryReminder, onNecessaryReminderChange)
}

@Composable
private fun ConfirmationStep(state: OnboardingState) {
    val plan = OnboardingPlanner.buildPlan(uid = "preview", state = state)
    val currency = plan.profile.currency
    val fixedTotal = plan.categories.filter { it.type == com.musab.niqdah.domain.finance.CategoryType.FIXED }
        .sumOf { it.monthlyBudget }
    val safeToSpend = plan.profile.salary -
        fixedTotal -
        plan.profile.monthlySavingsTarget -
        plan.debt.monthlyAutoReduction
    SectionHeader("Generated first plan")
    SummaryLine("Income", formatMoney(plan.profile.salary, currency))
    SummaryLine("Fixed expenses", formatMoney(fixedTotal, currency))
    SummaryLine("Safe-to-spend estimate", formatMoney(safeToSpend, currency))
    SummaryLine("Debt plan", if (plan.debt.startingAmount > 0.0) formatMoney(plan.debt.startingAmount, currency) else "No debt")
    SummaryLine("Primary goal", plan.goals.firstOrNull()?.name ?: "No primary goal")
    SummaryLine("Monthly savings target", formatMoney(plan.profile.monthlySavingsTarget, currency))
    SummaryLine("Category budgets", "${plan.categories.count { it.monthlyBudget >= 0.0 }} active")
    SummaryLine("Reminders", listOfNotNull(
        "savings".takeIf { plan.reminderSettings.isMonthlySavingsReminderEnabled },
        "debt".takeIf { state.preferences.debtPaymentReminderEnabled },
        "overspending".takeIf { plan.reminderSettings.areOverspendingWarningsEnabled }
    ).ifEmpty { listOf("off") }.joinToString(", "))
}

@Composable
private fun SelectableRow(title: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = title,
            textAlign = TextAlign.Start,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        if (selected) StatusPill(text = "Selected")
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(modifier = Modifier.weight(1f), text = title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> ChipGroup(items: List<T>, selected: T, onSelected: (T) -> Unit, label: (T) -> String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelected(item) },
                label = { Text(label(item)) }
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(modifier = Modifier.weight(0.46f), text = label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(modifier = Modifier.weight(0.54f), text = value, fontWeight = FontWeight.SemiBold)
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
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

private fun String.moneyOrZero(): Double =
    trim().replace(",", "").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

private fun trimMoney(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private fun goalPurposeForName(name: String, fallback: GoalPurpose): GoalPurpose =
    when {
        name.contains("marriage", ignoreCase = true) || name.contains("family", ignoreCase = true) ->
            GoalPurpose.MARRIAGE_FAMILY
        name.contains("emergency", ignoreCase = true) -> GoalPurpose.EMERGENCY_FUND
        else -> fallback.takeIf { it != GoalPurpose.GENERAL_BUDGETING } ?: GoalPurpose.CUSTOM
    }
