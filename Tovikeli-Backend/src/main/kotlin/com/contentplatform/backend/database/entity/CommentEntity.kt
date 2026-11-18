package com.contentplatform.backend.database.entity

/**
 * Persistence row model for the `content_comment` table.
 *
 * Internal to the database layer; never returned above repository
 * implementations.
 */
data class CommentEntity(
    val id: String,
    val userId: String,
    val contentId: String,
    val parentCommentId: String?,
    val text: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
