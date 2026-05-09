package com.musab.niqdah.domain.finance

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

class BankMessageParser {
    fun parsePendingImport(
        rawMessage: String,
        senderName: String,
        settings: BankMessageParserSettings,
        categories: List<BudgetCategory>,
        messageHash: String,
        receivedAtMillis: Long,
        latestBalances: List<AccountBalanceSnapshot> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): PendingBankImport {
        val parsed = parse(
            rawMessage = rawMessage,
            manualSenderName = senderName,
            settings = settings,
            categories = categories,
            latestBalances = latestBalances,
            nowMillis = receivedAtMillis
        )
        return PendingBankImport(
            id = messageHash,
            messageHash = messageHash,
            senderName = parsed.senderName.ifBlank { senderName },
            rawMessage = rawMessage,
            sourceType = parsed.sourceType,
            type = parsed.type,
            amount = parsed.amount,
            currency = parsed.currency,
            availableBalance = parsed.availableBalance,
            availableBalanceCurrency = parsed.availableBalanceCurrency,
            originalForeignAmount = parsed.originalForeignAmount,
            originalForeignCurrency = parsed.originalForeignCurrency,
            inferredAccountDebit = parsed.inferredAccountDebit,
            isAmountInferredFromBalance = parsed.isAmountInferredFromBalance,
            reviewNote = parsed.reviewNote,
            description = parsed.description,
            occurredAtMillis = parsed.occurredAtMillis,
            suggestedCategoryId = parsed.suggestedCategoryId,
            suggestedCategoryName = parsed.suggestedCategoryName,
            suggestedNecessity = parsed.suggestedNecessity,
            confidence = parsed.confidence,
            receivedAtMillis = receivedAtMillis,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis
        )
    }

