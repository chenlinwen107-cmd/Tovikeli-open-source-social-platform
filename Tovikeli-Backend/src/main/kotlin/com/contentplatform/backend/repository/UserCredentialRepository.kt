package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

/**
 * Persistence contract for platform-user authentication credentials.
 *
 * Credential data is intentionally kept separate from the User domain model.
 * Password hashes are authentication material and must never become part of
 * the public User aggregate/API representation.
 *
 * Transaction boundaries belong to Application use cases.
 */
interface UserCredentialRepository {

    /**
     * Returns the stored password hash for the case-insensitive username,
     * or null when the user does not exist or has no credential yet.
     */
    fun findPasswordHashByUsername(username: String): String?

    /**
     * Stores the password hash for an existing platform user.
     */
    fun savePasswordHash(userId: PublicId, passwordHash: String)
}
