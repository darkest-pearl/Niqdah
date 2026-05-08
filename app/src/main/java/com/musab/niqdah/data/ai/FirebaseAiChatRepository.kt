package com.musab.niqdah.data.ai

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableResult
import com.musab.niqdah.core.firebase.FirebaseProvider
import com.musab.niqdah.domain.ai.AiChatMessage
import com.musab.niqdah.domain.ai.AiChatRepository
import com.musab.niqdah.domain.ai.AiChatRole
import com.musab.niqdah.domain.ai.AiFinanceContext
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.FinanceData
import com.musab.niqdah.domain.finance.SavingsGoal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseAiChatRepository(context: Context) : AiChatRepository {
    private val appContext = context.applicationContext
    private val functions: FirebaseFunctions? by lazy { FirebaseProvider.functions(appContext) }

    override suspend fun askNiqdah(
        message: String,
        history: List<AiChatMessage>,
        context: AiFinanceContext
    ): Result<String> {
        val firebaseFunctions = functions
            ?: return Result.failure(
                IllegalStateException("Firebase Functions is not configured yet.")
            )

        val payload = mapOf(
            "message" to message,
            "history" to history.takeLast(8).map { it.toPayload() },
            "financeContext" to context.toPayload()
        )

        return firebaseFunctions
            .getHttpsCallable("askNiqdah")
            .call(payload)
            .awaitResult()
            .mapCatching { result ->
                val data = result.getData() as? Map<*, *>
                    ?: error("The AI response was not readable.")
                data["reply"] as? String
                    ?: error("Niqdah did not return a reply.")
            }
    }

    private suspend fun Task<HttpsCallableResult>.awaitResult(): Result<HttpsCallableResult> =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful && task.result != null) {
                    continuation.resume(Result.success(task.result))
                } else {
                    continuation.resume(
                        Result.failure(
                            task.exception ?: IllegalStateException("Cloud Function request failed.")
                        )
                    )
                }
            }
        }

    private fun AiChatMessage.toPayload(): Map<String, String> =
        mapOf(
            "role" to if (role == AiChatRole.ASSISTANT) "assistant" else "user",
            "content" to content
        )

    private fun AiFinanceContext.toPayload(): Map<String, Any> {
        val data = financeData
        val categoryById = data.categories.associateBy { it.id }
        val recentTransactions = data.transactions
            .sortedByDescending { it.occurredAtMillis }
            .take(recentTransactionLimit)
            .map { it.toPayload(categoryById[it.categoryId]?.name ?: "Uncategorized") }

        return mapOf(
            "profile" to data.profile.let {
                mapOf(
                    "currency" to it.currency,
                    "salary" to it.salary,
                    "extraIncome" to it.extraIncome,
                    "monthlySavingsTarget" to it.monthlySavingsTarget
                )
            },
            "debt" to mapOf(
                "startingAmount" to data.debt.startingAmount,
                "remainingAmount" to data.debt.remainingAmount,
                "monthlyAutoReduction" to data.debt.monthlyAutoReduction
            ),
            "currentMonthSnapshot" to mapOf(
                "yearMonth" to currentMonthSnapshot.yearMonth,
                "totalIncome" to currentMonthSnapshot.totalIncome,
                "totalSpent" to currentMonthSnapshot.totalSpent,
                "remainingSafeToSpend" to currentMonthSnapshot.remainingSafeToSpend,
                "marriageFundSaved" to currentMonthSnapshot.marriageFundSaved,
                "marriageFundTarget" to currentMonthSnapshot.marriageFundTarget,
                "debtRemaining" to currentMonthSnapshot.debtRemaining,
                "debtStarting" to currentMonthSnapshot.debtStarting,
                "healthSummary" to currentMonthSnapshot.healthSummary
            ),
            "categoryBudgets" to data.categories.map { it.toPayload() },
            "savingsGoals" to data.goals.map { it.toPayload() },
            "recentTransactions" to recentTransactions
        )
    }

    private fun BudgetCategory.toPayload(): Map<String, Any> =
        mapOf(
            "id" to id,
            "name" to name,
            "monthlyBudget" to monthlyBudget,
            "type" to type.label
        )

    private fun SavingsGoal.toPayload(): Map<String, Any> =
        mapOf(
            "id" to id,
            "name" to name,
            "targetAmount" to targetAmount,
            "savedAmount" to savedAmount
        )

    private fun ExpenseTransaction.toPayload(categoryName: String): Map<String, Any> =
        mapOf(
            "categoryId" to categoryId,
            "categoryName" to categoryName,
            "amount" to amount,
            "note" to note,
            "necessity" to necessity.label,
            "yearMonth" to yearMonth,
            "occurredAtMillis" to occurredAtMillis
        )
}
