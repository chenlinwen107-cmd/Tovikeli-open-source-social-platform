package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * Tag-scoped public-content page unit tests for
 * [ContentJdbcRepository.findPublicContentByTag] (Stage 15-C).
 *
 * No database is involved. [FakeJdbcTemplate] intercepts the content query and
 * emulates Postgres behaviour encoded in the SQL: content rows are treated as
 * already matched by the requested tag + ACTIVE/PUBLIC/allow_index, then the
 * strict keyset cursor predicate, ORDER BY created_at DESC / id DESC and
 * LIMIT <probe bound> are applied. Media rows are scripted.
 *
 * The repository itself decides delivery vs. probe, nextCursor derivation and
 * media batching, so those semantics are verified end-to-end through the
 * repository API.
 */
class ContentJdbcRepositoryByTagTest {

    private class FakeJdbcTemplate : JdbcTemplate() {

        val executedSql = mutableListOf<String>()
        val tagQueryArgs = mutableListOf<List<Any?>>()
        val mediaBatchArgs = mutableListOf<List<Any?>>()

        /** Candidate (already tag-matched + publicly indexable) content rows. */
        var rows: List<Map<String, Any?>> = emptyList()

        /** content_media rows in the DB order produced by the batch query. */
        var mediaRows: List<Map<String, Any?>> = emptyList()

        override fun <T> query(sql: String, rowMapper: RowMapper<T>, vararg args: Any?): List<T> {
            executedSql += sql
            val rowMaps = when {
                isTagContentSql(sql) -> {
                    tagQueryArgs += args.toList()
                    emulateOrderLimitCursor(rows, sql, args)
                }
                isMediaBatchSql(sql) -> {
                    mediaBatchArgs += args.toList()
                    mediaRows
                }
                else -> error("Unexpected query issued to FakeJdbcTemplate: $sql")
            }
            return mapRows(rowMaps, rowMapper)
        }

        private fun isTagContentSql(sql: String): Boolean =
            sql.contains("INNER JOIN content_tag ct") && sql.contains("ct.tag_id = ?")

        private fun isMediaBatchSql(sql: String): Boolean =
            sql.contains("FROM content_media") && sql.contains(" IN (")

        /**
         * Binding layout: [tagId, cursor.createdAt?, cursor.createdAt?,
         * cursor.id?, probeLimit]; the probe limit is always the last argument.
         */
        private fun emulateOrderLimitCursor(
            rows: List<Map<String, Any?>>,
            sql: String,
            args: Array<out Any?>
        ): List<Map<String, Any?>> {
            val ordered = rows.sortedWith(
                compareByDescending<Map<String, Any?>> { it["created_at"] as Long }
                    .thenByDescending { it["id"] as String }
            )
            val filtered = if (sql.contains("c.created_at < ?")) {
                val cursorCreatedAt = (args[args.size - 3] as Number).toLong()
                val cursorId = args[args.size - 2] as String
                ordered.filter { row ->
                    val createdAt = row["created_at"] as Long
                    val id = row["id"] as String
                    createdAt < cursorCreatedAt ||
                        (createdAt == cursorCreatedAt && id < cursorId)
                }
            } else {
                ordered
            }
            val limit = (args.last() as Number).toInt()
            return filtered.take(limit)
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

        private fun resultSetFor(rows: List<Map<String, Any?>>): ResultSet {
            val cursor = AtomicInteger(-1)
            var wasNull = false
            val loader = ContentJdbcRepository::class.java.classLoader
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
    // Row fixtures (content columns exactly as read by ContentJdbcRepository)
    // ------------------------------------------------------------------

    private fun contentRow(
        id: String,
        createdAt: Long,
        status: String = "ACTIVE",
        visibility: String = "PUBLIC"
    ): Map<String, Any?> = mapOf(
        "id" to id,
        "instance_id" to "inst_public",
        "source_content_id" to "sc-$id",
        "author_id" to "au_1",
        "source_id" to "src_1",
        "text" to "body of $id",
        "language" to "en",
        "visibility" to visibility,
        "status" to status,
        "parent_id" to null,
        "repost_of_id" to null,
        "quote_of_id" to null,
        "external_created_at" to null,
        "created_at" to createdAt,
        "updated_at" to createdAt,
        "metadata" to "{}"
    )

    private fun mediaRow(contentId: String, mediaId: String): Map<String, Any?> = mapOf(
        "content_id" to contentId,
        "media_id" to mediaId
    )

    private fun repository(fake: FakeJdbcTemplate): ContentJdbcRepository =
        ContentJdbcRepository(fake)

    private val tagId = PublicId("tg_anime")

    // ------------------------------------------------------------------
    // 1. SQL: content x instance x content_tag join, frozen public filters
    // ------------------------------------------------------------------

    @Test
    fun findPublicContentByTag_emitsTagJoinAndPublicFilters() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(contentRow("ct_1", createdAt = 1000L))
        val repo = repository(fake)

        val page = repo.findPublicContentByTag(tagId, cursor = null, limit = 10)

        assertEquals(1, page.contents.size)
        val sql = fake.executedSql.first()
        assertTrue(sql.contains("INNER JOIN instance i ON i.id = c.instance_id"), "must join instance")
        assertTrue(sql.contains("INNER JOIN content_tag ct ON ct.content_id = c.id"), "must join content_tag")
        assertTrue(sql.contains("AND ct.tag_id = ?"), "must filter by the requested tag id")
        assertTrue(sql.contains("c.status = 'ACTIVE'"), "must filter ACTIVE")
        assertTrue(sql.contains("c.visibility = 'PUBLIC'"), "must filter PUBLIC")
        assertTrue(sql.contains("i.allow_index = TRUE"), "must filter allow_index")
        assertTrue(sql.contains("ORDER BY c.created_at DESC, c.id DESC"), "ordering must be frozen")
        assertTrue(sql.contains("LIMIT ?"), "LIMIT must be bound")
        assertTrue(sql.contains("c.id DESC") , "id tie-breaker must be present")
        assertEquals(
            listOf<Any?>("tg_anime", 11),
            fake.tagQueryArgs.single(),
            "tag id then probe limit must bind in that order"
        )
        assertNull(page.nextCursor)
    }

    // ------------------------------------------------------------------
    // 2. Binding: tagId always first; cursor triple precedes probe limit
    // ------------------------------------------------------------------

    @Test
    fun findPublicContentByTag_withCursor_bindsTagThenCursorTripleThenProbeLimit() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(contentRow("ct_old", createdAt = 100L))
        val repo = repository(fake)
        val cursor = ContentCursor(createdAt = 5_000L, id = PublicId("ct_x"))

        repo.findPublicContentByTag(tagId, cursor = cursor, limit = 20)

        assertEquals(
            listOf<Any?>("tg_anime", 5_000L, 5_000L, "ct_x", 21),
            fake.tagQueryArgs.single(),
            "binding order must be tagId -> createdAt -> createdAt -> id -> probe limit"
        )
        val sql = fake.executedSql.first()
        assertTrue(
            sql.contains("AND (c.created_at < ? OR (c.created_at = ? AND c.id < ?))"),
            "the keyset cursor predicate must be appended after the tag filter"
        )
    }

