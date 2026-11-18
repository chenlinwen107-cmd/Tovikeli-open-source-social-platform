package com.contentplatform.backend.domain.comment

import com.contentplatform.backend.domain.common.PublicId
import java.util.UUID

/**
 * A platform comment attached to one content aggregate.
 *
 * A Comment is distinct from Interaction:
 * - one user may create multiple comments on the same content
 * - a comment may optionally reply to another comment
 * - comments have their own lifecycle: ACTIVE / DELETED
 *
 * A top-level comment has a null [parentCommentId].
 * A reply has a non-null [parentCommentId].
 *
 * Comment identity is entity-based and therefore does not derive from
 * (userId, contentId): the same user may create multiple comments
 * on the same content.
 *
 * Timestamps are platform unix-ms Longs, matching the existing domain.
 */
data class Comment(
    val id: PublicId,
    val userId: PublicId,
    val contentId: PublicId,
    val parentCommentId: PublicId?,
    val text: String,
    val status: CommentStatus,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        /**
         * Creates a new ACTIVE comment.
         *
         * Comment identity is unique per entity and is not derived from
         * user/content identity, because one user may create multiple
         * comments on the same content.
         */
        fun create(
            userId: PublicId,
            contentId: PublicId,
            parentCommentId: PublicId?,
            text: String
        ): Comment {
            require(text.isNotBlank()) { "comment text must not be blank" }

            val now = System.currentTimeMillis()

            return Comment(
                id = PublicId("cmt_" + UUID.randomUUID().toString().replace("-", "")),
                userId = userId,
                contentId = contentId,
                parentCommentId = parentCommentId,
                text = text,
                status = CommentStatus.ACTIVE,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
