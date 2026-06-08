package com.smartclock.server.service

import com.smartclock.server.dto.AlarmLogAdminDto
import com.smartclock.server.dto.AlarmLogBatchRequest
import com.smartclock.server.dto.AlarmLogBatchResponse
import com.smartclock.server.dto.AlarmPullResponse
import com.smartclock.server.dto.AlarmPushRequest
import com.smartclock.server.dto.AlarmPushResponse
import com.smartclock.server.dto.AlarmSyncDto
import com.smartclock.server.dto.ApiUserDto
import com.smartclock.server.dto.BootstrapResponse
import com.smartclock.server.dto.toDto
import com.smartclock.server.model.UserProfile
import com.smartclock.server.support.ApiException
import com.smartclock.server.support.toInstantUtc
import com.smartclock.server.support.toIsoUtc
import com.smartclock.server.support.toSqlTimestamp
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class SyncService(
    private val dsl: DSLContext,
    private val authService: AuthService,
    private val clock: Clock
) {

    fun bootstrap(userId: Long): BootstrapResponse {
        val user = authService.me(com.smartclock.server.security.AuthUserPrincipal(userId, com.smartclock.server.model.Role.USER))
        val alarms = dsl.fetch(
            "SELECT * FROM alarms WHERE user_id = ? ORDER BY updated_at ASC",
            userId
        ).map(::mapAlarm)
        return BootstrapResponse(
            user = user.toDto(),
            alarms = alarms,
            serverTime = Instant.now(clock).toIsoUtc()
        )
    }

    fun pushAlarms(userId: Long, request: AlarmPushRequest): AlarmPushResponse = dsl.transactionResult { cfg ->
        val tx = DSL.using(cfg)
        val results = request.alarms.map { upsertAlarm(tx, userId, it) }
        AlarmPushResponse(results, Instant.now(clock).toIsoUtc())
    }

    fun pullAlarms(userId: Long, since: String?): AlarmPullResponse {
        val alarms = if (since.isNullOrBlank()) {
            dsl.fetch(
                "SELECT * FROM alarms WHERE user_id = ? ORDER BY updated_at ASC",
                userId
            )
        } else {
            dsl.fetch(
                "SELECT * FROM alarms WHERE user_id = ? AND updated_at > ? ORDER BY updated_at ASC",
                userId,
                since.toInstantUtc().toSqlTimestamp()
            )
        }.map(::mapAlarm)
        return AlarmPullResponse(alarms = alarms, serverTime = Instant.now(clock).toIsoUtc())
    }

    fun uploadLogs(userId: Long, request: AlarmLogBatchRequest): AlarmLogBatchResponse = dsl.transactionResult { cfg ->
        val tx = DSL.using(cfg)
        var inserted = 0
        var ignored = 0
        val now = Instant.now(clock).toSqlTimestamp()
        request.alarmLogs.forEach { log ->
            val rows = tx.execute(
                """
                INSERT IGNORE INTO alarm_logs
                (alarm_id, user_id, fired_at, action, device_id, log_hash, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                log.alarmId,
                userId,
                log.firedAt.toInstantUtc().toSqlTimestamp(),
                log.action,
                log.deviceId,
                log.logHash,
                now
            )
            if (rows > 0) inserted++ else ignored++
        }
        AlarmLogBatchResponse(inserted = inserted, ignored = ignored, serverTime = Instant.now(clock).toIsoUtc())
    }

    private fun upsertAlarm(tx: DSLContext, userId: Long, dto: AlarmSyncDto): AlarmSyncDto {
        val now = Instant.now(clock)
        val existing = tx.fetchOne(
            "SELECT id, created_at FROM alarms WHERE user_id = ? AND client_uuid = ? LIMIT 1",
            userId,
            dto.clientUuid
        )

        val createdAt = dto.createdAt?.toInstantUtc() ?: now
        if (existing == null) {
            tx.execute(
                """
                INSERT INTO alarms
                (user_id, client_uuid, type, title, note, trigger_time, duration_sec, repeat_weekdays,
                 repeat_month_days, anniversary_calendar, advance_notify_days, ringtone, vibrate,
                 volume_fade, snooze_minutes, label, color, enabled, start_date, end_date,
                 schedule_mode, alert_policy, time_anchor_mode, interval_months, interval_years,
                 template_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                userId,
                dto.clientUuid,
                dto.type,
                dto.title,
                dto.note,
                dto.triggerTime?.toInstantUtc()?.toSqlTimestamp(),
                dto.durationSec,
                dto.repeatWeekdays,
                dto.repeatMonthDays,
                dto.anniversaryCalendar,
                dto.advanceNotifyDays,
                dto.ringtone,
                if (dto.vibrate) 1 else 0,
                if (dto.volumeFade) 1 else 0,
                dto.snoozeMinutes,
                dto.label,
                dto.color,
                if (dto.enabled) 1 else 0,
                dto.startDate?.toInstantUtc()?.toSqlTimestamp(),
                dto.endDate?.toInstantUtc()?.toSqlTimestamp(),
                dto.scheduleMode,
                dto.alertPolicy,
                dto.timeAnchorMode,
                dto.intervalMonths.coerceAtLeast(1),
                dto.intervalYears.coerceAtLeast(1),
                dto.templateId,
                dto.status,
                createdAt.toSqlTimestamp(),
                now.toSqlTimestamp()
            )
            val serverId = (tx.fetchValue("SELECT LAST_INSERT_ID()") as? Number)?.toLong()
                ?: throw ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to insert alarm")
            return fetchAlarmById(tx, serverId)
        }

        val alarmId = existing.get("id", Long::class.java)!!
        tx.execute(
            """
            UPDATE alarms SET
              type = ?, title = ?, note = ?, trigger_time = ?, duration_sec = ?, repeat_weekdays = ?,
              repeat_month_days = ?, anniversary_calendar = ?, advance_notify_days = ?, ringtone = ?,
              vibrate = ?, volume_fade = ?, snooze_minutes = ?, label = ?, color = ?, enabled = ?,
              start_date = ?, end_date = ?, schedule_mode = ?, alert_policy = ?, time_anchor_mode = ?,
              interval_months = ?, interval_years = ?, template_id = ?, status = ?, updated_at = ?
            WHERE id = ? AND user_id = ?
            """.trimIndent(),
            dto.type,
            dto.title,
            dto.note,
            dto.triggerTime?.toInstantUtc()?.toSqlTimestamp(),
            dto.durationSec,
            dto.repeatWeekdays,
            dto.repeatMonthDays,
            dto.anniversaryCalendar,
            dto.advanceNotifyDays,
            dto.ringtone,
            if (dto.vibrate) 1 else 0,
            if (dto.volumeFade) 1 else 0,
            dto.snoozeMinutes,
            dto.label,
            dto.color,
            if (dto.enabled) 1 else 0,
            dto.startDate?.toInstantUtc()?.toSqlTimestamp(),
            dto.endDate?.toInstantUtc()?.toSqlTimestamp(),
            dto.scheduleMode,
            dto.alertPolicy,
            dto.timeAnchorMode,
            dto.intervalMonths.coerceAtLeast(1),
            dto.intervalYears.coerceAtLeast(1),
            dto.templateId,
            dto.status,
            now.toSqlTimestamp(),
            alarmId,
            userId
        )
        return fetchAlarmById(tx, alarmId)
    }

    fun fetchAlarmById(tx: DSLContext, alarmId: Long): AlarmSyncDto =
        tx.fetchOne("SELECT * FROM alarms WHERE id = ? LIMIT 1", alarmId)
            ?.let(::mapAlarm)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "alarm not found")

    fun mapAlarm(record: Record): AlarmSyncDto = AlarmSyncDto(
        id = record.get("id", Long::class.java),
        clientUuid = record.get("client_uuid", String::class.java)!!,
        type = record.get("type", Int::class.java) ?: 0,
        title = record.get("title", String::class.java) ?: "",
        note = record.get("note", String::class.java),
        triggerTime = record.get("trigger_time", Timestamp::class.java)?.toInstant()?.toIsoUtc(),
        durationSec = record.get("duration_sec", Int::class.java),
        repeatWeekdays = record.get("repeat_weekdays", Int::class.java) ?: 0,
        repeatMonthDays = record.get("repeat_month_days", Int::class.java) ?: 0,
        anniversaryCalendar = record.get("anniversary_calendar", Int::class.java) ?: 0,
        advanceNotifyDays = record.get("advance_notify_days", String::class.java),
        ringtone = record.get("ringtone", String::class.java),
        vibrate = (record.get("vibrate", Int::class.java) ?: 0) == 1,
        volumeFade = (record.get("volume_fade", Int::class.java) ?: 0) == 1,
        snoozeMinutes = record.get("snooze_minutes", Int::class.java) ?: 5,
        label = record.get("label", String::class.java),
        color = record.get("color", String::class.java),
        enabled = (record.get("enabled", Int::class.java) ?: 0) == 1,
        startDate = record.get("start_date", Timestamp::class.java)?.toInstant()?.toIsoUtc(),
        endDate = record.get("end_date", Timestamp::class.java)?.toInstant()?.toIsoUtc(),
        scheduleMode = record.get("schedule_mode", Int::class.java) ?: 0,
        alertPolicy = record.get("alert_policy", Int::class.java) ?: 0,
        timeAnchorMode = record.get("time_anchor_mode", Int::class.java) ?: 0,
        intervalMonths = record.get("interval_months", Int::class.java) ?: 1,
        intervalYears = record.get("interval_years", Int::class.java) ?: 1,
        templateId = record.get("template_id", String::class.java),
        status = record.get("status", Int::class.java) ?: 0,
        createdAt = record.get("created_at", Timestamp::class.java)?.toInstant()?.toIsoUtc(),
        updatedAt = record.get("updated_at", Timestamp::class.java)?.toInstant()?.toIsoUtc()
    )
}
