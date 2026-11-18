package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.Interaction
import com.contentplatform.backend.domain.interaction.InteractionCursor
import com.contentplatform.backend.domain.interaction.InteractionPage
import com.contentplatform.backend.domain.interaction.InteractionType
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * Unit tests for [InteractionJdbcRepository] (Stage 17).
 *
 * No database is involved. [RecordingJdbcTemplate] subclasses [JdbcTemplate]
 * and intercepts every query/update call so the repository code path (SQL text,
 * bound arguments, row mapping, delete result) runs against recorded calls /
 * scripted result rows.
 *
 * The database-level guarantees are encoded in the V6 migration
 * (PRIMARY KEY, UNIQUE (user_id, content_id, type), FK ON DELETE RESTRICT,
 * access indexes); these tests assert the repository mirrors those semantics:
 * strict INSERT (no upsert), natural-key lookup with the exact triple, and a
 * physical DELETE whose return value reflects whether a row was removed.
 */
class InteractionJdbcRepositoryTest {

    // ------------------------------------------------------------------
    // Recording JdbcTemplate
    // ------------------------------------------------------------------

    private class RecordingJdbcTemplate : JdbcTemplate() {
        val executedSql = mutableListOf<String>()
        val queryArgs = mutableListOf<List<Any?>>()
        val updateArgs = mutableListOf<List<Any?>>()
        var rows: List<Map<String, Any?>> = emptyList()
        var updateResult: Int = 1

        override fun <T> query(sql: String, rowMapper: RowMapper<T>, vararg args: Any?): List<T> {
            executedSql += sql
            queryArgs += args.toList()
            return mapRows(rows, rowMapper)
        }

        override fun update(sql: String, vararg args: Any?): Int {
            executedSql += sql
            updateArgs += args.toList()
            return updateResult
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
            val loader = InteractionJdbcRepository::class.java.classLoader
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

    // ------------------------------------------------------------------
    // Row fixture (column labels exactly as read by the repository)
    // ------------------------------------------------------------------

    private fun interactionRow(
        id: String,
        userId: String,
        contentId: String,
        type: String,
        createdAt: Long = 1000L
    ): Map<String, Any?> = mapOf(
        "id" to id,
        "user_id" to userId,
        "content_id" to contentId,
        "type" to type,
        "created_at" to createdAt
    )

    private fun repository(template: RecordingJdbcTemplate): InteractionJdbcRepository =
        InteractionJdbcRepository(template)

    private fun interaction(
        type: InteractionType = InteractionType.LIKE
    ): Interaction = Interaction.create(
        userId = PublicId("usr_1"),
        contentId = PublicId("ct_1"),
        type = type
    )

    // ------------------------------------------------------------------
    // save: strict INSERT, no upsert
    // ------------------------------------------------------------------

    @Test
    fun save_issuesStrictInsertWithAllFiveColumnsAndReturnsSameAggregate() {
        val template = RecordingJdbcTemplate()
        val repo = repository(template)
        val value = interaction(InteractionType.BOOKMARK)

        val saved = repo.save(value)

        assertTrue(saved === value, "save must return the supplied aggregate")
        val sql = template.executedSql.single()
        assertTrue(sql.startsWith("INSERT INTO content_interaction"))
        assertTrue(
            sql.contains("id, user_id, content_id, type, created_at"),
            "all five columns"
        )
        assertTrue(sql.contains("VALUES (?, ?, ?, ?, ?)"), "five bound parameters")
        assertFalse(sql.contains("ON CONFLICT"), "an interaction create must never upsert/overwrite")
        assertFalse(sql.contains("UPDATE"), "no update path on save")
        val args = template.updateArgs.single()
        assertEquals(value.id.value, args[0])
        assertEquals("usr_1", args[1])
        assertEquals("ct_1", args[2])
        assertEquals("BOOKMARK", args[3], "type is stored as the enum name TEXT")
        assertEquals(value.createdAt, args[4])
    }

    // ------------------------------------------------------------------
    // findById
    // ------------------------------------------------------------------

    @Test
    fun findById_queriesByPrimaryKeyAndMapsTheAggregate() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 1000L)
        )
        val repo = repository(template)

        val found = repo.findById(PublicId("int_1"))

