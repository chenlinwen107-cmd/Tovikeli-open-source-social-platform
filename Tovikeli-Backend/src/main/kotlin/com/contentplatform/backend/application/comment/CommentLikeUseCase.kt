package com.contentplatform.backend.application.comment

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CommentLikeRepository
import com.contentplatform.backend.repository.CommentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

data class CommentLikeState(
    val liked: Boolean,
    val likeCount: Long
)

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class CommentLikeUseCase(
    private val commentRepository: CommentRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val transactionTemplate: TransactionTemplate
) {
    fun get(commentId: PublicId, userId: PublicId?): CommentLikeState =
        transactionTemplate.execute {
            if (commentRepository.findById(commentId) == null) {
                return@execute CommentLikeState(false, 0L)
            }
            CommentLikeState(
                liked = userId != null && commentLikeRepository.exists(userId, commentId),
                likeCount = commentLikeRepository.countByCommentId(commentId)
            )
        } ?: CommentLikeState(false, 0L)

    fun like(commentId: PublicId, userId: PublicId): CommentLikeState =
        transactionTemplate.execute {
            requireComment(commentId)
            commentLikeRepository.save(userId, commentId)
            CommentLikeState(true, commentLikeRepository.countByCommentId(commentId))
        } ?: CommentLikeState(true, 0L)

    fun unlike(commentId: PublicId, userId: PublicId): CommentLikeState =
        transactionTemplate.execute {
            requireComment(commentId)
            commentLikeRepository.delete(userId, commentId)
            CommentLikeState(false, commentLikeRepository.countByCommentId(commentId))
        } ?: CommentLikeState(false, 0L)

    private fun requireComment(commentId: PublicId) {
        if (commentRepository.findById(commentId) == null) {
            throw CommentNotFoundException("Comment $commentId does not exist")
        }
    }
}

class CommentNotFoundException(message: String) : RuntimeException(message)