    fun parse(
        rawMessage: String,
        manualSenderName: String,
        settings: BankMessageParserSettings,
        categories: List<BudgetCategory>,
        latestBalances: List<AccountBalanceSnapshot> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): ParsedBankMessage {
        val compactMessage = rawMessage.compact()
        val senderName = manualSenderName.trim().ifBlank { extractSender(rawMessage) }
        val savingsKeywords = mergedKeywords(
            settings.savingsTransferKeywords,
            FinanceDefaults.DEFAULT_SAVINGS_TRANSFER_KEYWORDS,
            STRONG_SAVINGS_TRANSFER_KEYWORDS
        )
        val sourceType = resolveSourceType(senderName, compactMessage, settings, savingsKeywords)
        val canUseDaily = settings.dailyUseSource.isEnabled &&
            (sourceType == BankMessageSourceType.DAILY_USE || sourceType == BankMessageSourceType.UNKNOWN)
        val debitKeywords = mergedKeywords(
            settings.debitKeywords,
            FinanceDefaults.DEFAULT_DEBIT_KEYWORDS,
            STRONG_DEBIT_KEYWORDS
        )
        val creditKeywords = mergedKeywords(
            settings.creditKeywords,
            FinanceDefaults.DEFAULT_CREDIT_KEYWORDS,
            STRONG_CREDIT_KEYWORDS
        )
        val hasSavingsTransfer = compactMessage.containsAny(savingsKeywords)
        val hasDebit = compactMessage.containsAny(debitKeywords)
        val hasCredit = compactMessage.containsAny(creditKeywords)
        val parsedType = when {
            hasSavingsTransfer -> ParsedBankMessageType.SAVINGS_TRANSFER
            hasDebit && canUseDaily -> ParsedBankMessageType.EXPENSE
            hasCredit && canUseDaily -> ParsedBankMessageType.INCOME
            else -> ParsedBankMessageType.UNKNOWN
        }

        val moneyMentions = extractMoneyMentions(rawMessage)
        val foreignTransactionAmount = chooseTransactionAmount(
            moneyMentions.filter { !it.isBalanceLike && it.currency != FinanceDefaults.DEFAULT_CURRENCY },
            compactMessage
        )
        val explicitAedTransactionAmount = chooseTransactionAmount(
            moneyMentions.filter { !it.isBalanceLike && it.currency == FinanceDefaults.DEFAULT_CURRENCY },
            compactMessage
        )
        val transactionAmount = when {
            foreignTransactionAmount != null && explicitAedTransactionAmount != null -> explicitAedTransactionAmount
            else -> chooseTransactionAmount(moneyMentions, compactMessage)
        }
        val balanceMention = chooseAvailableBalance(moneyMentions)
        val balanceCurrency = balanceMention?.currency ?: FinanceDefaults.DEFAULT_CURRENCY
        val inference = inferDebitFromBalanceChange(
            accountKind = sourceType.toAccountKind(),
            latestBalances = latestBalances,
            currentBalance = balanceMention,
            clearerAedTransactionAmount = explicitAedTransactionAmount,
            foreignTransactionAmount = foreignTransactionAmount
        )
        val amount = inference?.amount ?: transactionAmount?.amount
        val currency = inference?.currency
            ?: transactionAmount?.currency
            ?: moneyMentions.firstOrNull()?.currency
            ?: FinanceDefaults.DEFAULT_CURRENCY
        val hasExplicitCurrency = transactionAmount?.hasExplicitCurrency == true ||
            moneyMentions.any { !it.isBalanceLike && it.hasExplicitCurrency }
        val availableBalance = balanceMention?.amount
        val occurredAt = parseDateMillis(rawMessage, nowMillis) ?: nowMillis
        val categorySuggestion = when (parsedType) {
            ParsedBankMessageType.SAVINGS_TRANSFER -> categoryFor(
                categoryId = FinanceDefaults.MARRIAGE_SAVINGS_CATEGORY_ID,
                categories = categories,
                necessity = NecessityLevel.NECESSARY
            )
            ParsedBankMessageType.EXPENSE -> inferCategory(compactMessage, categories)
            else -> categoryFor(
                categoryId = FinanceDefaults.UNCATEGORIZED_CATEGORY_ID,
                categories = categories,
                necessity = NecessityLevel.OPTIONAL
            )
        }
        val description = extractDescription(
            rawMessage = rawMessage,
            compactMessage = compactMessage,
            parsedType = parsedType,
            senderName = senderName
        )

        return ParsedBankMessage(
            rawMessage = rawMessage,
            senderName = senderName,
            sourceType = sourceType,
            type = parsedType,
            amount = amount,
            currency = currency,
            availableBalance = availableBalance,
            availableBalanceCurrency = balanceCurrency,
            originalForeignAmount = foreignTransactionAmount?.amount,
            originalForeignCurrency = foreignTransactionAmount?.currency.orEmpty(),
            inferredAccountDebit = inference?.amount,
            isAmountInferredFromBalance = inference != null,
            reviewNote = inference?.note.orEmpty(),
            description = description,
            occurredAtMillis = occurredAt,
            suggestedCategoryId = categorySuggestion.id,
            suggestedCategoryName = categorySuggestion.name,
            suggestedNecessity = categorySuggestion.necessity,
            confidence = inference?.confidence ?: confidenceFor(
                parsedType = parsedType,
                amount = amount,
                hasExplicitCurrency = hasExplicitCurrency,
                hasSavingsTransfer = hasSavingsTransfer
            )
        )
    }

    private fun resolveSourceType(
        senderName: String,
        compactMessage: String,
        settings: BankMessageParserSettings,
        savingsKeywords: List<String>
    ): BankMessageSourceType {
        val normalizedSender = senderName.normalizedToken()
        val dailySender = settings.dailyUseSource.senderName.normalizedToken()
        val savingsSender = settings.savingsSource.senderName.normalizedToken()

        return when {
            settings.savingsSource.isEnabled &&
                compactMessage.containsAny(savingsKeywords) -> BankMessageSourceType.SAVINGS
            settings.dailyUseSource.isEnabled &&
                dailySender.isNotBlank() &&
                normalizedSender.contains(dailySender) -> BankMessageSourceType.DAILY_USE
            settings.savingsSource.isEnabled &&
                savingsSender.isNotBlank() &&
                normalizedSender.contains(savingsSender) -> BankMessageSourceType.SAVINGS
            settings.dailyUseSource.isEnabled &&
                dailySender.isBlank() -> BankMessageSourceType.DAILY_USE
            else -> BankMessageSourceType.UNKNOWN
        }
    }

