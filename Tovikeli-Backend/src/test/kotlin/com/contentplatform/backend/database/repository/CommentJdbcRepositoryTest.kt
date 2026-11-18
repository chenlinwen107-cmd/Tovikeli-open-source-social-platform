package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.comment.CommentCursor
import com.contentplatform.backend.domain.comment.CommentStatus
import com.contentplatform.backend.domain.common.PublicId
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

class CommentJdbcRepositoryTest {

    private class RecordingJdbcTemplate : JdbcTemplate() {

        val executedSql = mutableListOf<String>()
        val queryArgs = mutableListOf<List<Any?>>()
        val updateArgs = mutableListOf<List<Any?>>()

        var rows: List<Map<String, Any?>> = emptyList()
        var updateResult: Int = 1

        override fun <T> query(
            sql: String,
            rowMapper: RowMapper<T>,
            vararg args: Any?
        ): List<T> {
            executedSql += sql
            queryArgs += args.toList()
            return mapRows(rows, rowMapper)
        }

        override fun update(
            sql: String,
            vararg args: Any?
        ): Int {
            executedSql += sql
            updateArgs += args.toList()
            return updateResult
        }

        private fun <T> mapRows(
            rows: List<Map<String, Any?>>,
            rowMapper: RowMapper<T>
        ): List<T> {
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

        private fun resultSetFor(
            rows: List<Map<String, Any?>>
        ): ResultSet {
            val cursor = AtomicInteger(-1)
            var wasNull = false

            val proxy = Proxy.newProxyInstance(
                CommentJdbcRepository::class.java.classLoader,
                arrayOf(ResultSet::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "next" ->
                        cursor.incrementAndGet() < rows.size

                    "wasNull" ->
                        wasNull

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

                    else ->
                        defaultFor(method.returnType)
                }
            }

            @Suppress("UNCHECKED_CAST")
            return proxy as ResultSet
        }

        private fun defaultFor(type: Class<*>): Any? =
            when (type) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
    }

    private fun commentRow(
        id: String,
        userId: String,
        contentId: String,
        parentCommentId: String?,
        text: String,
        status: String,
        createdAt: Long = 1000L,
        updatedAt: Long = createdAt
    ): Map<String, Any?> =
        mapOf(
            "id" to id,
            "user_id" to userId,
            "content_id" to contentId,
            "parent_comment_id" to parentCommentId,
            "text" to text,
            "status" to status,
            "created_at" to createdAt,
            "updated_at" to updatedAt
        )

    private fun repository(
        template: RecordingJdbcTemplate
    ): CommentJdbcRepository =
        CommentJdbcRepository(template)

    private fun comment(
        parentCommentId: PublicId? = null,
        text: String = "hello",
        status: CommentStatus = CommentStatus.ACTIVE
    ): Comment =
        Comment(
            id = PublicId("cmt_1"),
            userId = PublicId("usr_1"),
            contentId = PublicId("ct_1"),
            parentCommentId = parentCommentId,
            text = text,
            status = status,
            createdAt = 1000L,
            updatedAt = 2000L
        )

    // ------------------------------------------------------------------
    // save
    // ------------------------------------------------------------------

    @Test
    fun save_issuesStrictInsertWithAllEightColumnsAndReturnsSameAggregate() {
        val template = RecordingJdbcTemplate()
        val value = comment(parentCommentId = PublicId("cmt_parent"))

        val saved = repository(template).save(value)

        assertTrue(
            saved === value,
            "save must return the supplied aggregate"
        )

        val sql = template.executedSql.single()

        assertTrue(
            sql.startsWith("INSERT INTO content_comment"),
            "save must use INSERT"
        )
        assertTrue(
            sql.contains(
                "id, user_id, content_id, parent_comment_id, text, status, created_at, updated_at"
            ),
            "all eight columns must be inserted"
        )
        assertTrue(
            sql.contains("VALUES (?, ?, ?, ?, ?, ?, ?, ?)"),
            "eight bound parameters are required"
        )
        assertFalse(
            sql.contains("ON CONFLICT"),
            "save must not upsert"
        )
        assertFalse(
            sql.contains("UPDATE"),
            "save must not update"
        )

        assertEquals(
            listOf<Any?>(
                value.id.value,
                "usr_1",
                "ct_1",
                "cmt_parent",
                "hello",
                "ACTIVE",
                1000L,
                2000L
            ),
            template.updateArgs.single()
        )
    }

