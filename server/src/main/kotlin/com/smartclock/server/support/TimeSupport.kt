package com.smartclock.server.support

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.sql.Timestamp
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

fun Instant.toIsoUtc(): String = ISO_FORMATTER.format(this)

fun String.toInstantUtc(): Instant = Instant.parse(this)

fun Instant.toSqlTimestamp(): Timestamp = Timestamp.from(this)

fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

@Configuration
class TimeConfig {
    @Bean
    fun utcClock(): Clock = Clock.system(ZoneOffset.UTC)
}
