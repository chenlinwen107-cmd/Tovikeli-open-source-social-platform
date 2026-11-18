package com.contentplatform.backend.database.repository

import com.contentplatform.backend.database.entity.TagEntity
import com.contentplatform.backend.database.mapper.TagMapper
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.domain.tag.TagOrigin
import com.contentplatform.backend.repository.TagRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * JDBC implementation of [TagRepository] against the `tag` table.
 *
 * PLATFORM tags are keyed by verbatim name; EXTERNAL tags by
 * (instance_id, source_tag_id). Partial unique indexes keep the two identity
 * spaces separate in the database.
 */
class TagJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : TagRepository {

    private val rowMapper = RowMapper { rs, _ ->
        TagEntity(
            id = rs.getString("id"),
            name = rs.getString("name"),
            normalizedName = rs.getString("normalized_name"),
            origin = rs.getString("origin"),
            sourceTagId = rs.getString("source_tag_id"),
            instanceId = rs.getString("instance_id"),
            sourceId = rs.getString("source_id"),
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at")
        )
    }

    private val select = """
        SELECT id, name, normalized_name, origin,
               source_tag_id, instance_id, source_id,
               created_at, updated_at
        FROM tag
    """.trimIndent()

    override fun findById(id: PublicId): Tag? {
        val rows = jdbcTemplate.query("$select WHERE id = ?", rowMapper, id.value)
        return rows.firstOrNull()?.let { TagMapper.toDomain(it) }
    }

    override fun findByInstanceAndSourceTagId(
        instanceId: PublicId,
        sourceTagId: String
    ): Tag? {
        val rows = jdbcTemplate.query(
            "$select WHERE instance_id = ? AND source_tag_id = ? AND origin = ?",
            rowMapper,
            instanceId.value,
            sourceTagId,
            TagOrigin.EXTERNAL.name
        )
        return rows.firstOrNull()?.let { TagMapper.toDomain(it) }
    }

    override fun findPlatformByName(name: String): Tag? {
        val rows = jdbcTemplate.query(
            "$select WHERE name = ? AND origin = ?",
            rowMapper,
            name,
            TagOrigin.PLATFORM.name
        )
        return rows.firstOrNull()?.let { TagMapper.toDomain(it) }
    }

    override fun search(query: String, limit: Int): List<Tag> {
        require(limit in 0..MAX_TAG_SEARCH_LIMIT) {
            "limit must be in the range 0..$MAX_TAG_SEARCH_LIMIT"
        }
        if (limit == 0) return emptyList()

        // Basic normalized-name matching (Stage 15-C): case-insensitive
        // substring match against tag.normalized_name ONLY (never full-text,
        // never trigram, never name/identity matching). Ordering is frozen as
        // normalized_name ASC, name ASC, id ASC. Tags without a normalizedName
        // can never match because the predicate itself is on normalized_name.
        val rows = jdbcTemplate.query(
            "$select WHERE normalized_name ILIKE ? ORDER BY normalized_name ASC, name ASC, id ASC LIMIT ?",
            rowMapper,
            "%$query%",
            limit
        )
        return rows.map { TagMapper.toDomain(it) }
    }

    override fun save(tag: Tag): Tag {
        val e = TagMapper.toEntity(tag)
        if (tag.origin == TagOrigin.EXTERNAL) {
            saveExternal(e)
        } else {
            savePlatform(e)
        }
        return tag
    }

    private companion object {
        /** Mirrors the project's shared browse/search limit bound (0..100). */
        const val MAX_TAG_SEARCH_LIMIT: Int = 100
    }

    private fun saveExternal(e: TagEntity) {
        jdbcTemplate.update(
            """
            INSERT INTO tag (
                id, name, normalized_name, origin,
                source_tag_id, instance_id, source_id,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instance_id, source_tag_id) WHERE origin = 'EXTERNAL'
            DO UPDATE SET
                name = EXCLUDED.name,
                normalized_name = EXCLUDED.normalized_name,
                source_id = EXCLUDED.source_id,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
            e.id,
            e.name,
            e.normalizedName,
            e.origin,
            e.sourceTagId,
            e.instanceId,
            e.sourceId,
            e.createdAt,
            e.updatedAt
        )
    }

    private fun savePlatform(e: TagEntity) {
        jdbcTemplate.update(
            """
            INSERT INTO tag (
                id, name, normalized_name, origin,
                source_tag_id, instance_id, source_id,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (name) WHERE origin = 'PLATFORM'
            DO UPDATE SET
                normalized_name = EXCLUDED.normalized_name,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
            e.id,
            e.name,
            e.normalizedName,
            e.origin,
            e.sourceTagId,
            e.instanceId,
            e.sourceId,
            e.createdAt,
            e.updatedAt
        )
    }
}
