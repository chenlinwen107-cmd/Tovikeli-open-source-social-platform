package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentCursor
import com.contentplatform.backend.domain.comment.CommentPage
import com.contentplatform.backend.domain.common.PublicId

/**
 * Aggregate data access contract for a [Comment] (Stage 18 + Stage 26).
 *
 * Repository contracts are transaction-agnostic. Transaction boundaries
 * belong to application use cases, not to this interface.
 *
 * Stage 18 semantics:
 * - [save] performs a strict INSERT.
 * - [findById] may return both ACTIVE and DELETED comments.
 * - content/reply list queries return ACTIVE comments only.
 * - comment deletion is handled as a soft-delete through [update].
 *
 * Stage 26 semantics:
 * - [findByContentId] and [findByParentCommentId] with cursor parameter
 *   support keyset pagination over the frozen ordering (created_at ASC, id ASC).
 * - LIMIT requested+1 probe technique detects has-next without a count query.
 * - The returned [CommentPage] never contains the probe row.
 */
interface CommentRepository {

    /** Persists a newly created comment aggregate and returns it. */
    fun save(comment: Comment): Comment

    /** Returns the comment with the given public id, or null when absent. */
    fun findById(id: PublicId): Comment?

    /**
     * Returns ACTIVE comments belonging to the given content.
     *
     * Results are ordered chronologically by (createdAt, id) ascending.
     */
    fun findByContentId(
        contentId: PublicId,
        limit: Int
    ): List<Comment>

    /**
     * Returns one page of at most [limit] ACTIVE comments belonging to the
     * given content, optionally starting after [cursor] (Stage 26).
     *
     * Ordering: comment.created_at ASC with comment.id ASC as the
     * deterministic tie-breaker for equal timestamps.
     *
     * Pagination mechanics: the query is executed with LIMIT limit+1 so the
     * repository can detect a following page without a separate count query.
     * The returned [CommentPage] never contains the probe row, and its
     * nextCursor is derived from the last delivered row (never the probe row).
     *
     * limit=0 returns an empty page without executing a query.
     */
    fun findByContentId(
        contentId: PublicId,
        limit: Int,
        cursor: CommentCursor?
    ): CommentPage =
        throw UnsupportedOperationException("findByContentId with cursor requires a persistence-backed CommentRepository")

    /**
     * Returns ACTIVE replies belonging to the given parent comment.
     *
     * Results are ordered chronologically by (createdAt, id) ascending.
     */
    fun findByParentCommentId(
        parentCommentId: PublicId,
        limit: Int
    ): List<Comment>

    /**
     * Returns one page of at most [limit] ACTIVE replies belonging to the
     * given parent comment, optionally starting after [cursor] (Stage 26).
     *
     * Ordering: comment.created_at ASC with comment.id ASC as the
     * deterministic tie-breaker for equal timestamps.
     *
     * Pagination mechanics: the query is executed with LIMIT limit+1 so the
     * repository can detect a following page without a separate count query.
     * The returned [CommentPage] never contains the probe row, and its
     * nextCursor is derived from the last delivered row (never the probe row).
     *
     * limit=0 returns an empty page without executing a query.
     */
    fun findByParentCommentId(
        parentCommentId: PublicId,
        limit: Int,
        cursor: CommentCursor?
    ): CommentPage =
        throw UnsupportedOperationException("findByParentCommentId with cursor requires a persistence-backed CommentRepository")

    /**
     * Updates an existing comment aggregate.
     *
     * Stage 18 currently uses this for soft deletion.
     */
    fun update(comment: Comment): Comment

    /**
     * Returns the count of ACTIVE comments belonging to the given content.
     *
     * DELETED comments are never counted.
     */
    fun countActiveByContentId(contentId: PublicId): Long =
        throw UnsupportedOperationException("countActiveByContentId requires a persistence-backed CommentRepository")
}