    private fun extractSender(rawMessage: String): String {
        val senderPatterns = listOf(
            Regex("""(?im)^\s*(?:from|sender)\s*[:\-]\s*([A-Za-z0-9 ._\-]{2,40})\s*$"""),
            Regex("""(?i)\b(?:from|sender)\s+([A-Za-z0-9._\-]{2,32})\b""")
        )
        senderPatterns.forEach { pattern ->
            pattern.find(rawMessage)?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        }

        val firstLine = rawMessage.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return firstLine
            .takeIf { it.length in 2..32 && !it.containsMoneyLike() && !it.containsDateLike() }
            .orEmpty()
    }

    private fun extractMoneyMentions(rawMessage: String): List<MoneyMention> {
        val mentions = mutableListOf<MoneyMention>()
        val patterns = listOf(
            Regex("""\b(AED|DHS|DIRHAMS?|USD|SAR|EUR|GBP)\s*([0-9][0-9,]*(?:\.\d{1,2})?)\b""", RegexOption.IGNORE_CASE),
            Regex("""\b([0-9][0-9,]*(?:\.\d{1,2})?)\s*(AED|DHS|DIRHAMS?|USD|SAR|EUR|GBP)\b""", RegexOption.IGNORE_CASE)
        )

        patterns.forEach { pattern ->
            pattern.findAll(rawMessage).forEach { match ->
                val first = match.groupValues[1]
                val second = match.groupValues[2]
                val amountText = if (first.firstOrNull()?.isDigit() == true) first else second
                val currencyText = if (first.firstOrNull()?.isDigit() == true) second else first
                val amount = amountText.replace(",", "").toDoubleOrNull()
                if (amount != null) {
                    mentions += moneyMention(
                        rawMessage = rawMessage,
                        amount = amount,
                        currency = normalizeCurrency(currencyText),
                        hasExplicitCurrency = true,
                        start = match.range.first,
                        end = match.range.last
                    )
                }
            }
        }

        Regex(
            """\b(?:amount|amt|debited|credited|spent|paid|purchase|transferred?)\D{0,20}([0-9][0-9,]*(?:\.\d{1,2})?)\b""",
            RegexOption.IGNORE_CASE
        ).findAll(rawMessage).forEach { match ->
            val range = match.groups[1]?.range ?: return@forEach
            if (mentions.any { range.first in it.start..it.end }) return@forEach
            val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@forEach
            mentions += moneyMention(
                rawMessage = rawMessage,
                amount = amount,
                currency = FinanceDefaults.DEFAULT_CURRENCY,
                hasExplicitCurrency = false,
                start = range.first,
                end = range.last
            )
        }

        return mentions.distinctBy { "${it.start}-${it.end}-${it.amount}" }
    }

    private fun moneyMention(
        rawMessage: String,
        amount: Double,
        currency: String,
        hasExplicitCurrency: Boolean,
        start: Int,
        end: Int
    ): MoneyMention {
        val contextStart = (start - 45).coerceAtLeast(0)
        val contextEnd = (end + 45).coerceAtMost(rawMessage.lastIndex)
        val prefixStart = (start - 28).coerceAtLeast(0)
        val prefix = rawMessage.substring(prefixStart until start).compact()
        val context = rawMessage.substring(contextStart..contextEnd).compact()
        return MoneyMention(
            amount = amount,
            currency = currency,
            hasExplicitCurrency = hasExplicitCurrency,
            start = start,
            end = end,
            context = context,
            isBalanceLike = isBalanceContext(prefix, context)
        )
    }

    private fun chooseTransactionAmount(
        mentions: List<MoneyMention>,
        compactMessage: String
    ): MoneyMention? {
        if (mentions.isEmpty()) return null
        val transactionCandidates = mentions.filterNot { it.isBalanceLike }
        if (transactionCandidates.isEmpty()) return null
        val hints = listOf("amount", "amt", "debited", "credited", "spent", "paid", "purchase", "transfer")
        return transactionCandidates.maxByOrNull { mention ->
            var score = 0
            score += 5
            if (mention.context.containsAny(hints)) score += 4
            if (compactMessage.indexOf(mention.amount.cleanAmountText()).let { it >= 0 && it < 80 }) score += 1
            score
        }
    }

