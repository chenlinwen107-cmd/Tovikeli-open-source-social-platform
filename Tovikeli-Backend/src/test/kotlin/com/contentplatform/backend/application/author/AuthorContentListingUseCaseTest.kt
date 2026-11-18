package com.contentplatform.backend.application.author

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AuthorContentListingUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fake ContentRepository that records browse requests and
    // returns a configurable page.
    // ------------------------------------------------------------------

    private class FakeContentRepository : ContentRepository {
        val rows = mutableMapOf<String, Content>()
        var lastBrowseInstanceId: PublicId? = null
            private set
        var lastBrowseAuthorId: PublicId? = null
            private set
        var lastBrowseHasMedia: Boolean? = null
            private set
        var lastBrowseCursor: ContentCursor? = null
            private set
        var lastBrowseLimit: Int? = null
            private set
        var browseResult: ContentBrowsePage = ContentBrowsePage(emptyList(), null)

        override fun findById(id: PublicId): Content? = rows[id.value]

        override fun findByInstanceAndSourceContentId(
            instanceId: PublicId,
            sourceContentId: String
        ): Content? =
            rows.values.firstOrNull {
                it.instanceId == instanceId && it.sourceContentId == sourceContentId
            }

        override fun save(content: Content): Content {
            rows[content.id.value] = content
            return content
        }

        override fun findPublicContent(
            instanceId: PublicId?,
            authorId: PublicId?,
            hasMedia: Boolean?,
            cursor: ContentCursor?,
            limit: Int
        ): ContentBrowsePage {
            lastBrowseInstanceId = instanceId
            lastBrowseAuthorId = authorId
            lastBrowseHasMedia = hasMedia
            lastBrowseCursor = cursor
            lastBrowseLimit = limit
            return browseResult
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
        text = "listing fixture $id",
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
    // Test 1 — forwards authorId, null instanceId, null hasMedia
    // ------------------------------------------------------------------

    @Test
    fun list_forwardsAuthorIdAndNullFilters() {
        val repo = FakeContentRepository()
        repo.browseResult = ContentBrowsePage(listOf(content("ct_1")), null)
        val uc = AuthorContentListingUseCase(repo)
        val authorId = PublicId("au_author_1")

        val result = uc.list(authorId, null, 20)

        assertEquals(authorId, repo.lastBrowseAuthorId, "authorId must reach the repository unchanged")
        assertNull(repo.lastBrowseInstanceId, "instanceId must be null")
        assertNull(repo.lastBrowseHasMedia, "hasMedia must be null")
        assertNull(repo.lastBrowseCursor, "cursor must be null when not supplied")
        assertEquals(20, repo.lastBrowseLimit, "limit must reach the repository unchanged")
        assertSame(result, repo.browseResult, "the returned page must be the repository result")
    }

    // ------------------------------------------------------------------
    // Test 2 — forwards cursor unchanged
    // ------------------------------------------------------------------

    @Test
    fun list_forwardsCursorUnchanged() {
        val repo = FakeContentRepository()
        repo.browseResult = ContentBrowsePage(listOf(content("ct_1")), null)
        val uc = AuthorContentListingUseCase(repo)
        val authorId = PublicId("au_author_1")
        val cursor = ContentCursor(createdAt = 1_757_000_000_456L, id = PublicId("ct_cursor"))

        val result = uc.list(authorId, cursor, 10)

        assertEquals(cursor, repo.lastBrowseCursor, "the cursor instance must reach the repository unchanged")
        assertSame(result, repo.browseResult, "the returned page must be the repository result")
    }

    // ------------------------------------------------------------------
    // Test 3 — forwards limit unchanged (multiple values)
    // ------------------------------------------------------------------

    @Test
    fun list_forwardsLimitZeroUnchanged() {
        val repo = FakeContentRepository()
        val uc = AuthorContentListingUseCase(repo)

        uc.list(PublicId("au_1"), null, 0)

        assertEquals(0, repo.lastBrowseLimit, "limit=0 must be passed unchanged")
    }

    @Test
    fun list_forwardsLimitOneUnchanged() {
        val repo = FakeContentRepository()
        repo.browseResult = ContentBrowsePage(listOf(content("ct_1")), null)
        val uc = AuthorContentListingUseCase(repo)

        uc.list(PublicId("au_1"), null, 1)

        assertEquals(1, repo.lastBrowseLimit, "limit=1 must be passed unchanged")
    }

    @Test
    fun list_forwardsLimitTwentyUnchanged() {
        val repo = FakeContentRepository()
        repo.browseResult = ContentBrowsePage(listOf(content("ct_1")), null)
        val uc = AuthorContentListingUseCase(repo)

        uc.list(PublicId("au_1"), null, 20)

        assertEquals(20, repo.lastBrowseLimit, "limit=20 must be passed unchanged")
    }

    @Test
    fun list_forwardsLimitHundredUnchanged() {
        val repo = FakeContentRepository()
        repo.browseResult = ContentBrowsePage(listOf(content("ct_1")), null)
        val uc = AuthorContentListingUseCase(repo)

        uc.list(PublicId("au_1"), null, 100)

        assertEquals(100, repo.lastBrowseLimit, "limit=100 must be passed unchanged")
    }

    // ------------------------------------------------------------------
    // Test 4 — preserves repository result (page with content + nextCursor)
    // ------------------------------------------------------------------

    @Test
    fun list_preservesRepositoryPageUnchanged() {
        val repo = FakeContentRepository()
        val c1 = repo.save(content("ct_1"))
        val c2 = repo.save(content("ct_2"))
        val expectedCursor = ContentCursor(createdAt = 999L, id = PublicId("ct_2"))
        val expected = ContentBrowsePage(contents = listOf(c1, c2), nextCursor = expectedCursor)
        repo.browseResult = expected
        val uc = AuthorContentListingUseCase(repo)

        val result = uc.list(PublicId("au_1"), null, 10)

        assertSame(expected, result, "the repository page must be returned unchanged")
        assertEquals(2, result.contents.size, "contents must have 2 items")
        assertSame(c1, result.contents[0], "first content must be identical")
        assertSame(c2, result.contents[1], "second content must be identical")
        assertSame(expectedCursor, result.nextCursor, "nextCursor must be identical")
    }

    // ------------------------------------------------------------------
    // Test 5 — empty page
    // ------------------------------------------------------------------

    @Test
    fun list_emptyPageReturnedUnchanged() {
        val repo = FakeContentRepository()
        val uc = AuthorContentListingUseCase(repo)

        val result = uc.list(PublicId("au_unknown"), null, 20)

        assertTrue(result.contents.isEmpty(), "empty page must have empty contents list")
        assertNull(result.nextCursor, "empty page must have null nextCursor")
        assertEquals(PublicId("au_unknown"), repo.lastBrowseAuthorId)
        assertNull(repo.lastBrowseInstanceId)
        assertNull(repo.lastBrowseHasMedia)
    }

    // ------------------------------------------------------------------
    // Test 6 — repository exception propagation
    // ------------------------------------------------------------------

    @Test
    fun list_repositoryExceptionPropagatesUnchanged() {
        val repo = object : ContentRepository {
            override fun findPublicContent(
                instanceId: PublicId?,
                authorId: PublicId?,
                hasMedia: Boolean?,
                cursor: ContentCursor?,
                limit: Int
            ): ContentBrowsePage = throw IllegalStateException("database unavailable")

            override fun findById(id: PublicId): Content? = null
            override fun findByInstanceAndSourceContentId(
                instanceId: PublicId,
                sourceContentId: String
            ): Content? = null
            override fun save(content: Content): Content = content
        }
        val uc = AuthorContentListingUseCase(repo)

        try {
            uc.list(PublicId("au_1"), null, 10)
            throw AssertionError("expected IllegalStateException to propagate")
        } catch (e: IllegalStateException) {
            assertEquals("database unavailable", e.message, "the original exception message must propagate unchanged")
        }
    }
}
