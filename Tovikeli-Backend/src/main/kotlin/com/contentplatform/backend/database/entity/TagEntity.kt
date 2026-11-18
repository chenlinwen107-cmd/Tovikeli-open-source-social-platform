package com.contentplatform.backend.database.entity

/**
 * Persistence row model for the `tag` table.
 *
 * Internal to the database layer; never returned above repository implementations.
 */
data class TagEntity(
    val id: String,
    val name: String,
    val normalizedName: String?,
    val origin: String,
    val sourceTagId: String?,
    val instanceId: String?,
    val sourceId: String?,
    val createdAt: Long,
    val updatedAt: Long
)
