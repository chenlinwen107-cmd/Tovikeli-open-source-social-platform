package com.contentplatform.backend.application.interaction

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.InteractionCursor
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.InteractionRepository
import com.contentplatform.backend.repository.UserRepository
import com.contentplatform.backend.repository.AuthorRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Thrown when a query references a platform user that does not exist.
 */
class InteractionQueryUserNotFoundException(message: String) : RuntimeException(message)

/**
 * Stage 19 Query User Interaction History use case with Stage 25 content
 * hydration.
 *
 * Returns one page of [HydratedInteraction] items for a given platform
 * [com.contentplatform.backend.domain.user.User], optionally filtered by
 * [InteractionType]. Each interaction is paired with its referenced Content
 * when available, or null when the content does not exist.
 *
 * Validation rules (frozen Stage 19 contract):
 *  1. The platform user must exist ([InteractionQueryUserNotFoundException]
 *     otherwise).
 *  2. DISABLED users are allowed; their interaction history is still queryable.
 *  3. All parameters are passed through unchanged to
 *     [InteractionRepository.findByUserId]; no re-implementation of
 *     pagination, cursor, SQL or filtering logic.
 *  4. Content status, visibility and allow_index are NOT checked.
 *
 * Hydration rules (Stage 25):
 *  5. Content is loaded via [ContentRepository.findByIds] in a single batch
 *     call (no N+1).
 *  6. No PUBLIC, ACTIVE or allow_index filtering is applied. Interaction is
 *     always preserved; missing content results in null.
 *
 * Return semantics: a [HydratedInteractionPage] is always returned (never
 * null). An empty page with null nextCursor is returned when the user has no
 * matching interactions.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetUserInteractionHistoryUseCase(
    private val userRepository: UserRepository,
    private val interactionRepository: InteractionRepository,
    private val contentRepository: ContentRepository,
    private val authorRepository: AuthorRepository
) {

    fun getBookmarks(userId: PublicId, limit: Int, offset: Int): List<HydratedInteraction> {
        if (userRepository.findById(userId) == null) throw InteractionQueryUserNotFoundException("User $userId does not exist")
        val items = interactionRepository.findBookmarks(userId, limit, offset)
        val contents = contentRepository.findByIds(items.map { it.contentId }.distinct()).associateBy { it.id }
        return items.map { interaction ->
            val content = contents[interaction.contentId]
            HydratedInteraction(interaction, content?.let { contentItem ->
                // Keep the domain object intact; the API layer enriches it with its local author.
                contentItem
            })
        }
    }

    fun getHistory(
        userId: PublicId,
        type: InteractionType?,
        limit: Int,
        cursor: InteractionCursor?
    ): HydratedInteractionPage {
        if (userRepository.findById(userId) == null) {
            throw InteractionQueryUserNotFoundException(
                "User $userId does not exist"
            )
        }
        val page = interactionRepository.findByUserId(userId, type, limit, cursor)
        if (page.items.isEmpty()) {
            return HydratedInteractionPage(items = emptyList(), nextCursor = null)
        }

        val contentIds = page.items.map { it.contentId }.distinct()
        val contents = contentRepository.findByIds(contentIds)
        val contentMap = contents.associateBy { it.id }

        val hydrated = page.items.map { interaction ->
            HydratedInteraction(
                interaction = interaction,
                content = contentMap[interaction.contentId]
            )
        }
        return HydratedInteractionPage(items = hydrated, nextCursor = page.nextCursor)
    }
}
