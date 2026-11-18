package com.contentplatform.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Password hashing configuration for platform-user credentials.
 *
 * Authentication/session behavior is intentionally not configured here yet.
 * Productization P1-B only requires password hashing for registration.
 */
@Configuration
class PasswordConfiguration {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
