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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class GetMediaByIdUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fake repositories
    // ------------------------------------------------------------------

    private class FakeMediaRepository : MediaRepository {
        var stored: Media? = null
        override fun findById(id: PublicId): Media? = stored?.takeIf { it.id == id }
        override fun findBySha256(sha256: String): Media? = null
        override fun save(media: Media): Media = media
    }

    private class FakeSourceRepository : SourceRepository {
        var stored: Source? = null
        override fun findById(id: PublicId): Source? = stored?.takeIf { it.id == id }
    }

    private class FakeInstanceRepository : InstanceRepository {
        var stored: Instance? = null
        override fun findById(id: PublicId): Instance? = stored?.takeIf { it.id == id }
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
        status: MediaStatus = MediaStatus.ACTIVE,
        thumbnail: MediaUrl? = MediaUrl("https://example.com/thumb.jpg"),
        preview: MediaUrl? = MediaUrl("https://example.com/preview.jpg"),
        original: MediaUrl? = MediaUrl("https://example.com/original.jpg")
    ): Media = Media(
        id = PublicId("md_test_media"),
        sha256 = null,
        sourceId = PublicId("src_misskey"),
        instanceId = PublicId("inst_misskey_io"),
        thumbnail = thumbnail,
        preview = preview,
        original = original,
        altText = "test alt",
        license = "CC-BY-4.0",
        metadata = mapOf("width" to 1920),
        status = status,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )

    private fun useCase(
        mediaRepo: FakeMediaRepository = FakeMediaRepository(),
        sourceRepo: FakeSourceRepository = FakeSourceRepository(),
        instanceRepo: FakeInstanceRepository = FakeInstanceRepository()
    ): Triple<GetMediaByIdUseCase, FakeMediaRepository, Pair<FakeSourceRepository, FakeInstanceRepository>> {
        val uc = GetMediaByIdUseCase(mediaRepo, sourceRepo, instanceRepo)
        return Triple(uc, mediaRepo, Pair(sourceRepo, instanceRepo))
    }

    // ------------------------------------------------------------------
    // 1. ACTIVE Media + enabled Source -> Readable
    // ------------------------------------------------------------------

    @Test
    fun getById_activeMediaAndEnabledSource_returnsReadable() {
        val (uc, mediaRepo, repos) = useCase()
        mediaRepo.stored = media()
        repos.first.stored = source()
        repos.second.stored = instance()

        val result = uc.getById(PublicId("md_test_media"))

        assertIs<MediaReadResult.Readable>(result)
        assertEquals("md_test_media", result.media.id.value)
        assertEquals("src_misskey", result.source.id.value)
        assertEquals("inst_misskey_io", result.instance.id.value)
    }

    // ------------------------------------------------------------------
    // 2. Media missing -> NotFound
    // ------------------------------------------------------------------

    @Test
    fun getById_mediaMissing_returnsNotFound() {
        val (uc, _, _) = useCase()

        val result = uc.getById(PublicId("md_nonexistent"))

        assertIs<MediaReadResult.NotFound>(result)
    }

    // ------------------------------------------------------------------
    // 3. Source missing -> NotFound
    // ------------------------------------------------------------------

    @Test
    fun getById_sourceMissing_returnsNotFound() {
        val (uc, mediaRepo, repos) = useCase()
        mediaRepo.stored = media()
        repos.second.stored = instance()
        // source not stored

        val result = uc.getById(PublicId("md_test_media"))

        assertIs<MediaReadResult.NotFound>(result)
    }

    // ------------------------------------------------------------------
    // 4. Instance missing -> NotFound
    // ------------------------------------------------------------------

    @Test
    fun getById_instanceMissing_returnsNotFound() {
        val (uc, mediaRepo, repos) = useCase()
        mediaRepo.stored = media()
        repos.first.stored = source()
        // instance not stored

        val result = uc.getById(PublicId("md_test_media"))

        assertIs<MediaReadResult.NotFound>(result)
    }

    // ------------------------------------------------------------------
    // 5. ACTIVE Media + disabled Source -> NotReadable
    // ------------------------------------------------------------------

    @Test
    fun getById_activeMediaAndDisabledSource_returnsNotReadable() {
        val (uc, mediaRepo, repos) = useCase()
        mediaRepo.stored = media()
        repos.first.stored = source(enabled = false)
        repos.second.stored = instance()

        val result = uc.getById(PublicId("md_test_media"))

        assertIs<MediaReadResult.NotReadable>(result)
    }

    // ------------------------------------------------------------------
    // 6-10. Non-ACTIVE statuses -> NotReadable
    // ------------------------------------------------------------------

    @Test
    fun getById_processingStatus_returnsNotReadable() {
        val (uc, mediaRepo, repos) = useCase()
        mediaRepo.stored = media(status = MediaStatus.PROCESSING)
        repos.first.stored = source()
        repos.second.stored = instance()

        val result = uc.getById(PublicId("md_test_media"))

        assertIs<MediaReadResult.NotReadable>(result)
    }

    @Test
    fun getById_unavailableStatus_returnsNotReadable() {
        val (uc, mediaRepo, repos) = useCase()
        mediaRepo.stored = media(status = MediaStatus.UNAVAILABLE)
        repos.first.stored = source()
        repos.second.stored = instance()

        val result = uc.getById(PublicId("md_test_media"))

        assertIs<MediaReadResult.NotReadable>(result)
    }

    @Test
    fun getById_deletedStatus_returnsNotReadable() {
        val (uc, mediaRepo, repos) = useCase()
        mediaRepo.stored = media(status = MediaStatus.DELETED)
        repos.first.stored = source()
        repos.second.stored = instance()

        val result = uc.getById(PublicId("md_test_media"))

        assertIs<MediaReadResult.NotReadable>(result)
    }

    @Test
    fun getById_hiddenStatus_returnsNotReadable() {
        val (uc, mediaRepo, repos) = useCase()
        mediaRepo.stored = media(status = MediaStatus.HIDDEN)
        repos.first.stored = source()
        repos.second.stored = instance()

        val result = uc.getById(PublicId("md_test_media"))

        assertIs<MediaReadResult.NotReadable>(result)
    }

    @Test
    fun getById_expiredStatus_returnsNotReadable() {
        val (uc, mediaRepo, repos) = useCase()
        mediaRepo.stored = media(status = MediaStatus.EXPIRED)
        repos.first.stored = source()
        repos.second.stored = instance()

        val result = uc.getById(PublicId("md_test_media"))

        assertIs<MediaReadResult.NotReadable>(result)
    }
}
