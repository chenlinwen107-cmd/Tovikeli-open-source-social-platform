package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.repository.ContentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Full-text search over publicly browsable [Content] bodies (Stage 15-A
 * Search Foundation).
 *
 * This use case is a thin pass-through over
 * [ContentRepository.searchPublicContent] and returns the complete Domain
 * aggregates. No query-language parsing, ranking design, tag search or search
 * cursor semantics exist here; the repository contract owns the PostgreSQL
 * full-text search and the ts_rank relevance ordering.
 *
 * The requested [query] and [limit] are passed through unchanged. The HTTP
 * boundary owns all request validation (q non-blank, limit 0..100, filter
 * wrapping); this use case deliberately performs no duplicate validation.
 *
 * Empty-result semantics: an empty list is returned when nothing matches
 * (never null).
 *
 * Transaction notes: simple read-only query; no transaction boundary is
 * declared. Consistent with the other repository-dependent read use cases,
 * this use case stays conditional on the configured datasource.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class SearchContentUseCase(
    private val contentRepository: ContentRepository
) {

    /**
     * Returns up to [limit] Content aggregates whose body text matches
     * [query], optionally scoped to [instanceId], [authorId] and/or
     * [sourceId], ordered by relevance then the frozen browse ordering.
     */
    fun search(
        query: String,
        instanceId: PublicId?,
        authorId: PublicId?,
        sourceId: PublicId?,
        limit: Int
    ): List<Content> =
        contentRepository.searchPublicContent(query, instanceId, authorId, sourceId, limit)
}
