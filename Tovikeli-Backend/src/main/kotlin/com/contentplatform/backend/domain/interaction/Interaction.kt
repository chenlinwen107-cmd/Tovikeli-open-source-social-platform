package com.contentplatform.backend.domain.interaction

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId

/**
 * A Platform Interaction aggregate (Stage 17 Platform Interaction Foundation).
 *
 * An Interaction is one platform user acting on one content aggregate with
 * exactly one [InteractionType]. It is the persistence/domain foundation for
 * later Feed / Recommendation stages; no API is exposed in Stage 17.
 *
 * Identity rules:
 *  - Interaction identity is the natural-key tuple
 *    (userId, contentId, type): a user may interact with one content at most
 *    once per type.
 *  - PublicId prefix is `int_`. The id is DERIVED DETERMINISTICALLY from the
 *    identity tuple — never from mutable display/user data:
 *      int_ + SHA-256(userId + ":" + contentId + ":" + type) first 32 hex
 *    The same (userId, contentId, type) always produces the same id; different
 *    tuples produce different ids.
 *  - [userId] references a Platform User ([com.contentplatform.backend.domain.user.User]).
 *  - [contentId] references a [com.contentplatform.backend.domain.content.Content].
 *
 * Timestamps are platform unix-ms Longs ([createdAt] creation time), matching
 * every other aggregate in the domain. No mutable fields exist in Stage 17.
 */
data class Interaction(
    // Core identity
    val id: PublicId,             // Public ID with int_ prefix
    val userId: PublicId,         // Platform user reference (User.id)
    val contentId: PublicId,      // Content reference (Content.id)
    val type: InteractionType,    // LIKE / FAVORITE / REPOST / BOOKMARK

    // Timestamps
    val createdAt: Long           // Platform creation timestamp (unix ms)
) {

    companion object {
        /**
         * Creates a new [Interaction] for the identity tuple
         * ([userId], [contentId], [type]).
         *
         * The PublicId is derived deterministically from the tuple; creating
         * the same tuple twice yields equal ids. No database round-trip is
         * involved in identity derivation.
         */
        fun create(
            userId: PublicId,
            contentId: PublicId,
            type: InteractionType
        ): Interaction {
            val idSeed = "${userId.value}:${contentId.value}:${type.name}"
            return Interaction(
                id = PublicId("int_" + IdentityHash.derive(idSeed)),
                userId = userId,
                contentId = contentId,
                type = type,
                createdAt = System.currentTimeMillis()
            )
        }
    }
}
