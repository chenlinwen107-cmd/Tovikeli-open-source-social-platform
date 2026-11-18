package com.contentplatform.backend.database.repository

import com.contentplatform.backend.database.entity.UserEntity
import com.contentplatform.backend.database.mapper.UserMapper
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.repository.UserRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * JDBC implementation of [UserRepository] against the `platform_user` table.
 *
 * Case-insensitive identity lookup mirrors the database's expression unique
 * index `uq_platform_user_username` on LOWER(username): both the index and the
 * lookup apply PostgreSQL LOWER, so the query is index-usable and the lookup
 * semantics can never drift from the final database guarantee.
 *
 * save() is a strict INSERT (no upsert): a platform user create must never
 * silently overwrite an existing identity. The case-insensitive unique index
 * is the final guarantee; callers (CreateUserUseCase) run the explicit
 * existence check first.
 */
class UserJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : UserRepository {

    private val rowMapper = RowMapper { rs, _ ->
        UserEntity(
            id = rs.getString("id"),
            username = rs.getString("username"),
            displayName = rs.getString("display_name"),
            avatar = rs.getString("avatar"),
            bio = rs.getString("bio"),
            gender = rs.getString("gender"),
            location = rs.getString("location"),
            cover = rs.getString("cover"),
            status = rs.getString("status"),
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at")
        )
    }

    private val select = """
        SELECT id, username, display_name, avatar, bio, gender, location, cover, status, created_at, updated_at
        FROM platform_user
    """.trimIndent()

    override fun findById(id: PublicId): User? {
        val rows = jdbcTemplate.query("$select WHERE id = ?", rowMapper, id.value)
        return rows.firstOrNull()?.let { UserMapper.toDomain(it) }
    }

    override fun findByUsernameCaseInsensitive(username: String): User? {
        val rows = jdbcTemplate.query(
            "$select WHERE LOWER(username) = LOWER(?)",
            rowMapper,
            username
        )
        return rows.firstOrNull()?.let { UserMapper.toDomain(it) }
    }

    override fun search(query: String, limit: Int): List<User> {
        val safeLimit = limit.coerceIn(1, 50)
        val pattern = "%${query.trim()}%"
        val rows = jdbcTemplate.query(
            "$select WHERE status = 'ACTIVE' AND (username ILIKE ? OR COALESCE(display_name, '') ILIKE ?) " +
                "ORDER BY CASE WHEN LOWER(username) = LOWER(?) THEN 0 " +
                "WHEN LOWER(username) LIKE LOWER(?) THEN 1 " +
                "WHEN LOWER(COALESCE(display_name, '')) LIKE LOWER(?) THEN 2 ELSE 3 END, " +
                "LOWER(COALESCE(display_name, username)), LOWER(username) LIMIT ?",
            rowMapper,
            pattern, pattern, query.trim(), "${query.trim()}%", "${query.trim()}%", safeLimit
        )
        return rows.map(UserMapper::toDomain)
    }

    override fun updateProfile(user: User): User {
        val e = UserMapper.toEntity(user)
        jdbcTemplate.update(
            """UPDATE platform_user SET display_name=?, avatar=?, bio=?, gender=?, location=?, cover=?, updated_at=? WHERE id=?""".trimIndent(),
            e.displayName, e.avatar, e.bio, e.gender, e.location, e.cover, e.updatedAt, e.id
        )
        return user
    }

    override fun save(user: User): User {
        val e = UserMapper.toEntity(user)
        jdbcTemplate.update(
            """
            INSERT INTO platform_user (
                id, username, display_name, status, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            e.id,
            e.username,
            e.displayName,
            e.status,
            e.createdAt,
            e.updatedAt
        )
        return user
    }
}
