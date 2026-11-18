package com.contentplatform.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Disables Spring Security's default web protection.
 *
 * WHY THIS EXISTS
 * ---------------
 * `spring-boot-starter-security` is on the classpath because Productization P1
 * needed [org.springframework.security.crypto.password.PasswordEncoder]
 * (see [PasswordConfiguration]). Pulling in that starter also pulls in Spring
 * Boot's `SecurityAutoConfiguration`, which — when no [SecurityFilterChain]
 * bean is declared — installs a default chain that rejects EVERY request with
 * `401` and a `WWW-Authenticate: Basic` challenge, using a randomly generated
 * in-memory user.
 *
 * That default chain was never compatible with this project's architecture:
 * authentication is implemented by the project's own servlet filter
 * ([com.contentplatform.backend.api.authentication.AuthenticationFilter]) on
 * top of a server-side HttpSession, and authorization decisions happen in
 * controllers through
 * [com.contentplatform.backend.api.authentication.CallerResolver]. Spring
 * Security is not used for authentication or authorization at all.
 *
 * Declaring this permissive chain makes Spring Boot back off its default and
 * hands the entire request flow back to the project's own filter, so:
 *  - static resources (the site root, the stylesheet and every JavaScript
 *    module) are served to anonymous users,
 *  - public API reads (`GET /api/v1/contents`, search, authors, tags, media,
 *    comments) work without a session,
 *  - protected endpoints are still rejected with 401 by `CallerResolver`
 *    whenever no authenticated session is present.
 *
 * DESIGN NOTES
 *  - `csrf` is disabled because this is a JSON API whose credentials travel in
 *    a SameSite session cookie, and because the session layer (P2/P3) was
 *    designed and verified around token-less requests; enabling Spring
 *    Security's CSRF would reject every state-changing request the frontend
 *    makes. This matches the pre-existing behaviour, not a new relaxation.
 *  - `httpBasic` / `formLogin` are disabled so no browser prompt or generated
 *    login page can appear.
 *  - `logout` is disabled because logout is owned by
 *    `POST /api/v1/auth/logout` (P3).
 *
 * Scope: this configuration deliberately adds no authentication logic. It only
 * removes a framework default that contradicts the project's existing design.
 */
@Configuration
class WebSecurityConfiguration {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .build()
}
