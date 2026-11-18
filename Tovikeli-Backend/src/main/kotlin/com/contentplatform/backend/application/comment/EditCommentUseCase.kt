package com.contentplatform.backend.application.comment

import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentStatus
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CommentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Edits the text of an existing ACTIVE comment (Stage 37).
 *
 * Only the comment owner may edit the comment. Only ACTIVE comments are
 * editable; DELETED comments are rejected.
 *
 * Ownership and lifecycle checks are performed inside a transaction to
 * prevent stale reads and lost updates.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class EditCommentUseCase(
    private val commentRepository: CommentRepository,
    private val transactionTemplate: TransactionTemplate
) {
    fun edit(commentId: PublicId, userId: PublicId, newText: String): EditCommentResult =
        transactionTemplate.execute {
            val comment = commentRepository.findById(commentId)
                ?: return@execute EditCommentResult.NotFound

            if (comment.status == CommentStatus.DELETED) {
                return@execute EditCommentResult.Rejected
            }

            if (comment.userId != userId) {
                return@execute EditCommentResult.Forbidden
            }

            val updated = comment.copy(
                text = newText,
                updatedAt = System.currentTimeMillis()
            )

            commentRepository.update(updated)
            EditCommentResult.Success(updated)
        }
}

/**
 * Outcome of an edit-comment operation.
 */
sealed class EditCommentResult {
    /** The comment was successfully updated. */
    data class Success(val comment: Comment) : EditCommentResult()
    /** No comment with the given id exists. */
    data object NotFound : EditCommentResult()
    /** The requesting user is not the comment owner. */
    data object Forbidden : EditCommentResult()
    /** The comment is DELETED and therefore not editable. */
    data object Rejected : EditCommentResult()
}
