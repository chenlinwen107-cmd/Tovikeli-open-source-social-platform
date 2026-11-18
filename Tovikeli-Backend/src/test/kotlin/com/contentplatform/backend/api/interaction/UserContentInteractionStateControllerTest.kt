package com.contentplatform.backend.api.interaction

import com.contentplatform.backend.application.interaction.GetUserContentInteractionStateUseCase
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.UserRepository
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.api.authentication.SessionAttributes
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.domain.interaction.UserContentInteractionState
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [UserContentInteractionStateController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class UserContentInteractionStateControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var getStateUseCase: GetUserContentInteractionStateUseCase

    @MockitoBean
    private lateinit var userRepository: UserRepository

    private val userId = "usr_alice"

    @BeforeEach
    fun setUp() {
        val activeUser = User(
            id = PublicId(userId),
            username = "alice",
            displayName = "Alice",
            status = UserStatus.ACTIVE,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )
        Mockito.`when`(userRepository.findById(PublicId(userId))).thenReturn(activeUser)
        Mockito.`when`(userRepository.findById(PublicId("au_1"))).thenReturn(activeUser.copy(id = PublicId("au_1")))
    }

    // ------------------------------------------------------------------
    // Valid request → 200
    // ------------------------------------------------------------------

    @Test
    fun getState_validRequest_returns200() {
        val state = UserContentInteractionState(
            contentId = PublicId("ct_1"),
            like = false, favorite = false, repost = false, bookmark = false
        )
        Mockito.`when`(getStateUseCase.getState(PublicId("au_1"), PublicId("ct_1")))
            .thenReturn(state)

        mockMvc.perform(get("/api/v1/users/{userId}/contents/{contentId}/interactions", "au_1", "ct_1")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "au_1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.contentId").value("ct_1"))
            .andExpect(jsonPath("$.like").value(false))
            .andExpect(jsonPath("$.favorite").value(false))
            .andExpect(jsonPath("$.repost").value(false))
            .andExpect(jsonPath("$.bookmark").value(false))
    }

    // ------------------------------------------------------------------
    // All false state
    // ------------------------------------------------------------------

    @Test
    fun getState_allFalse_returnsAllFalseJson() {
        val state = UserContentInteractionState(
            contentId = PublicId("ct_1"),
            like = false, favorite = false, repost = false, bookmark = false
        )
        Mockito.`when`(getStateUseCase.getState(PublicId("au_1"), PublicId("ct_1")))
            .thenReturn(state)

        mockMvc.perform(get("/api/v1/users/{userId}/contents/{contentId}/interactions", "au_1", "ct_1")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "au_1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.like").value(false))
            .andExpect(jsonPath("$.favorite").value(false))
            .andExpect(jsonPath("$.repost").value(false))
            .andExpect(jsonPath("$.bookmark").value(false))
    }

    // ------------------------------------------------------------------
    // Mixed state
    // ------------------------------------------------------------------

    @Test
    fun getState_mixedState_returnsCorrectFlags() {
        val state = UserContentInteractionState(
            contentId = PublicId("ct_1"),
            like = true, favorite = false, repost = true, bookmark = false
        )
        Mockito.`when`(getStateUseCase.getState(PublicId("au_1"), PublicId("ct_1")))
            .thenReturn(state)

        mockMvc.perform(get("/api/v1/users/{userId}/contents/{contentId}/interactions", "au_1", "ct_1")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "au_1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.like").value(true))
            .andExpect(jsonPath("$.favorite").value(false))
            .andExpect(jsonPath("$.repost").value(true))
            .andExpect(jsonPath("$.bookmark").value(false))
    }

    // ------------------------------------------------------------------
    // All true state
    // ------------------------------------------------------------------

    @Test
    fun getState_allTrue_returnsAllTrueJson() {
        val state = UserContentInteractionState(
            contentId = PublicId("ct_1"),
            like = true, favorite = true, repost = true, bookmark = true
        )
        Mockito.`when`(getStateUseCase.getState(PublicId("au_1"), PublicId("ct_1")))
            .thenReturn(state)

        mockMvc.perform(get("/api/v1/users/{userId}/contents/{contentId}/interactions", "au_1", "ct_1")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "au_1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.like").value(true))
            .andExpect(jsonPath("$.favorite").value(true))
            .andExpect(jsonPath("$.repost").value(true))
            .andExpect(jsonPath("$.bookmark").value(true))
    }

    // ------------------------------------------------------------------
    // Missing user → 404
    // ------------------------------------------------------------------

    @Test
    fun getState_missingUser_returns404() {
        Mockito.`when`(userRepository.findById(PublicId("au_missing"))).thenReturn(null)

        mockMvc.perform(get("/api/v1/users/{userId}/contents/{contentId}/interactions", "au_missing", "ct_1")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "au_missing"))
            .andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------
    // Missing content → 404
    // ------------------------------------------------------------------

    @Test
    fun getState_missingContent_returns404() {
        Mockito.`when`(getStateUseCase.getState(PublicId("au_1"), PublicId("ct_missing")))
            .thenReturn(null)

        mockMvc.perform(get("/api/v1/users/{userId}/contents/{contentId}/interactions", "au_1", "ct_missing")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "au_1"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // Inaccessible content → 404
    // ------------------------------------------------------------------

    @Test
    fun getState_inaccessibleContent_returns404() {
        Mockito.`when`(getStateUseCase.getState(PublicId("au_1"), PublicId("ct_deleted")))
            .thenReturn(null)

        mockMvc.perform(get("/api/v1/users/{userId}/contents/{contentId}/interactions", "au_1", "ct_deleted")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "au_1"))
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // Exact JSON field names
    // ------------------------------------------------------------------

    @Test
    fun getState_jsonFieldNames_areCorrect() {
        val state = UserContentInteractionState(
            contentId = PublicId("ct_1"),
            like = true, favorite = false, repost = false, bookmark = true
        )
        Mockito.`when`(getStateUseCase.getState(PublicId("au_1"), PublicId("ct_1")))
            .thenReturn(state)

        mockMvc.perform(get("/api/v1/users/{userId}/contents/{contentId}/interactions", "au_1", "ct_1")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "au_1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.contentId").exists())
            .andExpect(jsonPath("$.like").exists())
            .andExpect(jsonPath("$.favorite").exists())
            .andExpect(jsonPath("$.repost").exists())
            .andExpect(jsonPath("$.bookmark").exists())
    }

    // ------------------------------------------------------------------
    // Exact contentId mapping
    // ------------------------------------------------------------------

    @Test
    fun getState_contentId_matchesPathVariable() {
        val state = UserContentInteractionState(
            contentId = PublicId("ct_abc123"),
            like = false, favorite = false, repost = false, bookmark = false
        )
        Mockito.`when`(getStateUseCase.getState(PublicId("au_1"), PublicId("ct_abc123")))
            .thenReturn(state)

        mockMvc.perform(get("/api/v1/users/{userId}/contents/{contentId}/interactions", "au_1", "ct_abc123")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "au_1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.contentId").value("ct_abc123"))
    }

    // ------------------------------------------------------------------
    // Blank userId → 404
    // ------------------------------------------------------------------

    @Test
    fun getState_blankUserId_returns401() {
        mockMvc.perform(get("/api/v1/users/{userId}/contents/{contentId}/interactions", "   ", "ct_1")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "   "))
            .andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------
    // Blank contentId → 404
    // ------------------------------------------------------------------

    @Test
    fun getState_blankContentId_returns404() {
        mockMvc.perform(get("/api/v1/users/{userId}/contents/{contentId}/interactions", "au_1", "   ")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "au_1"))
            .andExpect(status().isNotFound)
    }
}
