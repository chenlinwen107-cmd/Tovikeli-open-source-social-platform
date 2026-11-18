package com.contentplatform.backend.api.authentication

import com.contentplatform.backend.application.authentication.AuthenticateUserUseCase
import com.contentplatform.backend.application.authentication.InvalidCredentialsException
import com.contentplatform.backend.application.authentication.DisabledUserAuthenticationException
import com.contentplatform.backend.api.user.UserResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * HTTP authentication endpoints.
 *
 * Production authentication is session-based:
 *
 *   HTTP login
 *       -> AuthenticateUserUseCase
 *       -> authenticated User
 *       -> server-side HttpSession
 *
 * The session contains only the platform-user public id.
 * CurrentCaller creation is handled separately by the authentication bridge.
 *
 * Logout is the inverse boundary:
 *
 *   HTTP logout
 *       -> invalidate the server-side HttpSession
 *
 * No session identifier is ever exposed, and no session is created by logout.
 *
 * Session invalidation (rather than deleting the [SessionAttributes.CURRENT_USER_ID]
 * attribute) is the authoritative operation: with JDBC-backed Spring Session it
 * removes the whole server-side session record, so a later request carrying the
 * stale session cookie can no longer authenticate.
 *
 * Both endpoints are public at the filter level: [AuthenticationFilter] never
 * blocks a request, it only populates a `CurrentCaller` when a valid session
 * exists. Logout therefore stays reachable (and idempotent) even when the
 * caller is not authenticated, which is required for a client whose session
 * has already expired.
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class AuthController(
    private val authenticateUserUseCase: AuthenticateUserUseCase
) {

    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<UserResponse> {
        val username = request.username?.trim().orEmpty()
        val password = request.password.orEmpty()

        if (username.isBlank() || password.isBlank()) {
            return ResponseEntity.status(401).build()
        }

        val user = authenticateUserUseCase.authenticate(
            username = username,
            password = password
        )

        val existingSession = httpRequest.getSession(false)
        if (existingSession != null) {
            httpRequest.changeSessionId()
        }

        val session = httpRequest.getSession(true)
        session.setAttribute(
            SessionAttributes.CURRENT_USER_ID,
            user.id.value
        )

        return ResponseEntity.ok(UserResponse.from(user))
    }

    /**
     * Ends the current session.
     *
     *  - POST /api/v1/auth/logout
     *
     * An existing session is invalidated (never merely cleared), so the
     * authentication state established by [login] is fully removed. When no
     * session exists the call is a safe no-op, which makes logout idempotent
     * for clients retrying after an expired session.
     *
     * The request is never authenticated as a side effect: logout does not
     * create a session ([HttpServletRequest.getSession] is called with
     * `create = false`) and never inspects the request body.
     *
     * Returns HTTP 204 No Content with an empty body; no session identifier,
     * user data, or other sensitive material is ever returned.
     */
    @PostMapping("/logout")
    fun logout(httpRequest: HttpServletRequest): ResponseEntity<Void> {
        val session = httpRequest.getSession(false)
        if (session != null) {
            session.invalidate()
        }
        return ResponseEntity.noContent().build()
    }
}