    @Test
    fun save_bindsNullParentAsNull() {
        val template = RecordingJdbcTemplate()
        val value = comment(parentCommentId = null)

        repository(template).save(value)

        assertEquals(
            null,
            template.updateArgs.single()[3],
            "top-level comment must bind NULL parent_comment_id"
        )
    }

    // ------------------------------------------------------------------
    // findById
    // ------------------------------------------------------------------

    @Test
    fun findById_queriesByPrimaryKeyAndMapsActiveComment() {
        val template = RecordingJdbcTemplate()

        template.rows = listOf(
            commentRow(
                id = "cmt_1",
                userId = "usr_1",
                contentId = "ct_1",
                parentCommentId = null,
                text = "hello",
                status = "ACTIVE",
                createdAt = 1000L,
                updatedAt = 2000L
            )
        )

        val found = repository(template).findById(PublicId("cmt_1"))

        val sql = template.executedSql.single()

        assertTrue(sql.contains("FROM content_comment"))
        assertTrue(sql.contains("WHERE id = ?"))
        assertEquals(
            listOf<Any?>("cmt_1"),
            template.queryArgs.single()
        )

        assertEquals(PublicId("cmt_1"), found?.id)
        assertEquals(PublicId("usr_1"), found?.userId)
        assertEquals(PublicId("ct_1"), found?.contentId)
        assertNull(found?.parentCommentId)
        assertEquals("hello", found?.text)
        assertEquals(CommentStatus.ACTIVE, found?.status)
        assertEquals(1000L, found?.createdAt)
        assertEquals(2000L, found?.updatedAt)
    }

    @Test
    fun findById_returnsDeletedCommentToo() {
        val template = RecordingJdbcTemplate()

        template.rows = listOf(
            commentRow(
                id = "cmt_deleted",
                userId = "usr_1",
                contentId = "ct_1",
                parentCommentId = "cmt_parent",
                text = "deleted",
                status = "DELETED",
                createdAt = 1000L,
                updatedAt = 3000L
            )
        )

        val found = repository(template).findById(
            PublicId("cmt_deleted")
        )

        assertEquals(CommentStatus.DELETED, found?.status)
        assertEquals(
            PublicId("cmt_parent"),
            found?.parentCommentId
        )
    }

    @Test
    fun findById_whenAbsent_returnsNull() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()

        assertNull(
            repository(template).findById(
                PublicId("cmt_missing")
            )
        )
    }

    // ------------------------------------------------------------------
    // findByContentId
    // ------------------------------------------------------------------

    @Test
    fun findByContentId_returnsOnlyActiveCommentsInChronologicalOrderWithLimit() {
        val template = RecordingJdbcTemplate()

        template.rows = listOf(
            commentRow(
                id = "cmt_1",
                userId = "usr_1",
                contentId = "ct_1",
                parentCommentId = null,
                text = "first",
                status = "ACTIVE",
                createdAt = 1000L
            ),
            commentRow(
                id = "cmt_2",
                userId = "usr_2",
                contentId = "ct_1",
                parentCommentId = null,
                text = "second",
                status = "ACTIVE",
                createdAt = 2000L
            )
        )

        val found = repository(template).findByContentId(
            PublicId("ct_1"),
            20
        )

        val sql = template.executedSql.single()

        assertTrue(
            sql.contains("WHERE content_id = ?")
        )
        assertTrue(
            sql.contains("AND status = 'ACTIVE'")
        )
        assertTrue(
            sql.contains("ORDER BY created_at ASC, id ASC")
        )
        assertTrue(
            sql.contains("LIMIT ?")
        )

        assertEquals(
            listOf<Any?>("ct_1", 20),
            template.queryArgs.single()
        )

        assertEquals(2, found.size)
        assertEquals(PublicId("cmt_1"), found[0].id)
        assertEquals(PublicId("cmt_2"), found[1].id)
    }

    @Test
    fun findByContentId_whenNoRows_returnsEmptyList() {
        val template = RecordingJdbcTemplate()

        val found = repository(template).findByContentId(
            PublicId("ct_missing"),
            10
        )

        assertTrue(found.isEmpty())
    }

    // ------------------------------------------------------------------
    // findByParentCommentId
    // ------------------------------------------------------------------

