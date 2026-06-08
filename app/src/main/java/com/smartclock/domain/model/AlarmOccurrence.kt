package com.smartclock.domain.model

enum class OccurrenceSource(val code: Int) {
    PRIMARY(0),
    SNOOZE(1);

    companion object {
        fun fromCode(code: Int?): OccurrenceSource = if (code == SNOOZE.code) SNOOZE else PRIMARY
    }
}

enum class OccurrenceStatus(val code: Int) {
    PENDING(0),
    CONSUMED(1),
    EXPIRED(2),
    CANCELED(3);

    companion object {
        fun fromCode(code: Int?): OccurrenceStatus = when (code) {
            CONSUMED.code -> CONSUMED
            EXPIRED.code -> EXPIRED
            CANCELED.code -> CANCELED
            else -> PENDING
        }
    }
}

data class AlarmOccurrence(
    val id: Long = 0L,
    val alarmId: Long,
    val triggerAt: Long,
    val originTriggerAt: Long = triggerAt,
    val source: OccurrenceSource = OccurrenceSource.PRIMARY,
    val status: OccurrenceStatus = OccurrenceStatus.PENDING,
    val snoozeCount: Int = 0,
    val snoozeMinutes: Int = 0,
    val expiresAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
