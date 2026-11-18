package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Unit tests for [ContentTagJdbcRepository] (Stage 15-B).
 *
 * No database is involved. [RecordingJdbcTemplate] subclasses [JdbcTemplate]
 * and intercepts every batchUpdate call so the repository code path (SQL text,
 * batched argument rows, empty-list short-circuit) runs against recorded calls.
 *
 * The database-level PK uniqueness is encoded in the SQL itself
 * (PRIMARY KEY (content_id, tag_id) + ON CONFLICT DO NOTHING), so the tests
 * assert that contract textually and verify the batching shape: exactly ONE
 * batchUpdate per non-empty attachAll, carrying exactly one argument row per
 * tag id — never a per-tag write (no N+1).
 */
class ContentTagJdbcRepositoryTest {

    private class RecordingJdbcTemplate : JdbcTemplate() {
        val executedSql = mutableListOf<String>()
        val batchUpdateCount = mutableListOf<List<Array<Any?>>>()
        var totalBatchRows = 0
            private set

        override fun batchUpdate(sql: String, batchArgs: List<Array<Any?>>): IntArray {
            executedSql += sql
            batchUpdateCount += listOf(batchArgs)
            totalBatchRows += batchArgs.size
            return IntArray(batchArgs.size) { 1 }
        }
    }

    private fun repository(template: RecordingJdbcTemplate): ContentTagJdbcRepository =
        ContentTagJdbcRepository(template)

    private fun contentId(value: String = "ct_1") = PublicId(value)
    private fun tagId(value: String) = PublicId(value)

    // ------------------------------------------------------------------
    // 1. attach() single association -> one batched write with one row
    // ------------------------------------------------------------------

    @Test
    fun attach_singleAssociation_issuesOneBatchInsertWithOneRow() {
        val template = RecordingJdbcTemplate()
        val repo = repository(template)

        repo.attach(contentId("ct_1"), tagId("tg_1"))

        assertEquals(1, template.batchUpdateCount.size, "attach must issue exactly one batched write")
        val args = template.batchUpdateCount.single().single()
        assertEquals(listOf("ct_1", "tg_1"), args.toList(), "the row must carry (content_id, tag_id)")
        assertInsertWithOnConflictDoNothing(template.executedSql.single())
    }

    // ------------------------------------------------------------------
    // 2. attachAll() multiple tags -> ONE batched write, one row per tag
    // ------------------------------------------------------------------

    @Test
    fun attachAll_multipleTags_issuesOneBatchInsertWithOneRowPerTag() {
        val template = RecordingJdbcTemplate()
        val repo = repository(template)

        repo.attachAll(contentId("ct_1"), listOf(tagId("tg_1"), tagId("tg_2"), tagId("tg_3")))

        assertEquals(1, template.batchUpdateCount.size, "multiple tags must share ONE batched write (no per-tag N+1)")
        val rows = template.batchUpdateCount.single()
        assertEquals(3, rows.size, "the batch must carry one argument row per tag")
        assertEquals(
            listOf("ct_1", "tg_1"),
            rows[0].toList(),
            "row order/content must map (content_id, tag_id)"
        )
        assertEquals(listOf("ct_1", "tg_3"), rows[2].toList())
        assertEquals(3, template.totalBatchRows)
    }

    // ------------------------------------------------------------------
    // 3. Duplicate association insert does not fail and stays idempotent
    // ------------------------------------------------------------------

    @Test
    fun attachAll_duplicatePair_doesNotFailAndReliesOnOnConflictDoNothing() {
        val template = RecordingJdbcTemplate()
        val repo = repository(template)

        // Same (content_id, tag_id) attached repeatedly: the repository must not
        // throw, must keep issuing the same idempotent write, and the SQL must
        // guarantee the DB never stores a duplicate (PK + ON CONFLICT DO NOTHING).
        repo.attach(contentId("ct_1"), tagId("tg_1"))
        repo.attach(contentId("ct_1"), tagId("tg_1"))

        assertEquals(2, template.batchUpdateCount.size, "repeated attach is a legal no-fail operation")
        assertEquals(2, template.totalBatchRows)
        template.executedSql.forEach { assertInsertWithOnConflictDoNothing(it) }
        // Exactly one row per repeated write: the dedup is the DB's PK conflict.
        template.batchUpdateCount.forEach { assertEquals(1, it.size) }
    }

    // ------------------------------------------------------------------
    // 4. Only one association ever exists per (content_id, tag_id) pair
    // ------------------------------------------------------------------

    @Test
    fun attachAll_samePairNeverProducesTwoRowsInOneWrite() {
        val template = RecordingJdbcTemplate()
        val repo = repository(template)

        repo.attachAll(contentId("ct_1"), listOf(tagId("tg_1"), tagId("tg_1")))

        val sql = template.executedSql.single()
        assertTrue(sql.contains("PRIMARY KEY (content_id, tag_id)") || sql.contains("ON CONFLICT"),
            "the schema's composite PK plus ON CONFLICT DO NOTHING is what keeps a single row per pair")
        // The repository forwards whatever the caller supplied; PK uniqueness is
        // enforced by the database, never by a DELETE-then-INSERT.
        assertTrue(!sql.contains("DELETE"), "attach must never delete existing associations")
    }

    // ------------------------------------------------------------------
    // 5. attachAll(emptyList()) performs no database write
    // ------------------------------------------------------------------

    @Test
    fun attachAll_emptyList_performsNoDatabaseWrite() {
        val template = RecordingJdbcTemplate()
        val repo = repository(template)

        repo.attachAll(contentId("ct_1"), emptyList())

        assertTrue(template.executedSql.isEmpty(), "an empty tag list must not reach the database")
        assertEquals(0, template.batchUpdateCount.size)
        assertEquals(0, template.totalBatchRows)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun assertInsertWithOnConflictDoNothing(sql: String) {
        assertTrue(sql.contains("INSERT INTO content_tag (content_id, tag_id)"), "must insert into content_tag")
        assertTrue(sql.contains("VALUES (?, ?)"), "must bind exactly (content_id, tag_id)")
        assertTrue(sql.contains("ON CONFLICT (content_id, tag_id) DO NOTHING"), "must be idempotent")
    }
}
