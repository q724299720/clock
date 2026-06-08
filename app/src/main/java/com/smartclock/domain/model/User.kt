package com.smartclock.domain.model

data class User(
    val id: Long,
    val phone: String? = null,
    val email: String? = null,
    val nickname: String? = null,
    val avatarUrl: String? = null
)
