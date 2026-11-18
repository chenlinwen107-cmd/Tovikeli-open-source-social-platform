package com.contentplatform.backend.database.repository

import com.contentplatform.backend.database.entity.ContentEntity
import com.contentplatform.backend.database.mapper.ContentMapper
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * JDBC implementation of [ContentRepository] against the `content` table and
 * the ordered `content_media` association table.
 *
 * save() persists the aggregate state (including the ordered mediaIds via
 * content_media) and uses the natural key (instance_id, source_content_id) as
 * the conflict target. No cross-aggregate queries are performed here.
 */
class ContentJdbcRepository(
    private val jdbcTemplate: JdbcTemplate
) : ContentRepository {

    private val rowMapper = RowMapper { rs, _ ->
        ContentEntity(
            id = rs.getString("id"),
            instanceId = rs.getString("instance_id"),
            sourceContentId = rs.getString("source_content_id"),
            authorId = rs.getString("author_id"),
            sourceId = rs.getString("source_id"),
            text = rs.getString("text"),
            language = rs.getString("language"),
            visibility = rs.getString("visibility"),
            status = rs.getString("status"),
            parentId = rs.getString("parent_id"),
            repostOfId = rs.getString("repost_of_id"),
            quoteOfId = rs.getString("quote_of_id"),
            topicId = rs.getString("topic_id"),
            externalCreatedAt = rs.getLong("external_created_at").let { if (rs.wasNull()) null else it },
            createdAt = rs.getLong("created_at"),
            updatedAt = rs.getLong("updated_at"),
            metadataJson = rs.getString("metadata")
        )
    }

    private val select = """
        SELECT id, instance_id, source_content_id, author_id, source_id,
               text, language, visibility, status,
               parent_id, repost_of_id, quote_of_id, topic_id,
               external_created_at, created_at, updated_at,
               metadata::text AS metadata
        FROM content c
    """.trimIndent()

    /** One ordered content_media association row (content_id + media_id). */
    private data class ContentMediaAssociation(val contentId: String, val mediaId: String)

    private val contentMediaRowMapper = RowMapper { rs, _ ->
        ContentMediaAssociation(
            contentId = rs.getString("content_id"),
            mediaId = rs.getString("media_id")
        )
    }

    override fun findById(id: PublicId): Content? =
        findSingle("$select WHERE id = ?", id.value)

    override fun findByInstanceAndSourceContentId(
        instanceId: PublicId,
        sourceContentId: String
    ): Content? =
        findSingle(
            "$select WHERE instance_id = ? AND source_content_id = ?",
            instanceId.value,
            sourceContentId
        )

    override fun findPublicContentSince(
        since: Long,
        cursor: ContentCursor?,
        limit: Int,
        viewerUserId: PublicId?
    ): ContentBrowsePage {
        require(limit in 0..ContentRepository.MAX_PUBLIC_BROWSE_LIMIT)
        if (limit == 0) return ContentBrowsePage(emptyList(), null)

        val cursorPredicate = if (cursor != null) {
            " AND (c.created_at < ? OR (c.created_at = ? AND c.id < ?))"
        } else ""
        val args = buildList<Any?> {
            add(since)
            if (viewerUserId != null) add(viewerUserId.value)
            if (cursor != null) {
                add(cursor.createdAt)
                add(cursor.createdAt)
                add(cursor.id.value)
            }
            add(limit + 1)
        }

        val viewerPredicate = if (viewerUserId != null) {
            " AND (c.visibility = 'PUBLIC' OR (c.visibility = 'PRIVATE' AND c.author_id = ?))"
        } else {
            " AND c.visibility = 'PUBLIC'"
        }

        val fetched = jdbcTemplate.query(
            """
            SELECT c.id, c.instance_id, c.source_content_id, c.author_id, c.source_id,
                   c.text, c.language, c.visibility, c.status,
                   c.parent_id, c.repost_of_id, c.quote_of_id, c.topic_id,
                   c.external_created_at, c.created_at, c.updated_at,
                   c.metadata::text AS metadata
            FROM content c
            INNER JOIN instance i ON i.id = c.instance_id
            WHERE c.status = '${ContentStatus.ACTIVE.name}'
              AND c.created_at >= ?
              $viewerPredicate
              AND i.allow_index = TRUE
              $cursorPredicate
            ORDER BY c.created_at DESC, c.id DESC
            LIMIT ?
            """.trimIndent(),
            rowMapper,
            *args.toTypedArray()
        )

        val hasNext = fetched.size > limit
        val delivered = if (hasNext) fetched.subList(0, limit) else fetched
        val nextCursor = if (hasNext) {
            ContentCursor(delivered.last().createdAt, PublicId(delivered.last().id))
        } else null

        return ContentBrowsePage(
            contents = toContentsWithMedia(delivered),
            nextCursor = nextCursor
        )
    }

    override fun findPublicContentByAuthors(authorIds: List<PublicId>, cursor: ContentCursor?, limit: Int): ContentBrowsePage {
        require(limit in 0..ContentRepository.MAX_PUBLIC_BROWSE_LIMIT)
        if (limit == 0 || authorIds.isEmpty()) return ContentBrowsePage(emptyList(), null)
        val placeholders = authorIds.joinToString(",") { "?" }
        val cursorPredicate = if (cursor != null) " AND (c.created_at < ? OR (c.created_at = ? AND c.id < ?))" else ""
        val args = buildList<Any?> {
            addAll(authorIds.map { it.value })
            if (cursor != null) { add(cursor.createdAt); add(cursor.createdAt); add(cursor.id.value) }
            add(limit + 1)
        }
        val rows = jdbcTemplate.query(
            """SELECT c.id, c.instance_id, c.source_content_id, c.author_id, c.source_id,
                      c.text, c.language, c.visibility, c.status, c.parent_id, c.repost_of_id,
                      c.quote_of_id, c.topic_id, c.external_created_at, c.created_at, c.updated_at,
                      c.metadata::text AS metadata
               FROM content c INNER JOIN instance i ON i.id = c.instance_id
               WHERE c.status = 'ACTIVE' AND c.visibility = 'PUBLIC' AND i.allow_index = TRUE
                 AND c.author_id IN ($placeholders)$cursorPredicate
               ORDER BY c.created_at DESC, c.id DESC LIMIT ?""".trimIndent(),
            rowMapper, *args.toTypedArray())
        val hasNext = rows.size > limit
        val delivered = if (hasNext) rows.subList(0, limit) else rows
        val next = if (hasNext) ContentCursor(delivered.last().createdAt, PublicId(delivered.last().id)) else null
        return ContentBrowsePage(toContentsWithMedia(delivered), next)
    }

    override fun findPublicVideoContent(cursor: ContentCursor?, limit: Int): ContentBrowsePage {
        require(limit in 0..ContentRepository.MAX_PUBLIC_BROWSE_LIMIT)
        if (limit == 0) return ContentBrowsePage(emptyList(), null)
        val cursorPredicate = if (cursor != null) " AND (c.created_at < ? OR (c.created_at = ? AND c.id < ?))" else ""
        val args = buildList<Any?> {
            if (cursor != null) { add(cursor.createdAt); add(cursor.createdAt); add(cursor.id.value) }
            add(limit + 1)
        }
        val rows = jdbcTemplate.query(
            """SELECT c.id, c.instance_id, c.source_content_id, c.author_id, c.source_id,
                      c.text, c.language, c.visibility, c.status, c.parent_id, c.repost_of_id,
                      c.quote_of_id, c.topic_id, c.external_created_at, c.created_at, c.updated_at,
                      c.metadata::text AS metadata
               FROM content c INNER JOIN instance i ON i.id = c.instance_id
               WHERE c.status = 'ACTIVE' AND c.visibility = 'PUBLIC' AND i.allow_index = TRUE
                 AND EXISTS (
                     SELECT 1 FROM content_media cm
                     INNER JOIN media m ON m.id = cm.media_id
                     WHERE cm.content_id = c.id AND m.kind = 'VIDEO' AND m.status = 'ACTIVE'
                 )$cursorPredicate
               ORDER BY c.created_at DESC, c.id DESC LIMIT ?""".trimIndent(),
            rowMapper, *args.toTypedArray())
        val hasNext = rows.size > limit
        val delivered = if (hasNext) rows.subList(0, limit) else rows
        val next = if (hasNext) ContentCursor(delivered.last().createdAt, PublicId(delivered.last().id)) else null
        return ContentBrowsePage(toContentsWithMedia(delivered), next)
    }

    override fun findPublicContent(
        instanceId: PublicId?,
        authorId: PublicId?,
        hasMedia: Boolean?,
        cursor: ContentCursor?,
        limit: Int,
        viewerAuthorId: PublicId?
    ): ContentBrowsePage {
        require(limit in 0..ContentRepository.MAX_PUBLIC_BROWSE_LIMIT) {
            "limit must be in the range 0..${ContentRepository.MAX_PUBLIC_BROWSE_LIMIT}"
        }
        if (limit == 0) return ContentBrowsePage(contents = emptyList(), nextCursor = null)

        // Optional instance scoping (Stage 13-A), author scoping (Stage 13-B),
        // media-presence scoping (Stage 13-C) and keyset cursor scoping
        // (Stage 14). Each null filter omits its predicate entirely so the
        // unfiltered Stage 10B-2 SQL and the Stage 13-A/B/C SQL stay
        // byte-for-byte unchanged for a first page. The browse WHERE clause
        // always keeps ACTIVE + PUBLIC + instance.allow_index = TRUE and the
        // locked ordering created_at DESC, c.id DESC. The media predicate is a
        // constant EXISTS/NOT EXISTS fragment and never contributes a bound
        // argument. Binding order is frozen: instanceId.value -> authorId.value
        // -> cursor.createdAt -> cursor.createdAt -> cursor.id -> limit, where
        // "limit" is always the last bound argument (the +1 probe bound).
        val instancePredicate = if (instanceId != null) {
            "\n              AND c.instance_id = ?"
        } else {
            ""
        }
        val authorPredicate = if (authorId != null) {
            "\n              AND c.author_id = ?"
        } else {
            ""
        }
        val mediaPresencePredicate = when (hasMedia) {
            true -> "\n              AND EXISTS (SELECT 1 FROM content_media cm WHERE cm.content_id = c.id)"
            false -> "\n              AND NOT EXISTS (SELECT 1 FROM content_media cm WHERE cm.content_id = c.id)"
            null -> ""
        }
        val viewerPredicate = if (viewerAuthorId != null) {
            "\n              AND (c.visibility = 'PUBLIC' OR (c.visibility = 'PRIVATE' AND c.author_id = ?))"
        } else {
            "\n              AND c.visibility = 'PUBLIC'"
        }
        val cursorPredicate = if (cursor != null) {
            "\n              AND (c.created_at < ? OR (c.created_at = ? AND c.id < ?))"
        } else {
            ""
        }
        // LIMIT requested+1 is the has-next probe: no separate count query.
        val probeLimit = limit + 1
        val args = buildList<Any?> {
            if (instanceId != null) add(instanceId.value)
            if (authorId != null) add(authorId.value)
            if (viewerAuthorId != null) add(viewerAuthorId.value)
            if (cursor != null) {
                add(cursor.createdAt)
                add(cursor.createdAt)
                add(cursor.id.value)
            }
            add(probeLimit)
        }

        val fetched = jdbcTemplate.query(
            """
            SELECT c.id, c.instance_id, c.source_content_id, c.author_id, c.source_id,
                   c.text, c.language, c.visibility, c.status,
                   c.parent_id, c.repost_of_id, c.quote_of_id, c.topic_id,
                   c.external_created_at, c.created_at, c.updated_at,
                   c.metadata::text AS metadata
            FROM content c
            INNER JOIN instance i ON i.id = c.instance_id
            WHERE c.status = '${ContentStatus.ACTIVE.name}'
              $viewerPredicate
              AND i.allow_index = TRUE$instancePredicate$authorPredicate$mediaPresencePredicate$cursorPredicate
            ORDER BY c.created_at DESC, c.id DESC
            LIMIT ?
            """.trimIndent(),
            rowMapper,
            *args.toTypedArray()
        )
        if (fetched.isEmpty()) return ContentBrowsePage(contents = emptyList(), nextCursor = null)

        // When more than the requested limit came back, the extra row is only a
        // has-next probe: it is never delivered and never participates in the
        // media batch. nextCursor is derived from the last DELIVERED row.
        val hasNext = fetched.size > limit
        val delivered = if (hasNext) fetched.subList(0, limit) else fetched
        val nextCursor = if (hasNext) {
            ContentCursor(
                createdAt = delivered.last().createdAt,
                id = PublicId(delivered.last().id)
            )
        } else {
            null
        }

        // Batched ordered media load for exactly the delivered rows: a single
        // content_media query replaces the per-content 2N read pattern and the
        // probe row never reaches this query.
        return ContentBrowsePage(
            contents = toContentsWithMedia(delivered),
            nextCursor = nextCursor
        )
    }

    override fun searchPublicContent(
        query: String,
        instanceId: PublicId?,
        authorId: PublicId?,
        sourceId: PublicId?,
        limit: Int
    ): List<Content> {
        require(limit in 0..ContentRepository.MAX_PUBLIC_BROWSE_LIMIT) {
            "limit must be in the range 0..${ContentRepository.MAX_PUBLIC_BROWSE_LIMIT}"
        }
        if (limit == 0) return emptyList()

        // PostgreSQL english full-text search is a poor fit for Chinese and
        // Japanese content. Use substring matching so multilingual posts and
        // short user-entered queries work naturally.
        val instancePredicate = if (instanceId != null) "\n              AND c.instance_id = ?" else ""
        val authorPredicate = if (authorId != null) "\n              AND c.author_id = ?" else ""
        val sourcePredicate = if (sourceId != null) "\n              AND c.source_id = ?" else ""
        val args = buildList<Any?> {
            if (instanceId != null) add(instanceId.value)
            if (authorId != null) add(authorId.value)
            if (sourceId != null) add(sourceId.value)
            add("%$query%")
            add(limit)
        }

        val entities = jdbcTemplate.query(
            """
            SELECT c.id, c.instance_id, c.source_content_id, c.author_id, c.source_id,
                   c.text, c.language, c.visibility, c.status,
                   c.parent_id, c.repost_of_id, c.quote_of_id, c.topic_id,
                   c.external_created_at, c.created_at, c.updated_at,
                   c.metadata::text AS metadata
            FROM content c
            INNER JOIN instance i ON i.id = c.instance_id
            WHERE c.status = '${ContentStatus.ACTIVE.name}'
              AND c.visibility = '${ContentVisibility.PUBLIC.name}'
              AND i.allow_index = TRUE$instancePredicate$authorPredicate$sourcePredicate
              AND c.text ILIKE ?
            ORDER BY c.created_at DESC, c.id DESC
            LIMIT ?
            """.trimIndent(),
            rowMapper,
            *args.toTypedArray()
        )
        if (entities.isEmpty()) return emptyList()

        return toContentsWithMedia(entities)
    }

    override fun findPublicContentByTopic(topicId: PublicId, cursor: ContentCursor?, limit: Int): ContentBrowsePage {
        require(limit in 0..ContentRepository.MAX_PUBLIC_BROWSE_LIMIT)
        if (limit == 0) return ContentBrowsePage(emptyList(), null)
        val cursorPredicate = if (cursor != null) " AND (c.created_at < ? OR (c.created_at = ? AND c.id < ?))" else ""
        val args = buildList<Any?> {
            add(topicId.value)
            if (cursor != null) { add(cursor.createdAt); add(cursor.createdAt); add(cursor.id.value) }
            add(limit + 1)
        }
        val fetched = jdbcTemplate.query(
            """SELECT c.id, c.instance_id, c.source_content_id, c.author_id, c.source_id,
                      c.text, c.language, c.visibility, c.status,
                      c.parent_id, c.repost_of_id, c.quote_of_id, c.topic_id,
                      c.external_created_at, c.created_at, c.updated_at,
                      c.metadata::text AS metadata
               FROM content c INNER JOIN instance i ON i.id = c.instance_id
               WHERE c.status = 'ACTIVE' AND c.visibility = 'PUBLIC' AND i.allow_index = TRUE
                 AND c.topic_id = ?$cursorPredicate
               ORDER BY c.created_at DESC, c.id DESC LIMIT ?""".trimIndent(),
            rowMapper, *args.toTypedArray())
        val hasNext = fetched.size > limit
        val delivered = if (hasNext) fetched.subList(0, limit) else fetched
        val next = if (hasNext) ContentCursor(delivered.last().createdAt, PublicId(delivered.last().id)) else null
        return ContentBrowsePage(toContentsWithMedia(delivered), next)
    }

    override fun findPublicContentByTag(
        tagId: PublicId,
        cursor: ContentCursor?,
        limit: Int
    ): ContentBrowsePage {
        require(limit in 0..ContentRepository.MAX_PUBLIC_BROWSE_LIMIT) {
            "limit must be in the range 0..${ContentRepository.MAX_PUBLIC_BROWSE_LIMIT}"
        }
        if (limit == 0) return ContentBrowsePage(contents = emptyList(), nextCursor = null)

        // Stage 15-C tag-scoped public content page. The join on content_tag
        // uses the table's composite PK (content_id, tag_id), so a content
        // associated with the requested tag appears exactly once — no row
        // duplication is possible. Filtering keeps ACTIVE + PUBLIC +
        // allow_index and the frozen Stage 14 keyset mechanics (cursor
        // predicate, LIMIT+1 probe, probe never delivered/media-batched).
        val cursorPredicate = if (cursor != null) {
            "\n              AND (c.created_at < ? OR (c.created_at = ? AND c.id < ?))"
        } else {
            ""
        }
        val probeLimit = limit + 1
        val args = buildList<Any?> {
            add(tagId.value)
            if (cursor != null) {
                add(cursor.createdAt)
                add(cursor.createdAt)
                add(cursor.id.value)
            }
            add(probeLimit)
        }

        val fetched = jdbcTemplate.query(
            """
            SELECT c.id, c.instance_id, c.source_content_id, c.author_id, c.source_id,
                   c.text, c.language, c.visibility, c.status,
                   c.parent_id, c.repost_of_id, c.quote_of_id, c.topic_id,
                   c.external_created_at, c.created_at, c.updated_at,
                   c.metadata::text AS metadata
            FROM content c
            INNER JOIN instance i ON i.id = c.instance_id
            INNER JOIN content_tag ct ON ct.content_id = c.id
            WHERE c.status = '${ContentStatus.ACTIVE.name}'
              AND c.visibility = '${ContentVisibility.PUBLIC.name}'
              AND i.allow_index = TRUE
              AND ct.tag_id = ?$cursorPredicate
            ORDER BY c.created_at DESC, c.id DESC
            LIMIT ?
            """.trimIndent(),
            rowMapper,
            *args.toTypedArray()
        )
        if (fetched.isEmpty()) return ContentBrowsePage(contents = emptyList(), nextCursor = null)

        val hasNext = fetched.size > limit
        val delivered = if (hasNext) fetched.subList(0, limit) else fetched
        val nextCursor = if (hasNext) {
            ContentCursor(
                createdAt = delivered.last().createdAt,
                id = PublicId(delivered.last().id)
            )
        } else {
            null
        }
        // Batched ordered media load for exactly the delivered rows: the probe
        // row never reaches the content_media batch query.
        return ContentBrowsePage(
            contents = toContentsWithMedia(delivered),
            nextCursor = nextCursor
        )
    }

    /**
     * Reconstructs full Content aggregates for the given content rows with a
     * single batched content_media query (no per-content media query, no N+1).
     */
    private fun toContentsWithMedia(entities: List<ContentEntity>): List<Content> {
        if (entities.isEmpty()) return emptyList()
        val mediaByContentId = loadMediaIdsBatch(entities.map { it.id })
        return entities.map { entity ->
            ContentMapper.toDomain(entity, mediaByContentId[entity.id].orEmpty())
        }
    }

    override fun save(content: Content): Content {
        val e = ContentMapper.toEntity(content)
        val rowsInserted = jdbcTemplate.update(
            """
            INSERT INTO content (
                id, instance_id, source_content_id, author_id, source_id,
                text, language, visibility, status,
                parent_id, repost_of_id, quote_of_id, topic_id,
                external_created_at, created_at, updated_at,
                metadata
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (instance_id, source_content_id) DO NOTHING
            """.trimIndent(),
            e.id,
            e.instanceId,
            e.sourceContentId,
            e.authorId,
            e.sourceId,
            e.text,
            e.language,
            e.visibility,
            e.status,
            e.parentId,
            e.repostOfId,
            e.quoteOfId,
            e.topicId,
            e.externalCreatedAt,
            e.createdAt,
            e.updatedAt,
            e.metadataJson
        )

        if (rowsInserted == 0) {
            return findByInstanceAndSourceContentId(content.instanceId, content.sourceContentId)
                ?: content
        }

        // Replace the ordered media association for this content.
        jdbcTemplate.update("DELETE FROM content_media WHERE content_id = ?", e.id)
        content.mediaIds.forEachIndexed { index, mediaId ->
            jdbcTemplate.update(
                "INSERT INTO content_media (content_id, media_id, \"position\") VALUES (?, ?, ?)",
                e.id,
                mediaId.value,
                index
            )
        }

        return content
    }

    override fun updateText(id: PublicId, text: String): Boolean {
        val now = System.currentTimeMillis()
        val rows = jdbcTemplate.update(
            "UPDATE content SET text = ?, updated_at = ? WHERE id = ?",
            text,
            now,
            id.value
        )
        return rows > 0
    }

    override fun updateStatus(id: PublicId, newStatus: ContentStatus): Boolean {
        val now = System.currentTimeMillis()
        val rows = jdbcTemplate.update(
            "UPDATE content SET status = ?, updated_at = ? WHERE id = ?",
            newStatus.name,
            now,
            id.value
        )
        return rows > 0
    }

    private fun findSingle(sql: String, vararg args: Any): Content? {
        val rows = jdbcTemplate.query(sql, rowMapper, *args)
        val entity = rows.firstOrNull() ?: return null
        val mediaIds = loadMediaIds(entity.id)
        return ContentMapper.toDomain(entity, mediaIds)
    }

    override fun countActiveByAuthorId(authorId: PublicId): Long = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM content WHERE author_id = ? AND status = 'ACTIVE'",
        Long::class.java,
        authorId.value
    ) ?: 0L

    override fun findByMediaId(mediaId: PublicId): Content? =
        findSingle(
            "$select INNER JOIN content_media cm ON cm.content_id = c.id WHERE cm.media_id = ?",
            mediaId.value
        )

    override fun findByIds(ids: List<PublicId>): List<Content> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val entities = jdbcTemplate.query(
            "$select WHERE id IN ($placeholders)",
            rowMapper,
            *ids.map { it.value }.toTypedArray()
        )
        if (entities.isEmpty()) return emptyList()
        return toContentsWithMedia(entities)
    }

    override fun findPublicByIds(ids: List<PublicId>): List<Content> {
        if (ids.isEmpty()) return emptyList()
        val placeholders = ids.joinToString(",") { "?" }
        val entities = jdbcTemplate.query(
            """
            SELECT c.id, c.instance_id, c.source_content_id, c.author_id, c.source_id,
                   c.text, c.language, c.visibility, c.status,
                   c.parent_id, c.repost_of_id, c.quote_of_id, c.topic_id,
                   c.external_created_at, c.created_at, c.updated_at,
                   c.metadata::text AS metadata
            FROM content c
            INNER JOIN instance i ON i.id = c.instance_id
            WHERE c.id IN ($placeholders)
              AND c.status = '${ContentStatus.ACTIVE.name}'
              AND c.visibility = '${ContentVisibility.PUBLIC.name}'
              AND i.allow_index = TRUE
            """.trimIndent(),
            rowMapper,
            *ids.map { it.value }.toTypedArray()
        )
        if (entities.isEmpty()) return emptyList()
        return toContentsWithMedia(entities)
    }

    private fun loadMediaIds(contentId: String): List<PublicId> {
        val rows = jdbcTemplate.queryForList(
            "SELECT media_id FROM content_media WHERE content_id = ? ORDER BY \"position\", media_id",
            String::class.java,
            contentId
        )
        return rows.mapNotNull { it?.let { id -> PublicId(id) } }
    }

    /**
     * Loads the ordered media ids for many content rows in a single query.
     *
     * Rows are grouped per content id preserving the position ordering
     * produced by ORDER BY content_id, "position", media_id. Content rows
     * without media associations are simply absent from the returned map.
     */
    private fun loadMediaIdsBatch(contentIds: List<String>): Map<String, List<PublicId>> {
        if (contentIds.isEmpty()) return emptyMap()
        val placeholders = contentIds.joinToString(",") { "?" }
        val rows = jdbcTemplate.query(
            """
            SELECT content_id, media_id
            FROM content_media
            WHERE content_id IN ($placeholders)
            ORDER BY content_id, "position", media_id
            """.trimIndent(),
            contentMediaRowMapper,
            *contentIds.toTypedArray()
        )
        return rows.groupBy({ it.contentId }, { PublicId(it.mediaId) })
    }
}
