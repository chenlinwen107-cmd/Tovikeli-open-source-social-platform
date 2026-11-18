package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

class ContentJdbcRepositoryFindPublicByIdsTest {

    private class RecordingJdbcTemplate : JdbcTemplate() {
        val executedSql = mutableListOf<String>()
        val queryArgs = mutableListOf<List<Any?>>()
        var rows: List<Map<String, Any?>> = emptyList()

        var rowSequences: List<List<Map<String, Any?>>>? = null
        private var queryCallIndex = 0

        override fun <T> query(sql: String, rowMapper: RowMapper<T>, vararg args: Any?): List<T> {
            executedSql += sql
            queryArgs += args.toList()
            val currentRows = rowSequences?.getOrNull(queryCallIndex) ?: rows
            queryCallIndex++
            return mapRows(currentRows, rowMapper)
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

    private fun contentRow(
        id: String,
        instanceId: String = "inst_1",
        sourceContentId: String = "sc_1",
        authorId: String = "au_1",
        sourceId: String = "src_1",
        text: String? = "hello",
        language: String? = "en",
        visibility: String = "PUBLIC",
        status: String = "ACTIVE",
        parentId: String? = null,
        repostOfId: String? = null,
        quoteOfId: String? = null,
        externalCreatedAt: Long? = null,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
        metadataJson: String = "{}"
    ): Map<String, Any?> = mapOf(
        "id" to id,
        "instance_id" to instanceId,
        "source_content_id" to sourceContentId,
        "author_id" to authorId,
        "source_id" to sourceId,
        "text" to text,
        "language" to language,
        "visibility" to visibility,
        "status" to status,
        "parent_id" to parentId,
        "repost_of_id" to repostOfId,
        "quote_of_id" to quoteOfId,
        "external_created_at" to externalCreatedAt,
        "created_at" to createdAt,
        "updated_at" to updatedAt,
        "metadata" to metadataJson
    )

    private fun mediaRow(
        contentId: String,
        mediaId: String
    ): Map<String, Any?> = mapOf(
        "content_id" to contentId,
        "media_id" to mediaId
    )

    private fun repository(template: RecordingJdbcTemplate): ContentJdbcRepository =
        ContentJdbcRepository(template)

    // ------------------------------------------------------------------
    // findPublicByIds — empty input
    // ------------------------------------------------------------------

    @Test
    fun findPublicByIds_emptyInput_returnsEmptyListWithoutQuery() {
        val template = RecordingJdbcTemplate()
        val repo = repository(template)

        val results = repo.findPublicByIds(emptyList())

        assertTrue(results.isEmpty())
        assertTrue(template.executedSql.isEmpty(), "empty input must not execute any query")
    }

    // ------------------------------------------------------------------
    // findPublicByIds — single ID
    // ------------------------------------------------------------------

    @Test
    fun findPublicByIds_singleId_returnsMatchingContent() {
        val template = RecordingJdbcTemplate()
        template.rowSequences = listOf(
            listOf(contentRow(id = "ct_1", sourceContentId = "sc_1")),
            listOf(mediaRow("ct_1", "md_1"))
        )
        val repo = repository(template)

        val results = repo.findPublicByIds(listOf(PublicId("ct_1")))

        assertEquals(1, results.size)
        assertEquals(PublicId("ct_1"), results[0].id)
        val sql = template.executedSql.first()
        assertTrue(sql.contains("WHERE c.id IN (?)"))
        assertTrue(sql.contains("c.status = 'ACTIVE'"))
        assertTrue(sql.contains("c.visibility = 'PUBLIC'"))
        assertTrue(sql.contains("i.allow_index = TRUE"))
    }

    // ------------------------------------------------------------------
    // findPublicByIds — multiple IDs
    // ------------------------------------------------------------------

    @Test
    fun findPublicByIds_multipleIds_returnsAllMatchingContent() {
        val template = RecordingJdbcTemplate()
        template.rowSequences = listOf(
            listOf(
                contentRow(id = "ct_1", sourceContentId = "sc_1"),
                contentRow(id = "ct_2", sourceContentId = "sc_2")
            ),
            emptyList()
        )
        val repo = repository(template)

        val results = repo.findPublicByIds(listOf(PublicId("ct_1"), PublicId("ct_2")))

        assertEquals(2, results.size)
        val sql = template.executedSql.first()
        assertTrue(sql.contains("WHERE c.id IN (?,?)"))
        val args = template.queryArgs.first()
        assertEquals("ct_1", args[0])
        assertEquals("ct_2", args[1])
    }

    // ------------------------------------------------------------------
    // findPublicByIds — partial match
    // ------------------------------------------------------------------

    @Test
    fun findPublicByIds_partialMatch_returnsOnlyMatchingContent() {
        val template = RecordingJdbcTemplate()
        template.rowSequences = listOf(
            listOf(contentRow(id = "ct_1", sourceContentId = "sc_1")),
            emptyList()
        )
        val repo = repository(template)

        val results = repo.findPublicByIds(listOf(PublicId("ct_1"), PublicId("ct_missing")))

        assertEquals(1, results.size)
        assertEquals(PublicId("ct_1"), results[0].id)
    }

    // ------------------------------------------------------------------
    // findPublicByIds — no matches
    // ------------------------------------------------------------------

    @Test
    fun findPublicByIds_noMatches_returnsEmptyList() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()
        val repo = repository(template)

        val results = repo.findPublicByIds(listOf(PublicId("ct_missing")))

        assertTrue(results.isEmpty())
    }

