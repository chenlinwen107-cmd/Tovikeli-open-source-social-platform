package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.repository.ContentRepository
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SearchContentUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fake ContentRepository that records search requests and
    // explicitly overrides searchPublicContent() with a configurable result.
    // ------------------------------------------------------------------

    private class FakeContentRepository : ContentRepository {
        val rows = mutableMapOf<String, Content>()
        var lastSearchQuery: String? = null
            private set
        var lastSearchInstanceId: PublicId? = null
            private set
        var lastSearchAuthorId: PublicId? = null
            private set
        var lastSearchSourceId: PublicId? = null
            private set
        var lastSearchLimit: Int? = null
            private set
        var searchResult: List<Content> = emptyList()

        override fun findById(id: PublicId): Content? = rows[id.value]

        override fun findByInstanceAndSourceContentId(
            instanceId: PublicId,
            sourceContentId: String
        ): Content? =
            rows.values.firstOrNull { it.instanceId == instanceId && it.sourceContentId == sourceContentId }

        override fun save(content: Content): Content {
            rows[content.id.value] = content
            return content
        }

        override fun searchPublicContent(
            query: String,
            instanceId: PublicId?,
            authorId: PublicId?,
            sourceId: PublicId?,
            limit: Int
        ): List<Content> {
            lastSearchQuery = query
            lastSearchInstanceId = instanceId
            lastSearchAuthorId = authorId
            lastSearchSourceId = sourceId
            lastSearchLimit = limit
            return searchResult
        }
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private fun content(id: String): Content = Content.createFromAdapter(
        instanceIdValue = "inst_misskey_io",
        sourceContentId = "sc-$id",
        authorId = PublicId("au_author_1"),
        sourceId = PublicId("src_misskey"),
        text = "search fixture $id",
        language = "en",
        visibility = ContentVisibility.PUBLIC,
        mediaIds = emptyList(),
        parentId = null,
        repostOfId = null,
        quoteOfId = null,
        externalCreatedAt = null,
        metadata = emptyMap()
    )

    // ------------------------------------------------------------------
    // 1. Query and limit are passed through unchanged (no filters)
    // ------------------------------------------------------------------

    @Test
    fun search_withoutFilters_passesQueryAndLimitThrough() {
        val repo = FakeContentRepository()
        repo.searchResult = listOf(content("ct_1"))
        val uc = SearchContentUseCase(repo)

        uc.search("hello world", null, null, null, 42)

        assertEquals("hello world", repo.lastSearchQuery, "the query must reach the repository unchanged")
        assertEquals(null, repo.lastSearchInstanceId)
        assertEquals(null, repo.lastSearchAuthorId)
        assertEquals(null, repo.lastSearchSourceId)
        assertEquals(42, repo.lastSearchLimit, "the requested limit must reach the repository unchanged")
    }

    // ------------------------------------------------------------------
    // 2. Every optional filter is passed through unchanged
    // ------------------------------------------------------------------

    @Test
    fun search_withInstanceFilter_passesInstanceIdThroughUnchanged() {
        val repo = FakeContentRepository()
        repo.searchResult = listOf(content("ct_1"))
        val uc = SearchContentUseCase(repo)
        val instanceId = PublicId("inst_misskey_io")

        uc.search("hello", instanceId, null, null, 7)

        assertEquals(instanceId, repo.lastSearchInstanceId)
        assertEquals(null, repo.lastSearchAuthorId)
        assertEquals(null, repo.lastSearchSourceId)
        assertEquals(7, repo.lastSearchLimit)
    }

    @Test
    fun search_withAuthorFilter_passesAuthorIdThroughUnchanged() {
        val repo = FakeContentRepository()
        repo.searchResult = listOf(content("ct_1"))
        val uc = SearchContentUseCase(repo)
        val authorId = PublicId("au_author_1")

        uc.search("hello", null, authorId, null, 7)

        assertEquals(null, repo.lastSearchInstanceId)
        assertEquals(authorId, repo.lastSearchAuthorId)
        assertEquals(null, repo.lastSearchSourceId)
    }

    @Test
    fun search_withSourceFilter_passesSourceIdThroughUnchanged() {
        val repo = FakeContentRepository()
        repo.searchResult = listOf(content("ct_1"))
        val uc = SearchContentUseCase(repo)
        val sourceId = PublicId("src_misskey")

        uc.search("hello", null, null, sourceId, 7)

        assertEquals(null, repo.lastSearchInstanceId)
        assertEquals(null, repo.lastSearchAuthorId)
        assertEquals(sourceId, repo.lastSearchSourceId)
    }

    @Test
    fun search_withAllFilters_passesAllThroughUnchanged() {
        val repo = FakeContentRepository()
        repo.searchResult = listOf(content("ct_1"))
        val uc = SearchContentUseCase(repo)
        val instanceId = PublicId("inst_misskey_io")
        val authorId = PublicId("au_author_1")
        val sourceId = PublicId("src_misskey")

        uc.search("hello", instanceId, authorId, sourceId, 5)

        assertEquals(instanceId, repo.lastSearchInstanceId)
        assertEquals(authorId, repo.lastSearchAuthorId)
        assertEquals(sourceId, repo.lastSearchSourceId)
        assertEquals(5, repo.lastSearchLimit)
    }

    // ------------------------------------------------------------------
    // 3. Repository list is returned unchanged
    // ------------------------------------------------------------------

    @Test
    fun search_whenRepositoryReturnsContent_returnsSameList() {
        val repo = FakeContentRepository()
        val stored = listOf(repo.save(content("ct_1")), repo.save(content("ct_2")))
        repo.searchResult = stored
        val uc = SearchContentUseCase(repo)

        val result = uc.search("hello", null, null, null, 10)

        assertSame(stored, result, "the repository list must be returned unchanged, no copies or reordering")
    }

    // ------------------------------------------------------------------
    // 4. Empty result stays empty (never null)
    // ------------------------------------------------------------------

    @Test
    fun search_whenNothingMatches_returnsEmptyList() {
        val repo = FakeContentRepository()
        val uc = SearchContentUseCase(repo)

        val result = uc.search("nothing-matches-this", null, null, null, 10)

        assertTrue(result.isEmpty(), "an empty repository result must remain an empty list")
        assertEquals(10, repo.lastSearchLimit)
        assertEquals("nothing-matches-this", repo.lastSearchQuery)
    }
}
