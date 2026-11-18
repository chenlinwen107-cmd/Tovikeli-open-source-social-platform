package com.contentplatform.backend.api.authentication

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.UserRepository
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Focused authentication boundary tests for [AuthenticationFilter].
 *
 * Validates the session-based auth contract:
 * - valid session with ACTIVE user → CurrentCaller set → endpoint proceeds
 * - missing session on protected endpoint → 401
 * - malformed session userId → 401
 * - non-existent user in session → 401
 * - DISABLED user in session → 403
 * - public GET endpoints remain accessible without session
 * - X-User-Id header has zero authority
 *
 * Uses a minimal controller stub (ContentControllerStub) to test the filter.
 * No database, no Flyway, no external HTTP.
 */
@WebMvcTest(controllers = [ContentControllerStub::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class AuthenticationFilterTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var userRepository: UserRepository

    private val testUserId = "usr_testuser"
    private val disabledUserId = "usr_disabled"

    private fun activeUser(id: String = testUserId): User = User(
        id = PublicId(id),
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

    private fun disabledUser(id: String = disabledUserId): User = User(
        id = PublicId(id),
        username = "disabled",
        displayName = "Disabled User",
        avatar = null,

        bio = null,

        gender = "secret",

        location = null,

        cover = null,

        status = UserStatus.DISABLED,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L
    )

    // ------------------------------------------------------------------
    // Protected GET — no session → 401
    // ------------------------------------------------------------------

    @Test
    fun protectedEndpoint_withoutSession_returns401() {
        mockMvc.perform(
            get("/stub/protected")
        )
            .andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------
    // Protected GET — ACTIVE user in session → 200
    // ------------------------------------------------------------------

    @Test
    fun protectedEndpoint_withActiveUserInSession_returns200() {
        Mockito.`when`(userRepository.findById(PublicId(testUserId))).thenReturn(activeUser())

        mockMvc.perform(
            get("/stub/protected")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, testUserId)
        )
            .andExpect(status().isOk)

        verify(userRepository).findById(PublicId(testUserId))
    }

    // ------------------------------------------------------------------
    // Protected GET — DISABLED user in session → 403
    // ------------------------------------------------------------------

    @Test
    fun protectedEndpoint_withDisabledUserInSession_returns403() {
        Mockito.`when`(userRepository.findById(PublicId(disabledUserId))).thenReturn(disabledUser())

        mockMvc.perform(
            get("/stub/protected")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, disabledUserId)
        )
            .andExpect(status().isForbidden)
    }

    // ------------------------------------------------------------------
    // Protected GET — unknown user in session → 401
    // ------------------------------------------------------------------

    @Test
    fun protectedEndpoint_withUnknownUserInSession_returns401() {
        Mockito.`when`(userRepository.findById(PublicId("usr_nobody"))).thenReturn(null)

        mockMvc.perform(
            get("/stub/protected")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "usr_nobody")
        )
            .andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------
    // Protected GET — malformed session userId → 401
    // ------------------------------------------------------------------

    @Test
    fun protectedEndpoint_withMalformedSessionUserId_returns401() {
        mockMvc.perform(
            get("/stub/protected")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "not_a_valid_id")
        )
            .andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------
    // Public GET — no session → 200
    // ------------------------------------------------------------------

    @Test
    fun publicEndpoint_withoutSession_returns200() {
        mockMvc.perform(
            get("/stub/public")
        )
            .andExpect(status().isOk)
    }

    // ------------------------------------------------------------------
    // Public GET — active session → 200
    // ------------------------------------------------------------------

    @Test
    fun publicEndpoint_withActiveSession_returns200() {
        Mockito.`when`(userRepository.findById(PublicId(testUserId))).thenReturn(activeUser())

        mockMvc.perform(
            get("/stub/public")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, testUserId)
        )
            .andExpect(status().isOk)
    }

    // ------------------------------------------------------------------
    // Protected POST — no session → 401
    // ------------------------------------------------------------------

    @Test
    fun protectedPostEndpoint_withoutSession_returns401() {
        mockMvc.perform(
            post("/stub/protected-post")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"data":"test"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------
    // Protected POST — ACTIVE user in session → 200
    // ------------------------------------------------------------------

    @Test
    fun protectedPostEndpoint_withActiveUserInSession_returns200() {
        Mockito.`when`(userRepository.findById(PublicId(testUserId))).thenReturn(activeUser())

        mockMvc.perform(
            post("/stub/protected-post")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, testUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"data":"test"}""")
        )
            .andExpect(status().isOk)

        verify(userRepository).findById(PublicId(testUserId))
    }

    // ------------------------------------------------------------------
    // X-User-Id header has zero authority — session user is used
    // ------------------------------------------------------------------

    @Test
    fun sessionIdentity_cannotBeOverriddenByXUserIdHeader_returns200ForSessionUser() {
        val otherUserId = "usr_bob"
        val otherUser = User(
            id = PublicId(otherUserId),
            username = "bob",
            displayName = "Bob",
            avatar = null,

            bio = null,

            gender = "secret",

            location = null,

            cover = null,

            status = UserStatus.ACTIVE,
            createdAt = 1_000_000L,
            updatedAt = 1_000_000L
        )

        Mockito.`when`(userRepository.findById(PublicId(testUserId))).thenReturn(activeUser())
        Mockito.`when`(userRepository.findById(PublicId(otherUserId))).thenReturn(otherUser)

        mockMvc.perform(
            get("/stub/protected")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, testUserId)
                .header("X-User-Id", otherUserId)
        )
            .andExpect(status().isOk)

        // UserRepository must be queried with the session user id, not the header
        verify(userRepository).findById(PublicId(testUserId))
        verify(userRepository, never()).findById(PublicId(otherUserId))
    }
}
