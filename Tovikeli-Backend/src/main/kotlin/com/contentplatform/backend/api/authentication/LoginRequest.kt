package com.contentplatform.backend.api.authentication

/**
 * HTTP request for platform-user login.
 *
 * The raw password exists only at the API/application authentication boundary
 * and is never persisted or returned.
 */
data class LoginRequest(
    val username: String?,
    val password: String?
)