        val sql = template.executedSql.single()
        assertTrue(sql.contains("FROM content_interaction"))
        assertTrue(sql.contains("WHERE id = ?"))
        assertEquals(listOf<Any?>("int_1"), template.queryArgs.single())
        assertEquals(PublicId("int_1"), found?.id)
        assertEquals(PublicId("usr_1"), found?.userId)
        assertEquals(PublicId("ct_1"), found?.contentId)
        assertEquals(InteractionType.LIKE, found?.type)
        assertEquals(1000L, found?.createdAt)
    }

    @Test
    fun findById_whenAbsent_returnsNull() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()
        val repo = repository(template)

        assertNull(repo.findById(PublicId("int_missing")))
    }

    // ------------------------------------------------------------------
    // findByUserContentType (natural key = the DB unique triple)
    // ------------------------------------------------------------------

    @Test
    fun findByUserContentType_matchesTheExactTripleAndMapsTheAggregate() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow("int_fav", "usr_1", "ct_1", "FAVORITE", 2000L)
        )
        val repo = repository(template)

        val found = repo.findByUserContentType(
            PublicId("usr_1"),
            PublicId("ct_1"),
            InteractionType.FAVORITE
        )

        val sql = template.executedSql.single()
        assertTrue(sql.contains("WHERE user_id = ? AND content_id = ? AND type = ?"),
            "the natural-key lookup must use the exact triple predicate")
        assertEquals(
            listOf<Any?>("usr_1", "ct_1", "FAVORITE"),
            template.queryArgs.single(),
            "binding order must be user_id, content_id, type"
        )
        assertEquals(InteractionType.FAVORITE, found?.type)
    }

    @Test
    fun findByUserContentType_whenAbsent_returnsNull() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()
        val repo = repository(template)

        assertNull(
            repo.findByUserContentType(PublicId("usr_1"), PublicId("ct_1"), InteractionType.LIKE)
        )
    }

    // ------------------------------------------------------------------
    // deleteByUserContentType: physical delete + boolean result
    // ------------------------------------------------------------------

    @Test
    fun deleteByUserContentType_issuesPhysicalDeleteAndReturnsTrueWhenRowRemoved() {
        val template = RecordingJdbcTemplate()
        template.updateResult = 1
        val repo = repository(template)

        val deleted = repo.deleteByUserContentType(
            PublicId("usr_1"),
            PublicId("ct_1"),
            InteractionType.REPOST
        )

        val sql = template.executedSql.single()
        assertTrue(sql.startsWith("DELETE FROM content_interaction"))
        assertTrue(sql.contains("WHERE user_id = ? AND content_id = ? AND type = ?"))
        assertFalse(sql.contains("UPDATE"), "no soft-delete flag update")
        assertEquals(
            listOf<Any?>("usr_1", "ct_1", "REPOST"),
            template.updateArgs.single()
        )
        assertTrue(deleted)
    }

    @Test
    fun deleteByUserContentType_returnsFalseWhenNoRowWasRemoved() {
        val template = RecordingJdbcTemplate()
        template.updateResult = 0
        val repo = repository(template)

        val deleted = repo.deleteByUserContentType(
            PublicId("usr_ghost"),
            PublicId("ct_ghost"),
            InteractionType.LIKE
        )

        assertFalse(deleted, "no row removed -> false (idempotent no-op result)")
    }

    @Test
    fun save_thenFindByUserContentType_roundTripsTheAggregate() {
        val template = RecordingJdbcTemplate()
        val repo = repository(template)
        val value = interaction(InteractionType.LIKE)

        repo.save(value)
        template.rows = listOf(
            interactionRow(value.id.value, value.userId.value, value.contentId.value, value.type.name, value.createdAt)
        )

        val reloaded = repo.findByUserContentType(value.userId, value.contentId, value.type)

        assertEquals(value, reloaded)
    }

    // ------------------------------------------------------------------
    // findByUserId: Stage 19 paginated query
    // ------------------------------------------------------------------

    @Test
    fun findByUserId_queriesAllInteractionsForUser() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 3000L),
            interactionRow("int_2", "usr_1", "ct_2", "FAVORITE", 2000L),
            interactionRow("int_3", "usr_1", "ct_3", "BOOKMARK", 1000L)
        )
        val repo = repository(template)

        val page = repo.findByUserId(PublicId("usr_1"), type = null, limit = 10, cursor = null)

        val sql = template.executedSql.single()
        assertTrue(sql.contains("WHERE user_id = ?"), "must filter by user_id")
        assertFalse(sql.contains("AND type = ?"), "no type predicate when type is null")
        assertTrue(sql.contains("ORDER BY created_at DESC, id DESC"), "must sort correctly")
        assertTrue(sql.contains("LIMIT ?"), "must use LIMIT")
        assertEquals(3, page.items.size)
        assertNull(page.nextCursor)
    }

    @Test
    fun findByUserId_typeFilter_addsTypePredicate() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 2000L)
        )
        val repo = repository(template)

        val page = repo.findByUserId(PublicId("usr_1"), type = InteractionType.LIKE, limit = 10, cursor = null)

        val sql = template.executedSql.single()
        assertTrue(sql.contains("AND type = ?"), "must include type predicate")
        val args = template.queryArgs.single()
        assertEquals("usr_1", args[0], "userId first")
        assertEquals("LIKE", args[1], "type second")
        assertEquals(11, args[2], "probeLimit = limit + 1 = 11")
        assertEquals(1, page.items.size)
        assertEquals(InteractionType.LIKE, page.items[0].type)
    }

    @Test
    fun findByUserId_noResults_returnsEmptyPage() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()
        val repo = repository(template)

        val page = repo.findByUserId(PublicId("usr_missing"), type = null, limit = 10, cursor = null)

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun findByUserId_limitRespected() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 3000L),
            interactionRow("int_2", "usr_1", "ct_2", "FAVORITE", 2000L)
        )
        val repo = repository(template)

        val page = repo.findByUserId(PublicId("usr_1"), type = null, limit = 2, cursor = null)

        val args = template.queryArgs.single()
        assertEquals(3, args.last(), "probeLimit = limit + 1")
        assertEquals(2, page.items.size)
    }

    @Test
    fun findByUserId_limitPlusOneProbe_detectsNextPage() {
        val template = RecordingJdbcTemplate()
        // Return 3 rows when limit=2, so probe detects a third page
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 3000L),
            interactionRow("int_2", "usr_1", "ct_2", "FAVORITE", 2000L),
            interactionRow("int_3", "usr_1", "ct_3", "BOOKMARK", 1000L)
        )
        val repo = repository(template)

        val page = repo.findByUserId(PublicId("usr_1"), type = null, limit = 2, cursor = null)

        assertEquals(2, page.items.size, "delivered = limit, probe excluded")
        assertNotNull(page.nextCursor, "probe row means nextCursor is non-null")
    }

    @Test
    fun findByUserId_nextCursorDerivedFromLastDeliveredRow() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 3000L),
            interactionRow("int_2", "usr_1", "ct_2", "FAVORITE", 2000L),
            interactionRow("int_3", "usr_1", "ct_3", "BOOKMARK", 1000L)
        )
        val repo = repository(template)

        val page = repo.findByUserId(PublicId("usr_1"), type = null, limit = 2, cursor = null)

        assertNotNull(page.nextCursor)
        assertEquals(2000L, page.nextCursor!!.createdAt, "from last delivered row (int_2), not the probe (int_3)")
        assertEquals(PublicId("int_2"), page.nextCursor!!.interactionId)
    }

    @Test
    fun findByUserId_lastPage_nextCursorIsNull() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 3000L),
            interactionRow("int_2", "usr_1", "ct_2", "FAVORITE", 2000L)
        )
        val repo = repository(template)

        val page = repo.findByUserId(PublicId("usr_1"), type = null, limit = 10, cursor = null)

        assertEquals(2, page.items.size)
        assertNull(page.nextCursor, "no probe row returned -> last page")
    }

    @Test
    fun findByUserId_cursorSecondPage_queryContinues() {
        val template = RecordingJdbcTemplate()
        // Simulate second page: cursor points to int_2, so query returns int_3
        template.rows = listOf(
            interactionRow("int_3", "usr_1", "ct_3", "BOOKMARK", 1000L)
        )
        val repo = repository(template)
        val cursor = InteractionCursor(createdAt = 2000L, interactionId = PublicId("int_2"))

        val page = repo.findByUserId(PublicId("usr_1"), type = null, limit = 2, cursor = cursor)

        val sql = template.executedSql.single()
        assertTrue(sql.contains("AND (created_at < ? OR (created_at = ? AND id < ?))"),
            "must include cursor predicate")
        val args = template.queryArgs.single()
        assertEquals("usr_1", args[0], "userId")
        assertEquals(2000L, args[1], "cursor.createdAt first")
        assertEquals(2000L, args[2], "cursor.createdAt second (duplicated)")
        assertEquals("int_2", args[3], "cursor.interactionId")
        assertEquals(3, args[4], "probeLimit last")
        assertEquals(1, page.items.size)
        assertEquals("int_3", page.items[0].id.value)
        assertNull(page.nextCursor, "only one row returned with limit=2, no probe -> last page")
    }

    @Test
    fun findByUserId_sameCreatedAt_usesIdDescTieBreaker() {
        val template = RecordingJdbcTemplate()
        // All rows share the same created_at, so ordering is by id DESC
        template.rows = listOf(
            interactionRow("int_3", "usr_1", "ct_3", "LIKE", 1000L),
            interactionRow("int_2", "usr_1", "ct_2", "FAVORITE", 1000L),
            interactionRow("int_1", "usr_1", "ct_1", "BOOKMARK", 1000L)
        )
        val repo = repository(template)

        val page = repo.findByUserId(PublicId("usr_1"), type = null, limit = 2, cursor = null)

        assertEquals(2, page.items.size)
        assertEquals("int_3", page.items[0].id.value, "first by id DESC")
        assertEquals("int_2", page.items[1].id.value, "second by id DESC")
        assertNotNull(page.nextCursor)
        assertEquals("int_2", page.nextCursor!!.interactionId.value, "cursor from last delivered")
        assertEquals(1000L, page.nextCursor!!.createdAt)
    }

    @Test
    fun findByUserId_cursorBoundary_noOverlapBetweenPages() {
        val template = RecordingJdbcTemplate()
        // First page returns int_3, int_2 (limit=2), with probe int_1
        template.rows = listOf(
            interactionRow("int_3", "usr_1", "ct_3", "LIKE", 3000L),
            interactionRow("int_2", "usr_1", "ct_2", "FAVORITE", 2000L),
            interactionRow("int_1", "usr_1", "ct_1", "BOOKMARK", 1000L)
        )
        val repo = repository(template)

        val page1 = repo.findByUserId(PublicId("usr_1"), type = null, limit = 2, cursor = null)
        assertEquals(2, page1.items.size)
        assertEquals("int_3", page1.items[0].id.value)
        assertEquals("int_2", page1.items[1].id.value)
        assertNotNull(page1.nextCursor)

        // Second page: cursor = int_2
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "BOOKMARK", 1000L)
        )
        val page2 = repo.findByUserId(PublicId("usr_1"), type = null, limit = 2, cursor = page1.nextCursor)
        assertEquals(1, page2.items.size)
        assertEquals("int_1", page2.items[0].id.value)
        assertNull(page2.nextCursor)
    }

    @Test
    fun findByUserId_differentUsers_cannotLeakData() {
        val template = RecordingJdbcTemplate()
        // Repo returns rows for usr_1, but the SQL must bind usr_2
        template.rows = listOf(
            interactionRow("int_99", "usr_2", "ct_99", "LIKE", 5000L)
        )
        val repo = repository(template)

        val page = repo.findByUserId(PublicId("usr_2"), type = null, limit = 10, cursor = null)

        val args = template.queryArgs.single()
        assertEquals("usr_2", args[0], "query must bind the requested userId, not a different user")
        assertEquals(1, page.items.size)
        assertEquals("usr_2", page.items[0].userId.value)
    }

    @Test
    fun findByUserId_contentStatusDoesNotAffectQuery() {
        val template = RecordingJdbcTemplate()
        // Interaction exists even though content might be deleted/inactive;
        // the query only reads content_interaction, no JOINs
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_deleted", "LIKE", 1000L)
        )
        val repo = repository(template)

        val page = repo.findByUserId(PublicId("usr_1"), type = null, limit = 10, cursor = null)

        val sql = template.executedSql.single()
        assertFalse(sql.contains("JOIN"), "must not JOIN content or any other table")
        assertFalse(sql.contains("content.status"), "must not filter by content status")
        assertEquals(1, page.items.size)
    }

    @Test
    fun findByUserId_noJoinNoHydration() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 1000L)
        )
        val repo = repository(template)

        repo.findByUserId(PublicId("usr_1"), type = null, limit = 10, cursor = null)

        val sql = template.executedSql.single()
        assertFalse(sql.contains("JOIN"), "no JOINs allowed")
        assertFalse(sql.contains("INNER JOIN"), "no INNER JOINs")
        assertFalse(sql.contains("LEFT JOIN"), "no LEFT JOINs")
        assertEquals(1, template.executedSql.size, "single query, no hydration queries")
    }

    @Test
    fun findByUserId_usesParameterBinding() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 1000L)
        )
        val repo = repository(template)

        repo.findByUserId(PublicId("usr_1"), type = InteractionType.LIKE, limit = 5, cursor = null)

        val sql = template.executedSql.single()
        val placeholderCount = sql.count { it == '?' }
        val args = template.queryArgs.single()
        assertEquals(placeholderCount, args.size, "each ? must have a corresponding bound argument")
        assertTrue(sql.contains("?"), "must use parameter placeholders, not string concatenation")
        assertEquals("usr_1", args[0])
        assertEquals("LIKE", args[1])
        assertEquals(6, args[2], "probeLimit = 5 + 1")
    }

    @Test
    fun findByUserId_limitZero_returnsEmptyWithoutQuery() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 1000L)
        )
        val repo = repository(template)

        val page = repo.findByUserId(PublicId("usr_1"), type = null, limit = 0, cursor = null)

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
        assertTrue(template.executedSql.isEmpty(), "limit=0 must not execute any query")
    }

    @Test
    fun findByUserId_sameCreatedAtIdDesc_cursorContinuesCorrectly() {
        val template = RecordingJdbcTemplate()
        // All interactions at timestamp 5000, different IDs
        // First page: int_5, int_4 (limit=2), probe int_3
        template.rows = listOf(
            interactionRow("int_5", "usr_1", "ct_5", "LIKE", 5000L),
            interactionRow("int_4", "usr_1", "ct_4", "FAVORITE", 5000L),
            interactionRow("int_3", "usr_1", "ct_3", "REPOST", 5000L)
        )
        val repo = repository(template)

        val page1 = repo.findByUserId(PublicId("usr_1"), type = null, limit = 2, cursor = null)
        assertEquals(2, page1.items.size)
        assertEquals("int_5", page1.items[0].id.value)
        assertEquals("int_4", page1.items[1].id.value)
        assertNotNull(page1.nextCursor)
        assertEquals(5000L, page1.nextCursor!!.createdAt)
        assertEquals("int_4", page1.nextCursor!!.interactionId.value)

        // Second page from cursor (5000, int_4): should get int_3, int_2, int_1
        template.rows = listOf(
            interactionRow("int_3", "usr_1", "ct_3", "REPOST", 5000L),
            interactionRow("int_2", "usr_1", "ct_2", "BOOKMARK", 5000L),
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 5000L)
        )
        val page2 = repo.findByUserId(PublicId("usr_1"), type = null, limit = 2, cursor = page1.nextCursor)
        assertEquals(2, page2.items.size)
        assertEquals("int_3", page2.items[0].id.value, "int_3 < int_4 at same timestamp")
        assertEquals("int_2", page2.items[1].id.value)
        assertNotNull(page2.nextCursor)
        assertEquals("int_2", page2.nextCursor!!.interactionId.value)

        // Third page from cursor (5000, int_2): should get int_1 only
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 5000L)
        )
        val page3 = repo.findByUserId(PublicId("usr_1"), type = null, limit = 2, cursor = page2.nextCursor)
        assertEquals(1, page3.items.size)
        assertEquals("int_1", page3.items[0].id.value)
        assertNull(page3.nextCursor, "last page")
    }

    @Test
    fun findByUserId_typeFilterWithCursor() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow("int_1", "usr_1", "ct_1", "LIKE", 1000L)
        )
        val repo = repository(template)
        val cursor = InteractionCursor(createdAt = 2000L, interactionId = PublicId("int_2"))

        val page = repo.findByUserId(PublicId("usr_1"), type = InteractionType.LIKE, limit = 5, cursor = cursor)

        val sql = template.executedSql.single()
        assertTrue(sql.contains("AND type = ?"), "type predicate present")
        assertTrue(sql.contains("AND (created_at < ? OR (created_at = ? AND id < ?))"), "cursor predicate present")
        val args = template.queryArgs.single()
        assertEquals("usr_1", args[0])
        assertEquals("LIKE", args[1])
        assertEquals(2000L, args[2])
        assertEquals(2000L, args[3])
        assertEquals("int_2", args[4])
        assertEquals(6, args[5], "probeLimit = 5 + 1")
    }

    // ------------------------------------------------------------------
    // countByContentId
    // ------------------------------------------------------------------

    @Test
    fun countByContentId_queriesGroupByTypeAndReturnsMap() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            mapOf("type" to "LIKE", "cnt" to 3L),
            mapOf("type" to "BOOKMARK", "cnt" to 1L)
        )
        val repo = repository(template)

        val counts = repo.countByContentId(PublicId("ct_1"))

        val sql = template.executedSql.single()
        assertTrue(sql.contains("SELECT type, COUNT(*) AS cnt"))
        assertTrue(sql.contains("FROM content_interaction"))
        assertTrue(sql.contains("WHERE content_id = ?"))
        assertTrue(sql.contains("GROUP BY type"))
        assertEquals(listOf<Any?>("ct_1"), template.queryArgs.single())
        assertEquals(2, counts.size)
        assertEquals(3L, counts[InteractionType.LIKE])
        assertEquals(1L, counts[InteractionType.BOOKMARK])
    }

    @Test
    fun countByContentId_whenNoRows_returnsEmptyMap() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()
        val repo = repository(template)

        val counts = repo.countByContentId(PublicId("ct_empty"))

        assertTrue(counts.isEmpty())
    }

    @Test
    fun countByContentId_singleType_returnsMapWithOneEntry() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            mapOf("type" to "FAVORITE", "cnt" to 7L)
        )
        val repo = repository(template)

        val counts = repo.countByContentId(PublicId("ct_1"))

        assertEquals(1, counts.size)
        assertEquals(7L, counts[InteractionType.FAVORITE])
    }

    // ------------------------------------------------------------------
    // findByUserAndContent
    // ------------------------------------------------------------------

    @Test
    fun findByUserAndContent_noRows_returnsEmptyList() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()
        val repo = repository(template)

        val results = repo.findByUserAndContent(PublicId("au_1"), PublicId("ct_1"))

        assertTrue(results.isEmpty())
        val sql = template.executedSql.first()
        assertTrue(sql.contains("WHERE user_id = ? AND content_id = ?"))
        val args = template.queryArgs.first()
        assertEquals("au_1", args[0])
        assertEquals("ct_1", args[1])
    }

    @Test
    fun findByUserAndContent_oneType_returnsSingleInteraction() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow(id = "int_1", userId = "au_1", contentId = "ct_1", type = "LIKE")
        )
        val repo = repository(template)

        val results = repo.findByUserAndContent(PublicId("au_1"), PublicId("ct_1"))

        assertEquals(1, results.size)
        assertEquals(InteractionType.LIKE, results[0].type)
    }

    @Test
    fun findByUserAndContent_multipleTypes_returnsAllInteractions() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow(id = "int_1", userId = "au_1", contentId = "ct_1", type = "LIKE"),
            interactionRow(id = "int_2", userId = "au_1", contentId = "ct_1", type = "BOOKMARK")
        )
        val repo = repository(template)

        val results = repo.findByUserAndContent(PublicId("au_1"), PublicId("ct_1"))

        assertEquals(2, results.size)
        assertEquals(InteractionType.LIKE, results[0].type)
        assertEquals(InteractionType.BOOKMARK, results[1].type)
    }

    @Test
    fun findByUserAndContent_allFourTypes_returnsFourInteractions() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow(id = "int_1", userId = "au_1", contentId = "ct_1", type = "LIKE"),
            interactionRow(id = "int_2", userId = "au_1", contentId = "ct_1", type = "FAVORITE"),
            interactionRow(id = "int_3", userId = "au_1", contentId = "ct_1", type = "REPOST"),
            interactionRow(id = "int_4", userId = "au_1", contentId = "ct_1", type = "BOOKMARK")
        )
        val repo = repository(template)

        val results = repo.findByUserAndContent(PublicId("au_1"), PublicId("ct_1"))

        assertEquals(4, results.size)
    }

    @Test
    fun findByUserAndContent_correctParameters() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()
        val repo = repository(template)

        repo.findByUserAndContent(PublicId("au_target"), PublicId("ct_target"))

        val args = template.queryArgs.first()
        assertEquals("au_target", args[0], "user_id must be first parameter")
        assertEquals("ct_target", args[1], "content_id must be second parameter")
    }

    @Test
    fun findByUserAndContent_usesSingleQuery() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            interactionRow(id = "int_1", userId = "au_1", contentId = "ct_1", type = "LIKE"),
            interactionRow(id = "int_2", userId = "au_1", contentId = "ct_1", type = "FAVORITE"),
            interactionRow(id = "int_3", userId = "au_1", contentId = "ct_1", type = "REPOST"),
            interactionRow(id = "int_4", userId = "au_1", contentId = "ct_1", type = "BOOKMARK")
        )
        val repo = repository(template)

        repo.findByUserAndContent(PublicId("au_1"), PublicId("ct_1"))

        assertEquals(1, template.executedSql.size, "must use exactly one SQL query")
    }
}
