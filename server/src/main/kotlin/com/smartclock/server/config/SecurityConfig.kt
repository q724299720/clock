package com.smartclock.server.config

import com.smartclock.server.security.JwtAuthenticationFilter
import com.smartclock.server.security.RestAccessDeniedHandler
import com.smartclock.server.security.RestAuthenticationEntryPoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val authEntryPoint: RestAuthenticationEntryPoint,
    private val accessDeniedHandler: RestAccessDeniedHandler
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(authEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
                    .permitAll()
                it.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
