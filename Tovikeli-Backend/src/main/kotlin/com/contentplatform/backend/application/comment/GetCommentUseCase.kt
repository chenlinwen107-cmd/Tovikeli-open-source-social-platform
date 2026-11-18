package com.contentplatform.backend.application.comment

import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CommentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetCommentUseCase(
    private val commentRepository: CommentRepository
) {
    fun get(commentId: PublicId): Comment? =
        commentRepository.findById(commentId)
}
