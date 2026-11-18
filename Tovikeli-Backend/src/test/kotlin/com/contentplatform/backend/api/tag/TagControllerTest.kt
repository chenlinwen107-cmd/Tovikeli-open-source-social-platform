package com.contentplatform.backend.api.tag

import com.contentplatform.backend.api.content.ContentCursorCodec
import com.contentplatform.backend.application.content.GetContentByTagUseCase
import com.contentplatform.backend.application.tag.GetTagByIdUseCase
import com.contentplatform.backend.application.tag.SearchTagsUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import java.util.Base64
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import com.contentplatform.backend.repository.UserRepository
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MVC slice test for [TagController].
 *
 * Covers:
 *   GET /api/v1/tags/{id}                detail (EXTERNAL + PLATFORM shape)
 *   GET /api/v1/tags                     tag search (Stage 15-C)
 *   GET /api/v1/tags/{tagId}/contents    tag -> content retrieval (Stage 15-C)
 *
 * Use cases are mocked; no database, no Flyway, no external HTTP.
 */
@WebMvcTest(controllers = [TagController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class TagControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var getTagByIdUseCase: GetTagByIdUseCase

    @MockitoBean
    private lateinit var searchTagsUseCase: SearchTagsUseCase

    @MockitoBean
    private lateinit var getContentByTagUseCase: GetContentByTagUseCase

    @MockitoBean
    private lateinit var userRepository: UserRepository

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private fun platformTag(id: String, name: String, normalizedName: String?): Tag =
        Tag.createPlatform(name = name, normalizedName = normalizedName)

    private fun content(sourceContentId: String): Content = Content.createFromAdapter(
        instanceIdValue = "inst_misskey_io",
        sourceContentId = sourceContentId,
        authorId = PublicId("au_1"),
        sourceId = PublicId("src_misskey"),
        text = "body of $sourceContentId",
        language = "en",
        visibility = ContentVisibility.PUBLIC,
        mediaIds = listOf(PublicId("media-1")),
        parentId = null,
        repostOfId = null,
        quoteOfId = null,
        externalCreatedAt = null,
        metadata = emptyMap()
    )

    private fun page(contents: List<Content>, nextCursor: ContentCursor? = null): ContentBrowsePage =
        ContentBrowsePage(contents, nextCursor)

    private fun b64(logical: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(logical.toByteArray())

    // ------------------------------------------------------------------
    // Detail: EXTERNAL tag found -> 200 with external reference fields
    // ------------------------------------------------------------------

    @Test
    fun getTag_whenExternalTagFound_returns200WithExternalFields() {
        val external = Tag.createFromAdapter(
            name = "misskey",
            normalizedName = "misskey",
            sourceTagId = "tag-ext-1",
            instanceId = PublicId("inst_misskey_io"),
            sourceId = PublicId("src_misskey")
        )
        Mockito.`when`(getTagByIdUseCase.getById(external.id)).thenReturn(external)

        mockMvc.perform(get("/api/v1/tags/{id}", external.id.value))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(external.id.value))
            .andExpect(jsonPath("$.name").value("misskey"))
            .andExpect(jsonPath("$.normalizedName").value("misskey"))
            .andExpect(jsonPath("$.origin").value("EXTERNAL"))
            .andExpect(jsonPath("$.sourceTagId").value("tag-ext-1"))
            .andExpect(jsonPath("$.instanceId").value("inst_misskey_io"))
            .andExpect(jsonPath("$.sourceId").value("src_misskey"))
    }

    // ------------------------------------------------------------------
    // Detail: PLATFORM tag found -> 200 with null external reference fields
    // ------------------------------------------------------------------

    @Test
    fun getTag_whenPlatformTagFound_returns200WithNullExternalFields() {
        val platform = Tag.createPlatform(
            name = "BlueArchive",
            normalizedName = null
        )
        Mockito.`when`(getTagByIdUseCase.getById(platform.id)).thenReturn(platform)

        mockMvc.perform(get("/api/v1/tags/{id}", platform.id.value))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(platform.id.value))
            .andExpect(jsonPath("$.name").value("BlueArchive"))
            .andExpect(jsonPath("$.normalizedName").value(nullValue()))
            .andExpect(jsonPath("$.origin").value("PLATFORM"))
            .andExpect(jsonPath("$.sourceTagId").value(nullValue()))
            .andExpect(jsonPath("$.instanceId").value(nullValue()))
            .andExpect(jsonPath("$.sourceId").value(nullValue()))
    }

    // ------------------------------------------------------------------
    // Detail: not found -> 404
    // ------------------------------------------------------------------

    @Test
    fun getTag_whenAbsent_returns404() {
        Mockito.`when`(getTagByIdUseCase.getById(PublicId("tg_missing"))).thenReturn(null)

        mockMvc.perform(get("/api/v1/tags/{id}", "tg_missing"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // Tag search (Stage 15-C): q validation
    // ------------------------------------------------------------------

    private fun neverSearchTags() {
        Mockito.verify(searchTagsUseCase, Mockito.never()).search(Mockito.anyString(), Mockito.anyInt())
    }

    @Test
    fun searchTags_whenQueryMissing_returns400() {
        mockMvc.perform(get("/api/v1/tags"))
            .andExpect(status().isBadRequest)
        neverSearchTags()
    }

    @Test
    fun searchTags_whenQueryEmpty_returns400() {
        mockMvc.perform(get("/api/v1/tags").param("q", ""))
            .andExpect(status().isBadRequest)
        neverSearchTags()
    }

    @Test
    fun searchTags_whenQueryWhitespace_returns400() {
        mockMvc.perform(get("/api/v1/tags").param("q", "   "))
            .andExpect(status().isBadRequest)
        neverSearchTags()
    }

    @Test
    fun searchTags_whenQueryWhitespaceAndLimitZero_returns400() {
        mockMvc.perform(get("/api/v1/tags").param("q", "  ").param("limit", "0"))
            .andExpect(status().isBadRequest)
        neverSearchTags()
    }

    // ------------------------------------------------------------------
    // Tag search: valid q, limit semantics
    // ------------------------------------------------------------------

    @Test
    fun searchTags_whenValidQuery_returnsFlatMappedList() {
        val first = platformTag("tg_1", "Blue Archive", "bluearchive")
        val second = platformTag("tg_2", "BA Fanart", "bluearchive-fanart")
        Mockito.`when`(searchTagsUseCase.search("blue", 20)).thenReturn(listOf(first, second))

        mockMvc.perform(get("/api/v1/tags").param("q", "blue"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasSize<Any>(2)))
            .andExpect(jsonPath("$[0].id").value(first.id.value))
            .andExpect(jsonPath("$[0].name").value("Blue Archive"))
            .andExpect(jsonPath("$[0].origin").value("PLATFORM"))
            .andExpect(jsonPath("$[1].id").value(second.id.value))

        Mockito.verify(searchTagsUseCase).search("blue", 20)
    }

    @Test
    fun searchTags_whenQueryHasSurroundingWhitespace_trimsBeforeSearch() {
        Mockito.`when`(searchTagsUseCase.search("blue", 20)).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/tags").param("q", "   blue   "))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(searchTagsUseCase).search("blue", 20)
    }

    @Test
    fun searchTags_whenDefaultLimit_uses20() {
        Mockito.`when`(searchTagsUseCase.search("blue", 20)).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/tags").param("q", "blue"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(searchTagsUseCase).search("blue", 20)
    }

    @Test
    fun searchTags_whenLimitMax_returns200() {
        Mockito.`when`(searchTagsUseCase.search("blue", 100)).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/tags").param("q", "blue").param("limit", "100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(searchTagsUseCase).search("blue", 100)
    }

    @Test
    fun searchTags_whenNoResults_returns200EmptyArray() {
        Mockito.`when`(searchTagsUseCase.search("no-such-tag", 20)).thenReturn(emptyList())

        mockMvc.perform(get("/api/v1/tags").param("q", "no-such-tag"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
    }

    @Test
    fun searchTags_whenLimitZero_returns200EmptyArrayWithoutInvokingUseCase() {
        mockMvc.perform(get("/api/v1/tags").param("q", "blue").param("limit", "0"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
        neverSearchTags()
    }

    @Test
    fun searchTags_whenLimitAboveMax_returns400() {
        mockMvc.perform(get("/api/v1/tags").param("q", "blue").param("limit", "101"))
            .andExpect(status().isBadRequest)
        neverSearchTags()
    }

    @Test
    fun searchTags_whenLimitNegative_returns400() {
        mockMvc.perform(get("/api/v1/tags").param("q", "blue").param("limit", "-1"))
            .andExpect(status().isBadRequest)
        neverSearchTags()
    }

    @Test
    fun searchTags_whenLimitNotAnInteger_returns400() {
        mockMvc.perform(get("/api/v1/tags").param("q", "blue").param("limit", "abc"))
            .andExpect(status().isBadRequest)
        neverSearchTags()
    }

    // ------------------------------------------------------------------
    // Tag -> Content (Stage 15-C): existence + page semantics
    // ------------------------------------------------------------------

    private fun neverContentByTag() {
        Mockito.verifyNoInteractions(getContentByTagUseCase)
    }

    @Test
    fun contentByTag_whenTagExists_returnsFlatMappedContentList() {
        val tagId = PublicId("tg_anime")
        val first = content("sc-1")
        Mockito.`when`(getContentByTagUseCase.getByTag(tagId, null, 20))
            .thenReturn(page(listOf(first)))

        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_anime"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].id").value(first.id.value))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-1"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        Mockito.verify(getContentByTagUseCase).getByTag(tagId, null, 20)
    }

    @Test
    fun contentByTag_whenTagUnknown_returns404() {
        val tagId = PublicId("tg_does_not_exist")
        Mockito.`when`(getContentByTagUseCase.getByTag(tagId, null, 20)).thenReturn(null)

        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_does_not_exist"))
            .andExpect(status().isNotFound)

        Mockito.verify(getContentByTagUseCase).getByTag(tagId, null, 20)
    }

    @Test
    fun contentByTag_whenTagExistsButNoMatchingContent_returns200EmptyArray() {
        val tagId = PublicId("tg_anime")
        Mockito.`when`(getContentByTagUseCase.getByTag(tagId, null, 20))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_anime"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))
    }

    // ------------------------------------------------------------------
    // Tag -> Content: cursor / Link / limit
    // ------------------------------------------------------------------

    @Test
    fun contentByTag_withValidCursor_decodesAndForwardsCursor() {
        val tagId = PublicId("tg_anime")
        val cursor = ContentCursor(createdAt = 5_000L, id = PublicId("ct_1"))
        val raw = ContentCursorCodec.encode(cursor)
        Mockito.`when`(getContentByTagUseCase.getByTag(tagId, cursor, 20))
            .thenReturn(page(listOf(content("sc-1"))))

        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_anime").param("cursor", raw))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))

        Mockito.verify(getContentByTagUseCase).getByTag(tagId, cursor, 20)
    }

    @Test
    fun contentByTag_whenCursorBlank_returns400() {
        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_anime").param("cursor", "  "))
            .andExpect(status().isBadRequest)
        neverContentByTag()
    }

    @Test
    fun contentByTag_whenCursorMalformedBase64_returns400() {
        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_anime").param("cursor", "!!!"))
            .andExpect(status().isBadRequest)
        neverContentByTag()
    }

    @Test
    fun contentByTag_whenCursorMalformedStructure_returns400() {
        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_anime").param("cursor", b64("v2:1:ct_x")))
            .andExpect(status().isBadRequest)
        neverContentByTag()
    }

    @Test
    fun contentByTag_whenLimitZero_returns200EmptyArrayWithTagLookupOnly() {
        val tagId = PublicId("tg_anime")
        Mockito.`when`(getContentByTagUseCase.getByTag(tagId, null, 0))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_anime").param("limit", "0"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        // The content page query must not run at limit=0: the repository
        // short-circuits it; the use case is still consulted once for the tag
        // existence lookup (404 semantics preserved).
        Mockito.verify(getContentByTagUseCase).getByTag(tagId, null, 0)
    }

    @Test
    fun contentByTag_whenLimitInvalid_returns400() {
        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_anime").param("limit", "101"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_anime").param("limit", "-1"))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_anime").param("limit", "abc"))
            .andExpect(status().isBadRequest)
        neverContentByTag()
    }

    @Test
    fun contentByTag_whenTagIdBlank_returns400() {
        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "   "))
            .andExpect(status().isBadRequest)
    }

    // ------------------------------------------------------------------
    // Tag -> Content: Link header when a next page exists
    // ------------------------------------------------------------------

    @Test
    fun contentByTag_whenNextPageExists_returnsLinkHeaderWithCurrentLimitAndNewCursor() {
        val tagId = PublicId("tg_anime")
        val nextCursor = ContentCursor(createdAt = 7_000L, id = PublicId("ct_last"))
        Mockito.`when`(getContentByTagUseCase.getByTag(tagId, null, 20))
            .thenReturn(page(listOf(content("sc-last")), nextCursor))

        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_anime"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.LINK, containsString("</api/v1/tags/tg_anime/contents?")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("limit=20")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("cursor=")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("rel=\"next\"")))

        Mockito.verify(getContentByTagUseCase).getByTag(tagId, null, 20)
    }

    @Test
    fun contentByTag_whenNoNextPage_returnsNoLinkHeader() {
        val tagId = PublicId("tg_anime")
        Mockito.`when`(getContentByTagUseCase.getByTag(tagId, null, 20))
            .thenReturn(page(listOf(content("sc-1")), null))

        mockMvc.perform(get("/api/v1/tags/{tagId}/contents", "tg_anime"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))
    }
}
