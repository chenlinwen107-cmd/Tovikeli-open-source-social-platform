package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User

/**
 * Aggregate data access contract for a Platform [User].
 *
 * Repository contracts are transaction-agnostic. Transaction boundaries belong
 * to Application use cases, not to this interface.
 *
 * Stage 16 semantics:
 *  - username identity lookups are CASE-INSENSITIVE (LOWER semantics), mirroring
 *    the database expression UNIQUE index on LOWER(username). The stored
 *    username value is verbatim; only the uniqueness/identity comparison is
 *    case-insensitive.
 *  - [save] performs a strict INSERT (a create must never overwrite an
 *    existing user). Case-insensitive uniqueness violations are answered by
 *    the database (the final guarantee); callers are expected to run the
 *    case-insensitive existence check before saving.
 */
interface UserRepository {

    /** Returns the platform user with the given public id, or null when absent. */
    fun findById(id: PublicId): User?

    /**
     * Case-insensitive username identity lookup.
     *
     * Matches users whose LOWER(username) equals LOWER([username]).
     */
    fun findByUsernameCaseInsensitive(username: String): User?

    /** Returns active users matching username or display name, ordered by identity match. */
    fun search(query: String, limit: Int): List<User>

    /** Persists a newly created user aggregate and returns the persisted aggregate. */
    fun save(user: User): User

    fun updateProfile(user: User): User
}
