package com.musab.niqdah.domain.ai

import com.musab.niqdah.domain.finance.FinanceData
import com.musab.niqdah.domain.finance.MonthlySnapshot

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

interface AiChatRepository {
    suspend fun askNiqdah(
        message: String,
        history: List<AiChatMessage>,
        context: AiFinanceContext
    ): Result<String>
}
