package com.smartclock.server.support

import java.time.Clock
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: String
)

@RestControllerAdvice
class ApiErrorHandler(
    private val clock: Clock
) {
    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ErrorResponse> =
        build(ex.status, ex.status.name, ex.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "invalid request"
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuth(ex: AuthenticationException): ResponseEntity<ErrorResponse> =
        build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", ex.message ?: "unauthorized")

    @ExceptionHandler(AccessDeniedException::class)
    fun handleDenied(ex: AccessDeniedException): ResponseEntity<ErrorResponse> =
        build(HttpStatus.FORBIDDEN, "FORBIDDEN", ex.message ?: "forbidden")

    @ExceptionHandler(Exception::class)
    fun handleOther(ex: Exception): ResponseEntity<ErrorResponse> =
        build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.message ?: "internal error")

    private fun build(status: HttpStatus, code: String, message: String): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(status).body(
            ErrorResponse(
                code = code,
                message = message,
                timestamp = Instant.now(clock).toIsoUtc()
            )
        )
}
