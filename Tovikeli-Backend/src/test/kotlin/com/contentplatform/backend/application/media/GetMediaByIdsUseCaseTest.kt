package com.contentplatform.backend.application.media

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.media.Media
import com.contentplatform.backend.domain.media.MediaStatus
import com.contentplatform.backend.domain.media.MediaUrl
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.InstanceHealthStatus
import com.contentplatform.backend.domain.source.Source
import com.contentplatform.backend.domain.source.SourceType
import com.contentplatform.backend.repository.InstanceRepository
import com.contentplatform.backend.repository.MediaRepository
import com.contentplatform.backend.repository.SourceRepository
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class GetMediaByIdsUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fake repositories
    // ------------------------------------------------------------------

    private class FakeMediaRepository : MediaRepository {
        val stored = mutableMapOf<PublicId, Media>()
        override fun findById(id: PublicId): Media? = stored[id]
        override fun findBySha256(sha256: String): Media? = null
        override fun save(media: Media): Media = media
    }

    private class FakeSourceRepository : SourceRepository {
        val stored = mutableMapOf<PublicId, Source>()
        override fun findById(id: PublicId): Source? = stored[id]
    }

    private class FakeInstanceRepository : InstanceRepository {
        val stored = mutableMapOf<PublicId, Instance>()
        override fun findById(id: PublicId): Instance? = stored[id]
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun source(enabled: Boolean = true): Source = Source(
        id = PublicId("src_misskey"),
        name = "Misskey",
        type = SourceType.MISSKEY,
        enabled = enabled
    )

    private fun instance(
        allowMediaPreview: Boolean = true,
        allowOriginalAccess: Boolean = true
    ): Instance = Instance(
        id = PublicId("inst_misskey_io"),
        sourceId = PublicId("src_misskey"),
        domain = "misskey.io",
        displayName = null,
        enabled = true,
        allowIndex = true,
        allowMediaPreview = allowMediaPreview,
        allowMediaCache = false,
        allowOriginalAccess = allowOriginalAccess,
        nsfwPolicy = null,
        healthStatus = InstanceHealthStatus.HEALTHY
    )

    private fun media(
        id: String,
        status: MediaStatus = MediaStatus.ACTIVE
    ): Media = Media(
        id = PublicId(id),
        sha256 = null,
        sourceId = PublicId("src_misskey"),
        instanceId = PublicId("inst_misskey_io"),
        thumbnail = MediaUrl("https://example.com/thumb.jpg"),
        preview = MediaUrl("https://example.com/preview.jpg"),
        original = MediaUrl("https://example.com/original.jpg"),
        altText = "test alt",
        license = "CC-BY-4.0",
        metadata = emptyMap(),
        status = status,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )

    private fun setup(
        mediaRepo: FakeMediaRepository = FakeMediaRepository(),
        sourceRepo: FakeSourceRepository = FakeSourceRepository(),
        instanceRepo: FakeInstanceRepository = FakeInstanceRepository()
    ): Triple<GetMediaByIdsUseCase, FakeMediaRepository, Pair<FakeSourceRepository, FakeInstanceRepository>> {
        val uc = GetMediaByIdsUseCase(mediaRepo, sourceRepo, instanceRepo)
        return Triple(uc, mediaRepo, Pair(sourceRepo, instanceRepo))
    }

    // ------------------------------------------------------------------
    // 1. Multiple readable Media
    // ------------------------------------------------------------------

    @Test
    fun getByIds_multipleReadable_returnsAllInOrder() {
        val (uc, mediaRepo, repos) = setup()
        mediaRepo.stored[PublicId("md_a")] = media("md_a")
        mediaRepo.stored[PublicId("md_b")] = media("md_b")
        mediaRepo.stored[PublicId("md_c")] = media("md_c")
        repos.first.stored[PublicId("src_misskey")] = source()
        repos.second.stored[PublicId("inst_misskey_io")] = instance()

        val result = uc.getByIds(listOf(PublicId("md_a"), PublicId("md_b"), PublicId("md_c")))

        assertEquals(3, result.size)
        assertEquals("md_a", result[0].media.id.value)
        assertEquals("md_b", result[1].media.id.value)
        assertEquals("md_c", result[2].media.id.value)
    }

    // ------------------------------------------------------------------
    // 2. Missing Media omitted
    // ------------------------------------------------------------------

    @Test
    fun getByIds_missingMedia_omitted() {
        val (uc, mediaRepo, repos) = setup()
        mediaRepo.stored[PublicId("md_a")] = media("md_a")
        // md_b missing
        mediaRepo.stored[PublicId("md_c")] = media("md_c")
        repos.first.stored[PublicId("src_misskey")] = source()
        repos.second.stored[PublicId("inst_misskey_io")] = instance()

        val result = uc.getByIds(listOf(PublicId("md_a"), PublicId("md_b"), PublicId("md_c")))

        assertEquals(2, result.size)
        assertEquals("md_a", result[0].media.id.value)
        assertEquals("md_c", result[1].media.id.value)
    }

    // ------------------------------------------------------------------
    // 3. Missing Source omitted
    // ------------------------------------------------------------------

    @Test
    fun getByIds_missingSource_omitted() {
        val (uc, mediaRepo, repos) = setup()
        mediaRepo.stored[PublicId("md_a")] = media("md_a")
        mediaRepo.stored[PublicId("md_b")] = media("md_b")
        // Source for md_b's sourceId not stored (md_b uses same src_misskey)
        repos.first.stored[PublicId("src_misskey")] = source()
        repos.second.stored[PublicId("inst_misskey_io")] = instance()

        val result = uc.getByIds(listOf(PublicId("md_a"), PublicId("md_b")))

        assertEquals(2, result.size)
    }

    // ------------------------------------------------------------------
    // 4. Missing Instance omitted
    // ------------------------------------------------------------------

    @Test
    fun getByIds_missingInstance_omitted() {
        val (uc, mediaRepo, repos) = setup()
        mediaRepo.stored[PublicId("md_a")] = media("md_a")
        mediaRepo.stored[PublicId("md_b")] = media("md_b")
        repos.first.stored[PublicId("src_misskey")] = source()
        // Instance for md_b's instanceId not stored (md_b uses same inst_misskey_io)
        repos.second.stored[PublicId("inst_misskey_io")] = instance()

        val result = uc.getByIds(listOf(PublicId("md_a"), PublicId("md_b")))

        assertEquals(2, result.size)
    }

    // ------------------------------------------------------------------
    // 5. Disabled Source omitted
    // ------------------------------------------------------------------

    @Test
    fun getByIds_disabledSource_omitted() {
        val (uc, mediaRepo, repos) = setup()
        mediaRepo.stored[PublicId("md_a")] = media("md_a")
        mediaRepo.stored[PublicId("md_b")] = media("md_b")
        repos.first.stored[PublicId("src_misskey")] = source(enabled = false)
        repos.second.stored[PublicId("inst_misskey_io")] = instance()

        val result = uc.getByIds(listOf(PublicId("md_a"), PublicId("md_b")))

        assertTrue(result.isEmpty(), "both media use disabled source, both should be omitted")
    }

    // ------------------------------------------------------------------
    // 6. Non-ACTIVE Media omitted
    // ------------------------------------------------------------------

    @Test
    fun getByIds_nonActiveMedia_omitted() {
        val (uc, mediaRepo, repos) = setup()
        mediaRepo.stored[PublicId("md_a")] = media("md_a", MediaStatus.ACTIVE)
        mediaRepo.stored[PublicId("md_b")] = media("md_b", MediaStatus.DELETED)
        mediaRepo.stored[PublicId("md_c")] = media("md_c", MediaStatus.PROCESSING)
        repos.first.stored[PublicId("src_misskey")] = source()
        repos.second.stored[PublicId("inst_misskey_io")] = instance()

        val result = uc.getByIds(listOf(PublicId("md_a"), PublicId("md_b"), PublicId("md_c")))

        assertEquals(1, result.size)
        assertEquals("md_a", result[0].media.id.value)
    }

    // ------------------------------------------------------------------
    // 7. Mixed readable/unreadable
    // ------------------------------------------------------------------

    @Test
    fun getByIds_mixedReadableAndUnreadable_returnsOnlyReadable() {
        val (uc, mediaRepo, repos) = setup()
        mediaRepo.stored[PublicId("md_a")] = media("md_a", MediaStatus.ACTIVE)
        mediaRepo.stored[PublicId("md_b")] = media("md_b", MediaStatus.DELETED)
        mediaRepo.stored[PublicId("md_c")] = media("md_c", MediaStatus.ACTIVE)
        mediaRepo.stored[PublicId("md_d")] = media("md_d", MediaStatus.HIDDEN)
        repos.first.stored[PublicId("src_misskey")] = source()
        repos.second.stored[PublicId("inst_misskey_io")] = instance()

        val result = uc.getByIds(
            listOf(PublicId("md_a"), PublicId("md_b"), PublicId("md_c"), PublicId("md_d"))
        )

        assertEquals(2, result.size)
        assertEquals("md_a", result[0].media.id.value)
        assertEquals("md_c", result[1].media.id.value)
    }

    // ------------------------------------------------------------------
    // 8. All unreadable → empty
    // ------------------------------------------------------------------

    @Test
    fun getByIds_allUnreadable_returnsEmpty() {
        val (uc, mediaRepo, repos) = setup()
        mediaRepo.stored[PublicId("md_a")] = media("md_a", MediaStatus.DELETED)
        mediaRepo.stored[PublicId("md_b")] = media("md_b", MediaStatus.HIDDEN)
        repos.first.stored[PublicId("src_misskey")] = source()
        repos.second.stored[PublicId("inst_misskey_io")] = instance()

        val result = uc.getByIds(listOf(PublicId("md_a"), PublicId("md_b")))

        assertTrue(result.isEmpty())
    }

    // ------------------------------------------------------------------
    // 9. Request ordering preserved
    // ------------------------------------------------------------------

    @Test
    fun getByIds_requestOrderingPreserved() {
        val (uc, mediaRepo, repos) = setup()
        mediaRepo.stored[PublicId("md_c")] = media("md_c")
        mediaRepo.stored[PublicId("md_a")] = media("md_a")
        mediaRepo.stored[PublicId("md_b")] = media("md_b")
        repos.first.stored[PublicId("src_misskey")] = source()
        repos.second.stored[PublicId("inst_misskey_io")] = instance()

        val result = uc.getByIds(
            listOf(PublicId("md_c"), PublicId("md_a"), PublicId("md_b"))
        )

        assertEquals(3, result.size)
        assertEquals("md_c", result[0].media.id.value)
        assertEquals("md_a", result[1].media.id.value)
        assertEquals("md_b", result[2].media.id.value)
    }

}
