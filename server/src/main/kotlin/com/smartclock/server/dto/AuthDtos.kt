package com.smartclock.server.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank val account: String,
    val isEmail: Boolean,
    @field:NotBlank @field:Size(min = 6, max = 128) val password: String,
    val nickname: String?
)

data class LoginRequest(
    @field:NotBlank val account: String,
    val isEmail: Boolean,
    @field:NotBlank val password: String
)

data class AuthResponse(
    val user: ApiUserDto,
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: String,
    val refreshTokenExpiresAt: String
)
