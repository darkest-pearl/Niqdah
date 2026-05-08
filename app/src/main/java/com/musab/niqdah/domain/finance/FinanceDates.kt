package com.musab.niqdah.domain.finance

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object FinanceDates {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun currentYearMonth(): String = YearMonth.now().toString()

    fun yearMonthFromMillis(millis: Long): String =
        YearMonth.from(
            Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        ).toString()

    fun todayInput(): String = LocalDate.now().format(dateFormatter)

    fun dateInputFromMillis(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(dateFormatter)

    fun parseDateInput(value: String): Long? =
        runCatching {
            LocalDate.parse(value.trim(), dateFormatter)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
}
