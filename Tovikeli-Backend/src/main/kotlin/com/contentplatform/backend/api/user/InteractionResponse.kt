package com.contentplatform.backend.api.user

import com.contentplatform.backend.application.interaction.HydratedInteraction
import com.contentplatform.backend.domain.interaction.Interaction
import com.contentplatform.backend.domain.author.Author

/**
 * API response DTO for one Interaction in user interaction history (Stage 19 + Stage 25).
 *
 * PublicId value objects are projected as their plain string value.
 * [InteractionType] is projected as its enum name.
 *
 * [content] is an optional lightweight hydrated representation of the
 * referenced content (Stage 25). null when the content does not exist.
 */
data class InteractionResponse(
    val id: String,
    val userId: String,
    val contentId: String,
    val type: String,
    val createdAt: Long,
    val content: HydratedContentResponse? = null
) {

    companion object {
        /** Maps one Domain [Interaction] aggregate to its API response projection (no hydration). */
        fun from(interaction: Interaction): InteractionResponse = InteractionResponse(
            id = interaction.id.value,
            userId = interaction.userId.value,
            contentId = interaction.contentId.value,
            type = interaction.type.name,
            createdAt = interaction.createdAt
        )

        /** Maps a [HydratedInteraction] to its API response projection with content hydration. */
        fun from(hydrated: HydratedInteraction, author: Author? = null): InteractionResponse = InteractionResponse(
            id = hydrated.interaction.id.value,
            userId = hydrated.interaction.userId.value,
            contentId = hydrated.interaction.contentId.value,
            type = hydrated.interaction.type.name,
            createdAt = hydrated.interaction.createdAt,
            content = hydrated.content?.let { HydratedContentResponse.from(it, author) }
        )
    }
}