    // ------------------------------------------------------------------
    // findPublicByIds — SQL contains public filter
    // ------------------------------------------------------------------

    @Test
    fun findPublicByIds_sqlContainsActiveFilter() {
        val template = RecordingJdbcTemplate()
        template.rowSequences = listOf(
            listOf(contentRow(id = "ct_1")),
            emptyList()
        )
        val repo = repository(template)

        repo.findPublicByIds(listOf(PublicId("ct_1")))

        val sql = template.executedSql.first()
        assertTrue(sql.contains("c.status = 'ACTIVE'"), "SQL must filter ACTIVE status")
    }

    @Test
    fun findPublicByIds_sqlContainsPublicVisibilityFilter() {
        val template = RecordingJdbcTemplate()
        template.rowSequences = listOf(
            listOf(contentRow(id = "ct_1")),
            emptyList()
        )
        val repo = repository(template)

        repo.findPublicByIds(listOf(PublicId("ct_1")))

        val sql = template.executedSql.first()
        assertTrue(sql.contains("c.visibility = 'PUBLIC'"), "SQL must filter PUBLIC visibility")
    }

    @Test
    fun findPublicByIds_sqlContainsAllowIndexFilter() {
        val template = RecordingJdbcTemplate()
        template.rowSequences = listOf(
            listOf(contentRow(id = "ct_1")),
            emptyList()
        )
        val repo = repository(template)

        repo.findPublicByIds(listOf(PublicId("ct_1")))

        val sql = template.executedSql.first()
        assertTrue(sql.contains("i.allow_index = TRUE"), "SQL must filter allow_index = TRUE")
    }

    @Test
    fun findPublicByIds_sqlJoinsInstance() {
        val template = RecordingJdbcTemplate()
        template.rowSequences = listOf(
            listOf(contentRow(id = "ct_1")),
            emptyList()
        )
        val repo = repository(template)

        repo.findPublicByIds(listOf(PublicId("ct_1")))

        val sql = template.executedSql.first()
        assertTrue(sql.contains("INNER JOIN instance i ON i.id = c.instance_id"), "SQL must join instance table")
    }

    // ------------------------------------------------------------------
    // findPublicByIds — batch query (single SQL call)
    // ------------------------------------------------------------------

    @Test
    fun findPublicByIds_usesSingleContentQuery() {
        val template = RecordingJdbcTemplate()
        template.rowSequences = listOf(
            listOf(
                contentRow(id = "ct_1"),
                contentRow(id = "ct_2"),
                contentRow(id = "ct_3")
            ),
            emptyList()
        )
        val repo = repository(template)

        repo.findPublicByIds(listOf(PublicId("ct_1"), PublicId("ct_2"), PublicId("ct_3")))

        val contentSql = template.executedSql.first()
        assertTrue(contentSql.contains("FROM content c"), "first query must be content lookup")
        assertTrue(contentSql.contains("WHERE c.id IN"), "first query must batch by IDs")
    }

    // ------------------------------------------------------------------
    // findPublicByIds — with media batch
    // ------------------------------------------------------------------

    @Test
    fun findPublicByIds_withMedia_returnsContentWithMediaIds() {
        val template = RecordingJdbcTemplate()
        template.rowSequences = listOf(
            listOf(contentRow(id = "ct_1")),
            listOf(
                mediaRow("ct_1", "md_1"),
                mediaRow("ct_1", "md_2")
            )
        )
        val repo = repository(template)

        val results = repo.findPublicByIds(listOf(PublicId("ct_1")))

        assertEquals(1, results.size)
        assertEquals(2, results[0].mediaIds.size)
        assertEquals(PublicId("md_1"), results[0].mediaIds[0])
        assertEquals(PublicId("md_2"), results[0].mediaIds[1])
    }
}
