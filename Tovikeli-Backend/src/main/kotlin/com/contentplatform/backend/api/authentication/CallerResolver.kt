package com.contentplatform.backend.api.authentication

import com.contentplatform.backend.domain.user.CurrentCaller
import jakarta.servlet.http.HttpServletRequest

/**
 * Helper for extracting [CurrentCaller] from the HTTP request.
 *
 * Used by controllers to retrieve the authenticated identity established by
 * [AuthenticationFilter]. Returns null when no caller is present (public endpoints).
 * Throws [AuthenticationException] when a protected endpoint has no caller.
 */
object CallerResolver {

    /**
     * Returns the [CurrentCaller] from the request attribute, or null when
     * the request was not authenticated (public endpoint).
     */
    fun resolve(request: HttpServletRequest): CurrentCaller? {
        return request.getAttribute(AuthenticationFilter.CURRENT_CALLER_ATTRIBUTE) as? CurrentCaller
    }

    /**
     * Returns the [CurrentCaller] from the request attribute.
     * Throws [AuthenticationException] when no caller is present.
     *
     * Use this for protected endpoints that require authentication.
     */
    fun require(request: HttpServletRequest): CurrentCaller {
        return resolve(request)
            ?: throw AuthenticationException("Authentication required")
    }
}
