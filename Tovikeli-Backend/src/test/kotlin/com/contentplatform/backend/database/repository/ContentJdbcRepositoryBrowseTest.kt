package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * Browse-focused unit tests for [ContentJdbcRepository.findPublicContent]
 * including Stage 14 keyset cursor pagination.
 *
 * No database is involved. [FakeJdbcTemplate] subclasses [JdbcTemplate] and
 * intercepts every query call so the repository code path (SQL construction,
 * LIMIT+1 probe binding, cursor predicate emulation, batched content_media
 * loading, aggregate reconstruction) runs against scripted result rows.
 *
 * The fake emulates the documented Postgres behaviour encoded in the SQL:
 * ORDER BY created_at DESC, id DESC, the strict keyset predicate
 * (created_at < ? OR (created_at = ? AND id < ?)) when a cursor is bound, and
 * LIMIT <probeLimit>. The repository itself decides what is delivered and
 * what is a probe row, so delivery/nextCursor semantics are verified
 * end-to-end through the repository API.
 */
class ContentJdbcRepositoryBrowseTest {

    // ------------------------------------------------------------------
    // Fake JdbcTemplate
    // ------------------------------------------------------------------

    private class FakeJdbcTemplate : JdbcTemplate() {

        val executedSql = mutableListOf<String>()
        val browseQueryArgs = mutableListOf<List<Any?>>()
        val mediaBatchArgs = mutableListOf<List<Any?>>()

        /** Candidate content rows (as if all other browse filters matched). */
        var contentRows: List<Map<String, Any?>> = emptyList()

        /** content_media rows in the DB order produced by the batch query. */
        var mediaRows: List<Map<String, Any?>> = emptyList()

        override fun <T> query(sql: String, rowMapper: RowMapper<T>, vararg args: Any?): List<T> {
            executedSql += sql
            val rowMaps = when {
                isContentBrowseSql(sql) -> {
                    browseQueryArgs += args.toList()
                    emulateBrowseOrderLimitAndCursor(contentRows, sql, args)
                }
                isMediaBatchSql(sql) -> {
                    mediaBatchArgs += args.toList()
                    mediaRows
                }
                else -> error("Unexpected query issued to FakeJdbcTemplate: $sql")
            }
            return mapRows(rowMaps, rowMapper)
        }

        private fun isContentBrowseSql(sql: String): Boolean =
            sql.contains("FROM content c") && sql.contains("allow_index")

        private fun isMediaBatchSql(sql: String): Boolean =
            sql.contains("FROM content_media") && sql.contains(" IN (")

