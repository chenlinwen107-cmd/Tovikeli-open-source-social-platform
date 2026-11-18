package com.contentplatform.backend.application.tag

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.domain.tag.TagOrigin
import com.contentplatform.backend.repository.TagRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

class GetTagByIdUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fake TagRepository that records the requested id
    // ------------------------------------------------------------------

    private class FakeTagRepository : TagRepository {
        val rows = mutableMapOf<String, Tag>()
        var lastRequestedId: PublicId? = null
            private set

        override fun findById(id: PublicId): Tag? {
            lastRequestedId = id
            return rows[id.value]
        }

        override fun findByInstanceAndSourceTagId(
            instanceId: PublicId,
            sourceTagId: String
        ): Tag? =
            rows.values.firstOrNull {
                it.instanceId == instanceId && it.sourceTagId == sourceTagId && it.origin == TagOrigin.EXTERNAL
            }

        override fun findPlatformByName(name: String): Tag? =
            rows.values.firstOrNull { it.name == name && it.origin == TagOrigin.PLATFORM }

        override fun save(tag: Tag): Tag {
            rows[tag.id.value] = tag
            return tag
        }
    }

    // ------------------------------------------------------------------
    // Fixtures (EXTERNAL and PLATFORM tags both resolve through findById)
    // ------------------------------------------------------------------

    private fun externalTag(): Tag = Tag.createFromAdapter(
        name = "misskey",
        normalizedName = "misskey",
        sourceTagId = "tag-ext-1",
        instanceId = PublicId("inst_misskey_io"),
        sourceId = PublicId("src_misskey")
    )

    private fun platformTag(): Tag = Tag.createPlatform(
        name = "BlueArchive",
        normalizedName = null
    )

    // ------------------------------------------------------------------
    // 1. Repository returns Tag -> UseCase returns the same Tag
    // ------------------------------------------------------------------

    @Test
    fun get_whenExternalTagExists_returnsSameTag() {
        val repo = FakeTagRepository()
        val stored = repo.save(externalTag())
        val uc = GetTagByIdUseCase(repo)

        val result = uc.getById(stored.id)

        assertSame(stored, result, "the repository aggregate must be returned unchanged")
        assertEquals(TagOrigin.EXTERNAL, result!!.origin)
    }

    @Test
    fun get_whenPlatformTagExists_returnsSameTag() {
        val repo = FakeTagRepository()
        val stored = repo.save(platformTag())
        val uc = GetTagByIdUseCase(repo)

        val result = uc.getById(stored.id)

        assertSame(stored, result, "the repository aggregate must be returned unchanged")
        assertEquals(TagOrigin.PLATFORM, result!!.origin)
    }

    // ------------------------------------------------------------------
    // 2. Repository returns null -> UseCase returns null
    // ------------------------------------------------------------------

    @Test
    fun get_whenTagAbsent_returnsNull() {
        val repo = FakeTagRepository()
        val uc = GetTagByIdUseCase(repo)

        assertNull(uc.getById(PublicId("tg_not_persisted")))
    }

    // ------------------------------------------------------------------
    // 3. The requested Tag PublicId is passed through correctly
    // ------------------------------------------------------------------

    @Test
    fun get_passesRequestedPublicIdToRepository() {
        val repo = FakeTagRepository()
        val uc = GetTagByIdUseCase(repo)

        val requested = PublicId("tg_requested_id")
        val result = uc.getById(requested)

        assertEquals(requested, repo.lastRequestedId, "repository must be queried with the requested id")
        assertNull(result)
    }
}
