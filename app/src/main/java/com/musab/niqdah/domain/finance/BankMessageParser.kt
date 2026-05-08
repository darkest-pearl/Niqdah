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
    fun parse(
        rawMessage: String,
        manualSenderName: String,
        settings: BankMessageParserSettings,
        categories: List<BudgetCategory>,
        nowMillis: Long = System.currentTimeMillis()
    ): ParsedBankMessage {
        val compactMessage = rawMessage.compact()
        val senderName = manualSenderName.trim().ifBlank { extractSender(rawMessage) }
        val sourceType = resolveSourceType(senderName, compactMessage, settings)
        val canUseDaily = settings.dailyUseSource.isEnabled &&
            (sourceType == BankMessageSourceType.DAILY_USE || settings.dailyUseSource.senderName.isBlank())
        val canUseSavings = settings.savingsSource.isEnabled &&
            (sourceType == BankMessageSourceType.SAVINGS || settings.savingsSource.senderName.isBlank())
        val hasSavingsTransfer = compactMessage.containsAny(settings.savingsTransferKeywords)
        val hasDebit = compactMessage.containsAny(settings.debitKeywords)
        val hasCredit = compactMessage.containsAny(settings.creditKeywords)
        val parsedType = when {
            hasSavingsTransfer && canUseSavings -> ParsedBankMessageType.SAVINGS_TRANSFER
            hasDebit && canUseDaily -> ParsedBankMessageType.EXPENSE
            hasCredit && canUseDaily -> ParsedBankMessageType.INCOME
            else -> ParsedBankMessageType.UNKNOWN
        }

        val moneyMentions = extractMoneyMentions(rawMessage)
        val amount = chooseTransactionAmount(moneyMentions, compactMessage)?.amount
        val currency = chooseTransactionAmount(moneyMentions, compactMessage)?.currency
            ?: moneyMentions.firstOrNull()?.currency
            ?: FinanceDefaults.DEFAULT_CURRENCY
        val availableBalance = moneyMentions.firstOrNull { it.isBalanceLike }?.amount
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
            description = description,
            occurredAtMillis = occurredAt,
            suggestedCategoryId = categorySuggestion.id,
            suggestedCategoryName = categorySuggestion.name,
            suggestedNecessity = categorySuggestion.necessity,
            confidence = confidenceFor(
                parsedType = parsedType,
                sourceType = sourceType,
                amount = amount,
                categorySuggestion = categorySuggestion
            )
        )
    }

    private fun resolveSourceType(
        senderName: String,
        compactMessage: String,
        settings: BankMessageParserSettings
    ): BankMessageSourceType {
        val normalizedSender = senderName.normalizedToken()
        val dailySender = settings.dailyUseSource.senderName.normalizedToken()
        val savingsSender = settings.savingsSource.senderName.normalizedToken()

        return when {
            settings.dailyUseSource.isEnabled &&
                dailySender.isNotBlank() &&
                normalizedSender.contains(dailySender) -> BankMessageSourceType.DAILY_USE
            settings.savingsSource.isEnabled &&
                savingsSender.isNotBlank() &&
                normalizedSender.contains(savingsSender) -> BankMessageSourceType.SAVINGS
            settings.savingsSource.isEnabled &&
                compactMessage.containsAny(settings.savingsTransferKeywords) -> BankMessageSourceType.SAVINGS
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
            start = start,
            end = end,
            context = context,
            isBalanceLike = prefix.containsAny(listOf("balance", "available", "avail", "bal"))
        )
    }

    private fun chooseTransactionAmount(
        mentions: List<MoneyMention>,
        compactMessage: String
    ): MoneyMention? {
        if (mentions.isEmpty()) return null
        val hints = listOf("amount", "amt", "debited", "credited", "spent", "paid", "purchase", "transfer")
        return mentions.maxByOrNull { mention ->
            var score = 0
            if (!mention.isBalanceLike) score += 5
            if (mention.context.containsAny(hints)) score += 4
            if (compactMessage.indexOf(mention.amount.cleanAmountText()).let { it >= 0 && it < 80 }) score += 1
            if (mention.isBalanceLike) score -= 6
            score
        }
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
                keywords = listOf("fiancee", "fiancée", "fiance", "bride"),
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
        sourceType: BankMessageSourceType,
        amount: Double?,
        categorySuggestion: CategorySuggestion
    ): ParsedBankMessageConfidence = when {
        parsedType != ParsedBankMessageType.UNKNOWN &&
            amount != null &&
            sourceType != BankMessageSourceType.UNKNOWN &&
            categorySuggestion.id != FinanceDefaults.UNCATEGORIZED_CATEGORY_ID -> ParsedBankMessageConfidence.HIGH
        parsedType != ParsedBankMessageType.UNKNOWN && amount != null -> ParsedBankMessageConfidence.MEDIUM
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

    private fun String.containsMoneyLike(): Boolean =
        contains(Regex("""(?i)\b(AED|DHS|DIRHAMS?|USD|SAR|EUR|GBP)\b|[0-9][0-9,]*(?:\.\d{1,2})?"""))

    private fun String.containsDateLike(): Boolean =
        contains(Regex("""\b\d{1,4}[/-]\d{1,2}[/-]\d{1,4}\b"""))

    private fun Double.cleanAmountText(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    private data class MoneyMention(
        val amount: Double,
        val currency: String,
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
}
