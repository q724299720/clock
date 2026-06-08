package com.smartclock.server.controller

import com.smartclock.server.dto.ApiUserDto
import com.smartclock.server.dto.toDto
import com.smartclock.server.security.AuthUserPrincipal
import com.smartclock.server.service.AuthService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class MeController(
    private val authService: AuthService
) {

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: AuthUserPrincipal): ApiUserDto =
        authService.me(principal).toDto()
}
