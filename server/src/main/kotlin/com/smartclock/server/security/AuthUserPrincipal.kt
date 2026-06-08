package com.smartclock.server.security

import com.smartclock.server.model.Role

data class AuthUserPrincipal(
    val userId: Long,
    val role: Role
)
