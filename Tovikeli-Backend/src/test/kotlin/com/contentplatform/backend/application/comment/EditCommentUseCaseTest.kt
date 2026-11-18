package com.contentplatform.backend.application.comment

import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentStatus
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CommentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.AbstractPlatformTransactionManager

class EditCommentUseCaseTest {

    private val commentId = PublicId("cmt_test_comment")
    private val ownerId = PublicId("usr_owner")
    private val otherUserId = PublicId("usr_other")
    private val contentId = PublicId("ct_test_content")

    @Test
    fun `edit active comment by owner updates text`() {
        val comment = comment(CommentStatus.ACTIVE, "Old text")
        val repository = FakeCommentRepository(comment)
        val transactionManager = RecordingTransactionManager()
        val useCase = EditCommentUseCase(repository, transactionTemplate(transactionManager))

        val result = useCase.edit(commentId, ownerId, "New text")

        assertTrue(result is EditCommentResult.Success)
        val success = result as EditCommentResult.Success
        assertEquals("New text", success.comment.text)
        assertEquals(1, transactionManager.commitCount)
        assertEquals(0, transactionManager.rollbackCount)
        assertEquals(1, repository.findByIdCount)
        assertEquals(1, repository.updateCount)
        assertEquals("New text", repository.updatedComment?.text)
        assertEquals(commentId, repository.updatedComment?.id)
        assertEquals(ownerId, repository.updatedComment?.userId)
        assertNotNull(repository.updatedComment?.updatedAt)
        assertTrue(repository.updatedComment!!.updatedAt >= comment.updatedAt)
    }

    @Test
    fun `edit active comment by non owner returns forbidden`() {
        val comment = comment(CommentStatus.ACTIVE)
        val repository = FakeCommentRepository(comment)
        val transactionManager = RecordingTransactionManager()
        val useCase = EditCommentUseCase(repository, transactionTemplate(transactionManager))

        val result = useCase.edit(commentId, otherUserId, "New text")

        assertTrue(result is EditCommentResult.Forbidden)
        assertEquals(1, transactionManager.commitCount)
        assertEquals(0, transactionManager.rollbackCount)
        assertEquals(1, repository.findByIdCount)
        assertEquals(0, repository.updateCount)
        assertSame(comment, repository.currentComment)
    }

    @Test
    fun `edit deleted comment returns rejected`() {
        val comment = comment(CommentStatus.DELETED)
        val repository = FakeCommentRepository(comment)
        val transactionManager = RecordingTransactionManager()
        val useCase = EditCommentUseCase(repository, transactionTemplate(transactionManager))

        val result = useCase.edit(commentId, ownerId, "New text")

        assertTrue(result is EditCommentResult.Rejected)
        assertEquals(1, transactionManager.commitCount)
        assertEquals(0, transactionManager.rollbackCount)
        assertEquals(1, repository.findByIdCount)
        assertEquals(0, repository.updateCount)
        assertSame(comment, repository.currentComment)
    }

    @Test
    fun `edit missing comment returns not found`() {
        val repository = FakeCommentRepository(null)
        val transactionManager = RecordingTransactionManager()
        val useCase = EditCommentUseCase(repository, transactionTemplate(transactionManager))

        val result = useCase.edit(commentId, ownerId, "New text")

        assertTrue(result is EditCommentResult.NotFound)
        assertEquals(1, transactionManager.commitCount)
        assertEquals(0, transactionManager.rollbackCount)
        assertEquals(1, repository.findByIdCount)
        assertEquals(0, repository.updateCount)
    }

    private fun comment(status: CommentStatus, text: String = "Test comment"): Comment =
        Comment(
            id = commentId,
            userId = ownerId,
            contentId = contentId,
            parentCommentId = null,
            text = text,
            status = status,
            createdAt = 1_000L,
            updatedAt = 1_000L
        )

    private fun transactionTemplate(
        transactionManager: RecordingTransactionManager
    ) = org.springframework.transaction.support.TransactionTemplate(transactionManager)

    private class FakeCommentRepository(
        initialComment: Comment?
    ) : CommentRepository {

        var currentComment: Comment? = initialComment
            private set

        var findByIdCount = 0
            private set

        var updateCount = 0
            private set

        var updatedComment: Comment? = null
            private set

        override fun save(comment: Comment): Comment =
            error("save should not be called")

        override fun findById(id: PublicId): Comment? {
            findByIdCount++
            return currentComment?.takeIf { it.id == id }
        }

        override fun findByContentId(contentId: PublicId, limit: Int): List<Comment> =
            error("findByContentId should not be called")

        override fun findByParentCommentId(
            parentCommentId: PublicId,
            limit: Int
        ): List<Comment> =
            error("findByParentCommentId should not be called")

        override fun update(comment: Comment): Comment {
            updateCount++
            updatedComment = comment
            currentComment = comment
            return comment
        }
    }

    private class RecordingTransactionManager :
        AbstractPlatformTransactionManager() {

        var commitCount = 0
            private set

        var rollbackCount = 0
            private set

        override fun doGetTransaction(): Any = Any()

        override fun doBegin(transaction: Any, definition: org.springframework.transaction.TransactionDefinition) {
        }

        override fun doCommit(status: org.springframework.transaction.support.DefaultTransactionStatus) {
            commitCount++
        }

        override fun doRollback(status: org.springframework.transaction.support.DefaultTransactionStatus) {
            rollbackCount++
        }
    }
}
