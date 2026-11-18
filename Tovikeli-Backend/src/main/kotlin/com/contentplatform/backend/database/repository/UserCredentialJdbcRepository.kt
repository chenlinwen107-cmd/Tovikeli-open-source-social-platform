package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.UserCredentialRepository
import org.springframework.jdbc.core.JdbcTemplate

/**
 * JDBC implementation of [UserCredentialRepository].
 *
 * Password hashes are accessed only through this credential-specific
 * repository. The regular UserJdbcRepository deliberately does not select
 * password_hash.
 */
class UserCredentialJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : UserCredentialRepository {

    override fun findPasswordHashByUsername(username: String): String? {
        val rows = jdbcTemplate.query(
            """
            SELECT password_hash
            FROM platform_user
            WHERE LOWER(username) = LOWER(?)
            """.trimIndent(),
            { rs, _ -> rs.getString("password_hash") },
            username
        )
        return rows.firstOrNull()
    }

    override fun savePasswordHash(userId: PublicId, passwordHash: String) {
        require(passwordHash.isNotBlank()) {
            "passwordHash must not be blank"
        }

        val updated = jdbcTemplate.update(
            """
            UPDATE platform_user
            SET password_hash = ?
            WHERE id = ?
            """.trimIndent(),
            passwordHash,
            userId.value
        )

        check(updated == 1) {
            "platform user not found: ${userId.value}"
        }
    }
}
