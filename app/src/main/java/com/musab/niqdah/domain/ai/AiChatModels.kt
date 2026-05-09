package com.musab.niqdah.domain.ai

import com.musab.niqdah.domain.finance.FinanceData
import com.musab.niqdah.domain.finance.MonthlySnapshot
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.ParsedBankMessageConfidence
import com.musab.niqdah.domain.finance.ParsedBankMessageType

data class AiChatMessage(
    val id: String,
    val role: AiChatRole,
    val content: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

enum class AiChatRole {
    USER,
    ASSISTANT
}

data class AiFinanceContext(
    val financeData: FinanceData,
    val currentMonthSnapshot: MonthlySnapshot,
    val recentTransactionLimit: Int = 8
)

data class AiFinanceDraftAction(
    val type: ParsedBankMessageType,
    val amount: Double?,
    val currency: String,
    val categoryId: String?,
    val categoryName: String,
    val necessity: NecessityLevel,
    val description: String,
    val dateInput: String,
    val confidence: ParsedBankMessageConfidence,
    val senderName: String,
    val originalText: String
)

interface AiChatRepository {
    suspend fun askNiqdah(
        message: String,
        history: List<AiChatMessage>,
        context: AiFinanceContext
    ): Result<String>
}

class AiChatAuthRequiredException(message: String) : Exception(message)

class AiChatBackendUnauthenticatedException : Exception(
    "Your login session was not attached to the AI request. Please log out and log in again."
)

class AiChatTokenVerificationException : Exception(
    "Your login token could not be verified. Please log out and log in again."
)
