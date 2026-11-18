package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.*
import org.springframework.jdbc.core.JdbcTemplate

class CircleJdbcRepository(private val jdbc: JdbcTemplate) : CircleRepository {
    override fun list(category: String?, userId: PublicId?): List<CircleSummary> {
        val where = if (category.isNullOrBlank()) "" else " WHERE c.category = ?"
        val joined = if (userId != null) "EXISTS (SELECT 1 FROM circle_member cm2 WHERE cm2.circle_id = c.id AND cm2.user_id = ?)" else "FALSE"
        val args = buildList<Any> { if (!category.isNullOrBlank()) add(category!!); if (userId != null) add(userId.value) }
        val sql = """SELECT c.id,c.slug,c.name,c.description,c.category,c.icon,c.color,c.owner_id,
            (SELECT count(*) FROM circle_member cm WHERE cm.circle_id=c.id) member_count,
            (SELECT count(*) FROM circle_post cp JOIN content ct ON ct.id=cp.content_id WHERE cp.circle_id=c.id AND ct.status='ACTIVE') post_count,
            """ + joined + """ joined FROM circle c""" + where + """ ORDER BY member_count DESC,post_count DESC,c.id DESC"""
        return jdbc.query(sql, { rs, _ -> mapCircle(rs) }, *args.toTypedArray())
    }
    override fun findBySlug(slug: String, userId: PublicId?) = list(null, userId).firstOrNull { it.slug == slug }
    override fun findById(id: Long, userId: PublicId?): CircleSummary? {
        val joined = if (userId != null) "EXISTS (SELECT 1 FROM circle_member cm2 WHERE cm2.circle_id=c.id AND cm2.user_id = ?)" else "FALSE"
        val args = if (userId != null) arrayOf<Any>(userId.value, id) else arrayOf<Any>(id)
        val sql = """SELECT c.id,c.slug,c.name,c.description,c.category,c.icon,c.color,c.owner_id,
            (SELECT count(*) FROM circle_member cm WHERE cm.circle_id=c.id) member_count,
            (SELECT count(*) FROM circle_post cp JOIN content ct ON ct.id=cp.content_id WHERE cp.circle_id=c.id AND ct.status='ACTIVE') post_count,
            """ + joined + """ joined FROM circle c WHERE c.id=?"""
        return jdbc.query(sql, { rs, _ -> mapCircle(rs) }, *args).firstOrNull()
    }
    override fun create(name: String, slug: String, description: String, category: String, icon: String, color: String, ownerId: PublicId, now: Long): CircleSummary {
        val id = jdbc.queryForObject("INSERT INTO circle(slug,name,description,category,icon,color,owner_id,created_at) VALUES(?,?,?,?,?,?,?,?) RETURNING id", Long::class.java, slug,name,description,category,icon,color,ownerId.value,now) ?: error("circle insert returned no id")
        jdbc.update("INSERT INTO circle_member(circle_id,user_id,created_at) VALUES(?,?,?)", id, ownerId.value, now)
        return findById(id, ownerId) ?: error("created circle not found")
    }
    override fun toggleMember(circleId: Long, userId: PublicId): Boolean {
        val count = jdbc.queryForObject("SELECT count(*) FROM circle_member WHERE circle_id=? AND user_id=?", Long::class.java,circleId,userId.value) ?: 0L
        if (count > 0) { jdbc.update("DELETE FROM circle_member WHERE circle_id=? AND user_id=?",circleId,userId.value); return false }
        jdbc.update("INSERT INTO circle_member(circle_id,user_id,created_at) VALUES(?,?,?) ON CONFLICT DO NOTHING",circleId,userId.value,System.currentTimeMillis()); return true
    }
    override fun isMember(circleId: Long,userId: PublicId) = (jdbc.queryForObject("SELECT count(*) FROM circle_member WHERE circle_id=? AND user_id=?",Long::class.java,circleId,userId.value) ?: 0L) > 0
    override fun members(circleId: Long,limit: Int)=jdbc.query("""SELECT u.id,u.username,COALESCE(u.display_name,u.username) nickname,u.avatar FROM circle_member cm JOIN platform_user u ON u.id=cm.user_id WHERE cm.circle_id=? AND u.status='ACTIVE' ORDER BY cm.created_at ASC LIMIT ?""",{rs,_->CircleMember(rs.getString("id"),rs.getString("username"),rs.getString("nickname"),rs.getString("avatar"))},circleId,limit.coerceIn(1,100))
    override fun addPost(circleId: Long,contentId: PublicId,now: Long){jdbc.update("INSERT INTO circle_post(circle_id,content_id,created_at) VALUES(?,?,?)",circleId,contentId.value,now)}
    override fun postIds(circleId: Long,limit: Int)=jdbc.query("SELECT cp.content_id FROM circle_post cp JOIN content c ON c.id=cp.content_id WHERE cp.circle_id=? AND c.status='ACTIVE' ORDER BY cp.created_at DESC,cp.content_id DESC LIMIT ?",{rs,_->rs.getString("content_id")},circleId,limit.coerceIn(1,100))
    override fun chat(circleId: Long,limit: Int)=jdbc.query("""SELECT m.id,m.content,m.created_at,u.id user_id,u.username,COALESCE(u.display_name,u.username) nickname,u.avatar FROM circle_chat_message m JOIN platform_user u ON u.id=m.user_id WHERE m.circle_id=? ORDER BY m.id DESC LIMIT ?""",{rs,_->CircleChatMessage(rs.getLong("id"),rs.getString("content"),rs.getLong("created_at"),CircleMember(rs.getString("user_id"),rs.getString("username"),rs.getString("nickname"),rs.getString("avatar")))},circleId,limit.coerceIn(1,200)).asReversed()
    override fun addChat(circleId: Long,userId: PublicId,content: String,now: Long): CircleChatMessage {
        val id=jdbc.queryForObject("INSERT INTO circle_chat_message(circle_id,user_id,content,created_at) VALUES(?,?,?,?) RETURNING id",Long::class.java,circleId,userId.value,content,now) ?: error("chat insert returned no id")
        return chat(circleId,1).first{it.id==id}
    }
    private fun mapCircle(rs: java.sql.ResultSet)=CircleSummary(rs.getLong("id"),rs.getString("slug"),rs.getString("name"),rs.getString("description"),rs.getString("category"),rs.getString("icon"),rs.getString("color"),rs.getString("owner_id"),rs.getLong("member_count"),rs.getLong("post_count"),rs.getBoolean("joined"))
}