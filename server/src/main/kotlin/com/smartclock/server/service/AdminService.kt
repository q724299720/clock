package com.smartclock.server.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.smartclock.server.dto.AdminAuditLogDto
import com.smartclock.server.dto.AlarmLogAdminDto
import com.smartclock.server.dto.AlarmSyncDto
import com.smartclock.server.dto.ApiUserDto
import com.smartclock.server.dto.PageResponse
import com.smartclock.server.dto.UpdateAlarmRequest
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
class AdminService(
    private val dsl: DSLContext,
    private val authService: AuthService,
    private val syncService: SyncService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {

    fun listUsers(query: String?, page: Int, pageSize: Int): PageResponse<ApiUserDto> {
        val normalizedPage = page.coerceAtLeast(1)
        val normalizedPageSize = pageSize.coerceIn(10, 100)
        val offset = (normalizedPage - 1) * normalizedPageSize
        val records = if (query.isNullOrBlank()) {
            dsl.fetch(
                "SELECT id, phone, email, nickname, role, status FROM users ORDER BY id DESC LIMIT ? OFFSET ?",
                normalizedPageSize,
                offset
            )
        } else {
            val keyword = "%${query.trim()}%"
            dsl.fetch(
                """
                SELECT id, phone, email, nickname, role, status
                FROM users
                WHERE phone LIKE ? OR email LIKE ? OR nickname LIKE ?
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """.trimIndent(),
                keyword,
                keyword,
                keyword,
                normalizedPageSize,
                offset
            )
        }
        val total = if (query.isNullOrBlank()) {
            (dsl.fetchValue("SELECT COUNT(*) FROM users") as? Number)?.toInt() ?: 0
        } else {
            val keyword = "%${query.trim()}%"
            (dsl.fetchValue(
                """
                SELECT COUNT(*)
                FROM users
                WHERE phone LIKE ? OR email LIKE ? OR nickname LIKE ?
                """.trimIndent(),
                keyword,
                keyword,
                keyword
            ) as? Number)?.toInt() ?: 0
        }
        return PageResponse(
            items = records.map(::mapUser).map(UserProfile::toDto),
            total = total,
            page = normalizedPage,
            pageSize = normalizedPageSize
        )
    }

    fun getUser(userId: Long): ApiUserDto =
        authService.me(com.smartclock.server.security.AuthUserPrincipal(userId, com.smartclock.server.model.Role.USER)).toDto()

    fun updateUserStatus(adminUserId: Long, userId: Long, status: Int, ipAddress: String): ApiUserDto = dsl.transactionResult { cfg ->
        val tx = DSL.using(cfg)
        tx.execute(
            "UPDATE users SET status = ?, updated_at = ? WHERE id = ?",
            status,
            Instant.now(clock).toSqlTimestamp(),
            userId
        )
        logAudit(tx, adminUserId, "USER_STATUS_UPDATE", "USER", userId.toString(), mapOf("status" to status), ipAddress)
        tx.fetchOne("SELECT id, phone, email, nickname, role, status FROM users WHERE id = ?", userId)
            ?.let(::mapUser)
            ?.toDto()
            ?: throw ApiException(HttpStatus.NOT_FOUND, "user not found")
    }

    fun listAlarms(userId: Long?, query: String?, page: Int, pageSize: Int): PageResponse<AlarmSyncDto> {
        val normalizedPage = page.coerceAtLeast(1)
        val normalizedPageSize = pageSize.coerceIn(10, 100)
        val offset = (normalizedPage - 1) * normalizedPageSize
        val items = when {
            userId != null && query.isNullOrBlank() ->
                dsl.fetch(
                    "SELECT * FROM alarms WHERE user_id = ? ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                    userId,
                    normalizedPageSize,
                    offset
                )
            userId != null ->
                dsl.fetch(
                    """
                    SELECT * FROM alarms
                    WHERE user_id = ? AND (title LIKE ? OR client_uuid LIKE ?)
                    ORDER BY updated_at DESC
                    LIMIT ? OFFSET ?
                    """.trimIndent(),
                    userId,
                    "%${query!!.trim()}%",
                    "%${query.trim()}%",
                    normalizedPageSize,
                    offset
                )
            query.isNullOrBlank() ->
                dsl.fetch(
                    "SELECT * FROM alarms ORDER BY updated_at DESC LIMIT ? OFFSET ?",
                    normalizedPageSize,
                    offset
                )
            else ->
                dsl.fetch(
                    """
                    SELECT * FROM alarms
                    WHERE title LIKE ? OR client_uuid LIKE ?
                    ORDER BY updated_at DESC
                    LIMIT ? OFFSET ?
                    """.trimIndent(),
                    "%${query.trim()}%",
                    "%${query.trim()}%",
                    normalizedPageSize,
                    offset
                )
        }.map(syncService::mapAlarm)
        val total = when {
            userId != null && query.isNullOrBlank() ->
                (dsl.fetchValue("SELECT COUNT(*) FROM alarms WHERE user_id = ?", userId) as? Number)?.toInt() ?: 0
            userId != null ->
                (dsl.fetchValue(
                    """
                    SELECT COUNT(*)
                    FROM alarms
                    WHERE user_id = ? AND (title LIKE ? OR client_uuid LIKE ?)
                    """.trimIndent(),
                    userId,
                    "%${query!!.trim()}%",
                    "%${query.trim()}%"
                ) as? Number)?.toInt() ?: 0
            query.isNullOrBlank() ->
                (dsl.fetchValue("SELECT COUNT(*) FROM alarms") as? Number)?.toInt() ?: 0
            else ->
                (dsl.fetchValue(
                    """
                    SELECT COUNT(*)
                    FROM alarms
                    WHERE title LIKE ? OR client_uuid LIKE ?
                    """.trimIndent(),
                    "%${query.trim()}%",
                    "%${query.trim()}%"
                ) as? Number)?.toInt() ?: 0
        }
        return PageResponse(
            items = items,
            total = total,
            page = normalizedPage,
            pageSize = normalizedPageSize
        )
    }

    fun getAlarm(alarmId: Long): AlarmSyncDto =
        dsl.fetchOne("SELECT * FROM alarms WHERE id = ? LIMIT 1", alarmId)
            ?.let(syncService::mapAlarm)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "alarm not found")

    fun updateAlarm(adminUserId: Long, alarmId: Long, request: UpdateAlarmRequest, ipAddress: String): AlarmSyncDto = dsl.transactionResult { cfg ->
        val tx = DSL.using(cfg)
        val existing = tx.fetchOne("SELECT * FROM alarms WHERE id = ? LIMIT 1", alarmId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "alarm not found")
        tx.execute(
            """
            UPDATE alarms SET
              title = ?, note = ?, enabled = ?, status = ?, trigger_time = ?, start_date = ?, end_date = ?,
              schedule_mode = ?, alert_policy = ?, time_anchor_mode = ?, interval_months = ?, interval_years = ?,
              template_id = ?, updated_at = ?
            WHERE id = ?
            """.trimIndent(),
            request.title ?: existing.get("title", String::class.java),
            request.note ?: existing.get("note", String::class.java),
            request.enabled?.let { if (it) 1 else 0 } ?: existing.get("enabled", Int::class.java),
            request.status ?: existing.get("status", Int::class.java),
            request.triggerTime?.toInstantUtc()?.toSqlTimestamp() ?: existing.get("trigger_time", Timestamp::class.java),
            request.startDate?.toInstantUtc()?.toSqlTimestamp() ?: existing.get("start_date", Timestamp::class.java),
            request.endDate?.toInstantUtc()?.toSqlTimestamp() ?: existing.get("end_date", Timestamp::class.java),
            request.scheduleMode ?: existing.get("schedule_mode", Int::class.java),
            request.alertPolicy ?: existing.get("alert_policy", Int::class.java),
            request.timeAnchorMode ?: existing.get("time_anchor_mode", Int::class.java),
            request.intervalMonths ?: existing.get("interval_months", Int::class.java),
            request.intervalYears ?: existing.get("interval_years", Int::class.java),
            request.templateId ?: existing.get("template_id", String::class.java),
            Instant.now(clock).toSqlTimestamp(),
            alarmId
        )
        logAudit(tx, adminUserId, "ALARM_UPDATE", "ALARM", alarmId.toString(), request, ipAddress)
        tx.fetchOne("SELECT * FROM alarms WHERE id = ? LIMIT 1", alarmId)
            ?.let(syncService::mapAlarm)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "alarm not found")
    }

    fun softDeleteAlarm(adminUserId: Long, alarmId: Long, ipAddress: String) = dsl.transaction { cfg ->
        val tx = DSL.using(cfg)
        tx.execute(
            "UPDATE alarms SET status = 1, updated_at = ? WHERE id = ?",
            Instant.now(clock).toSqlTimestamp(),
            alarmId
        )
        logAudit(tx, adminUserId, "ALARM_SOFT_DELETE", "ALARM", alarmId.toString(), mapOf("status" to 1), ipAddress)
    }

    fun listAlarmLogs(userId: Long?, page: Int, pageSize: Int): PageResponse<AlarmLogAdminDto> {
        val normalizedPage = page.coerceAtLeast(1)
        val normalizedPageSize = pageSize.coerceIn(10, 100)
        val offset = (normalizedPage - 1) * normalizedPageSize
        val records = if (userId == null) {
            dsl.fetch(
                "SELECT * FROM alarm_logs ORDER BY fired_at DESC LIMIT ? OFFSET ?",
                normalizedPageSize,
                offset
            )
        } else {
            dsl.fetch(
                "SELECT * FROM alarm_logs WHERE user_id = ? ORDER BY fired_at DESC LIMIT ? OFFSET ?",
                userId,
                normalizedPageSize,
                offset
            )
        }
        val total = if (userId == null) {
            (dsl.fetchValue("SELECT COUNT(*) FROM alarm_logs") as? Number)?.toInt() ?: 0
        } else {
            (dsl.fetchValue("SELECT COUNT(*) FROM alarm_logs WHERE user_id = ?", userId) as? Number)?.toInt() ?: 0
        }
        return PageResponse(
            items = records.map { record ->
            AlarmLogAdminDto(
                id = record.get("id", Long::class.java)!!,
                alarmId = record.get("alarm_id", Long::class.java)!!,
                userId = record.get("user_id", Long::class.java)!!,
                firedAt = record.get("fired_at", Timestamp::class.java)!!.toInstant().toIsoUtc(),
                action = record.get("action", Int::class.java) ?: 0,
                deviceId = record.get("device_id", String::class.java) ?: "",
                logHash = record.get("log_hash", String::class.java) ?: ""
            )
            },
            total = total,
            page = normalizedPage,
            pageSize = normalizedPageSize
        )
    }

    fun listAuditLogs(page: Int, pageSize: Int): PageResponse<AdminAuditLogDto> {
        val normalizedPage = page.coerceAtLeast(1)
        val normalizedPageSize = pageSize.coerceIn(10, 100)
        val offset = (normalizedPage - 1) * normalizedPageSize
        val items = dsl.fetch(
            "SELECT * FROM admin_audit_logs ORDER BY created_at DESC LIMIT ? OFFSET ?",
            normalizedPageSize,
            offset
        ).map { record ->
            AdminAuditLogDto(
                id = record.get("id", Long::class.java)!!,
                adminUserId = record.get("admin_user_id", Long::class.java)!!,
                action = record.get("action", String::class.java) ?: "",
                targetType = record.get("target_type", String::class.java) ?: "",
                targetId = record.get("target_id", String::class.java),
                detailJson = record.get("detail_json", String::class.java),
                ipAddress = record.get("ip_address", String::class.java),
                createdAt = record.get("created_at", Timestamp::class.java)!!.toInstant().toIsoUtc()
            )
        }
        val total = (dsl.fetchValue("SELECT COUNT(*) FROM admin_audit_logs") as? Number)?.toInt() ?: 0
        return PageResponse(
            items = items,
            total = total,
            page = normalizedPage,
            pageSize = normalizedPageSize
        )
    }

    private fun logAudit(
        tx: DSLContext,
        adminUserId: Long,
        action: String,
        targetType: String,
        targetId: String?,
        detail: Any,
        ipAddress: String
    ) {
        tx.execute(
            """
            INSERT INTO admin_audit_logs
            (admin_user_id, action, target_type, target_id, detail_json, ip_address, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            adminUserId,
            action,
            targetType,
            targetId,
            objectMapper.writeValueAsString(detail),
            ipAddress,
            Instant.now(clock).toSqlTimestamp()
        )
    }

    private fun mapUser(record: Record): UserProfile = UserProfile(
        id = record.get("id", Long::class.java)!!,
        phone = record.get("phone", String::class.java),
        email = record.get("email", String::class.java),
        nickname = record.get("nickname", String::class.java),
        role = com.smartclock.server.model.Role.valueOf(record.get("role", String::class.java)!!),
        status = record.get("status", Int::class.java) ?: 0
    )
}