    // ------------------------------------------------------------------
    // 3. Ordering / same-createdAt tie-breaker
    // ------------------------------------------------------------------

    @Test
    fun findPublicContentByTag_ordersByCreatedAtDescThenIdDesc() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_old", createdAt = 1_000L),
            contentRow("ct_mid", createdAt = 3_000L),
            contentRow("ct_new", createdAt = 3_000L)
        )
        val repo = repository(fake)

        val page = repo.findPublicContentByTag(tagId, cursor = null, limit = 10)

        assertEquals(
            listOf("ct_new", "ct_mid", "ct_old"),
            page.contents.map { it.id.value },
            "created_at DESC then id DESC must be the returned order"
        )
    }

    // ------------------------------------------------------------------
    // 4. LIMIT + 1 probe: probe not delivered, nextCursor from delivered row
    // ------------------------------------------------------------------

    @Test
    fun findPublicContentByTag_probeRowNeverDeliveredAndNotInMediaBatch() {
        val fake = FakeJdbcTemplate()
        // ct_21 (newest) ... ct_1 (oldest); requested limit 20 -> probe ct_1.
        fake.rows = (1..21).map { contentRow("ct_$it", createdAt = it * 1_000L) }
        fake.mediaRows = (1..21).map { mediaRow("ct_$it", "m_$it") }
        val repo = repository(fake)

        val page = repo.findPublicContentByTag(tagId, cursor = null, limit = 20)

        assertEquals(listOf<Any?>("tg_anime", 21), fake.tagQueryArgs.single(), "the DB bound limit must be requested+1")
        assertEquals(20, page.contents.size, "the probe row must never be delivered")
        assertTrue(page.contents.none { it.id.value == "ct_1" }, "probe row ct_1 must not be delivered")
        assertEquals(
            ContentCursor(createdAt = 2_000L, id = PublicId("ct_2")),
            page.nextCursor,
            "nextCursor must come from the last delivered row (ct_2), never the probe (ct_1)"
        )

        // Media batch covers exactly the delivered rows, never the probe.
        val mediaSqls = fake.executedSql.filter { it.contains("content_media") }
        assertEquals(1, mediaSqls.size, "exactly one batched media query")
        val deliveredIds = page.contents.map { it.id.value }
        assertEquals(deliveredIds, fake.mediaBatchArgs.single(), "media batch must cover only delivered rows")
        assertFalseContains(fake.mediaBatchArgs.single(), "ct_1")
        assertTrue(
            fake.executedSql.none { it.contains("WHERE content_id = ?") },
            "per-content media lookups are forbidden"
        )
    }

    private fun assertFalseContains(list: List<Any?>, forbidden: String) {
        assertTrue(list.none { it == forbidden }, "probe row must not reach the media batch")
    }

    // ------------------------------------------------------------------
    // 5. Keyset walking across pages: no duplicates, no misses
    // ------------------------------------------------------------------

    @Test
    fun findPublicContentByTag_cursorPagesWalkAllRowsWithoutDuplicates() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_z", createdAt = 3_000L),
            contentRow("ct_m", createdAt = 3_000L),
            contentRow("ct_a", createdAt = 3_000L),
            contentRow("ct_q", createdAt = 1_000L)
        )
        val repo = repository(fake)

        val p1 = repo.findPublicContentByTag(tagId, cursor = null, limit = 1)
        assertEquals(listOf("ct_z"), p1.contents.map { it.id.value })
        assertEquals(ContentCursor(createdAt = 3_000L, id = PublicId("ct_z")), p1.nextCursor)

        val p2 = repo.findPublicContentByTag(tagId, cursor = p1.nextCursor, limit = 1)
        assertEquals(listOf("ct_m"), p2.contents.map { it.id.value })

        val p3 = repo.findPublicContentByTag(tagId, cursor = p2.nextCursor, limit = 1)
        assertEquals(listOf("ct_a"), p3.contents.map { it.id.value })

        val p4 = repo.findPublicContentByTag(tagId, cursor = p3.nextCursor, limit = 1)
        assertEquals(listOf("ct_q"), p4.contents.map { it.id.value })
        assertNull(p4.nextCursor, "final page must not carry a next cursor")

        val walked = p1.contents + p2.contents + p3.contents + p4.contents
        assertEquals(
            listOf("ct_z", "ct_m", "ct_a", "ct_q"),
            walked.map { it.id.value },
            "same-createdAt rows must be walked exactly once via the id tie-breaker"
        )
    }

    // ------------------------------------------------------------------
    // 6. limit boundary behaviour
    // ------------------------------------------------------------------

    @Test
    fun findPublicContentByTag_whenExactlyOnePage_returnsNoNextCursor() {
        val fake = FakeJdbcTemplate()
        fake.rows = (1..20).map { contentRow("ct_$it", createdAt = it * 1_000L) }
        val repo = repository(fake)

        val page = repo.findPublicContentByTag(tagId, cursor = null, limit = 20)

        assertEquals(20, page.contents.size)
        assertNull(page.nextCursor)
    }

    @Test
    fun findPublicContentByTag_whenLimitZero_returnsEmptyWithoutQuerying() {
        val fake = FakeJdbcTemplate()
        val repo = repository(fake)

        val page = repo.findPublicContentByTag(tagId, cursor = null, limit = 0)

        assertTrue(page.contents.isEmpty())
        assertNull(page.nextCursor)
        assertTrue(fake.executedSql.isEmpty(), "limit 0 must short-circuit without any content query")
    }

    @Test
    fun findPublicContentByTag_rejectsInvalidLimits() {
        val fake = FakeJdbcTemplate()
        val repo = repository(fake)

        assertFailsWith<IllegalArgumentException> { repo.findPublicContentByTag(tagId, null, -1) }
        assertFailsWith<IllegalArgumentException> {
            repo.findPublicContentByTag(tagId, null, ContentRepository.MAX_PUBLIC_BROWSE_LIMIT + 1)
        }
    }

    // ------------------------------------------------------------------
    // 7. Empty result semantics
    // ------------------------------------------------------------------

    @Test
    fun findPublicContentByTag_whenNoMatchingContent_returnsEmptyPageAndNoMediaQuery() {
        val fake = FakeJdbcTemplate()
        fake.rows = emptyList()
        val repo = repository(fake)

        val page = repo.findPublicContentByTag(tagId, cursor = null, limit = 10)

        assertTrue(page.contents.isEmpty())
        assertNull(page.nextCursor)
        assertEquals(1, fake.executedSql.size, "only the tag content query runs")
        assertTrue(fake.executedSql.none { it.contains("content_media") }, "no media query on an empty page")
    }

    // ------------------------------------------------------------------
    // 8. Media: single batch, order preserved, full aggregates
    // ------------------------------------------------------------------

    @Test
    fun findPublicContentByTag_loadsMediaInOneBatchPreservingOrder() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_a", createdAt = 2_000L),
            contentRow("ct_b", createdAt = 1_000L)
        )
        fake.mediaRows = listOf(
            mediaRow("ct_a", "m_alpha"),
            mediaRow("ct_a", "m_beta"),
            mediaRow("ct_b", "m_gamma")
        )
        val repo = repository(fake)

        val page = repo.findPublicContentByTag(tagId, cursor = null, limit = 10)

        assertEquals(listOf("ct_a", "ct_b"), page.contents.map { it.id.value })
        val mediaSqls = fake.executedSql.filter { it.contains("content_media") }
        assertEquals(1, mediaSqls.size)
        assertTrue(mediaSqls.single().contains("WHERE content_id IN ("), "single IN batch required")
        assertEquals(listOf<Any?>("ct_a", "ct_b"), fake.mediaBatchArgs.single())
        assertEquals(listOf(PublicId("m_alpha"), PublicId("m_beta")), page.contents[0].mediaIds)
        assertEquals(listOf(PublicId("m_gamma")), page.contents[1].mediaIds)
        assertNotNull(page.contents[0].id)
    }
}
