package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * Unit tests for [UserJdbcRepository] (Stage 16).
 *
 * No database is involved. [RecordingJdbcTemplate] subclasses [JdbcTemplate]
 * and intercepts every query/update call so the repository code path (SQL text,
 * bound arguments, row mapping) runs against recorded calls / scripted result
 * rows.
 *
 * The database-level case-insensitive username uniqueness is encoded in the
 * V5 migration (`uq_platform_user_username` expression UNIQUE index on
 * LOWER(username)); these tests assert the repository SQL mirrors that with
 * the same LOWER predicate and that save() is a strict INSERT (no upsert,
 * never an overwrite).
 */
class UserJdbcRepositoryTest {

    // ------------------------------------------------------------------
    // Recording JdbcTemplate
    // ------------------------------------------------------------------

    private class RecordingJdbcTemplate : JdbcTemplate() {
        val executedSql = mutableListOf<String>()
        val queryArgs = mutableListOf<List<Any?>>()
        val updateArgs = mutableListOf<List<Any?>>()
        var rows: List<Map<String, Any?>> = emptyList()

        override fun <T> query(sql: String, rowMapper: RowMapper<T>, vararg args: Any?): List<T> {
            executedSql += sql
            queryArgs += args.toList()
            return mapRows(rows, rowMapper)
        }

        override fun update(sql: String, vararg args: Any?): Int {
            executedSql += sql
            updateArgs += args.toList()
            return 1
        }

        private fun <T> mapRows(rows: List<Map<String, Any?>>, rowMapper: RowMapper<T>): List<T> {
            if (rows.isEmpty()) return emptyList()
            val rs = resultSetFor(rows)
            val mapped = ArrayList<T>(rows.size)
            var rowNum = 0
            while (rs.next()) {
                mapped.add(rowMapper.mapRow(rs, rowNum))
                rowNum++
            }
            return mapped
        }

        /** Minimal dynamic ResultSet over a list of column-label -> value maps. */
        private fun resultSetFor(rows: List<Map<String, Any?>>): ResultSet {
            val cursor = AtomicInteger(-1)
            var wasNull = false
            val loader = UserJdbcRepository::class.java.classLoader
            val proxy = Proxy.newProxyInstance(
                loader,
                arrayOf(ResultSet::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "next" -> cursor.incrementAndGet() < rows.size
                    "wasNull" -> wasNull
                    "getString" -> {
                        val label = args?.get(0) as? String
                        val value = rows.getOrNull(cursor.get())?.get(label)
                        wasNull = value == null
                        value as? String
                    }
                    "getLong" -> {
                        val label = args?.get(0) as? String
                        val value = rows.getOrNull(cursor.get())?.get(label)
                        wasNull = value == null
                        (value as? Number)?.toLong() ?: 0L
                    }
                    else -> defaultFor(method.returnType)
                }
            }
            @Suppress("UNCHECKED_CAST")
            return proxy as ResultSet
        }

        private fun defaultFor(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            else -> null
        }
    }

    private fun userRow(
        id: String,
        username: String,
        displayName: String? = null,
        status: String = "ACTIVE",
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L
    ): Map<String, Any?> = mapOf(
        "id" to id,
        "username" to username,
        "display_name" to displayName,
        "avatar" to null,
        "bio" to null,
        "gender" to "secret",
        "location" to null,
        "cover" to null,
        "status" to status,
        "created_at" to createdAt,
        "updated_at" to updatedAt
    )

    private fun repository(template: RecordingJdbcTemplate): UserJdbcRepository =
        UserJdbcRepository(template)

    // ------------------------------------------------------------------
    // findById: SQL shape + row mapping
    // ------------------------------------------------------------------

    @Test
    fun findById_queriesByPrimaryKeyAndMapsTheAggregate() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(userRow("usr_1", "alice", "Alice", "ACTIVE", 1000L, 2000L))
        val repo = repository(template)

        val user = repo.findById(PublicId("usr_1"))

