package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.repository.ContentRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

class GetContentByIdUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fake ContentRepository that records the requested id
    // ------------------------------------------------------------------

    private class FakeContentRepository : ContentRepository {
        val rows = mutableMapOf<String, Content>()
        var lastRequestedId: PublicId? = null
            private set

        override fun findById(id: PublicId): Content? {
            lastRequestedId = id
            return rows[id.value]
        }

        override fun findByInstanceAndSourceContentId(
            instanceId: PublicId,
            sourceContentId: String
        ): Content? =
            rows.values.firstOrNull { it.instanceId == instanceId && it.sourceContentId == sourceContentId }

        override fun save(content: Content): Content {
            rows[content.id.value] = content
            return content
        }
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private fun content(): Content = Content.createFromAdapter(
        instanceIdValue = "inst_misskey_io",
        sourceContentId = "sc-1",
        authorId = PublicId("au_author_1"),
        sourceId = PublicId("src_misskey"),
        text = "hello stage 9",
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
    // 1. Repository returns Content -> UseCase returns the same Content
    // ------------------------------------------------------------------

    @Test
    fun get_whenContentExists_returnsSameContent() {
        val repo = FakeContentRepository()
        val stored = repo.save(content())
        val uc = GetContentByIdUseCase(repo)

        val result = uc.getById(stored.id)

        assertSame(stored, result, "the repository aggregate must be returned unchanged")
    }

    // ------------------------------------------------------------------
    // 2. Repository returns null -> UseCase returns null
    // ------------------------------------------------------------------

    @Test
    fun get_whenContentAbsent_returnsNull() {
        val repo = FakeContentRepository()
        val uc = GetContentByIdUseCase(repo)

        assertNull(uc.getById(PublicId("ct_not_persisted")))
    }

    // ------------------------------------------------------------------
    // 3. The requested Content PublicId is passed through correctly
    // ------------------------------------------------------------------

    @Test
    fun get_passesRequestedPublicIdToRepository() {
        val repo = FakeContentRepository()
        val uc = GetContentByIdUseCase(repo)

        // Request an id even when nothing is stored: the exact id must reach the repository.
        val requested = PublicId("ct_requested_id")
        val result = uc.getById(requested)

        assertEquals(requested, repo.lastRequestedId, "repository must be queried with the requested id")
        assertNull(result)
    }
}
