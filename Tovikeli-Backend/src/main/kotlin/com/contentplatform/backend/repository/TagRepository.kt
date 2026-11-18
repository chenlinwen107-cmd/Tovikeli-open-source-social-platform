package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.tag.Tag

/**
 * Aggregate data access contract for [Tag].
 *
 * Tag has two identities:
 * - EXTERNAL: (instanceId, sourceTagId)
 * - PLATFORM: verbatim name
 *
 * Repository contracts are transaction-agnostic. Transaction boundaries belong
 * to Application use cases, not to this interface.
 */
interface TagRepository {

    /** Returns the tag with the given public id, or null when absent. */
    fun findById(id: PublicId): Tag?

    /**
     * EXTERNAL tag identity lookup.
     *
     * External tag uniqueness is (instanceId, sourceTagId).
     */
    fun findByInstanceAndSourceTagId(
        instanceId: PublicId,
        sourceTagId: String
    ): Tag?

    /**
     * PLATFORM tag identity lookup.
     *
     * [name] is matched against the domain `name` verbatim, exactly as stored.
     * It is NOT a normalizedName lookup.
     */
    fun findPlatformByName(name: String): Tag?

    /**
     * Persists the current state of the supplied aggregate and returns the
     * persisted aggregate.
     */
    fun save(tag: Tag): Tag

    /**
     * Basic tag search over [Tag.normalizedName] (Stage 15-C).
     *
     * The search is deliberately NOT full-text / trigram / ranking: it is a
     * basic case-insensitive substring match on the normalized search field
     * only. Tag identity rules are untouched. Tags without a normalizedName
     * never match (the match itself is on normalizedName).
     *
     * Ordering is frozen as: normalized_name ASC, name ASC, id ASC (no
     * ranking score is produced or returned).
     *
     * The limit follows the project's 0..100 browse/search contract: values
     * outside 0..100 are rejected with [IllegalArgumentException], and limit=0
     * returns an empty list without executing a query. The repository never
     * validates the query text; HTTP validation belongs to the API boundary.
     *
     * The default implementation is intentionally unsupported so that existing
     * non-persistence implementors (in-memory test doubles) remain
     * source-compatible; persistence-backed repositories must override it.
     */
    fun search(query: String, limit: Int): List<Tag> =
        throw UnsupportedOperationException("search requires a persistence-backed TagRepository")
}