        /**
         * Emulates Postgres: optional strict keyset cursor filter, then
         * ORDER BY created_at DESC, id DESC, then LIMIT <bound>. The bound is
         * always the last argument (the repository binds the probe limit).
         */
        private fun emulateBrowseOrderLimitAndCursor(
            rows: List<Map<String, Any?>>,
            sql: String,
            args: Array<out Any?>
        ): List<Map<String, Any?>> {
            val ordered = rows.sortedWith(
                compareByDescending<Map<String, Any?>> { it["created_at"] as Long }
                    .thenByDescending { it["id"] as String }
            )
            val filtered = if (sql.contains("c.created_at < ?")) {
                // Binding layout when a cursor is present:
                // [instanceId?, authorId?, cursor.createdAt, cursor.createdAt,
                //  cursor.id, limit]
                val cursorCreatedAt = (args[args.size - 4] as Number).toLong()
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

        /** Minimal dynamic ResultSet over a list of column-label -> value maps. */
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
    // Row fixtures (column labels exactly as read by ContentJdbcRepository)
    // ------------------------------------------------------------------

    private fun contentRow(
        id: String,
        createdAt: Long,
        instanceId: String = "inst_public",
        authorId: String = "au_1",
        status: String = "ACTIVE",
        visibility: String = "PUBLIC"
    ): Map<String, Any?> = mapOf(
        "id" to id,
        "instance_id" to instanceId,
        "source_content_id" to "sc-$id",
        "author_id" to authorId,
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

    // ------------------------------------------------------------------
    // 1. Base public-browse SQL preserved on a first page (no cursor)
    // ------------------------------------------------------------------

    @Test
    fun findPublicContent_firstPage_emitsPublicFiltersJoinAndNoCursorPredicate() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = listOf(contentRow("ct_1", createdAt = 1000L))
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, null, null, 10)

        val contentSql = fake.executedSql.first()
        assertTrue(contentSql.contains("INNER JOIN instance i ON i.id = c.instance_id"), "browse must join instance")
        assertTrue(contentSql.contains("c.status = 'ACTIVE'"), "browse must filter status = ACTIVE")
        assertTrue(contentSql.contains("c.visibility = 'PUBLIC'"), "browse must filter visibility = PUBLIC")
        assertTrue(contentSql.contains("i.allow_index = TRUE"), "browse must filter instance.allow_index = TRUE")
        assertFalse(
            contentSql.contains("c.created_at < ?"),
            "a first page without a cursor must NOT add the keyset cursor predicate"
        )
        assertEquals(1, page.contents.size)
        assertNull(page.nextCursor, "no cursor and rows <= limit must not produce a next cursor")
    }

    @Test
    fun findPublicContent_firstPage_stillBindsProbeLimit() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = (1..5).map { contentRow("ct_$it", createdAt = it * 1_000L) }
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, null, null, 3)

        assertEquals(listOf<Any?>(4), fake.browseQueryArgs.single(), "the DB bound limit must be requested+1")
        assertEquals(3, page.contents.size, "only the requested rows may be delivered")
        assertNotNull(page.nextCursor, "rows beyond the requested limit imply a next page")
    }

    // ------------------------------------------------------------------
    // 2. Instance / author / hasMedia predicates preserved (with probe)
    // ------------------------------------------------------------------

    @Test
    fun findPublicContent_withInstanceFilter_emitsInstancePredicateAndBindsInOrder() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = listOf(contentRow("ct_1", createdAt = 1000L, instanceId = "inst_target"))
        val repo = repository(fake)

        val page = repo.findPublicContent(PublicId("inst_target"), null, null, null, 10)

