package com.musab.niqdah.domain.finance

object BankMessageParserDecision {
    const val REASON_SENDER_NOT_WHITELISTED = "Sender not whitelisted"
    const val REASON_INFORMATIONAL = "Informational message"
    const val REASON_DUPLICATE = "Duplicate message"
    const val REASON_COULD_NOT_DETECT_AMOUNT = "Could not detect amount"
    const val REASON_UNSUPPORTED = "Unsupported bank message"
    const val REASON_ALREADY_HANDLED = "Already handled"
    const val REASON_MISSING_CONFIGURED_ACCOUNT_SUFFIX = "Missing configured account suffix"

    fun shouldCreatePendingImport(parsed: ParsedBankMessage): Boolean =
        ignoredReason(parsed).isBlank()

    fun ignoredReason(parsed: ParsedBankMessage): String =
        when {
            parsed.type == ParsedBankMessageType.INFORMATIONAL -> REASON_INFORMATIONAL
            parsed.type == ParsedBankMessageType.UNKNOWN && parsed.amountMinor == null -> REASON_COULD_NOT_DETECT_AMOUNT
            parsed.amountMinor == null -> REASON_COULD_NOT_DETECT_AMOUNT
            parsed.type == ParsedBankMessageType.UNKNOWN -> REASON_UNSUPPORTED
            else -> ""
        }
}
