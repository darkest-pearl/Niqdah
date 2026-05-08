package com.musab.niqdah.domain.finance

import kotlinx.coroutines.flow.Flow

interface FinanceRepository {
    suspend fun ensureDefaults()
    fun observeFinanceData(): Flow<FinanceData>
    suspend fun upsertProfile(profile: UserProfile)
    suspend fun upsertCategory(category: BudgetCategory)
    suspend fun upsertTransaction(transaction: ExpenseTransaction)
    suspend fun deleteTransaction(transactionId: String)
    suspend fun upsertGoal(goal: SavingsGoal)
    suspend fun upsertDebt(debt: DebtTracker)
    suspend fun saveMonthlySnapshot(snapshot: MonthlySnapshot)
}
