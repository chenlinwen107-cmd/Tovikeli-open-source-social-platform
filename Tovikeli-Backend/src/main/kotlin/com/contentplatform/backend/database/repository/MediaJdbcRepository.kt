package com.contentplatform.backend.database.repository

import com.contentplatform.backend.database.entity.MediaEntity
import com.contentplatform.backend.database.mapper.MediaMapper
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.media.Media
import com.contentplatform.backend.repository.MediaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * JDBC implementation of [MediaRepository] against the `media` table.
 *
 * sha256 is a dedup lookup key only (normal index, no UNIQUE constraint).
 * save() persists the aggregate state keyed by the supplied Domain PublicId
 * (id TEXT PRIMARY KEY). No Media identity rule is derived here.
 */
class MediaJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : MediaRepository {

    private val rowMapper = RowMapper { rs, _ ->
        MediaEntity(
            id = rs.getString("id"),
            sha256 = rs.getString("sha256"),
            sourceId = rs.getString("source_id"),
            instanceId = rs.getString("instance_id"),
            thumbnailUrl = rs.getString("thumbnail_url"),
            previewUrl = rs.getString("preview_url"),
            originalUrl = rs.getString("original_url"),
            altText = rs.getString("alt_text"),
            license = rs.getString("license"),
            metadataJson = rs.getString("metadata"),
            kind = rs.getString("kind"),
            status = rs.getString("status"),
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at")
        )
    }

    private val select = """
        SELECT id, sha256, source_id, instance_id,
               thumbnail_url, preview_url, original_url,
               alt_text, license, metadata::text AS metadata, kind,
               status, created_at, updated_at
        FROM media
    """.trimIndent()

    override fun findById(id: PublicId): Media? {
        val rows = jdbcTemplate.query("$select WHERE id = ?", rowMapper, id.value)
        return rows.firstOrNull()?.let { MediaMapper.toDomain(it) }
    }

    override fun findBySha256(sha256: String): Media? {
        val rows = jdbcTemplate.query(
            "$select WHERE sha256 = ? LIMIT 1",
            rowMapper,
            sha256
        )
        return rows.firstOrNull()?.let { MediaMapper.toDomain(it) }
    }

    override fun save(media: Media): Media {
        val e = MediaMapper.toEntity(media)
        jdbcTemplate.update(
            """
            INSERT INTO media (
                id, sha256, source_id, instance_id,
                thumbnail_url, preview_url, original_url,
                alt_text, license, metadata, kind,
                status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                sha256 = EXCLUDED.sha256,
                source_id = EXCLUDED.source_id,
                instance_id = EXCLUDED.instance_id,
                thumbnail_url = EXCLUDED.thumbnail_url,
                preview_url = EXCLUDED.preview_url,
                original_url = EXCLUDED.original_url,
                alt_text = EXCLUDED.alt_text,
                license = EXCLUDED.license,
                metadata = EXCLUDED.metadata,
                updated_at = EXCLUDED.updated_at
            """.trimIndent(),
            e.id,
            e.sha256,
            e.sourceId,
            e.instanceId,
            e.thumbnailUrl,
            e.previewUrl,
            e.originalUrl,
            e.altText,
            e.license,
            e.metadataJson,
            e.kind,
            e.status,
            e.createdAt,
            e.updatedAt
        )
        return media
    }
}
