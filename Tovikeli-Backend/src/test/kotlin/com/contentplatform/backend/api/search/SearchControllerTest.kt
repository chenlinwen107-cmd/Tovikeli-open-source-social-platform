package com.contentplatform.backend.api.search

import com.contentplatform.backend.application.content.SearchContentUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentVisibility
import org.hamcrest.Matchers.hasSize
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
 * MVC slice test for [SearchController].
 *
 * The controller under test is registered because the slice property enables
 * its datasource-conditional wiring. The search use case is mocked; no
 * database, no Flyway and no external HTTP is involved.
 *
 * Covered: GET /api/v1/search (q, limit 0..100 default 20, instanceId,
 * authorId, sourceId filters, flat List<ContentResponse> body, validation
 * rules, limit=0 short-circuit).
 */
@WebMvcTest(controllers = [SearchController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class SearchControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var searchContentUseCase: SearchContentUseCase

    @MockitoBean
    private lateinit var userRepository: UserRepository

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private fun matchingContent(sourceContentId: String, text: String = "body of $sourceContentId"): Content =
        Content.createFromAdapter(
            instanceIdValue = "inst_misskey_io",
            sourceContentId = sourceContentId,
            authorId = PublicId("au_author_1"),
            sourceId = PublicId("src_misskey"),
            text = text,
            language = "en",
            visibility = ContentVisibility.PUBLIC,
            mediaIds = listOf(PublicId("media-ref-1")),
            parentId = null,
            repostOfId = null,
            quoteOfId = null,
            externalCreatedAt = 1_600_000_000_000L,
            metadata = emptyMap()
        )

    private fun neverSearch() {
        Mockito.verify(searchContentUseCase, Mockito.never())
            .search(
                Mockito.anyString(),
                Mockito.any<PublicId>(),
                Mockito.any<PublicId>(),
                Mockito.any<PublicId>(),
                Mockito.anyInt()
            )
    }

    // ------------------------------------------------------------------
    // q: valid query forwards the trimmed text, response is a flat array
    // ------------------------------------------------------------------

    @Test
    fun searchContent_whenValidQuery_returns200WithFlatMappedArray() {
        val hit = matchingContent("sc-1", "needle in public text")
        Mockito.`when`(searchContentUseCase.search("needle", null, null, null, 20))
            .thenReturn(listOf(hit))

        mockMvc.perform(get("/api/v1/search").param("q", "needle"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].id").value(hit.id.value))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-1"))
            .andExpect(jsonPath("$[0].text").value("needle in public text"))
            .andExpect(jsonPath("$[0].visibility").value("PUBLIC"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))

        Mockito.verify(searchContentUseCase).search("needle", null, null, null, 20)
    }

    @Test
    fun searchContent_whenQueryHasSurroundingWhitespace_trimsBeforeSearch() {
        Mockito.`when`(searchContentUseCase.search("needle", null, null, null, 20))
            .thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/search").param("q", "   needle   "))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(searchContentUseCase).search("needle", null, null, null, 20)
    }

    // ------------------------------------------------------------------
    // q: missing / empty / whitespace -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun searchContent_whenQueryMissing_returns400() {
        mockMvc.perform(get("/api/v1/search"))
            .andExpect(status().isBadRequest)

        neverSearch()
    }

    @Test
    fun searchContent_whenQueryEmpty_returns400() {
        mockMvc.perform(get("/api/v1/search").param("q", ""))
            .andExpect(status().isBadRequest)

        neverSearch()
    }

    @Test
    fun searchContent_whenQueryWhitespace_returns400() {
        mockMvc.perform(get("/api/v1/search").param("q", "   "))
            .andExpect(status().isBadRequest)

        neverSearch()
    }

    // Invalid q wins over everything else, even a valid limit.
    @Test
    fun searchContent_whenQueryWhitespaceAndLimitZero_returns400() {
        mockMvc.perform(get("/api/v1/search").param("q", "   ").param("limit", "0"))
            .andExpect(status().isBadRequest)

        neverSearch()
    }

    // ------------------------------------------------------------------
    // limit: default / bounds / limit=0 short-circuit
    // ------------------------------------------------------------------

    @Test
    fun searchContent_whenDefaultLimit_uses20() {
        Mockito.`when`(searchContentUseCase.search("needle", null, null, null, 20))
            .thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/search").param("q", "needle"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(searchContentUseCase).search("needle", null, null, null, 20)
    }

    @Test
    fun searchContent_whenLimitOne_returnsOneMappedRow() {
        Mockito.`when`(searchContentUseCase.search("needle", null, null, null, 1))
            .thenReturn(listOf(matchingContent("sc-1")))

        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("limit", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))

        Mockito.verify(searchContentUseCase).search("needle", null, null, null, 1)
    }

    @Test
    fun searchContent_whenLimitMax_returns200() {
        Mockito.`when`(searchContentUseCase.search("needle", null, null, null, 100))
            .thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("limit", "100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(searchContentUseCase).search("needle", null, null, null, 100)
    }

    @Test
    fun searchContent_whenLimitZero_returns200EmptyArrayWithoutInvokingUseCase() {
        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("limit", "0"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        neverSearch()
    }

    @Test
    fun searchContent_whenLimitNegative_returns400() {
        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("limit", "-1"))
            .andExpect(status().isBadRequest)

        neverSearch()
    }

    @Test
    fun searchContent_whenLimitAboveMax_returns400() {
        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("limit", "101"))
            .andExpect(status().isBadRequest)

        neverSearch()
    }

    @Test
    fun searchContent_whenLimitNotAnInteger_returns400() {
        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("limit", "abc"))
            .andExpect(status().isBadRequest)

        neverSearch()
    }

    // ------------------------------------------------------------------
    // Filters: forwarded, AND-combinable, unknown ids -> 200 []
    // ------------------------------------------------------------------

    @Test
    fun searchContent_withInstanceFilter_forwardsTypedFilterToUseCase() {
        val instanceId = PublicId("inst_misskey_io")
        Mockito.`when`(searchContentUseCase.search("needle", instanceId, null, null, 20))
            .thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("instanceId", "inst_misskey_io"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(searchContentUseCase).search("needle", instanceId, null, null, 20)
    }

    @Test
    fun searchContent_withAuthorFilter_forwardsTypedFilterToUseCase() {
        val authorId = PublicId("au_author_1")
        Mockito.`when`(searchContentUseCase.search("needle", null, authorId, null, 20))
            .thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("authorId", "au_author_1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(searchContentUseCase).search("needle", null, authorId, null, 20)
    }

    @Test
    fun searchContent_withSourceFilter_forwardsTypedFilterToUseCase() {
        val sourceId = PublicId("src_misskey")
        Mockito.`when`(searchContentUseCase.search("needle", null, null, sourceId, 20))
            .thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("sourceId", "src_misskey"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(searchContentUseCase).search("needle", null, null, sourceId, 20)
    }

    @Test
    fun searchContent_withAllFilters_forwardsAllToUseCase() {
        val instanceId = PublicId("inst_misskey_io")
        val authorId = PublicId("au_author_1")
        val sourceId = PublicId("src_misskey")
        Mockito.`when`(searchContentUseCase.search("needle", instanceId, authorId, sourceId, 5))
            .thenReturn(listOf(matchingContent("sc-1")))

        mockMvc.perform(
            get("/api/v1/search")
                .param("q", "needle")
                .param("limit", "5")
                .param("instanceId", "inst_misskey_io")
                .param("authorId", "au_author_1")
                .param("sourceId", "src_misskey")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-1"))

        Mockito.verify(searchContentUseCase).search("needle", instanceId, authorId, sourceId, 5)
    }

    // Syntactically valid but unknown filters are NOT errors: 200 [].
    @Test
    fun searchContent_withUnknownInstanceId_returns200EmptyArray() {
        val unknown = PublicId("inst_does_not_exist")
        Mockito.`when`(searchContentUseCase.search("needle", unknown, null, null, 20))
            .thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("instanceId", "inst_does_not_exist"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(searchContentUseCase).search("needle", unknown, null, null, 20)
    }

    @Test
    fun searchContent_withUnknownAuthorId_returns200EmptyArray() {
        val unknown = PublicId("au_does_not_exist")
        Mockito.`when`(searchContentUseCase.search("needle", null, unknown, null, 20))
            .thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("authorId", "au_does_not_exist"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(searchContentUseCase).search("needle", null, unknown, null, 20)
    }

    @Test
    fun searchContent_withUnknownSourceId_returns200EmptyArray() {
        val unknown = PublicId("src_does_not_exist")
        Mockito.`when`(searchContentUseCase.search("needle", null, null, unknown, 20))
            .thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("sourceId", "src_does_not_exist"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(searchContentUseCase).search("needle", null, null, unknown, 20)
    }

    // ------------------------------------------------------------------
    // Blank filters -> 400 (validation always precedes the limit=0 shortcut)
    // ------------------------------------------------------------------

    @Test
    fun searchContent_whenInstanceIdBlank_returns400() {
        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("instanceId", "  "))
            .andExpect(status().isBadRequest)

        neverSearch()
    }

    @Test
    fun searchContent_whenAuthorIdBlank_returns400() {
        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("authorId", ""))
            .andExpect(status().isBadRequest)

        neverSearch()
    }

    @Test
    fun searchContent_whenSourceIdBlankAndLimitZero_returns400() {
        mockMvc.perform(get("/api/v1/search").param("q", "needle").param("limit", "0").param("sourceId", "  "))
            .andExpect(status().isBadRequest)

        neverSearch()
    }
}