        assertTrue(template.executedSql.single().contains("SELECT id, username, display_name, avatar, bio, gender, location, cover, status, created_at, updated_at"))
        assertTrue(template.executedSql.single().contains("FROM platform_user"))
        assertTrue(template.executedSql.single().contains("WHERE id = ?"))
        assertEquals(listOf<Any?>("usr_1"), template.queryArgs.single())
        assertEquals("usr_1", user?.id?.value)
        assertEquals("alice", user?.username)
        assertEquals("Alice", user?.displayName)
        assertEquals(UserStatus.ACTIVE, user?.status)
        assertEquals(1000L, user?.createdAt)
        assertEquals(2000L, user?.updatedAt)
    }

    @Test
    fun findById_nullDisplayNameAndDisabledStatus_areMappedCorrectly() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(userRow("usr_2", "bob", null, "DISABLED"))
        val repo = repository(template)

        val user = repo.findById(PublicId("usr_2"))

        assertNull(user?.displayName)
        assertEquals(UserStatus.DISABLED, user?.status)
    }

    @Test
    fun findById_whenAbsent_returnsNull() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()
        val repo = repository(template)

        assertNull(repo.findById(PublicId("usr_missing")))
    }

    // ------------------------------------------------------------------
    // findByUsernameCaseInsensitive: mirrors the DB LOWER index predicate
    // ------------------------------------------------------------------

    @Test
    fun findByUsernameCaseInsensitive_usesLowerPredicateOnBothSides() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(userRow("usr_1", "Alice"))
        val repo = repository(template)

        val user = repo.findByUsernameCaseInsensitive("alice")

        val sql = template.executedSql.single()
        assertTrue(sql.contains("WHERE LOWER(username) = LOWER(?)"),
            "the lookup must mirror the expression unique index on LOWER(username)")
        assertEquals(listOf<Any?>("alice"), template.queryArgs.single())
        assertEquals("Alice", user?.username, "the stored verbatim value survives the lookup")
    }

    @Test
    fun findByUsernameCaseInsensitive_whenAbsent_returnsNull() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()
        val repo = repository(template)

        assertNull(repo.findByUsernameCaseInsensitive("nobody"))
    }

    // ------------------------------------------------------------------
    // save: strict INSERT, no upsert
    // ------------------------------------------------------------------

    @Test
    fun save_issuesStrictInsertWithoutOnConflict() {
        val template = RecordingJdbcTemplate()
        val repo = repository(template)
        val user = User.create(username = "alice", displayName = "Alice Example")

        val saved = repo.save(user)

        assertTrue(saved === user, "save must return the supplied aggregate")
        val sql = template.executedSql.single()
        assertTrue(sql.startsWith("INSERT INTO platform_user"), "save must insert into platform_user")
        assertTrue(
            sql.contains("id, username, display_name, status, created_at, updated_at"),
            "all six columns"
        )
        assertTrue(sql.contains("VALUES (?, ?, ?, ?, ?, ?)"), "six bound parameters")
        assertFalse(sql.contains("ON CONFLICT"), "a platform user create must never upsert/overwrite")
        assertFalse(sql.contains("UPDATE"), "no update path on save")
        val args = template.updateArgs.single()
        assertEquals(user.id.value, args[0])
        assertEquals("alice", args[1])
        assertEquals("Alice Example", args[2])
        assertEquals(UserStatus.ACTIVE.name, args[3], "status is stored as the enum name TEXT")
        assertEquals(user.createdAt, args[4])
        assertEquals(user.updatedAt, args[5])
    }

    @Test
    fun save_thenFind_roundTripsTheAggregate() {
        val template = RecordingJdbcTemplate()
        val repo = repository(template)
        val user = User.create(username = "Bob", displayName = null)

        repo.save(user)
        template.rows = listOf(userRow(user.id.value, user.username, user.displayName, user.status.name, user.createdAt, user.updatedAt))

        val reloaded = repo.findById(user.id)

        assertEquals(user, reloaded)
    }
}
