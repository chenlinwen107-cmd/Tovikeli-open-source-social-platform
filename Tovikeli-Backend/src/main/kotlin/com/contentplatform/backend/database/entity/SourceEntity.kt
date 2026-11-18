package com.contentplatform.backend.database.entity

/**
 * Persistence row model for the `source` table.
 *
 * Internal to the database layer; never returned above repository implementations.
 * Enum values are stored as TEXT (see V1 migration conventions).
 */
data class SourceEntity(
    val id: String,
    val name: String,
    val type: String,
    val enabled: Boolean
)
