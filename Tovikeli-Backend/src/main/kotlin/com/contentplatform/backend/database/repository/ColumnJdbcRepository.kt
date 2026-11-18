package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.*
import org.springframework.jdbc.core.JdbcTemplate

class ColumnJdbcRepository(private val jdbc: JdbcTemplate) : ColumnRepository {
    override fun list(category: String?, sort: String, userId: PublicId?, limit: Int, offset: Int): List<ColumnArticle> {
        val where = if (category == null) "" else "WHERE ar.category = ?"
        val args = mutableListOf<Any>()
        if (category != null) args += category
        args += limit
        args += offset
        val order = if (sort == "hot") {
            "((SELECT count(*) FROM content_interaction i WHERE i.content_id=ar.id AND i.type='LIKE') * 3 + (SELECT count(*) FROM content_comment cm WHERE cm.content_id=ar.id) * 2 + ar.views) DESC, ar.created_at DESC, ar.id DESC"
        } else {
            "ar.created_at DESC, ar.id DESC"
        }
        return jdbc.query(
            """SELECT ar.id,ar.title,ar.category,ar.cover,ar.summary,ar.featured,ar.views,ar.created_at,
                      c.text,a.source_author_id author_id,a.username,COALESCE(a.display_name,a.username) nickname,a.avatar,
                      (SELECT count(*) FROM content_interaction i WHERE i.content_id=ar.id AND i.type='LIKE') like_count,
                      (SELECT count(*) FROM content_comment cm WHERE cm.content_id=ar.id) comment_count
               FROM article ar JOIN content c ON c.id=ar.id JOIN author a ON a.id=c.author_id
               $where ORDER BY $order LIMIT ? OFFSET ?""",
            { rs, _ -> mapArticle(rs, userId, false) }, *args.toTypedArray()
        )
    }

    override fun findById(id: PublicId, userId: PublicId?): ColumnArticle? =
        jdbc.query(
            """SELECT ar.id,ar.title,ar.category,ar.cover,ar.summary,ar.featured,ar.views,ar.created_at,
                      c.text,a.source_author_id author_id,a.username,COALESCE(a.display_name,a.username) nickname,a.avatar,
                      (SELECT count(*) FROM content_interaction i WHERE i.content_id=ar.id AND i.type='LIKE') like_count,
                      (SELECT count(*) FROM content_comment cm WHERE cm.content_id=ar.id) comment_count
               FROM article ar JOIN content c ON c.id=ar.id JOIN author a ON a.id=c.author_id
               WHERE ar.id=? AND c.status='ACTIVE'""",
            { rs, _ -> mapArticle(rs, userId, true) }, id.value
        ).firstOrNull()

    override fun listRelated(id: PublicId, category: String, userId: PublicId?, limit: Int): List<ColumnArticle> =
        jdbc.query(
            """SELECT ar.id,ar.title,ar.category,ar.cover,ar.summary,ar.featured,ar.views,ar.created_at,
                      c.text,a.source_author_id author_id,a.username,COALESCE(a.display_name,a.username) nickname,a.avatar,
                      (SELECT count(*) FROM content_interaction i WHERE i.content_id=ar.id AND i.type='LIKE') like_count,
                      (SELECT count(*) FROM content_comment cm WHERE cm.content_id=ar.id) comment_count
               FROM article ar JOIN content c ON c.id=ar.id JOIN author a ON a.id=c.author_id
               WHERE ar.category=? AND ar.id<>? AND c.status='ACTIVE'
               ORDER BY ar.created_at DESC,ar.id DESC LIMIT ?""",
            { rs, _ -> mapArticle(rs, userId, false) }, category, id.value, limit
        )

    override fun listTrending(userId: PublicId?, limit: Int): List<ColumnArticle> =
        jdbc.query(
            """SELECT ar.id,ar.title,ar.category,ar.cover,ar.summary,ar.featured,ar.views,ar.created_at,
                      c.text,a.source_author_id author_id,a.username,COALESCE(a.display_name,a.username) nickname,a.avatar,
                      (SELECT count(*) FROM content_interaction i WHERE i.content_id=ar.id AND i.type='LIKE') like_count,
                      (SELECT count(*) FROM content_comment cm WHERE cm.content_id=ar.id) comment_count
               FROM article ar JOIN content c ON c.id=ar.id JOIN author a ON a.id=c.author_id
               WHERE c.status='ACTIVE'
               ORDER BY ((SELECT count(*) FROM content_interaction i WHERE i.content_id=ar.id AND i.type='LIKE') * 3 +
                         (SELECT count(*) FROM content_comment cm WHERE cm.content_id=ar.id) * 2 + ar.views) DESC,
                        ar.created_at DESC, ar.id DESC LIMIT ?""",
            { rs, _ -> mapArticle(rs, userId, false) }, limit
        )

    override fun categoryCounts(): List<Pair<String, Long>> =
        jdbc.query("SELECT category,count(*) count FROM article ar JOIN content c ON c.id=ar.id WHERE c.status='ACTIVE' GROUP BY category ORDER BY category",
            { rs, _ -> rs.getString("category") to rs.getLong("count") })

    override fun create(id: PublicId, title: String, category: String, cover: String, summary: String, createdAt: Long): ColumnArticle {
        jdbc.update(
            "INSERT INTO article(id,title,category,cover,summary,created_at) VALUES(?,?,?,?,?,?)",
            id.value, title.trim(), category, cover.trim(), summary.trim(), createdAt
        )
        return findById(id, null)!!
    }

    override fun delete(id: PublicId): Boolean =
        jdbc.update("DELETE FROM article WHERE id=?", id.value) > 0

    private fun mapArticle(rs: java.sql.ResultSet, userId: PublicId?, includeContent: Boolean): ColumnArticle {
        val id = rs.getString("id")
        val text = rs.getString("text") ?: ""
        val liked = userId != null && (jdbc.queryForObject(
            "SELECT count(*) FROM content_interaction WHERE content_id=? AND user_id=? AND type='LIKE'",
            Long::class.java, id, userId.value
        ) ?: 0) > 0
        val readMins = ((text.length + 699) / 700).coerceAtLeast(1)
        return ColumnArticle(
            id = id,
            title = rs.getString("title"),
            summary = rs.getString("summary"),
            cover = rs.getString("cover"),
            category = rs.getString("category"),
            featured = rs.getBoolean("featured"),
            views = rs.getLong("views"),
            likeCount = rs.getLong("like_count"),
            commentCount = rs.getLong("comment_count"),
            readMins = readMins,
            createdAt = rs.getLong("created_at"),
            author = ColumnAuthor(rs.getString("author_id"), rs.getString("username"), rs.getString("nickname"), rs.getString("avatar")),
            liked = liked,
            content = if (includeContent) text else null
        )
    }
}
