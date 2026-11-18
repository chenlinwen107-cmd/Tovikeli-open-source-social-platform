package com.contentplatform.backend.api.user

import com.contentplatform.backend.application.user.CreateUserUseCase
import com.contentplatform.backend.application.user.GetUserUseCase
import com.contentplatform.backend.application.user.UsernameAlreadyExistsException
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import org.hamcrest.Matchers.nullValue
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MVC slice test for [UserController] (Stage 16).
 *
 * Covers:
 *   POST /api/v1/users           create (validation + 201/400/409)
 *   GET  /api/v1/users/{userId}  detail (200/400/404)
 *
 * Use cases are mocked; no database, no Flyway, no external HTTP.
 */
@WebMvcTest(controllers = [UserController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class UserControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var createUserUseCase: CreateUserUseCase

    @MockitoBean
    private lateinit var getUserUseCase: GetUserUseCase

    @MockitoBean
    private lateinit var updateUserProfileUseCase: com.contentplatform.backend.application.user.UpdateUserProfileUseCase

        @MockitoBean
    private lateinit var authorRepository: com.contentplatform.backend.repository.AuthorRepository

    @MockitoBean
    private lateinit var contentRepository: com.contentplatform.backend.repository.ContentRepository

    @MockitoBean
    private lateinit var followRepository: com.contentplatform.backend.repository.FollowRepository

    @MockitoBean
    private lateinit var interactionHistoryUseCase: com.contentplatform.backend.application.interaction.GetUserInteractionHistoryUseCase

    @MockitoBean
    private lateinit var userRepository: UserRepository

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private fun user(
        id: String,
        username: String,
        displayName: String?,
        status: UserStatus = UserStatus.ACTIVE
    ): User = User(
        id = PublicId(id),
        username = username,
        displayName = displayName,
        avatar = null,

        bio = null,

        gender = "secret",

        location = null,

        cover = null,

        status = status,
        createdAt = 1000L,
        updatedAt = 2000L
    )

    private fun postJson(body: String) =
        post("/api/v1/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    // ------------------------------------------------------------------
    // POST: happy path
    // ------------------------------------------------------------------

    @Test
    fun createUser_whenValid_returns201WithUserResponse() {
        val created = user("usr_alice", "alice", "Alice Example")
        Mockito.`when`(createUserUseCase.create("alice", "secret123", "Alice Example")).thenReturn(created)

        mockMvc.perform(postJson("""{"username":"alice","password":"secret123","displayName":"Alice Example"}"""))
            .andExpect(status().isCreated)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("usr_alice"))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.displayName").value("Alice Example"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").isNumber())
            .andExpect(jsonPath("$.updatedAt").isNumber())

        Mockito.verify(createUserUseCase).create("alice", "secret123", "Alice Example")
    }

    @Test
    fun createUser_withoutDisplayName_returns201WithNullDisplayName() {
        val created = user("usr_alice", "alice", null)
        Mockito.`when`(createUserUseCase.create("alice", "secret123", null)).thenReturn(created)

        mockMvc.perform(postJson("""{"username":"alice","password":"secret123"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("usr_alice"))
            .andExpect(jsonPath("$.displayName").value(nullValue()))
            .andExpect(jsonPath("$.status").value("ACTIVE"))

        Mockito.verify(createUserUseCase).create("alice", "secret123", null)
    }

    @Test
    fun createUser_usernameWithSurroundingWhitespace_isTrimmedBeforeCreate() {
        val created = user("usr_alice", "alice", null)
        Mockito.`when`(createUserUseCase.create("alice", "secret123", null)).thenReturn(created)

        mockMvc.perform(postJson("""{"username":"   alice   ","password":"secret123"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.username").value("alice"))

        Mockito.verify(createUserUseCase).create("alice", "secret123", null)
    }

    // ------------------------------------------------------------------
    // POST: validation failures -> 400, use case never invoked
    // ------------------------------------------------------------------

    private fun neverCreate() {
        Mockito.verifyNoInteractions(createUserUseCase)
    }

    @Test
    fun createUser_whenUsernameMissing_returns400() {
        mockMvc.perform(postJson("""{"displayName":"Alice"}"""))
            .andExpect(status().isBadRequest)
        neverCreate()
    }

    @Test
    fun createUser_whenUsernameEmpty_returns400() {
        mockMvc.perform(postJson("""{"username":""}"""))
            .andExpect(status().isBadRequest)
        neverCreate()
    }

    @Test
    fun createUser_whenUsernameWhitespaceOnly_returns400() {
        mockMvc.perform(postJson("""{"username":"    "}"""))
            .andExpect(status().isBadRequest)
        neverCreate()
    }

    @Test
    fun createUser_whenPasswordMissing_returns400() {
        mockMvc.perform(postJson("""{"username":"alice"}"""))
            .andExpect(status().isBadRequest)
        neverCreate()
    }

    @Test
    fun createUser_whenPasswordEmpty_returns400() {
        mockMvc.perform(postJson("""{"username":"alice","password":""}"""))
            .andExpect(status().isBadRequest)
        neverCreate()
    }

    @Test
    fun createUser_whenPasswordWhitespaceOnly_returns400() {
        mockMvc.perform(postJson("""{"username":"alice","password":"   "}"""))
            .andExpect(status().isBadRequest)
        neverCreate()
    }

    @Test
    fun createUser_whenBodyMalformedJson_returns400() {
        mockMvc.perform(
            post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"username": """)
        )
            .andExpect(status().isBadRequest)
        neverCreate()
    }

    // ------------------------------------------------------------------
    // POST: duplicate username -> 409, empty body
    // ------------------------------------------------------------------

    @Test
    fun createUser_whenUsernameAlreadyExists_returns409() {
        Mockito.`when`(createUserUseCase.create("alice", "secret123", null))
            .thenThrow(UsernameAlreadyExistsException("username already exists: alice"))

        mockMvc.perform(postJson("""{"username":"alice","password":"secret123"}"""))
            .andExpect(status().isConflict)
    }

    @Test
    fun createUser_duplicateCasing_alsoReturns409() {
        Mockito.`when`(createUserUseCase.create("ALICE", "secret123", null))
            .thenThrow(UsernameAlreadyExistsException("username already exists: ALICE"))

        mockMvc.perform(postJson("""{"username":"ALICE","password":"secret123"}"""))
            .andExpect(status().isConflict)
    }

    // ------------------------------------------------------------------
    // GET: detail
    // ------------------------------------------------------------------

    @Test
    fun getUser_whenFound_returns200WithResponseDto() {
        val stored = user("usr_bob", "bob", "Bob Builder", UserStatus.DISABLED)
        Mockito.`when`(getUserUseCase.getById(stored.id)).thenReturn(stored)

        mockMvc.perform(get("/api/v1/users/{userId}", "usr_bob"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("usr_bob"))
            .andExpect(jsonPath("$.username").value("bob"))
            .andExpect(jsonPath("$.displayName").value("Bob Builder"))
            .andExpect(jsonPath("$.status").value("DISABLED"))
            .andExpect(jsonPath("$.createdAt").isNumber())
            .andExpect(jsonPath("$.updatedAt").isNumber())

        Mockito.verify(getUserUseCase).getById(stored.id)
    }

    @Test
    fun getUser_whenAbsent_returns404() {
        Mockito.`when`(getUserUseCase.getById(PublicId("usr_missing"))).thenReturn(null)

        mockMvc.perform(get("/api/v1/users/{userId}", "usr_missing"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun getUser_whenUserIdBlank_returns400() {
        mockMvc.perform(get("/api/v1/users/{userId}", "   "))
            .andExpect(status().isBadRequest)
        Mockito.verifyNoInteractions(getUserUseCase)
    }
}
