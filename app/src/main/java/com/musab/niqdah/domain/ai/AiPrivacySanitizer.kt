package com.musab.niqdah.domain.ai

data class SanitizedAiMessage(
    val displayText: String,
    val backendText: String,
    val wasSanitized: Boolean
)

object AiPrivacySanitizer {
    private val moneyPattern = Regex(
        pattern = """(?i)\b(?:AED|DHS|DIRHAMS?|USD|SAR|EUR|GBP)\s*[0-9]|\b[0-9][0-9,]*(?:\.\d{1,2})?\s*(?:AED|DHS|DIRHAMS?|USD|SAR|EUR|GBP)\b"""
    )
    private val bankTerms = listOf(
        "available balance",
        "avl bal",
        "avail bal",
        "current balance",
        "debited",
        "credited",
        "account",
        "acct",
        "ac no",
        "card ending",
        "transaction",
        "transfer",
        "purchase"
    )

    fun sanitizeUserMessage(rawText: String, draftAction: AiFinanceDraftAction?): SanitizedAiMessage {
        val trimmed = rawText.trim()
        val shouldSanitize = draftAction != null || looksLikeBankSms(trimmed)
        if (!shouldSanitize) {
            return SanitizedAiMessage(
                displayText = trimmed,
                backendText = trimmed,
                wasSanitized = false
            )
        }

        val summary = draftAction?.let { action ->
            val amountText = action.amount?.let { amount ->
                "${action.currency.ifBlank { "AED" }} ${amount.toPlainAmount()}"
            } ?: "amount missing"
            listOf(
                "type=${action.type.label}",
                "amount=$amountText",
                "category=${action.categoryName}",
                "necessity=${action.necessity.label}",
                "date=${action.dateInput}",
                "confidence=${action.confidence.label}"
            ).joinToString(", ")
        } ?: "no saveable transaction draft"

        return SanitizedAiMessage(
            displayText = "Bank message parsed locally. Raw SMS was not sent to AI.",
            backendText = "The user pasted bank SMS content. The Android app withheld the raw SMS text and parsed it locally. Parsed summary: $summary. Give guidance using this summary only.",
            wasSanitized = true
        )
    }

    fun looksLikeBankSms(text: String): Boolean {
        val compact = text.trim().lowercase()
        if (compact.isBlank()) return false
        val hasMoney = moneyPattern.containsMatchIn(text)
        val hasBankTerm = bankTerms.any { compact.contains(it) }
        return hasMoney && hasBankTerm
    }

    private fun Double.toPlainAmount(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()
}
