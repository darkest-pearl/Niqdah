package com.musab.niqdah.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.musab.niqdah.core.firebase.FirebaseProvider
import com.musab.niqdah.data.finance.BankSmsImportProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        receiverScope.launch {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                val sender = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()
                val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
                if (sender.isNotBlank() && body.isNotBlank()) {
                    val uid = FirebaseProvider.auth(appContext)?.currentUser?.uid
                    if (!uid.isNullOrBlank()) {
                        BankSmsImportProcessor(appContext, uid).processIncomingSms(
                            senderName = sender,
                            messageBody = body,
                            receivedAtMillis = System.currentTimeMillis()
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
