package com.contentplatform.backend.api.interaction

import com.contentplatform.backend.application.interaction.CreateInteractionResult
import com.contentplatform.backend.application.interaction.CreateInteractionUseCase
import com.contentplatform.backend.application.interaction.DeleteInteractionUseCase
import com.contentplatform.backend.application.interaction.InteractionContentNotFoundException
import com.contentplatform.backend.application.interaction.InteractionContentNotInteractableException
import com.contentplatform.backend.application.interaction.InteractionUserNotFoundException
import com.contentplatform.backend.application.interaction.GetUserInteractionHistoryUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.UserRepository
import com.contentplatform.backend.api.authentication.SessionAttributes
import com.contentplatform.backend.domain.interaction.Interaction
import com.contentplatform.backend.domain.interaction.InteractionType
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MVC slice test for [InteractionController] (Stage 20-A + 20-B).
 *
 * Covers:
 *   POST   /api/v1/users/{userId}/interactions
 *   DELETE /api/v1/users/{userId}/interactions/{contentId}?type=…
 *
 * Use cases are mocked; no database, no Flyway, no external HTTP.
 * The controller has NO direct dependency on InteractionRepository.
 */
@WebMvcTest(controllers = [InteractionController::class])
@TestPropertySource(properties = ["spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_slice_test"])
class InteractionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var createInteractionUseCase: CreateInteractionUseCase

    @MockitoBean
    private lateinit var deleteInteractionUseCase: DeleteInteractionUseCase

    // Stage 19 GET controller must not be affected; declare it so Spring context loads
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

    private fun interaction(
        idValue: String = "int_1",
        userIdValue: String = userId,
        contentIdValue: String = contentId,
        type: InteractionType = InteractionType.LIKE,
        createdAt: Long = 1_757_000_000_100L
    ): Interaction = Interaction(
        id = PublicId(idValue),
        userId = PublicId(userIdValue),
        contentId = PublicId(contentIdValue),
        type = type,
        createdAt = createdAt
    )

    private fun created(interaction: Interaction): CreateInteractionResult =
        CreateInteractionResult(interaction = interaction, created = true)

    private fun existing(interaction: Interaction): CreateInteractionResult =
        CreateInteractionResult(interaction = interaction, created = false)

    private fun json(contentId: String, type: String): String =
        """{"contentId":"$contentId","type":"$type"}"""

    // ==================================================================
    // POST /api/v1/users/{userId}/interactions  (Stage 20-A)
    // ==================================================================

    // ------------------------------------------------------------------
    // LIKE creation -> 201
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenLIKE_returns201WithResponse() {
        val i = interaction(type = InteractionType.LIKE)
        Mockito.`when`(createInteractionUseCase.create(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )).thenReturn(created(i))

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "LIKE"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("int_1"))
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.contentId").value(contentId))
            .andExpect(jsonPath("$.type").value("LIKE"))
            .andExpect(jsonPath("$.createdAt").isNumber())
    }

    // ------------------------------------------------------------------
    // FAVORITE creation -> 201
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenFAVORITE_returns201() {
        val i = interaction(idValue = "int_fav", type = InteractionType.FAVORITE)
        Mockito.`when`(createInteractionUseCase.create(
            PublicId(userId), PublicId(contentId), InteractionType.FAVORITE
        )).thenReturn(created(i))

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "FAVORITE"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("FAVORITE"))
    }

    // ------------------------------------------------------------------
    // REPOST creation -> 201
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenREPOST_returns201() {
        val i = interaction(idValue = "int_re", type = InteractionType.REPOST)
        Mockito.`when`(createInteractionUseCase.create(
            PublicId(userId), PublicId(contentId), InteractionType.REPOST
        )).thenReturn(created(i))

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "REPOST"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("REPOST"))
    }

    // ------------------------------------------------------------------
    // BOOKMARK creation -> 201
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenBOOKMARK_returns201() {
        val i = interaction(idValue = "int_bm", type = InteractionType.BOOKMARK)
        Mockito.`when`(createInteractionUseCase.create(
            PublicId(userId), PublicId(contentId), InteractionType.BOOKMARK
        )).thenReturn(created(i))

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "BOOKMARK"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.type").value("BOOKMARK"))
    }

    // ------------------------------------------------------------------
    // Duplicate creation -> 200 (idempotent, created=false)
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenAlreadyExists_returns200() {
        val existing = interaction(type = InteractionType.LIKE)
        Mockito.`when`(createInteractionUseCase.create(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )).thenReturn(existing(existing))

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "LIKE"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("int_1"))
            .andExpect(jsonPath("$.type").value("LIKE"))
    }

    // ------------------------------------------------------------------
    // Concurrent duplicate -> 200 (created=false from DuplicateKeyException path)
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenConcurrentDuplicate_returns200() {
        val winner = interaction(idValue = "int_winner", type = InteractionType.FAVORITE)
        Mockito.`when`(createInteractionUseCase.create(
            PublicId(userId), PublicId(contentId), InteractionType.FAVORITE
        )).thenReturn(existing(winner))

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "FAVORITE"))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("int_winner"))
    }

    // ------------------------------------------------------------------
    // User not found -> 404
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenUserNotFound_returns403() {
        val missingUserId = "usr_missing"
        Mockito.`when`(userRepository.findById(PublicId(missingUserId))).thenReturn(null)

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", missingUserId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, missingUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "LIKE"))
        )
            .andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------
    // Content not found -> 404
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenContentNotFound_returns404() {
        val missingContentId = "ct_missing"
        Mockito.`when`(createInteractionUseCase.create(
            PublicId(userId), PublicId(missingContentId), InteractionType.LIKE
        )).thenThrow(InteractionContentNotFoundException("Content ct_missing does not exist"))

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(missingContentId, "LIKE"))
        )
            .andExpect(status().isNotFound)
    }

    // ------------------------------------------------------------------
    // Content not interactable -> 409
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenContentNotInteractable_returns409() {
        Mockito.`when`(createInteractionUseCase.create(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )).thenThrow(InteractionContentNotInteractableException(
            "Content ct_1 is not publicly interactable"
        ))

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "LIKE"))
        )
            .andExpect(status().isConflict)
    }

    // ------------------------------------------------------------------
    // Invalid userId (blank) -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenUserIdBlank_returns401() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", "   ")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "   ")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "LIKE"))
        )
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(createInteractionUseCase)
    }

    // ------------------------------------------------------------------
    // Invalid contentId (blank) -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenContentIdBlank_returns400() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("   ", "LIKE"))
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createInteractionUseCase)
    }

    @Test
    fun createInteraction_whenContentIdNull_returns400() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"contentId":null,"type":"LIKE"}""")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createInteractionUseCase)
    }

    // ------------------------------------------------------------------
    // Invalid type -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenInvalidType_returns400() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "INVALID"))
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createInteractionUseCase)
    }

    @Test
    fun createInteraction_whenTypeLowercase_returns400() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "like"))
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createInteractionUseCase)
    }

    @Test
    fun createInteraction_whenTypeNull_returns400() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"contentId":"$contentId","type":null}""")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createInteractionUseCase)
    }

    // ------------------------------------------------------------------
    // COMMENT is rejected -> 400
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenCOMMENT_returns400() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "COMMENT"))
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createInteractionUseCase)
    }

    // ------------------------------------------------------------------
    // Response fields are correct
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_returnsAllInteractionResponseFields() {
        val i = interaction(
            idValue = "int_xyz",
            userIdValue = "usr_alice",
            contentIdValue = "ct_content1",
            type = InteractionType.BOOKMARK,
            createdAt = 1_700_000_000_000L
        )
        Mockito.`when`(createInteractionUseCase.create(
            PublicId(userId), PublicId("ct_content1"), InteractionType.BOOKMARK
        )).thenReturn(created(i))

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("ct_content1", "BOOKMARK"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("int_xyz"))
            .andExpect(jsonPath("$.userId").value("usr_alice"))
            .andExpect(jsonPath("$.contentId").value("ct_content1"))
            .andExpect(jsonPath("$.type").value("BOOKMARK"))
            .andExpect(jsonPath("$.createdAt").value(1_700_000_000_000L))
    }

    // ------------------------------------------------------------------
    // Missing request body -> 400
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenMissingBody_returns400() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createInteractionUseCase)
    }

    // ------------------------------------------------------------------
    // Malformed JSON -> 400
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_whenMalformedJson_returns400() {
        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("not json")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(createInteractionUseCase)
    }

    // ------------------------------------------------------------------
    // Use case is called with correct arguments
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_forwardsCorrectArgumentsToUseCase() {
        val i = interaction(type = InteractionType.REPOST)
        Mockito.`when`(createInteractionUseCase.create(
            PublicId(userId), PublicId(contentId), InteractionType.REPOST
        )).thenReturn(created(i))

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "REPOST"))
        )
            .andExpect(status().isCreated)

        Mockito.verify(createInteractionUseCase).create(
            PublicId(userId), PublicId(contentId), InteractionType.REPOST
        )
    }

    // ------------------------------------------------------------------
    // Stage 19 History API not affected
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_doesNotAffectGetHistoryUseCase() {
        val i = interaction(type = InteractionType.LIKE)
        Mockito.`when`(createInteractionUseCase.create(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )).thenReturn(created(i))

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(contentId, "LIKE"))
        )
            .andExpect(status().isCreated)

        // Verify the GET use case was never invoked by the POST endpoint
        Mockito.verifyNoInteractions(getHistoryUseCase)
    }

    // ------------------------------------------------------------------
    // ContentId is trimmed
    // ------------------------------------------------------------------

    @Test
    fun createInteraction_trimsContentId() {
        val i = interaction(type = InteractionType.LIKE)
        Mockito.`when`(createInteractionUseCase.create(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )).thenReturn(created(i))

        mockMvc.perform(
            post("/api/v1/users/{userId}/interactions", userId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("  $contentId  ", "LIKE"))
        )
            .andExpect(status().isCreated)

        Mockito.verify(createInteractionUseCase).create(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )
    }

    // ==================================================================
    // DELETE /api/v1/users/{userId}/interactions/{contentId}?type=…
    // (Stage 20-B)
    // ==================================================================

    // ------------------------------------------------------------------
    // LIKE delete -> 204
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_whenLIKE_returns204() {
        Mockito.`when`(deleteInteractionUseCase.delete(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )).thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "LIKE")
        )
            .andExpect(status().isNoContent)

        Mockito.verify(deleteInteractionUseCase).delete(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )
    }

    // ------------------------------------------------------------------
    // FAVORITE delete -> 204
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_whenFAVORITE_returns204() {
        Mockito.`when`(deleteInteractionUseCase.delete(
            PublicId(userId), PublicId(contentId), InteractionType.FAVORITE
        )).thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "FAVORITE")
        )
            .andExpect(status().isNoContent)
    }

    // ------------------------------------------------------------------
    // REPOST delete -> 204
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_whenREPOST_returns204() {
        Mockito.`when`(deleteInteractionUseCase.delete(
            PublicId(userId), PublicId(contentId), InteractionType.REPOST
        )).thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "REPOST")
        )
            .andExpect(status().isNoContent)
    }

    // ------------------------------------------------------------------
    // BOOKMARK delete -> 204
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_whenBOOKMARK_returns204() {
        Mockito.`when`(deleteInteractionUseCase.delete(
            PublicId(userId), PublicId(contentId), InteractionType.BOOKMARK
        )).thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "BOOKMARK")
        )
            .andExpect(status().isNoContent)
    }

    // ------------------------------------------------------------------
    // Interaction not found -> 204 (idempotent)
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_whenInteractionNotFound_returns204() {
        Mockito.`when`(deleteInteractionUseCase.delete(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )).thenReturn(false)

        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "LIKE")
        )
            .andExpect(status().isNoContent)
    }

    // ------------------------------------------------------------------
    // Repeated delete -> 204 (idempotent)
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_repeatedDelete_returns204() {
        Mockito.`when`(deleteInteractionUseCase.delete(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )).thenReturn(false)

        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "LIKE")
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "LIKE")
        )
            .andExpect(status().isNoContent)
    }

    // ------------------------------------------------------------------
    // User not found -> 404
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_whenUserNotFound_returns403() {
        val missingUserId = "usr_missing"
        Mockito.`when`(userRepository.findById(PublicId(missingUserId))).thenReturn(null)

        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", missingUserId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, missingUserId)
                .param("type", "LIKE")
        )
            .andExpect(status().isUnauthorized)
    }

    // ------------------------------------------------------------------
    // Invalid userId (blank) -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_whenUserIdBlank_returns401() {
        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", "   ", contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, "   ")
                .param("type", "LIKE")
        )
            .andExpect(status().isUnauthorized)

        Mockito.verifyNoInteractions(deleteInteractionUseCase)
    }

    // ------------------------------------------------------------------
    // Invalid contentId (blank) -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_whenContentIdBlank_returns400() {
        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, "   ")
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "LIKE")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(deleteInteractionUseCase)
    }

    // ------------------------------------------------------------------
    // Type missing -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_whenTypeMissing_returns400() {
        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(deleteInteractionUseCase)
    }

    // ------------------------------------------------------------------
    // Invalid type -> 400, use case never invoked
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_whenInvalidType_returns400() {
        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "INVALID")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(deleteInteractionUseCase)
    }

    @Test
    fun deleteInteraction_whenTypeLowercase_returns400() {
        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "like")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(deleteInteractionUseCase)
    }

    // ------------------------------------------------------------------
    // COMMENT is rejected -> 400
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_whenCOMMENT_returns400() {
        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "COMMENT")
        )
            .andExpect(status().isBadRequest)

        Mockito.verifyNoInteractions(deleteInteractionUseCase)
    }

    // ------------------------------------------------------------------
    // Stage 20-A POST not affected
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_doesNotAffectCreateUseCase() {
        Mockito.`when`(deleteInteractionUseCase.delete(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )).thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "LIKE")
        )
            .andExpect(status().isNoContent)

        Mockito.verifyNoInteractions(createInteractionUseCase)
    }

    // ------------------------------------------------------------------
    // Stage 19 History API not affected
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_doesNotAffectGetHistoryUseCase() {
        Mockito.`when`(deleteInteractionUseCase.delete(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )).thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "LIKE")
        )
            .andExpect(status().isNoContent)

        Mockito.verifyNoInteractions(getHistoryUseCase)
    }

    // ------------------------------------------------------------------
    // Controller has no direct InteractionRepository dependency
    // ------------------------------------------------------------------

    @Test
    fun deleteInteraction_controllerHasNoRepositoryDependency() {
        // This test verifies at compile time that InteractionController's
        // constructor does not accept InteractionRepository. The test class
        // would fail to compile if InteractionRepository were injected.
        Mockito.`when`(deleteInteractionUseCase.delete(
            PublicId(userId), PublicId(contentId), InteractionType.LIKE
        )).thenReturn(true)

        mockMvc.perform(
            delete("/api/v1/users/{userId}/interactions/{contentId}", userId, contentId)
                .sessionAttr(SessionAttributes.CURRENT_USER_ID, userId)
                .param("type", "LIKE")
        )
            .andExpect(status().isNoContent)
    }
}
