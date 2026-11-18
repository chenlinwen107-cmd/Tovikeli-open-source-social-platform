package com.contentplatform.backend.database.entity

/**
 * Persistence row model for the `content_interaction` table.
 *
 * Internal to the database layer; never returned above repository implementations.
 * type carries the [com.contentplatform.backend.domain.interaction.InteractionType]
 * name (LIKE / FAVORITE / REPOST / BOOKMARK), exactly like the existing enum-TEXT
 * persistence mapping.
 */
data class InteractionEntity(
    val id: String,
    val userId: String,
    val contentId: String,
    val type: String,
    val createdAt: Long
)
