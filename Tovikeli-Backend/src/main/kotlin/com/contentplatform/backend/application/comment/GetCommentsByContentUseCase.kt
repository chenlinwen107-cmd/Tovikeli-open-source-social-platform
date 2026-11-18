package com.contentplatform.backend.application.comment

import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentCursor
import com.contentplatform.backend.domain.comment.CommentPage
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CommentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Returns ACTIVE comments belonging to the given content (Stage 18 + Stage 26).
 *
 * Stage 18: delegates to [CommentRepository.findByContentId] with the
 * repository's frozen semantics: ACTIVE comments only, ordered by
 * (createdAt ASC, id ASC).
 *
 * Stage 26: accepts an optional [CommentCursor] for keyset pagination.
 * When a cursor is provided, the paginated repository method is called
 * and a [CommentPage] with nextCursor is returned.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetCommentsByContentUseCase(
    private val commentRepository: CommentRepository
) {
    fun getByContent(contentId: PublicId, limit: Int): List<Comment> =
        commentRepository.findByContentId(contentId, limit)

    fun getByContent(contentId: PublicId, limit: Int, cursor: CommentCursor?): CommentPage =
        commentRepository.findByContentId(contentId, limit, cursor)
}
