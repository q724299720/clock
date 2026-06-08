package com.smartclock.server.support

import jakarta.servlet.http.HttpServletRequest

fun resolveClientIp(request: HttpServletRequest): String =
    request.getHeader("X-Real-IP")
        ?: request.getHeader("X-Forwarded-For")?.substringBefore(",")?.trim()
        ?: request.remoteAddr
