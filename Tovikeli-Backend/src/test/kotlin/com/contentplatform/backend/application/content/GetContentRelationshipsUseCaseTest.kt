package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetContentRelationshipsUseCaseTest {

    private class FakeContentRepository : ContentRepository {
        var findByIdResult: Content? = null
        var findPublicByIdsResult: List<Content> = emptyList()
        var lastFindById: PublicId? = null
            private set
        var lastFindPublicByIds: List<PublicId>? = null
            private set

        override fun findById(id: PublicId): Content? {
            lastFindById = id
            return findByIdResult
        }
        override fun findByInstanceAndSourceContentId(instanceId: PublicId, sourceContentId: String): Content? = null
        override fun save(content: Content): Content = content
        override fun findPublicContent(
            instanceId: PublicId?, authorId: PublicId?, hasMedia: Boolean?,
            cursor: ContentCursor?, limit: Int, viewerAuthorId: PublicId?
        ): ContentBrowsePage = ContentBrowsePage(emptyList(), null)
        override fun searchPublicContent(
            query: String, instanceId: PublicId?, authorId: PublicId?, sourceId: PublicId?, limit: Int
        ): List<Content> = emptyList()
        override fun findPublicContentByTag(
            tagId: PublicId, cursor: ContentCursor?, limit: Int
        ): ContentBrowsePage = ContentBrowsePage(emptyList(), null)
        override fun findByIds(ids: List<PublicId>): List<Content> = emptyList()
        override fun findPublicByIds(ids: List<PublicId>): List<Content> {
            lastFindPublicByIds = ids
            return findPublicByIdsResult
        }
    }

    private fun content(
        id: String,
        status: ContentStatus = ContentStatus.ACTIVE,
        visibility: ContentVisibility = ContentVisibility.PUBLIC,
        parentId: String? = null,
        repostOfId: String? = null,
        quoteOfId: String? = null
    ): Content = Content.createFromAdapter(
        instanceIdValue = "inst_1",
        sourceContentId = "sc_$id",
        authorId = PublicId("au_1"),
        sourceId = PublicId("src_1"),
        text = "content $id",
        language = "en",
        visibility = visibility,
        mediaIds = emptyList(),
        parentId = parentId?.let { PublicId(it) },
        repostOfId = repostOfId?.let { PublicId(it) },
        quoteOfId = quoteOfId?.let { PublicId(it) },
        externalCreatedAt = null,
        metadata = emptyMap()
    ).let { it.copy(id = PublicId(id), status = status) }

    // ------------------------------------------------------------------
    // Current Content missing → null
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_contentNotFound_returnsNull() {
        val repo = FakeContentRepository()
        repo.findByIdResult = null
        val useCase = GetContentRelationshipsUseCase(repo)

        val result = useCase.getRelationships(PublicId("ct_missing"))

        assertNull(result)
        assertEquals(PublicId("ct_missing"), repo.lastFindById)
    }

    // ------------------------------------------------------------------
    // Current Content non-ACTIVE → null
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_deletedContent_returnsNull() {
        val repo = FakeContentRepository()
        repo.findByIdResult = content("ct_1", status = ContentStatus.DELETED)
        val useCase = GetContentRelationshipsUseCase(repo)

        val result = useCase.getRelationships(PublicId("ct_1"))

        assertNull(result)
    }

    @Test
    fun getRelationships_hiddenContent_returnsNull() {
        val repo = FakeContentRepository()
        repo.findByIdResult = content("ct_1", status = ContentStatus.HIDDEN)
        val useCase = GetContentRelationshipsUseCase(repo)

        val result = useCase.getRelationships(PublicId("ct_1"))

        assertNull(result)
    }

    @Test
    fun getRelationships_nonActiveContent_doesNotQueryFindPublicByIds() {
        val repo = FakeContentRepository()
        repo.findByIdResult = content("ct_1", status = ContentStatus.DELETED)
        val useCase = GetContentRelationshipsUseCase(repo)

        useCase.getRelationships(PublicId("ct_1"))

        assertNull(repo.lastFindPublicByIds, "findPublicByIds must not be called for non-ACTIVE content")
    }

    // ------------------------------------------------------------------
    // No relationships → all null
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_noRelationships_returnsAllNull() {
        val repo = FakeContentRepository()
        repo.findByIdResult = content("ct_1")
        val useCase = GetContentRelationshipsUseCase(repo)

        val result = useCase.getRelationships(PublicId("ct_1"))!!

        assertNull(result.parent)
        assertNull(result.repostOf)
        assertNull(result.quoteOf)
        assertNull(repo.lastFindPublicByIds, "findPublicByIds must not be called when no relationship IDs exist")
    }

    // ------------------------------------------------------------------
    // Parent only
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_parentOnly_returnsParent() {
        val repo = FakeContentRepository()
        repo.findByIdResult = content("ct_1", parentId = "ct_parent")
        val parent = content("ct_parent")
        repo.findPublicByIdsResult = listOf(parent)
        val useCase = GetContentRelationshipsUseCase(repo)

        val result = useCase.getRelationships(PublicId("ct_1"))!!

        assertEquals(parent, result.parent)
        assertNull(result.repostOf)
        assertNull(result.quoteOf)
        assertEquals(listOf(PublicId("ct_parent")), repo.lastFindPublicByIds)
    }

    // ------------------------------------------------------------------
    // RepostOf only
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_repostOfOnly_returnsRepostOf() {
        val repo = FakeContentRepository()
        repo.findByIdResult = content("ct_1", repostOfId = "ct_original")
        val original = content("ct_original")
        repo.findPublicByIdsResult = listOf(original)
        val useCase = GetContentRelationshipsUseCase(repo)

        val result = useCase.getRelationships(PublicId("ct_1"))!!

        assertNull(result.parent)
        assertEquals(original, result.repostOf)
        assertNull(result.quoteOf)
    }

    // ------------------------------------------------------------------
    // QuoteOf only
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_quoteOfOnly_returnsQuoteOf() {
        val repo = FakeContentRepository()
        repo.findByIdResult = content("ct_1", quoteOfId = "ct_quoted")
        val quoted = content("ct_quoted")
        repo.findPublicByIdsResult = listOf(quoted)
        val useCase = GetContentRelationshipsUseCase(repo)

        val result = useCase.getRelationships(PublicId("ct_1"))!!

        assertNull(result.parent)
        assertNull(result.repostOf)
        assertEquals(quoted, result.quoteOf)
    }

    // ------------------------------------------------------------------
    // All three relationships
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_allThree_returnsAll() {
        val repo = FakeContentRepository()
        repo.findByIdResult = content(
            "ct_1",
            parentId = "ct_parent",
            repostOfId = "ct_original",
            quoteOfId = "ct_quoted"
        )
        val parent = content("ct_parent")
        val original = content("ct_original")
        val quoted = content("ct_quoted")
        repo.findPublicByIdsResult = listOf(parent, original, quoted)
        val useCase = GetContentRelationshipsUseCase(repo)

        val result = useCase.getRelationships(PublicId("ct_1"))!!

        assertEquals(parent, result.parent)
        assertEquals(original, result.repostOf)
        assertEquals(quoted, result.quoteOf)
    }

    // ------------------------------------------------------------------
    // Missing target → null
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_targetNotInPublicResults_returnsNull() {
        val repo = FakeContentRepository()
        repo.findByIdResult = content("ct_1", parentId = "ct_parent")
        repo.findPublicByIdsResult = emptyList()
        val useCase = GetContentRelationshipsUseCase(repo)

        val result = useCase.getRelationships(PublicId("ct_1"))!!

        assertNull(result.parent, "missing target must result in null")
        assertNull(result.repostOf)
        assertNull(result.quoteOf)
    }

    // ------------------------------------------------------------------
    // Inaccessible target → null
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_inaccessibleTarget_notReturned() {
        val repo = FakeContentRepository()
        repo.findByIdResult = content("ct_1", parentId = "ct_parent", repostOfId = "ct_original")
        val parent = content("ct_parent")
        repo.findPublicByIdsResult = listOf(parent)
        val useCase = GetContentRelationshipsUseCase(repo)

        val result = useCase.getRelationships(PublicId("ct_1"))!!

        assertEquals(parent, result.parent)
        assertNull(result.repostOf, "inaccessible repostOf must be null")
        assertNull(result.quoteOf)
    }

    // ------------------------------------------------------------------
    // Duplicate target IDs → one lookup
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_duplicateTargetIds_deduplicatesBeforeLookup() {
        val repo = FakeContentRepository()
        repo.findByIdResult = content(
            "ct_1",
            parentId = "ct_shared",
            repostOfId = "ct_shared"
        )
        val shared = content("ct_shared")
        repo.findPublicByIdsResult = listOf(shared)
        val useCase = GetContentRelationshipsUseCase(repo)

        val result = useCase.getRelationships(PublicId("ct_1"))!!

        assertEquals(shared, result.parent)
        assertEquals(shared, result.repostOf)
        assertEquals(listOf(PublicId("ct_shared")), repo.lastFindPublicByIds, "duplicate IDs must be deduplicated")
    }

    // ------------------------------------------------------------------
    // Single batch lookup
    // ------------------------------------------------------------------

    @Test
    fun getRelationships_usesSingleBatchLookup() {
        val repo = FakeContentRepository()
        repo.findByIdResult = content(
            "ct_1",
            parentId = "ct_parent",
            repostOfId = "ct_original",
            quoteOfId = "ct_quoted"
        )
        repo.findPublicByIdsResult = listOf(
            content("ct_parent"),
            content("ct_original"),
            content("ct_quoted")
        )
        val useCase = GetContentRelationshipsUseCase(repo)

        useCase.getRelationships(PublicId("ct_1"))

        assertEquals(3, repo.lastFindPublicByIds!!.size, "must pass all three distinct target IDs")
    }
}
