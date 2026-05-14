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
import com.musab.niqdah.domain.finance.AccountBalanceStatus
import com.musab.niqdah.domain.finance.BudgetCategory
import com.musab.niqdah.domain.finance.CategoryBudgetWarning
import com.musab.niqdah.domain.finance.DepositType
import com.musab.niqdah.domain.finance.DisciplineCalculator
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.IncomeTransaction
import com.musab.niqdah.domain.finance.NecessaryItemDue
import com.musab.niqdah.domain.finance.SavingsGoal
import com.musab.niqdah.domain.finance.formatMinorUnits
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

    private fun AiFinanceContext.toPayload(): Map<String, Any?> {
        val data = financeData
        val categoryById = data.categories.associateBy { it.id }
        val recentTransactions = data.transactions
            .sortedByDescending { it.occurredAtMillis }
            .take(recentTransactionLimit)
            .map { it.toPayload(categoryById[it.categoryId]?.name ?: "Uncategorized") }
        val recentIncomeTransactions = data.incomeTransactions
            .sortedByDescending { it.occurredAtMillis }
            .take(recentTransactionLimit)
            .map { it.toPayload() }
        val monthDeposits = data.incomeTransactions
            .filter { it.yearMonth == currentMonthSnapshot.yearMonth }
        val salaryDeposits = monthDeposits.filter { it.depositType == DepositType.SALARY }
        val disciplineStatus = DisciplineCalculator.status(
            data = data,
            yearMonth = currentMonthSnapshot.yearMonth
        )

        return mapOf(
            "profile" to mapOf(
                "currency" to data.profile.currency,
                "salary" to data.profile.salary,
                "extraIncome" to data.profile.extraIncome,
                "monthlySavingsTarget" to data.profile.monthlySavingsTarget
            ),
            "accountBalances" to mapOf(
                "dailyUse" to data.latestDailyUseBalanceStatus?.toPayload(),
                "savings" to data.latestSavingsBalanceStatus?.toPayload()
            ),
            "salaryAndDepositsThisMonth" to mapOf(
                "salaryRecorded" to salaryDeposits.isNotEmpty(),
                "depositCount" to monthDeposits.size,
                "totalDeposits" to monthDeposits.sumOf { it.amount },
                "items" to monthDeposits.map { it.toPayload() }
            ),
            "accountLedgerSummary" to data.accountLedgerEntries
                .sortedByDescending { it.createdAtMillis }
                .take(8)
                .map {
                    mapOf(
                        "accountKind" to it.accountKind.name,
                        "eventType" to it.eventType.name,
                        "amount" to formatMinorUnits(it.amountMinor, it.currency),
                        "balanceAfter" to it.balanceAfterMinor?.let { balance -> formatMinorUnits(balance, it.currency) },
                        "confidence" to it.confidence.name,
                        "source" to it.source.name,
                        "createdAtMillis" to it.createdAtMillis,
                        "note" to it.note
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
            "primaryGoalProgress" to data.primaryGoal?.let { goal ->
                mapOf(
                    "name" to goal.name,
                    "savedAmount" to goal.savedAmount,
                    "targetAmount" to goal.targetAmount
                )
            },
            "disciplineStatus" to mapOf(
                "currentSavingsProgress" to mapOf(
                    "savedThisMonth" to disciplineStatus.savingsTarget.savedThisMonth,
                    "targetAmount" to disciplineStatus.savingsTarget.targetAmount,
                    "shortfall" to disciplineStatus.savingsTarget.shortfall,
                    "progress" to disciplineStatus.savingsTarget.progress
                ),
                "categoryWarnings" to disciplineStatus.categoryWarnings.map { it.toPayload() },
                "overspentCategories" to disciplineStatus.categoryWarnings
                    .filter { it.percentUsed > 1.0 }
                    .map { it.toPayload() },
                "necessaryItemsDue" to disciplineStatus.necessaryItemsDueSoon.map { it.toPayload() },
                "avoidSpendingThisMonth" to disciplineStatus.avoidSpendingThisMonth,
                "safeToSpendAmount" to disciplineStatus.safeToSpendAmount,
                "januaryCountdown" to mapOf(
                    "targetDate" to disciplineStatus.januaryCountdown.targetDate,
                    "daysRemaining" to disciplineStatus.januaryCountdown.daysRemaining,
                    "monthsRemaining" to disciplineStatus.januaryCountdown.monthsRemaining,
                    "currentSaved" to disciplineStatus.januaryCountdown.currentSaved,
                    "targetAmount" to disciplineStatus.januaryCountdown.targetAmount,
                    "requiredMonthlySavings" to disciplineStatus.januaryCountdown.requiredMonthlySavings
                )
            ),
            "recentTransactions" to recentTransactions,
            "recentIncomeTransactions" to recentIncomeTransactions
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

    private fun CategoryBudgetWarning.toPayload(): Map<String, Any> =
        mapOf(
            "categoryId" to category.id,
            "categoryName" to category.name,
            "spent" to spent,
            "budget" to budget,
            "percentUsed" to percentUsed,
            "level" to level.name,
            "message" to message
        )

    private fun NecessaryItemDue.toPayload(): Map<String, Any?> =
        mapOf(
            "id" to item.id,
            "title" to item.title,
            "amount" to item.amount,
            "dueDateMillis" to dueDateMillis,
            "daysUntilDue" to daysUntilDue,
            "recurrence" to item.recurrence.label,
            "status" to item.status.label
        )

    private fun AccountBalanceStatus.toPayload(): Map<String, Any> =
        mapOf(
            "accountKind" to accountKind.name,
            "accountSuffix" to accountSuffix,
            "amount" to formatMinorUnits(amountMinor, currency),
            "currency" to currency,
            "confidence" to confidence.name,
            "lastUpdatedMillis" to lastUpdatedMillis,
            "source" to source.name,
            "note" to note
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

    private fun IncomeTransaction.toPayload(): Map<String, Any> =
        mapOf(
            "amount" to amount,
            "currency" to currency,
            "source" to source,
            "note" to note,
            "depositType" to depositType.name,
            "yearMonth" to yearMonth,
            "occurredAtMillis" to occurredAtMillis
        )
}
