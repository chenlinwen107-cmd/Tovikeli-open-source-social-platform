package com.contentplatform.backend.api.comment

import com.contentplatform.backend.api.authentication.SessionAttributes
import com.contentplatform.backend.application.comment.CommentContentNotFoundException
import com.contentplatform.backend.application.comment.CommentContentNotInteractableException
import com.contentplatform.backend.application.comment.CommentParentContentMismatchException
import com.contentplatform.backend.application.comment.CommentParentNotFoundException
import com.contentplatform.backend.application.comment.CommentUserNotFoundException
import com.contentplatform.backend.application.comment.CreateCommentUseCase
import com.contentplatform.backend.application.comment.DeleteCommentUseCase
import com.contentplatform.backend.application.comment.EditCommentResult
import com.contentplatform.backend.application.comment.EditCommentUseCase
import com.contentplatform.backend.application.comment.GetCommentRepliesUseCase
import com.contentplatform.backend.application.comment.GetCommentUseCase
import com.contentplatform.backend.application.comment.GetCommentsByContentUseCase
import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentCursor
import com.contentplatform.backend.domain.comment.CommentPage
import com.contentplatform.backend.domain.comment.CommentStatus
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MVC slice test for [CommentController] (Stage 21-A + 21-B + 21-C1 + Stage 37 + Stage 40-C).
 *
 * Covers:
 *   POST   /api/v1/contents/{contentId}/comments       (authenticated)
 *   GET    /api/v1/contents/{contentId}/comments       (public)
 *   PATCH  /api/v1/comments/{commentId}                (authenticated, self-only)
 *   DELETE /api/v1/comments/{commentId}                (authenticated, self-only)
 *
 * Authentication: caller identity is established by the server-side session
 * via the authentication filter (mocked via UserRepository). The request
 * body does NOT contain userId; it was removed in Stage 40-C.
 *
 * Use cases are mocked; no database, no Flyway, no external HTTP.
 * The controller has NO direct dependency on CommentRepository.
 */
