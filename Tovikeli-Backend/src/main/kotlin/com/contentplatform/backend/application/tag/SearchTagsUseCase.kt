package com.contentplatform.backend.application.tag

import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.repository.TagRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Basic tag search (Stage 15-C).
 *
 * This use case is a thin pass-through over [TagRepository.search] and returns
 * the complete [Tag] aggregates. It never writes SQL, never applies ranking,
 * never changes tag identity/normalization and never validates the query
 * string (that is the HTTP boundary's job).
 *
 * The requested [query] and [limit] are passed through unchanged. The
 * repository bounds the limit with the project's 0..100 contract and returns
 * an empty list for limit=0 without executing a query; the HTTP layer owns the
 * default-limit and limit=0 short-circuit behaviour.
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
class SearchTagsUseCase(
    private val tagRepository: TagRepository
) {

    /**
     * Returns up to [limit] tags whose normalizedName matches [query],
     * ordered by normalized_name ASC, name ASC, id ASC.
     */
    fun search(query: String, limit: Int): List<Tag> = tagRepository.search(query, limit)
}
