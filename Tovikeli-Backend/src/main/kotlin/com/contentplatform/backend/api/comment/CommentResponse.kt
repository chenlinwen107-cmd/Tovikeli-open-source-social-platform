package com.contentplatform.backend.api.comment

import com.contentplatform.backend.domain.comment.Comment

/**
 * API response DTO for a Comment (Stage 21-A).
 *
 * Pure Comment projection with no Content / User / Author hydration.
 * PublicId value objects are projected as their plain string value.
 */
data class CommentResponse(
    val id: String,
    val userId: String,
    val contentId: String,
    val parentCommentId: String?,
    val text: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        /** Maps one Domain [Comment] aggregate to its API response projection. */
        fun from(comment: Comment): CommentResponse = CommentResponse(
            id = comment.id.value,
            userId = comment.userId.value,
            contentId = comment.contentId.value,
            parentCommentId = comment.parentCommentId?.value,
            text = comment.text,
            status = comment.status.name,
            createdAt = comment.createdAt,
            updatedAt = comment.updatedAt
        )
    }
}
