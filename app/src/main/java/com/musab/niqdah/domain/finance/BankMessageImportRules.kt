package com.musab.niqdah.domain.finance

import java.security.MessageDigest
import java.util.Locale

object BankMessageImportRules {
    private const val HASH_BUCKET_MILLIS = 5 * 60 * 1000L

    fun isSenderWhitelisted(senderName: String, settings: BankMessageParserSettings): Boolean {
        val normalizedSender = senderName.normalizedSenderToken()
        if (normalizedSender.isBlank()) return false

        return listOf(settings.dailyUseSource, settings.savingsSource)
            .filter { it.isEnabled }
            .map { it.senderName.normalizedSenderToken() }
            .filter { it.isNotBlank() }
            .any { configured ->
                normalizedSender.contains(configured) || configured.contains(normalizedSender)
            }
    }

    fun hashFor(senderName: String, messageBody: String, receivedAtMillis: Long): String {
        val roundedTimestamp = (receivedAtMillis / HASH_BUCKET_MILLIS) * HASH_BUCKET_MILLIS
        val input = listOf(
            senderName.normalizedSenderToken(),
            messageBody.trim(),
            roundedTimestamp.toString()
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun shouldAllowReimport(
        historyStatus: BankMessageImportStatus?,
        hasSavedMatchingRecord: Boolean
    ): Boolean =
        when (historyStatus) {
            null -> true
            BankMessageImportStatus.PENDING -> true
            BankMessageImportStatus.SAVED,
            BankMessageImportStatus.LINKED -> !hasSavedMatchingRecord
            BankMessageImportStatus.DISMISSED,
            BankMessageImportStatus.IGNORED -> false
        }

    private fun String.normalizedSenderToken(): String =
        trim()
            .lowercase(Locale.US)
            .replace(Regex("""[^a-z0-9]+"""), "")
}
