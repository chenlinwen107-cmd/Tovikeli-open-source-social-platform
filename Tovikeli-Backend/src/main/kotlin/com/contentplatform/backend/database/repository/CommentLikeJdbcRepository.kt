package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CommentLikeRepository
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

class CommentLikeJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : CommentLikeRepository {

    override fun exists(userId: PublicId, commentId: PublicId): Boolean =
        jdbcTemplate.query(
            """
            SELECT 1 FROM comment_like
            WHERE user_id = ? AND comment_id = ?
            LIMIT 1
            """.trimIndent(),
            { rs, _ -> rs.getInt(1) },
            userId.value,
            commentId.value
        ).isNotEmpty()

    override fun countByCommentId(commentId: PublicId): Long =
        jdbcTemplate.query(
            "SELECT COUNT(*) FROM comment_like WHERE comment_id = ?",
            { rs, _ -> rs.getLong(1) },
            commentId.value
        ).firstOrNull() ?: 0L

    override fun save(userId: PublicId, commentId: PublicId): Boolean {
        val inserted = jdbcTemplate.update(
            """
            INSERT INTO comment_like (id, user_id, comment_id, created_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, comment_id) DO NOTHING
            """.trimIndent(),
            "clike_" + UUID.randomUUID().toString().replace("-", ""),
            userId.value,
            commentId.value,
            System.currentTimeMillis()
        )
        return inserted > 0
    }

    override fun delete(userId: PublicId, commentId: PublicId): Boolean =
        jdbcTemplate.update(
            """
            DELETE FROM comment_like
            WHERE user_id = ? AND comment_id = ?
            """.trimIndent(),
            userId.value,
            commentId.value
        ) > 0
}
