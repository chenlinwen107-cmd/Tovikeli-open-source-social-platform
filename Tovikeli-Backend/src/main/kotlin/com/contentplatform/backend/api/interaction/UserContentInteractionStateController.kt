package com.contentplatform.backend.api.interaction

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.interaction.GetUserContentInteractionStateUseCase
import com.contentplatform.backend.domain.common.PublicId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * User Content Interaction state API (Stage 32).
 *
 *   GET /api/v1/users/{userId}/contents/{contentId}/interactions
 *
 * Authenticated + self-only: the path userId must match the authenticated caller.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/contents/{contentId}/interactions")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class UserContentInteractionStateController(
    private val getStateUseCase: GetUserContentInteractionStateUseCase
) {

    @GetMapping
    fun getState(
        @PathVariable("userId") userId: String,
        @PathVariable("contentId") contentId: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<UserContentInteractionStateResponse> {
        val caller = CallerResolver.require(servletRequest)
        if (userId.isBlank() || contentId.isBlank()) {
            return ResponseEntity.notFound().build()
        }
        if (caller.userId.value != userId) {
            return ResponseEntity.status(403).build()
        }
        val state = try {
            getStateUseCase.getState(caller.userId, PublicId(contentId))
        } catch (_: IllegalArgumentException) {
            return ResponseEntity.notFound().build()
        }
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(UserContentInteractionStateResponse.from(state))
    }
}
