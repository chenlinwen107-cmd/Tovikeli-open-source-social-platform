package com.contentplatform.backend.database.repository

import com.contentplatform.backend.database.entity.CommentEntity
import com.contentplatform.backend.database.mapper.CommentMapper
import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentCursor
import com.contentplatform.backend.domain.comment.CommentPage
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CommentRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * JDBC implementation of [CommentRepository] against the
 * `content_comment` table.
 *
 * Stage 18 semantics:
 * - save() is a strict INSERT.
 * - findById() returns both ACTIVE and DELETED comments.
 * - content/reply list queries return ACTIVE comments only.
 * - update() persists the mutable comment fields used by soft deletion.
 * - no physical DELETE is performed by this repository.
 *
 * Stage 26 semantics:
 * - [findByContentId] and [findByParentCommentId] with cursor parameter
 *   support keyset pagination with LIMIT limit+1 probe technique.
 */
class CommentJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : CommentRepository {

    private val rowMapper = RowMapper { rs, _ ->
        CommentEntity(
            id = rs.getString("id"),
            userId = rs.getString("user_id"),
            contentId = rs.getString("content_id"),
            parentCommentId = rs.getString("parent_comment_id"),
            text = rs.getString("text"),
            status = rs.getString("status"),
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at")
        )
    }

    private val select = """
        SELECT id, user_id, content_id, parent_comment_id, text, status, created_at, updated_at
        FROM content_comment
    """.trimIndent()

    override fun save(comment: Comment): Comment {
        val e = CommentMapper.toEntity(comment)

        jdbcTemplate.update(
            """
            INSERT INTO content_comment (
                id, user_id, content_id, parent_comment_id, text, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            e.id,
            e.userId,
            e.contentId,
            e.parentCommentId,
            e.text,
            e.status,
            e.createdAt,
            e.updatedAt
        )

        return comment
    }

    override fun findById(id: PublicId): Comment? {
        val rows = jdbcTemplate.query(
            "$select WHERE id = ?",
            rowMapper,
            id.value
        )

        return rows.firstOrNull()?.let(CommentMapper::toDomain)
    }

    override fun findByContentId(
        contentId: PublicId,
        limit: Int
    ): List<Comment> {
        val rows = jdbcTemplate.query(
            """
            $select
            WHERE content_id = ?
              AND status = 'ACTIVE'
            ORDER BY created_at ASC, id ASC
            LIMIT ?
            """.trimIndent(),
            rowMapper,
            contentId.value,
            limit
        )

        return rows.map(CommentMapper::toDomain)
    }

    override fun findByContentId(
        contentId: PublicId,
        limit: Int,
        cursor: CommentCursor?
    ): CommentPage {
        if (limit == 0) return CommentPage(items = emptyList(), nextCursor = null)

        val cursorPredicate = if (cursor != null) {
            "\n              AND (created_at > ? OR (created_at = ? AND id > ?))"
        } else {
            ""
        }

        val probeLimit = limit + 1
        val args = buildList<Any?> {
            add(contentId.value)
            if (cursor != null) {
                add(cursor.createdAt)
                add(cursor.createdAt)
                add(cursor.commentId.value)
            }
            add(probeLimit)
        }

        val fetched = jdbcTemplate.query(
            """
            $select
            WHERE content_id = ?
              AND status = 'ACTIVE'$cursorPredicate
            ORDER BY created_at ASC, id ASC
            LIMIT ?
            """.trimIndent(),
            rowMapper,
            *args.toTypedArray()
        )

        if (fetched.isEmpty()) return CommentPage(items = emptyList(), nextCursor = null)

        val hasNext = fetched.size > limit
        val delivered = if (hasNext) fetched.subList(0, limit) else fetched
        val items = delivered.map(CommentMapper::toDomain)
        val nextCursor = if (hasNext) {
            val last = delivered.last()
            CommentCursor(
                createdAt = last.createdAt,
                commentId = PublicId(last.id)
            )
        } else {
            null
        }

        return CommentPage(items = items, nextCursor = nextCursor)
    }

    override fun findByParentCommentId(
        parentCommentId: PublicId,
        limit: Int
    ): List<Comment> {
        val rows = jdbcTemplate.query(
            """
            $select
            WHERE parent_comment_id = ?
              AND status = 'ACTIVE'
            ORDER BY created_at ASC, id ASC
            LIMIT ?
            """.trimIndent(),
            rowMapper,
            parentCommentId.value,
            limit
        )

        return rows.map(CommentMapper::toDomain)
    }

    override fun findByParentCommentId(
        parentCommentId: PublicId,
        limit: Int,
        cursor: CommentCursor?
    ): CommentPage {
        if (limit == 0) return CommentPage(items = emptyList(), nextCursor = null)

        val cursorPredicate = if (cursor != null) {
            "\n              AND (created_at > ? OR (created_at = ? AND id > ?))"
        } else {
            ""
        }

        val probeLimit = limit + 1
        val args = buildList<Any?> {
            add(parentCommentId.value)
            if (cursor != null) {
                add(cursor.createdAt)
                add(cursor.createdAt)
                add(cursor.commentId.value)
            }
            add(probeLimit)
        }

        val fetched = jdbcTemplate.query(
            """
            $select
            WHERE parent_comment_id = ?
              AND status = 'ACTIVE'$cursorPredicate
            ORDER BY created_at ASC, id ASC
            LIMIT ?
            """.trimIndent(),
            rowMapper,
            *args.toTypedArray()
        )

        if (fetched.isEmpty()) return CommentPage(items = emptyList(), nextCursor = null)

        val hasNext = fetched.size > limit
        val delivered = if (hasNext) fetched.subList(0, limit) else fetched
        val items = delivered.map(CommentMapper::toDomain)
        val nextCursor = if (hasNext) {
            val last = delivered.last()
            CommentCursor(
                createdAt = last.createdAt,
                commentId = PublicId(last.id)
            )
        } else {
            null
        }

        return CommentPage(items = items, nextCursor = nextCursor)
    }

    override fun update(comment: Comment): Comment {
        val e = CommentMapper.toEntity(comment)

        jdbcTemplate.update(
            """
            UPDATE content_comment
            SET text = ?, status = ?, updated_at = ?
            WHERE id = ?
            """.trimIndent(),
            e.text,
            e.status,
            e.updatedAt,
            e.id
        )

        return comment
    }

    override fun countActiveByContentId(contentId: PublicId): Long {
        val rows = jdbcTemplate.query(
            """
            SELECT COUNT(*) AS cnt FROM content_comment
            WHERE content_id = ? AND status = 'ACTIVE'
            """.trimIndent(),
            { rs, _ -> rs.getLong("cnt") },
            contentId.value
        )
        return rows.firstOrNull() ?: 0L
    }
}
