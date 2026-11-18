package com.contentplatform.backend.database.repository

import com.contentplatform.backend.database.entity.AuthorEntity
import com.contentplatform.backend.database.mapper.AuthorMapper
import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.AuthorRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * JDBC implementation of [AuthorRepository] against the `author` table.
 *
 * save() persists the current aggregate state. The implementation uses the
 * natural key (instance_id, source_author_id) as the conflict target; the
 * contract does not promise any specific upsert mechanism.
 */
class AuthorJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : AuthorRepository {

    private val rowMapper = RowMapper { rs, _ ->
        AuthorEntity(
            id = rs.getString("id"),
            instanceId = rs.getString("instance_id"),
            sourceAuthorId = rs.getString("source_author_id"),
            name = rs.getString("name"),
            username = rs.getString("username"),
            displayName = rs.getString("display_name"),
            url = rs.getString("url"),
            avatar = rs.getString("avatar"),
            bio = rs.getString("bio"),
            status = rs.getString("status")
        )
    }

    private val select = """
        SELECT id, instance_id, source_author_id, name, username,
               display_name, url, avatar, bio, status
        FROM author
    """.trimIndent()

    override fun findById(id: PublicId): Author? {
        val rows = jdbcTemplate.query("$select WHERE id = ?", rowMapper, id.value)
        return rows.firstOrNull()?.let { AuthorMapper.toDomain(it) }
    }

    override fun findByInstanceAndSourceAuthorId(
        instanceId: PublicId,
        sourceAuthorId: String
    ): Author? {
        val rows = jdbcTemplate.query(
            "$select WHERE instance_id = ? AND source_author_id = ?",
            rowMapper,
            instanceId.value,
            sourceAuthorId
        )
        return rows.firstOrNull()?.let { AuthorMapper.toDomain(it) }
    }

    override fun save(author: Author): Author {
        val e = AuthorMapper.toEntity(author)
        jdbcTemplate.update(
            """
            INSERT INTO author (
                id, instance_id, source_author_id, name, username,
                display_name, url, avatar, bio, status
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instance_id, source_author_id) DO UPDATE SET
                name = EXCLUDED.name,
                username = EXCLUDED.username,
                display_name = EXCLUDED.display_name,
                url = EXCLUDED.url,
                avatar = EXCLUDED.avatar,
                bio = EXCLUDED.bio,
                status = EXCLUDED.status
            """.trimIndent(),
            e.id,
            e.instanceId,
            e.sourceAuthorId,
            e.name,
            e.username,
            e.displayName,
            e.url,
            e.avatar,
            e.bio,
            e.status
        )
        return author
    }
}
