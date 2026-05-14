package com.musab.niqdah.domain.finance

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private const val MINOR_UNITS_PER_MAJOR = 100L

fun parseMoneyToMinorUnits(input: String, currency: String = FinanceDefaults.DEFAULT_CURRENCY): Long =
    parseMoneyToMinorUnitsOrNull(input, currency)
        ?: throw IllegalArgumentException("Invalid money amount: $input")

fun parseMoneyToMinorUnitsOrNull(input: String, currency: String = FinanceDefaults.DEFAULT_CURRENCY): Long? {
    val normalizedCurrency = currency.trim().uppercase(Locale.US)
    val cleaned = input
        .trim()
        .uppercase(Locale.US)
        .replace(normalizedCurrency, "")
        .replace("DHS", "")
        .replace("DIRHAMS", "")
        .replace("DIRHAM", "")
        .replace(",", "")
        .trim()
        .takeIf { it.isNotBlank() }
        ?: return null

    return runCatching {
        BigDecimal(cleaned)
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
    }.getOrNull()
}

fun formatMinorUnits(amountMinor: Long, currency: String = FinanceDefaults.DEFAULT_CURRENCY): String {
    val amount = BigDecimal.valueOf(amountMinor, 2)
    val format = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
    return "${currency.trim().uppercase(Locale.US).ifBlank { FinanceDefaults.DEFAULT_CURRENCY }} ${format.format(amount)}"
}

fun minorUnitsToDecimalString(amountMinor: Long): String =
    BigDecimal.valueOf(amountMinor, 2)
        .setScale(2, RoundingMode.UNNECESSARY)
        .toPlainString()

fun majorToMinorUnits(amount: Double): Long =
    BigDecimal.valueOf(amount)
        .setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2)
        .longValueExact()

fun minorUnitsToMajor(amountMinor: Long): Double =
    BigDecimal.valueOf(amountMinor, 2).toDouble()

fun effectiveMinorUnits(amountMinor: Long, legacyMajorAmount: Double): Long =
    if (amountMinor != 0L) amountMinor else majorToMinorUnits(legacyMajorAmount)

fun effectiveMinorUnits(amountMinor: Long?, legacyMajorAmount: Double?): Long? =
    amountMinor ?: legacyMajorAmount?.let { majorToMinorUnits(it) }
