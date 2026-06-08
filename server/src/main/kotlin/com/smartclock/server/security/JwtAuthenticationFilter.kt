package com.smartclock.server.security

import com.smartclock.server.model.Role
import com.smartclock.server.support.ApiException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")
        if (!header.isNullOrBlank() && header.startsWith("Bearer ")) {
            val token = header.removePrefix("Bearer ").trim()
            val principal = runCatching { jwtService.parse(token) }
                .getOrElse { throw ApiException(HttpStatus.UNAUTHORIZED, "invalid access token") }
            val authorities = listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))
            val authentication = UsernamePasswordAuthenticationToken(principal, token, authorities)
            SecurityContextHolder.getContext().authentication = authentication
        }
        filterChain.doFilter(request, response)
    }
}
