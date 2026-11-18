package com.contentplatform.backend.application.comment

import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentStatus
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CommentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class GetCommentUseCaseTest {

    private val commentId = PublicId("cmt_1234567890abcdef1234567890abcdef")
    private val userId = PublicId("usr_1234567890abcdef1234567890abcdef")
    private val contentId = PublicId("cnt_1234567890abcdef1234567890abcdef")

    @Test
    fun `get returns active comment`() {
        val comment = comment(CommentStatus.ACTIVE)
        val repository = FakeCommentRepository(comment)
        val useCase = GetCommentUseCase(repository)

        val result = useCase.get(commentId)

        assertSame(comment, result)
        assertEquals(commentId, repository.lastFindById)
        assertEquals(1, repository.findByIdCount)
    }

    @Test
    fun `get returns deleted comment`() {
        val comment = comment(CommentStatus.DELETED)
        val repository = FakeCommentRepository(comment)
        val useCase = GetCommentUseCase(repository)

        val result = useCase.get(commentId)

        assertSame(comment, result)
        assertEquals(commentId, repository.lastFindById)
        assertEquals(1, repository.findByIdCount)
    }

    @Test
    fun `get returns null when comment does not exist`() {
        val repository = FakeCommentRepository(null)
        val useCase = GetCommentUseCase(repository)

        val result = useCase.get(commentId)

        assertNull(result)
        assertEquals(commentId, repository.lastFindById)
        assertEquals(1, repository.findByIdCount)
    }

    private fun comment(status: CommentStatus): Comment =
        Comment(
            id = commentId,
            userId = userId,
            contentId = contentId,
            parentCommentId = null,
            text = "Test comment",
            status = status,
            createdAt = 1_000L,
            updatedAt = 1_000L
        )

    private class FakeCommentRepository(
        private val result: Comment?
    ) : CommentRepository {

        var findByIdCount = 0
            private set

        var lastFindById: PublicId? = null
            private set

        override fun save(comment: Comment): Comment =
            error("save should not be called")

        override fun findById(id: PublicId): Comment? {
            findByIdCount++
            lastFindById = id
            return result
        }

        override fun findByContentId(contentId: PublicId, limit: Int): List<Comment> =
            error("findByContentId should not be called")

        override fun findByParentCommentId(
            parentCommentId: PublicId,
            limit: Int
        ): List<Comment> =
            error("findByParentCommentId should not be called")

        override fun update(comment: Comment): Comment =
            error("update should not be called")
    }
}
