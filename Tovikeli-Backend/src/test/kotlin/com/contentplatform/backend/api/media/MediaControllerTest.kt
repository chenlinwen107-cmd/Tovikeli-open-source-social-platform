package com.contentplatform.backend.api.media

import com.contentplatform.backend.application.media.GetMediaByIdUseCase
import com.contentplatform.backend.application.media.GetMediaByIdsUseCase
import com.contentplatform.backend.application.media.MediaReadResult
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MVC slice test for [MediaController].
 *
 * The controller under test is registered because the slice property enables
 * its datasource-conditional wiring. The Stage 23-B use case is mocked; no
 * database, no Flyway and no external HTTP is involved.
 */
@WebMvcTest(controllers = [MediaController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class MediaControllerTest {

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

    // ------------------------------------------------------------------
    // 1. Readable Media -> 200
    // ------------------------------------------------------------------

    @Test
    fun getMedia_whenReadable_returns200WithResponseDto() {
        val m = media()
        Mockito.`when`(getMediaByIdUseCase.getById(m.id))
            .thenReturn(MediaReadResult.Readable(m, source(), instance()))

        mockMvc.perform(get("/api/v1/media/{mediaId}", "md_test_media"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("md_test_media"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.sourceId").value("src_misskey"))
            .andExpect(jsonPath("$.instanceId").value("inst_misskey_io"))
            .andExpect(jsonPath("$.thumbnailUrl").value("https://example.com/thumb.jpg"))
            .andExpect(jsonPath("$.previewUrl").value("https://example.com/preview.jpg"))
            .andExpect(jsonPath("$.originalUrl").value("https://example.com/original.jpg"))
            .andExpect(jsonPath("$.altText").value("test alt"))
            .andExpect(jsonPath("$.license").value("CC-BY-4.0"))
            .andExpect(jsonPath("$.metadata.width").value(1920))
            .andExpect(jsonPath("$.createdAt").value(1_700_000_000_000L))
            .andExpect(jsonPath("$.updatedAt").value(1_700_000_000_000L))
    }

    // ------------------------------------------------------------------
    // 2. Media missing -> 404
    // ------------------------------------------------------------------

    @Test
    fun getMedia_whenNotFound_returns404() {
        Mockito.`when`(getMediaByIdUseCase.getById(PublicId("md_missing")))
            .thenReturn(MediaReadResult.NotFound)

        mockMvc.perform(get("/api/v1/media/{mediaId}", "md_missing"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 3. Source missing -> 404
    // ------------------------------------------------------------------

    @Test
    fun getMedia_whenSourceMissing_returns404() {
        Mockito.`when`(getMediaByIdUseCase.getById(PublicId("md_test_media")))
            .thenReturn(MediaReadResult.NotFound)

        mockMvc.perform(get("/api/v1/media/{mediaId}", "md_test_media"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 4. Instance missing -> 404
    // ------------------------------------------------------------------

    @Test
    fun getMedia_whenInstanceMissing_returns404() {
        Mockito.`when`(getMediaByIdUseCase.getById(PublicId("md_test_media")))
            .thenReturn(MediaReadResult.NotFound)

        mockMvc.perform(get("/api/v1/media/{mediaId}", "md_test_media"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 5. Non-ACTIVE Media -> 404
    // ------------------------------------------------------------------

    @Test
    fun getMedia_whenNotReadable_returns404() {
        Mockito.`when`(getMediaByIdUseCase.getById(PublicId("md_test_media")))
            .thenReturn(MediaReadResult.NotReadable)

        mockMvc.perform(get("/api/v1/media/{mediaId}", "md_test_media"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 6. Disabled Source -> 404
    // ------------------------------------------------------------------

    @Test
    fun getMedia_whenSourceDisabled_returns404() {
        Mockito.`when`(getMediaByIdUseCase.getById(PublicId("md_test_media")))
            .thenReturn(MediaReadResult.NotReadable)

        mockMvc.perform(get("/api/v1/media/{mediaId}", "md_test_media"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 7. Blank ID -> 400
    // ------------------------------------------------------------------

    @Test
    fun getMedia_whenBlankId_returns400() {
        mockMvc.perform(get("/api/v1/media/{mediaId}", "   "))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getMediaByIdUseCase)
    }

    // ------------------------------------------------------------------
    // 8. Whitespace-only ID -> 400
    // ------------------------------------------------------------------

    @Test
    fun getMedia_whenWhitespaceOnlyId_returns400() {
        mockMvc.perform(get("/api/v1/media/{mediaId}", "\t\n"))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getMediaByIdUseCase)
    }

    // ------------------------------------------------------------------
    // 9. Preview disabled -> 200 with thumbnailUrl=null + previewUrl=null
    // ------------------------------------------------------------------

    @Test
    fun getMedia_whenPreviewDisabled_returns200WithNullPreviewUrls() {
        val m = media()
        val inst = instance(allowMediaPreview = false)
        Mockito.`when`(getMediaByIdUseCase.getById(m.id))
            .thenReturn(MediaReadResult.Readable(m, source(), inst))

        mockMvc.perform(get("/api/v1/media/{mediaId}", "md_test_media"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.thumbnailUrl").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.previewUrl").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.originalUrl").value("https://example.com/original.jpg"))
    }

    // ------------------------------------------------------------------
    // 10. Original disabled -> 200 with originalUrl=null
    // ------------------------------------------------------------------

    @Test
    fun getMedia_whenOriginalDisabled_returns200WithNullOriginalUrl() {
        val m = media()
        val inst = instance(allowOriginalAccess = false)
        Mockito.`when`(getMediaByIdUseCase.getById(m.id))
            .thenReturn(MediaReadResult.Readable(m, source(), inst))

        mockMvc.perform(get("/api/v1/media/{mediaId}", "md_test_media"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.thumbnailUrl").value("https://example.com/thumb.jpg"))
            .andExpect(jsonPath("$.previewUrl").value("https://example.com/preview.jpg"))
            .andExpect(jsonPath("$.originalUrl").value(org.hamcrest.Matchers.nullValue()))
    }

    // ------------------------------------------------------------------
    // 11. Preview + original disabled -> both hidden
    // ------------------------------------------------------------------

    @Test
    fun getMedia_whenPreviewAndOriginalDisabled_returns200WithBothNull() {
        val m = media()
        val inst = instance(allowMediaPreview = false, allowOriginalAccess = false)
        Mockito.`when`(getMediaByIdUseCase.getById(m.id))
            .thenReturn(MediaReadResult.Readable(m, source(), inst))

        mockMvc.perform(get("/api/v1/media/{mediaId}", "md_test_media"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.thumbnailUrl").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.previewUrl").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.originalUrl").value(org.hamcrest.Matchers.nullValue()))
    }

    // ------------------------------------------------------------------
    // 12. Media URL itself null -> response remains null
    // ------------------------------------------------------------------

    @Test
    fun getMedia_whenMediaUrlsNull_returns200WithNullUrls() {
        val m = media(thumbnail = null, preview = null, original = null)
        Mockito.`when`(getMediaByIdUseCase.getById(m.id))
            .thenReturn(MediaReadResult.Readable(m, source(), instance()))

        mockMvc.perform(get("/api/v1/media/{mediaId}", "md_test_media"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.thumbnailUrl").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.previewUrl").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.originalUrl").value(org.hamcrest.Matchers.nullValue()))
    }
}
