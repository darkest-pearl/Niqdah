package com.musab.niqdah.domain.finance

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test
    fun parsesAedWithCentsToMinorUnits() {
        assertEquals(1_725L, parseMoneyToMinorUnits("AED 17.25", "AED"))
    }

    @Test
    fun parsesLeadingDecimalEuroToMinorUnits() {
        assertEquals(93L, parseMoneyToMinorUnits("EUR .93", "EUR"))
    }

    @Test
    fun parsesGroupedAedToMinorUnits() {
        assertEquals(149_923L, parseMoneyToMinorUnits("AED 1,499.23", "AED"))
    }

    @Test
    fun formatsMinorUnitsWithTwoDecimalsAndGrouping() {
        assertEquals("AED 1,499.23", formatMinorUnits(149_923L, "AED"))
        assertEquals("EUR 0.93", formatMinorUnits(93L, "EUR"))
        assertEquals("AED 10.00", formatMinorUnits(1_000L, "AED"))
    }

    @Test
    fun decimalStringKeepsTwoDecimalsWithoutGrouping() {
        assertEquals("1499.23", minorUnitsToDecimalString(149_923L))
        assertEquals("0.93", minorUnitsToDecimalString(93L))
    }

    @Test
    fun minorUnitAdditionDoesNotLoseCents() {
        val total = parseMoneyToMinorUnits("17.25", "AED") +
            parseMoneyToMinorUnits("0.93", "AED") +
            parseMoneyToMinorUnits("1499.23", "AED")

        assertEquals(151_741L, total)
        assertEquals("AED 1,517.41", formatMinorUnits(total, "AED"))
    }
}
