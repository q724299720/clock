package com.smartclock.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDefaultsTest {

    @Test
    fun `countdown defaults to absolute wake alarm`() {
        assertEquals(AlertPolicy.WAKE_ALARM, defaultAlertPolicy(AlarmType.COUNTDOWN, null))
        assertEquals(TimeAnchorMode.ABSOLUTE_UTC, defaultTimeAnchorMode(AlarmType.COUNTDOWN))
    }

    @Test
    fun `birthday template defaults to quiet reminder`() {
        assertEquals(
            AlertPolicy.QUIET_REMINDER,
            defaultAlertPolicy(AlarmType.ANNIVERSARY, AlarmTemplateIds.BIRTHDAY)
        )
        assertEquals(TimeAnchorMode.FLOATING_LOCAL, defaultTimeAnchorMode(AlarmType.WEEKLY))
    }
}
