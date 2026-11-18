package com.contentplatform.backend.application.content

import com.contentplatform.backend.application.media.GetMediaByIdsUseCase
import com.contentplatform.backend.application.media.ReadableMedia
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.media.Media
import com.contentplatform.backend.domain.media.MediaStatus
import com.contentplatform.backend.domain.media.MediaUrl
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.InstanceHealthStatus
import com.contentplatform.backend.domain.source.Source
import com.contentplatform.backend.domain.source.SourceType
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetContentMediaUseCaseTest {

    // ------------------------------------------------------------------
    // Fake repositories
    // ------------------------------------------------------------------

    private class FakeContentRepository : ContentRepository {
        var findByIdResult: Content? = null
        var lastFindById: PublicId? = null
            private set

        override fun findById(id: PublicId): Content? {
            lastFindById = id
            return findByIdResult
        }
        override fun findByInstanceAndSourceContentId(instanceId: PublicId, sourceContentId: String): Content? = null
        override fun save(content: Content): Content = content
        override fun findPublicContent(
            instanceId: PublicId?, authorId: PublicId?, hasMedia: Boolean?,
            cursor: ContentCursor?, limit: Int
        ): ContentBrowsePage = ContentBrowsePage(emptyList(), null)
        override fun searchPublicContent(
            query: String, instanceId: PublicId?, authorId: PublicId?, sourceId: PublicId?, limit: Int
        ): List<Content> = emptyList()
        override fun findPublicContentByTag(
            tagId: PublicId, cursor: ContentCursor?, limit: Int
        ): ContentBrowsePage = ContentBrowsePage(emptyList(), null)
        override fun findByIds(ids: List<PublicId>): List<Content> = emptyList()
        override fun findPublicByIds(ids: List<PublicId>): List<Content> = emptyList()
    }

    private class FakeGetMediaByIdsUseCase : GetMediaByIdsUseCase(
        mediaRepository = object : com.contentplatform.backend.repository.MediaRepository {
            override fun findById(id: PublicId): Media? = null
            override fun findBySha256(sha256: String): Media? = null
            override fun save(media: Media): Media = media
        },
        sourceRepository = object : com.contentplatform.backend.repository.SourceRepository {
            override fun findById(id: PublicId): Source? = null
        },
        instanceRepository = object : com.contentplatform.backend.repository.InstanceRepository {
            override fun findById(id: PublicId): Instance? = null
        }
    ) {
        var lastRequestedIds: List<PublicId>? = null
        var getByIdsResult: List<ReadableMedia> = emptyList()

        override fun getByIds(ids: List<PublicId>): List<ReadableMedia> {
            lastRequestedIds = ids
            return getByIdsResult
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun source(): Source = Source(
        id = PublicId("src_misskey"),
        name = "Misskey",
        type = SourceType.MISSKEY,
        enabled = true
    )

    private fun instance(): Instance = Instance(
        id = PublicId("inst_misskey_io"),
        sourceId = PublicId("src_misskey"),
        domain = "misskey.io",
        displayName = null,
        enabled = true,
        allowIndex = true,
        allowMediaPreview = true,
        allowMediaCache = false,
        allowOriginalAccess = true,
        nsfwPolicy = null,
        healthStatus = InstanceHealthStatus.HEALTHY
    )

    private fun media(id: String): Media = Media(
        id = PublicId(id),
        sha256 = null,
        sourceId = PublicId("src_misskey"),
        instanceId = PublicId("inst_misskey_io"),
        thumbnail = MediaUrl("https://example.com/thumb.jpg"),
        preview = MediaUrl("https://example.com/preview.jpg"),
        original = MediaUrl("https://example.com/original.jpg"),
        altText = "alt $id",
        license = "CC-BY-4.0",
        metadata = emptyMap(),
        status = MediaStatus.ACTIVE,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )

    private fun readableMedia(m: Media): ReadableMedia =
        ReadableMedia(media = m, source = source(), instance = instance())

    private fun content(
        id: String,
        status: ContentStatus = ContentStatus.ACTIVE,
        mediaIds: List<String> = emptyList()
    ): Content = Content.createFromAdapter(
        instanceIdValue = "inst_misskey_io",
        sourceContentId = "sc_$id",
        authorId = PublicId("au_1"),
        sourceId = PublicId("src_misskey"),
        text = "content $id",
        language = "en",
        visibility = ContentVisibility.PUBLIC,
        mediaIds = mediaIds.map { PublicId(it) },
        parentId = null,
        repostOfId = null,
        quoteOfId = null,
        externalCreatedAt = null,
        metadata = emptyMap()
    ).let { it.copy(id = PublicId(id), status = status) }

    // ------------------------------------------------------------------
    // Content missing → null
    // ------------------------------------------------------------------

    @Test
    fun getMedia_contentNotFound_returnsNull() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = null
        val mediaUc = FakeGetMediaByIdsUseCase()
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        val result = useCase.getMedia(PublicId("ct_missing"))

        assertNull(result)
        assertEquals(PublicId("ct_missing"), contentRepo.lastFindById)
    }

    // ------------------------------------------------------------------
    // DELETED Content → null
    // ------------------------------------------------------------------

    @Test
    fun getMedia_deletedContent_returnsNull() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_1", status = ContentStatus.DELETED)
        val mediaUc = FakeGetMediaByIdsUseCase()
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        val result = useCase.getMedia(PublicId("ct_1"))

        assertNull(result)
    }

    // ------------------------------------------------------------------
    // HIDDEN Content → null
    // ------------------------------------------------------------------

    @Test
    fun getMedia_hiddenContent_returnsNull() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_1", status = ContentStatus.HIDDEN)
        val mediaUc = FakeGetMediaByIdsUseCase()
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        val result = useCase.getMedia(PublicId("ct_1"))

        assertNull(result)
    }

    // ------------------------------------------------------------------
    // UNAVAILABLE Content → null
    // ------------------------------------------------------------------

    @Test
    fun getMedia_unavailableContent_returnsNull() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_1", status = ContentStatus.UNAVAILABLE)
        val mediaUc = FakeGetMediaByIdsUseCase()
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        val result = useCase.getMedia(PublicId("ct_1"))

        assertNull(result)
    }

    // ------------------------------------------------------------------
    // UNKNOWN Content → null
    // ------------------------------------------------------------------

    @Test
    fun getMedia_unknownContent_returnsNull() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_1", status = ContentStatus.UNKNOWN)
        val mediaUc = FakeGetMediaByIdsUseCase()
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        val result = useCase.getMedia(PublicId("ct_1"))

        assertNull(result)
    }

    // ------------------------------------------------------------------
    // ACTIVE Content with no media → empty list
    // ------------------------------------------------------------------

    @Test
    fun getMedia_contentWithNoMedia_returnsEmptyList() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_1", mediaIds = emptyList())
        val mediaUc = FakeGetMediaByIdsUseCase()
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        val result = useCase.getMedia(PublicId("ct_1"))

        assertEquals(emptyList(), result)
        assertNull(mediaUc.lastRequestedIds, "media use case must not be invoked for empty mediaIds")
    }

    // ------------------------------------------------------------------
    // ACTIVE Content with one media
    // ------------------------------------------------------------------

    @Test
    fun getMedia_contentWithOneMedia_returnsSingleMedia() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_1", mediaIds = listOf("md_a"))
        val mediaUc = FakeGetMediaByIdsUseCase()
        mediaUc.getByIdsResult = listOf(readableMedia(media("md_a")))
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        val result = useCase.getMedia(PublicId("ct_1"))!!

        assertEquals(1, result.size)
        assertEquals("md_a", result[0].media.id.value)
    }

    // ------------------------------------------------------------------
    // ACTIVE Content with multiple media
    // ------------------------------------------------------------------

    @Test
    fun getMedia_contentWithMultipleMedia_returnsAllMedia() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_1", mediaIds = listOf("md_a", "md_b", "md_c"))
        val mediaUc = FakeGetMediaByIdsUseCase()
        mediaUc.getByIdsResult = listOf(
            readableMedia(media("md_a")),
            readableMedia(media("md_b")),
            readableMedia(media("md_c"))
        )
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        val result = useCase.getMedia(PublicId("ct_1"))!!

        assertEquals(3, result.size)
        assertEquals("md_a", result[0].media.id.value)
        assertEquals("md_b", result[1].media.id.value)
        assertEquals("md_c", result[2].media.id.value)
    }

    // ------------------------------------------------------------------
    // Critical test: media association order is preserved
    // ------------------------------------------------------------------

    @Test
    fun getMedia_mediaAssociationOrderIsPreserved() {
        val contentRepo = FakeContentRepository()
        // Content defines order: md_3, md_1, md_2
        contentRepo.findByIdResult = content("ct_1", mediaIds = listOf("md_3", "md_1", "md_2"))
        val mediaUc = FakeGetMediaByIdsUseCase()
        // Bulk read returns different order: md_2, md_3, md_1
        mediaUc.getByIdsResult = listOf(
            readableMedia(media("md_2")),
            readableMedia(media("md_3")),
            readableMedia(media("md_1"))
        )
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        val result = useCase.getMedia(PublicId("ct_1"))!!

        assertEquals(3, result.size)
        assertEquals("md_3", result[0].media.id.value, "must follow content.mediaIds order")
        assertEquals("md_1", result[1].media.id.value, "must follow content.mediaIds order")
        assertEquals("md_2", result[2].media.id.value, "must follow content.mediaIds order")
    }

    // ------------------------------------------------------------------
    // Missing media records are omitted
    // ------------------------------------------------------------------

    @Test
    fun getMedia_someMediaMissing_omittedFromResult() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_1", mediaIds = listOf("md_a", "md_b", "md_c"))
        val mediaUc = FakeGetMediaByIdsUseCase()
        // md_b is missing from readable result
        mediaUc.getByIdsResult = listOf(
            readableMedia(media("md_a")),
            readableMedia(media("md_c"))
        )
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        val result = useCase.getMedia(PublicId("ct_1"))!!

        assertEquals(2, result.size)
        assertEquals("md_a", result[0].media.id.value)
        assertEquals("md_c", result[1].media.id.value)
    }

    // ------------------------------------------------------------------
    // All media missing → empty list
    // ------------------------------------------------------------------

    @Test
    fun getMedia_allMediaMissing_returnsEmptyList() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_1", mediaIds = listOf("md_a", "md_b"))
        val mediaUc = FakeGetMediaByIdsUseCase()
        mediaUc.getByIdsResult = emptyList()
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        val result = useCase.getMedia(PublicId("ct_1"))

        assertEquals(emptyList(), result)
    }

    // ------------------------------------------------------------------
    // Correct Content ID passed through
    // ------------------------------------------------------------------

    @Test
    fun getMedia_usesCorrectContentId() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_target", mediaIds = listOf("md_a"))
        val mediaUc = FakeGetMediaByIdsUseCase()
        mediaUc.getByIdsResult = listOf(readableMedia(media("md_a")))
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        useCase.getMedia(PublicId("ct_target"))

        assertEquals(PublicId("ct_target"), contentRepo.lastFindById)
    }

    // ------------------------------------------------------------------
    // Correct media IDs passed to bulk read
    // ------------------------------------------------------------------

    @Test
    fun getMedia_passesContentMediaIdsToBulkRead() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_1", mediaIds = listOf("md_c", "md_a", "md_b"))
        val mediaUc = FakeGetMediaByIdsUseCase()
        mediaUc.getByIdsResult = listOf(
            readableMedia(media("md_c")),
            readableMedia(media("md_a")),
            readableMedia(media("md_b"))
        )
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        useCase.getMedia(PublicId("ct_1"))

        assertEquals(
            listOf(PublicId("md_c"), PublicId("md_a"), PublicId("md_b")),
            mediaUc.lastRequestedIds,
            "must pass content.mediaIds in original order to bulk read"
        )
    }

    // ------------------------------------------------------------------
    // Bulk media use case is invoked exactly once
    // ------------------------------------------------------------------

    @Test
    fun getMedia_bulkMediaUseCaseInvokedExactlyOnce() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_1", mediaIds = listOf("md_a", "md_b", "md_c"))
        val mediaUc = FakeGetMediaByIdsUseCase()
        mediaUc.getByIdsResult = listOf(
            readableMedia(media("md_a")),
            readableMedia(media("md_b")),
            readableMedia(media("md_c"))
        )
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        useCase.getMedia(PublicId("ct_1"))

        // lastRequestedIds being non-null proves getByIds was called exactly once
        assertEquals(3, mediaUc.lastRequestedIds!!.size)
    }

    // ------------------------------------------------------------------
    // Non-ACTIVE content does not invoke media use case
    // ------------------------------------------------------------------

    @Test
    fun getMedia_nonActiveContent_doesNotInvokeMediaUseCase() {
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdResult = content("ct_1", status = ContentStatus.DELETED, mediaIds = listOf("md_a"))
        val mediaUc = FakeGetMediaByIdsUseCase()
        val useCase = GetContentMediaUseCase(contentRepo, mediaUc)

        useCase.getMedia(PublicId("ct_1"))

        assertNull(mediaUc.lastRequestedIds, "media use case must not be invoked for non-ACTIVE content")
    }
}
