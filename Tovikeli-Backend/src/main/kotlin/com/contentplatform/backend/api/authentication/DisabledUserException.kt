package com.contentplatform.backend.api.authentication

/**
 * Thrown when the authenticated platform user has [DISABLED][com.contentplatform.backend.domain.user.UserStatus.DISABLED]
 * status. Maps to HTTP 403 Forbidden.
 */
class DisabledUserException(message: String) : RuntimeException(message)
