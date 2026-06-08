package com.smartclock.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownRuntimeTest {

    @Test
    fun `running countdown clamps remaining seconds at zero`() {
        val runtime = CountdownRuntime(
            alarmId = 1L,
            status = CountdownStatus.RUNNING,
            endAt = 1_000L,
            remainingSec = 0,
            originalDurationSec = 90
        )

        assertEquals(0, runtime.remainingAt(now = 5_000L))
    }

    @Test
    fun `paused countdown returns stored remaining seconds`() {
        val runtime = CountdownRuntime(
            alarmId = 1L,
            status = CountdownStatus.PAUSED,
            endAt = null,
            remainingSec = 125,
            originalDurationSec = 180
        )

        assertEquals(125, runtime.remainingAt(now = 5_000L))
    }
}