    private fun chooseAvailableBalance(mentions: List<MoneyMention>): MoneyMention? =
        mentions
            .filter { it.isBalanceLike }
            .maxByOrNull { mention ->
                var score = 0
                if (mention.currency == FinanceDefaults.DEFAULT_CURRENCY) score += 2
                if (mention.context.containsAny(listOf("available balance", "avl bal", "avail bal"))) score += 3
                if (mention.context.containsAny(listOf("current balance", "balance:", "available limit"))) score += 2
                score
            }

    private fun inferDebitFromBalanceChange(
        accountKind: AccountKind?,
        latestBalances: List<AccountBalanceSnapshot>,
        currentBalance: MoneyMention?,
        clearerAedTransactionAmount: MoneyMention?,
        foreignTransactionAmount: MoneyMention?
    ): BalanceDebitInference? {
        if (accountKind == null || currentBalance == null || foreignTransactionAmount == null) return null
        if (clearerAedTransactionAmount != null) return null

        val previousBalance = latestBalances
            .filter { it.accountKind == accountKind && it.currency == currentBalance.currency }
            .maxWithOrNull(
                compareBy<AccountBalanceSnapshot> { it.messageTimestampMillis }
                    .thenBy { it.createdAtMillis }
            )
            ?: return null
        val inferredDebit = previousBalance.availableBalance - currentBalance.amount
        if (!inferredDebit.isReasonableInferredDebit(previousBalance.availableBalance)) return null

        return BalanceDebitInference(
            amount = inferredDebit,
            currency = currentBalance.currency,
            confidence = ParsedBankMessageConfidence.MEDIUM,
            note = INFERRED_BALANCE_NOTE
        )
    }

    private fun inferCategory(
        compactMessage: String,
        categories: List<BudgetCategory>
    ): CategorySuggestion {
        val rules = listOf(
            CategoryRule(
                id = FinanceDefaults.FOOD_TRANSPORT_CATEGORY_ID,
                keywords = listOf("burger", "restaurant", "cafeteria", "food", "talabat", "noon food", "taxi", "fuel", "metro", "bus"),
                necessity = NecessityLevel.NECESSARY
            ),
            CategoryRule(
                id = FinanceDefaults.RENT_CATEGORY_ID,
                keywords = listOf("rent", "landlord", "apartment", "flat"),
                necessity = NecessityLevel.NECESSARY
            ),
            CategoryRule(
                id = FinanceDefaults.MEDICAL_CATEGORY_ID,
                keywords = listOf("clinic", "pharmacy", "doctor", "dental", "hospital"),
                necessity = NecessityLevel.NECESSARY
            ),
            CategoryRule(
                id = FinanceDefaults.CLOTHING_CATEGORY_ID,
                keywords = listOf("thobe", "clothes", "clothing", "garment", "tailor"),
                necessity = NecessityLevel.OPTIONAL
            ),
            CategoryRule(
                id = FinanceDefaults.FIANCEE_CATEGORY_ID,
                keywords = listOf("fiancee", "fiance", "bride"),
                necessity = NecessityLevel.OPTIONAL
            ),
            CategoryRule(
                id = FinanceDefaults.FAMILY_GIFTS_CATEGORY_ID,
                keywords = listOf("family gift", "family gifts", "gift", "perfume"),
                necessity = NecessityLevel.OPTIONAL
            ),
            CategoryRule(
                id = FinanceDefaults.AVOID_CATEGORY_ID,
                keywords = listOf("avoid", "casino", "betting", "lottery"),
                necessity = NecessityLevel.AVOID
            )
        )

        val match = rules.firstOrNull { rule -> compactMessage.containsAny(rule.keywords) }
        return categoryFor(
            categoryId = match?.id ?: FinanceDefaults.UNCATEGORIZED_CATEGORY_ID,
            categories = categories,
            necessity = match?.necessity ?: NecessityLevel.OPTIONAL
        )
    }

