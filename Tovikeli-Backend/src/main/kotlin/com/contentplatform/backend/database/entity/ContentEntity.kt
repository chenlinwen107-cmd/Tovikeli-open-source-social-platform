package com.contentplatform.backend.database.entity

/**
 * Persistence row model for the `content` table.
 *
 * Internal to the database layer; never returned above repository implementations.
 * mediaIds is NOT part of the content row; it is stored in `content_media` and
 * reconstructed by the repository before mapping back to the Domain aggregate.
 */
data class ContentEntity(
    val id: String,
    val instanceId: String,
    val sourceContentId: String,
    val authorId: String,
    val sourceId: String,
    val text: String?,
    val language: String?,
    val visibility: String,
    val status: String,
    val parentId: String?,
    val repostOfId: String?,
    val quoteOfId: String?,
    val topicId: String?,
    val externalCreatedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val metadataJson: String
)