    @Test
    fun findByParentCommentId_returnsOnlyActiveRepliesInChronologicalOrderWithLimit() {
        val template = RecordingJdbcTemplate()

        template.rows = listOf(
            commentRow(
                id = "cmt_reply_1",
                userId = "usr_1",
                contentId = "ct_1",
                parentCommentId = "cmt_parent",
                text = "reply one",
                status = "ACTIVE",
                createdAt = 1000L
            ),
            commentRow(
                id = "cmt_reply_2",
                userId = "usr_2",
                contentId = "ct_1",
                parentCommentId = "cmt_parent",
                text = "reply two",
                status = "ACTIVE",
                createdAt = 2000L
            )
        )

        val found = repository(template).findByParentCommentId(
            PublicId("cmt_parent"),
            15
        )

        val sql = template.executedSql.single()

        assertTrue(
            sql.contains("WHERE parent_comment_id = ?")
        )
        assertTrue(
            sql.contains("AND status = 'ACTIVE'")
        )
        assertTrue(
            sql.contains("ORDER BY created_at ASC, id ASC")
        )
        assertTrue(
            sql.contains("LIMIT ?")
        )

        assertEquals(
            listOf<Any?>("cmt_parent", 15),
            template.queryArgs.single()
        )

        assertEquals(2, found.size)
        assertEquals(
            PublicId("cmt_reply_1"),
            found[0].id
        )
        assertEquals(
            PublicId("cmt_reply_2"),
            found[1].id
        )
    }

    // ------------------------------------------------------------------
    // update
    // ------------------------------------------------------------------

    @Test
    fun update_changesOnlyTextStatusAndUpdatedAt() {
        val template = RecordingJdbcTemplate()

        val value = comment(
            parentCommentId = PublicId("cmt_parent"),
            text = "deleted text",
            status = CommentStatus.DELETED
        )

        val updated = repository(template).update(value)

        assertTrue(
            updated === value,
            "update must return the supplied aggregate"
        )

        val sql = template.executedSql.single()

        assertTrue(
            sql.startsWith("UPDATE content_comment")
        )
        assertTrue(
            sql.contains("SET text = ?, status = ?, updated_at = ?")
        )
        assertTrue(
            sql.contains("WHERE id = ?")
        )
        assertFalse(
            sql.contains("DELETE FROM"),
            "comment deletion must never be physical DELETE"
        )

        assertEquals(
            listOf<Any?>(
                "deleted text",
                "DELETED",
                2000L,
                "cmt_1"
            ),
            template.updateArgs.single()
        )

        assertFalse(
            sql.contains("user_id = ?"),
            "update must not change comment owner"
        )
        assertFalse(
            sql.contains("content_id = ?"),
            "update must not change content ownership"
        )
        assertFalse(
            sql.contains("parent_comment_id = ?"),
            "update must not change parent"
        )
        assertFalse(
            sql.contains("created_at = ?"),
            "update must not change creation time"
        )
    }

    @Test
    fun update_doesNotIssueDelete() {
        val template = RecordingJdbcTemplate()
        val value = comment(status = CommentStatus.DELETED)

        repository(template).update(value)

        assertFalse(
            template.executedSql.single().contains(
                "DELETE FROM content_comment"
            )
        )
    }

    // ------------------------------------------------------------------
    // countActiveByContentId
    // ------------------------------------------------------------------

    @Test
    fun countActiveByContentId_queriesCountWithActiveStatusFilter() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(mapOf("cnt" to 5L))

        val count = repository(template).countActiveByContentId(PublicId("ct_1"))

