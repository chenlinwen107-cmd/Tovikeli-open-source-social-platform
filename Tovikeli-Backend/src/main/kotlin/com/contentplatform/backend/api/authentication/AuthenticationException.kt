package com.contentplatform.backend.api.authentication

/**
 * Thrown when the authentication credential is missing, malformed, or does not
 * resolve to a known platform user. Maps to HTTP 401 Unauthorized.
 */
class AuthenticationException(message: String) : RuntimeException(message)
