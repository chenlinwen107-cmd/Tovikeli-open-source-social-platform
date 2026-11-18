package com.contentplatform.backend.database.repository

import com.contentplatform.backend.database.entity.InteractionEntity
import com.contentplatform.backend.database.mapper.InteractionMapper
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.Interaction
import com.contentplatform.backend.domain.interaction.InteractionCursor
import com.contentplatform.backend.domain.interaction.InteractionPage
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.repository.InteractionRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * JDBC implementation of [InteractionRepository] against the
 * `content_interaction` table.
 *
 * save() is a strict INSERT (no upsert): an interaction create must never
 * silently overwrite an existing row. The database UNIQUE constraint
 * (user_id, content_id, type) is the final concurrency protection; callers
 * (CreateInteractionUseCase) run the explicit natural-key check first and
 * translate any concurrent duplicate-key violation themselves.
 *
 * deleteByUserContentType() is a physical DELETE (no soft delete, no history)
 * returning whether a row was actually removed.
 */
class InteractionJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : InteractionRepository {

    private val rowMapper = RowMapper { rs, _ ->
        InteractionEntity(
            id = rs.getString("id"),
            userId = rs.getString("user_id"),
            contentId = rs.getString("content_id"),
            type = rs.getString("type"),
            createdAt = rs.getLong("created_at")
        )
    }

    private val select = """
        SELECT id, user_id, content_id, type, created_at
        FROM content_interaction
    """.trimIndent()

    override fun save(interaction: Interaction): Interaction {
        val e = InteractionMapper.toEntity(interaction)
        jdbcTemplate.update(
            """
            INSERT INTO content_interaction (
                id, user_id, content_id, type, created_at
            ) VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            e.id,
            e.userId,
            e.contentId,
            e.type,
            e.createdAt
        )
        return interaction
    }

    override fun findById(id: PublicId): Interaction? {
        val rows = jdbcTemplate.query("$select WHERE id = ?", rowMapper, id.value)
        return rows.firstOrNull()?.let { InteractionMapper.toDomain(it) }
    }

    override fun findByUserContentType(
        userId: PublicId,
        contentId: PublicId,
        type: InteractionType
    ): Interaction? {
        val rows = jdbcTemplate.query(
            "$select WHERE user_id = ? AND content_id = ? AND type = ?",
            rowMapper,
            userId.value,
            contentId.value,
            type.name
        )
        return rows.firstOrNull()?.let { InteractionMapper.toDomain(it) }
    }

    override fun deleteByUserContentType(
        userId: PublicId,
        contentId: PublicId,
        type: InteractionType
    ): Boolean {
        val deleted = jdbcTemplate.update(
            """
            DELETE FROM content_interaction
            WHERE user_id = ? AND content_id = ? AND type = ?
            """.trimIndent(),
            userId.value,
            contentId.value,
            type.name
        )
        return deleted > 0
    }

    override fun findByUserId(
        userId: PublicId,
        type: InteractionType?,
        limit: Int,
        cursor: InteractionCursor?
    ): InteractionPage {
        if (limit == 0) return InteractionPage(items = emptyList(), nextCursor = null)

        val typePredicate = if (type != null) "\n              AND type = ?" else ""
        val cursorPredicate = if (cursor != null) {
            "\n              AND (created_at < ? OR (created_at = ? AND id < ?))"
        } else {
            ""
        }

        val probeLimit = limit + 1
        val args = buildList<Any?> {
            add(userId.value)
            if (type != null) add(type.name)
            if (cursor != null) {
                add(cursor.createdAt)
                add(cursor.createdAt)
                add(cursor.interactionId.value)
            }
            add(probeLimit)
        }

        val fetched = jdbcTemplate.query(
            """
            SELECT id, user_id, content_id, type, created_at
            FROM content_interaction
            WHERE user_id = ?$typePredicate$cursorPredicate
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """.trimIndent(),
            rowMapper,
            *args.toTypedArray()
        )

        if (fetched.isEmpty()) return InteractionPage(items = emptyList(), nextCursor = null)

        val hasNext = fetched.size > limit
        val delivered = if (hasNext) fetched.subList(0, limit) else fetched
        val items = delivered.map { InteractionMapper.toDomain(it) }
        val nextCursor = if (hasNext) {
            val last = delivered.last()
            InteractionCursor(
                createdAt = last.createdAt,
                interactionId = PublicId(last.id)
            )
        } else {
            null
        }

        return InteractionPage(items = items, nextCursor = nextCursor)
    }

    override fun countByContentId(contentId: PublicId): Map<InteractionType, Long> {
        val rows = jdbcTemplate.query(
            """
            SELECT type, COUNT(*) AS cnt
            FROM content_interaction
            WHERE content_id = ?
            GROUP BY type
            """.trimIndent(),
            { rs, _ ->
                val type = InteractionType.valueOf(rs.getString("type"))
                val cnt = rs.getLong("cnt")
                type to cnt
            },
            contentId.value
        )
        return rows.toMap()
    }

    override fun findBookmarks(userId: PublicId, limit: Int, offset: Int): List<Interaction> {
        require(limit in 1..100)
        require(offset >= 0)
        return jdbcTemplate.query(
            """SELECT id,user_id,content_id,type,created_at
               FROM content_interaction
               WHERE user_id = ? AND type = 'BOOKMARK'
               ORDER BY created_at DESC,id DESC
               LIMIT ? OFFSET ?""".trimIndent(),
            rowMapper,
            userId.value, limit + 1, offset
        ).take(limit).map { InteractionMapper.toDomain(it) }
    }

    override fun findByUserAndContent(
        userId: PublicId,
        contentId: PublicId
    ): List<Interaction> {
        val rows = jdbcTemplate.query(
            "$select WHERE user_id = ? AND content_id = ?",
            rowMapper,
            userId.value,
            contentId.value
        )
        return rows.map { InteractionMapper.toDomain(it) }
    }
}