        val sql = template.executedSql.single()
        assertTrue(sql.contains("SELECT COUNT(*)"))
        assertTrue(sql.contains("FROM content_comment"))
        assertTrue(sql.contains("WHERE content_id = ?"))
        assertTrue(sql.contains("AND status = 'ACTIVE'"))
        assertEquals(listOf<Any?>("ct_1"), template.queryArgs.single())
        assertEquals(5L, count)
    }

    @Test
    fun countActiveByContentId_whenNoRows_returnsZero() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(mapOf("cnt" to 0L))

        val count = repository(template).countActiveByContentId(PublicId("ct_missing"))

        assertEquals(0L, count)
    }

    // ------------------------------------------------------------------
    // findByContentId with cursor (Stage 26)
    // ------------------------------------------------------------------

    @Test
    fun findByContentId_withCursor_limitZero_returnsEmptyPage() {
        val template = RecordingJdbcTemplate()

        val page = repository(template).findByContentId(
            PublicId("ct_1"), 0, null
        )

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
        assertTrue(template.executedSql.isEmpty(), "limit=0 must not execute any query")
    }

    @Test
    fun findByContentId_withCursor_noCursor_firstPage() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            commentRow(id = "cmt_1", userId = "usr_1", contentId = "ct_1",
                parentCommentId = null, text = "first", status = "ACTIVE", createdAt = 1000L),
            commentRow(id = "cmt_2", userId = "usr_2", contentId = "ct_1",
                parentCommentId = null, text = "second", status = "ACTIVE", createdAt = 2000L)
        )

        val page = repository(template).findByContentId(
            PublicId("ct_1"), 20, null
        )

        val sql = template.executedSql.single()
        assertTrue(sql.contains("WHERE content_id = ?"))
        assertTrue(sql.contains("AND status = 'ACTIVE'"))
        assertTrue(sql.contains("ORDER BY created_at ASC, id ASC"))
        assertTrue(sql.contains("LIMIT ?"))
        assertFalse(sql.contains("created_at > ?"), "no cursor predicate without cursor")

        assertEquals(listOf<Any?>("ct_1", 21), template.queryArgs.single())
        assertEquals(2, page.items.size)
        assertEquals(PublicId("cmt_1"), page.items[0].id)
        assertEquals(PublicId("cmt_2"), page.items[1].id)
        assertNull(page.nextCursor, "no probe row means no next cursor")
    }

    @Test
    fun findByContentId_withCursor_hasNextPage_returnsNextCursor() {
        val template = RecordingJdbcTemplate()
        // 3 rows fetched with probeLimit=3 (limit=2 + 1 probe)
        template.rows = listOf(
            commentRow(id = "cmt_1", userId = "usr_1", contentId = "ct_1",
                parentCommentId = null, text = "first", status = "ACTIVE", createdAt = 1000L),
            commentRow(id = "cmt_2", userId = "usr_2", contentId = "ct_1",
                parentCommentId = null, text = "second", status = "ACTIVE", createdAt = 2000L),
            commentRow(id = "cmt_3", userId = "usr_3", contentId = "ct_1",
                parentCommentId = null, text = "third", status = "ACTIVE", createdAt = 3000L)
        )

        val page = repository(template).findByContentId(
            PublicId("ct_1"), 2, null
        )

        assertEquals(2, page.items.size, "probe row must not be delivered")
        assertEquals(PublicId("cmt_1"), page.items[0].id)
        assertEquals(PublicId("cmt_2"), page.items[1].id)
        assertEquals(2000L, page.nextCursor?.createdAt)
        assertEquals(PublicId("cmt_2"), page.nextCursor?.commentId)
    }

    @Test
    fun findByContentId_withCursor_withCursor_usesKeysetPredicate() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            commentRow(id = "cmt_4", userId = "usr_4", contentId = "ct_1",
                parentCommentId = null, text = "fourth", status = "ACTIVE", createdAt = 4000L)
        )

        val cursor = CommentCursor(createdAt = 2000L, commentId = PublicId("cmt_2"))
        val page = repository(template).findByContentId(
            PublicId("ct_1"), 5, cursor
        )

        val sql = template.executedSql.single()
        assertTrue(sql.contains("created_at > ?"), "must contain keyset cursor predicate")
        assertTrue(sql.contains("created_at = ? AND id > ?"), "must contain tie-breaker predicate")

        assertEquals(listOf<Any?>("ct_1", 2000L, 2000L, "cmt_2", 6), template.queryArgs.single())
        assertEquals(1, page.items.size)
        assertEquals(PublicId("cmt_4"), page.items[0].id)
        assertNull(page.nextCursor)
    }

    @Test
    fun findByContentId_withCursor_whenEmpty_returnsEmptyPage() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()

        val cursor = CommentCursor(createdAt = 1000L, commentId = PublicId("cmt_1"))
        val page = repository(template).findByContentId(
            PublicId("ct_missing"), 10, cursor
        )

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }

    // ------------------------------------------------------------------
    // findByParentCommentId with cursor (Stage 26)
    // ------------------------------------------------------------------

    @Test
    fun findByParentCommentId_withCursor_limitZero_returnsEmptyPage() {
        val template = RecordingJdbcTemplate()

        val page = repository(template).findByParentCommentId(
            PublicId("cmt_parent"), 0, null
        )

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
        assertTrue(template.executedSql.isEmpty(), "limit=0 must not execute any query")
    }

    @Test
    fun findByParentCommentId_withCursor_noCursor_firstPage() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            commentRow(id = "cmt_reply_1", userId = "usr_1", contentId = "ct_1",
                parentCommentId = "cmt_parent", text = "reply one", status = "ACTIVE", createdAt = 1000L),
            commentRow(id = "cmt_reply_2", userId = "usr_2", contentId = "ct_1",
                parentCommentId = "cmt_parent", text = "reply two", status = "ACTIVE", createdAt = 2000L)
        )

        val page = repository(template).findByParentCommentId(
            PublicId("cmt_parent"), 20, null
        )

        val sql = template.executedSql.single()
        assertTrue(sql.contains("WHERE parent_comment_id = ?"))
        assertTrue(sql.contains("AND status = 'ACTIVE'"))
        assertTrue(sql.contains("ORDER BY created_at ASC, id ASC"))
        assertTrue(sql.contains("LIMIT ?"))
        assertFalse(sql.contains("created_at > ?"), "no cursor predicate without cursor")

        assertEquals(listOf<Any?>("cmt_parent", 21), template.queryArgs.single())
        assertEquals(2, page.items.size)
        assertEquals(PublicId("cmt_reply_1"), page.items[0].id)
        assertEquals(PublicId("cmt_reply_2"), page.items[1].id)
        assertNull(page.nextCursor, "no probe row means no next cursor")
    }

    @Test
    fun findByParentCommentId_withCursor_hasNextPage_returnsNextCursor() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            commentRow(id = "cmt_reply_1", userId = "usr_1", contentId = "ct_1",
                parentCommentId = "cmt_parent", text = "reply one", status = "ACTIVE", createdAt = 1000L),
            commentRow(id = "cmt_reply_2", userId = "usr_2", contentId = "ct_1",
                parentCommentId = "cmt_parent", text = "reply two", status = "ACTIVE", createdAt = 2000L),
            commentRow(id = "cmt_reply_3", userId = "usr_3", contentId = "ct_1",
                parentCommentId = "cmt_parent", text = "reply three", status = "ACTIVE", createdAt = 3000L)
        )

        val page = repository(template).findByParentCommentId(
            PublicId("cmt_parent"), 2, null
        )

        assertEquals(2, page.items.size, "probe row must not be delivered")
        assertEquals(PublicId("cmt_reply_1"), page.items[0].id)
        assertEquals(PublicId("cmt_reply_2"), page.items[1].id)
        assertEquals(2000L, page.nextCursor?.createdAt)
        assertEquals(PublicId("cmt_reply_2"), page.nextCursor?.commentId)
    }

    @Test
    fun findByParentCommentId_withCursor_withCursor_usesKeysetPredicate() {
        val template = RecordingJdbcTemplate()
        template.rows = listOf(
            commentRow(id = "cmt_reply_4", userId = "usr_4", contentId = "ct_1",
                parentCommentId = "cmt_parent", text = "reply four", status = "ACTIVE", createdAt = 4000L)
        )

        val cursor = CommentCursor(createdAt = 2000L, commentId = PublicId("cmt_reply_2"))
        val page = repository(template).findByParentCommentId(
            PublicId("cmt_parent"), 5, cursor
        )

        val sql = template.executedSql.single()
        assertTrue(sql.contains("created_at > ?"), "must contain keyset cursor predicate")
        assertTrue(sql.contains("created_at = ? AND id > ?"), "must contain tie-breaker predicate")

        assertEquals(listOf<Any?>("cmt_parent", 2000L, 2000L, "cmt_reply_2", 6), template.queryArgs.single())
        assertEquals(1, page.items.size)
        assertEquals(PublicId("cmt_reply_4"), page.items[0].id)
        assertNull(page.nextCursor)
    }

    @Test
    fun findByParentCommentId_withCursor_whenEmpty_returnsEmptyPage() {
        val template = RecordingJdbcTemplate()
        template.rows = emptyList()

        val cursor = CommentCursor(createdAt = 1000L, commentId = PublicId("cmt_reply_1"))
        val page = repository(template).findByParentCommentId(
            PublicId("cmt_missing"), 10, cursor
        )

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }
}
