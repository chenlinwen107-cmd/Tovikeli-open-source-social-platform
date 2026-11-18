package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.Interaction
import com.contentplatform.backend.domain.interaction.InteractionCursor
import com.contentplatform.backend.domain.interaction.InteractionPage
import com.contentplatform.backend.domain.interaction.InteractionType

/**
 * Aggregate data access contract for an [Interaction] (Stage 17).
 *
 * Repository contracts are transaction-agnostic. Transaction boundaries belong
 * to Application use cases, not to this interface.
 *
 * Stage 17 semantics:
 *  - Interaction identity is (userId, contentId, type). [findByUserContentType]
 *    is the natural-key lookup mirroring the database UNIQUE constraint
 *    (user_id, content_id, type).
 *  - [save] performs a strict INSERT. The database UNIQUE constraint is the
 *    FINAL concurrency protection against duplicate rows; callers
 *    (CreateInteractionUseCase) run the natural-key existence check first and
 *    translate any concurrent duplicate-key violation themselves.
 *  - [deleteByUserContentType] is a physical delete (no soft delete, no
 *    history tables) returning whether a row was actually removed.
 */
interface InteractionRepository {

    /** Persists a newly created interaction aggregate and returns it. */
    fun save(interaction: Interaction): Interaction

    /** Returns the interaction with the given public id, or null when absent. */
    fun findById(id: PublicId): Interaction?

    /**
     * Natural-key lookup.
     *
     * Interaction uniqueness is (userId, contentId, type).
     */
    fun findByUserContentType(
        userId: PublicId,
        contentId: PublicId,
        type: InteractionType
    ): Interaction?

    /**
     * Physically deletes the interaction with the natural key
     * (userId, contentId, type).
     *
     * @return true when a row was deleted, false when no such interaction
     *         existed (idempotent no-op result).
     */
    fun deleteByUserContentType(
        userId: PublicId,
        contentId: PublicId,
        type: InteractionType
    ): Boolean

    /**
     * Returns one page of at most [limit] of the most recently created
     * Interaction aggregates for the given [userId], plus the stateless
     * keyset cursor for the next page when one exists (Stage 19).
     *
     * When [type] is non-null the result is further restricted to interactions
     * of that type; when null no type restriction is applied and all
     * interaction types are returned.
     *
     * Ordering: interaction.created_at DESC with interaction.id DESC as the
     * deterministic tie-breaker for equal timestamps.
     *
     * Pagination mechanics: the query is executed with LIMIT limit+1 so the
     * repository can detect a following page without a separate count query.
     * The returned [InteractionPage] never contains the probe row, and its
     * nextCursor is derived from the last delivered row (never the probe row).
     *
     * The limit is an explicit bounded upper bound expressed as a plain Int.
     * Values outside the valid range are rejected with [IllegalArgumentException].
     * limit=0 returns an empty page without executing a query. No HTTP concepts
     * are part of this contract.
     *
     * The default implementation is intentionally unsupported so that existing
     * non-persistence implementors (in-memory test doubles) remain
     * source-compatible; persistence-backed repositories must override it.
     */
    fun findByUserId(
        userId: PublicId,
        type: InteractionType?,
        limit: Int,
        cursor: InteractionCursor?
    ): InteractionPage =
        throw UnsupportedOperationException("findByUserId requires a persistence-backed InteractionRepository")

    /**
     * Returns the per-type interaction counts for the given content.
     *
     * Types with zero occurrences may be absent from the returned map.
     * Callers must treat missing types as zero.
     */
    fun countByContentId(contentId: PublicId): Map<InteractionType, Long> =
        throw UnsupportedOperationException("countByContentId requires a persistence-backed InteractionRepository")

    /**
     * Returns all Interaction rows for the given user/content pair (Stage 32).
     *
     * Uses one SQL query with WHERE user_id = ? AND content_id = ?. The
     * result contains at most one row per InteractionType (enforced by the
     * database UNIQUE constraint on (user_id, content_id, type)).
     *
     * An empty list means the user has no interactions for this content.
     *
     * This method does NOT change the semantics of [findByUserContentType]
     * which remains a single-type natural-key lookup.
     */
    fun findBookmarks(userId: PublicId, limit: Int, offset: Int): List<Interaction> =
        throw UnsupportedOperationException("findBookmarks requires a persistence-backed InteractionRepository")

    fun findByUserAndContent(
        userId: PublicId,
        contentId: PublicId
    ): List<Interaction> =
        throw UnsupportedOperationException("findByUserAndContent requires a persistence-backed InteractionRepository")
}
