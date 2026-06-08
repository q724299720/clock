package com.smartclock.server.service

import com.smartclock.server.config.JwtProperties
import com.smartclock.server.dto.AuthResponse
import com.smartclock.server.dto.LoginRequest
import com.smartclock.server.dto.RefreshTokenRequest
import com.smartclock.server.dto.RegisterRequest
import com.smartclock.server.dto.toDto
import com.smartclock.server.model.Role
import com.smartclock.server.model.UserProfile
import com.smartclock.server.security.AuthUserPrincipal
import com.smartclock.server.security.JwtService
import com.smartclock.server.support.ApiException
import com.smartclock.server.support.sha256Hex
import com.smartclock.server.support.toIsoUtc
import com.smartclock.server.support.toSqlTimestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val dsl: DSLContext,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties,
    private val clock: Clock
) {

    fun register(request: RegisterRequest): AuthResponse = dsl.transactionResult { cfg ->
        val tx = DSL.using(cfg)
        if (findUserByAccount(tx, request.account, request.isEmail) != null) {
            throw ApiException(HttpStatus.CONFLICT, "account already exists")
        }
        val now = Instant.now(clock)
        val accountColumn = if (request.isEmail) "email" else "phone"
        tx.execute(
            """
            INSERT INTO users ($accountColumn, password_hash, nickname, role, status, created_at, updated_at)
            VALUES (?, ?, ?, 'USER', 0, ?, ?)
            """.trimIndent(),
            request.account,
            passwordEncoder.encode(request.password),
            request.nickname,
            now.toSqlTimestamp(),
            now.toSqlTimestamp()
        )
        val userId = (tx.fetchValue("SELECT LAST_INSERT_ID()") as? Number)?.toLong()
            ?: throw ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to create user")
        val user = findUserById(tx, userId) ?: throw ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "user not found after create")
        issueTokens(tx, user)
    }

    fun login(request: LoginRequest): AuthResponse = dsl.transactionResult { cfg ->
        val tx = DSL.using(cfg)
        val userRecord = findUserRecordByAccount(tx, request.account, request.isEmail)
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials")
        if ((userRecord.get("status", Int::class.java) ?: 1) != 0) {
            throw ApiException(HttpStatus.FORBIDDEN, "user is disabled")
        }
        val passwordHash = userRecord.get("password_hash", String::class.java)
            ?: throw ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials")
        if (!passwordEncoder.matches(request.password, passwordHash)) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "invalid credentials")
        }
        val user = mapUser(userRecord)
        val now = Instant.now(clock)
        tx.execute(
            "UPDATE users SET last_login_at = ?, updated_at = ? WHERE id = ?",
            now.toSqlTimestamp(),
            now.toSqlTimestamp(),
            user.id
        )
        issueTokens(tx, user)
    }

    fun refresh(request: RefreshTokenRequest): AuthResponse = dsl.transactionResult { cfg ->
        val tx = DSL.using(cfg)
        val now = Instant.now(clock)
        val tokenHash = hashRefreshToken(request.refreshToken)
        val record = tx.fetchOne(
            """
            SELECT rt.id AS refresh_id, rt.user_id, rt.expires_at, rt.revoked_at,
                   u.id, u.phone, u.email, u.nickname, u.role, u.status
            FROM refresh_tokens rt
            JOIN users u ON u.id = rt.user_id
            WHERE rt.token_hash = ?
            """.trimIndent(),
            tokenHash
        ) ?: throw ApiException(HttpStatus.UNAUTHORIZED, "invalid refresh token")
        if (record.get("revoked_at") != null || record.get("expires_at", java.sql.Timestamp::class.java)!!.toInstant().isBefore(now)) {
            throw ApiException(HttpStatus.UNAUTHORIZED, "refresh token expired")
        }
        val user = mapUser(record)
        if (user.status != 0) {
            throw ApiException(HttpStatus.FORBIDDEN, "user is disabled")
        }
        tx.execute(
            "UPDATE refresh_tokens SET revoked_at = ?, last_used_at = ? WHERE id = ?",
            now.toSqlTimestamp(),
            now.toSqlTimestamp(),
            record.get("refresh_id", Long::class.java),
        )
        issueTokens(tx, user)
    }

    fun logout(principal: AuthUserPrincipal, request: RefreshTokenRequest) {
        val tokenHash = hashRefreshToken(request.refreshToken)
        dsl.execute(
            "UPDATE refresh_tokens SET revoked_at = ? WHERE user_id = ? AND token_hash = ? AND revoked_at IS NULL",
            Instant.now(clock).toSqlTimestamp(),
            principal.userId,
            tokenHash
        )
    }

    fun me(principal: AuthUserPrincipal): UserProfile =
        findUserById(dsl, principal.userId) ?: throw ApiException(HttpStatus.NOT_FOUND, "user not found")

    private fun issueTokens(tx: DSLContext, user: UserProfile): AuthResponse {
        val access = jwtService.createAccessToken(user.id, user.role)
        val refreshToken = generateRefreshToken()
        val now = Instant.now(clock)
        val refreshExpiresAt = now.plusSeconds(jwtProperties.refreshTokenDays * 24 * 60 * 60)
        tx.execute(
            """
            INSERT INTO refresh_tokens (user_id, token_hash, expires_at, created_at, last_used_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            user.id,
            hashRefreshToken(refreshToken),
            refreshExpiresAt.toSqlTimestamp(),
            now.toSqlTimestamp(),
            now.toSqlTimestamp()
        )
        return AuthResponse(
            user = user.toDto(),
            accessToken = access.token,
            refreshToken = refreshToken,
            accessTokenExpiresAt = access.expiresAt.toIsoUtc(),
            refreshTokenExpiresAt = refreshExpiresAt.toIsoUtc()
        )
    }

    private fun generateRefreshToken(): String =
        "${UUID.randomUUID()}${UUID.randomUUID()}".replace("-", "")

    private fun hashRefreshToken(token: String): String = sha256Hex("${jwtProperties.refreshPepper}:$token")

    private fun findUserByAccount(tx: DSLContext, account: String, isEmail: Boolean): UserProfile? =
        findUserRecordByAccount(tx, account, isEmail)?.let(::mapUser)

    private fun findUserRecordByAccount(tx: DSLContext, account: String, isEmail: Boolean): Record? {
        val column = if (isEmail) "email" else "phone"
        return tx.fetchOne(
            """
            SELECT id, phone, email, nickname, role, status, password_hash
            FROM users
            WHERE $column = ?
            LIMIT 1
            """.trimIndent(),
            account
        )
    }

    private fun findUserById(tx: DSLContext, userId: Long): UserProfile? =
        tx.fetchOne(
            "SELECT id, phone, email, nickname, role, status FROM users WHERE id = ? LIMIT 1",
            userId
        )?.let(::mapUser)

    private fun mapUser(record: Record): UserProfile = UserProfile(
        id = record.get("id", Long::class.java)!!,
        phone = record.get("phone", String::class.java),
        email = record.get("email", String::class.java),
        nickname = record.get("nickname", String::class.java),
        role = Role.valueOf(record.get("role", String::class.java)!!),
        status = record.get("status", Int::class.java) ?: 0
    )
}