    private fun categoryFor(
        categoryId: String,
        categories: List<BudgetCategory>,
        necessity: NecessityLevel
    ): CategorySuggestion {
        val category = categories.firstOrNull { it.id == categoryId }
            ?: FinanceDefaults.budgetCategories().firstOrNull { it.id == categoryId }
        return CategorySuggestion(
            id = category?.id ?: FinanceDefaults.UNCATEGORIZED_CATEGORY_ID,
            name = category?.name ?: "Uncategorized",
            necessity = necessity
        )
    }

    private fun extractDescription(
        rawMessage: String,
        compactMessage: String,
        parsedType: ParsedBankMessageType,
        senderName: String
    ): String {
        if (parsedType == ParsedBankMessageType.SAVINGS_TRANSFER) return "Transfer to savings"

        val merchantPatterns = listOf(
            Regex("""(?i)\b(?:at|from|to)\s+([A-Za-z][A-Za-z0-9 &'./\-]{2,55}?)(?=\s+(?:on|at|bal|balance|available|ref|card|acct|account)|[.,;]|$)"""),
            Regex("""(?i)\b(?:merchant|details|desc|description)\s*[:\-]\s*([A-Za-z0-9 &'./\-]{2,60})""")
        )
        merchantPatterns.forEach { pattern ->
            val candidate = pattern.find(rawMessage)?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank() && !candidate.first().isDigit()) return candidate
        }

        if (parsedType == ParsedBankMessageType.INCOME) {
            return senderName.ifBlank { "Income" }
        }

