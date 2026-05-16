package com.musab.niqdah.data.finance

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.musab.niqdah.MainActivity
import com.musab.niqdah.core.firebase.FirebaseProvider
import com.musab.niqdah.data.firestore.FirestoreCollections
import com.musab.niqdah.domain.finance.CategoryType
import com.musab.niqdah.domain.finance.ExpenseTransaction
import com.musab.niqdah.domain.finance.FinanceDates
import com.musab.niqdah.domain.finance.FinanceDefaults
import com.musab.niqdah.domain.finance.GoalPurpose
import com.musab.niqdah.domain.finance.NecessityLevel
import com.musab.niqdah.domain.finance.SavingsGoal
import com.musab.niqdah.domain.finance.effectiveMinorUnits
import com.musab.niqdah.domain.finance.majorToMinorUnits
import com.musab.niqdah.domain.finance.minorUnitsToMajor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SavingsFollowUpNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != ACTION_SUPPRESS) return
        val uid = intent.getStringExtra(EXTRA_UID).orEmpty()
        val cycleMonth = intent.getStringExtra(EXTRA_CYCLE_MONTH).orEmpty()
        if (uid.isBlank() || cycleMonth.isBlank()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        receiverScope.launch {
            try {
                val db = FirebaseProvider.firestore(appContext) ?: return@launch
                when (action) {
                    ACTION_SUPPRESS -> suppressCycle(db = db, uid = uid, cycleMonth = cycleMonth)
                }
                appContext.getSystemService(NotificationManager::class.java)
                    .cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun suppressCycle(db: FirebaseFirestore, uid: String, cycleMonth: String) {
        db.collection(FirestoreCollections.USERS)
            .document(uid)
            .collection(FirestoreCollections.FINANCE)
            .document("reminderSettings")
            .set(
                mapOf(
                    "suppressedPostSalarySavingsFollowUpCycleMonth" to cycleMonth,
                    "updatedAtMillis" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
            .awaitValue()
    }

    private suspend fun <T> Task<T>.awaitValue(): T =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("Firestore request failed.")
                    )
                }
            }
        }

    companion object {
        private const val ACTION_SUPPRESS = "com.musab.niqdah.action.SUPPRESS_SAVINGS_FOLLOW_UP"
        private const val EXTRA_UID = "uid"
        private const val EXTRA_CYCLE_MONTH = "cycleMonth"
        private const val EXTRA_REMAINING_AMOUNT_MINOR = "remainingAmountMinor"
        private const val EXTRA_CURRENCY = "currency"
        private const val EXTRA_NOTIFICATION_ID = "notificationId"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        internal fun notificationActionTitles(): List<String> =
            listOf("Review", "Skip this month")

        internal fun actions(
            context: Context,
            uid: String,
            cycleMonth: String,
            remainingAmountMinor: Long,
            currency: String,
            notificationId: Int
        ): List<DisciplineNotificationAction> {
            val appContext = context.applicationContext
            val reviewIntent = PendingIntent.getActivity(
                appContext,
                "savings-review-$cycleMonth".hashCode(),
                Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return listOf(
                DisciplineNotificationAction(title = "Review", intent = reviewIntent),
                DisciplineNotificationAction(
                    title = "Skip this month",
                    intent = actionIntent(
                        appContext,
                        ACTION_SUPPRESS,
                        uid,
                        cycleMonth,
                        remainingAmountMinor,
                        currency,
                        notificationId
                    )
                )
            )
        }

        private fun actionIntent(
            context: Context,
            action: String,
            uid: String,
            cycleMonth: String,
            remainingAmountMinor: Long,
            currency: String,
            notificationId: Int
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                "$action-$uid-$cycleMonth".hashCode(),
                Intent(context, SavingsFollowUpNotificationActionReceiver::class.java).apply {
                    this.action = action
                    putExtra(EXTRA_UID, uid)
                    putExtra(EXTRA_CYCLE_MONTH, cycleMonth)
                    putExtra(EXTRA_REMAINING_AMOUNT_MINOR, remainingAmountMinor)
                    putExtra(EXTRA_CURRENCY, currency)
                    putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
