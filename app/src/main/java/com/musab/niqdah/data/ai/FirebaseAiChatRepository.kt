package com.musab.niqdah.data.ai

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.GetTokenResult
import com.musab.niqdah.core.firebase.FirebaseProvider
import com.musab.niqdah.domain.ai.AiChatAuthRequiredException
import com.musab.niqdah.domain.ai.AiChatMessage
import com.musab.niqdah.domain.ai.AiChatRepository
import com.musab.niqdah.domain.ai.AiChatRole
import com.musab.niqdah.domain.ai.AiChatTokenVerificationException
import com.musab.niqdah.domain.ai.AiFinanceContext
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.SavingsGoal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseAiChatRepository(context: Context) : AiChatRepository {
    private companion object {
        const val TAG = "NiqdahAiChat"
        const val AI_FUNCTION_URL =
            "https://us-central1-niqdah.cloudfunctions.net/askNiqdahHttp"
    }

    private val appContext = context.applicationContext
    private val auth by lazy { FirebaseProvider.auth(appContext) }

    override suspend fun askNiqdah(
        message: String,
        history: List<AiChatMessage>,
        context: AiFinanceContext
    ): Result<String> {
        val currentUser = auth?.currentUser
            ?: return Result.failure(
                AiChatAuthRequiredException("Please log in again before using AI Chat.")
            )

        val tokenResult = runCatching { currentUser.getIdToken(true).awaitValue() }
            .getOrElse {
                Log.d(TAG, "askNiqdahHttp auth uid=${currentUser.uid}, tokenExists=false")
                return Result.failure(
                    AiChatAuthRequiredException("Please log in again before using AI Chat.")
                )
            }
        val token = tokenResult.token.orEmpty()
        val tokenExists = token.isNotBlank()
        Log.d(TAG, "askNiqdahHttp auth uid=${currentUser.uid}, tokenExists=$tokenExists")
        if (!tokenExists) {
            return Result.failure(
                AiChatAuthRequiredException("Please log in again before using AI Chat.")
            )
        }

        val payload = JSONObject(
            mapOf(
                "message" to message,
                "history" to JSONArray(history.takeLast(8).map { JSONObject(it.toPayload()) }),
                "financeContext" to JSONObject(context.toPayload())
            )
        )

        return withContext(Dispatchers.IO) {
            postToNiqdah(token = token, payload = payload)
        }
    }

    private fun postToNiqdah(token: String, payload: JSONObject): Result<String> {
        val connection = (URL(AI_FUNCTION_URL).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return runCatching {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val statusCode = connection.responseCode
            val responseBody = connection.readBody(statusCode)
            when (statusCode) {
                HttpURLConnection.HTTP_OK -> {
                    val json = JSONObject(responseBody)
                    json.optString("reply").takeIf { it.isNotBlank() }
                        ?: error("Niqdah did not return a reply.")
                }
                HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    throw AiChatTokenVerificationException()
                }
                else -> {
                    val error = runCatching {
                        JSONObject(responseBody).optString("error")
                    }.getOrNull().orEmpty()
                    throw IllegalStateException(
                        error.ifBlank { "Niqdah AI is unavailable right now. Try again shortly." }
                    )
                }
            }
        }.also {
            connection.disconnect()
        }
    }

    private suspend fun Task<GetTokenResult>.awaitValue(): GetTokenResult =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful && task.result != null) {
                    continuation.resume(task.result)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("Firebase token refresh failed.")
                    )
                }
            }
        }

    private fun HttpsURLConnection.readBody(statusCode: Int): String {
        val stream = if (statusCode in 200..299) inputStream else errorStream ?: inputStream
        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
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
            "profile" to mapOf(
                "currency" to data.profile.currency,
                "salary" to data.profile.salary,
                "extraIncome" to data.profile.extraIncome,
                "monthlySavingsTarget" to data.profile.monthlySavingsTarget
            ),
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
