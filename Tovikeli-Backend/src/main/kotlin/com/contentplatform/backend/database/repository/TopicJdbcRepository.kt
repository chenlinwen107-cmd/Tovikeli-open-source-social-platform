package com.contentplatform.backend.database.repository

import com.contentplatform.backend.database.entity.TopicEntity
import com.contentplatform.backend.database.mapper.TopicMapper
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.topic.Topic
import com.contentplatform.backend.repository.TopicRepository
import com.contentplatform.backend.repository.TopicSummary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

class TopicJdbcRepository(private val jdbcTemplate: JdbcTemplate) : TopicRepository {
    private val rowMapper = RowMapper { rs, _ -> TopicEntity(
        id = rs.getString("id"), name = rs.getString("name"),
        normalizedName = rs.getString("normalized_name"),
        description = rs.getString("description"),
        createdAt = rs.getLong("created_at"), updatedAt = rs.getLong("updated_at")
    ) }
    private val select = "SELECT id, name, normalized_name, description, created_at, updated_at FROM topic"

    override fun findById(id: PublicId): Topic? = jdbcTemplate.query("$select WHERE id = ?", rowMapper, id.value).firstOrNull()?.let(TopicMapper::toDomain)
    override fun findByName(name: String): Topic? = jdbcTemplate.query("$select WHERE normalized_name = ?", rowMapper, Topic.normalize(name)).firstOrNull()?.let(TopicMapper::toDomain)

    override fun ensure(name: String, description: String?): Topic {
        val candidate = Topic.create(name, description)
        jdbcTemplate.update(
            """INSERT INTO topic (id, name, normalized_name, description, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (normalized_name) DO NOTHING""".trimIndent(),
            candidate.id.value, candidate.name, candidate.normalizedName, candidate.description,
            candidate.createdAt, candidate.updatedAt
        )
        return findByName(candidate.name) ?: candidate
    }

    private fun summarySql(order: String, where: String = "", extra: List<Any?> = emptyList(), limit: Int = 20): List<TopicSummary> {
        require(limit in 0..100)
        if (limit == 0) return emptyList()
        val sql = """
            SELECT t.id, t.name, t.normalized_name, t.description, t.created_at, t.updated_at,
                   COUNT(DISTINCT CASE WHEN c.status = 'ACTIVE' AND c.visibility = 'PUBLIC' THEN c.id END) AS post_count,
                   COUNT(DISTINCT tf.user_id) AS follower_count,
                   (COUNT(DISTINCT CASE WHEN c.status = 'ACTIVE' AND c.visibility = 'PUBLIC' AND c.created_at >= ? THEN c.id END) * 5
                    + COUNT(DISTINCT tf.user_id) * 2) AS hot
            FROM topic t
            LEFT JOIN content c ON c.topic_id = t.id
            LEFT JOIN topic_follow tf ON tf.topic_id = t.id
            $where
            GROUP BY t.id
            ORDER BY $order
            LIMIT ?
        """.trimIndent()
        val args = mutableListOf<Any?>(System.currentTimeMillis() - 86_400_000L)
        args.addAll(extra); args.add(limit)
        return jdbcTemplate.query(sql, rowMapper, *args.toTypedArray()).map { e ->
            val counts = jdbcTemplate.queryForObject(
                """SELECT COUNT(DISTINCT CASE WHEN c.status = 'ACTIVE' AND c.visibility = 'PUBLIC' THEN c.id END),
                          COUNT(DISTINCT tf.user_id)
                   FROM topic t LEFT JOIN content c ON c.topic_id=t.id LEFT JOIN topic_follow tf ON tf.topic_id=t.id
                   WHERE t.id = ?""".trimIndent(),
                { rs, _ -> longArrayOf(rs.getLong(1), rs.getLong(2)) }, e.id
            ) ?: longArrayOf(0,0)
            TopicSummary(TopicMapper.toDomain(e), counts[0], counts[1], 0)
        }
    }

