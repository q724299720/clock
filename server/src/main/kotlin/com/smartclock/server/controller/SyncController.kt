package com.smartclock.server.controller

import com.smartclock.server.dto.AlarmLogBatchRequest
import com.smartclock.server.dto.AlarmLogBatchResponse
import com.smartclock.server.dto.AlarmPullResponse
import com.smartclock.server.dto.AlarmPushRequest
import com.smartclock.server.dto.AlarmPushResponse
import com.smartclock.server.dto.BootstrapResponse
import com.smartclock.server.security.AuthUserPrincipal
import com.smartclock.server.service.SyncService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/sync")
class SyncController(
    private val syncService: SyncService
) {

    @GetMapping("/bootstrap")
    fun bootstrap(@AuthenticationPrincipal principal: AuthUserPrincipal): BootstrapResponse =
        syncService.bootstrap(principal.userId)

    @PostMapping("/alarms/push")
    fun pushAlarms(
        @AuthenticationPrincipal principal: AuthUserPrincipal,
        @Valid @RequestBody request: AlarmPushRequest
    ): AlarmPushResponse = syncService.pushAlarms(principal.userId, request)

    @GetMapping("/alarms/pull")
    fun pullAlarms(
        @AuthenticationPrincipal principal: AuthUserPrincipal,
        @RequestParam(required = false) since: String?
    ): AlarmPullResponse = syncService.pullAlarms(principal.userId, since)

    @PostMapping("/alarm-logs/batch")
    fun pushAlarmLogs(
        @AuthenticationPrincipal principal: AuthUserPrincipal,
        @Valid @RequestBody request: AlarmLogBatchRequest
    ): AlarmLogBatchResponse = syncService.uploadLogs(principal.userId, request)
}
