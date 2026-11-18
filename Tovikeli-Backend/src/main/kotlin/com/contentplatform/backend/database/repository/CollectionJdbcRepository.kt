package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CollectionDetail
import com.contentplatform.backend.repository.CollectionItem
import com.contentplatform.backend.repository.CollectionOwner
import com.contentplatform.backend.repository.CollectionRepository
import com.contentplatform.backend.repository.CollectionSummary
import org.springframework.jdbc.core.JdbcTemplate

class CollectionJdbcRepository(private val jdbc: JdbcTemplate) : CollectionRepository {

    override fun list(ownerId: PublicId?, viewerId: PublicId?): List<CollectionSummary> {
        val where = if (ownerId == null) "" else "WHERE c.owner_id = ?"
        val args = if (ownerId == null) emptyArray<Any>() else arrayOf(ownerId.value)
        return jdbc.query(
            """SELECT c.id,c.title,c.description,c.cover,c.owner_id,c.created_at,
                      u.username,COALESCE(u.display_name,u.username) nickname,
                      a.avatar,
                      (SELECT count(*) FROM collection_item ci WHERE ci.collection_id=c.id) item_count
               FROM collection c
               JOIN platform_user u ON u.id=c.owner_id
               LEFT JOIN author a ON a.source_author_id=u.id
               $where
               ORDER BY c.created_at DESC,c.id DESC""",
            { rs, _ -> mapSummary(rs, viewerId) }, *args
        )
    }

    override fun findById(id: PublicId, viewerId: PublicId?): CollectionDetail? {
        val summary = jdbc.query(
            """SELECT c.id,c.title,c.description,c.cover,c.owner_id,c.created_at,
                      u.username,COALESCE(u.display_name,u.username) nickname,
                      a.avatar,
                      (SELECT count(*) FROM collection_item ci WHERE ci.collection_id=c.id) item_count
               FROM collection c
               JOIN platform_user u ON u.id=c.owner_id
               LEFT JOIN author a ON a.source_author_id=u.id
               WHERE c.id=?""",
            { rs, _ -> mapSummary(rs, viewerId) }, id.value
        ).firstOrNull() ?: return null

        val items = jdbc.query(
            """SELECT ci.id item_id,ci.target_type,ci.target_id
               FROM collection_item ci
               WHERE ci.collection_id=?
               ORDER BY ci.sort_order ASC,ci.created_at ASC,ci.id ASC""",
            { rs, _ -> mapItem(rs) }, id.value
        )
        return CollectionDetail(summary, items)
    }

    override fun create(id: PublicId, ownerId: PublicId, title: String, description: String, cover: String, createdAt: Long): CollectionSummary {
        jdbc.update(
            "INSERT INTO collection(id,owner_id,title,description,cover,created_at) VALUES(?,?,?,?,?,?)",
            id.value, ownerId.value, title.trim(), description.trim(), cover.trim(), createdAt
        )
        return list(ownerId, ownerId).first { it.id == id.value }
    }

    override fun delete(id: PublicId): Boolean =
        jdbc.update("DELETE FROM collection WHERE id=?", id.value) > 0

    override fun owns(collectionId: PublicId, ownerId: PublicId): Boolean =
        jdbc.queryForObject("SELECT count(*) FROM collection WHERE id=? AND owner_id=?", Long::class.java, collectionId.value, ownerId.value) == 1L

    override fun addItem(id: PublicId, collectionId: PublicId, targetType: String, targetId: PublicId, sortOrder: Int): CollectionItem {
        jdbc.update(
            "INSERT INTO collection_item(id,collection_id,target_type,target_id,sort_order,created_at) VALUES(?,?,?,?,?,?)",
            id.value, collectionId.value, targetType, targetId.value, sortOrder, System.currentTimeMillis()
        )
        return jdbc.query(
            "SELECT id item_id,target_type,target_id FROM collection_item WHERE id=?",
            { rs, _ -> mapItem(rs) }, id.value
        ).first()
    }

    override fun targetExists(targetType: String, targetId: PublicId): Boolean = when (targetType) {
        "article" -> (jdbc.queryForObject("SELECT count(*) FROM article ar JOIN content c ON c.id=ar.id WHERE ar.id=? AND c.status='ACTIVE'", Long::class.java, targetId.value) ?: 0L) > 0
        "post" -> (jdbc.queryForObject("SELECT count(*) FROM content c WHERE c.id=? AND c.status='ACTIVE'", Long::class.java, targetId.value) ?: 0L) > 0
        else -> false
    }

