package com.contentplatform.backend.application.interaction

import com.contentplatform.backend.domain.interaction.InteractionCursor

/**
 * Page of [HydratedInteraction] items with keyset cursor.
 *
 * Mirrors [com.contentplatform.backend.domain.interaction.InteractionPage]
 * semantics but each item carries an optional hydrated Content.
 */
data class HydratedInteractionPage(
    val items: List<HydratedInteraction>,
    val nextCursor: InteractionCursor?
)
