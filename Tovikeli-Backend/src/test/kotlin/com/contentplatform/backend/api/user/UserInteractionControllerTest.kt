package com.contentplatform.backend.api.user

import com.contentplatform.backend.api.authentication.SessionAttributes
import com.contentplatform.backend.application.interaction.GetUserInteractionHistoryUseCase
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.UserRepository
import com.contentplatform.backend.application.interaction.HydratedInteraction
import com.contentplatform.backend.application.interaction.HydratedInteractionPage
import com.contentplatform.backend.application.interaction.InteractionQueryUserNotFoundException
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.interaction.Interaction
import com.contentplatform.backend.domain.interaction.InteractionCursor
import com.contentplatform.backend.domain.interaction.InteractionType
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MVC slice test for [UserInteractionController] (Stage 19.5).
 *
 * Covers:
 *   GET /api/v1/users/{userId}/interactions
 *
 * Use cases are mocked; no database, no Flyway, no external HTTP.
 */
@WebMvcTest(controllers = [UserInteractionController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class UserInteractionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var getHistoryUseCase: GetUserInteractionHistoryUseCase

    @MockitoBean
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        val activeUser = User(
            id = PublicId(userId),
            username = "alice",
            displayName = "Alice",
            avatar = null,

            bio = null,

            gender = "secret",

            location = null,

            cover = null,

            status = UserStatus.ACTIVE,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        Mockito.`when`(userRepository.findById(PublicId(userId))).thenReturn(activeUser)
    }

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private val userId = "usr_alice"

    private fun interaction(
        idValue: String = "int_1",
        userIdValue: String = userId,
        contentIdValue: String = "ct_1",
        type: InteractionType = InteractionType.LIKE,
        createdAt: Long = 1_757_000_000_100L
    ): Interaction = Interaction(
        id = PublicId(idValue),
        userId = PublicId(userIdValue),
        contentId = PublicId(contentIdValue),
        type = type,
        createdAt = createdAt
    )

    private fun page(
        items: List<HydratedInteraction> = emptyList(),
        nextCursor: InteractionCursor? = null
    ): HydratedInteractionPage = HydratedInteractionPage(items = items, nextCursor = nextCursor)

    private fun hydrated(interaction: Interaction, content: Content? = null): HydratedInteraction =
        HydratedInteraction(interaction = interaction, content = content)

    private fun sampleContent(
        id: String = "ct_sc1",
        sourceContentId: String = "sc_1"
    ): Content = Content(
        id = PublicId(id),
        instanceId = PublicId("inst_1"),
        sourceContentId = sourceContentId,
        authorId = PublicId("au_1"),
        sourceId = PublicId("src_1"),
        text = "sample",
        language = "en",
        visibility = ContentVisibility.PUBLIC,
        status = ContentStatus.ACTIVE,
        mediaIds = emptyList(),
        parentId = null,
        repostOfId = null,
        quoteOfId = null,
        externalCreatedAt = null,
        createdAt = 1000L,
        updatedAt = 1000L,
        metadata = emptyMap()
    )

    /** Encodes an arbitrary logical string the way a malformed cursor would. */
    private fun b64(logical: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(logical.toByteArray())

    private fun neverInvoke() {
        Mockito.verifyNoInteractions(getHistoryUseCase)
    }

    // ------------------------------------------------------------------
    // Default limit=20, type not provided
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenDefaultParams_returns200WithMappedArray() {
        val i1 = interaction(idValue = "int_1", contentIdValue = "ct_a", type = InteractionType.LIKE)
        val i2 = interaction(idValue = "int_2", contentIdValue = "ct_b", type = InteractionType.FAVORITE)
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), null, 20, null))
            .thenReturn(page(listOf(hydrated(i1), hydrated(i2))))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$", hasSize<Any>(2)))
            .andExpect(jsonPath("$[0].id").value("int_1"))
            .andExpect(jsonPath("$[0].userId").value(userId))
            .andExpect(jsonPath("$[0].contentId").value("ct_a"))
            .andExpect(jsonPath("$[0].type").value("LIKE"))
            .andExpect(jsonPath("$[0].createdAt").isNumber())
            .andExpect(jsonPath("$[1].id").value("int_2"))
            .andExpect(jsonPath("$[1].contentId").value("ct_b"))
            .andExpect(jsonPath("$[1].type").value("FAVORITE"))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        Mockito.verify(getHistoryUseCase).getHistory(PublicId(userId), null, 20, null)
    }

    // ------------------------------------------------------------------
    // Explicit limit values
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenExplicitLimit5_forwardsItToUseCase() {
        val i1 = interaction(idValue = "int_1")
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), null, 5, null))
            .thenReturn(page(listOf(hydrated(i1))))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("limit", "5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))

        Mockito.verify(getHistoryUseCase).getHistory(PublicId(userId), null, 5, null)
    }

    @Test
    fun getUserInteractions_whenLimit100_returns200() {
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), null, 100, null))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("limit", "100"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        Mockito.verify(getHistoryUseCase).getHistory(PublicId(userId), null, 100, null)
    }

    // ------------------------------------------------------------------
    // limit=0: 200 [] without invoking use case
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenLimitZero_returns200EmptyArrayWithoutInvokingUseCase() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("limit", "0"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenLimitZeroWithValidCursor_returns200EmptyArrayWithoutInvokingUseCase() {
        val cursor = InteractionCursor(createdAt = 123L, interactionId = PublicId("int_1"))
        val raw = cursor.encode()

        mockMvc.perform(
            get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("limit", "0")
                .param("cursor", raw)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        neverInvoke()
    }

    // ------------------------------------------------------------------
    // Empty page -> 200 [] and no Link
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenEmptyPage_returns200EmptyArray() {
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), null, 20, null))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))
    }

    // ------------------------------------------------------------------
    // Type filter: each valid type
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenTypeLIKE_forwardsTypeToUseCase() {
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), InteractionType.LIKE, 20, null))
            .thenReturn(page(listOf(hydrated(interaction(type = InteractionType.LIKE)))))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("type", "LIKE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].type").value("LIKE"))

        Mockito.verify(getHistoryUseCase).getHistory(PublicId(userId), InteractionType.LIKE, 20, null)
    }

    @Test
    fun getUserInteractions_whenTypeFAVORITE_forwardsTypeToUseCase() {
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), InteractionType.FAVORITE, 20, null))
            .thenReturn(page(listOf(hydrated(interaction(type = InteractionType.FAVORITE)))))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("type", "FAVORITE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].type").value("FAVORITE"))

        Mockito.verify(getHistoryUseCase).getHistory(PublicId(userId), InteractionType.FAVORITE, 20, null)
    }

    @Test
    fun getUserInteractions_whenTypeREPOST_forwardsTypeToUseCase() {
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), InteractionType.REPOST, 20, null))
            .thenReturn(page(listOf(hydrated(interaction(type = InteractionType.REPOST)))))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("type", "REPOST"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].type").value("REPOST"))

        Mockito.verify(getHistoryUseCase).getHistory(PublicId(userId), InteractionType.REPOST, 20, null)
    }

    @Test
    fun getUserInteractions_whenTypeBOOKMARK_forwardsTypeToUseCase() {
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), InteractionType.BOOKMARK, 20, null))
            .thenReturn(page(listOf(hydrated(interaction(type = InteractionType.BOOKMARK)))))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("type", "BOOKMARK"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].type").value("BOOKMARK"))

        Mockito.verify(getHistoryUseCase).getHistory(PublicId(userId), InteractionType.BOOKMARK, 20, null)
    }

    @Test
    fun getUserInteractions_whenInvalidType_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("type", "INVALID"))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenTypeLowercase_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("type", "like"))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    // ------------------------------------------------------------------
    // Invalid limit -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenLimitNegative_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("limit", "-1"))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenLimitAboveMax_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("limit", "101"))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenLimitNotAnInteger_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("limit", "abc"))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    // ------------------------------------------------------------------
    // Cursor: valid cursor is decoded and forwarded
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenCursorProvided_decodesAndForwardsToUseCase() {
        val cursor = InteractionCursor(createdAt = 1_757_000_000_123L, interactionId = PublicId("int_1"))
        val raw = cursor.encode()
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), null, 5, cursor))
            .thenReturn(page(listOf(hydrated(interaction(idValue = "int_2")))))

        mockMvc.perform(
            get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("limit", "5")
                .param("cursor", raw)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))

        Mockito.verify(getHistoryUseCase).getHistory(PublicId(userId), null, 5, cursor)
    }

    // ------------------------------------------------------------------
    // Cursor: invalid cursors -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenCursorEmpty_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("cursor", ""))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenCursorWhitespace_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("cursor", "   "))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenCursorMalformedBase64_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("cursor", "!!!"))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenCursorMissingVersion_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("cursor", b64("123:int_1")))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenCursorInvalidVersion_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("cursor", b64("v2:123:int_1")))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenCursorMalformedFieldCount_returns400() {
        mockMvc.perform(
            get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("cursor", b64("v1:123:int_1:extra"))
        )
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenCursorInvalidCreatedAt_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("cursor", b64("v1:abc:int_1")))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenCursorNegativeCreatedAt_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("cursor", b64("v1:-5:int_1")))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenCursorMissingInteractionId_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("cursor", b64("v1:123:")))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenCursorBlankInteractionId_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("cursor", b64("v1:123:   ")))
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    @Test
    fun getUserInteractions_whenInvalidCursorWithLimitZero_stillReturns400() {
        mockMvc.perform(
            get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("limit", "0")
                .param("cursor", "!!!")
        )
            .andExpect(status().isBadRequest)

        neverInvoke()
    }

    // ------------------------------------------------------------------
    // Valid cursor, no matching rows -> 200 [] (not 400)
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenValidCursorButNoMatchingRows_returns200EmptyArray() {
        val cursor = InteractionCursor(createdAt = 999L, interactionId = PublicId("int_none"))
        val raw = cursor.encode()
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), null, 20, cursor))
            .thenReturn(page(emptyList()))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("cursor", raw))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))

        Mockito.verify(getHistoryUseCase).getHistory(PublicId(userId), null, 20, cursor)
    }

    // ------------------------------------------------------------------
    // User not found -> 404
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenUserNotFound_returns404() {
        val missingUserId = "usr_missing"
        Mockito.`when`(userRepository.findById(PublicId(missingUserId))).thenReturn(null)

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", missingUserId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, missingUserId))
            .andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------
    // DISABLED user still allows query
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenUserDisabled_returns403() {
        val disabledUserId = "usr_disabled"
        val disabledUser = User(
            id = PublicId(disabledUserId),
            username = "disabled",
            displayName = "Disabled",
            avatar = null,

            bio = null,

            gender = "secret",

            location = null,

            cover = null,

            status = UserStatus.DISABLED,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        Mockito.`when`(userRepository.findById(PublicId(disabledUserId))).thenReturn(disabledUser)

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", disabledUserId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, disabledUserId))
            .andExpect(status().isForbidden)
    }

    // ------------------------------------------------------------------
    // Blank userId -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenUserIdBlank_returns401() {
        mockMvc.perform(get("/api/v1/users/{userId}/interactions", "   ")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "   "))
            .andExpect(status().isUnauthorized)

        neverInvoke()
    }

    // ------------------------------------------------------------------
    // Link header: sent only when a next page exists
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenNextPageExists_returnsLinkHeaderWithNextCursor() {
        val nextCursor = InteractionCursor(createdAt = 1_757_000_000_123L, interactionId = PublicId("int_last"))
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), null, 5, null))
            .thenReturn(page(listOf(hydrated(interaction(idValue = "int_1"))), nextCursor))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("limit", "5"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(header().string(HttpHeaders.LINK, containsString("</api/v1/users/usr_alice/interactions?")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("rel=\"next\"")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("limit=5")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("cursor=")))

        Mockito.verify(getHistoryUseCase).getHistory(PublicId(userId), null, 5, null)
    }

    @Test
    fun getUserInteractions_whenNextPageExists_linkPreservesTypeFilter() {
        val nextCursor = InteractionCursor(createdAt = 7L, interactionId = PublicId("int_last"))
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), InteractionType.LIKE, 20, null))
            .thenReturn(page(listOf(hydrated(interaction(idValue = "int_1"))), nextCursor))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("type", "LIKE"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.LINK, containsString("type=LIKE")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("limit=20")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("cursor=")))
    }

    @Test
    fun getUserInteractions_whenNextPageExists_linkPreservesUserIdInPath() {
        val nextCursor = InteractionCursor(createdAt = 7L, interactionId = PublicId("int_last"))
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), null, 10, null))
            .thenReturn(page(listOf(hydrated(interaction(idValue = "int_1"))), nextCursor))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId).param("limit", "10"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.LINK, containsString("/api/v1/users/usr_alice/interactions?")))
            .andExpect(header().string(HttpHeaders.LINK, containsString("limit=10")))
    }

    @Test
    fun getUserInteractions_whenNoNextPage_returnsNoLinkHeader() {
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), null, 20, null))
            .thenReturn(page(listOf(hydrated(interaction())), null))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(header().doesNotExist(HttpHeaders.LINK))
    }

    // ------------------------------------------------------------------
    // Response is InteractionResponse with optional hydrated content
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_returnsInteractionResponseFields() {
        val i1 = interaction(
            idValue = "int_xyz",
            userIdValue = "usr_alice",
            contentIdValue = "ct_content1",
            type = InteractionType.BOOKMARK,
            createdAt = 1_700_000_000_000L
        )
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), null, 20, null))
            .thenReturn(page(listOf(hydrated(i1))))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("int_xyz"))
            .andExpect(jsonPath("$[0].userId").value("usr_alice"))
            .andExpect(jsonPath("$[0].contentId").value("ct_content1"))
            .andExpect(jsonPath("$[0].type").value("BOOKMARK"))
            .andExpect(jsonPath("$[0].createdAt").value(1_700_000_000_000L))
            .andExpect(jsonPath("$[0].content").value(nullValue()))
    }

    // ------------------------------------------------------------------
    // Hydrated content in response
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_withContent_hydratesContentInResponse() {
        val content = sampleContent("ct_c1", "sc_1")
        val i1 = interaction(idValue = "int_1", contentIdValue = "ct_c1")
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), null, 20, null))
            .thenReturn(page(listOf(hydrated(i1, content))))

        mockMvc.perform(get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].content").isNotEmpty)
            .andExpect(jsonPath("$[0].content.id").value("ct_c1"))
            .andExpect(jsonPath("$[0].content.sourceContentId").value("sc_1"))
            .andExpect(jsonPath("$[0].content.visibility").value("PUBLIC"))
    }

    // ------------------------------------------------------------------
    // Cursor + type + limit combined
    // ------------------------------------------------------------------

    @Test
    fun getUserInteractions_whenTypeAndLimitAndCursorProvided_forwardsAllToUseCase() {
        val cursor = InteractionCursor(createdAt = 5_000L, interactionId = PublicId("int_1"))
        val raw = cursor.encode()
        Mockito.`when`(getHistoryUseCase.getHistory(PublicId(userId), InteractionType.REPOST, 15, cursor))
            .thenReturn(page(listOf(hydrated(interaction(idValue = "int_2", type = InteractionType.REPOST)))))

        mockMvc.perform(
            get("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "REPOST")
                .param("limit", "15")
                .param("cursor", raw)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))

        Mockito.verify(getHistoryUseCase).getHistory(PublicId(userId), InteractionType.REPOST, 15, cursor)
    }
}
