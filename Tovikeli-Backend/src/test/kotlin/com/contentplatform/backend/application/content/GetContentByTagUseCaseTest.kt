package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.TagRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

/**
 * Unit tests for [GetContentByTagUseCase] (Stage 15-C).
 *
 * The use case performs exactly ONE tag existence lookup and then delegates
 * the page query to the ContentRepository. When the tag is missing it returns
 * null (the controller maps that to 404) WITHOUT touching the content
 * repository — no per-row tag lookup, no N+1.
 */
class GetContentByTagUseCaseTest {

    private class FakeTagRepository(
        var tag: Tag? = null,
        var lookups: Int = 0
    ) : TagRepository {
        override fun findById(id: PublicId): Tag? {
            lookups++
            return tag
        }

        override fun findByInstanceAndSourceTagId(instanceId: PublicId, sourceTagId: String): Tag? = null
        override fun findPlatformByName(name: String): Tag? = null
        override fun save(tag: Tag): Tag = tag
    }

    private class FakeContentRepository : ContentRepository {
        var lastTagId: PublicId? = null
            private set
        var lastCursor: ContentCursor? = null
            private set
        var lastLimit: Int? = null
            private set
        var pageResult: ContentBrowsePage = ContentBrowsePage(emptyList(), null)
        var calls = 0
            private set

        override fun findById(id: PublicId): Content? = null
        override fun findByInstanceAndSourceContentId(instanceId: PublicId, sourceContentId: String): Content? = null
        override fun save(content: Content): Content = content

        override fun findPublicContentByTag(tagId: PublicId, cursor: ContentCursor?, limit: Int): ContentBrowsePage {
            calls++
            lastTagId = tagId
            lastCursor = cursor
            lastLimit = limit
            return pageResult
        }
    }

    private fun tag(id: String): Tag = Tag.createPlatform(id, id.lowercase())
    private fun content(): Content = Content.createFromAdapter(
        instanceIdValue = "inst_misskey_io",
        sourceContentId = "sc-1",
        authorId = PublicId("au_1"),
        sourceId = PublicId("src_misskey"),
        text = "body",
        language = "en",
        visibility = ContentVisibility.PUBLIC,
        mediaIds = emptyList(),
        parentId = null,
        repostOfId = null,
        quoteOfId = null,
        externalCreatedAt = null,
        metadata = emptyMap()
    )

    private val tagId = PublicId("tg_anime")

    // ------------------------------------------------------------------
    // 1. Existing tag -> content repository page returned unchanged
    // ------------------------------------------------------------------

    @Test
    fun getByTag_whenTagExists_returnsContentRepositoryPage() {
        val resolvedTag = tag("anime")
        val tags = FakeTagRepository(tag = resolvedTag)
        val contents = FakeContentRepository()
        val storedPage = ContentBrowsePage(
            contents = listOf(content()),
            nextCursor = ContentCursor(createdAt = 5L, id = PublicId("ct_1"))
        )
        contents.pageResult = storedPage
        val uc = GetContentByTagUseCase(tags, contents)
        val cursor = ContentCursor(createdAt = 1L, id = PublicId("ct_prev"))

        val result = uc.getByTag(tagId, cursor, 20)

        assertEquals(1, tags.lookups, "tag existence must be checked exactly once")
        assertEquals(storedPage, result, "the repository page must be returned unchanged")
        // The content query is scoped by the RESOLVED tag aggregate's real
        // (identity-derived) id, not by the caller-supplied raw id.
        assertEquals(resolvedTag.id, contents.lastTagId)
        assertEquals(cursor, contents.lastCursor)
        assertEquals(20, contents.lastLimit)
    }

    // ------------------------------------------------------------------
    // 2. Missing tag -> null, content repository never invoked
    // ------------------------------------------------------------------

    @Test
    fun getByTag_whenTagMissing_returnsNullWithoutContentQuery() {
        val tags = FakeTagRepository(tag = null)
        val contents = FakeContentRepository()
        val uc = GetContentByTagUseCase(tags, contents)

        val result = uc.getByTag(tagId, null, 20)

        assertEquals(1, tags.lookups, "one existence lookup is enough to answer 404")
        assertNull(result, "a missing tag must map to null (HTTP 404 by the caller)")
        assertEquals(0, contents.calls, "no content page query may run for a missing tag")
    }

    // ------------------------------------------------------------------
    // 3. Existing tag with no matching content -> empty page (never null)
    // ------------------------------------------------------------------

    @Test
    fun getByTag_whenTagExistsButNoContent_returnsEmptyPage() {
        val tags = FakeTagRepository(tag = tag("anime"))
        val contents = FakeContentRepository()
        val uc = GetContentByTagUseCase(tags, contents)

        val result = uc.getByTag(tagId, null, 20)

        assertSame(contents.pageResult, result, "an existing tag must always produce a page, even when empty")
        assertEquals(0, result!!.contents.size)
        assertNull(result.nextCursor)
        assertEquals(1, contents.calls)
    }
}
