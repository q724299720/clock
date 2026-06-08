package com.smartclock.server.model

data class UserProfile(
    val id: Long,
    val phone: String?,
    val email: String?,
    val nickname: String?,
    val role: Role,
    val status: Int
)
