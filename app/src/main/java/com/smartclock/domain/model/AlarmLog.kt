package com.smartclock.domain.model

enum class AlarmLogAction(val code: Int) {
    DISMISS(1),
    SNOOZE(2),
    MISSED(3);

    companion object {
        fun fromCode(code: Int): AlarmLogAction = entries.firstOrNull { it.code == code } ?: MISSED
    }
}

data class AlarmLog(
    val id: Long = 0L,
    val alarmId: Long,
    val userId: Long,
    val firedAt: Long,
    val action: AlarmLogAction,
    val deviceId: String? = null,
    val logHash: String = ""
)
