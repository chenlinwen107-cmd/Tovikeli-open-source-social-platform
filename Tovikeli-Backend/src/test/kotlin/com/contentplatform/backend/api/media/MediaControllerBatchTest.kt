package com.contentplatform.backend.api.media

import com.contentplatform.backend.application.media.GetMediaByIdUseCase
import com.contentplatform.backend.application.media.GetMediaByIdsUseCase
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
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import com.contentplatform.backend.repository.UserRepository
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MVC slice test for [MediaController] batch endpoint.
 *
 * The controller under test is registered because the slice property enables
 * its datasource-conditional wiring. The Stage 24 batch use case is mocked; no
 * database, no Flyway and no external HTTP is involved.
 */
@WebMvcTest(controllers = [MediaController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class MediaControllerBatchTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var getMediaByIdUseCase: GetMediaByIdUseCase

    @MockitoBean
    private lateinit var getMediaByIdsUseCase: GetMediaByIdsUseCase

    @MockitoBean
    private lateinit var userRepository: UserRepository

    // ------------------------------------------------------------------
    // Fixture helpers
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
        thumbnail: MediaUrl? = MediaUrl("https://example.com/thumb.jpg"),
        preview: MediaUrl? = MediaUrl("https://example.com/preview.jpg"),
        original: MediaUrl? = MediaUrl("https://example.com/original.jpg")
    ): Media = Media(
        id = PublicId(id),
        sha256 = null,
        sourceId = PublicId("src_misskey"),
        instanceId = PublicId("inst_misskey_io"),
        thumbnail = thumbnail,
        preview = preview,
        original = original,
        altText = "test alt",
        license = "CC-BY-4.0",
        metadata = emptyMap(),
        status = MediaStatus.ACTIVE,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L
    )

    private fun readableMedia(m: Media): ReadableMedia =
        ReadableMedia(m, source(), instance())

    // ------------------------------------------------------------------
    // 1. Readable batch → 200
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_readableBatch_returns200() {
        val m1 = media("md_a")
        val m2 = media("md_b")
        Mockito.`when`(getMediaByIdsUseCase.getByIds(listOf(PublicId("md_a"), PublicId("md_b"))))
            .thenReturn(listOf(readableMedia(m1), readableMedia(m2)))

        mockMvc.perform(get("/api/v1/media/batch").param("ids", "md_a,md_b"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("md_a"))
            .andExpect(jsonPath("$[1].id").value("md_b"))
    }

    // ------------------------------------------------------------------
    // 2. Mixed IDs → 200 with readable only
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_mixedIds_returns200WithReadableOnly() {
        val m1 = media("md_a")
        Mockito.`when`(getMediaByIdsUseCase.getByIds(
            listOf(PublicId("md_a"), PublicId("md_b"), PublicId("md_c"))
        )).thenReturn(listOf(readableMedia(m1)))

        mockMvc.perform(get("/api/v1/media/batch").param("ids", "md_a,md_b,md_c"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("md_a"))
    }

    // ------------------------------------------------------------------
    // 3. All missing → 200 []
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_allMissing_returns200Empty() {
        Mockito.`when`(getMediaByIdsUseCase.getByIds(
            listOf(PublicId("md_a"), PublicId("md_b"))
        )).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/media/batch").param("ids", "md_a,md_b"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // ------------------------------------------------------------------
    // 4. Blank ID → 400
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_blankId_returns400() {
        mockMvc.perform(get("/api/v1/media/batch").param("ids", "md_a,,md_b"))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getMediaByIdsUseCase)
    }

    // ------------------------------------------------------------------
    // 5. Whitespace ID → 400
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_whitespaceId_returns400() {
        mockMvc.perform(get("/api/v1/media/batch").param("ids", "md_a,   ,md_b"))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getMediaByIdsUseCase)
    }

    // ------------------------------------------------------------------
    // 6. Missing ids → 400
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_missingIds_returns400() {
        mockMvc.perform(get("/api/v1/media/batch"))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getMediaByIdsUseCase)
    }

    // ------------------------------------------------------------------
    // 7. >50 IDs → 400
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_over50Ids_returns400() {
        val ids = (1..51).joinToString(",") { "md_$it" }

        mockMvc.perform(get("/api/v1/media/batch").param("ids", ids))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getMediaByIdsUseCase)
    }

    // ------------------------------------------------------------------
    // 8. Duplicate IDs → deduplicated
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_duplicateIds_deduplicated() {
        val m1 = media("md_a")
        Mockito.`when`(getMediaByIdsUseCase.getByIds(
            listOf(PublicId("md_a"), PublicId("md_b"))
        )).thenReturn(listOf(readableMedia(m1)))

        mockMvc.perform(get("/api/v1/media/batch").param("ids", "md_a,md_b,md_a"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("md_a"))
    }

    // ------------------------------------------------------------------
    // 9. Ordering preserved
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_orderingPreserved() {
        val m1 = media("md_c")
        val m2 = media("md_a")
        Mockito.`when`(getMediaByIdsUseCase.getByIds(Mockito.anyList()))
            .thenReturn(listOf(readableMedia(m1), readableMedia(m2)))

        mockMvc.perform(get("/api/v1/media/batch").param("ids", "md_c,md_a,md_b"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("md_c"))
            .andExpect(jsonPath("$[1].id").value("md_a"))
    }

    // ------------------------------------------------------------------
    // 10. Preview visibility
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_previewDisabled_returns200WithNullPreviewUrls() {
        val m = media("md_a")
        val inst = instance(allowMediaPreview = false)
        Mockito.`when`(getMediaByIdsUseCase.getByIds(listOf(PublicId("md_a"))))
            .thenReturn(listOf(ReadableMedia(m, source(), inst)))

        mockMvc.perform(get("/api/v1/media/batch").param("ids", "md_a"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].thumbnailUrl").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[0].previewUrl").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[0].originalUrl").value("https://example.com/original.jpg"))
    }

    // ------------------------------------------------------------------
    // 11. Original visibility
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_originalDisabled_returns200WithNullOriginalUrl() {
        val m = media("md_a")
        val inst = instance(allowOriginalAccess = false)
        Mockito.`when`(getMediaByIdsUseCase.getByIds(listOf(PublicId("md_a"))))
            .thenReturn(listOf(ReadableMedia(m, source(), inst)))

        mockMvc.perform(get("/api/v1/media/batch").param("ids", "md_a"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].thumbnailUrl").value("https://example.com/thumb.jpg"))
            .andExpect(jsonPath("$[0].previewUrl").value("https://example.com/preview.jpg"))
            .andExpect(jsonPath("$[0].originalUrl").value(org.hamcrest.Matchers.nullValue()))
    }

    // ------------------------------------------------------------------
    // 12. Both visibility policies
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_bothDisabled_returns200WithBothNull() {
        val m = media("md_a")
        val inst = instance(allowMediaPreview = false, allowOriginalAccess = false)
        Mockito.`when`(getMediaByIdsUseCase.getByIds(listOf(PublicId("md_a"))))
            .thenReturn(listOf(ReadableMedia(m, source(), inst)))

        mockMvc.perform(get("/api/v1/media/batch").param("ids", "md_a"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].thumbnailUrl").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[0].previewUrl").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[0].originalUrl").value(org.hamcrest.Matchers.nullValue()))
    }

    // ==================================================================
    // Boundary verification tests (Stage 24 Boundary Verification)
    // ==================================================================

    // ------------------------------------------------------------------
    // 13. Trailing blank ID → 400
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_trailingBlankId_returns400() {
        mockMvc.perform(get("/api/v1/media/batch").param("ids", "md_a,"))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getMediaByIdsUseCase)
    }

    // ------------------------------------------------------------------
    // 14. Leading blank ID → 400
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_leadingBlankId_returns400() {
        mockMvc.perform(get("/api/v1/media/batch").param("ids", ",md_a"))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getMediaByIdsUseCase)
    }

    // ------------------------------------------------------------------
    // 15. All-blank ID list → 400
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_allBlankIdList_returns400() {
        mockMvc.perform(get("/api/v1/media/batch").param("ids", ","))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getMediaByIdsUseCase)
    }

    // ------------------------------------------------------------------
    // 16. 51 original IDs with duplicates → 400 (dedup BEFORE is wrong)
    // ------------------------------------------------------------------

    @Test
    fun getMediaBatch_51DuplicateIds_returns400() {
        val ids = List(51) { "md_same" }.joinToString(",")

        mockMvc.perform(get("/api/v1/media/batch").param("ids", ids))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getMediaByIdsUseCase)
    }
}
