package com.contentplatform.backend.api.user

/**
 * API request DTO for creating a Platform User.
 *
 * POST /api/v1/users
 *
 * Password is accepted only at the HTTP/application boundary and is never
 * stored on or exposed by the User domain/API response.
 */
data class CreateUserRequest(
    val username: String?,
    val password: String?,
    val displayName: String?
)
