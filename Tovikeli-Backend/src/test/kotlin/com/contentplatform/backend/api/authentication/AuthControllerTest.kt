package com.contentplatform.backend.api.authentication

import com.contentplatform.backend.application.authentication.AuthenticateUserUseCase
import com.contentplatform.backend.application.authentication.DisabledUserAuthenticationException
import com.contentplatform.backend.application.authentication.InvalidCredentialsException
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(
    controllers = [AuthController::class],
    excludeFilters = [
        org.springframework.context.annotation.ComponentScan.Filter(
            type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
            classes = [AuthenticationFilter::class]
        )
    ]
)
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"
    ]
)
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var authenticateUserUseCase: AuthenticateUserUseCase

    private fun user(
        id: String = "usr_alice",
        username: String = "alice"
    ): User = User(
        id = PublicId(id),
        username = username,
        displayName = "Alice",
        status = UserStatus.ACTIVE,
        createdAt = 1000L,
        updatedAt = 2000L
    )

    private fun loginJson(body: String) =
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)

    @Test
    fun login_withValidCredentials_returns200AndCreatesSession() {
        val expected = user()

        Mockito.`when`(
            authenticateUserUseCase.authenticate("alice", "secret123")
        ).thenReturn(expected)

        mockMvc.perform(
            loginJson(
                """{"username":"alice","password":"secret123"}"""
            )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("usr_alice"))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.displayName").value("Alice"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.request()
                    .sessionAttribute(SessionAttributes.CURRENT_USER_ID, "usr_alice")
            )
    }

    @Test
    fun login_trimsUsernameBeforeAuthentication() {
        val expected = user()

        Mockito.`when`(
            authenticateUserUseCase.authenticate("alice", "secret123")
        ).thenReturn(expected)

        mockMvc.perform(
            loginJson(
                """{"username":"   alice   ","password":"secret123"}"""
            )
        )
            .andExpect(status().isOk)

        Mockito.verify(authenticateUserUseCase)
            .authenticate("alice", "secret123")
    }

    @Test
    fun login_missingUsername_returns401WithoutCallingUseCase() {
        mockMvc.perform(
            loginJson(
                """{"password":"secret123"}"""
            )
        )
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(authenticateUserUseCase)
    }

    @Test
    fun login_missingPassword_returns401WithoutCallingUseCase() {
        mockMvc.perform(
            loginJson(
                """{"username":"alice"}"""
            )
        )
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(authenticateUserUseCase)
    }

    @Test
    fun login_wrongCredentials_returns401() {
        Mockito.`when`(
            authenticateUserUseCase.authenticate("alice", "wrong")
        ).thenThrow(InvalidCredentialsException())

        mockMvc.perform(
            loginJson(
                """{"username":"alice","password":"wrong"}"""
            )
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun login_disabledUser_returns403() {
        Mockito.`when`(
            authenticateUserUseCase.authenticate("alice", "secret123")
        ).thenThrow(DisabledUserAuthenticationException())

        mockMvc.perform(
            loginJson(
                """{"username":"alice","password":"secret123"}"""
            )
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun login_responseDoesNotContainPasswordFields() {
        val expected = user()

        Mockito.`when`(
            authenticateUserUseCase.authenticate("alice", "secret123")
        ).thenReturn(expected)

        mockMvc.perform(
            loginJson(
                """{"username":"alice","password":"secret123"}"""
            )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.password_hash").doesNotExist())
    }

    // ==================================================================
    // POST /api/v1/auth/logout  (Productization P3)
    // ==================================================================

    @Test
    fun logout_withExistingSession_returns204AndInvalidatesTheSession() {
        val session = MockHttpSession()
        session.setAttribute(SessionAttributes.CURRENT_USER_ID, "usr_alice")

        mockMvc.perform(
            post("/api/v1/auth/logout").session(session)
        )
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))

        org.junit.jupiter.api.Assertions.assertTrue(
            session.isInvalid,
            "logout must invalidate the server-side session, not merely clear an attribute"
        )
        Mockito.verifyNoInteractions(authenticateUserUseCase)
    }

    @Test
    fun logout_withoutSession_returns204AndCreatesNoSession() {
        val result = mockMvc.perform(
            post("/api/v1/auth/logout")
        )
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
            .andReturn()

        org.junit.jupiter.api.Assertions.assertNull(
            result.request.getSession(false),
            "logout must never create a session"
        )
        Mockito.verifyNoInteractions(authenticateUserUseCase)
    }

    @Test
    fun logout_isRepeatable_returns204EveryTime() {
        val session = MockHttpSession()
        session.setAttribute(SessionAttributes.CURRENT_USER_ID, "usr_alice")

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
            .andExpect(status().isNoContent)

        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isNoContent)
            .andExpect(content().string(""))
    }

    @Test
    fun logout_doesNotExposeSessionIdentifierOrUserDataInBody() {
        val session = MockHttpSession()
        session.setAttribute(SessionAttributes.CURRENT_USER_ID, "usr_alice")

        val result = mockMvc.perform(
            post("/api/v1/auth/logout").session(session)
        )
            .andExpect(status().isNoContent)
            .andReturn()

        val body = result.response.contentAsString
        org.junit.jupiter.api.Assertions.assertTrue(
            body.isEmpty(),
            "logout must return an empty body, but was: $body"
        )
        org.junit.jupiter.api.Assertions.assertFalse(
            body.contains(session.id),
            "logout must never expose the session identifier"
        )
    }
}
