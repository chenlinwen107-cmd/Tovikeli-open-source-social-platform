package com.contentplatform.backend.application.comment

import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentCursor
import com.contentplatform.backend.domain.comment.CommentPage
import com.contentplatform.backend.domain.comment.CommentStatus
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CommentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GetCommentRepliesUseCaseTest {

    private val parentCommentId = PublicId("cmt_parent")
    private val userId = PublicId("usr_1234567890abcdef1234567890abcdef")
    private val contentId = PublicId("ct_1234567890abcdef1234567890abcdef")

    // ------------------------------------------------------------------
    // ACTIVE parent + ACTIVE replies -> returns replies
    // ------------------------------------------------------------------

    @Test
    fun `getReplies returns replies when parent is ACTIVE`() {
        val parent = parent(CommentStatus.ACTIVE)
        val reply1 = reply("cmt_reply1", 1_000L)
        val reply2 = reply("cmt_reply2", 2_000L)
        val repository = FakeCommentRepository(parent, listOf(reply1, reply2))
        val useCase = GetCommentRepliesUseCase(repository)

        val result = useCase.getReplies(parentCommentId, 20)

        assertEquals(2, result.size)
        assertSame(reply1, result[0])
        assertSame(reply2, result[1])
        assertEquals(parentCommentId, repository.lastFindById)
        assertEquals(parentCommentId, repository.lastFindByParentCommentId)
        assertEquals(20, repository.lastLimit)
        assertEquals(1, repository.findByParentCommentIdCount)
    }

    // ------------------------------------------------------------------
    // ACTIVE parent + no replies -> []
    // ------------------------------------------------------------------

    @Test
    fun `getReplies returns empty list when parent is ACTIVE but no replies`() {
        val parent = parent(CommentStatus.ACTIVE)
        val repository = FakeCommentRepository(parent, emptyList())
        val useCase = GetCommentRepliesUseCase(repository)

        val result = useCase.getReplies(parentCommentId, 20)

        assertTrue(result.isEmpty())
        assertEquals(parentCommentId, repository.lastFindById)
        assertEquals(parentCommentId, repository.lastFindByParentCommentId)
        assertEquals(1, repository.findByParentCommentIdCount)
    }

    // ------------------------------------------------------------------
    // parent not found -> []
    // ------------------------------------------------------------------

    @Test
    fun `getReplies returns empty list when parent does not exist`() {
        val repository = FakeCommentRepository(parentComment = null, replies = listOf(reply("cmt_r1", 1_000L)))
        val useCase = GetCommentRepliesUseCase(repository)

        val result = useCase.getReplies(parentCommentId, 20)

        assertTrue(result.isEmpty())
        assertEquals(parentCommentId, repository.lastFindById)
        assertEquals(0, repository.findByParentCommentIdCount)
    }

    // ------------------------------------------------------------------
    // DELETED parent + ACTIVE replies -> [] (core contract test)
    // ------------------------------------------------------------------

    @Test
    fun `getReplies returns empty list when parent is DELETED even with ACTIVE replies`() {
        val deletedParent = parent(CommentStatus.DELETED)
        val activeReply = reply("cmt_reply1", 1_000L)
        val repository = FakeCommentRepository(deletedParent, listOf(activeReply))
        val useCase = GetCommentRepliesUseCase(repository)

        val result = useCase.getReplies(parentCommentId, 20)

        assertTrue(result.isEmpty())
        assertEquals(parentCommentId, repository.lastFindById)
        assertEquals(0, repository.findByParentCommentIdCount)
    }

    // ------------------------------------------------------------------
    // ACTIVE parent calls findByParentCommentId
    // ------------------------------------------------------------------

    @Test
    fun `getReplies delegates to findByParentCommentId when parent is ACTIVE`() {
        val parent = parent(CommentStatus.ACTIVE)
        val repository = FakeCommentRepository(parent, emptyList())
        val useCase = GetCommentRepliesUseCase(repository)

        useCase.getReplies(parentCommentId, 5)

        assertEquals(5, repository.lastLimit)
        assertEquals(1, repository.findByParentCommentIdCount)
    }

    // ------------------------------------------------------------------
    // DELETED parent does NOT call findByParentCommentId
    // ------------------------------------------------------------------

    @Test
    fun `getReplies does not call findByParentCommentId when parent is DELETED`() {
        val deletedParent = parent(CommentStatus.DELETED)
        val repository = FakeCommentRepository(deletedParent, emptyList())
        val useCase = GetCommentRepliesUseCase(repository)

        useCase.getReplies(parentCommentId, 20)

        assertEquals(0, repository.findByParentCommentIdCount)
    }

    // ------------------------------------------------------------------
    // missing parent does NOT call findByParentCommentId
    // ------------------------------------------------------------------

    @Test
    fun `getReplies does not call findByParentCommentId when parent is missing`() {
        val repository = FakeCommentRepository(parentComment = null, replies = emptyList())
        val useCase = GetCommentRepliesUseCase(repository)

        useCase.getReplies(parentCommentId, 20)

        assertEquals(0, repository.findByParentCommentIdCount)
    }

    // ==================================================================
    // Paginated getReplies(parentCommentId, limit, cursor) -> CommentPage
    // ==================================================================

    // ------------------------------------------------------------------
    // ACTIVE parent + cursor -> page returned
    // ------------------------------------------------------------------

    @Test
    fun `getReplies paginated returns page when parent is ACTIVE`() {
        val parent = parent(CommentStatus.ACTIVE)
        val reply1 = reply("cmt_reply1", 1_000L)
        val reply2 = reply("cmt_reply2", 2_000L)
        val nextCursor = CommentCursor(createdAt = 2_000L, commentId = PublicId("cmt_reply2"))
        val page = CommentPage(items = listOf(reply1, reply2), nextCursor = nextCursor)
        val repository = FakeCommentRepository(parent, page = page)
        val useCase = GetCommentRepliesUseCase(repository)

        val result = useCase.getReplies(parentCommentId, 20, null)

        assertEquals(2, result.items.size)
        assertSame(reply1, result.items[0])
        assertSame(reply2, result.items[1])
        assertEquals(nextCursor, result.nextCursor)
        assertEquals(1, repository.findByParentCommentIdPaginatedCount)
        assertEquals(parentCommentId, repository.lastFindByParentCommentIdPaginated)
        assertEquals(20, repository.lastLimitPaginated)
        assertNull(repository.lastCursorPaginated)
    }

    // ------------------------------------------------------------------
    // cursor is forwarded to repository
    // ------------------------------------------------------------------

    @Test
    fun `getReplies paginated forwards cursor to repository`() {
        val parent = parent(CommentStatus.ACTIVE)
        val cursor = CommentCursor(createdAt = 1_000L, commentId = PublicId("cmt_reply1"))
        val page = CommentPage(items = emptyList(), nextCursor = null)
        val repository = FakeCommentRepository(parent, page = page)
        val useCase = GetCommentRepliesUseCase(repository)

        useCase.getReplies(parentCommentId, 10, cursor)

        assertEquals(cursor, repository.lastCursorPaginated)
        assertEquals(10, repository.lastLimitPaginated)
    }

    // ------------------------------------------------------------------
    // missing parent -> empty page, no paginated query
    // ------------------------------------------------------------------

    @Test
    fun `getReplies paginated returns empty page when parent does not exist`() {
        val repository = FakeCommentRepository(parentComment = null, page = CommentPage(emptyList(), null))
        val useCase = GetCommentRepliesUseCase(repository)

        val result = useCase.getReplies(parentCommentId, 20, null)

        assertTrue(result.items.isEmpty())
        assertNull(result.nextCursor)
        assertEquals(0, repository.findByParentCommentIdPaginatedCount)
    }

    // ------------------------------------------------------------------
    // DELETED parent -> empty page, no paginated query
    // ------------------------------------------------------------------

    @Test
    fun `getReplies paginated returns empty page when parent is DELETED`() {
        val deletedParent = parent(CommentStatus.DELETED)
        val activeReply = reply("cmt_reply1", 1_000L)
        val page = CommentPage(items = listOf(activeReply), nextCursor = null)
        val repository = FakeCommentRepository(deletedParent, page = page)
        val useCase = GetCommentRepliesUseCase(repository)

        val result = useCase.getReplies(parentCommentId, 20, null)

        assertTrue(result.items.isEmpty())
        assertNull(result.nextCursor)
        assertEquals(0, repository.findByParentCommentIdPaginatedCount)
    }

    // ------------------------------------------------------------------
    // repository returns empty -> empty page
    // ------------------------------------------------------------------

    @Test
    fun `getReplies paginated returns empty page when repository returns empty`() {
        val parent = parent(CommentStatus.ACTIVE)
        val page = CommentPage(items = emptyList(), nextCursor = null)
        val repository = FakeCommentRepository(parent, page = page)
        val useCase = GetCommentRepliesUseCase(repository)

        val result = useCase.getReplies(parentCommentId, 20, null)

        assertTrue(result.items.isEmpty())
        assertNull(result.nextCursor)
    }

    // ------------------------------------------------------------------
    // nextCursor is correctly returned from page
    // ------------------------------------------------------------------

    @Test
    fun `getReplies paginated returns nextCursor from page`() {
        val parent = parent(CommentStatus.ACTIVE)
        val reply1 = reply("cmt_reply1", 1_000L)
        val nextCursor = CommentCursor(createdAt = 1_000L, commentId = PublicId("cmt_reply1"))
        val page = CommentPage(items = listOf(reply1), nextCursor = nextCursor)
        val repository = FakeCommentRepository(parent, page = page)
        val useCase = GetCommentRepliesUseCase(repository)

        val result = useCase.getReplies(parentCommentId, 5, null)

        assertEquals(nextCursor, result.nextCursor)
        assertEquals(1, result.items.size)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun parent(status: CommentStatus): Comment =
        Comment(
            id = parentCommentId,
            userId = userId,
            contentId = contentId,
            parentCommentId = null,
            text = "Parent comment",
            status = status,
            createdAt = 1_000L,
            updatedAt = 1_000L
        )

    private fun reply(idValue: String, createdAt: Long): Comment =
        Comment(
            id = PublicId(idValue),
            userId = userId,
            contentId = contentId,
            parentCommentId = parentCommentId,
            text = "Reply",
            status = CommentStatus.ACTIVE,
            createdAt = createdAt,
            updatedAt = createdAt
        )

    private class FakeCommentRepository(
        private val parentComment: Comment?,
        private val replies: List<Comment> = emptyList(),
        private val page: CommentPage = CommentPage(emptyList(), null)
    ) : CommentRepository {

        var findByIdCount = 0
            private set

        var lastFindById: PublicId? = null
            private set

        var findByParentCommentIdCount = 0
            private set

        var lastFindByParentCommentId: PublicId? = null
            private set

        var lastLimit: Int = 0
            private set

        var findByParentCommentIdPaginatedCount = 0
            private set

        var lastFindByParentCommentIdPaginated: PublicId? = null
            private set

        var lastLimitPaginated: Int = 0
            private set

        var lastCursorPaginated: CommentCursor? = null
            private set

        override fun save(comment: Comment): Comment =
            error("save should not be called")

        override fun findById(id: PublicId): Comment? {
            findByIdCount++
            lastFindById = id
            return parentComment
        }

        override fun findByContentId(contentId: PublicId, limit: Int): List<Comment> =
            error("findByContentId should not be called")

        override fun findByParentCommentId(
            parentCommentId: PublicId,
            limit: Int
        ): List<Comment> {
            findByParentCommentIdCount++
            lastFindByParentCommentId = parentCommentId
            lastLimit = limit
            return replies
        }

        override fun findByParentCommentId(
            parentCommentId: PublicId,
            limit: Int,
            cursor: CommentCursor?
        ): CommentPage {
            findByParentCommentIdPaginatedCount++
            lastFindByParentCommentIdPaginated = parentCommentId
            lastLimitPaginated = limit
            lastCursorPaginated = cursor
            return page
        }

        override fun update(comment: Comment): Comment =
            error("update should not be called")
    }
}
