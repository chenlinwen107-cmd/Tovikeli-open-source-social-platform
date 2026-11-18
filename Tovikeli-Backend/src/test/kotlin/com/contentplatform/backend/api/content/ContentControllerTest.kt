package com.contentplatform.backend.api.content

import com.contentplatform.backend.api.authentication.SessionAttributes
import com.contentplatform.backend.application.content.BrowseContentUseCase
import com.contentplatform.backend.application.content.DeleteContentUseCase
import com.contentplatform.backend.application.content.GetContentDetailUseCase
import com.contentplatform.backend.application.content.HideContentUseCase
import com.contentplatform.backend.application.content.RestoreContentUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.UserRepository
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentDetail
import com.contentplatform.backend.domain.content.ContentLifecycleResult
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.interaction.InteractionStatistics
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import java.util.Base64
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MVC slice test for [ContentController].
 *
 * The controller under test is registered because the slice property enables
 * its datasource-conditional wiring. The browse/detail use cases are mocked;
 * no database, no Flyway and no external HTTP is involved.
 *
 * Covered endpoints:
 *   GET /api/v1/contents        browse (limit, instanceId, authorId, hasMedia,
 *                               and Stage 14 cursor + Link header)
 *   GET /api/v1/contents/{id}   detail with commentCount + interactionStatistics (Stage 25)
 */
