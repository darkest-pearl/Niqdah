package com.musab.niqdah.domain.ai

import com.musab.niqdah.domain.finance.ParsedBankMessage
import com.musab.niqdah.domain.finance.ParsedBankMessageType
import java.util.Locale

object AiFinanceDraftGate {
    fun shouldCreateDraft(rawText: String, parsed: ParsedBankMessage): Boolean {
        val text = rawText.compact()
        if (parsed.type == ParsedBankMessageType.INFORMATIONAL) return false
        if ((parsed.amount ?: 0.0) <= 0.0) return false

        if (hasExplicitRecordIntent(text)) return true
        if (parsed.type == ParsedBankMessageType.UNKNOWN) return false

        return hasCurrencyAmount(text) && hasRecognizedBankSmsPattern(text)
    }

    fun draftTypeFor(rawText: String, parsed: ParsedBankMessage): ParsedBankMessageType {
        if (parsed.type != ParsedBankMessageType.UNKNOWN) return parsed.type

        val text = rawText.compact()
        return when {
            text.containsAny(savingsSignals) -> ParsedBankMessageType.SAVINGS_TRANSFER
            text.containsAny(creditSignals) -> ParsedBankMessageType.INCOME
            text.containsAny(transferOutSignals) -> ParsedBankMessageType.INTERNAL_TRANSFER_OUT
            else -> ParsedBankMessageType.EXPENSE
        }
    }

    private fun hasExplicitRecordIntent(text: String): Boolean =
        Regex("""\b(log|record)\b""").containsMatchIn(text) ||
            Regex("""\bsave\s+this\b""").containsMatchIn(text) ||
            Regex("""\bimport\s+this\s+sms\b""").containsMatchIn(text) ||
            Regex("""\badd\s+(?:a\s+|this\s+)?(transaction|expense|income|deposit|transfer|purchase)\b""")
                .containsMatchIn(text) ||
            Regex("""\bsave\s+(?:this\s+)?(transaction|expense|income|deposit|transfer|purchase)\b""")
                .containsMatchIn(text)

    private fun hasCurrencyAmount(text: String): Boolean =
        Regex("""\b(aed|dhs|dirhams?|usd|sar|eur|gbp)\s*(?:[0-9][0-9,]*(?:\.\d{1,2})?|\.\d{1,2})\b""")
            .containsMatchIn(text) ||
            Regex("""\b(?:[0-9][0-9,]*(?:\.\d{1,2})?|\.\d{1,2})\s*(aed|dhs|dirhams?|usd|sar|eur|gbp)\b""")
                .containsMatchIn(text)

    private fun hasRecognizedBankSmsPattern(text: String): Boolean =
        text.containsAny(debitSignals + creditSignals + savingsSignals + transferOutSignals) &&
            text.containsAny(bankPatternSignals)

    private fun String.compact(): String =
        trim().replace(Regex("""\s+"""), " ").lowercase(Locale.US)

    private fun String.containsAny(values: List<String>): Boolean =
        values.any { contains(it) }

    private val debitSignals = listOf(
        "debited",
        "debit",
        "purchase",
        "paid",
        "spent",
        "card transaction",
        "pos",
        "atm withdrawal",
        "deducted",
        "charged"
    )

    private val creditSignals = listOf(
        "credited",
        "credit",
        "deposited",
        "deposit",
        "salary",
        "payroll",
        "wages",
        "employer",
        "refund",
        "transfer received"
    )

    private val savingsSignals = listOf(
        "transferred to savings",
        "transfer to savings",
        "savings account",
        "saving account",
        "deposited to savings"
    )

    private val transferOutSignals = listOf(
        "account to account transfer",
        "transfer out",
        "transferred from"
    )

    private val bankPatternSignals = listOf(
        "account no",
        "ac no",
        "card ending",
        "debit card",
        "available balance",
        "avl bal",
        "current balance"
    )
}