    override fun nextSortOrder(collectionId: PublicId): Int =
        jdbc.queryForObject("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM collection_item WHERE collection_id=?", Int::class.java, collectionId.value) ?: 0

    override fun removeItem(collectionId: PublicId, itemId: PublicId): Boolean =
        jdbc.update("DELETE FROM collection_item WHERE collection_id=? AND id=?", collectionId.value, itemId.value) > 0

    private fun mapSummary(rs: java.sql.ResultSet, viewerId: PublicId?): CollectionSummary {
        val ownerId = rs.getString("owner_id")
        return CollectionSummary(
            id = rs.getString("id"),
            title = rs.getString("title"),
            description = rs.getString("description"),
            cover = rs.getString("cover"),
            itemCount = rs.getLong("item_count"),
            owner = CollectionOwner(ownerId, rs.getString("username"), rs.getString("nickname"), rs.getString("avatar")),
            isOwner = viewerId?.value == ownerId
        )
    }

    private fun mapItem(rs: java.sql.ResultSet): CollectionItem {
        val type = rs.getString("target_type")
        val id = rs.getString("target_id")
        val itemId = rs.getString("item_id")
        val data = if (type == "article") article(id) else post(id)
        return CollectionItem(itemId, type, data)
    }

    private fun article(id: String): Map<String, Any?> =
        jdbc.query(
            """SELECT ar.id,ar.title,ar.category,ar.cover,ar.summary,ar.created_at,
                      a.id author_id,a.source_author_id,a.username,COALESCE(a.display_name,a.username) nickname,a.avatar,
                      (SELECT count(*) FROM content_interaction i WHERE i.content_id=ar.id AND i.type='LIKE') like_count
               FROM article ar JOIN content c ON c.id=ar.id
               JOIN author a ON a.id=c.author_id
               WHERE ar.id=? AND c.status='ACTIVE'""",
            { rs, _ -> mapOf(
                "id" to rs.getString("id"),
                "title" to rs.getString("title"),
                "category" to rs.getString("category"),
                "cover" to rs.getString("cover"),
                "summary" to rs.getString("summary"),
                "createdAt" to rs.getLong("created_at"),
                "likeCount" to rs.getLong("like_count"),
                "author" to mapOf(
                    "id" to rs.getString("author_id"),
                    "sourceAuthorId" to rs.getString("source_author_id"),
                    "username" to rs.getString("username"),
                    "nickname" to rs.getString("nickname"),
                    "avatar" to rs.getString("avatar")
                )
            ) }, id
        ).firstOrNull() ?: mapOf("id" to id, "title" to "内容不存在")

    private fun post(id: String): Map<String, Any?> =
        jdbc.query(
            """SELECT c.id,c.text,c.visibility,c.created_at,c.updated_at,
                      c.metadata::text metadata,
                      a.id author_id,a.source_author_id,a.username,COALESCE(a.display_name,a.username) nickname,a.avatar,
                      (SELECT count(*) FROM content_interaction i WHERE i.content_id=c.id AND i.type='LIKE') like_count,
                      (SELECT count(*) FROM content_comment cm WHERE cm.content_id=c.id) comment_count
               FROM content c JOIN author a ON a.id=c.author_id
               WHERE c.id=? AND c.status='ACTIVE'""",
            { rs, _ -> mapOf(
                "id" to rs.getString("id"),
                "content" to (rs.getString("text") ?: ""),
                "visibility" to rs.getString("visibility").lowercase(),
                "createdAt" to rs.getLong("created_at"),
                "updatedAt" to rs.getLong("updated_at"),
                "media" to emptyList<Any>(),
                "poll" to null,
                "liked" to false,
                "likeCount" to rs.getLong("like_count"),
                "bookmarked" to false,
                "commentCount" to rs.getLong("comment_count"),
                "shareCount" to 0,
                "reactions" to null,
                "myReaction" to null,
                "location" to null,
                "views" to 0,
                "edited" to (rs.getLong("updated_at") != rs.getLong("created_at")),
                "pinned" to false,
                "globalPinned" to false,
                "shared" to null,
                "sharedDeleted" to false,
                "locked" to null,
                "author" to mapOf(
                    "id" to rs.getString("author_id"),
                    "sourceAuthorId" to rs.getString("source_author_id"),
                    "username" to rs.getString("username"),
                    "nickname" to rs.getString("nickname"),
                    "avatar" to rs.getString("avatar")
                )
            ) }, id
        ).firstOrNull() ?: mapOf("id" to id, "content" to "内容不存在", "visibility" to "public")
}
