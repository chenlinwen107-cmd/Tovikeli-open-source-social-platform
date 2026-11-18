package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.ContentRelationships
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.repository.ContentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Reads the direct Content relationships for a single Content aggregate
 * (Stage 31).
 *
 * Exposes parentId, repostOfId, and quoteOfId as publicly readable related
 * Content objects. The current Content must be ACTIVE for any relationships
 * to be returned.
 *
 * A missing/inaccessible related Content does NOT make the entire request
 * fail — the corresponding relationship field is null.
 *
 * Lifecycle visibility (Stage 28): only ACTIVE content is surfaced
 * through public detail reads. This follows the same convention
 * as [com.contentplatform.backend.application.content.GetContentDetailUseCase]:
 * the use case checks status == ACTIVE; visibility and allow_index are
 * enforced at the SQL level in browse/search/tag queries, not in
 * direct-content-detail use cases. Related Content accessibility is
 * enforced by [ContentRepository.findPublicByIds] which applies
 * ACTIVE + PUBLIC + allow_index filtering.
 *
 * Not-found semantics: null is returned when the current Content does not
 * exist or is not ACTIVE. Mapping to HTTP 404 is the API layer's
 * responsibility.
 *
 * No recursive traversal: one level only. Related Content objects do not
 * carry their own relationships.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetContentRelationshipsUseCase(
    private val contentRepository: ContentRepository
) {

    fun getRelationships(contentId: PublicId): ContentRelationships? {
        val content = contentRepository.findById(contentId) ?: return null
        if (content.status != ContentStatus.ACTIVE) return null

        val targetIds = listOfNotNull(content.parentId, content.repostOfId, content.quoteOfId)
            .distinct()

        if (targetIds.isEmpty()) {
            return ContentRelationships(parent = null, repostOf = null, quoteOf = null)
        }

        val relatedContents = contentRepository.findPublicByIds(targetIds)
        val contentById = associatedBy(relatedContents)

        return ContentRelationships(
            parent = content.parentId?.let { contentById[it.value] },
            repostOf = content.repostOfId?.let { contentById[it.value] },
            quoteOf = content.quoteOfId?.let { contentById[it.value] }
        )
    }

    private fun associatedBy(contents: List<com.contentplatform.backend.domain.content.Content>): Map<String, com.contentplatform.backend.domain.content.Content> =
        contents.associateBy { it.id.value }
}
