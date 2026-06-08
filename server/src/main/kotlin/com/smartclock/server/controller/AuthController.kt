package com.smartclock.server.controller

import com.smartclock.server.dto.AuthResponse
import com.smartclock.server.dto.LoginRequest
import com.smartclock.server.dto.RefreshTokenRequest
import com.smartclock.server.dto.RegisterRequest
import com.smartclock.server.dto.StatusMessageResponse
import com.smartclock.server.dto.toDto
import com.smartclock.server.security.AuthUserPrincipal
import com.smartclock.server.service.AuthService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): AuthResponse =
        authService.register(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): AuthResponse =
        authService.login(request)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): AuthResponse =
        authService.refresh(request)

    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal principal: AuthUserPrincipal,
        @Valid @RequestBody request: RefreshTokenRequest
    ): StatusMessageResponse {
        authService.logout(principal, request)
        return StatusMessageResponse("ok")
    }
}
