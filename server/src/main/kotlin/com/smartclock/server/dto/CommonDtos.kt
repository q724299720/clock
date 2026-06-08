package com.smartclock.server.dto

import com.smartclock.server.model.UserProfile
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

data class ApiUserDto(
    val id: Long,
    val phone: String?,
    val email: String?,
    val nickname: String?,
    val role: String,
    val status: Int
)

fun UserProfile.toDto(): ApiUserDto = ApiUserDto(
    id = id,
    phone = phone,
    email = email,
    nickname = nickname,
    role = role.name,
    status = status
)

data class StatusMessageResponse(
    val message: String
)

data class PageResponse<T>(
    val items: List<T>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

data class RefreshTokenRequest(
    @field:NotBlank val refreshToken: String
)

data class SinceRequest(
    val since: String?
)