        return compactMessage
            .replace(Regex("""\b(AED|DHS|DIRHAMS?|USD|SAR|EUR|GBP)\s*[0-9][0-9,]*(?:\.\d{1,2})?\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b[0-9][0-9,]*(?:\.\d{1,2})?\s*(AED|DHS|DIRHAMS?|USD|SAR|EUR|GBP)\b""", RegexOption.IGNORE_CASE), "")
            .trim()
            .take(80)
            .ifBlank { "Imported bank message" }
    }

    private fun parseDateMillis(rawMessage: String, nowMillis: Long): Long? {
        val date = parseDate(rawMessage) ?: return null
        val time = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")
            .find(rawMessage)
            ?.let { match ->
                LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
            }
            ?: LocalTime.MIDNIGHT
        return LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
            .takeIf { it > 0L }
            ?: nowMillis
    }

    private fun parseDate(rawMessage: String): LocalDate? {
        Regex("""\b(20\d{2})-(\d{1,2})-(\d{1,2})\b""")
            .find(rawMessage)
            ?.let { return LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }

        Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b""")
            .find(rawMessage)
            ?.let {
                val year = it.groupValues[3].toInt().let { value -> if (value < 100) 2000 + value else value }
                return runCatching {
                    LocalDate.of(year, it.groupValues[2].toInt(), it.groupValues[1].toInt())
                }.getOrNull()
            }

        Regex("""\b(\d{1,2})\s+([A-Za-z]{3,9})\s+(\d{2,4})\b""")
            .find(rawMessage)
            ?.let {
                val year = it.groupValues[3].toInt().let { value -> if (value < 100) 2000 + value else value }
                val formatter = DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("d MMM uuuu")
                    .toFormatter(Locale.US)
                return try {
                    LocalDate.parse("${it.groupValues[1]} ${it.groupValues[2].take(3)} $year", formatter)
                } catch (_: DateTimeParseException) {
                    null
                }
            }

        return null
    }

    private fun confidenceFor(
        parsedType: ParsedBankMessageType,
        amount: Double?,
        hasExplicitCurrency: Boolean,
        hasSavingsTransfer: Boolean
    ): ParsedBankMessageConfidence = when {
        parsedType == ParsedBankMessageType.SAVINGS_TRANSFER && hasSavingsTransfer && amount != null ->
            ParsedBankMessageConfidence.HIGH
        parsedType != ParsedBankMessageType.UNKNOWN && amount != null && hasExplicitCurrency ->
            ParsedBankMessageConfidence.HIGH
        amount != null && hasExplicitCurrency ->
            ParsedBankMessageConfidence.MEDIUM
        parsedType != ParsedBankMessageType.UNKNOWN && amount != null ->
            ParsedBankMessageConfidence.MEDIUM
        else -> ParsedBankMessageConfidence.LOW
    }

    private fun normalizeCurrency(value: String): String =
        when (value.trim().uppercase(Locale.US)) {
            "DHS", "DIRHAM", "DIRHAMS" -> FinanceDefaults.DEFAULT_CURRENCY
            else -> value.trim().uppercase(Locale.US)
        }

    private fun String.compact(): String =
        trim().replace(Regex("""\s+"""), " ").lowercase(Locale.US)

    private fun String.normalizedToken(): String =
        trim().lowercase(Locale.US).replace(Regex("""[^a-z0-9]+"""), "")

    private fun String.containsAny(keywords: List<String>): Boolean {
        val text = lowercase(Locale.US)
        return keywords.any { keyword -> keyword.trim().isNotBlank() && text.contains(keyword.trim().lowercase(Locale.US)) }
    }

    private fun isBalanceContext(prefix: String, context: String): Boolean {
        val balancePrefix = Regex(
            """(?i)(?:available|avail|avl|current)\s+(?:balance|bal|limit)\s*[:\-]?$|(?:balance|bal)\s*[:\-]?$"""
        )
        return balancePrefix.containsMatchIn(prefix.trim()) ||
            context.contains(Regex("""(?i)\b(?:available|avail|avl|current)\s+(?:balance|bal|limit)\s*[:\-]?\s*$""")) ||
            context.contains(Regex("""(?i)\b(?:balance|bal)\s*[:\-]?\s*$"""))
    }

    private fun mergedKeywords(vararg keywordGroups: List<String>): List<String> =
        keywordGroups
            .flatMap { it }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.US) }

    private fun String.containsMoneyLike(): Boolean =
        contains(Regex("""(?i)\b(AED|DHS|DIRHAMS?|USD|SAR|EUR|GBP)\b|[0-9][0-9,]*(?:\.\d{1,2})?"""))

    private fun String.containsDateLike(): Boolean =
        contains(Regex("""\b\d{1,4}[/-]\d{1,2}[/-]\d{1,4}\b"""))

    private fun Double.cleanAmountText(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    private fun Double.isReasonableInferredDebit(previousBalance: Double): Boolean =
        this > 0.0 && this <= previousBalance && this <= MAX_REASONABLE_INFERRED_DEBIT

    private fun BankMessageSourceType.toAccountKind(): AccountKind? =
        when (this) {
            BankMessageSourceType.DAILY_USE -> AccountKind.DAILY_USE
            BankMessageSourceType.SAVINGS -> AccountKind.SAVINGS
            BankMessageSourceType.UNKNOWN -> null
        }

    private data class MoneyMention(
        val amount: Double,
        val currency: String,
        val hasExplicitCurrency: Boolean,
        val start: Int,
        val end: Int,
        val context: String,
        val isBalanceLike: Boolean
    )

    private data class CategoryRule(
        val id: String,
        val keywords: List<String>,
        val necessity: NecessityLevel
    )

    private data class CategorySuggestion(
        val id: String,
        val name: String,
        val necessity: NecessityLevel
    )

    private data class BalanceDebitInference(
        val amount: Double,
        val currency: String,
        val confidence: ParsedBankMessageConfidence,
        val note: String
    )

    private companion object {
        const val INFERRED_BALANCE_NOTE = "AED amount inferred from balance change."
        const val MAX_REASONABLE_INFERRED_DEBIT = 10_000.0

        val STRONG_SAVINGS_TRANSFER_KEYWORDS = listOf(
            "transferred to savings",
            "transfer to savings",
            "savings account",
            "saving account",
            "moved to savings",
            "marriage savings",
            "goal account",
            "reserve account",
            "saved to",
            "deposited to savings"
        )

        val STRONG_DEBIT_KEYWORDS = listOf(
            "debited",
            "spent",
            "purchase",
            "paid",
            "card transaction",
            "pos",
            "atm withdrawal",
            "deducted",
            "charged"
        )

        val STRONG_CREDIT_KEYWORDS = listOf(
            "credited",
            "salary",
            "received",
            "deposited",
            "refund",
            "cash deposit",
            "transfer received"
        )
    }
}
