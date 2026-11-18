package com.contentplatform.backend.domain.user

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId

/**
 * A Platform User aggregate.
 *
 * The Platform User is the future identity basis for Interaction -> Feed ->
 * Recommendation. It is a PLATFORM-native identity: it is NOT an external
 * Author, it has no password/login/JWT/OAuth/session concept and it has no
 * instance/source reference (Stage 16 scope).
 *
 * Identity rules (Stage 16):
 *  - [username] is the platform identity marker and is unique
 *    CASE-INSENSITIVELY. The case-insensitive uniqueness is guaranteed by the
 *    database (expression UNIQUE index on LOWER(username)) and is the final
 *    guarantee; the repository lookup and in-memory rules mirror the same
 *    LOWER semantics. The stored username is kept verbatim (exact case).
 *  - [displayName] is NOT an identity: it may repeat freely and is optional
 *    (null when absent).
 *  - [status] defaults to [UserStatus.ACTIVE] for a newly created user.
 *  - PublicId prefix is `usr_`. Like the other platform/external aggregates
 *    the id is derived deterministically from the identity value:
 *    usr_ + SHA-256(username) first 32 hex chars (same scheme as
 *    Tag.createPlatform uses for PLATFORM names). Two usernames that differ
 *    only in case can never coexist (case-insensitive uniqueness), so this
 *    derivation is injective over stored usernames.
 *
 * Timestamps are platform unix-ms Longs ([createdAt] ingestion time,
 * [updatedAt] last update), matching every other aggregate in the domain.
 */
data class User(
    // Core identity
    val id: PublicId,             // Public ID with usr_ prefix
    val username: String,         // Platform identity marker, case-insensitively unique, stored verbatim
    val displayName: String?,
    val avatar: String?,
    val bio: String?,
    val gender: String?,
    val location: String?,
    val cover: String?,     // Display name; not an identity, optional, may repeat

    // Lifecycle
    val status: UserStatus,       // ACTIVE (default for new users) or DISABLED

    // Timestamps
    val createdAt: Long,          // Platform creation timestamp (unix ms)
    val updatedAt: Long           // Last update timestamp (unix ms)
) {

    companion object {
        /**
         * Creates a new ACTIVE platform [User] from raw create input.
         *
         * The caller (HTTP boundary) owns trimming; this factory keeps the
         * stored [username] verbatim exactly as supplied and only enforces the
         * minimal non-blank invariant.
         *
         * @throws IllegalArgumentException when [username] is blank
         */
        fun create(username: String, displayName: String?): User {
            require(username.isNotBlank()) { "username must not be blank" }
            val now = System.currentTimeMillis()
            return User(
                id = PublicId("usr_" + IdentityHash.derive(username)),
                username = username,
                displayName = displayName,
                avatar = null,
                bio = null,
                gender = "secret",
                location = null,
                cover = null,
                status = UserStatus.ACTIVE,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
