package com.contentplatform.backend.application.interaction

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.interaction.UserContentInteractionState
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.InteractionRepository
import com.contentplatform.backend.repository.UserRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Reads the current Interaction state for one user on one Content (Stage 32).
 *
 * Returns a [UserContentInteractionState] indicating which of the four
 * interaction types (LIKE, FAVORITE, REPOST, BOOKMARK) the user currently
 * has for the specified Content.
 *
 * Lifecycle visibility (Stage 28): only ACTIVE content is surfaced
 * through public detail reads. DELETED, HIDDEN, UNAVAILABLE and UNKNOWN
 * content is treated as non-existent. This follows the same convention
 * as [com.contentplatform.backend.application.content.GetContentDetailUseCase]:
 * the use case checks status == ACTIVE; visibility and allow_index are
 * enforced at the SQL level in browse/search/tag queries, not in
 * direct-content-detail use cases.
 *
 * Not-found semantics: null is returned when the user does not exist,
 * the content does not exist, or the content is not ACTIVE.
 * Mapping to HTTP 404 is the API layer's responsibility.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetUserContentInteractionStateUseCase(
    private val userRepository: UserRepository,
    private val contentRepository: ContentRepository,
    private val interactionRepository: InteractionRepository
) {

    fun getState(userId: PublicId, contentId: PublicId): UserContentInteractionState? {
        if (userRepository.findById(userId) == null) return null

        val content = contentRepository.findById(contentId) ?: return null
        if (content.status != ContentStatus.ACTIVE) return null

        val interactions = interactionRepository.findByUserAndContent(userId, contentId)
        return UserContentInteractionState.fromInteractions(contentId, interactions)
    }
}
