package com.contentplatform.backend.database.entity

/**
 * Persistence row model for the `author` table.
 *
 * Internal to the database layer; never returned above repository implementations.
 * Author has no createdAt/updatedAt in the Domain model, so the row has none either.
 */
data class AuthorEntity(
    val id: String,
    val instanceId: String,
    val sourceAuthorId: String,
    val name: String,
    val username: String,
    val displayName: String?,
    val url: String?,
    val avatar: String?,
    val bio: String?,
    val status: String?
)
