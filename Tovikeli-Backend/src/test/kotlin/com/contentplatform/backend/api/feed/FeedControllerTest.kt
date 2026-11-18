package com.contentplatform.backend.api.feed

import com.contentplatform.backend.api.content.ContentCursorCodec
import com.contentplatform.backend.application.content.BrowseContentUseCase
import com.contentplatform.backend.application.feed.FeedRefillTrigger
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.UserRepository
import java.util.Base64
import kotlin.test.assertEquals
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.hasSize
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MVC slice test for the P9-G2 Public Feed API ([FeedController]).
 *
 * The controller is registered because the slice property enables its
 * datasource-conditional wiring. [BrowseContentUseCase] is mocked, so no
 * database, Flyway or external HTTP is involved. Every request is anonymous
 * (no session is provided), which doubles as the "no authentication required"
 * coverage.
 */
@WebMvcTest(controllers = [FeedController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class FeedControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var browseContentUseCase: BrowseContentUseCase

    @MockitoBean
    private lateinit var homeFeedUseCase: com.contentplatform.backend.application.feed.HomeFeedUseCase

    @MockitoBean
    private lateinit var feedRefillTrigger: FeedRefillTrigger

    // The MVC slice also pulls in the project's AuthenticationFilter, which
    // depends on UserRepository. It is mocked so the filter can be constructed
    // while every Feed request stays anonymous.
    @MockitoBean
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun connectHomeFeedMockToBrowseFixture() {
        Mockito.lenient().`when`(
            browseContentUseCase.browse(
                Mockito.isNull<PublicId>(),
                Mockito.isNull<PublicId>(),
                Mockito.isNull<Boolean>(),
                Mockito.isNull<ContentCursor>(),
                Mockito.anyInt()
            )
        ).thenReturn(ContentBrowsePage(emptyList(), null))

        Mockito.`when`(homeFeedUseCase.query(Mockito.any(com.contentplatform.backend.application.feed.FeedQuery::class.java)))
            .thenAnswer { invocation ->
                val query = invocation.getArgument<com.contentplatform.backend.application.feed.FeedQuery>(0)
                val page = browseContentUseCase.browse(null, null, null, query.cursor, query.limit)
                com.contentplatform.backend.application.feed.FeedResult.Ready(
                    com.contentplatform.backend.application.feed.FeedPage(page.contents, page.nextCursor)
                )
            }
    }
    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun feedContent(sourceContentId: String, text: String = "feed $sourceContentId"): Content =
        Content.createFromAdapter(
            instanceIdValue = "inst_misskey_art",
            sourceContentId = sourceContentId,
            authorId = PublicId("au_author_1"),
            sourceId = PublicId("src_misskey"),
            text = text,
            language = "en",
            visibility = ContentVisibility.PUBLIC,
            mediaIds = listOf(PublicId("md_1")),
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

    private fun neverFeed() {
        Mockito.verifyNoInteractions(browseContentUseCase)
    }

    // ------------------------------------------------------------------
    // A/E/H: first page, default limit, global public pool (all filters null)
    // ------------------------------------------------------------------

    @Test
    fun feed_whenDefaultLimit_returns200FlatMappedArrayAndNoLink() {
        val first = feedContent("sc-1", "feed one")
        val second = feedContent("sc-2", "feed two")
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(listOf(first, second)))

        mockMvc.perform(get("/api/v1/feed"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasSize<Any>(2)))
            .andExpect(jsonPath("$[0].id").value(first.id.value))
            .andExpect(jsonPath("$[0].instanceId").value("inst_misskey_art"))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-1"))
            .andExpect(jsonPath("$[0].text").value("feed one"))
            .andExpect(jsonPath("$[0].visibility").value("PUBLIC"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].mediaIds", hasSize<Any>(1)))
            .andExpect(jsonPath("$[1].id").value(second.id.value))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        // Unfiltered global public pool: every filter is null and the default limit is 20.
        Mockito.verify(browseContentUseCase).browse(null, null, null, null, 20)
    }

    // ------------------------------------------------------------------
    // B: explicit limit
    // ------------------------------------------------------------------

    @Test
    fun feed_whenExplicitLimit_forwardsItToUseCase() {
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 10))
            .thenReturn(page(listOf(feedContent("sc-1"))))

        mockMvc.perform(get("/api/v1/feed").param("limit", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-1"))

        Mockito.verify(browseContentUseCase).browse(null, null, null, null, 10)
    }

    // ------------------------------------------------------------------
    // C/K: max limit equals the existing repository contract
    // ------------------------------------------------------------------

    @Test
    fun feed_whenMaxLimit_returns200AndMatchesRepositoryContract() {
        assertEquals(100, ContentRepository.MAX_PUBLIC_BROWSE_LIMIT, "frozen public browse limit")

        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, ContentRepository.MAX_PUBLIC_BROWSE_LIMIT))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/feed").param("limit", "100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        Mockito.verify(browseContentUseCase)
            .browse(null, null, null, null, ContentRepository.MAX_PUBLIC_BROWSE_LIMIT)
    }

    // ------------------------------------------------------------------
    // limit=0 -> 200 [] without invoking the use case
    // ------------------------------------------------------------------

    @Test
    fun feed_whenLimitZero_returns200EmptyArrayWithoutInvokingUseCase() {
        mockMvc.perform(get("/api/v1/feed").param("limit", "0"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        neverFeed()
    }

    @Test
    fun feed_whenLimitZeroWithValidCursor_returns200EmptyArrayWithoutInvokingUseCase() {
        val valid = ContentCursorCodec.encode(ContentCursor(createdAt = 123L, id = PublicId("ct_1")))

        mockMvc.perform(get("/api/v1/feed").param("limit", "0").param("cursor", valid))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        neverFeed()
    }

    // ------------------------------------------------------------------
    // D: invalid limit -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun feed_whenLimitNegative_returns400() {
        mockMvc.perform(get("/api/v1/feed").param("limit", "-1"))
            .andExpect(status().isBadRequest)
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        neverFeed()
    }

    @Test
    fun feed_whenLimitAboveMax_returns400() {
        mockMvc.perform(get("/api/v1/feed").param("limit", "101"))
            .andExpect(status().isBadRequest)
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        neverFeed()
    }

    @Test
    fun feed_whenLimitNotAnInteger_returns400() {
        mockMvc.perform(get("/api/v1/feed").param("limit", "abc"))
            .andExpect(status().isBadRequest)
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        neverFeed()
    }

    // ------------------------------------------------------------------
    // F: second page cursor is decoded and forwarded
    // ------------------------------------------------------------------

    @Test
    fun feed_whenCursorProvided_decodesAndForwardsCursorToUseCase() {
        val cursor = ContentCursor(createdAt = 1_757_000_000_123L, id = PublicId("ct_1"))
        val raw = ContentCursorCodec.encode(cursor)
        Mockito.`when`(browseContentUseCase.browse(null, null, null, cursor, 20))
            .thenReturn(page(listOf(feedContent("sc-1"))))

        mockMvc.perform(get("/api/v1/feed").param("cursor", raw))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))

        Mockito.verify(browseContentUseCase).browse(null, null, null, cursor, 20)
    }

    @Test
    fun feed_whenValidCursorButNoMatchingRows_returns200EmptyArray() {
        val cursor = ContentCursor(createdAt = 999L, id = PublicId("ct_none"))
        val raw = ContentCursorCodec.encode(cursor)
        Mockito.`when`(browseContentUseCase.browse(null, null, null, cursor, 20))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/feed").param("cursor", raw))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        Mockito.verify(browseContentUseCase).browse(null, null, null, cursor, 20)
    }

    // ------------------------------------------------------------------
    // Malformed cursor -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun feed_whenCursorMalformed_returns400() {
        mockMvc.perform(get("/api/v1/feed").param("cursor", "!!!"))
            .andExpect(status().isBadRequest)

        neverFeed()
    }

    @Test
    fun feed_whenCursorMalformedWithLimitZero_stillReturns400() {
        mockMvc.perform(get("/api/v1/feed").param("limit", "0").param("cursor", "!!!"))
            .andExpect(status().isBadRequest)

        neverFeed()
    }

    @Test
    fun feed_whenCursorUnknownVersion_returns400() {
        mockMvc.perform(get("/api/v1/feed").param("cursor", b64("v2:123:ct_1")))
            .andExpect(status().isBadRequest)

        neverFeed()
    }

    // ------------------------------------------------------------------
    // G: Link header only when a next page exists
    // ------------------------------------------------------------------

    @Test
    fun feed_whenNextPageExists_returnsLinkHeaderWithNextCursor() {
        val last = feedContent("sc-last")
        val nextCursor = ContentCursor(createdAt = 1_757_000_000_123L, id = PublicId("ct_last"))
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(listOf(last), nextCursor))

        mockMvc.perform(get("/api/v1/feed"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-last"))
            .andExpect(header().string(HttpHeaders.LINK, containsString("</api/v1/feed?")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("rel=\"next\"")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("limit=20")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("cursor=")))

        Mockito.verify(browseContentUseCase).browse(null, null, null, null, 20)
    }

    @Test
    fun feed_whenNextPageExists_linkCarriesEncodedCursor() {
        val nextCursor = ContentCursor(createdAt = 7L, id = PublicId("ct_last"))
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 5))
            .thenReturn(page(listOf(feedContent("sc-last")), nextCursor))

        val encoded = ContentCursorCodec.encode(nextCursor)
        mockMvc.perform(get("/api/v1/feed").param("limit", "5"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.LINK, containsString("limit=5")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("cursor=$encoded")))
    }

    @Test
    fun feed_whenNoNextPage_returnsNoLinkHeader() {
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(listOf(feedContent("sc-1")), null))

        mockMvc.perform(get("/api/v1/feed"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))
    }

    // ------------------------------------------------------------------
    // J: empty pool -> 200 [] (existing empty-list contract)
    // ------------------------------------------------------------------

    @Test
    fun feed_whenPoolEmpty_returns200EmptyArray() {
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/feed"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))
    }

    // ------------------------------------------------------------------
    // I: anonymous access requires no session and applies no identity filter
    // ------------------------------------------------------------------

    @Test
    fun feed_isAnonymousAndUsesUnfilteredGlobalPool() {
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(listOf(feedContent("sc-1"))))

        // No session, no X-User-Id header.
        mockMvc.perform(get("/api/v1/feed"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))

        Mockito.verify(browseContentUseCase).browse(null, null, null, null, 20)
    }

    // ------------------------------------------------------------------
    // P9-G3: tail detection triggers exactly one non-blocking refill request
    // ------------------------------------------------------------------

    @Test
    fun feed_whenTailPage_requestsBackgroundRefillOnce() {
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(listOf(feedContent("sc-last")), nextCursor = null))

        mockMvc.perform(get("/api/v1/feed"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-last"))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        Mockito.verify(feedRefillTrigger).trigger()
    }

    @Test
    fun feed_whenEmptyPool_requestsBackgroundRefill() {
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(emptyList(), nextCursor = null))

        mockMvc.perform(get("/api/v1/feed"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))

        Mockito.verify(feedRefillTrigger).trigger()
    }

    @Test
    fun feed_whenNextPageExists_doesNotRequestRefill() {
        val nextCursor = ContentCursor(createdAt = 7L, id = PublicId("ct_last"))
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(listOf(feedContent("sc-1")), nextCursor))

        mockMvc.perform(get("/api/v1/feed"))
            .andExpect(status().isOk)

        Mockito.verifyNoInteractions(feedRefillTrigger)
    }

    @Test
    fun feed_whenLimitZero_doesNotRequestRefill() {
        mockMvc.perform(get("/api/v1/feed").param("limit", "0"))
            .andExpect(status().isOk)

        Mockito.verifyNoInteractions(feedRefillTrigger)
    }

    @Test
    fun feed_whenInvalidLimit_doesNotRequestRefill() {
        mockMvc.perform(get("/api/v1/feed").param("limit", "101"))
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(feedRefillTrigger)
    }

    @Test
    fun feed_whenRefillTriggerThrows_stillReturns200WithFeedBody() {
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(listOf(feedContent("sc-1")), nextCursor = null))
        Mockito.doThrow(RuntimeException("refill boom")).`when`(feedRefillTrigger).trigger()

        mockMvc.perform(get("/api/v1/feed"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].sourceContentId").value("sc-1"))

        Mockito.verify(feedRefillTrigger).trigger()
    }

    @Test
    fun feed_whenTailPageWithoutSession_isAnonymousAndTriggersRefill() {
        Mockito.`when`(browseContentUseCase.browse(null, null, null, null, 20))
            .thenReturn(page(emptyList(), nextCursor = null))

        mockMvc.perform(get("/api/v1/feed"))
            .andExpect(status().isOk)

        Mockito.verify(feedRefillTrigger).trigger()
    }
}
