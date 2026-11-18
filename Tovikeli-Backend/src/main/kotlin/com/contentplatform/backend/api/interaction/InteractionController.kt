package com.contentplatform.backend.api.interaction

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.api.user.InteractionResponse
import com.contentplatform.backend.application.interaction.CreateInteractionUseCase
import com.contentplatform.backend.application.interaction.DeleteInteractionUseCase
import com.contentplatform.backend.application.interaction.InteractionContentNotFoundException
import com.contentplatform.backend.application.interaction.InteractionContentNotInteractableException
import com.contentplatform.backend.application.interaction.InteractionUserNotFoundException
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.InteractionType
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Platform Interaction API (Stage 20-A + 20-B).
 *
 *   POST   /api/v1/users/{userId}/interactions                  create
 *   DELETE /api/v1/users/{userId}/interactions/{contentId}?type=… delete
 *
 * Flow: HTTP -> this controller -> UseCase -> InteractionRepository.
 * The controller never touches a Repository directly.
 *
 * Create semantics (HTTP boundary only):
 *  - `userId` (path) uses the existing PublicId semantics; the requested user
 *    is resolved by the use case. A non-existent user is HTTP 404 with an empty
 *    body.
 *  - Request body JSON is required; missing / malformed JSON is HTTP 400 via
 *    Spring MVC's standard handling.
 *  - `contentId` is required and must be non-blank; a missing, empty or
 *    whitespace-only value is HTTP 400 with an empty body.
 *  - `type` is required and must be a valid [InteractionType] name
 *    (LIKE / FAVORITE / REPOST / BOOKMARK / LOVE / HAHA / WOW / SUPPORT). An absent, empty or invalid value
 *    (including COMMENT) is HTTP 400 with an empty body; the use case is never
 *    invoked for invalid input.
 *  - A newly created interaction is HTTP 201 Created with the API
 *    [InteractionResponse] projection.
 *  - An already-existing interaction (idempotent create) is HTTP 200 OK with
 *    the API [InteractionResponse] projection of the existing interaction.
 *  - A non-existent user is HTTP 404 via [InteractionUserNotFoundException].
 *  - A non-existent content is HTTP 404 via [InteractionContentNotFoundException].
 *  - Content that is not public/interactable is HTTP 409 via
 *    [InteractionContentNotInteractableException].
 *  - Content, Author and Media are NOT hydrated (pure Interaction projection).
 *
 * The HTTP 201 vs 200 distinction is driven by
 * [com.contentplatform.backend.application.interaction.CreateInteractionResult.created],
 * determined reliably inside the UseCase. The controller never queries a
 * Repository to make this decision.
 *
 * Delete semantics (HTTP boundary only):
 *  - `userId` (path) is validated at this boundary; a non-existent user is
 *    HTTP 404 via [InteractionUserNotFoundException].
 *  - `contentId` (path) is validated at this boundary; blank is HTTP 400.
 *  - `type` (query, required) must be a valid [InteractionType] name.
 *    Missing or invalid is HTTP 400; the use case is never invoked.
 *  - Successful delete, idempotent no-op (interaction already gone) and
 *    repeated delete are all HTTP 204 No Content.
 *
 * PublicId: the raw path variable is wrapped by the existing [PublicId] value
 * object (its only rule: non-blank). No new parser and no prefix contract is
 * introduced at the API layer.
 *
 * Wiring: conditional on a configured datasource, mirroring the other
 * persistence-backed use cases, so the no-datasource context keeps working.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/interactions")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class InteractionController(
    private val createInteractionUseCase: CreateInteractionUseCase,
    private val deleteInteractionUseCase: DeleteInteractionUseCase
) {

    @PostMapping
    fun createInteraction(
        @PathVariable("userId") userId: String,
        @RequestBody request: CreateInteractionRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<InteractionResponse> {
        val caller = CallerResolver.require(servletRequest)
        if (userId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        if (caller.userId.value != userId) {
            return ResponseEntity.status(403).build()
        }
        val contentId = request.contentId?.trim().orEmpty()
        if (contentId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val type = try {
            InteractionType.valueOf(request.type ?: "")
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
        val contentIdPid = PublicId(contentId)
        return try {
            val result = createInteractionUseCase.create(caller.userId, contentIdPid, type)
            val response = InteractionResponse.from(result.interaction)
            if (result.created) {
                ResponseEntity.status(201).body(response)
            } else {
                ResponseEntity.ok(response)
            }
        } catch (e: InteractionUserNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (e: InteractionContentNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (e: InteractionContentNotInteractableException) {
            ResponseEntity.status(409).build()
        }
    }

    @DeleteMapping("/{contentId}")
    fun deleteInteraction(
        @PathVariable("userId") userId: String,
        @PathVariable("contentId") contentId: String,
        @RequestParam(name = "type") type: String?,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Void> {
        val caller = CallerResolver.require(servletRequest)
        if (userId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        if (caller.userId.value != userId) {
            return ResponseEntity.status(403).build()
        }
        if (contentId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val interactionType = try {
            InteractionType.valueOf(type ?: "")
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
        return try {
            deleteInteractionUseCase.delete(caller.userId, PublicId(contentId), interactionType)
            ResponseEntity.noContent().build()
        } catch (e: InteractionUserNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }
}
