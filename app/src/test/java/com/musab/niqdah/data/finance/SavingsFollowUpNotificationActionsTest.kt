package com.musab.niqdah.data.finance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SavingsFollowUpNotificationActionsTest {
    @Test
    fun postSalaryNotificationActionsExcludeMarkDone() {
        val titles = SavingsFollowUpNotificationActionReceiver.notificationActionTitles()

        assertEquals(listOf("Review", "Skip this month"), titles)
        assertFalse(titles.contains("Mark done"))
    }
}
