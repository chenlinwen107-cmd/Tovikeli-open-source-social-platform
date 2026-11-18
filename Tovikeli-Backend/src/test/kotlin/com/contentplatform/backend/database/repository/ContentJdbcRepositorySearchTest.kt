package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.ContentRepository
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * Search-focused unit tests for [ContentJdbcRepository.searchPublicContent]
 * (Stage 15-A Search Foundation).
 *
 * No database is involved. [FakeJdbcTemplate] subclasses [JdbcTemplate] and
 * intercepts every query call so the repository code path (SQL construction,
 * parameter binding, full-text matching/ordering emulation, batched
 * content_media loading, aggregate reconstruction) runs against scripted
 * result rows.
 *
 * The fake emulates the documented Postgres behaviour encoded in the SQL:
 *  - public indexability: status = ACTIVE, visibility = PUBLIC and
 *    instance.allow_index = TRUE (rows carry both content columns and the
 *    joined allow_index flag);
 *  - optional instance / author / source equality filters;
 *  - full-text semantics over content.text: all query tokens must match
 *    (websearch_to_tsquery AND behaviour); relevance = number of matched
 *    tokens (a deterministic stand-in for ts_rank monotonicity);
 *  - ordering relevance DESC, created_at DESC, id DESC; LIMIT <bound>.
 * This is an approximation of real FTS only where it cannot run (stemming and
 * ts_rank weights); the ordering and match semantics used by the assertions
 * are the ones frozen by the SQL.
 */
class ContentJdbcRepositorySearchTest {

    private class FakeJdbcTemplate : JdbcTemplate() {

        val executedSql = mutableListOf<String>()
        val searchQueryArgs = mutableListOf<List<Any?>>()
        val mediaBatchArgs = mutableListOf<List<Any?>>()

        /** Candidate joined (content x instance) rows. */
        var rows: List<Map<String, Any?>> = emptyList()

        /** content_media rows in the DB order produced by the batch query. */
        var mediaRows: List<Map<String, Any?>> = emptyList()

        override fun <T> query(sql: String, rowMapper: RowMapper<T>, vararg args: Any?): List<T> {
            executedSql += sql
            val rowMaps = when {
                isSearchSql(sql) -> {
                    searchQueryArgs += args.toList()
                    emulateSearch(sql, rows, args)
                }
                isMediaBatchSql(sql) -> {
                    mediaBatchArgs += args.toList()
                    mediaRows
                }
                else -> error("Unexpected query issued to FakeJdbcTemplate: $sql")
            }
            return mapRows(rowMaps, rowMapper)
        }

        private fun isSearchSql(sql: String): Boolean =
            sql.contains("websearch_to_tsquery")

        private fun isMediaBatchSql(sql: String): Boolean =
            sql.contains("FROM content_media") && sql.contains(" IN (")