@WebMvcTest(controllers = [CommentController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class CommentControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

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

    @MockitoBean
    private lateinit var userRepository: UserRepository

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
    }

    // ------------------------------------------------------------------
    // Fixture helpers
    // ------------------------------------------------------------------

    private val userId = "usr_alice"
    private val contentId = "ct_1"

    private fun comment(
        idValue: String = "cmt_1",
        userIdValue: String = userId,
        contentIdValue: String = contentId,
        parentCommentIdValue: String? = null,
        text: String = "Hello",
        status: CommentStatus = CommentStatus.ACTIVE,
        createdAt: Long = 1_757_000_000_100L,
        updatedAt: Long = 1_757_000_000_100L
    ): Comment = Comment(
        id = PublicId(idValue),
        userId = PublicId(userIdValue),
        contentId = PublicId(contentIdValue),
        parentCommentId = parentCommentIdValue?.let { PublicId(it) },
        text = text,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun json(
        text: String,
        parentCommentId: String? = null
    ): String = buildString {
        append("""{"text":"$text"""")
        if (parentCommentId != null) {
            append(""","parentCommentId":"$parentCommentId"""")
        } else {
            append(""","parentCommentId":null""")
        }
        append("}")
    }

    // ==================================================================
    // POST /api/v1/contents/{contentId}/comments  (Stage 21-A + 40-C)
    // ==================================================================

    // ------------------------------------------------------------------
    // 1. Valid top-level comment -> 201
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenValidTopLevel_returns201() {
        val c = comment()
        Mockito.`when`(createCommentUseCase.create(
            PublicId(userId), PublicId(contentId), null, "Hello"
        )).thenReturn(c)

        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Hello"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("cmt_1"))
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.contentId").value(contentId))
            .andExpect(jsonPath("$.parentCommentId").isEmpty())
            .andExpect(jsonPath("$.text").value("Hello"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").isNumber())
            .andExpect(jsonPath("$.updatedAt").isNumber())
    }

    // ------------------------------------------------------------------
    // 2. Valid reply -> 201
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenValidReply_returns201() {
        val parentId = "cmt_parent"
        val c = comment(parentCommentIdValue = parentId, text = "Reply")
        Mockito.`when`(createCommentUseCase.create(
            PublicId(userId), PublicId(contentId), PublicId(parentId), "Reply"
        )).thenReturn(c)

        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Reply", parentId))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("cmt_1"))
            .andExpect(jsonPath("$.parentCommentId").value(parentId))
            .andExpect(jsonPath("$.text").value("Reply"))
    }

    // ------------------------------------------------------------------
    // 3. Missing session -> 401, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenUserIdBlank_returns401() {
        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "   ")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Hello"))
        )
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    @Test
    fun createComment_whenUserIdNull_returns401() {
        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Hello","parentCommentId":null}""")
        )
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 4. Invalid contentId (blank) -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenContentIdBlank_returns400() {
        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", "   ")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Hello"))
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 5. Blank text -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenTextBlank_returns400() {
        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(""))
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    @Test
    fun createComment_whenTextNull_returns400() {
        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":null,"parentCommentId":null}""")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 6. Whitespace-only text -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenTextWhitespaceOnly_returns400() {
        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("   \t\n  "))
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 7. Invalid parentCommentId (blank) -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenParentCommentIdBlank_returns400() {
        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Hello","parentCommentId":"   "}""")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 8. Missing User -> 404
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenUserNotFound_returns404() {
        val missingUserId = "usr_missing"
        Mockito.`when`(userRepository.findById(PublicId(missingUserId))).thenReturn(null)

        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, missingUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Hello"))
        )
            .andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------
    // 9. Missing Content -> 404
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenContentNotFound_returns404() {
        val missingContentId = "ct_missing"
        Mockito.`when`(createCommentUseCase.create(
            PublicId(userId), PublicId(missingContentId), null, "Hello"
        )).thenThrow(CommentContentNotFoundException("Content ct_missing does not exist"))

        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", missingContentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Hello"))
        )
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 10. Missing Parent Comment -> 404
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenParentNotFound_returns404() {
        val missingParentId = "cmt_missing"
        Mockito.`when`(createCommentUseCase.create(
            PublicId(userId), PublicId(contentId), PublicId(missingParentId), "Reply"
        )).thenThrow(CommentParentNotFoundException("Parent comment cmt_missing does not exist"))

        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Reply", missingParentId))
        )
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 11. Non-interactable Content -> 409
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenContentNotInteractable_returns409() {
        Mockito.`when`(createCommentUseCase.create(
            PublicId(userId), PublicId(contentId), null, "Hello"
        )).thenThrow(CommentContentNotInteractableException(
            "Content ct_1 is not publicly interactable"
        ))

        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Hello"))
        )
            .andExpect(status().isConflict)
    }

    // ------------------------------------------------------------------
    // 12. Parent belongs to another Content -> 409
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenParentContentMismatch_returns409() {
        val parentId = "cmt_parent"
        Mockito.`when`(createCommentUseCase.create(
            PublicId(userId), PublicId(contentId), PublicId(parentId), "Reply"
        )).thenThrow(CommentParentContentMismatchException(
            "Parent comment cmt_parent belongs to content ct_other, not ct_1"
        ))

        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Reply", parentId))
        )
            .andExpect(status().isConflict)
    }

    // ------------------------------------------------------------------
    // Missing request body -> 400
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenMissingBody_returns400() {
        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ------------------------------------------------------------------
    // Malformed JSON -> 400
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenMalformedJson_returns400() {
        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("not json")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ------------------------------------------------------------------
    // Empty parentCommentId string -> 400 (blank is invalid)
    // ------------------------------------------------------------------

    @Test
    fun createComment_whenParentCommentIdEmptyString_returns400() {
        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Hello","parentCommentId":""}""")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createCommentUseCase)
    }

    // ------------------------------------------------------------------
    // Response fields are correct
    // ------------------------------------------------------------------

    @Test
    fun createComment_returnsAllCommentResponseFields() {
        val c = comment(
            idValue = "cmt_xyz",
            userIdValue = "usr_bob",
            contentIdValue = "ct_content1",
            parentCommentIdValue = "cmt_parent1",
            text = "Detailed reply",
            status = CommentStatus.ACTIVE,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_500L
        )
        Mockito.`when`(createCommentUseCase.create(
            PublicId(userId), PublicId("ct_content1"), PublicId("cmt_parent1"), "Detailed reply"
        )).thenReturn(c)

        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", "ct_content1")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Detailed reply", "cmt_parent1"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("cmt_xyz"))
            .andExpect(jsonPath("$.userId").value("usr_bob"))
            .andExpect(jsonPath("$.contentId").value("ct_content1"))
            .andExpect(jsonPath("$.parentCommentId").value("cmt_parent1"))
            .andExpect(jsonPath("$.text").value("Detailed reply"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").value(1_700_000_000_000L))
            .andExpect(jsonPath("$.updatedAt").value(1_700_000_000_500L))
    }

    // ------------------------------------------------------------------
    // Use case is called with correct arguments
    // ------------------------------------------------------------------

    @Test
    fun createComment_forwardsCorrectArgumentsToUseCase() {
        val c = comment()
        Mockito.`when`(createCommentUseCase.create(
            PublicId(userId), PublicId(contentId), null, "Hello"
        )).thenReturn(c)

        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Hello"))
        )
            .andExpect(status().isCreated)

        Mockito.verify(createCommentUseCase).create(
            PublicId(userId), PublicId(contentId), null, "Hello"
        )
    }

    // ------------------------------------------------------------------
    // Controller has no direct CommentRepository dependency
    // ------------------------------------------------------------------

    @Test
    fun createComment_controllerHasNoRepositoryDependency() {
        // This test verifies at compile time that CommentController's
        // constructor does not accept CommentRepository. The test class
        // would fail to compile if CommentRepository were injected.
        val c = comment()
        Mockito.`when`(createCommentUseCase.create(
            PublicId(userId), PublicId(contentId), null, "Hello"
        )).thenReturn(c)

        mockMvc.perform(
            post("/api/v1/contents/{contentId}/comments", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("Hello"))
        )
            .andExpect(status().isCreated)
    }

    // ==================================================================
    // DELETE /api/v1/comments/{commentId}  (Stage 21-B + 40-C)
    // ==================================================================

    private val commentId = "cmt_1"

    // ------------------------------------------------------------------
    // 1. Owner deletes ACTIVE comment -> 204
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_whenOwnerDeletesActiveComment_returns204() {
        val c = comment(status = CommentStatus.ACTIVE)
        Mockito.`when`(getCommentUseCase.get(PublicId(commentId))).thenReturn(c)
        Mockito.`when`(deleteCommentUseCase.delete(PublicId(commentId), PublicId(userId)))
            .thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
        )
            .andExpect(status().isNoContent)
    }

    // ------------------------------------------------------------------
    // 2. Repeated delete -> 204
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_whenRepeatedDelete_returns204() {
        val c = comment(status = CommentStatus.DELETED)
        Mockito.`when`(getCommentUseCase.get(PublicId(commentId))).thenReturn(c)
        Mockito.`when`(deleteCommentUseCase.delete(PublicId(commentId), PublicId(userId)))
            .thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
        )
            .andExpect(status().isNoContent)
    }

    // ------------------------------------------------------------------
    // 3. Missing comment -> 204
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_whenCommentNotFound_returns204() {
        Mockito.`when`(getCommentUseCase.get(PublicId(commentId))).thenReturn(null)
        Mockito.`when`(deleteCommentUseCase.delete(PublicId(commentId), PublicId(userId)))
            .thenReturn(false)

        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
        )
            .andExpect(status().isNoContent)
    }

    // ------------------------------------------------------------------
    // 4. Already DELETED comment -> 204
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_whenAlreadyDeleted_returns204() {
        val c = comment(status = CommentStatus.DELETED)
        Mockito.`when`(getCommentUseCase.get(PublicId(commentId))).thenReturn(c)
        Mockito.`when`(deleteCommentUseCase.delete(PublicId(commentId), PublicId(userId)))
            .thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
        )
            .andExpect(status().isNoContent)
    }

    // ------------------------------------------------------------------
    // 5. Missing User -> 404
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_whenUserNotFound_returns404() {
        val missingUserId = "usr_missing"
        Mockito.`when`(userRepository.findById(PublicId(missingUserId))).thenReturn(null)

        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, missingUserId)
        )
            .andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------
    // 6. Missing session -> 401
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_whenUserIdMissing_returns401() {
        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", commentId)
        )
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(deleteCommentUseCase)
        Mockito.verifyNoInteractions(getCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 7. Blank session userId -> 401
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_whenUserIdBlank_returns401() {
        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "   ")
        )
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(deleteCommentUseCase)
        Mockito.verifyNoInteractions(getCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 8. Empty session userId -> 401
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_whenUserIdEmpty_returns401() {
        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "")
        )
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(deleteCommentUseCase)
        Mockito.verifyNoInteractions(getCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 9. Blank commentId -> 400
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_whenCommentIdBlank_returns400() {
        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", "   ")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(deleteCommentUseCase)
        Mockito.verifyNoInteractions(getCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 10. Invalid commentId -> 400
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_whenCommentIdEmpty_returns400() {
        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", "")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
        )
            .andExpect(status().isNotFound)

        Mockito.verifyNoInteractions(deleteCommentUseCase)
        Mockito.verifyNoInteractions(getCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 11. Non-owner -> 403
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_whenNotOwner_returns403() {
        val c = comment(status = CommentStatus.ACTIVE, userIdValue = "usr_other")
        Mockito.`when`(getCommentUseCase.get(PublicId(commentId))).thenReturn(c)
        Mockito.`when`(deleteCommentUseCase.delete(PublicId(commentId), PublicId(userId)))
            .thenReturn(false)

        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
        )
            .andExpect(status().isForbidden)
    }

    // ------------------------------------------------------------------
    // 12. Controller has no Repository dependency
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_controllerHasNoRepositoryDependency() {
        // This test verifies at compile time that CommentController's
        // constructor does not accept CommentRepository. The test class
        // would fail to compile if CommentRepository were injected.
        val c = comment(status = CommentStatus.ACTIVE)
        Mockito.`when`(getCommentUseCase.get(PublicId(commentId))).thenReturn(c)
        Mockito.`when`(deleteCommentUseCase.delete(PublicId(commentId), PublicId(userId)))
            .thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
        )
            .andExpect(status().isNoContent)
    }

    // ------------------------------------------------------------------
    // 13. Controller delegates to DeleteCommentUseCase
    // ------------------------------------------------------------------

    @Test
    fun deleteComment_delegatesToDeleteCommentUseCase() {
        val c = comment(status = CommentStatus.ACTIVE)
        Mockito.`when`(getCommentUseCase.get(PublicId(commentId))).thenReturn(c)
        Mockito.`when`(deleteCommentUseCase.delete(PublicId(commentId), PublicId(userId)))
            .thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
        )
            .andExpect(status().isNoContent)

        Mockito.verify(deleteCommentUseCase).delete(PublicId(commentId), PublicId(userId))
    }

    // ==================================================================
    // PATCH /api/v1/comments/{commentId}  (Stage 37 + 40-C)
    // ==================================================================

    // ------------------------------------------------------------------
    // 1. Owner edits ACTIVE comment -> 200
    // ------------------------------------------------------------------

    @Test
    fun editComment_whenOwnerEditsActiveComment_returns200() {
        val updated = comment(text = "Updated text", updatedAt = 1_757_000_000_999L)
        Mockito.`when`(editCommentUseCase.edit(PublicId(commentId), PublicId(userId), "Updated text"))
            .thenReturn(EditCommentResult.Success(updated))

        mockMvc.perform(
            patch("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Updated text"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(commentId))
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.text").value("Updated text"))
    }

    // ------------------------------------------------------------------
    // 2. Unknown comment -> 404
    // ------------------------------------------------------------------

    @Test
    fun editComment_whenCommentNotFound_returns404() {
        Mockito.`when`(editCommentUseCase.edit(PublicId(commentId), PublicId(userId), "New text"))
            .thenReturn(EditCommentResult.NotFound)

        mockMvc.perform(
            patch("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"New text"}""")
        )
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 3. Non-owner -> 403
    // ------------------------------------------------------------------

    @Test
    fun editComment_whenNotOwner_returns403() {
        Mockito.`when`(editCommentUseCase.edit(PublicId(commentId), PublicId(userId), "New text"))
            .thenReturn(EditCommentResult.Forbidden)

        mockMvc.perform(
            patch("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"New text"}""")
        )
            .andExpect(status().isForbidden)
    }

    // ------------------------------------------------------------------
    // 4. Deleted comment -> 422
    // ------------------------------------------------------------------

    @Test
    fun editComment_whenCommentDeleted_returns422() {
        Mockito.`when`(editCommentUseCase.edit(PublicId(commentId), PublicId(userId), "New text"))
            .thenReturn(EditCommentResult.Rejected)

        mockMvc.perform(
            patch("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"New text"}""")
        )
            .andExpect(@Suppress("DEPRECATION") status().isUnprocessableEntity)
    }

    // ------------------------------------------------------------------
    // 5. Blank commentId -> 400
    // ------------------------------------------------------------------

    @Test
    fun editComment_whenCommentIdBlank_returns400() {
        mockMvc.perform(
            patch("/api/v1/comments/{commentId}", "   ")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"New text"}""")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(editCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 6. Missing session -> 401
    // ------------------------------------------------------------------

    @Test
    fun editComment_whenUserIdMissing_returns401() {
        mockMvc.perform(
            patch("/api/v1/comments/{commentId}", commentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"New text"}""")
        )
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(editCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 7. Blank session userId -> 401
    // ------------------------------------------------------------------

    @Test
    fun editComment_whenUserIdBlank_returns401() {
        mockMvc.perform(
            patch("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "   ")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"New text"}""")
        )
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(editCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 8. Blank text -> 400
    // ------------------------------------------------------------------

    @Test
    fun editComment_whenTextBlank_returns400() {
        mockMvc.perform(
            patch("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"   "}""")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(editCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 9. Null text -> 400
    // ------------------------------------------------------------------

    @Test
    fun editComment_whenTextNull_returns400() {
        mockMvc.perform(
            patch("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":null}""")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(editCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 10. Missing request body -> 400
    // ------------------------------------------------------------------

    @Test
    fun editComment_whenMissingBody_returns400() {
        mockMvc.perform(
            patch("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(editCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 11. Malformed JSON -> 400
    // ------------------------------------------------------------------

    @Test
    fun editComment_whenMalformedJson_returns400() {
        mockMvc.perform(
            patch("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("not json")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(editCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 12. Controller delegates to EditCommentUseCase
    // ------------------------------------------------------------------

    @Test
    fun editComment_delegatesToEditCommentUseCase() {
        val updated = comment(text = "Updated")
        Mockito.`when`(editCommentUseCase.edit(PublicId(commentId), PublicId(userId), "Updated"))
            .thenReturn(EditCommentResult.Success(updated))

        mockMvc.perform(
            patch("/api/v1/comments/{commentId}", commentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"text":"Updated"}""")
        )
            .andExpect(status().isOk)

        Mockito.verify(editCommentUseCase).edit(PublicId(commentId), PublicId(userId), "Updated")
    }

    // ==================================================================
    // GET /api/v1/contents/{contentId}/comments  (Stage 21-C1)
    // ==================================================================

    // ------------------------------------------------------------------
    // 1. Returns list of comments -> 200
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_whenCommentsExist_returns200() {
        val c1 = comment(idValue = "cmt_1", text = "First", createdAt = 1_757_000_000_100L)
        val c2 = comment(idValue = "cmt_2", text = "Second", createdAt = 1_757_000_000_200L)
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 20, null))
            .thenReturn(CommentPage(items = listOf(c1, c2), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("cmt_1"))
            .andExpect(jsonPath("$[0].text").value("First"))
            .andExpect(jsonPath("$[1].id").value("cmt_2"))
            .andExpect(jsonPath("$[1].text").value("Second"))
    }

    // ------------------------------------------------------------------
    // 2. Custom limit -> 200
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_whenCustomLimit_returns200() {
        val c = comment(idValue = "cmt_1", text = "Only one")
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 5, null))
            .thenReturn(CommentPage(items = listOf(c), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
                .param("limit", "5")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("cmt_1"))

        Mockito.verify(getCommentsByContentUseCase).getByContent(PublicId(contentId), 5, null)
    }

    // ------------------------------------------------------------------
    // 3. Empty list -> 200 []
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_whenNoComments_returns200Empty() {
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // ------------------------------------------------------------------
    // 4. Non-existing content -> 200 []
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_whenContentNotFound_returns200Empty() {
        val missingContentId = "ct_missing"
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(missingContentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", missingContentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // ------------------------------------------------------------------
    // 5. Ordered by createdAt ASC
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_returnsOrderedByCreatedAt() {
        val c1 = comment(idValue = "cmt_1", createdAt = 1_757_000_000_100L)
        val c2 = comment(idValue = "cmt_2", createdAt = 1_757_000_000_200L)
        val c3 = comment(idValue = "cmt_3", createdAt = 1_757_000_000_300L)
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 20, null))
            .thenReturn(CommentPage(items = listOf(c1, c2, c3), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].createdAt").value(1_757_000_000_100L))
            .andExpect(jsonPath("$[1].createdAt").value(1_757_000_000_200L))
            .andExpect(jsonPath("$[2].createdAt").value(1_757_000_000_300L))
    }

    // ------------------------------------------------------------------
    // 6. Ordered by id ASC (same createdAt)
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_returnsOrderedByIdWhenCreatedAtEqual() {
        val c1 = comment(idValue = "cmt_a", createdAt = 1_757_000_000_100L)
        val c2 = comment(idValue = "cmt_b", createdAt = 1_757_000_000_100L)
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 20, null))
            .thenReturn(CommentPage(items = listOf(c1, c2), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("cmt_a"))
            .andExpect(jsonPath("$[1].id").value("cmt_b"))
    }

    // ------------------------------------------------------------------
    // 7. Only ACTIVE comments in response
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_returnsAllResponseFields() {
        val c = comment(
            idValue = "cmt_xyz",
            userIdValue = "usr_bob",
            contentIdValue = "ct_content1",
            parentCommentIdValue = "cmt_parent1",
            text = "Detailed comment",
            status = CommentStatus.ACTIVE,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_500L
        )
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 20, null))
            .thenReturn(CommentPage(items = listOf(c), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("cmt_xyz"))
            .andExpect(jsonPath("$[0].userId").value("usr_bob"))
            .andExpect(jsonPath("$[0].contentId").value("ct_content1"))
            .andExpect(jsonPath("$[0].parentCommentId").value("cmt_parent1"))
            .andExpect(jsonPath("$[0].text").value("Detailed comment"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].createdAt").value(1_700_000_000_000L))
            .andExpect(jsonPath("$[0].updatedAt").value(1_700_000_000_500L))
    }

    // ------------------------------------------------------------------
    // 8. Blank contentId -> 400
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_whenContentIdBlank_returns400() {
        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", "   ")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentsByContentUseCase)
    }

    // ------------------------------------------------------------------
    // 9. Empty contentId -> 400
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_whenContentIdEmpty_returns400() {
        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", "")
        )
            .andExpect(status().isNotFound)

        Mockito.verifyNoInteractions(getCommentsByContentUseCase)
    }

    // ------------------------------------------------------------------
    // 10. Negative limit -> 400
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_whenNegativeLimit_returns400() {
        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
                .param("limit", "-1")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentsByContentUseCase)
    }

    // ------------------------------------------------------------------
    // 11. Limit > 100 -> 400
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_whenLimitExceeds100_returns400() {
        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
                .param("limit", "101")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentsByContentUseCase)
    }

    // ------------------------------------------------------------------
    // 12. Limit = 0 -> 200 [] (no use case invocation)
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_whenLimitIsZero_returns200Empty() {
        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
                .param("limit", "0")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))

        Mockito.verifyNoInteractions(getCommentsByContentUseCase)
    }

    // ------------------------------------------------------------------
    // 13. Limit = 100 -> 200
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_whenLimitIs100_returns200() {
        val c = comment()
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 100, null))
            .thenReturn(CommentPage(items = listOf(c), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
                .param("limit", "100")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    // ------------------------------------------------------------------
    // 14. Non-numeric limit -> 400
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_whenNonNumericLimit_returns400() {
        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
                .param("limit", "abc")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentsByContentUseCase)
    }

    // ------------------------------------------------------------------
    // 15. Default limit is 20
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_defaultLimitIs20() {
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
        )
            .andExpect(status().isOk)

        Mockito.verify(getCommentsByContentUseCase).getByContent(PublicId(contentId), 20, null)
    }

    // ------------------------------------------------------------------
    // 16. Controller has no Repository dependency
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_controllerHasNoRepositoryDependency() {
        // This test verifies at compile time that CommentController's
        // constructor does not accept CommentRepository. The test class
        // would fail to compile if CommentRepository were injected.
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
        )
            .andExpect(status().isOk)
    }

    // ------------------------------------------------------------------
    // 17. Controller delegates to GetCommentsByContentUseCase
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_delegatesToGetCommentsByContentUseCase() {
        val c = comment()
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 20, null))
            .thenReturn(CommentPage(items = listOf(c), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
        )
            .andExpect(status().isOk)

        Mockito.verify(getCommentsByContentUseCase).getByContent(PublicId(contentId), 20, null)
    }

    // ------------------------------------------------------------------
    // 18. Non-existing content returns 200 [] (no 404)
    // ------------------------------------------------------------------

    @Test
    fun getCommentsByContent_nonExistingContent_doesNotReturn404() {
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    // ==================================================================
    // GET /api/v1/comments/{commentId}  (Stage 21-C2)
    // ==================================================================

    // ------------------------------------------------------------------
    // 1. ACTIVE comment exists -> 200
    // ------------------------------------------------------------------

    @Test
    fun getComment_whenActiveCommentExists_returns200() {
        val c = comment(status = CommentStatus.ACTIVE)
        Mockito.`when`(getCommentUseCase.get(PublicId(commentId))).thenReturn(c)

        mockMvc.perform(
            get("/api/v1/comments/{commentId}", commentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(commentId))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }

    // ------------------------------------------------------------------
    // 2. DELETED comment exists -> 200
    // ------------------------------------------------------------------

    @Test
    fun getComment_whenDeletedCommentExists_returns200() {
        val c = comment(status = CommentStatus.DELETED)
        Mockito.`when`(getCommentUseCase.get(PublicId(commentId))).thenReturn(c)

        mockMvc.perform(
            get("/api/v1/comments/{commentId}", commentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(commentId))
            .andExpect(jsonPath("$.status").value("DELETED"))
    }

    // ------------------------------------------------------------------
    // 3. Comment does not exist -> 404
    // ------------------------------------------------------------------

    @Test
    fun getComment_whenCommentNotFound_returns404() {
        Mockito.`when`(getCommentUseCase.get(PublicId(commentId))).thenReturn(null)

        mockMvc.perform(
            get("/api/v1/comments/{commentId}", commentId)
        )
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // 4. Blank commentId -> 400
    // ------------------------------------------------------------------

    @Test
    fun getComment_whenCommentIdBlank_returns400() {
        mockMvc.perform(
            get("/api/v1/comments/{commentId}", "   ")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentUseCase)
    }

    @Test
    fun getComment_whenCommentIdEmpty_returns400() {
        mockMvc.perform(
            get("/api/v1/comments/{commentId}", "")
        )
            .andExpect(status().isNotFound)

        Mockito.verifyNoInteractions(getCommentUseCase)
    }

    // ------------------------------------------------------------------
    // 5. Response fields are correct
    // ------------------------------------------------------------------

    @Test
    fun getComment_returnsAllCommentResponseFields() {
        val c = comment(
            idValue = "cmt_xyz",
            userIdValue = "usr_bob",
            contentIdValue = "ct_content1",
            parentCommentIdValue = "cmt_parent1",
            text = "Detailed comment",
            status = CommentStatus.ACTIVE,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_500L
        )
        Mockito.`when`(getCommentUseCase.get(PublicId("cmt_xyz"))).thenReturn(c)

        mockMvc.perform(
            get("/api/v1/comments/{commentId}", "cmt_xyz")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("cmt_xyz"))
            .andExpect(jsonPath("$.userId").value("usr_bob"))
            .andExpect(jsonPath("$.contentId").value("ct_content1"))
            .andExpect(jsonPath("$.parentCommentId").value("cmt_parent1"))
            .andExpect(jsonPath("$.text").value("Detailed comment"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").value(1_700_000_000_000L))
            .andExpect(jsonPath("$.updatedAt").value(1_700_000_000_500L))
    }

    // ------------------------------------------------------------------
    // 6. Controller delegates to GetCommentUseCase
    // ------------------------------------------------------------------

    @Test
    fun getComment_delegatesToGetCommentUseCase() {
        val c = comment()
        Mockito.`when`(getCommentUseCase.get(PublicId(commentId))).thenReturn(c)

        mockMvc.perform(
            get("/api/v1/comments/{commentId}", commentId)
        )
            .andExpect(status().isOk)

        Mockito.verify(getCommentUseCase).get(PublicId(commentId))
    }

    // ------------------------------------------------------------------
    // 7. Controller has no Repository dependency
    // ------------------------------------------------------------------

    @Test
    fun getComment_controllerHasNoRepositoryDependency() {
        // This test verifies at compile time that CommentController's
        // constructor does not accept CommentRepository. The test class
        // would fail to compile if CommentRepository were injected.
        val c = comment()
        Mockito.`when`(getCommentUseCase.get(PublicId(commentId))).thenReturn(c)

        mockMvc.perform(
            get("/api/v1/comments/{commentId}", commentId)
        )
            .andExpect(status().isOk)
    }

    // ==================================================================
    // GET /api/v1/comments/{commentId}/replies  (Stage 21-C2)
    // ==================================================================

    private val parentCommentId = "cmt_parent"

    // ------------------------------------------------------------------
    // 1. Happy path -> 200
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_whenRepliesExist_returns200() {
        val r1 = comment(idValue = "cmt_reply1", parentCommentIdValue = parentCommentId, text = "Reply 1", createdAt = 1_757_000_000_100L)
        val r2 = comment(idValue = "cmt_reply2", parentCommentIdValue = parentCommentId, text = "Reply 2", createdAt = 1_757_000_000_200L)
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 20, null))
            .thenReturn(CommentPage(items = listOf(r1, r2), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("cmt_reply1"))
            .andExpect(jsonPath("$[0].text").value("Reply 1"))
            .andExpect(jsonPath("$[1].id").value("cmt_reply2"))
            .andExpect(jsonPath("$[1].text").value("Reply 2"))
    }

    // ------------------------------------------------------------------
    // 2. Empty list -> 200 []
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_whenNoReplies_returns200Empty() {
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // ------------------------------------------------------------------
    // 3. Nonexistent parent comment -> 200 []
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_whenParentNotFound_returns200Empty() {
        val missingParentId = "cmt_missing"
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(missingParentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", missingParentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // ------------------------------------------------------------------
    // 4. Deleted parent comment -> 200 []
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_whenParentDeleted_returns200Empty() {
        val deletedParentId = "cmt_deleted_parent"
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(deletedParentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", deletedParentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    // ------------------------------------------------------------------
    // 5. Only ACTIVE direct replies are returned (no nested)
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_returnsOnlyActiveDirectReplies() {
        // The use case/repository only returns ACTIVE direct replies.
        // This test verifies the controller correctly projects them.
        val r1 = comment(idValue = "cmt_reply1", parentCommentIdValue = parentCommentId, status = CommentStatus.ACTIVE)
        val r2 = comment(idValue = "cmt_reply2", parentCommentIdValue = parentCommentId, status = CommentStatus.DELETED)
        // Use case only returns ACTIVE (r1)
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 20, null))
            .thenReturn(CommentPage(items = listOf(r1), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("cmt_reply1"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
    }

    // ------------------------------------------------------------------
    // 6. Ordering: createdAt ASC, id ASC
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_returnsOrderedByCreatedAt() {
        val r1 = comment(idValue = "cmt_1", parentCommentIdValue = parentCommentId, createdAt = 1_757_000_000_100L)
        val r2 = comment(idValue = "cmt_2", parentCommentIdValue = parentCommentId, createdAt = 1_757_000_000_200L)
        val r3 = comment(idValue = "cmt_3", parentCommentIdValue = parentCommentId, createdAt = 1_757_000_000_300L)
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 20, null))
            .thenReturn(CommentPage(items = listOf(r1, r2, r3), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].createdAt").value(1_757_000_000_100L))
            .andExpect(jsonPath("$[1].createdAt").value(1_757_000_000_200L))
            .andExpect(jsonPath("$[2].createdAt").value(1_757_000_000_300L))
    }

    @Test
    fun getCommentReplies_returnsOrderedByIdWhenCreatedAtEqual() {
        val r1 = comment(idValue = "cmt_a", parentCommentIdValue = parentCommentId, createdAt = 1_757_000_000_100L)
        val r2 = comment(idValue = "cmt_b", parentCommentIdValue = parentCommentId, createdAt = 1_757_000_000_100L)
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 20, null))
            .thenReturn(CommentPage(items = listOf(r1, r2), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("cmt_a"))
            .andExpect(jsonPath("$[1].id").value("cmt_b"))
    }

    // ------------------------------------------------------------------
    // 7. Response fields
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_returnsAllCommentResponseFields() {
        val r = comment(
            idValue = "cmt_reply_xyz",
            userIdValue = "usr_bob",
            contentIdValue = "ct_content1",
            parentCommentIdValue = parentCommentId,
            text = "Detailed reply",
            status = CommentStatus.ACTIVE,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_500L
        )
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 20, null))
            .thenReturn(CommentPage(items = listOf(r), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value("cmt_reply_xyz"))
            .andExpect(jsonPath("$[0].userId").value("usr_bob"))
            .andExpect(jsonPath("$[0].contentId").value("ct_content1"))
            .andExpect(jsonPath("$[0].parentCommentId").value(parentCommentId))
            .andExpect(jsonPath("$[0].text").value("Detailed reply"))
            .andExpect(jsonPath("$[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$[0].createdAt").value(1_700_000_000_000L))
            .andExpect(jsonPath("$[0].updatedAt").value(1_700_000_000_500L))
    }

    // ------------------------------------------------------------------
    // 8. Default limit = 20
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_defaultLimitIs20() {
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
        )
            .andExpect(status().isOk)

        Mockito.verify(getCommentRepliesUseCase).getReplies(PublicId(parentCommentId), 20, null)
    }

    // ------------------------------------------------------------------
    // 9. Limit = 0 -> 200 [] (no use case invocation)
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_whenLimitIsZero_returns200Empty() {
        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
                .param("limit", "0")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))

        Mockito.verifyNoInteractions(getCommentRepliesUseCase)
    }

    // ------------------------------------------------------------------
    // 10. Limit = 100 -> 200
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_whenLimitIs100_returns200() {
        val r = comment()
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 100, null))
            .thenReturn(CommentPage(items = listOf(r), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
                .param("limit", "100")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    // ------------------------------------------------------------------
    // 11. Limit < 0 -> 400
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_whenNegativeLimit_returns400() {
        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
                .param("limit", "-1")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentRepliesUseCase)
    }

    // ------------------------------------------------------------------
    // 12. Limit > 100 -> 400
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_whenLimitExceeds100_returns400() {
        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
                .param("limit", "101")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentRepliesUseCase)
    }

    // ------------------------------------------------------------------
    // 13. Non-numeric limit -> 400
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_whenNonNumericLimit_returns400() {
        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
                .param("limit", "abc")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentRepliesUseCase)
    }

    // ------------------------------------------------------------------
    // 14. Blank commentId -> 400
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_whenCommentIdBlank_returns400() {
        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", "   ")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentRepliesUseCase)
    }

    @Test
    fun getCommentReplies_whenCommentIdEmpty_returns400() {
        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", "")
        )
            .andExpect(status().isNotFound)

        Mockito.verifyNoInteractions(getCommentRepliesUseCase)
    }

    // ------------------------------------------------------------------
    // 15. Controller has no Repository dependency
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_controllerHasNoRepositoryDependency() {
        // This test verifies at compile time that CommentController's
        // constructor does not accept CommentRepository. The test class
        // would fail to compile if CommentRepository were injected.
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
        )
            .andExpect(status().isOk)
    }

    // ------------------------------------------------------------------
    // 16. Controller delegates to GetCommentRepliesUseCase
    // ------------------------------------------------------------------

    @Test
    fun getCommentReplies_delegatesToGetCommentRepliesUseCase() {
        val r = comment()
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 20, null))
            .thenReturn(CommentPage(items = listOf(r), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
        )
            .andExpect(status().isOk)

        Mockito.verify(getCommentRepliesUseCase).getReplies(PublicId(parentCommentId), 20, null)
    }

    // ==================================================================
    // Stage 26 — Comment List Cursor Pagination
    // ==================================================================

    @Test
    fun getCommentsByContent_withCursor_returnsLinkHeader() {
        val c1 = comment(idValue = "cmt_1", text = "First", createdAt = 1_757_000_000_100L)
        val c2 = comment(idValue = "cmt_2", text = "Second", createdAt = 1_757_000_000_200L)
        val nextCursor = CommentCursor(createdAt = 1_757_000_000_200L, commentId = PublicId("cmt_2"))
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 2, null))
            .thenReturn(CommentPage(items = listOf(c1, c2), nextCursor = nextCursor))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
                .param("limit", "2")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect { result ->
                val linkHeader = result.response.getHeader("Link")
                assert(linkHeader != null) { "Link header must be present when nextCursor exists" }
                assert(linkHeader!!.contains("rel=\"next\"")) { "Link header must have rel=\"next\"" }
                assert(linkHeader.contains("cursor=")) { "Link header must contain cursor parameter" }
                assert(linkHeader.contains("limit=2")) { "Link header must preserve limit" }
            }
    }

    @Test
    fun getCommentsByContent_lastPage_noLinkHeader() {
        val c1 = comment(idValue = "cmt_1", text = "Only", createdAt = 1_757_000_000_100L)
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 20, null))
            .thenReturn(CommentPage(items = listOf(c1), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
        )
            .andExpect(status().isOk)
            .andExpect { result ->
                val linkHeader = result.response.getHeader("Link")
                assert(linkHeader == null) { "Link header must not be present on last page" }
            }
    }

    @Test
    fun getCommentsByContent_invalidCursor_returns400() {
        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
                .param("cursor", "!!!invalid!!!")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentsByContentUseCase)
    }

    @Test
    fun getCommentsByContent_blankCursor_returns400() {
        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
                .param("cursor", "   ")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentsByContentUseCase)
    }

    @Test
    fun getCommentsByContent_wrongVersionCursor_returns400() {
        val wrongVersion = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("v2:123:cmt_1".toByteArray())
        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
                .param("cursor", wrongVersion)
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentsByContentUseCase)
    }

    @Test
    fun getCommentsByContent_negativeCreatedAtCursor_returns400() {
        val negativeCreatedAt = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("v1:-5:cmt_1".toByteArray())
        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
                .param("cursor", negativeCreatedAt)
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentsByContentUseCase)
    }

    @Test
    fun getCommentsByContent_emptyResult_noLinkHeader() {
        Mockito.`when`(getCommentsByContentUseCase.getByContent(PublicId(contentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
            .andExpect { result ->
                val linkHeader = result.response.getHeader("Link")
                assert(linkHeader == null) { "Link header must not be present on empty result" }
            }
    }

    @Test
    fun getCommentsByContent_limitZero_noLinkHeader() {
        mockMvc.perform(
            get("/api/v1/contents/{contentId}/comments", contentId)
                .param("limit", "0")
        )
            .andExpect(status().isOk)
            .andExpect { result ->
                val linkHeader = result.response.getHeader("Link")
                assert(linkHeader == null) { "Link header must not be present for limit=0" }
            }
    }

    // ==================================================================
    // Stage 26 — Reply List Cursor Pagination
    // ==================================================================

    @Test
    fun getCommentReplies_withCursor_returnsLinkHeader() {
        val r1 = comment(idValue = "cmt_reply1", parentCommentIdValue = parentCommentId, text = "Reply 1", createdAt = 1_757_000_000_100L)
        val r2 = comment(idValue = "cmt_reply2", parentCommentIdValue = parentCommentId, text = "Reply 2", createdAt = 1_757_000_000_200L)
        val nextCursor = CommentCursor(createdAt = 1_757_000_000_200L, commentId = PublicId("cmt_reply2"))
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 2, null))
            .thenReturn(CommentPage(items = listOf(r1, r2), nextCursor = nextCursor))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
                .param("limit", "2")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect { result ->
                val linkHeader = result.response.getHeader("Link")
                assert(linkHeader != null) { "Link header must be present when nextCursor exists" }
                assert(linkHeader!!.contains("rel=\"next\"")) { "Link header must have rel=\"next\"" }
                assert(linkHeader.contains("cursor=")) { "Link header must contain cursor parameter" }
                assert(linkHeader.contains("limit=2")) { "Link header must preserve limit" }
            }
    }

    @Test
    fun getCommentReplies_lastPage_noLinkHeader() {
        val r1 = comment(idValue = "cmt_reply1", parentCommentIdValue = parentCommentId, text = "Only", createdAt = 1_757_000_000_100L)
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 20, null))
            .thenReturn(CommentPage(items = listOf(r1), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
        )
            .andExpect(status().isOk)
            .andExpect { result ->
                val linkHeader = result.response.getHeader("Link")
                assert(linkHeader == null) { "Link header must not be present on last page" }
            }
    }

    @Test
    fun getCommentReplies_invalidCursor_returns400() {
        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
                .param("cursor", "!!!invalid!!!")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentRepliesUseCase)
    }

    @Test
    fun getCommentReplies_blankCursor_returns400() {
        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
                .param("cursor", "   ")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentRepliesUseCase)
    }

    @Test
    fun getCommentReplies_wrongVersionCursor_returns400() {
        val wrongVersion = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("v2:123:cmt_1".toByteArray())
        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
                .param("cursor", wrongVersion)
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentRepliesUseCase)
    }

    @Test
    fun getCommentReplies_negativeCreatedAtCursor_returns400() {
        val negativeCreatedAt = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("v1:-5:cmt_1".toByteArray())
        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
                .param("cursor", negativeCreatedAt)
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(getCommentRepliesUseCase)
    }

    @Test
    fun getCommentReplies_emptyResult_noLinkHeader() {
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(parentCommentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
            .andExpect { result ->
                val linkHeader = result.response.getHeader("Link")
                assert(linkHeader == null) { "Link header must not be present on empty result" }
            }
    }

    @Test
    fun getCommentReplies_limitZero_noLinkHeader() {
        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", parentCommentId)
                .param("limit", "0")
        )
            .andExpect(status().isOk)
            .andExpect { result ->
                val linkHeader = result.response.getHeader("Link")
                assert(linkHeader == null) { "Link header must not be present for limit=0" }
            }
    }

    @Test
    fun getCommentReplies_deletedParent_noLinkHeader() {
        val deletedParentId = "cmt_deleted_parent"
        Mockito.`when`(getCommentRepliesUseCase.getReplies(PublicId(deletedParentId), 20, null))
            .thenReturn(CommentPage(items = emptyList(), nextCursor = null))

        mockMvc.perform(
            get("/api/v1/comments/{commentId}/replies", deletedParentId)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
            .andExpect { result ->
                val linkHeader = result.response.getHeader("Link")
                assert(linkHeader == null) { "Link header must not be present for deleted parent" }
            }
    }
}
