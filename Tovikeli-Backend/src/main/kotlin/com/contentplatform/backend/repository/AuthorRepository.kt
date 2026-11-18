package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.PublicId

/**
 * Aggregate data access contract for [Author].
 *
 * Repository contracts are transaction-agnostic. Transaction boundaries belong
 * to Application use cases, not to this interface.
 */
interface AuthorRepository {

    /** Returns the author with the given public id, or null when absent. */
    fun findById(id: PublicId): Author?

    /**
     * Natural-key lookup.
     *
     * External author uniqueness is (instanceId, sourceAuthorId).
     */
    fun findByInstanceAndSourceAuthorId(
        instanceId: PublicId,
        sourceAuthorId: String
    ): Author?

    /**
     * Persists the current state of the supplied aggregate and returns the
     * persisted aggregate.
     */
    fun save(author: Author): Author
}
