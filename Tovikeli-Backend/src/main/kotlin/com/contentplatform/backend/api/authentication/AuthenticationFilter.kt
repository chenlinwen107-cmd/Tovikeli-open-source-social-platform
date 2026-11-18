package com.contentplatform.backend.api.authentication

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.CurrentCaller
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Servlet filter that establishes the authenticated platform identity from
 * the server-side [HttpSession][jakarta.servlet.http.HttpSession].
 *
 * On each request the filter looks for an existing session (without creating one)
 * and reads [SessionAttributes.CURRENT_USER_ID]. If present, the corresponding
 * [User][com.contentplatform.backend.domain.user.User] is loaded from the database
 * and a [CurrentCaller] is stored as a request attribute for downstream controllers.
 *
 * Behavior:
 * - No session or session attribute absent → no `CurrentCaller` is set.
 *   Public endpoints proceed normally; protected endpoints reject with 401 via
 *   [CallerResolver.require].
 * - Session attribute present but malformed → HTTP 401.
 * - Session attribute present but user not found → HTTP 401.
 * - Session attribute present but user is DISABLED → HTTP 403.
 * - Session attribute present and user is ACTIVE → `CurrentCaller` is set.
 *
 * This filter never creates a session. Session creation is performed exclusively
 * by [AuthController][com.contentplatform.backend.api.authentication.AuthController]
 * during the login flow.
 *
 * Active only when `spring.datasource.url` is configured (same condition as DatabaseConfig).
 */
@Component
@Order(1)
@ConditionalOnProperty(name = ["spring.datasource.url"])
class AuthenticationFilter(
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    companion object {
        /** Request attribute key under which the [CurrentCaller] is stored. */
        const val CURRENT_CALLER_ATTRIBUTE = "currentCaller"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val session = request.getSession(false)
        val currentUserId = session?.getAttribute(SessionAttributes.CURRENT_USER_ID) as? String

        if (currentUserId.isNullOrBlank()) {
            // No session or no stored identity. Public endpoints will proceed;
            // protected endpoints will reject with 401 via CallerResolver.require.
            filterChain.doFilter(request, response)
            return
        }

        val user = try {
            userRepository.findById(PublicId(currentUserId))
        } catch (_: IllegalArgumentException) {
            // Malformed PublicId format stored in session
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid user identity")
            return
        }

        if (user == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found")
            return
        }

        if (user.status == UserStatus.DISABLED) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "User is disabled")
            return
        }

        val caller = CurrentCaller(
            userId = user.id,
            username = user.username,
            status = user.status
        )
        request.setAttribute(CURRENT_CALLER_ATTRIBUTE, caller)

        filterChain.doFilter(request, response)
    }
}