    override fun search(name: String, limit: Int): List<TopicSummary> {
        require(limit in 1..100)
        val pattern = "%" + name.trim() + "%"
        return jdbcTemplate.query(
            """SELECT t.id,t.name,t.normalized_name,t.description,t.created_at,t.updated_at,
                      COUNT(DISTINCT CASE WHEN c.status='ACTIVE' AND c.visibility='PUBLIC' THEN c.id END) post_count,
                      COUNT(DISTINCT tf.user_id) follower_count,0::bigint hot
               FROM topic t
               LEFT JOIN content c ON c.topic_id=t.id
               LEFT JOIN topic_follow tf ON tf.topic_id=t.id
               WHERE t.name ILIKE ? OR t.normalized_name ILIKE ?
               GROUP BY t.id ORDER BY t.name ASC LIMIT ?""".trimIndent(),
            { rs,_ -> TopicSummary(
                Topic(PublicId(rs.getString("id")),rs.getString("name"),rs.getString("normalized_name"),rs.getString("description"),rs.getLong("created_at"),rs.getLong("updated_at")),
                rs.getLong("post_count"),rs.getLong("follower_count"),rs.getLong("hot")
            )},
            pattern, pattern, limit
        )
    }

    override fun listHot(limit: Int): List<TopicSummary> {
        require(limit in 0..100); if (limit == 0) return emptyList()
        val cutoff = System.currentTimeMillis() - 86_400_000L
        return jdbcTemplate.query(
            """SELECT t.id,t.name,t.normalized_name,t.description,t.created_at,t.updated_at,
                      COUNT(DISTINCT CASE WHEN c.status='ACTIVE' AND c.visibility='PUBLIC' THEN c.id END) post_count,
                      COUNT(DISTINCT tf.user_id) follower_count,
                      (COUNT(DISTINCT CASE WHEN c.status='ACTIVE' AND c.visibility='PUBLIC' AND c.created_at >= ? THEN c.id END) * 5
                       + COUNT(DISTINCT tf.user_id) * 2) hot
               FROM topic t LEFT JOIN content c ON c.topic_id=t.id LEFT JOIN topic_follow tf ON tf.topic_id=t.id
               GROUP BY t.id ORDER BY hot DESC, t.name ASC LIMIT ?""".trimIndent(),
            { rs,_ -> TopicSummary(
                Topic(rs.getString("id").let(::PublicId),rs.getString("name"),rs.getString("normalized_name"),rs.getString("description"),rs.getLong("created_at"),rs.getLong("updated_at")),
                rs.getLong("post_count"),rs.getLong("follower_count"),rs.getLong("hot"))
            }, cutoff, limit)
    }

    override fun listFollowing(userId: PublicId, limit: Int): List<TopicSummary> = jdbcTemplate.query(
        """SELECT t.id,t.name,t.normalized_name,t.description,t.created_at,t.updated_at,
                  COUNT(DISTINCT CASE WHEN c.status='ACTIVE' AND c.visibility='PUBLIC' THEN c.id END) post_count,
                  COUNT(DISTINCT tf2.user_id) follower_count,
                  0::bigint hot
           FROM topic t JOIN topic_follow tf ON tf.topic_id=t.id
           LEFT JOIN content c ON c.topic_id=t.id LEFT JOIN topic_follow tf2 ON tf2.topic_id=t.id
           WHERE tf.user_id=? GROUP BY t.id ORDER BY tf.created_at DESC LIMIT ?""".trimIndent(),
        { rs,_ -> TopicSummary(Topic(PublicId(rs.getString("id")),rs.getString("name"),rs.getString("normalized_name"),rs.getString("description"),rs.getLong("created_at"),rs.getLong("updated_at")),rs.getLong("post_count"),rs.getLong("follower_count"),rs.getLong("hot")) },
        userId.value, limit
    )

    override fun countPosts(topicId: PublicId): Long = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM content WHERE topic_id=? AND status='ACTIVE' AND visibility='PUBLIC'",Long::class.java,topicId.value) ?: 0L
    override fun countFollowers(topicId: PublicId): Long = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM topic_follow WHERE topic_id=?",Long::class.java,topicId.value) ?: 0L
    override fun isFollowing(topicId: PublicId,userId: PublicId): Boolean = jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM topic_follow WHERE topic_id=? AND user_id=?)",Boolean::class.java,topicId.value,userId.value) ?: false
    override fun follow(topicId: PublicId,userId: PublicId): Boolean = jdbcTemplate.update("INSERT INTO topic_follow(topic_id,user_id,created_at) VALUES(?,?,?) ON CONFLICT DO NOTHING",topicId.value,userId.value,System.currentTimeMillis()) > 0
    override fun unfollow(topicId: PublicId,userId: PublicId): Boolean = jdbcTemplate.update("DELETE FROM topic_follow WHERE topic_id=? AND user_id=?",topicId.value,userId.value) > 0
}
