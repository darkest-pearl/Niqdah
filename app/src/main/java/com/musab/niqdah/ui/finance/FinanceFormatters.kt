package com.musab.niqdah.ui.finance

import com.musab.niqdah.domain.finance.FinanceDates
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun formatMoney(amount: Double, currency: String): String =
    String.format(Locale.US, "%,.0f %s", amount, currency)

fun formatProgress(progress: Double): String =
    "${(progress.coerceIn(0.0, 1.0) * 100).roundToInt()}%"

fun formatInputMoney(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.roundToInt().toString() else String.format(Locale.US, "%.2f", amount)

fun formatTransactionDate(millis: Long): String =
    FinanceDates.dateInputFromMillis(millis)

fun formatTransactionDateTime(millis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
