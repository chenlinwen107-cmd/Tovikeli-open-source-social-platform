package com.contentplatform.backend.application.comment

import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentCursor
import com.contentplatform.backend.domain.comment.CommentPage
import com.contentplatform.backend.domain.comment.CommentStatus
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CommentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Returns ACTIVE direct replies belonging to the given parent comment
 * (Stage 21 + Stage 26).
 *
 * Before fetching replies the use case verifies that the parent comment
 * exists and is ACTIVE. A missing or DELETED parent always yields an
 * empty list, even when ACTIVE replies exist in the database.
 *
 * Stage 26: accepts an optional [CommentCursor] for keyset pagination.
 * When a cursor is provided, the paginated repository method is called
 * and a [CommentPage] with nextCursor is returned.
 *
 * Regression protection: Stage 21 frozen behaviour is preserved:
 * DELETED parent + ACTIVE replies must return [] without querying replies.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetCommentRepliesUseCase(
    private val commentRepository: CommentRepository
) {
    fun getReplies(parentCommentId: PublicId, limit: Int): List<Comment> {
        val parent = commentRepository.findById(parentCommentId) ?: return emptyList()
        if (parent.status != CommentStatus.ACTIVE) return emptyList()
        return commentRepository.findByParentCommentId(parentCommentId, limit)
    }

    fun getReplies(parentCommentId: PublicId, limit: Int, cursor: CommentCursor?): CommentPage {
        val parent = commentRepository.findById(parentCommentId) ?: return CommentPage(emptyList(), null)
        if (parent.status != CommentStatus.ACTIVE) return CommentPage(emptyList(), null)
        return commentRepository.findByParentCommentId(parentCommentId, limit, cursor)
    }
}