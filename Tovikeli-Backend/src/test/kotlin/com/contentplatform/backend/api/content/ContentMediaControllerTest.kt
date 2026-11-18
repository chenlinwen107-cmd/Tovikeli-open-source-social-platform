package com.contentplatform.backend.api.content

import com.contentplatform.backend.application.media.ReadableMedia
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.media.Media
import com.contentplatform.backend.domain.media.MediaStatus
import com.contentplatform.backend.domain.media.MediaUrl
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.InstanceHealthStatus
import com.contentplatform.backend.domain.source.Source
import com.contentplatform.backend.domain.source.SourceType
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import com.contentplatform.backend.repository.UserRepository
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MVC slice test for the Content Media endpoint (Stage 33).
 *
 * Tests GET /api/v1/contents/{contentId}/media.
 */
@WebMvcTest(controllers = [ContentController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class ContentMediaControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var browseContentUseCase: com.contentplatform.backend.application.content.BrowseContentUseCase

    @MockitoBean
    private lateinit var getContentDetailUseCase: com.contentplatform.backend.application.content.GetContentDetailUseCase

    @MockitoBean
    private lateinit var getContentMediaUseCase: com.contentplatform.backend.application.content.GetContentMediaUseCase

    @MockitoBean
    private lateinit var getContentRelationshipsUseCase: com.contentplatform.backend.application.content.GetContentRelationshipsUseCase

    @MockitoBean
    private lateinit var deleteContentUseCase: com.contentplatform.backend.application.content.DeleteContentUseCase

    @MockitoBean
    private lateinit var hideContentUseCase: com.contentplatform.backend.application.content.HideContentUseCase

    @MockitoBean
    private lateinit var restoreContentUseCase: com.contentplatform.backend.application.content.RestoreContentUseCase

        @MockitoBean
    private lateinit var publishContentUseCase: com.contentplatform.backend.application.content.PublishContentUseCase

    @MockitoBean
    private lateinit var editContentUseCase: com.contentplatform.backend.application.content.EditContentUseCase

    @MockitoBean
    private lateinit var contentAccessUseCase: com.contentplatform.backend.application.content.ContentAccessUseCase

    @MockitoBean
    private lateinit var browseHistoryUseCase: com.contentplatform.backend.application.history.BrowseHistoryUseCase

    @MockitoBean
    private lateinit var authorRepository: com.contentplatform.backend.repository.AuthorRepository

    @MockitoBean
    private lateinit var userRepository: UserRepository

    // ------------------------------------------------------------------
    // Fixture helpers
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
        metadata = mapOf("key" to "value"),
        status = MediaStatus.ACTIVE,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_001L
    )

    private fun readableMedia(m: Media): ReadableMedia =
        ReadableMedia(media = m, source = source(), instance = instance())

    // ------------------------------------------------------------------
    // 200 with empty list
    // ------------------------------------------------------------------

    @Test
    fun getContentMedia_emptyList_returns200WithEmptyArray() {
        val contentId = "ct_1"
        Mockito.`when`(getContentMediaUseCase.getMedia(PublicId(contentId)))
            .thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/contents/{contentId}/media", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$").isEmpty)
    }

    // ------------------------------------------------------------------
    // 200 with one media
    // ------------------------------------------------------------------

    @Test
    fun getContentMedia_oneMedia_returns200WithOneItem() {
        val contentId = "ct_1"
        val m = media("md_a")
        Mockito.`when`(getContentMediaUseCase.getMedia(PublicId(contentId)))
            .thenReturn(listOf(readableMedia(m)))

        mockMvc.perform(get("/api/v1/contents/{contentId}/media", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("md_a"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].sourceId").value("src_misskey"))
            .andExpect(jsonPath("$[0].instanceId").value("inst_misskey_io"))
    }

    // ------------------------------------------------------------------
    // 200 with multiple media
    // ------------------------------------------------------------------

    @Test
    fun getContentMedia_multipleMedia_returns200WithAllItems() {
        val contentId = "ct_1"
        val m1 = media("md_a")
        val m2 = media("md_b")
        val m3 = media("md_c")
        Mockito.`when`(getContentMediaUseCase.getMedia(PublicId(contentId)))
            .thenReturn(listOf(readableMedia(m1), readableMedia(m2), readableMedia(m3)))

        mockMvc.perform(get("/api/v1/contents/{contentId}/media", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("md_a"))
            .andExpect(jsonPath("$[1].id").value("md_b"))
            .andExpect(jsonPath("$[2].id").value("md_c"))
    }

    // ------------------------------------------------------------------
    // Exact JSON shape from MediaResponse
    // ------------------------------------------------------------------

    @Test
    fun getContentMedia_jsonFieldNames_areCorrect() {
        val contentId = "ct_1"
        val m = media("md_a")
        Mockito.`when`(getContentMediaUseCase.getMedia(PublicId(contentId)))
            .thenReturn(listOf(readableMedia(m)))

        mockMvc.perform(get("/api/v1/contents/{contentId}/media", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("md_a"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].sourceId").value("src_misskey"))
            .andExpect(jsonPath("$[0].instanceId").value("inst_misskey_io"))
            .andExpect(jsonPath("$[0].thumbnailUrl").value("https://example.com/thumb.jpg"))
            .andExpect(jsonPath("$[0].previewUrl").value("https://example.com/preview.jpg"))
            .andExpect(jsonPath("$[0].originalUrl").value("https://example.com/original.jpg"))
            .andExpect(jsonPath("$[0].altText").value("alt md_a"))
            .andExpect(jsonPath("$[0].license").value("CC-BY-4.0"))
            .andExpect(jsonPath("$[0].metadata.key").value("value"))
            .andExpect(jsonPath("$[0].createdAt").value(1_700_000_000_000L))
            .andExpect(jsonPath("$[0].updatedAt").value(1_700_000_000_001L))
    }

    // ------------------------------------------------------------------
    // 404 for missing Content
    // ------------------------------------------------------------------

    @Test
    fun getContentMedia_missingContent_returns404() {
        Mockito.`when`(getContentMediaUseCase.getMedia(PublicId("ct_missing")))
            .thenReturn(null)

        mockMvc.perform(get("/api/v1/contents/{contentId}/media", "ct_missing"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 404 for inactive Content (DELETED)
    // ------------------------------------------------------------------

    @Test
    fun getContentMedia_deletedContent_returns404() {
        Mockito.`when`(getContentMediaUseCase.getMedia(PublicId("ct_deleted")))
            .thenReturn(null)

        mockMvc.perform(get("/api/v1/contents/{contentId}/media", "ct_deleted"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 404 for inactive Content (HIDDEN)
    // ------------------------------------------------------------------

    @Test
    fun getContentMedia_hiddenContent_returns404() {
        Mockito.`when`(getContentMediaUseCase.getMedia(PublicId("ct_hidden")))
            .thenReturn(null)

        mockMvc.perform(get("/api/v1/contents/{contentId}/media", "ct_hidden"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 404 for inactive Content (UNAVAILABLE)
    // ------------------------------------------------------------------

    @Test
    fun getContentMedia_unavailableContent_returns404() {
        Mockito.`when`(getContentMediaUseCase.getMedia(PublicId("ct_unavailable")))
            .thenReturn(null)

        mockMvc.perform(get("/api/v1/contents/{contentId}/media", "ct_unavailable"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 404 for inactive Content (UNKNOWN)
    // ------------------------------------------------------------------

    @Test
    fun getContentMedia_unknownContent_returns404() {
        Mockito.`when`(getContentMediaUseCase.getMedia(PublicId("ct_unknown")))
            .thenReturn(null)

        mockMvc.perform(get("/api/v1/contents/{contentId}/media", "ct_unknown"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // Blank contentId → 404
    // ------------------------------------------------------------------

    @Test
    fun getContentMedia_blankContentId_returns404() {
        mockMvc.perform(get("/api/v1/contents/{contentId}/media", "   "))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // Ordering is preserved through controller
    // ------------------------------------------------------------------

    @Test
    fun getContentMedia_orderingPreserved() {
        val contentId = "ct_1"
        val m3 = media("md_3")
        val m1 = media("md_1")
        val m2 = media("md_2")
        Mockito.`when`(getContentMediaUseCase.getMedia(PublicId(contentId)))
            .thenReturn(listOf(readableMedia(m3), readableMedia(m1), readableMedia(m2)))

        mockMvc.perform(get("/api/v1/contents/{contentId}/media", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("md_3"))
            .andExpect(jsonPath("$[1].id").value("md_1"))
            .andExpect(jsonPath("$[2].id").value("md_2"))
    }

    // ------------------------------------------------------------------
    // Media URL visibility policies applied
    // ------------------------------------------------------------------

    @Test
    fun getContentMedia_previewDisabled_returnsNullPreviewUrl() {
        val contentId = "ct_1"
        val instanceNoPreview = Instance(
            id = PublicId("inst_no_preview"),
            sourceId = PublicId("src_misskey"),
            domain = "no-preview.example.com",
            displayName = null,
            enabled = true,
            allowIndex = true,
            allowMediaPreview = false,
            allowMediaCache = false,
            allowOriginalAccess = true,
            nsfwPolicy = null,
            healthStatus = InstanceHealthStatus.HEALTHY
        )
        val m = media("md_a")
        val readable = ReadableMedia(media = m, source = source(), instance = instanceNoPreview)
        Mockito.`when`(getContentMediaUseCase.getMedia(PublicId(contentId)))
            .thenReturn(listOf(readable))

        mockMvc.perform(get("/api/v1/contents/{contentId}/media", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].thumbnailUrl").isEmpty)
            .andExpect(jsonPath("$[0].previewUrl").isEmpty)
            .andExpect(jsonPath("$[0].originalUrl").value("https://example.com/original.jpg"))
    }

    @Test
    fun getContentMedia_originalAccessDisabled_returnsNullOriginalUrl() {
        val contentId = "ct_1"
        val instanceNoOriginal = Instance(
            id = PublicId("inst_no_original"),
            sourceId = PublicId("src_misskey"),
            domain = "no-original.example.com",
            displayName = null,
            enabled = true,
            allowIndex = true,
            allowMediaPreview = true,
            allowMediaCache = false,
            allowOriginalAccess = false,
            nsfwPolicy = null,
            healthStatus = InstanceHealthStatus.HEALTHY
        )
        val m = media("md_a")
        val readable = ReadableMedia(media = m, source = source(), instance = instanceNoOriginal)
        Mockito.`when`(getContentMediaUseCase.getMedia(PublicId(contentId)))
            .thenReturn(listOf(readable))

        mockMvc.perform(get("/api/v1/contents/{contentId}/media", contentId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].thumbnailUrl").value("https://example.com/thumb.jpg"))
            .andExpect(jsonPath("$[0].previewUrl").value("https://example.com/preview.jpg"))
            .andExpect(jsonPath("$[0].originalUrl").isEmpty)
    }
}
