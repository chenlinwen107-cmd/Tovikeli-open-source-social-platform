package com.contentplatform.backend.api.authentication

import com.contentplatform.backend.api.comment.CommentController
import com.contentplatform.backend.application.authentication.AuthenticateUserUseCase
import com.contentplatform.backend.application.authentication.InvalidCredentialsException
import com.contentplatform.backend.application.comment.CreateCommentUseCase
import com.contentplatform.backend.application.comment.DeleteCommentUseCase
import com.contentplatform.backend.application.comment.EditCommentUseCase
import com.contentplatform.backend.application.comment.GetCommentRepliesUseCase
import com.contentplatform.backend.application.comment.GetCommentUseCase
import com.contentplatform.backend.application.comment.GetCommentsByContentUseCase
import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentPage
import com.contentplatform.backend.domain.comment.CommentStatus
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Productization P3 integration verification — the complete login / logout
 * session lifecycle across the real HTTP boundary.
 *
 * The whole production authentication path is wired together in one MockMvc
 * context:
 *
 *     POST /api/v1/auth/login     -> AuthenticateUserUseCase -> HttpSession
 *     POST /api/v1/contents/{id}/comments   (protected) -> AuthenticationFilter
 *                                       -> UserRepository -> CurrentCaller
 *                                       -> CommentController
 *     GET  /api/v1/contents/{id}/comments   (public, stays accessible)
 *     POST /api/v1/auth/logout    -> HttpSession.invalidate()
 *
 * Verified contract:
 *  - an authenticated session can log out (204, empty body, no session id)
 *  - logout invalidates the session; the stale session cannot authenticate (401)
 *  - public endpoints remain accessible after logout
 *  - logout without a session is safe, idempotent and creates no session
 *  - logout never authenticates the request as a side effect
 *  - a failed login leaves no authenticated session
 *  - login -> logout -> login again restores authentication
 *  - session identity survives normal subsequent protected requests
 *  - X-User-Id cannot override the session identity, before or after logout
 *  - the session carries only [SessionAttributes.CURRENT_USER_ID] as
 *    application authentication state
 *
 * Scope guard: no database, no Flyway, no Spring Session JDBC filter, no JWT,
 * no OAuth2. Every collaborator behind the HTTP boundary is mocked; this test
 * exercises HTTP + session + filter + controller integration only.
 */
