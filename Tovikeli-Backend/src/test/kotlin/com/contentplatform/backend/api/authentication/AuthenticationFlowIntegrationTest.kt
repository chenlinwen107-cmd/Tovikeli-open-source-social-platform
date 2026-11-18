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
import com.contentplatform.backend.domain.comment.CommentStatus
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * P2 integration verification — the session-backed authentication closed loop.
 *
 * This is the first test that wires the WHOLE production authentication path
 * together in a single MockMvc context instead of exercising each piece in
 * isolation:
 *
 *     POST /api/v1/auth/login
 *         -> AuthenticateUserUseCase (mocked)
 *         -> server-side HttpSession carries SessionAttributes.CURRENT_USER_ID
 *
 *     POST /api/v1/contents/{contentId}/comments        (a REAL protected endpoint)
 *         -> AuthenticationFilter reads the SAME HttpSession
 *         -> UserRepository.findById -> CurrentCaller
 *         -> CommentController via CallerResolver.require
 *
 * Both [AuthController] and [CommentController] are active controllers, and the
 * production [AuthenticationFilter] is auto-included (it is a `Filter` bean).
 * `spring.datasource.url` is set so the three `@ConditionalOnProperty` beans
 * activate exactly like in production.
 *
 * Scope guard: no database, no Flyway, no Spring Session JDBC filter, no JWT,
 * no OAuth2 — every collaborator behind the HTTP boundary is mocked. This test
 * verifies HTTP + session + filter + controller integration only.
 */
@WebMvcTest(controllers = [AuthController::class, CommentController::class])
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"
    ]
)
class AuthenticationFlowIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    // --- Authentication boundary ---------------------------------------

    @MockitoBean
    private lateinit var authenticateUserUseCase: AuthenticateUserUseCase

    @MockitoBean
    private lateinit var userRepository: UserRepository

    // --- CommentController collaborators (mocked; only Create is exercised) ---

    @MockitoBean
    private lateinit var commentLikeUseCase: com.contentplatform.backend.application.comment.CommentLikeUseCase

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
        avatar = null,

        bio = null,

        gender = "secret",

        location = null,

        cover = null,

        status = UserStatus.ACTIVE,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L
    )

    private val bob = User(
        id = PublicId(bobId),
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

    private fun loginBody(username: String, password: String) =
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$username","password":"$password"}""")

    private fun createCommentBody(text: String) =
        post("/api/v1/contents/{contentId}/comments", contentId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"text":"$text","parentCommentId":null}""")

    /** Performs a login and returns the resulting HTTP session. */
    private fun loginAndCaptureSession(username: String, password: String): MockHttpSession {
        val result = mockMvc.perform(loginBody(username, password))
            .andExpect(status().isOk)
            .andReturn()
        return result.request.getSession(false) as? MockHttpSession
            ?: error("login must create an HTTP session")
    }

    // ==================================================================
    // 1. Closed loop: login -> same session -> real protected endpoint
    // ==================================================================

    @Test
    fun login_thenProtectedRequestWithSameSession_authenticatesAsLoggedInUser() {
        val created = comment(aliceId, "First!")
        Mockito.`when`(
            createCommentUseCase.create(PublicId(aliceId), PublicId(contentId), null, "First!")
        ).thenReturn(created)

        // --- Step 1: login establishes the session identity -------
        val loginResult = mockMvc.perform(loginBody("alice", "secret123"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(aliceId))
            .andExpect(jsonPath("$.username").value("alice"))
            .andReturn()

        val session = loginResult.request.getSession(false) as? MockHttpSession
            ?: error("login must create an HTTP session")
        assertEquals(
            aliceId,
            session.getAttribute(SessionAttributes.CURRENT_USER_ID),
            "login must store the authenticated user's public id in the session"
        )

        // --- Step 2: the SAME session reaches a real protected endpoint ----
        mockMvc.perform(
            createCommentBody("First!").session(session)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("cmt_1"))
            .andExpect(jsonPath("$.userId").value(aliceId))
            .andExpect(jsonPath("$.contentId").value(contentId))
            .andExpect(jsonPath("$.text").value("First!"))

        // --- Step 3: the identity flowed from the session, not the client ----
        Mockito.verify(userRepository).findById(PublicId(aliceId))
        Mockito.verify(createCommentUseCase)
            .create(PublicId(aliceId), PublicId(contentId), null, "First!")
    }

    // ==================================================================
    // 2. Negative control: no login session -> protected endpoint 401
    // ==================================================================

    @Test
    fun protectedRequest_withoutLoginSession_returns401() {
        mockMvc.perform(createCommentBody("Anon"))
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ==================================================================
    // 3. X-User-Id cannot override the session identity
    // ==================================================================

    @Test
    fun xUserIdHeader_cannotOverrideSessionIdentityOnProtectedEndpoint() {
        val created = comment(aliceId, "Hi")
        Mockito.`when`(
            createCommentUseCase.create(PublicId(aliceId), PublicId(contentId), null, "Hi")
        ).thenReturn(created)

        val session = loginAndCaptureSession("alice", "secret123")

        mockMvc.perform(
            createCommentBody("Hi")
                .session(session)
                .header("X-User-Id", bobId)
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(aliceId))

        // The session identity wins; Bob is never looked up nor used.
        Mockito.verify(createCommentUseCase)
            .create(PublicId(aliceId), PublicId(contentId), null, "Hi")
        Mockito.verify(createCommentUseCase, Mockito.never())
            .create(PublicId(bobId), PublicId(contentId), null, "Hi")
        Mockito.verify(userRepository, Mockito.never()).findById(PublicId(bobId))
    }

    // ==================================================================
    // 4. Failed login never produces a session (no access granted)
    // ==================================================================

    @Test
    fun failedLogin_doesNotCreateSession_andProtectedEndpointRemainsUnauthenticated() {
        Mockito.`when`(authenticateUserUseCase.authenticate("alice", "wrong"))
            .thenThrow(InvalidCredentialsException())

        val result = mockMvc.perform(loginBody("alice", "wrong"))
            .andExpect(status().isUnauthorized)
            .andReturn()

        assertNull(
            result.request.getSession(false),
            "a rejected login must not establish a server-side session"
        )

        // With no session, the real protected endpoint is still inaccessible.
        mockMvc.perform(createCommentBody("Should not work"))
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ==================================================================
    // 5. Sanity: the captured session is a real, non-null identity carrier
    // ==================================================================

    @Test
    fun login_createsSessionCarryingOnlyTheUserPublicId() {
        val session = loginAndCaptureSession("alice", "secret123")

        assertNotNull(session.getAttribute(SessionAttributes.CURRENT_USER_ID))
        assertEquals(aliceId, session.getAttribute(SessionAttributes.CURRENT_USER_ID))

        // The session deliberately stores ONLY the identity, never credentials.
        val attributeNames = session.attributeNames.asSequence().toList()
        assertEquals(
            listOf(SessionAttributes.CURRENT_USER_ID),
            attributeNames,
            "the session must carry only the current user id, no credential material"
        )
    }
}
