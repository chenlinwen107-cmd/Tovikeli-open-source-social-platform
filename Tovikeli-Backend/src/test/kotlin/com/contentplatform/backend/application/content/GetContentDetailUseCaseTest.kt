package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.interaction.InteractionStatistics
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.repository.CommentRepository
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.InteractionRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetContentDetailUseCaseTest {

    private val contentId = PublicId("ct_test")

    private class FakeContentRepository(private val content: Content?) : ContentRepository {
        var lastId: PublicId? = null
            private set
        override fun findById(id: PublicId): Content? {
            lastId = id
            return content
        }
        override fun findByInstanceAndSourceContentId(instanceId: PublicId, sourceContentId: String): Content? = null
        override fun save(content: Content): Content = content
        override fun findPublicContent(
            instanceId: PublicId?, authorId: PublicId?, hasMedia: Boolean?,
            cursor: com.contentplatform.backend.repository.ContentCursor?, limit: Int,
            viewerAuthorId: PublicId?
        ): com.contentplatform.backend.repository.ContentBrowsePage =
            com.contentplatform.backend.repository.ContentBrowsePage(emptyList(), null)
        override fun searchPublicContent(
            query: String, instanceId: PublicId?, authorId: PublicId?, sourceId: PublicId?, limit: Int
        ): List<Content> = emptyList()
        override fun findPublicContentByTag(
            tagId: PublicId, cursor: com.contentplatform.backend.repository.ContentCursor?, limit: Int
        ): com.contentplatform.backend.repository.ContentBrowsePage =
            com.contentplatform.backend.repository.ContentBrowsePage(emptyList(), null)
        override fun findByIds(ids: List<PublicId>): List<Content> = emptyList()
    }

    private class FakeCommentRepository(private val count: Long) : CommentRepository {
        var lastContentId: PublicId? = null
            private set
        override fun save(comment: com.contentplatform.backend.domain.comment.Comment): com.contentplatform.backend.domain.comment.Comment =
            error("not used")
        override fun findById(id: PublicId): com.contentplatform.backend.domain.comment.Comment? = null
        override fun findByContentId(contentId: PublicId, limit: Int): List<com.contentplatform.backend.domain.comment.Comment> = emptyList()
        override fun findByParentCommentId(parentCommentId: PublicId, limit: Int): List<com.contentplatform.backend.domain.comment.Comment> = emptyList()
        override fun update(comment: com.contentplatform.backend.domain.comment.Comment): com.contentplatform.backend.domain.comment.Comment =
            error("not used")
        override fun countActiveByContentId(contentId: PublicId): Long {
            lastContentId = contentId
            return count
        }
    }

    private class FakeInteractionRepository(
        private val counts: Map<InteractionType, Long>
    ) : InteractionRepository {
        var lastContentId: PublicId? = null
            private set
        override fun save(interaction: com.contentplatform.backend.domain.interaction.Interaction): com.contentplatform.backend.domain.interaction.Interaction =
            error("not used")
        override fun findById(id: PublicId): com.contentplatform.backend.domain.interaction.Interaction? = null
        override fun findByUserContentType(userId: PublicId, contentId: PublicId, type: InteractionType): com.contentplatform.backend.domain.interaction.Interaction? = null
        override fun deleteByUserContentType(userId: PublicId, contentId: PublicId, type: InteractionType): Boolean = false
        override fun findByUserId(userId: PublicId, type: InteractionType?, limit: Int, cursor: com.contentplatform.backend.domain.interaction.InteractionCursor?): com.contentplatform.backend.domain.interaction.InteractionPage =
            com.contentplatform.backend.domain.interaction.InteractionPage(emptyList(), null)
        override fun countByContentId(contentId: PublicId): Map<InteractionType, Long> {
            lastContentId = contentId
            return counts
        }
    }

    @Test
    fun getById_contentFound_returnsDetailWithStatistics() {
        val c = content()
        val contentRepo = FakeContentRepository(c)
        val commentRepo = FakeCommentRepository(5L)
        val interactionRepo = FakeInteractionRepository(
            mapOf(InteractionType.LIKE to 3L, InteractionType.BOOKMARK to 1L)
        )
        val useCase = GetContentDetailUseCase(contentRepo, commentRepo, interactionRepo)

        val detail = useCase.getById(contentId)!!

        assertEquals(c, detail.content)
        assertEquals(5L, detail.commentCount)
        assertEquals(4L, detail.interactionStatistics.total)
        assertEquals(3L, detail.interactionStatistics.like)
        assertEquals(1L, detail.interactionStatistics.bookmark)
        assertEquals(0L, detail.interactionStatistics.favorite)
        assertEquals(0L, detail.interactionStatistics.repost)
    }

    @Test
    fun getById_contentNotFound_returnsNull() {
        val contentRepo = FakeContentRepository(null)
        val commentRepo = FakeCommentRepository(0L)
        val interactionRepo = FakeInteractionRepository(emptyMap())
        val useCase = GetContentDetailUseCase(contentRepo, commentRepo, interactionRepo)

        val detail = useCase.getById(PublicId("ct_missing"))

        assertNull(detail)
    }

    @Test
    fun getById_zeroComments_returnsZeroCommentCount() {
        val contentRepo = FakeContentRepository(content())
        val commentRepo = FakeCommentRepository(0L)
        val interactionRepo = FakeInteractionRepository(emptyMap())
        val useCase = GetContentDetailUseCase(contentRepo, commentRepo, interactionRepo)

        val detail = useCase.getById(contentId)!!

        assertEquals(0L, detail.commentCount)
    }

    @Test
    fun getById_zeroInteractions_returnsAllZeroStatistics() {
        val contentRepo = FakeContentRepository(content())
        val commentRepo = FakeCommentRepository(0L)
        val interactionRepo = FakeInteractionRepository(emptyMap())
        val useCase = GetContentDetailUseCase(contentRepo, commentRepo, interactionRepo)

        val detail = useCase.getById(contentId)!!

        assertEquals(InteractionStatistics.ZERO, detail.interactionStatistics)
    }

    @Test
    fun getById_allFourInteractionTypes_returnsCorrectTotals() {
        val contentRepo = FakeContentRepository(content())
        val commentRepo = FakeCommentRepository(0L)
        val interactionRepo = FakeInteractionRepository(
            mapOf(
                InteractionType.LIKE to 10L,
                InteractionType.FAVORITE to 5L,
                InteractionType.REPOST to 3L,
                InteractionType.BOOKMARK to 2L
            )
        )
        val useCase = GetContentDetailUseCase(contentRepo, commentRepo, interactionRepo)

        val detail = useCase.getById(contentId)!!

        assertEquals(20L, detail.interactionStatistics.total)
        assertEquals(10L, detail.interactionStatistics.like)
        assertEquals(5L, detail.interactionStatistics.favorite)
        assertEquals(3L, detail.interactionStatistics.repost)
        assertEquals(2L, detail.interactionStatistics.bookmark)
    }

    @Test
    fun getById_repositoriesCalledWithCorrectId() {
        val contentRepo = FakeContentRepository(content())
        val commentRepo = FakeCommentRepository(0L)
        val interactionRepo = FakeInteractionRepository(emptyMap())
        val useCase = GetContentDetailUseCase(contentRepo, commentRepo, interactionRepo)

        useCase.getById(contentId)

        assertEquals(contentId, contentRepo.lastId)
        assertEquals(contentId, commentRepo.lastContentId)
        assertEquals(contentId, interactionRepo.lastContentId)
    }

    // ------------------------------------------------------------------
    // Stage 28: Non-ACTIVE content must return null
    // ------------------------------------------------------------------

    @Test
    fun getById_deletedContent_returnsNull() {
        val contentRepo = FakeContentRepository(content(status = ContentStatus.DELETED))
        val commentRepo = FakeCommentRepository(5L)
        val interactionRepo = FakeInteractionRepository(mapOf(InteractionType.LIKE to 3L))
        val useCase = GetContentDetailUseCase(contentRepo, commentRepo, interactionRepo)

        val detail = useCase.getById(contentId)

        assertNull(detail)
        assertEquals(contentId, contentRepo.lastId)
    }

    @Test
    fun getById_hiddenContent_returnsNull() {
        val contentRepo = FakeContentRepository(content(status = ContentStatus.HIDDEN))
        val commentRepo = FakeCommentRepository(5L)
        val interactionRepo = FakeInteractionRepository(mapOf(InteractionType.LIKE to 3L))
        val useCase = GetContentDetailUseCase(contentRepo, commentRepo, interactionRepo)

        val detail = useCase.getById(contentId)

        assertNull(detail)
    }

    @Test
    fun getById_unavailableContent_returnsNull() {
        val contentRepo = FakeContentRepository(content(status = ContentStatus.UNAVAILABLE))
        val commentRepo = FakeCommentRepository(5L)
        val interactionRepo = FakeInteractionRepository(mapOf(InteractionType.LIKE to 3L))
        val useCase = GetContentDetailUseCase(contentRepo, commentRepo, interactionRepo)

        val detail = useCase.getById(contentId)

        assertNull(detail)
    }

    @Test
    fun getById_unknownContent_returnsNull() {
        val contentRepo = FakeContentRepository(content(status = ContentStatus.UNKNOWN))
        val commentRepo = FakeCommentRepository(5L)
        val interactionRepo = FakeInteractionRepository(mapOf(InteractionType.LIKE to 3L))
        val useCase = GetContentDetailUseCase(contentRepo, commentRepo, interactionRepo)

        val detail = useCase.getById(contentId)

        assertNull(detail)
    }

    @Test
    fun getById_deletedContent_doesNotQueryCommentOrInteractionRepositories() {
        val contentRepo = FakeContentRepository(content(status = ContentStatus.DELETED))
        val commentRepo = FakeCommentRepository(5L)
        val interactionRepo = FakeInteractionRepository(mapOf(InteractionType.LIKE to 3L))
        val useCase = GetContentDetailUseCase(contentRepo, commentRepo, interactionRepo)

        useCase.getById(contentId)

        assertNull(commentRepo.lastContentId, "commentRepository must not be called for non-ACTIVE content")
        assertNull(interactionRepo.lastContentId, "interactionRepository must not be called for non-ACTIVE content")
    }

    private fun content(status: ContentStatus = ContentStatus.ACTIVE): Content = Content.createFromAdapter(
        instanceIdValue = "inst_1",
        sourceContentId = "sc_1",
        authorId = PublicId("au_1"),
        sourceId = PublicId("src_1"),
        text = "test",
        language = "en",
        visibility = ContentVisibility.PUBLIC,
        mediaIds = emptyList(),
        parentId = null,
        repostOfId = null,
        quoteOfId = null,
        externalCreatedAt = null,
        metadata = emptyMap()
    ).let { it.copy(status = status) }
}
