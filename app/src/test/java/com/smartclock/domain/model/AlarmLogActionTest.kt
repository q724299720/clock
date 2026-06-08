package com.smartclock.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmLogActionTest {

    @Test
    fun `from code falls back to missed on unknown value`() {
        assertEquals(AlarmLogAction.MISSED, AlarmLogAction.fromCode(99))
    }
}
