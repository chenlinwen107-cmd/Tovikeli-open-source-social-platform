package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

class ContentJdbcRepositoryTest {

    private class RecordingJdbcTemplate : JdbcTemplate() {
        val executedSql = mutableListOf<String>()
        val queryArgs = mutableListOf<List<Any?>>()
        val updateArgs = mutableListOf<List<Any?>>()
        var rows: List<Map<String, Any?>> = emptyList()
        var updateResult: Int = 1

        var rowSequences: List<List<Map<String, Any?>>>? = null
        private var queryCallIndex = 0

        override fun <T> query(sql: String, rowMapper: RowMapper<T>, vararg args: Any?): List<T> {
            executedSql += sql
            queryArgs += args.toList()
            val currentRows = rowSequences?.getOrNull(queryCallIndex) ?: rows
            queryCallIndex++
            return mapRows(currentRows, rowMapper)
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
    // findByIds
    // ------------------------------------------------------------------

    @Test
    fun findByIds_queriesWithInClauseAndReturnsContentWithMedia() {
        val template = RecordingJdbcTemplate()
        template.rowSequences = listOf(
            listOf(
                contentRow(id = "ct_1", sourceContentId = "sc_1"),
                contentRow(id = "ct_2", sourceContentId = "sc_2")
            ),
            listOf(
                mediaRow("ct_1", "md_1")
            )
        )
        val repo = repository(template)

        val results = repo.findByIds(listOf(PublicId("ct_1"), PublicId("ct_2")))

        val sql = template.executedSql.first()
        assertTrue(sql.contains("FROM content"))
        assertTrue(sql.contains("WHERE id IN (?,?)"))
        val args = template.queryArgs.first()
        assertEquals("ct_1", args[0])
        assertEquals("ct_2", args[1])
        assertEquals(2, results.size)
        assertEquals(PublicId("ct_1"), results[0].id)
        assertEquals(PublicId("ct_2"), results[1].id)
    }

    @Test
    fun findByIds_emptyInput_returnsEmptyListWithoutQuery() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(contentRow(id = "ct_1"))
        val repo = repository(template)

        val results = repo.findByIds(emptyList())

        assertTrue(results.isEmpty())
        assertTrue(template.executedSql.isEmpty(), "empty input must not execute any query")
    }

    @Test
    fun findByIds_partialMatches_returnsOnlyFoundContent() {
        val template = RecordingJdbcTemplate()
        template.rowSequences = listOf(
            listOf(
                contentRow(id = "ct_1", sourceContentId = "sc_1")
            ),
            emptyList()
        )
        val repo = repository(template)

        val results = repo.findByIds(listOf(PublicId("ct_1"), PublicId("ct_missing")))

        assertEquals(1, results.size)
        assertEquals(PublicId("ct_1"), results[0].id)
    }

    @Test
    fun findByIds_noMatches_returnsEmptyList() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()
        val repo = repository(template)

        val results = repo.findByIds(listOf(PublicId("ct_missing")))

        assertTrue(results.isEmpty())
    }

    // ------------------------------------------------------------------
    // updateStatus
    // ------------------------------------------------------------------

    @Test
    fun updateStatus_executesUpdateWithStatusAndTimestamp() {
        val template = RecordingJdbcTemplate()
        template.updateResult = 1
        val repo = repository(template)

        val result = repo.updateStatus(PublicId("ct_1"), com.contentplatform.backend.domain.content.ContentStatus.DELETED)

        assertTrue(result)
        assertEquals(1, template.executedSql.size)
        val sql = template.executedSql.first()
        assertTrue(sql.contains("UPDATE content SET status = ?, updated_at = ? WHERE id = ?"))
        val args = template.updateArgs.first()
        assertEquals("DELETED", args[0])
        assertTrue(args[1] is Long, "updated_at must be a Long timestamp")
        assertEquals("ct_1", args[2])
    }

    @Test
    fun updateStatus_whenNoRowUpdated_returnsFalse() {
        val template = RecordingJdbcTemplate()
        template.updateResult = 0
        val repo = repository(template)

        val result = repo.updateStatus(PublicId("ct_missing"), com.contentplatform.backend.domain.content.ContentStatus.HIDDEN)

        assertFalse(result)
    }
}
