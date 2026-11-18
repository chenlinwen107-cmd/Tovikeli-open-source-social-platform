package com.contentplatform.backend.application.content

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

class BrowseContentUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fake ContentRepository that records browse requests and
    // explicitly overrides findPublicContent() with a configurable page.
    // ------------------------------------------------------------------

    private class FakeContentRepository : ContentRepository {
        val rows = mutableMapOf<String, Content>()
        var lastBrowseLimit: Int? = null
            private set
        var lastBrowseInstanceId: PublicId? = null
            private set
        var lastBrowseAuthorId: PublicId? = null
            private set
        var lastBrowseHasMedia: Boolean? = null
            private set
        var lastBrowseCursor: ContentCursor? = null
            private set
        var browseResult: ContentBrowsePage = ContentBrowsePage(emptyList(), null)

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

        override fun findPublicContent(
            instanceId: PublicId?,
            authorId: PublicId?,
            hasMedia: Boolean?,
            cursor: ContentCursor?,
            limit: Int,
            viewerAuthorId: PublicId?
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
        text = "browse fixture $id",
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
    // 1. No arguments (null, null, null, null) are passed through unchanged
    // ------------------------------------------------------------------

    @Test
    fun browse_withoutArguments_passesNullsAndLimitThrough() {
        val repo = FakeContentRepository()
        repo.browseResult = ContentBrowsePage(listOf(content("ct_1")), null)
        val uc = BrowseContentUseCase(repo, org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java))

        uc.browse(null, null, null, null, 42)

        assertNull(repo.lastBrowseInstanceId, "an absent instance filter must reach the repository as null")
        assertNull(repo.lastBrowseAuthorId, "an absent author filter must reach the repository as null")
        assertNull(repo.lastBrowseHasMedia, "an absent media filter must reach the repository as null")
        assertNull(repo.lastBrowseCursor, "an absent cursor must reach the repository as null")
        assertEquals(42, repo.lastBrowseLimit, "the requested limit must reach the repository unchanged")
    }

    // ------------------------------------------------------------------
    // 2. Each optional filter is passed through unchanged
    // ------------------------------------------------------------------

    @Test
    fun browse_withInstanceFilter_passesInstanceIdThroughUnchanged() {
        val repo = FakeContentRepository()
        repo.browseResult = ContentBrowsePage(listOf(content("ct_1")), null)
        val uc = BrowseContentUseCase(repo, org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java))
        val instanceId = PublicId("inst_misskey_io")

        uc.browse(instanceId, null, null, null, 7)

        assertEquals(instanceId, repo.lastBrowseInstanceId, "the instance filter must reach the repository unchanged")
        assertNull(repo.lastBrowseAuthorId)
        assertNull(repo.lastBrowseHasMedia)
        assertNull(repo.lastBrowseCursor)
        assertEquals(7, repo.lastBrowseLimit)
    }

    @Test
    fun browse_withAuthorFilter_passesAuthorIdThroughUnchanged() {
        val repo = FakeContentRepository()
        repo.browseResult = ContentBrowsePage(listOf(content("ct_1")), null)
        val uc = BrowseContentUseCase(repo, org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java))
        val authorId = PublicId("au_author_1")

        uc.browse(null, authorId, null, null, 7)

        assertEquals(authorId, repo.lastBrowseAuthorId, "the author filter must reach the repository unchanged")
        assertNull(repo.lastBrowseInstanceId)
        assertNull(repo.lastBrowseHasMedia)
        assertNull(repo.lastBrowseCursor)
        assertEquals(7, repo.lastBrowseLimit)
    }

    @Test
    fun browse_withHasMediaTrue_passesTrueThroughUnchanged() {
        val repo = FakeContentRepository()
        repo.browseResult = ContentBrowsePage(listOf(content("ct_1")), null)
        val uc = BrowseContentUseCase(repo, org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java))

        uc.browse(null, null, true, null, 7)

        assertEquals(true, repo.lastBrowseHasMedia, "hasMedia=true must reach the repository unchanged")
        assertNull(repo.lastBrowseInstanceId)
        assertNull(repo.lastBrowseAuthorId)
        assertNull(repo.lastBrowseCursor)
        assertEquals(7, repo.lastBrowseLimit)
    }

    @Test
    fun browse_withHasMediaFalse_passesFalseThroughUnchanged() {
        val repo = FakeContentRepository()
        repo.browseResult = ContentBrowsePage(listOf(content("ct_1")), null)
        val uc = BrowseContentUseCase(repo, org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java))

        uc.browse(null, null, false, null, 7)

        assertEquals(false, repo.lastBrowseHasMedia, "hasMedia=false must reach the repository unchanged")
        assertNull(repo.lastBrowseInstanceId)
        assertNull(repo.lastBrowseAuthorId)
        assertNull(repo.lastBrowseCursor)
        assertEquals(7, repo.lastBrowseLimit)
    }

    // ------------------------------------------------------------------
    // 3. A cursor is passed through unchanged
    // ------------------------------------------------------------------

    @Test
    fun browse_withCursor_passesCursorThroughUnchanged() {
        val repo = FakeContentRepository()
        repo.browseResult = ContentBrowsePage(listOf(content("ct_1")), null)
        val uc = BrowseContentUseCase(repo, org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java))
        val cursor = ContentCursor(createdAt = 1_757_000_000_123L, id = PublicId("ct_1"))

        uc.browse(null, null, null, cursor, 7)

        assertEquals(cursor, repo.lastBrowseCursor, "the cursor must reach the repository unchanged")
        assertNull(repo.lastBrowseInstanceId)
        assertNull(repo.lastBrowseAuthorId)
        assertNull(repo.lastBrowseHasMedia)
        assertEquals(7, repo.lastBrowseLimit)
    }

    // ------------------------------------------------------------------
    // 4. All arguments are passed through unchanged (combined)
    // ------------------------------------------------------------------

    @Test
    fun browse_withAllArguments_passesAllThroughUnchanged() {
        val repo = FakeContentRepository()
        repo.browseResult = ContentBrowsePage(listOf(content("ct_1")), null)
        val uc = BrowseContentUseCase(repo, org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java))
        val instanceId = PublicId("inst_misskey_io")
        val authorId = PublicId("au_author_1")
        val cursor = ContentCursor(createdAt = 123L, id = PublicId("ct_1"))

        uc.browse(instanceId, authorId, true, cursor, 5)

        assertEquals(instanceId, repo.lastBrowseInstanceId)
        assertEquals(authorId, repo.lastBrowseAuthorId)
        assertEquals(true, repo.lastBrowseHasMedia)
        assertEquals(cursor, repo.lastBrowseCursor)
        assertEquals(5, repo.lastBrowseLimit)
    }

    // ------------------------------------------------------------------
    // 5. Repository page is returned unchanged
    // ------------------------------------------------------------------

    @Test
    fun browse_whenRepositoryReturnsPage_returnsSamePage() {
        val repo = FakeContentRepository()
        val stored = ContentBrowsePage(
            contents = listOf(repo.save(content("ct_1")), repo.save(content("ct_2"))),
            nextCursor = ContentCursor(createdAt = 456L, id = PublicId("ct_2"))
        )
        repo.browseResult = stored
        val uc = BrowseContentUseCase(repo, org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java))

        val result = uc.browse(null, null, null, null, 10)

        assertSame(stored, result, "the repository page must be returned unchanged, no copies or reordering")
        assertSame(stored.nextCursor, result.nextCursor, "the page nextCursor must be forwarded unchanged")
    }

    // ------------------------------------------------------------------
    // 6. Empty page stays empty (never null)
    // ------------------------------------------------------------------

    @Test
    fun browse_whenNothingBrowsable_returnsEmptyPage() {
        val repo = FakeContentRepository()
        val uc = BrowseContentUseCase(repo, org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java))

        val result = uc.browse(null, null, null, null, 10)

        assertTrue(result.contents.isEmpty(), "an empty repository result must remain an empty list")
        assertNull(result.nextCursor, "an empty page must not carry a next cursor")
        assertEquals(10, repo.lastBrowseLimit)
        assertNull(repo.lastBrowseInstanceId)
        assertNull(repo.lastBrowseAuthorId)
        assertNull(repo.lastBrowseHasMedia)
        assertNull(repo.lastBrowseCursor)
    }
}