@WebMvcTest(controllers = [AuthController::class, CommentController::class])
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"
    ]
)
class LogoutSessionLifecycleIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    // --- Authentication boundary ---------------------------------------

    @MockitoBean
    private lateinit var authenticateUserUseCase: AuthenticateUserUseCase

    @MockitoBean
    private lateinit var userRepository: UserRepository

    // --- CommentController collaborators -------------------------------

    @MockitoBean
    private lateinit var createCommentUseCase: CreateCommentUseCase

    @MockitoBean
    private lateinit var deleteCommentUseCase: DeleteCommentUseCase

    @MockitoBean
    private lateinit var editCommentUseCase: EditCommentUseCase

    @MockitoBean
    private lateinit var getCommentRepliesUseCase: GetCommentRepliesUseCase

    @MockitoBean
    private lateinit var getCommentUseCase: GetCommentUseCase

    @MockitoBean
    private lateinit var getCommentsByContentUseCase: GetCommentsByContentUseCase

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private val aliceId = "usr_alice"
    private val bobId = "usr_bob"
    private val contentId = "ct_1"

    private val alice = User(
        id = PublicId(aliceId),
        username = "alice",
        displayName = "Alice",
        status = UserStatus.ACTIVE,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L
    )

    private val bob = User(
        id = PublicId(bobId),
        username = "bob",
        displayName = "Bob",
        status = UserStatus.ACTIVE,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L
    )

    private val disabledId = "usr_disabled"

    private val disabledUser = User(
        id = PublicId(disabledId),
        username = "disabled",
        displayName = "Disabled",
        status = UserStatus.DISABLED,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L
    )

    private fun comment(userId: String, text: String): Comment = Comment(
        id = PublicId("cmt_1"),
        userId = PublicId(userId),
        contentId = PublicId(contentId),
        parentCommentId = null,
        text = text,
        status = CommentStatus.ACTIVE,
        createdAt = 1_757_000_000_100L,
        updatedAt = 1_757_000_000_100L
    )

    @BeforeEach
    fun setUp() {
        Mockito.`when`(authenticateUserUseCase.authenticate("alice", "secret123"))
            .thenReturn(alice)
        Mockito.`when`(userRepository.findById(PublicId(aliceId))).thenReturn(alice)
        Mockito.`when`(userRepository.findById(PublicId(bobId))).thenReturn(bob)
    }

    // ------------------------------------------------------------------
    // Request builders
    // ------------------------------------------------------------------

    /** Logs in and returns the established HTTP session. */
    private fun loginSession(
        username: String = "alice",
        password: String = "secret123"
    ): MockHttpSession {
        val result = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"$username","password":"$password"}""")
        )
            .andExpect(status().isOk)
            .andReturn()

        return result.request.getSession(false) as? MockHttpSession
            ?: error("login must establish a server-side session")
    }

    private fun logoutRequest(session: MockHttpSession? = null): MockHttpServletRequestBuilder {
        val builder = post("/api/v1/auth/logout")
        if (session != null) builder.session(session)
        return builder
    }

    private fun protectedCreateRequest(
        session: MockHttpSession? = null,
        text: String = "Hello",
        headers: Map<String, String> = emptyMap()
    ): MockHttpServletRequestBuilder {
        val builder = post("/api/v1/contents/{contentId}/comments", contentId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"text":"$text","parentCommentId":null}""")
        if (session != null) builder.session(session)
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder
    }

    private fun publicListRequest(session: MockHttpSession? = null): MockHttpServletRequestBuilder {
        val builder = get("/api/v1/contents/{contentId}/comments", contentId)
        if (session != null) builder.session(session)
        return builder
    }

    private fun stubCreate(userId: String, text: String) {
        Mockito.`when`(
            createCommentUseCase.create(PublicId(userId), PublicId(contentId), null, text)
        ).thenReturn(comment(userId, text))
    }

    private fun stubEmptyPublicList() {
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))
    }

    // ==================================================================
    // 1. An authenticated session can log out
    // ==================================================================

    @Test
    fun authenticatedSession_canLogout_returns204WithEmptyBody() {
        val session = loginSession()

        val result = mockMvc.perform(logoutRequest(session))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
            .andReturn()

        assertTrue(
            session.isInvalid,
            "logout must invalidate the server-side session, not merely clear an attribute"
        )
        assertFalse(
            result.response.contentAsString.contains(session.id),
            "logout must never expose the session identifier"
        )
    }

    // ==================================================================
    // 2. After logout the stale session can no longer authenticate
    // ==================================================================

    @Test
    fun afterLogout_protectedEndpointRejectsTheStaleSessionWith401() {
        val session = loginSession()

        mockMvc.perform(logoutRequest(session))
            .andExpect(status().isNoContent)

        // The client keeps sending its cookie, but the session is gone server-side.
        mockMvc.perform(protectedCreateRequest(session, "after logout"))
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ==================================================================
    // 3. Public endpoints remain accessible after logout
    // ==================================================================

    @Test
    fun afterLogout_publicEndpointRemainsAccessible() {
        stubEmptyPublicList()
        val session = loginSession()

        mockMvc.perform(logoutRequest(session))
            .andExpect(status().isNoContent)

        mockMvc.perform(publicListRequest(session))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun withoutAnySession_publicEndpointIsAccessible() {
        stubEmptyPublicList()

        mockMvc.perform(publicListRequest())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    // ==================================================================
    // 4. Logout without a session is safe and idempotent
    // ==================================================================

    @Test
    fun logout_withoutAnySession_isSafeAndIdempotent() {
        mockMvc.perform(logoutRequest())
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))

        // Repeated logout stays successful.
        mockMvc.perform(logoutRequest())
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))

        // ...and never grants access to a protected endpoint.
        mockMvc.perform(protectedCreateRequest())
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    @Test
    fun logout_doesNotCreateASessionOrInvokeAuthentication() {
        val result = mockMvc.perform(logoutRequest())
            .andExpect(status().isNoContent)
            .andReturn()

        assertNull(
            result.request.getSession(false),
            "logout must never create a session (getSession(false) only)"
        )
        Mockito.verifyNoInteractions(authenticateUserUseCase)
    }

    @Test
    fun logoutAlreadyExpiredSession_isStillSafe() {
        // A session object that is already invalid models an expired session.
        val expired = MockHttpSession()
        expired.invalidate()

        mockMvc.perform(logoutRequest(expired))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
    }

    // ==================================================================
    // 5. Failed login leaves no authenticated session
    // ==================================================================

    @Test
    fun failedLogin_doesNotEstablishAnAuthenticatedSession() {
        Mockito.`when`(authenticateUserUseCase.authenticate("alice", "wrong"))
            .thenThrow(InvalidCredentialsException())

        val result = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username":"alice","password":"wrong"}""")
        )
            .andExpect(status().isUnauthorized)
            .andReturn()

        assertNull(
            result.request.getSession(false),
            "a rejected login must not create a session"
        )

        mockMvc.perform(protectedCreateRequest())
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ==================================================================
    // 6. login -> logout -> login again
    // ==================================================================

    @Test
    fun login_thenLogout_thenLoginAgain_restoresAuthentication() {
        // --- login #1 --------------------------------------------------
        val firstSession = loginSession()
        stubCreate(aliceId, "one")
        mockMvc.perform(protectedCreateRequest(firstSession, "one"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(aliceId))

        // --- logout ----------------------------------------------------
        mockMvc.perform(logoutRequest(firstSession))
            .andExpect(status().isNoContent)

        mockMvc.perform(protectedCreateRequest(firstSession, "blocked"))
            .andExpect(status().isUnauthorized)

        // --- login #2 (fresh session) ----------------------------------
        val secondSession = loginSession()
        assertNotSame(
            firstSession,
            secondSession,
            "logging in again after logout must establish a fresh session"
        )

        stubCreate(aliceId, "two")
        mockMvc.perform(protectedCreateRequest(secondSession, "two"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(aliceId))
    }

    // ==================================================================
    // 7. Session identity survives normal subsequent protected requests
    // ==================================================================

    @Test
    fun sessionIdentity_survivesSubsequentProtectedRequests() {
        val session = loginSession()
        stubCreate(aliceId, "first")
        stubCreate(aliceId, "second")

        mockMvc.perform(protectedCreateRequest(session, "first"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(aliceId))

        mockMvc.perform(protectedCreateRequest(session, "second"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(aliceId))

        Mockito.verify(userRepository, Mockito.times(2)).findById(PublicId(aliceId))
    }

    // ==================================================================
    // 8. X-User-Id has no authority — before logout
    // ==================================================================

    @Test
    fun xUserId_cannotOverrideSessionIdentityBeforeLogout() {
        val session = loginSession()
        stubCreate(aliceId, "hi")

        mockMvc.perform(
            protectedCreateRequest(session, "hi", mapOf("X-User-Id" to bobId))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(aliceId))

        Mockito.verify(userRepository, Mockito.never()).findById(PublicId(bobId))
        Mockito.verify(createCommentUseCase, Mockito.never())
            .create(PublicId(bobId), PublicId(contentId), null, "hi")
    }

    // ==================================================================
    // 9. X-User-Id cannot resurrect authentication after logout
    // ==================================================================

    @Test
    fun xUserId_cannotResurrectAuthenticationAfterLogout() {
        val session = loginSession()

        mockMvc.perform(logoutRequest(session))
            .andExpect(status().isNoContent)

        mockMvc.perform(
            protectedCreateRequest(session, "zombie", mapOf("X-User-Id" to bobId))
        )
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ==================================================================
    // 10. Session carries only the identity as authentication state
    // ==================================================================

    @Test
    fun sessionCarriesOnlyTheCurrentUserIdAsAuthenticationState() {
        val session = loginSession()

        assertEquals(
            listOf(SessionAttributes.CURRENT_USER_ID),
            session.attributeNames.asSequence().toList(),
            "the session must carry only the current user id, no credential material"
        )
        assertEquals(aliceId, session.getAttribute(SessionAttributes.CURRENT_USER_ID))
    }

    // ==================================================================
    // 11. Documented boundary: a DISABLED session is rejected before logout
    // ==================================================================

    /**
     * Documents the interaction between a session whose user has been disabled
     * AFTER login and the logout endpoint.
     *
     * Because [AuthenticationFilter] rejects a DISABLED session with 403 before
     * any controller runs, logout cannot invalidate such a session. This is the
     * pre-existing P2 filter contract ("DISABLED -> 403") applied uniformly, not
     * a P3 behavior change; it is asserted here so the session lifecycle
     * limitation stays visible rather than accidental.
     */
    @Test
    fun disabledUserSession_isRejectedWith403ByTheFilter_andSessionSurvivesLogoutAttempt() {
        Mockito.`when`(userRepository.findById(PublicId(disabledId))).thenReturn(disabledUser)

        val session = MockHttpSession()
        session.setAttribute(SessionAttributes.CURRENT_USER_ID, disabledId)

        mockMvc.perform(logoutRequest(session))
            .andExpect(status().isForbidden)

        assertFalse(
            session.isInvalid,
            "the filter short-circuits DISABLED sessions before the logout controller runs"
        )
    }

    @Test
    fun afterLogout_theSessionNoLongerCarriesAnyAuthenticationState() {
        val session = loginSession()
        assertEquals(aliceId, session.getAttribute(SessionAttributes.CURRENT_USER_ID))

        mockMvc.perform(logoutRequest(session))
            .andExpect(status().isNoContent)

        assertTrue(session.isInvalid)
        var stillReadable = true
        try {
            session.getAttribute(SessionAttributes.CURRENT_USER_ID)
        } catch (e: IllegalStateException) {
            stillReadable = false
        }
        assertFalse(
            stillReadable,
            "the invalidated session must no longer expose the current user id"
        )
    }
}
