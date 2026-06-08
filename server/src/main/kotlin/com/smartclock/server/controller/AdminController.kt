package com.smartclock.server.controller

import com.smartclock.server.dto.AdminAuditLogDto
import com.smartclock.server.dto.AlarmLogAdminDto
import com.smartclock.server.dto.AlarmSyncDto
import com.smartclock.server.dto.ApiUserDto
import com.smartclock.server.dto.PageResponse
import com.smartclock.server.dto.StatusMessageResponse
import com.smartclock.server.dto.UpdateAlarmRequest
import com.smartclock.server.dto.UpdateUserStatusRequest
import com.smartclock.server.security.AuthUserPrincipal
import com.smartclock.server.service.AdminService
import com.smartclock.server.support.resolveClientIp
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val adminService: AdminService
) {

    @GetMapping("/users")
    fun listUsers(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): PageResponse<ApiUserDto> = adminService.listUsers(q, page, pageSize)

    @GetMapping("/users/{id}")
    fun getUser(@PathVariable id: Long): ApiUserDto = adminService.getUser(id)

    @PatchMapping("/users/{id}/status")
    fun updateUserStatus(
        @AuthenticationPrincipal principal: AuthUserPrincipal,
        @PathVariable id: Long,
        @RequestBody request: UpdateUserStatusRequest,
        servletRequest: HttpServletRequest
    ): ApiUserDto = adminService.updateUserStatus(principal.userId, id, request.status, resolveClientIp(servletRequest))

    @GetMapping("/alarms")
    fun listAlarms(
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): PageResponse<AlarmSyncDto> = adminService.listAlarms(userId, q, page, pageSize)

    @GetMapping("/alarms/{id}")
    fun getAlarm(@PathVariable id: Long): AlarmSyncDto = adminService.getAlarm(id)

    @PatchMapping("/alarms/{id}")
    fun updateAlarm(
        @AuthenticationPrincipal principal: AuthUserPrincipal,
        @PathVariable id: Long,
        @RequestBody request: UpdateAlarmRequest,
        servletRequest: HttpServletRequest
    ): AlarmSyncDto = adminService.updateAlarm(principal.userId, id, request, resolveClientIp(servletRequest))

    @DeleteMapping("/alarms/{id}")
    fun deleteAlarm(
        @AuthenticationPrincipal principal: AuthUserPrincipal,
        @PathVariable id: Long,
        servletRequest: HttpServletRequest
    ): StatusMessageResponse {
        adminService.softDeleteAlarm(principal.userId, id, resolveClientIp(servletRequest))
        return StatusMessageResponse("ok")
    }

    @GetMapping("/alarm-logs")
    fun listAlarmLogs(
        @RequestParam(required = false) userId: Long?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): PageResponse<AlarmLogAdminDto> = adminService.listAlarmLogs(userId, page, pageSize)

    @GetMapping("/audit-logs")
    fun listAuditLogs(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int
    ): PageResponse<AdminAuditLogDto> = adminService.listAuditLogs(page, pageSize)
}
