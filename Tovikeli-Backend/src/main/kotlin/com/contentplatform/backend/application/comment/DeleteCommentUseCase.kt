package com.contentplatform.backend.application.comment

import com.contentplatform.backend.domain.comment.CommentStatus
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CommentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class DeleteCommentUseCase(
    private val commentRepository: CommentRepository,
    private val transactionTemplate: TransactionTemplate
) {
    fun delete(commentId: PublicId, userId: PublicId): Boolean =
        transactionTemplate.execute {
            val comment = commentRepository.findById(commentId)
                ?: return@execute false

            if (comment.status == CommentStatus.DELETED) {
                return@execute true
            }

            if (comment.userId != userId) {
                return@execute false
            }

            val deleted = comment.copy(
                status = CommentStatus.DELETED,
                updatedAt = System.currentTimeMillis()
            )

            commentRepository.update(deleted)
            true
        }
}
