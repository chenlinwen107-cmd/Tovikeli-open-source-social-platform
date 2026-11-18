package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.TagRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Tag-scoped public Content retrieval (Stage 15-C).
 *
 * Flow:
 *
 *   tagId
 *     -> TagRepository.findById(tagId)  (exactly ONE tag existence lookup)
 *     -> null when the tag is not persisted (caller maps to HTTP 404)
 *     -> ContentRepository.findPublicContentByTag(tagId, cursor, limit)
 *        -> ContentBrowsePage
 *
 * The tag existence check happens exactly once per request and never inside
 * the content page query, so there is no per-row tag lookup and no N+1. The
 * content page query itself owns the frozen public filter (ACTIVE + PUBLIC +
 * allow_index), the content_tag association, the Stage 14 keyset cursor and
 * the batched media loading.
 *
 * The repository page is returned unchanged (pure pass-through); no business
 * rule is implemented here.
 *
 * Transaction notes: simple read-only queries; no transaction boundary is
 * declared. Consistent with the other repository-dependent read use cases,
 * this use case stays conditional on the configured datasource.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetContentByTagUseCase(
    private val tagRepository: TagRepository,
    private val contentRepository: ContentRepository
) {

    /**
     * Returns one page of public content associated with [tagId], or null when
     * no tag with that id exists (the caller decides the 404 mapping). A page
     * is returned even when the tag exists but has no matching public content
     * (empty page, never null for an existing tag).
     */
    fun getByTag(tagId: PublicId, cursor: ContentCursor?, limit: Int): ContentBrowsePage? {
        val tag = tagRepository.findById(tagId) ?: return null
        return contentRepository.findPublicContentByTag(tagId = tag.id, cursor = cursor, limit = limit)
    }
}
