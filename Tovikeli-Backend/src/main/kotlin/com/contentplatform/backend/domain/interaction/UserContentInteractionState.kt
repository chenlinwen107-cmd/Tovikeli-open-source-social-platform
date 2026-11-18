package com.contentplatform.backend.domain.interaction

import com.contentplatform.backend.domain.common.PublicId

/**
 * Read-side projection for a single user's current Interaction state on
 * one Content aggregate (Stage 32).
 *
 * Exposes the four platform interaction types (LIKE, FAVORITE, REPOST,
 * BOOKMARK) as Boolean flags. A true flag means the corresponding
 * Interaction row exists; false means it does not.
 *
 * Not a persistent entity; no mutable fields. Constructed from a list of
 * [Interaction] rows returned by a single batch query.
 */
data class UserContentInteractionState(
    val contentId: PublicId,
    val like: Boolean,
    val favorite: Boolean,
    val repost: Boolean,
    val bookmark: Boolean
) {
    companion object {
        /**
         * Builds a [UserContentInteractionState] from the list of
         * [Interaction] rows for one user/content pair.
         *
         * The list is expected to contain at most one row per type. If a type
         * is absent from the list, its flag is false.
         */
        fun fromInteractions(contentId: PublicId, interactions: List<Interaction>): UserContentInteractionState {
            val types = interactions.map { it.type }.toSet()
            return UserContentInteractionState(
                contentId = contentId,
                like = InteractionType.LIKE in types,
                favorite = InteractionType.FAVORITE in types,
                repost = InteractionType.REPOST in types,
                bookmark = InteractionType.BOOKMARK in types
            )
        }
    }
}
