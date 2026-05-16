package com.musab.niqdah.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musab.niqdah.domain.ai.AiFinanceDraftAction
import com.musab.niqdah.domain.ai.AiChatMessage
import com.musab.niqdah.domain.ai.AiChatRole
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import com.musab.niqdah.ui.finance.ErrorBanner
import com.musab.niqdah.ui.finance.FinanceHeader
import com.musab.niqdah.ui.finance.FinanceUiState
import com.musab.niqdah.ui.finance.PremiumCard
import com.musab.niqdah.ui.finance.PrimaryActionButton
import com.musab.niqdah.ui.finance.SecondaryActionButton
import com.musab.niqdah.ui.finance.StatusPill
import com.musab.niqdah.ui.finance.formatInputMoney
import com.musab.niqdah.ui.finance.formatMoney

@Composable
fun AiChatScreen(
    chatUiState: AiChatUiState,
    financeUiState: FinanceUiState,
    padding: PaddingValues,
    onSendMessage: (String, FinanceUiState) -> Unit,
    onSaveDraft: (String, AiFinanceDraftAction) -> Unit,
    onUpdateDraft: (String, AiFinanceDraftAction) -> Unit,
    onCancelDraft: (String) -> Unit,
    onClearError: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                FinanceHeader(
                    title = "AI Chat",
                    subtitle = "Ask for purchase checks, budget adjustments, and monthly focus."
                )
            }
            item { ErrorBanner(message = chatUiState.errorMessage, onDismiss = onClearError) }
            item {
                SuggestedPrompts(
                    enabled = !chatUiState.isSending,
                    onPrompt = { prompt -> onSendMessage(prompt, financeUiState) }
                )
            }
            items(chatUiState.messages, key = { it.id }) { message ->
                ChatBubble(message = message)
                chatUiState.draftActions[message.id]?.let { draftAction ->
                    AiFinanceDraftCard(
                        messageId = message.id,
                        draftAction = draftAction,
                        categories = financeUiState.data.categories,
                        isSaved = message.id in chatUiState.savedDraftMessageIds,
                        isSaving = financeUiState.isSaving,
                        errorMessage = chatUiState.draftErrors[message.id],
                        onSave = onSaveDraft,
                        onUpdate = onUpdateDraft,
                        onCancel = onCancelDraft
                    )
                }
            }
            if (chatUiState.isSending) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Niqdah is checking the plan")
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = input,
                    onValueChange = { input = it },
                    enabled = !chatUiState.isSending,
                    label = { Text("Ask Niqdah") },
                    minLines = 1,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val text = input
                            input = ""
                            onSendMessage(text, financeUiState)
                        }
                    )
                )
                IconButton(
                    enabled = input.isNotBlank() && !chatUiState.isSending,
                    onClick = {
                        val text = input
                        input = ""
                        onSendMessage(text, financeUiState)
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send message"
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedPrompts(enabled: Boolean, onPrompt: (String) -> Unit) {
    val prompts = listOf(
        "Can I buy this?",
        "What should I focus on this month?",
        "Summarize my spending",
        "Am I on track?"
    )
    PremiumCard {
        Text(
            text = "Suggested prompts",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        prompts.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { prompt ->
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        onClick = { onPrompt(prompt) }
                    ) {
                        Text(prompt, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AiFinanceDraftCard(
    messageId: String,
    draftAction: AiFinanceDraftAction,
    categories: List<BudgetCategory>,
    isSaved: Boolean,
    isSaving: Boolean,
    errorMessage: String?,
    onSave: (String, AiFinanceDraftAction) -> Unit,
    onUpdate: (String, AiFinanceDraftAction) -> Unit,
    onCancel: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }

    PremiumCard(modifier = Modifier.padding(top = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Draft financial action", style = MaterialTheme.typography.titleMedium)
                StatusPill(text = draftAction.confidence.label, isWarning = draftAction.confidence.name != "HIGH")
            }
            DraftLine(label = "Type", value = draftAction.type.label)
            DraftLine(
                label = "Amount",
                value = draftAction.amount?.let { formatMoney(it, draftAction.currency) } ?: "Missing"
            )
            DraftLine(label = "Category", value = draftAction.categoryName)
            DraftLine(label = "Necessity", value = draftAction.necessity.label)
            DraftLine(label = "Description", value = draftAction.description.ifBlank { "Imported bank message" })
            DraftLine(label = "Date", value = draftAction.dateInput)

            if (isSaved) {
                Text(
                    text = "Saved successfully.",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryActionButton(
                        modifier = Modifier.weight(1f),
                        label = if (isSaving) "Saving..." else "Save",
                        enabled = !isSaving,
                        onClick = { onSave(messageId, draftAction) }
                    )
                    SecondaryActionButton(
                        modifier = Modifier.weight(1f),
                        label = "Edit",
                        onClick = { isEditing = true }
                    )
                    TextButton(onClick = { onCancel(messageId) }) {
                        Text("Cancel")
                    }
                }
            }
    }

    if (isEditing) {
        AiFinanceDraftEditDialog(
            draftAction = draftAction,
            categories = categories,
            onDismiss = { isEditing = false },
            onSave = { updatedDraft ->
                onUpdate(messageId, updatedDraft)
                isEditing = false
            }
        )
    }
}

@Composable
private fun DraftLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.58f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AiFinanceDraftEditDialog(
    draftAction: AiFinanceDraftAction,
    categories: List<BudgetCategory>,
    onDismiss: () -> Unit,
    onSave: (AiFinanceDraftAction) -> Unit
) {
    var amount by remember(draftAction) {
        mutableStateOf(draftAction.amount?.let { formatInputMoney(it) }.orEmpty())
    }
    var categoryId by remember(draftAction) { mutableStateOf(draftAction.categoryId) }
    var necessity by remember(draftAction) { mutableStateOf(draftAction.necessity) }
    var description by remember(draftAction) { mutableStateOf(draftAction.description) }
    var dateInput by remember(draftAction) { mutableStateOf(draftAction.dateInput) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit draft") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                if (draftAction.type == ParsedBankMessageType.EXPENSE) {
                    DraftCategoryPicker(
                        categories = categories,
                        selectedCategoryId = categoryId,
                        onCategorySelected = { categoryId = it }
                    )
                }
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    label = { Text("Date") },
                    supportingText = { Text("YYYY-MM-DD") },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NecessityLevel.entries.forEach { level ->
                        FilterChip(
                            selected = necessity == level,
                            onClick = { necessity = level },
                            label = { Text(level.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val selectedCategory = categories.firstOrNull { it.id == categoryId }
                    onSave(
                        draftAction.copy(
                            amount = amount.toDraftAmountOrNull(),
                            categoryId = categoryId,
                            categoryName = selectedCategory?.name ?: draftAction.categoryName,
                            necessity = necessity,
                            description = description,
                            dateInput = dateInput
                        )
                    )
                }
            ) {
                Text("Apply")
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
private fun DraftCategoryPicker(
    categories: List<BudgetCategory>,
    selectedCategoryId: String?,
    onCategorySelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val categoryName = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Choose category"

    Box {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            onClick = { expanded = true }
        ) {
            Text(
                text = "Category: $categoryName",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(imageVector = Icons.Rounded.KeyboardArrowDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text("${category.name} (${category.type.label})") },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun String.toDraftAmountOrNull(): Double? =
    trim().replace(",", "").toDoubleOrNull()

@Composable
private fun ChatBubble(message: AiChatMessage) {
    val isUser = message.role == AiChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = if (isUser) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            tonalElevation = if (isUser) 0.dp else 1.dp,
            shadowElevation = if (isUser) 0.dp else 2.dp
        ) {
            Text(
                modifier = Modifier.padding(14.dp),
                text = message.content,
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
