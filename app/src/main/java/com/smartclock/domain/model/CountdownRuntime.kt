package com.smartclock.domain.model

enum class CountdownStatus {
    RUNNING,
    PAUSED
}

data class CountdownRuntime(
    val alarmId: Long,
    val status: CountdownStatus,
    val endAt: Long?,
    val remainingSec: Int,
    val originalDurationSec: Int
) {
    fun remainingAt(now: Long = System.currentTimeMillis()): Int = when (status) {
        CountdownStatus.RUNNING -> (((endAt ?: now) - now) / 1000L).toInt().coerceAtLeast(0)
        CountdownStatus.PAUSED -> remainingSec.coerceAtLeast(0)
    }
}
