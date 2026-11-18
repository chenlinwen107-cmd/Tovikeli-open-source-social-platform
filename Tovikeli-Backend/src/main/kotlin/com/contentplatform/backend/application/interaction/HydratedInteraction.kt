package com.contentplatform.backend.application.interaction

import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.interaction.Interaction

/**
 * An [Interaction] paired with its optional hydrated [Content].
 *
 * [content] is null when the referenced content does not exist in the
 * database. The [interaction] is always present regardless of content state.
 */
data class HydratedInteraction(
    val interaction: Interaction,
    val content: Content?
)
