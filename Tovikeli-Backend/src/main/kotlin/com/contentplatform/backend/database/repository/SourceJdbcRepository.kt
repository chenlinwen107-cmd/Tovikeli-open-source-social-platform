package com.contentplatform.backend.database.repository

import com.contentplatform.backend.database.entity.SourceEntity
import com.contentplatform.backend.database.mapper.SourceMapper
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.source.Source
import com.contentplatform.backend.repository.SourceRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * JDBC implementation of [SourceRepository] against the `source` table.
 */
class SourceJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : SourceRepository {

    private val rowMapper = RowMapper { rs, _ ->
        SourceEntity(
            id = rs.getString("id"),
            name = rs.getString("name"),
            type = rs.getString("type"),
            enabled = rs.getBoolean("enabled")
        )
    }

    override fun findById(id: PublicId): Source? {
        val rows = jdbcTemplate.query(
            "SELECT id, name, type, enabled FROM source WHERE id = ?",
            rowMapper,
            id.value
        )
        return rows.firstOrNull()?.let { SourceMapper.toDomain(it) }
    }
}
