package com.smartclock.server.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.smartclock.server.support.ErrorResponse
import com.smartclock.server.support.toIsoUtc
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Clock
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class RestAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse(
                    code = "UNAUTHORIZED",
                    message = authException.message ?: "unauthorized",
                    timestamp = Instant.now(clock).toIsoUtc()
                )
            )
        )
    }
}

@Component
class RestAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        response.status = HttpStatus.FORBIDDEN.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse(
                    code = "FORBIDDEN",
                    message = accessDeniedException.message ?: "forbidden",
                    timestamp = Instant.now(clock).toIsoUtc()
                )
            )
        )
    }
}
