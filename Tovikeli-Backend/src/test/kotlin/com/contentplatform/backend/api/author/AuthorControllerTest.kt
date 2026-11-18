package com.contentplatform.backend.api.author

import com.contentplatform.backend.application.author.AuthorContentListingUseCase
import com.contentplatform.backend.application.author.GetAuthorByIdUseCase
import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import org.hamcrest.Matchers.containsString
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
 * MVC slice test for [AuthorController].
 *
 * The controller under test is registered because the slice property enables
 * its datasource-conditional wiring. The Stage 9 use case and the Stage 22-A
 * content listing use case are mocked; no database, no Flyway and no external
 * HTTP is involved.
 */
@WebMvcTest(controllers = [AuthorController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class AuthorControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var getAuthorByIdUseCase: GetAuthorByIdUseCase

    @MockitoBean
    private lateinit var authorContentListingUseCase: AuthorContentListingUseCase

    @MockitoBean
    private lateinit var getLocalAuthorByUserIdUseCase: com.contentplatform.backend.application.author.GetLocalAuthorByUserIdUseCase

    @MockitoBean
    private lateinit var userRepository: UserRepository

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private fun content(id: String, authorIdValue: String = "au_author_1"): Content =
        Content.createFromAdapter(
            instanceIdValue = "inst_misskey_io",
            sourceContentId = "sc-$id",
            authorId = PublicId(authorIdValue),
            sourceId = PublicId("src_misskey"),
            text = "content $id",
            language = "en",
            visibility = ContentVisibility.PUBLIC,
            mediaIds = emptyList(),
            parentId = null,
            repostOfId = null,
            quoteOfId = null,
            externalCreatedAt = null,
            metadata = emptyMap()
        )

    // ==================================================================
    // GET /api/v1/authors/{id}  (Stage 9 — unchanged)
    // ==================================================================

    @Test
    fun getAuthor_whenFound_returns200WithResponseDto() {
        val author = Author.createFromAdapter(
            instanceId = PublicId("inst_misskey_io"),
            sourceAuthorId = "ext-42",
            name = "Alice",
            username = "alice",
            displayName = "Alice Example",
            url = "https://misskey.io/@alice",
            avatar = "https://cdn.example/alice.png",
            bio = "hello",
            status = null
        )
        Mockito.`when`(getAuthorByIdUseCase.getById(author.id)).thenReturn(author)

        mockMvc.perform(get("/api/v1/authors/{id}", author.id.value))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(author.id.value))
            .andExpect(jsonPath("$.instanceId").value("inst_misskey_io"))
            .andExpect(jsonPath("$.sourceAuthorId").value("ext-42"))
            .andExpect(jsonPath("$.name").value("Alice"))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.displayName").value("Alice Example"))
            .andExpect(jsonPath("$.url").value("https://misskey.io/@alice"))
            .andExpect(jsonPath("$.avatar").value("https://cdn.example/alice.png"))
            .andExpect(jsonPath("$.bio").value("hello"))
            .andExpect(jsonPath("$.status").value(nullValue()))
    }

    @Test
    fun getAuthor_whenAbsent_returns404() {
        Mockito.`when`(getAuthorByIdUseCase.getById(PublicId("au_missing"))).thenReturn(null)

        mockMvc.perform(get("/api/v1/authors/{id}", "au_missing"))
            .andExpect(status().isNotFound)
    }

    // ==================================================================
    // GET /api/v1/authors/{authorId}/contents  (Stage 22-B)
    // ==================================================================

    // ------------------------------------------------------------------
    // Test 1 — basic author content listing, default limit
    // ------------------------------------------------------------------

    @Test
    fun getAuthorContents_basicListing_returns200() {
        val authorId = "au_author_1"
        val c1 = content("ct_1", authorId)
        val c2 = content("ct_2", authorId)
        Mockito.`when`(authorContentListingUseCase.list(
            PublicId(authorId), null, 20
        )).thenReturn(ContentBrowsePage(listOf(c1, c2), null))

        mockMvc.perform(get("/api/v1/authors/{authorId}/contents", authorId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(c1.id.value))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-ct_1"))
            .andExpect(jsonPath("$[1].id").value(c2.id.value))
            .andExpect(jsonPath("$[1].sourceContentId").value("sc-ct_2"))

        Mockito.verify(authorContentListingUseCase).list(PublicId(authorId), null, 20)
    }

    // ------------------------------------------------------------------
    // Test 2 — explicit limit
    // ------------------------------------------------------------------

    @Test
    fun getAuthorContents_explicitLimitForwardsToUseCase() {
        val authorId = "au_author_1"
        val c = content("ct_1", authorId)
        Mockito.`when`(authorContentListingUseCase.list(
            PublicId(authorId), null, 50
        )).thenReturn(ContentBrowsePage(listOf(c), null))

        mockMvc.perform(
            get("/api/v1/authors/{authorId}/contents", authorId)
                .param("limit", "50")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-ct_1"))

        Mockito.verify(authorContentListingUseCase).list(PublicId(authorId), null, 50)
    }

    // ------------------------------------------------------------------
    // Test 3 — limit=0 returns 200 [] without invoking use case
    // ------------------------------------------------------------------

    @Test
    fun getAuthorContents_limitZero_returns200Empty() {
        mockMvc.perform(
            get("/api/v1/authors/{authorId}/contents", "au_author_1")
                .param("limit", "0")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))

        Mockito.verifyNoInteractions(authorContentListingUseCase)
    }

    // ------------------------------------------------------------------
    // Test 4 — invalid limits
    // ------------------------------------------------------------------

    @Test
    fun getAuthorContents_negativeLimit_returns400() {
        mockMvc.perform(
            get("/api/v1/authors/{authorId}/contents", "au_author_1")
                .param("limit", "-1")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(authorContentListingUseCase)
    }

    @Test
    fun getAuthorContents_limitExceedsMax_returns400() {
        mockMvc.perform(
            get("/api/v1/authors/{authorId}/contents", "au_author_1")
                .param("limit", "101")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(authorContentListingUseCase)
    }

    // ------------------------------------------------------------------
    // Test 5 — blank authorId
    // ------------------------------------------------------------------

    @Test
    fun getAuthorContents_blankAuthorId_returns400() {
        mockMvc.perform(
            get("/api/v1/authors/{authorId}/contents", "   ")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(authorContentListingUseCase)
    }

    // ------------------------------------------------------------------
    // Test 6 — invalid cursor
    // ------------------------------------------------------------------

    @Test
    fun getAuthorContents_invalidCursor_returns400() {
        mockMvc.perform(
            get("/api/v1/authors/{authorId}/contents", "au_author_1")
                .param("cursor", "not-a-valid-cursor")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(authorContentListingUseCase)
    }

    // ------------------------------------------------------------------
    // Test 7 — valid cursor passed through
    // ------------------------------------------------------------------

    @Test
    fun getAuthorContents_validCursor_decodedAndForwarded() {
        val authorId = "au_author_1"
        val cursor = ContentCursor(createdAt = 1_757_000_000_123L, id = PublicId("ct_cursor"))
        Mockito.`when`(authorContentListingUseCase.list(
            PublicId(authorId), cursor, 20
        )).thenReturn(ContentBrowsePage(emptyList(), null))

        mockMvc.perform(
            get("/api/v1/authors/{authorId}/contents", authorId)
                .param("cursor", com.contentplatform.backend.api.content.ContentCursorCodec.encode(cursor))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))

        Mockito.verify(authorContentListingUseCase).list(PublicId(authorId), cursor, 20)
    }

    // ------------------------------------------------------------------
    // Test 8 — empty result
    // ------------------------------------------------------------------

    @Test
    fun getAuthorContents_emptyResult_returns200EmptyNoLink() {
        val authorId = "au_unknown"
        Mockito.`when`(authorContentListingUseCase.list(
            PublicId(authorId), null, 20
        )).thenReturn(ContentBrowsePage(emptyList(), null))

        mockMvc.perform(get("/api/v1/authors/{authorId}/contents", authorId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
            .andExpect(header().doesNotExist("Link"))
    }

    // ------------------------------------------------------------------
    // Test 9 — next page Link header
    // ------------------------------------------------------------------

    @Test
    fun getAuthorContents_nextPage_returnsLinkHeader() {
        val authorId = "au_author_1"
        val c = content("ct_1", authorId)
        val nextCursor = ContentCursor(createdAt = 1_757_000_000_999L, id = c.id)
        Mockito.`when`(authorContentListingUseCase.list(
            PublicId(authorId), null, 20
        )).thenReturn(ContentBrowsePage(listOf(c), nextCursor))

        mockMvc.perform(get("/api/v1/authors/{authorId}/contents", authorId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(header().string(HttpHeaders.LINK, containsString("/api/v1/authors/$authorId/contents")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("limit=20")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("cursor=")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("rel=\"next\"")))
    }

    // ------------------------------------------------------------------
    // Test 10 — last page (no nextCursor)
    // ------------------------------------------------------------------

    @Test
    fun getAuthorContents_lastPage_noLinkHeader() {
        val authorId = "au_author_1"
        val c = content("ct_1", authorId)
        Mockito.`when`(authorContentListingUseCase.list(
            PublicId(authorId), null, 20
        )).thenReturn(ContentBrowsePage(listOf(c), null))

        mockMvc.perform(get("/api/v1/authors/{authorId}/contents", authorId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(header().doesNotExist("Link"))
    }

    // ------------------------------------------------------------------
    // Test 11 — unknown author returns 200 [] (no 404)
    // ------------------------------------------------------------------

    @Test
    fun getAuthorContents_unknownAuthor_returns200Empty() {
        val authorId = "au_nonexistent"
        Mockito.`when`(authorContentListingUseCase.list(
            PublicId(authorId), null, 20
        )).thenReturn(ContentBrowsePage(emptyList(), null))

        mockMvc.perform(get("/api/v1/authors/{authorId}/contents", authorId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))

        Mockito.verify(authorContentListingUseCase).list(PublicId(authorId), null, 20)
    }
}