        val contentSql = fake.executedSql.first()
        assertTrue(contentSql.contains("AND c.instance_id = ?"), "the instance predicate must be present")
        assertTrue(contentSql.contains("ORDER BY c.created_at DESC, c.id DESC"), "the locked ordering must be preserved")
        assertEquals(
            listOf<Any?>("inst_target", 11),
            fake.browseQueryArgs.single(),
            "binding order must be instanceId.value -> probe limit"
        )
        assertEquals("inst_target", page.contents.single().instanceId.value)
        assertNull(page.nextCursor)
    }

    @Test
    fun findPublicContent_withAuthorFilter_emitsAuthorPredicateAndBindsInOrder() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = listOf(contentRow("ct_1", createdAt = 1000L, authorId = "au_target"))
        val repo = repository(fake)

        val page = repo.findPublicContent(null, PublicId("au_target"), null, null, 10)

        val contentSql = fake.executedSql.first()
        assertTrue(contentSql.contains("AND c.author_id = ?"), "the author predicate must be present")
        assertEquals(
            listOf<Any?>("au_target", 11),
            fake.browseQueryArgs.single(),
            "binding order must be authorId.value -> probe limit"
        )
        assertEquals("au_target", page.contents.single().authorId.value)
        assertNull(page.nextCursor)
    }

    @Test
    fun findPublicContent_withHasMediaTrue_emitsExistsWithoutBind() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = listOf(contentRow("ct_1", createdAt = 1000L))
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, true, null, 10)

        val contentSql = fake.executedSql.first()
        assertTrue(
            contentSql.contains("AND EXISTS (SELECT 1 FROM content_media cm WHERE cm.content_id = c.id)"),
            "hasMedia=true must emit an EXISTS media-presence predicate"
        )
        assertEquals(
            listOf<Any?>(11),
            fake.browseQueryArgs.single(),
            "hasMedia must not add a bound argument"
        )
        assertEquals(1, page.contents.size)
    }

    @Test
    fun findPublicContent_withHasMediaFalse_emitsNotExistsWithoutBind() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = listOf(contentRow("ct_1", createdAt = 1000L))
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, false, null, 10)

        val contentSql = fake.executedSql.first()
        assertTrue(
            contentSql.contains("AND NOT EXISTS (SELECT 1 FROM content_media cm WHERE cm.content_id = c.id)"),
            "hasMedia=false must emit a NOT EXISTS media-presence predicate"
        )
        assertEquals(
            listOf<Any?>(11),
            fake.browseQueryArgs.single(),
            "hasMedia must not add a bound argument"
        )
        assertEquals(1, page.contents.size)
    }

    // ------------------------------------------------------------------
    // 3. Ordering, same-createdAt tie-breaker and page boundary rows
    // ------------------------------------------------------------------

    @Test
    fun findPublicContent_ordersByCreatedAtDescThenIdDesc() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = listOf(
            contentRow("ct_old", createdAt = 1_000L),
            contentRow("ct_mid", createdAt = 3_000L),
            contentRow("ct_new", createdAt = 3_000L)
        )
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, null, null, 10)

        val contentSql = fake.executedSql.first()
        assertTrue(contentSql.contains("ORDER BY c.created_at DESC, c.id DESC"), "SQL must carry the locked ordering")
        assertEquals(
            listOf("ct_new", "ct_mid", "ct_old"),
            page.contents.map { it.id.value },
            "created_at DESC then id DESC must be the returned aggregate order"
        )
    }

    // ------------------------------------------------------------------
    // 4. limit boundary behaviour
    // ------------------------------------------------------------------

    @Test
    fun findPublicContent_whenExactlyOnePage_returnsNoNextCursor() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = (1..20).map { contentRow("ct_$it", createdAt = it * 1_000L) }
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, null, null, 20)

        assertEquals(20, page.contents.size)
        assertNull(page.nextCursor, "rows <= requested limit must not produce a next cursor")
    }

    @Test
    fun findPublicContent_whenLimitOne_returnsOneRowPerPage() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = (1..3).map { contentRow("ct_$it", createdAt = it * 1_000L) }
        val repo = repository(fake)

        val first = repo.findPublicContent(null, null, null, null, 1)
        val second = repo.findPublicContent(
            null, null, null,
            first.nextCursor, 1
        )

        assertEquals(1, first.contents.size)
        assertEquals("ct_3", first.contents.single().id.value)
        assertNotNull(first.nextCursor)
        assertEquals(1, second.contents.size)
        assertEquals("ct_2", second.contents.single().id.value)
    }

    @Test
    fun findPublicContent_whenLimitMax_returnsMaxLegalPage() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = (1..101).map { contentRow("ct_$it", createdAt = it * 1_000L) }
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, null, null, ContentRepository.MAX_PUBLIC_BROWSE_LIMIT)

        assertEquals(listOf<Any?>(101), fake.browseQueryArgs.single(), "the probe bound must be limit+1 = 101")
        assertEquals(100, page.contents.size, "the delivered page must cap at the requested limit")
        assertNotNull(page.nextCursor)
    }

    @Test
    fun findPublicContent_whenLimitZero_returnsEmptyPageWithoutQuerying() {
        val fake = FakeJdbcTemplate()
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, null, null, 0)

        assertTrue(page.contents.isEmpty())
        assertNull(page.nextCursor)
        assertTrue(fake.executedSql.isEmpty(), "limit 0 must short-circuit without executing any query")
    }

    @Test
    fun findPublicContent_rejectsInvalidLimits() {
        val fake = FakeJdbcTemplate()
        val repo = repository(fake)

        assertFailsWith<IllegalArgumentException> { repo.findPublicContent(null, null, null, null, -1) }
        assertFailsWith<IllegalArgumentException> {
            repo.findPublicContent(null, null, null, null, ContentRepository.MAX_PUBLIC_BROWSE_LIMIT + 1)
        }
    }

    // ------------------------------------------------------------------
    // 5. LIMIT+1 probe: probe row never delivered, nextCursor from last
    //    delivered row (never from the probe row)
    // ------------------------------------------------------------------

    @Test
    fun findPublicContent_whenProbeDetectsNextPage_derivesCursorFromLastDeliveredRow() {
        val fake = FakeJdbcTemplate()
        // ct_21 (newest, createdAt 21000) ... ct_1 (oldest, createdAt 1000).
        fake.contentRows = (1..21).map { contentRow("ct_$it", createdAt = it * 1_000L) }
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, null, null, 20)

        assertEquals(listOf<Any?>(21), fake.browseQueryArgs.single(), "the DB must be queried with LIMIT 21")
        assertEquals(20, page.contents.size, "the probe row must never be delivered")
        assertEquals("ct_21", page.contents.first().id.value)
        assertEquals("ct_2", page.contents.last().id.value)
        assertTrue(page.contents.none { it.id.value == "ct_1" }, "the probe row ct_1 must not be delivered")
        assertEquals(
            ContentCursor(createdAt = 2_000L, id = PublicId("ct_2")),
            page.nextCursor,
            "nextCursor must be derived from the last delivered row (ct_2), never from the probe row ct_1"
        )
    }

    // ------------------------------------------------------------------
    // 6. Media batch: exactly the delivered rows, never the probe row, no N+1
    // ------------------------------------------------------------------

    @Test
    fun findPublicContent_mediaBatchExcludesProbeRowAndStaysSingleQuery() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = (1..21).map { contentRow("ct_$it", createdAt = it * 1_000L) }
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, null, null, 20)

        assertEquals(20, page.contents.size)
        val mediaSqls = fake.executedSql.filter { it.contains("content_media") }
        assertEquals(1, mediaSqls.size, "media ids must be loaded by exactly one batched query")
        assertTrue(mediaSqls.single().contains("WHERE content_id IN ("), "the media load must be a single IN batch")
        val deliveredIds = page.contents.map { it.id.value }
        assertEquals(deliveredIds, fake.mediaBatchArgs.single(), "media batch must cover exactly the delivered rows")
        assertEquals(20, fake.mediaBatchArgs.single().size)
        assertFalse(fake.mediaBatchArgs.single().contains("ct_1"), "the probe row must not reach the media batch")
        assertTrue(
            fake.executedSql.none { it.contains("WHERE content_id = ?") },
            "per-content media lookups are forbidden"
        )
    }

    @Test
    fun findPublicContent_loadsMediaInOneBatchPreservingPositionOrder() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = listOf(
            contentRow("ct_b", createdAt = 1_000L),
            contentRow("ct_a", createdAt = 2_000L)
        )
        fake.mediaRows = listOf(
            mediaRow("ct_a", "m_alpha"),
            mediaRow("ct_a", "m_beta"),
            mediaRow("ct_b", "m_gamma")
        )
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, null, null, 10)

        assertEquals(listOf("ct_a", "ct_b"), page.contents.map { it.id.value })

        val mediaSqls = fake.executedSql.filter { it.contains("content_media") }
        assertEquals(1, mediaSqls.size, "media ids must be loaded by exactly one batched query")
        assertTrue(mediaSqls.single().contains("ORDER BY content_id, \"position\", media_id"), "batch must preserve position order")
        assertEquals(listOf<Any?>("ct_a", "ct_b"), fake.mediaBatchArgs.single())

        assertEquals(
            listOf(PublicId("m_alpha"), PublicId("m_beta")),
            page.contents[0].mediaIds,
            "media ids must be reconstructed in position order"
        )
        assertEquals(listOf(PublicId("m_gamma")), page.contents[1].mediaIds)
    }

    // ------------------------------------------------------------------
    // 7. Empty result semantics
    // ------------------------------------------------------------------

    @Test
    fun findPublicContent_whenNothingMatches_returnsEmptyPageAndNoMediaQuery() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = emptyList()
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, null, null, 10)

        assertTrue(page.contents.isEmpty(), "no matches must yield an empty page")
        assertNull(page.nextCursor)
        assertEquals(1, fake.executedSql.size, "only the content query runs; no media query may follow an empty page")
        assertTrue(fake.executedSql.none { it.contains("content_media") })
    }

    // ------------------------------------------------------------------
    // 8. Cursor keyset pagination across pages: no duplicates, no misses
    // ------------------------------------------------------------------

    @Test
    fun findPublicContent_cursorPagesWalkAllRowsWithoutDuplicatesOrMisses() {
        val fake = FakeJdbcTemplate()
        // Same-createdAt group (3000) with ids descending ct_z > ct_m > ct_a,
        // then an older row ct_q at 1000.
        fake.contentRows = listOf(
            contentRow("ct_z", createdAt = 3_000L),
            contentRow("ct_m", createdAt = 3_000L),
            contentRow("ct_a", createdAt = 3_000L),
            contentRow("ct_q", createdAt = 1_000L)
        )
        val repo = repository(fake)

        val page1 = repo.findPublicContent(null, null, null, null, 1)
        assertEquals(listOf("ct_z"), page1.contents.map { it.id.value })
        assertEquals(ContentCursor(createdAt = 3_000L, id = PublicId("ct_z")), page1.nextCursor)
        assertTrue(fake.executedSql.first().contains("ORDER BY c.created_at DESC, c.id DESC"))

        val page2 = repo.findPublicContent(null, null, null, page1.nextCursor, 1)
        assertEquals(listOf("ct_m"), page2.contents.map { it.id.value })
        assertEquals(ContentCursor(createdAt = 3_000L, id = PublicId("ct_m")), page2.nextCursor)
        val page2Sql = fake.executedSql.last { it.contains("FROM content c") && it.contains("allow_index") }
        assertTrue(page2Sql.contains("c.created_at < ?"), "a cursor page must add the keyset predicate")
        assertTrue(
            page2Sql.contains("(c.created_at = ? AND c.id < ?)"),
            "the keyset predicate must keep the equal-createdAt id tie-breaker"
        )

        val page3 = repo.findPublicContent(null, null, null, page2.nextCursor, 1)
        assertEquals(listOf("ct_a"), page3.contents.map { it.id.value })
        assertEquals(ContentCursor(createdAt = 3_000L, id = PublicId("ct_a")), page3.nextCursor)

        val page4 = repo.findPublicContent(null, null, null, page3.nextCursor, 1)
        assertEquals(listOf("ct_q"), page4.contents.map { it.id.value })
        assertNull(page4.nextCursor, "the final page must not carry a next cursor")

        val walked = page1.contents + page2.contents + page3.contents + page4.contents
        assertEquals(
            listOf("ct_z", "ct_m", "ct_a", "ct_q"),
            walked.map { it.id.value },
            "keyset pages must not duplicate or miss rows across the same-createdAt boundary"
        )
    }

    // ------------------------------------------------------------------
    // 9. Same created_at: the (created_at, id) pair drives pagination
    // ------------------------------------------------------------------

    @Test
    fun findPublicContent_cursorUsesCreatedAtAndIdTieBreaker() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = listOf(
            contentRow("ct_a", createdAt = 5_000L),
            contentRow("ct_b", createdAt = 5_000L),
            contentRow("ct_c", createdAt = 5_000L)
        )
        val repo = repository(fake)

        // Cursor at (5000, ct_b) - the only remaining newer/equal-ordered row
        // older than it within the tie group is ct_a.
        val page = repo.findPublicContent(
            null, null, null,
            ContentCursor(createdAt = 5_000L, id = PublicId("ct_b")),
            10
        )

        assertEquals(listOf("ct_a"), page.contents.map { it.id.value }, "id DESC tie-breaker must position ct_a after ct_b")
        assertNull(page.nextCursor, "no more rows remain after ct_a")
    }

    // ------------------------------------------------------------------
    // 10. Cursor + filters combined, frozen binding order
    //     instanceId -> authorId -> cursor.createdAt -> cursor.createdAt
    //     -> cursor.id -> probe limit
    // ------------------------------------------------------------------

    @Test
    fun findPublicContent_withCursorOnly_bindsCursorTripleThenProbeLimit() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = listOf(contentRow("ct_old", createdAt = 100L))
        val repo = repository(fake)
        val cursor = ContentCursor(createdAt = 5_000L, id = PublicId("ct_x"))

        repo.findPublicContent(null, null, null, cursor, 20)

        assertEquals(
            listOf<Any?>(5_000L, 5_000L, "ct_x", 21),
            fake.browseQueryArgs.single(),
            "cursor binding must be createdAt, createdAt, id, probe limit"
        )
    }

    @Test
    fun findPublicContent_withInstanceAuthorHasMediaAndCursor_bindsInFrozenOrder() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = listOf(
            contentRow("ct_1", createdAt = 100L, instanceId = "inst_target", authorId = "au_target")
        )
        val repo = repository(fake)
        val cursor = ContentCursor(createdAt = 5_000L, id = PublicId("ct_x"))

        val page = repo.findPublicContent(
            PublicId("inst_target"),
            PublicId("au_target"),
            true,
            cursor,
            20
        )

        val contentSql = fake.executedSql.first()
        assertTrue(contentSql.contains("AND c.instance_id = ?"), "the instance predicate must be present")
        assertTrue(contentSql.contains("AND c.author_id = ?"), "the author predicate must be present")
        assertTrue(
            contentSql.contains("AND EXISTS (SELECT 1 FROM content_media cm WHERE cm.content_id = c.id)"),
            "the media-presence predicate must be present"
        )
        assertTrue(
            contentSql.contains("AND (c.created_at < ? OR (c.created_at = ? AND c.id < ?))"),
            "the keyset cursor predicate must be present"
        )
        assertTrue(contentSql.contains("ORDER BY c.created_at DESC, c.id DESC"), "the locked ordering must be preserved")
        assertEquals(
            listOf<Any?>("inst_target", "au_target", 5_000L, 5_000L, "ct_x", 21),
            fake.browseQueryArgs.single(),
            "binding order must be instance -> author -> createdAt -> createdAt -> id -> probe limit"
        )
        assertEquals(1, page.contents.size)
    }

    // ------------------------------------------------------------------
    // 11. Aggregate reconstruction stays complete
    // ------------------------------------------------------------------

    @Test
    fun findPublicContent_reconstructsCompleteDomainAggregates() {
        val fake = FakeJdbcTemplate()
        fake.contentRows = listOf(
            contentRow("ct_1", createdAt = 1_000L),
            contentRow("ct_2", createdAt = 500L)
        )
        fake.mediaRows = listOf(mediaRow("ct_2", "m_only"))
        val repo = repository(fake)

        val page = repo.findPublicContent(null, null, null, null, 10)

        assertEquals(2, page.contents.size)
        val first = page.contents[0]
        assertEquals("ct_1", first.id.value)
        assertEquals("inst_public", first.instanceId.value)
        assertEquals("sc-ct_1", first.sourceContentId)
        assertEquals("body of ct_1", first.text)
        assertEquals("PUBLIC", first.visibility.name)
        assertEquals("ACTIVE", first.status.name)
        assertTrue(first.mediaIds.isEmpty())
        assertEquals(1_000L, first.createdAt)

        val second = page.contents[1]
        assertEquals(listOf(PublicId("m_only")), second.mediaIds)
        assertEquals(500L, second.createdAt)

        assertNull(page.nextCursor)
    }
}