        /**
         * Emulates the frozen Postgres semantics. Binding layout:
         * [instance?, author?, source?, query, query, limit]; the filters that
         * are actually present are derived from the SQL so the optional args
         * can be consumed in the fixed instance -> author -> source order.
         */
        private fun emulateSearch(
            sql: String,
            rows: List<Map<String, Any?>>,
            args: Array<out Any?>
        ): List<Map<String, Any?>> {
            var idx = 0
            fun nextFilter(): String? = args.getOrNull(idx++) as? String

            val hasInstance = sql.contains("AND c.instance_id = ?")
            val hasAuthor = sql.contains("AND c.author_id = ?")
            val hasSource = sql.contains("AND c.source_id = ?")
            val instance = if (hasInstance) nextFilter() else null
            val author = if (hasAuthor) nextFilter() else null
            val source = if (hasSource) nextFilter() else null

            // Remaining fixed tail: query, query, limit.
            val query = args[idx] as String
            val limit = (args[idx + 2] as Number).toInt()

            val tokens = query.lowercase().split(" ").filter { it.isNotBlank() }
            val publiclyIndexable = rows.filter { row ->
                row["status"] == "ACTIVE" &&
                    row["visibility"] == "PUBLIC" &&
                    row["allow_index"] == true
            }
            val filtered = publiclyIndexable.filter { row ->
                (instance == null || row["instance_id"] == instance) &&
                    (author == null || row["author_id"] == author) &&
                    (source == null || row["source_id"] == source)
            }
            val matched = filtered.mapNotNull { row ->
                val text = (row["text"] as String).lowercase()
                // websearch_to_tsquery defaults to AND semantics: every query
                // token must be present for the row to match at all. The rank
                // stand-in is the total number of token occurrences in the
                // text, which is monotonic with ts_rank coverage.
                val matchedTokens = tokens.count { text.contains(it) }
                if (tokens.isNotEmpty() && matchedTokens == tokens.size) {
                    val rank = tokens.sumOf { token -> text.split(token).size - 1 }
                    row to rank
                } else {
                    null
                }
            }
            val ordered = matched.sortedWith(
                compareByDescending<Pair<Map<String, Any?>, Int>> { it.second }
                    .thenByDescending { it.first["created_at"] as Long }
                    .thenByDescending { it.first["id"] as String }
            )
            return ordered.map { it.first }.take(limit)
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
    // Row fixtures (content columns + joined allow_index flag)
    // ------------------------------------------------------------------

    private fun contentRow(
        id: String,
        createdAt: Long,
        text: String = "body of $id",
        instanceId: String = "inst_public",
        authorId: String = "au_1",
        sourceId: String = "src_1",
        allowIndex: Boolean = true,
        status: String = "ACTIVE",
        visibility: String = "PUBLIC"
    ): Map<String, Any?> = mapOf(
        "id" to id,
        "instance_id" to instanceId,
        "source_content_id" to "sc-$id",
        "author_id" to authorId,
        "source_id" to sourceId,
        "text" to text,
        "language" to "en",
        "visibility" to visibility,
        "status" to status,
        "parent_id" to null,
        "repost_of_id" to null,
        "quote_of_id" to null,
        "external_created_at" to null,
        "created_at" to createdAt,
        "updated_at" to createdAt,
        "metadata" to "{}",
        "allow_index" to allowIndex
    )

    private fun mediaRow(contentId: String, mediaId: String): Map<String, Any?> = mapOf(
        "content_id" to contentId,
        "media_id" to mediaId
    )

    private fun repository(fake: FakeJdbcTemplate): ContentJdbcRepository =
        ContentJdbcRepository(fake)

    private fun searchSql(fake: FakeJdbcTemplate): String =
        fake.executedSql.first { it.contains("websearch_to_tsquery") }

    // ------------------------------------------------------------------
    // 1. FTS SQL shape: public filters, join, tsquery, ts_rank ordering
    // ------------------------------------------------------------------

    @Test
    fun searchPublicContent_emitsFtsSqlWithPublicFiltersAndTsRankOrdering() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(contentRow("ct_1", createdAt = 1000L, text = "a needle in a haystack"))
        val repo = repository(fake)

        val result = repo.searchPublicContent("needle", null, null, null, 20)

        assertEquals(1, result.size)
        val sql = searchSql(fake)
        assertTrue(sql.contains("INNER JOIN instance i ON i.id = c.instance_id"), "search must join instance")
        assertTrue(sql.contains("c.status = 'ACTIVE'"), "search must filter status = ACTIVE")
        assertTrue(sql.contains("c.visibility = 'PUBLIC'"), "search must filter visibility = PUBLIC")
        assertTrue(sql.contains("i.allow_index = TRUE"), "search must filter instance.allow_index = TRUE")
        assertTrue(
            sql.contains("to_tsvector('english', COALESCE(c.text, '')) @@ websearch_to_tsquery('english', ?)"),
            "search must match content.text through to_tsvector @@ websearch_to_tsquery"
        )
        assertTrue(
            sql.contains(
                "ORDER BY ts_rank(to_tsvector('english', COALESCE(c.text, '')), websearch_to_tsquery('english', ?)) DESC"
            ),
            "search must order by native ts_rank DESC first"
        )
        assertTrue(sql.contains("c.created_at DESC, c.id DESC"), "search must keep the frozen stable tie-breakers")
        assertTrue(sql.contains("LIMIT ?"), "search must bind LIMIT as a parameter")
    }