@WebMvcTest(controllers = [ContentController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class ContentControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var browseContentUseCase: BrowseContentUseCase

    @MockitoBean
    private lateinit var getContentDetailUseCase: GetContentDetailUseCase

    @MockitoBean
    private lateinit var getContentMediaUseCase: com.contentplatform.backend.application.content.GetContentMediaUseCase

    @MockitoBean
    private lateinit var getContentRelationshipsUseCase: com.contentplatform.backend.application.content.GetContentRelationshipsUseCase

    @MockitoBean
    private lateinit var deleteContentUseCase: DeleteContentUseCase

    @MockitoBean
    private lateinit var hideContentUseCase: HideContentUseCase

    @MockitoBean
    private lateinit var restoreContentUseCase: RestoreContentUseCase

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

    @BeforeEach
    fun setUp() {
        val activeUser = User(
            id = PublicId("usr_testuser"),
            username = "testuser",
            displayName = "Test User",
            avatar = null,

            bio = null,

            gender = "secret",

            location = null,

            cover = null,

            status = UserStatus.ACTIVE,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        Mockito.`when`(userRepository.findById(PublicId("usr_testuser"))).thenReturn(activeUser)
    }

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private fun browsableContent(sourceContentId: String, text: String = "browse $sourceContentId"): Content =
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

    private fun page(contents: List<Content>, nextCursor: ContentCursor? = null): ContentBrowsePage =
        ContentBrowsePage(contents = contents, nextCursor = nextCursor)

    /** Encodes an arbitrary logical string the way a malformed cursor would. */
    private fun b64(logical: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(logical.toByteArray())

    private fun neverBrowse() {
        Mockito.verifyNoInteractions(browseContentUseCase)
    }

    // ------------------------------------------------------------------
    // First page: no cursor -> Stage 13 behaviour with flat array body
    // ------------------------------------------------------------------

    @Test
    fun browseContent_whenDefaultLimit_returns200WithFlatMappedArray() {
        val first = browsableContent("sc-1", "browse one")
        val second = browsableContent("sc-2", "browse two")
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(listOf(first, second)))

        mockMvc.perform(get("/api/v1/contents"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasSize<Any>(2)))
            .andExpect(jsonPath("$[0].id").value(first.id.value))
            .andExpect(jsonPath("$[0].instanceId").value("inst_misskey_io"))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-1"))
            .andExpect(jsonPath("$[0].text").value("browse one"))
            .andExpect(jsonPath("$[0].visibility").value("PUBLIC"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[1].id").value(second.id.value))
            .andExpect(jsonPath("$[1].sourceContentId").value("sc-2"))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        Mockito.verify(browseContentUseCase).browse(null, null, null, null, 20)
    }

    @Test
    fun browseContent_whenExplicitValidLimit_forwardsItToUseCase() {
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 5))
            .thenReturn(page(listOf(browsableContent("sc-1"))))

        mockMvc.perform(get("/api/v1/contents").param("limit", "5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-1"))

        Mockito.verify(browseContentUseCase).browse(null, null, null, null, 5)
    }

    @Test
    fun browseContent_whenLimitIsMax_returns200() {
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 100))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/contents").param("limit", "100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        Mockito.verify(browseContentUseCase).browse(null, null, null, null, 100)
    }

    // ------------------------------------------------------------------
    // limit=0: 200 [] without invoking the repository-bound use case
    // ------------------------------------------------------------------

    @Test
    fun browseContent_whenLimitZero_returns200EmptyArrayWithoutInvokingUseCase() {
        mockMvc.perform(get("/api/v1/contents").param("limit", "0"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        neverBrowse()
    }

    @Test
    fun browseContent_whenLimitZeroWithValidCursor_returns200EmptyArrayWithoutInvokingUseCase() {
        val valid = ContentCursorCodec.encode(ContentCursor(createdAt = 123L, id = PublicId("ct_1")))

        mockMvc.perform(get("/api/v1/contents").param("limit", "0").param("cursor", valid))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        neverBrowse()
    }

    // ------------------------------------------------------------------
    // Empty browse page -> 200 [] and no Link
    // ------------------------------------------------------------------

    @Test
    fun browseContent_whenNothingBrowsable_returns200EmptyArray() {
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/contents"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))
    }

    // ------------------------------------------------------------------
    // Invalid limit -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun browseContent_whenLimitNegative_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("limit", "-1"))
            .andExpect(status().isBadRequest)
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        neverBrowse()
    }

    @Test
    fun browseContent_whenLimitAboveMax_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("limit", "101"))
            .andExpect(status().isBadRequest)
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        neverBrowse()
    }

    @Test
    fun browseContent_whenLimitNotAnInteger_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("limit", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        neverBrowse()
    }

    // ------------------------------------------------------------------
    // instanceId (Stage 13-A)
    // ------------------------------------------------------------------

    @Test
    fun browseContent_whenInstanceIdProvided_forwardsTypedFilterToUseCase() {
        val instanceId = PublicId("inst_misskey_io")
        Mockito.`when`(browseContentUseCase.browse(instanceId, null, null, null, 5))
            .thenReturn(page(listOf(browsableContent("sc-1"))))

        mockMvc.perform(get("/api/v1/contents").param("limit", "5").param("instanceId", "inst_misskey_io"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-1"))

        Mockito.verify(browseContentUseCase).browse(instanceId, null, null, null, 5)
    }

    @Test
    fun browseContent_whenInstanceIdUnknown_returns200EmptyArray() {
        val unknown = PublicId("inst_does_not_exist")
        Mockito.`when`(browseContentUseCase.browse(unknown, null, null, null, 20))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/contents").param("instanceId", "inst_does_not_exist"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        Mockito.verify(browseContentUseCase).browse(unknown, null, null, null, 20)
    }

    @Test
    fun browseContent_whenInstanceIdEmpty_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("instanceId", ""))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenInstanceIdWhitespace_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("instanceId", "   "))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    // ------------------------------------------------------------------
    // authorId (Stage 13-B)
    // ------------------------------------------------------------------

    @Test
    fun browseContent_whenAuthorIdProvided_forwardsTypedFilterToUseCase() {
        val authorId = PublicId("au_author_1")
        Mockito.`when`(browseContentUseCase.browse(null, authorId, null, null, 5))
            .thenReturn(page(listOf(browsableContent("sc-1"))))

        mockMvc.perform(get("/api/v1/contents").param("limit", "5").param("authorId", "au_author_1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-1"))

        Mockito.verify(browseContentUseCase).browse(null, authorId, null, null, 5)
    }

    @Test
    fun browseContent_whenAuthorIdUnknown_returns200EmptyArray() {
        val unknown = PublicId("au_does_not_exist")
        Mockito.`when`(browseContentUseCase.browse(null, unknown, null, null, 20))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/contents").param("authorId", "au_does_not_exist"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        Mockito.verify(browseContentUseCase).browse(null, unknown, null, null, 20)
    }

    @Test
    fun browseContent_whenAuthorIdEmpty_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("authorId", ""))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenAuthorIdWhitespace_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("authorId", "   "))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    // ------------------------------------------------------------------
    // hasMedia (Stage 13-C)
    // ------------------------------------------------------------------

    @Test
    fun browseContent_whenHasMediaTrue_forwardsTrueToUseCase() {
        Mockito.`when`(browseContentUseCase.browse(null, null, true, null, 5))
            .thenReturn(page(listOf(browsableContent("sc-1"))))

        mockMvc.perform(get("/api/v1/contents").param("limit", "5").param("hasMedia", "true"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))

        Mockito.verify(browseContentUseCase).browse(null, null, true, null, 5)
    }

    @Test
    fun browseContent_whenHasMediaFalse_forwardsFalseToUseCase() {
        Mockito.`when`(browseContentUseCase.browse(null, null, false, null, 5))
            .thenReturn(page(listOf(browsableContent("sc-1"))))

        mockMvc.perform(get("/api/v1/contents").param("limit", "5").param("hasMedia", "false"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))

        Mockito.verify(browseContentUseCase).browse(null, null, false, null, 5)
    }

    @Test
    fun browseContent_whenHasMediaEmpty_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("hasMedia", ""))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenHasMediaWhitespace_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("hasMedia", "   "))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenHasMediaInvalidLiteral_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("hasMedia", "yes"))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    // ------------------------------------------------------------------
    // Cursor (Stage 14): valid cursor is decoded and forwarded
    // ------------------------------------------------------------------

    @Test
    fun browseContent_whenCursorProvided_decodesAndForwardsCursorToUseCase() {
        val cursor = ContentCursor(createdAt = 1_757_000_000_123L, id = PublicId("ct_1"))
        val raw = ContentCursorCodec.encode(cursor)
        Mockito.`when`(browseContentUseCase.browse(null, null, null, cursor, 5))
            .thenReturn(page(listOf(browsableContent("sc-1"))))

        mockMvc.perform(get("/api/v1/contents").param("limit", "5").param("cursor", raw))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))

        Mockito.verify(browseContentUseCase).browse(null, null, null, cursor, 5)
    }

    // ------------------------------------------------------------------
    // Cursor (Stage 14): invalid cursors -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun browseContent_whenCursorEmpty_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("cursor", ""))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenCursorWhitespace_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("cursor", "   "))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenCursorMalformedBase64_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("cursor", "!!!"))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenCursorMissingVersion_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("cursor", b64("123:ct_1")))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenCursorInvalidVersion_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("cursor", b64("v2:123:ct_1")))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenCursorMalformedFieldCount_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("cursor", b64("v1:123:ct_1:extra")))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenCursorInvalidCreatedAt_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("cursor", b64("v1:abc:ct_1")))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenCursorNegativeCreatedAt_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("cursor", b64("v1:-5:ct_1")))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenCursorMissingContentId_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("cursor", b64("v1:123:")))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenCursorBlankContentId_returns400() {
        mockMvc.perform(get("/api/v1/contents").param("cursor", b64("v1:123:   ")))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    @Test
    fun browseContent_whenCursorInvalidWithLimitZero_stillReturns400() {
        mockMvc.perform(get("/api/v1/contents").param("limit", "0").param("cursor", "!!!"))
            .andExpect(status().isBadRequest)

        neverBrowse()
    }

    // ------------------------------------------------------------------
    // Valid cursor, no matching rows -> 200 [] (not 400)
    // ------------------------------------------------------------------

    @Test
    fun browseContent_whenValidCursorButNoMatchingRows_returns200EmptyArray() {
        val cursor = ContentCursor(createdAt = 999L, id = PublicId("ct_none"))
        val raw = ContentCursorCodec.encode(cursor)
        Mockito.`when`(browseContentUseCase.browse(null, null, null, cursor, 20))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/contents").param("cursor", raw))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        Mockito.verify(browseContentUseCase).browse(null, null, null, cursor, 20)
    }

    // ------------------------------------------------------------------
    // Cursor + filters combined
    // ------------------------------------------------------------------

    @Test
    fun browseContent_whenInstanceAuthorHasMediaAndCursorProvided_forwardsAllToUseCase() {
        val instanceId = PublicId("inst_misskey_io")
        val authorId = PublicId("au_author_1")
        val cursor = ContentCursor(createdAt = 5_000L, id = PublicId("ct_1"))
        val raw = ContentCursorCodec.encode(cursor)
        Mockito.`when`(browseContentUseCase.browse(instanceId, authorId, true, cursor, 5))
            .thenReturn(page(listOf(browsableContent("sc-1"))))

        mockMvc.perform(
            get("/api/v1/contents")
                .param("limit", "5")
                .param("instanceId", "inst_misskey_io")
                .param("authorId", "au_author_1")
                .param("hasMedia", "true")
                .param("cursor", raw)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-1"))

        Mockito.verify(browseContentUseCase).browse(instanceId, authorId, true, cursor, 5)
    }

    // ------------------------------------------------------------------
    // Link header: sent only when a next page exists
    // ------------------------------------------------------------------

    @Test
    fun browseContent_whenNextPageExists_returnsLinkHeaderWithNextCursorAndCurrentParams() {
        val last = browsableContent("sc-last")
        val nextCursor = ContentCursor(createdAt = 1_757_000_000_123L, id = PublicId("ct_last"))
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 5))
            .thenReturn(page(listOf(last), nextCursor))

        mockMvc.perform(get("/api/v1/contents").param("limit", "5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-last"))
            .andExpect(header().string(HttpHeaders.LINK, containsString("</api/v1/contents?")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("rel=\"next\"")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("limit=5")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("cursor=")))

        Mockito.verify(browseContentUseCase).browse(null, null, null, null, 5)
    }

    @Test
    fun browseContent_whenNextPageExists_linkPreservesCurrentFilters() {
        val instanceId = "inst_misskey_io"
        val authorId = "au_author_1"
        val nextCursor = ContentCursor(createdAt = 7L, id = PublicId("ct_last"))
        Mockito.`when`(
            browseContentUseCase.browse(
                PublicId(instanceId), PublicId(authorId), true, null, 20
            )
        ).thenReturn(page(listOf(browsableContent("sc-last")), nextCursor))

        mockMvc.perform(
            get("/api/v1/contents")
                .param("instanceId", instanceId)
                .param("authorId", authorId)
                .param("hasMedia", "true")
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.LINK, containsString("instanceId=inst_misskey_io")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("authorId=au_author_1")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("hasMedia=true")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("cursor=")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("limit=20")))
    }

    @Test
    fun browseContent_whenNoNextPage_returnsNoLinkHeader() {
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(listOf(browsableContent("sc-1")), null))

        mockMvc.perform(get("/api/v1/contents"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))
    }

    // ------------------------------------------------------------------
    // Detail (Stage 25: with statistics)
    // ------------------------------------------------------------------

    @Test
    fun getContent_whenFound_returns200WithStatistics() {
        val content = Content.createFromAdapter(
            instanceIdValue = "inst_misskey_io",
            sourceContentId = "sc-1",
            authorId = PublicId("au_author_1"),
            sourceId = PublicId("src_misskey"),
            text = "hello from the api",
            language = "en",
            visibility = ContentVisibility.PUBLIC,
            mediaIds = listOf(PublicId("media-ref-1"), PublicId("media-ref-2")),
            parentId = null,
            repostOfId = null,
            quoteOfId = null,
            externalCreatedAt = 1_600_000_000_000L,
            metadata = emptyMap()
        )
        val detail = ContentDetail(
            content = content,
            commentCount = 5L,
            interactionStatistics = InteractionStatistics.fromCounts(
                mapOf(
                    com.contentplatform.backend.domain.interaction.InteractionType.LIKE to 10L,
                    com.contentplatform.backend.domain.interaction.InteractionType.BOOKMARK to 2L
                )
            )
        )
        Mockito.`when`(getContentDetailUseCase.getById(content.id)).thenReturn(detail)

        mockMvc.perform(get("/api/v1/contents/{id}", content.id.value))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(content.id.value))
            .andExpect(jsonPath("$.instanceId").value("inst_misskey_io"))
            .andExpect(jsonPath("$.sourceContentId").value("sc-1"))
            .andExpect(jsonPath("$.authorId").value("au_author_1"))
            .andExpect(jsonPath("$.sourceId").value("src_misskey"))
            .andExpect(jsonPath("$.text").value("hello from the api"))
            .andExpect(jsonPath("$.language").value("en"))
            .andExpect(jsonPath("$.visibility").value("PUBLIC"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.mediaIds", hasSize<Any>(2)))
            .andExpect(jsonPath("$.mediaIds[0]").value("media-ref-1"))
            .andExpect(jsonPath("$.mediaIds[1]").value("media-ref-2"))
            .andExpect(jsonPath("$.parentId").value(nullValue()))
            .andExpect(jsonPath("$.repostOfId").value(nullValue()))
            .andExpect(jsonPath("$.quoteOfId").value(nullValue()))
            .andExpect(jsonPath("$.externalCreatedAt").value(1_600_000_000_000L))
            .andExpect(jsonPath("$.commentCount").value(5))
            .andExpect(jsonPath("$.interactionStatistics.total").value(12))
            .andExpect(jsonPath("$.interactionStatistics.like").value(10))
            .andExpect(jsonPath("$.interactionStatistics.favorite").value(0))
            .andExpect(jsonPath("$.interactionStatistics.repost").value(0))
            .andExpect(jsonPath("$.interactionStatistics.bookmark").value(2))
    }

    @Test
    fun getContent_whenFound_zeroStatistics_returnsZeros() {
        val content = Content.createFromAdapter(
            instanceIdValue = "inst_misskey_io",
            sourceContentId = "sc-1",
            authorId = PublicId("au_author_1"),
            sourceId = PublicId("src_misskey"),
            text = "text",
            language = null,
            visibility = ContentVisibility.PUBLIC,
            mediaIds = emptyList(),
            parentId = null,
            repostOfId = null,
            quoteOfId = null,
            externalCreatedAt = null,
            metadata = emptyMap()
        )
        val detail = ContentDetail(
            content = content,
            commentCount = 0L,
            interactionStatistics = InteractionStatistics.ZERO
        )
        Mockito.`when`(getContentDetailUseCase.getById(content.id)).thenReturn(detail)

        mockMvc.perform(get("/api/v1/contents/{id}", content.id.value))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.commentCount").value(0))
            .andExpect(jsonPath("$.interactionStatistics.total").value(0))
            .andExpect(jsonPath("$.interactionStatistics.like").value(0))
            .andExpect(jsonPath("$.interactionStatistics.favorite").value(0))
            .andExpect(jsonPath("$.interactionStatistics.repost").value(0))
            .andExpect(jsonPath("$.interactionStatistics.bookmark").value(0))
    }

    @Test
    fun getContent_whenAbsent_returns404() {
        Mockito.`when`(getContentDetailUseCase.getById(PublicId("ct_missing"))).thenReturn(null)

        mockMvc.perform(get("/api/v1/contents/{id}", "ct_missing"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // Lifecycle: DELETE /api/v1/contents/{contentId}/delete
    // ------------------------------------------------------------------

    @Test
    fun deleteContent_whenSuccess_returns204() {
        val id = PublicId("ct_123")
        Mockito.`when`(deleteContentUseCase.delete(id, PublicId("usr_testuser"))).thenReturn(ContentLifecycleResult.SUCCESS)

        mockMvc.perform(post("/api/v1/contents/{contentId}/delete", "ct_123").sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_testuser"))
            .andExpect(status().isNoContent)

        Mockito.verify(deleteContentUseCase).delete(id, PublicId("usr_testuser"))
    }

    @Test
    fun deleteContent_whenNotFound_returns404() {
        val id = PublicId("ct_missing")
        Mockito.`when`(deleteContentUseCase.delete(id, PublicId("usr_testuser"))).thenReturn(ContentLifecycleResult.NOT_FOUND)

        mockMvc.perform(post("/api/v1/contents/{contentId}/delete", "ct_missing").sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_testuser"))
            .andExpect(status().isNotFound)

        Mockito.verify(deleteContentUseCase).delete(id, PublicId("usr_testuser"))
    }

    @Test
    fun deleteContent_whenInvalidTransition_returns400() {
        val id = PublicId("ct_123")
        Mockito.`when`(deleteContentUseCase.delete(id, PublicId("usr_testuser"))).thenReturn(ContentLifecycleResult.INVALID_TRANSITION)

        mockMvc.perform(post("/api/v1/contents/{contentId}/delete", "ct_123").sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_testuser"))
            .andExpect(status().isBadRequest)

        Mockito.verify(deleteContentUseCase).delete(id, PublicId("usr_testuser"))
    }

    @Test
    fun deleteContent_whenBlankId_returns401() {
        mockMvc.perform(post("/api/v1/contents/{contentId}/delete", "   ")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_testuser"))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(deleteContentUseCase)
    }

    // ------------------------------------------------------------------
    // Lifecycle: HIDE /api/v1/contents/{contentId}/hide
    // ------------------------------------------------------------------

    @Test
    fun hideContent_whenSuccess_returns204() {
        val id = PublicId("ct_123")
        Mockito.`when`(hideContentUseCase.hide(id)).thenReturn(ContentLifecycleResult.SUCCESS)

        mockMvc.perform(post("/api/v1/contents/{contentId}/hide", "ct_123").sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_testuser"))
            .andExpect(status().isNoContent)

        Mockito.verify(hideContentUseCase).hide(id)
    }

    @Test
    fun hideContent_whenNotFound_returns404() {
        val id = PublicId("ct_missing")
        Mockito.`when`(hideContentUseCase.hide(id)).thenReturn(ContentLifecycleResult.NOT_FOUND)

        mockMvc.perform(post("/api/v1/contents/{contentId}/hide", "ct_missing").sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_testuser"))
            .andExpect(status().isNotFound)

        Mockito.verify(hideContentUseCase).hide(id)
    }

    @Test
    fun hideContent_whenInvalidTransition_returns400() {
        val id = PublicId("ct_123")
        Mockito.`when`(hideContentUseCase.hide(id)).thenReturn(ContentLifecycleResult.INVALID_TRANSITION)

        mockMvc.perform(post("/api/v1/contents/{contentId}/hide", "ct_123").sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_testuser"))
            .andExpect(status().isBadRequest)

        Mockito.verify(hideContentUseCase).hide(id)
    }

    @Test
    fun hideContent_whenBlankId_returns401() {
        mockMvc.perform(post("/api/v1/contents/{contentId}/hide", "   ")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_testuser"))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(hideContentUseCase)
    }

    // ------------------------------------------------------------------
    // Lifecycle: RESTORE /api/v1/contents/{contentId}/restore
    // ------------------------------------------------------------------

    @Test
    fun restoreContent_whenSuccess_returns204() {
        val id = PublicId("ct_123")
        Mockito.`when`(restoreContentUseCase.restore(id)).thenReturn(ContentLifecycleResult.SUCCESS)

        mockMvc.perform(post("/api/v1/contents/{contentId}/restore", "ct_123").sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_testuser"))
            .andExpect(status().isNoContent)

        Mockito.verify(restoreContentUseCase).restore(id)
    }

    @Test
    fun restoreContent_whenNotFound_returns404() {
        val id = PublicId("ct_missing")
        Mockito.`when`(restoreContentUseCase.restore(id)).thenReturn(ContentLifecycleResult.NOT_FOUND)

        mockMvc.perform(post("/api/v1/contents/{contentId}/restore", "ct_missing").sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_testuser"))
            .andExpect(status().isNotFound)

        Mockito.verify(restoreContentUseCase).restore(id)
    }

    @Test
    fun restoreContent_whenInvalidTransition_returns400() {
        val id = PublicId("ct_123")
        Mockito.`when`(restoreContentUseCase.restore(id)).thenReturn(ContentLifecycleResult.INVALID_TRANSITION)

        mockMvc.perform(post("/api/v1/contents/{contentId}/restore", "ct_123").sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_testuser"))
            .andExpect(status().isBadRequest)

        Mockito.verify(restoreContentUseCase).restore(id)
    }

    @Test
    fun restoreContent_whenBlankId_returns401() {
        mockMvc.perform(post("/api/v1/contents/{contentId}/restore", "   ")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_testuser"))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(restoreContentUseCase)
    }
}
