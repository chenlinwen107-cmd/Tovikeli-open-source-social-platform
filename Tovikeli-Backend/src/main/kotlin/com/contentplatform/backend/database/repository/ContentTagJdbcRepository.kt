package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.ContentTagRepository
import org.springframework.jdbc.core.JdbcTemplate

/**
 * JDBC implementation of [ContentTagRepository] against the `content_tag`
 * association table (Stage 15-B).
 *
 * Every write is a single batched INSERT ... ON CONFLICT (content_id, tag_id)
 * DO NOTHING, so:
 *  - multiple tag ids for one content are persisted in ONE batched write (no
 *    per-tag Repository query / transaction / N+1);
 *  - duplicate pairs are idempotent (never inserted twice, never deleted);
 *  - an empty tag list performs no database write at all.
 *
 * This repository never creates Content/Tag aggregates and never queries them;
 * it owns only the association rows.
 */
class ContentTagJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : ContentTagRepository {

    override fun attach(contentId: PublicId, tagId: PublicId): Unit =
        attachAll(contentId, listOf(tagId))

    override fun attachAll(contentId: PublicId, tagIds: List<PublicId>) {
        if (tagIds.isEmpty()) return

        val batchArgs = tagIds.map { tagId ->
            arrayOf<Any?>(contentId.value, tagId.value)
        }
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO content_tag (content_id, tag_id)
            VALUES (?, ?)
            ON CONFLICT (content_id, tag_id) DO NOTHING
            """.trimIndent(),
            batchArgs
        )
    }
}
