package com.contentplatform.backend.api.interaction

import com.contentplatform.backend.domain.interaction.UserContentInteractionState

/**
 * API response DTO for a single user's current Interaction state on one
 * Content (Stage 32).
 *
 * All four Boolean fields are always present. No timestamps, no full
 * Interaction objects.
 */
data class UserContentInteractionStateResponse(
    val contentId: String,
    val like: Boolean,
    val favorite: Boolean,
    val repost: Boolean,
    val bookmark: Boolean
) {
    companion object {
        fun from(state: UserContentInteractionState): UserContentInteractionStateResponse =
            UserContentInteractionStateResponse(
                contentId = state.contentId.value,
                like = state.like,
                favorite = state.favorite,
                repost = state.repost,
                bookmark = state.bookmark
            )
    }
}
