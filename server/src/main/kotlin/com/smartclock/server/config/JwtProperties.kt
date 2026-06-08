package com.smartclock.server.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "smartclock.jwt")
data class JwtProperties(
    val issuer: String,
    val accessTokenMinutes: Long,
    val refreshTokenDays: Long,
    val secret: String,
    val refreshPepper: String
)
