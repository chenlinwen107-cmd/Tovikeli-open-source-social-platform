package com.contentplatform.backend.database.repository

import com.contentplatform.backend.database.entity.InstanceEntity
import com.contentplatform.backend.database.mapper.InstanceMapper
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.repository.InstanceRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * JDBC implementation of [InstanceRepository] against the `instance` table.
 */
class InstanceJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : InstanceRepository {

    private val rowMapper = RowMapper { rs, _ ->
        InstanceEntity(
            id = rs.getString("id"),
            sourceId = rs.getString("source_id"),
            domain = rs.getString("domain"),
            displayName = rs.getString("display_name"),
            enabled = rs.getBoolean("enabled"),
            allowIndex = rs.getBoolean("allow_index"),
            allowMediaPreview = rs.getBoolean("allow_media_preview"),
            allowMediaCache = rs.getBoolean("allow_media_cache"),
            allowOriginalAccess = rs.getBoolean("allow_original_access"),
            nsfwPolicy = rs.getString("nsfw_policy"),
            healthStatus = rs.getString("health_status")
        )
    }

    private val select = """
        SELECT id, source_id, domain, display_name, enabled,
               allow_index, allow_media_preview, allow_media_cache,
               allow_original_access, nsfw_policy, health_status
        FROM instance
    """.trimIndent()

    override fun findById(id: PublicId): Instance? {
        val rows = jdbcTemplate.query("$select WHERE id = ?", rowMapper, id.value)
        return rows.firstOrNull()?.let { InstanceMapper.toDomain(it) }
    }
}