    // ------------------------------------------------------------------
    // 2. Binding: filters (when present) -> query -> query -> limit
    // ------------------------------------------------------------------

    @Test
    fun searchPublicContent_withoutFilters_bindsQueryThenQueryThenLimit() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(contentRow("ct_1", createdAt = 1000L, text = "needle"))
        val repo = repository(fake)

        repo.searchPublicContent("needle", null, null, null, 20)

        assertEquals(
            listOf<Any?>("needle", "needle", 20),
            fake.searchQueryArgs.single(),
            "binding order must be query -> query (ts_rank) -> limit"
        )
    }

    @Test
    fun searchPublicContent_withAllFilters_bindsInstanceAuthorSourceThenQueryThenLimit() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_1", createdAt = 1000L, text = "needle", instanceId = "inst_t", authorId = "au_t", sourceId = "src_t")
        )
        val repo = repository(fake)

        val result = repo.searchPublicContent(
            "needle",
            PublicId("inst_t"),
            PublicId("au_t"),
            PublicId("src_t"),
            7
        )

        assertEquals(1, result.size)
        val sql = searchSql(fake)
        assertTrue(sql.contains("AND c.instance_id = ?"), "instance filter predicate must be present")
        assertTrue(sql.contains("AND c.author_id = ?"), "author filter predicate must be present")
        assertTrue(sql.contains("AND c.source_id = ?"), "source filter predicate must be present")
        assertEquals(
            listOf<Any?>("inst_t", "au_t", "src_t", "needle", "needle", 7),
            fake.searchQueryArgs.single(),
            "binding order must be instanceId -> authorId -> sourceId -> query -> query -> limit"
        )
    }

    @Test
    fun searchPublicContent_withSourceOnly_bindsSourceThenQueryThenLimit() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(contentRow("ct_1", createdAt = 1000L, text = "needle", sourceId = "src_t"))
        val repo = repository(fake)

        repo.searchPublicContent("needle", null, null, PublicId("src_t"), 7)

        assertEquals(
            listOf<Any?>("src_t", "needle", "needle", 7),
            fake.searchQueryArgs.single(),
            "only the present source filter is bound, before the query"
        )
    }

    // ------------------------------------------------------------------
    // 3. Filters exclude non-matching rows (AND semantics through the fake)
    // ------------------------------------------------------------------

    @Test
    fun searchPublicContent_withInstanceFilter_excludesOtherInstances() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_a", createdAt = 2_000L, text = "needle", instanceId = "inst_t"),
            contentRow("ct_b", createdAt = 3_000L, text = "needle", instanceId = "inst_other")
        )
        val repo = repository(fake)

        val result = repo.searchPublicContent("needle", PublicId("inst_t"), null, null, 20)

        assertEquals(listOf("ct_a"), result.map { it.id.value }, "rows of other instances must be excluded")
    }

    @Test
    fun searchPublicContent_withAuthorAndSource_excludesNonMatchingRows() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_a", createdAt = 2_000L, text = "needle", authorId = "au_t", sourceId = "src_t"),
            contentRow("ct_b", createdAt = 3_000L, text = "needle", authorId = "au_x", sourceId = "src_t"),
            contentRow("ct_c", createdAt = 4_000L, text = "needle", authorId = "au_t", sourceId = "src_x")
        )
        val repo = repository(fake)

        val result = repo.searchPublicContent("needle", null, PublicId("au_t"), PublicId("src_t"), 20)

        assertEquals(listOf("ct_a"), result.map { it.id.value }, "author and source filters must combine with AND")
    }

    // ------------------------------------------------------------------
    // 4. Public indexability: ACTIVE / PUBLIC / allow_index exclusions
    // ------------------------------------------------------------------

    @Test
    fun searchPublicContent_excludesNonActiveContent() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_a", createdAt = 2_000L, text = "needle", status = "ACTIVE"),
            contentRow("ct_b", createdAt = 3_000L, text = "needle", status = "DELETED"),
            contentRow("ct_c", createdAt = 4_000L, text = "needle", status = "HIDDEN")
        )
        val repo = repository(fake)

        val result = repo.searchPublicContent("needle", null, null, null, 20)

        assertEquals(listOf("ct_a"), result.map { it.id.value }, "non-ACTIVE content must never be searchable")
    }

    @Test
    fun searchPublicContent_excludesNonPublicContent() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_a", createdAt = 2_000L, text = "needle", visibility = "PUBLIC"),
            contentRow("ct_b", createdAt = 3_000L, text = "needle", visibility = "UNLISTED"),
            contentRow("ct_c", createdAt = 4_000L, text = "needle", visibility = "FOLLOWERS")
        )
        val repo = repository(fake)

        val result = repo.searchPublicContent("needle", null, null, null, 20)

        assertEquals(listOf("ct_a"), result.map { it.id.value }, "non-PUBLIC content must never be searchable")
    }

    @Test
    fun searchPublicContent_excludesInstancesWithAllowIndexFalse() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_a", createdAt = 2_000L, text = "needle", allowIndex = true),
            contentRow("ct_b", createdAt = 3_000L, text = "needle", allowIndex = false)
        )
        val repo = repository(fake)

        val result = repo.searchPublicContent("needle", null, null, null, 20)

        assertEquals(listOf("ct_a"), result.map { it.id.value }, "allow_index=false instances must never be searchable")
    }

    // ------------------------------------------------------------------
    // 5. FTS match semantics and relevance ordering
    // ------------------------------------------------------------------

    @Test
    fun searchPublicContent_returnsMatchingContentAndSkipsNonMatching() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_match", createdAt = 1_000L, text = "contains the needle phrase"),
            contentRow("ct_no", createdAt = 2_000L, text = "pure haystack, nothing relevant")
        )
        val repo = repository(fake)

        val result = repo.searchPublicContent("needle", null, null, null, 20)

        assertEquals(listOf("ct_match"), result.map { it.id.value }, "non-matching text must be excluded")
    }

    @Test
    fun searchPublicContent_ordersByRelevanceFirstEvenWhenCreatedAtDiffers() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            // Higher relevance (4 token occurrences: cat x3 + dog x1) but older.
            contentRow("ct_high_rank", createdAt = 1_000L, text = "cat dog cat and cat again"),
            // Lower relevance (2 token occurrences: cat x1 + dog x1) but newer.
            contentRow("ct_new_low_rank", createdAt = 9_000L, text = "cat dog only")
        )
        val repo = repository(fake)

        val result = repo.searchPublicContent("cat dog", null, null, null, 20)

        assertEquals(
            listOf("ct_high_rank", "ct_new_low_rank"),
            result.map { it.id.value },
            "ts_rank (relevance) must dominate created_at ordering"
        )
    }

    @Test
    fun searchPublicContent_withEqualRelevance_ordersByCreatedAtDesc() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_older", createdAt = 1_000L, text = "cat"),
            contentRow("ct_newer", createdAt = 9_000L, text = "cat")
        )
        val repo = repository(fake)

        val result = repo.searchPublicContent("cat", null, null, null, 20)

        assertEquals(
            listOf("ct_newer", "ct_older"),
            result.map { it.id.value },
            "equal relevance must fall back to created_at DESC"
        )
    }

    @Test
    fun searchPublicContent_withEqualRelevanceAndCreatedAt_ordersByIdDesc() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_a", createdAt = 5_000L, text = "cat"),
            contentRow("ct_b", createdAt = 5_000L, text = "cat")
        )
        val repo = repository(fake)

        val result = repo.searchPublicContent("cat", null, null, null, 20)

        assertEquals(
            listOf("ct_b", "ct_a"),
            result.map { it.id.value },
            "equal relevance and created_at must use the id DESC tie-breaker"
        )
    }

    // ------------------------------------------------------------------
    // 6. Limit semantics
    // ------------------------------------------------------------------

    @Test
    fun searchPublicContent_appliesRequestedLimit() {
        val fake = FakeJdbcTemplate()
        fake.rows = (1..5).map { contentRow("ct_$it", createdAt = it * 1_000L, text = "needle") }
        val repo = repository(fake)

        val result = repo.searchPublicContent("needle", null, null, null, 3)

        assertEquals(listOf<Any?>("needle", "needle", 3), fake.searchQueryArgs.single())
        assertEquals(3, result.size, "the bounded result must respect the requested limit")
        assertEquals(listOf("ct_5", "ct_4", "ct_3"), result.map { it.id.value })
    }

    @Test
    fun searchPublicContent_whenLimitZero_returnsEmptyWithoutQuerying() {
        val fake = FakeJdbcTemplate()
        val repo = repository(fake)

        val result = repo.searchPublicContent("needle", null, null, null, 0)

        assertTrue(result.isEmpty())
        assertTrue(fake.executedSql.isEmpty(), "limit 0 must short-circuit without executing any query")
    }

    @Test
    fun searchPublicContent_rejectsInvalidLimits() {
        val fake = FakeJdbcTemplate()
        val repo = repository(fake)

        assertFailsWith<IllegalArgumentException> { repo.searchPublicContent("q", null, null, null, -1) }
        assertFailsWith<IllegalArgumentException> {
            repo.searchPublicContent("q", null, null, null, ContentRepository.MAX_PUBLIC_BROWSE_LIMIT + 1)
        }
    }

    // ------------------------------------------------------------------
    // 7. Media: one batch query over exactly the matched rows, no N+1
    // ------------------------------------------------------------------

    @Test
    fun searchPublicContent_loadsMediaInOneBatchPreservingOrder() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_a", createdAt = 2_000L, text = "needle"),
            contentRow("ct_b", createdAt = 1_000L, text = "needle")
        )
        fake.mediaRows = listOf(
            mediaRow("ct_a", "m_alpha"),
            mediaRow("ct_a", "m_beta"),
            mediaRow("ct_b", "m_gamma")
        )
        val repo = repository(fake)

        val result = repo.searchPublicContent("needle", null, null, null, 10)

        assertEquals(listOf("ct_a", "ct_b"), result.map { it.id.value })
        val mediaSqls = fake.executedSql.filter { it.contains("content_media") }
        assertEquals(1, mediaSqls.size, "media ids must be loaded by exactly one batched query")
        assertTrue(mediaSqls.single().contains("WHERE content_id IN ("), "media ids must be loaded with a single IN batch")
        assertEquals(
            listOf<Any?>("ct_a", "ct_b"),
            fake.mediaBatchArgs.single(),
            "the media batch must cover exactly the matched content ids in order"
        )
        assertEquals(listOf(PublicId("m_alpha"), PublicId("m_beta")), result[0].mediaIds)
        assertEquals(listOf(PublicId("m_gamma")), result[1].mediaIds)
        assertTrue(
            fake.executedSql.none { it.contains("WHERE content_id = ?") },
            "per-content media lookups are forbidden"
        )
    }

    @Test
    fun searchPublicContent_whenNothingMatches_doesNotQueryMedia() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(contentRow("ct_1", createdAt = 1_000L, text = "nothing relevant here"))
        val repo = repository(fake)

        val result = repo.searchPublicContent("needle", null, null, null, 10)

        assertTrue(result.isEmpty())
        assertEquals(1, fake.executedSql.size, "only the search query runs; no media query may follow an empty result")
        assertTrue(fake.executedSql.none { it.contains("content_media") })
    }

    // ------------------------------------------------------------------
    // 8. Aggregate reconstruction stays complete
    // ------------------------------------------------------------------

    @Test
    fun searchPublicContent_reconstructsCompleteDomainAggregates() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            contentRow("ct_1", createdAt = 1_000L, text = "needle in public text"),
            contentRow("ct_2", createdAt = 500L, text = "needle as well")
        )
        fake.mediaRows = listOf(mediaRow("ct_2", "m_only"))
        val repo = repository(fake)

        val result = repo.searchPublicContent("needle", null, null, null, 10)

        assertEquals(2, result.size)
        val first = result[0]
        assertEquals("ct_1", first.id.value)
        assertEquals("inst_public", first.instanceId.value)
        assertEquals("needle in public text", first.text)
        assertEquals("PUBLIC", first.visibility.name)
        assertEquals("ACTIVE", first.status.name)
        assertTrue(first.mediaIds.isEmpty())

        val second = result[1]
        assertEquals(listOf(PublicId("m_only")), second.mediaIds)
        assertEquals(500L, second.createdAt)
    }
}
