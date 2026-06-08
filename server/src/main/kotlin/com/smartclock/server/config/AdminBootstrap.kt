package com.smartclock.server.config

import java.time.Clock
import java.time.Instant
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class AdminBootstrapConfig {

    @Bean
    fun adminBootstrapRunner(
        dsl: DSLContext,
        passwordEncoder: PasswordEncoder,
        clock: Clock,
        @Value("\${SMARTCLOCK_ADMIN_ACCOUNT:}") adminAccount: String,
        @Value("\${SMARTCLOCK_ADMIN_PASSWORD:}") adminPassword: String,
        @Value("\${SMARTCLOCK_ADMIN_IS_EMAIL:true}") adminIsEmail: Boolean
    ) = ApplicationRunner {
        if (adminAccount.isBlank() || adminPassword.isBlank()) {
            return@ApplicationRunner
        }

        val accountColumn = if (adminIsEmail) "email" else "phone"
        val existing = dsl.fetchOne(
            "SELECT 1 FROM users WHERE $accountColumn = ? AND role = 'ADMIN' LIMIT 1",
            adminAccount
        ) != null
        if (existing) {
            return@ApplicationRunner
        }

        val now = Instant.now(clock)
        dsl.execute(
            """
            INSERT INTO users ($accountColumn, password_hash, nickname, role, status, created_at, updated_at)
            VALUES (?, ?, 'System Admin', 'ADMIN', 0, ?, ?)
            """.trimIndent(),
            adminAccount,
            passwordEncoder.encode(adminPassword),
            java.sql.Timestamp.from(now),
            java.sql.Timestamp.from(now)
        )
    }
}
