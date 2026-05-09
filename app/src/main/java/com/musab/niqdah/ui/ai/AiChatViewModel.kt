package com.musab.niqdah.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musab.niqdah.domain.ai.AiFinanceDraftAction
import com.musab.niqdah.domain.ai.AiChatMessage
import com.musab.niqdah.domain.ai.AiChatRepository
import com.musab.niqdah.domain.ai.AiChatRole
import com.musab.niqdah.domain.ai.AiChatBackendUnauthenticatedException
import com.musab.niqdah.domain.ai.AiChatTokenVerificationException
import com.musab.niqdah.domain.ai.AiFinanceContext
import com.musab.niqdah.ui.finance.FinanceUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AiChatUiState(
    val messages: List<AiChatMessage> = listOf(
        AiChatMessage(
            id = "welcome",
            role = AiChatRole.ASSISTANT,
            content = "Ask me if a purchase fits the January marriage plan, or ask for a quick budget adjustment."
        )
    ),
    val draftActions: Map<String, AiFinanceDraftAction> = emptyMap(),
    val savedDraftMessageIds: Set<String> = emptySet(),
    val draftErrors: Map<String, String> = emptyMap(),
    val isSending: Boolean = false,
    val errorMessage: String? = null
)

class AiChatViewModel(
    private val repository: AiChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState = _uiState.asStateFlow()

    fun sendMessage(
        text: String,
        financeUiState: FinanceUiState,
        draftActionFactory: (String) -> AiFinanceDraftAction?
    ) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (financeUiState.isLoading) {
            _uiState.update {
                it.copy(errorMessage = "Wait for your finance data to load, then ask Niqdah again.")
            }
            return
        }

        val userMessage = AiChatMessage(
            id = UUID.randomUUID().toString(),
            role = AiChatRole.USER,
            content = trimmed
        )
        val history = _uiState.value.messages
        val draftAction = draftActionFactory(trimmed)

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                isSending = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val context = AiFinanceContext(
                financeData = financeUiState.data,
                currentMonthSnapshot = financeUiState.dashboard.snapshot
            )
            repository.askNiqdah(
                message = trimmed,
                history = history,
                context = context
            ).onSuccess { reply ->
                val assistantMessage = AiChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = AiChatRole.ASSISTANT,
                    content = reply
                )
                _uiState.update {
                    it.copy(
                        messages = it.messages + assistantMessage,
                        draftActions = if (draftAction == null) {
                            it.draftActions
                        } else {
                            it.draftActions + (assistantMessage.id to draftAction)
                        },
                        isSending = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = error.friendlyChatMessage()
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun updateDraftAction(messageId: String, draftAction: AiFinanceDraftAction) {
        _uiState.update {
            it.copy(
                draftActions = it.draftActions + (messageId to draftAction),
                draftErrors = it.draftErrors - messageId
            )
        }
    }

    fun cancelDraftAction(messageId: String) {
        _uiState.update {
            it.copy(
                draftActions = it.draftActions - messageId,
                savedDraftMessageIds = it.savedDraftMessageIds - messageId,
                draftErrors = it.draftErrors - messageId
            )
        }
    }

    fun markDraftActionSaved(messageId: String) {
        _uiState.update {
            it.copy(
                savedDraftMessageIds = it.savedDraftMessageIds + messageId,
                draftErrors = it.draftErrors - messageId
            )
        }
    }

    fun setDraftActionError(messageId: String, message: String) {
        _uiState.update {
            it.copy(draftErrors = it.draftErrors + (messageId to message))
        }
    }

    private fun Throwable.friendlyChatMessage(): String =
        when (this) {
            is AiChatBackendUnauthenticatedException ->
                "Your login session was not attached to the AI request. Please log out and log in again."
            is AiChatTokenVerificationException ->
                "Your login token could not be verified. Please log out and log in again."
            else -> message?.takeIf { it.isNotBlank() }
                ?: "Niqdah AI is unavailable right now. Try again shortly."
        }

    class Factory(
        private val repository: AiChatRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AiChatViewModel::class.java)) {
                return AiChatViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
