package com.smartclock.server.security

import com.smartclock.server.config.JwtProperties
import com.smartclock.server.model.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import java.time.Clock
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey
import org.springframework.stereotype.Component

data class AccessTokenBundle(
    val token: String,
    val expiresAt: Instant
)

@Component
class JwtService(
    private val properties: JwtProperties,
    private val clock: Clock
) {
    private val key: SecretKey by lazy {
        val secretBytes = runCatching { Decoders.BASE64.decode(properties.secret) }
            .getOrElse { properties.secret.toByteArray(Charsets.UTF_8) }
        Keys.hmacShaKeyFor(secretBytes.copyOf(32.coerceAtLeast(secretBytes.size)))
    }

    fun createAccessToken(userId: Long, role: Role): AccessTokenBundle {
        val now = Instant.now(clock)
        val expiresAt = now.plusSeconds(properties.accessTokenMinutes * 60)
        val token = Jwts.builder()
            .issuer(properties.issuer)
            .subject(userId.toString())
            .claim("role", role.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
        return AccessTokenBundle(token = token, expiresAt = expiresAt)
    }

    fun parse(token: String): AuthUserPrincipal {
        val claims = parser().parseSignedClaims(token).payload
        return AuthUserPrincipal(
            userId = claims.subject.toLong(),
            role = Role.valueOf(claims["role"] as String)
        )
    }

    fun expiresAt(token: String): Instant = parser().parseSignedClaims(token).payload.expiration.toInstant()

    private fun parser() = Jwts.parser().verifyWith(key).requireIssuer(properties.issuer).build()
}
