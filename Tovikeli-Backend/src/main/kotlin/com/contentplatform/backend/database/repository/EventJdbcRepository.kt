package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.*
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.ResultSet
import java.util.UUID

class EventJdbcRepository(private val jdbc: JdbcTemplate) : EventRepository {
    override fun list(filter: String?, category: String?, viewerId: PublicId?, limit: Int): EventListResult {
        val now = System.currentTimeMillis()
        val where = mutableListOf<String>(); val args = mutableListOf<Any>()
        when (filter) {
            "mine" -> { if (viewerId == null) return EventListResult(emptyList(), categories(), 0); where += "EXISTS (SELECT 1 FROM community_event_signup s WHERE s.event_id=e.id AND s.user_id=?)"; args += viewerId.value }
            "past" -> { where += "e.end_at <= ?"; args += now }
            "upcoming" -> { where += "e.end_at > ?"; args += now }
            null, "" -> {}
            else -> throw IllegalArgumentException("invalid filter")
        }
        if (!category.isNullOrBlank()) { where += "e.category = ?"; args += category }
        val clause = if (where.isEmpty()) "" else "WHERE " + where.joinToString(" AND ")
        args += now; args += limit
        val rows = jdbc.query(
            """SELECT e.*,u.username,u.display_name,u.id organizer_user_id,a.avatar,
                      (SELECT count(*) FROM community_event_signup s WHERE s.event_id=e.id) signup_count
               FROM community_event e JOIN platform_user u ON u.id=e.organizer_id
               LEFT JOIN author a ON a.source_author_id=u.id
               $clause ORDER BY CASE WHEN e.start_at > ? THEN 0 ELSE 1 END,e.start_at ASC,e.id ASC LIMIT ?""",
            { rs, _ -> mapEvent(rs, viewerId) }, *args.toTypedArray()
        )
        val upcomingCount = jdbc.queryForObject("SELECT count(*) FROM community_event WHERE end_at > ?", Long::class.java, now) ?: 0L
        return EventListResult(rows, categories(), upcomingCount)
    }

    override fun findById(id: PublicId, viewerId: PublicId?): EventDetail? {
        val event = jdbc.query(
            """SELECT e.*,u.username,u.display_name,u.id organizer_user_id,a.avatar,
                      (SELECT count(*) FROM community_event_signup s WHERE s.event_id=e.id) signup_count
               FROM community_event e JOIN platform_user u ON u.id=e.organizer_id
               LEFT JOIN author a ON a.source_author_id=u.id WHERE e.id=?""",
            { rs, _ -> mapEvent(rs, viewerId, true) }, id.value
        ).firstOrNull() ?: return null
        val attendees = jdbc.query(
            """SELECT u.id,u.username,u.display_name,a.avatar FROM community_event_signup s
               JOIN platform_user u ON u.id=s.user_id LEFT JOIN author a ON a.source_author_id=u.id
               WHERE s.event_id=? ORDER BY s.created_at ASC,s.id ASC""",
            { rs, _ -> EventOrganizer(rs.getString("id"),rs.getString("username"),rs.getString("display_name") ?: rs.getString("username"),rs.getString("avatar")) }, id.value
        )
        return EventDetail(event, attendees)
    }

    override fun create(id: PublicId, organizerId: PublicId, title: String, cover: String, location: String, category: String,
                        startAt: Long, endAt: Long, capacity: Int, fee: Double, online: Boolean, description: String, createdAt: Long): CommunityEvent {
        jdbc.update("""INSERT INTO community_event(id,organizer_id,title,cover,location,category,start_at,end_at,capacity,fee,online,description,created_at)
                       VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)""",
            id.value,organizerId.value,title.trim(),cover.trim(),location.trim(),category,startAt,endAt,capacity,fee,online,description.trim(),createdAt)
        return findById(id, organizerId)!!.event
    }

    override fun delete(id: PublicId): Boolean = jdbc.update("DELETE FROM community_event WHERE id=?",id.value)>0
    override fun isOrganizer(id: PublicId,userId: PublicId): Boolean = (jdbc.queryForObject("SELECT count(*) FROM community_event WHERE id=? AND organizer_id=?",Long::class.java,id.value,userId.value) ?: 0L)==1L
    override fun isSigned(id: PublicId,userId: PublicId): Boolean = (jdbc.queryForObject("SELECT count(*) FROM community_event_signup WHERE event_id=? AND user_id=?",Long::class.java,id.value,userId.value) ?: 0L)==1L
    override fun signup(id: PublicId,userId: PublicId): Boolean =
        jdbc.update("INSERT INTO community_event_signup(id,event_id,user_id,created_at) VALUES(?,?,?,?) ON CONFLICT(event_id,user_id) DO NOTHING",UUID.randomUUID().toString(),id.value,userId.value,System.currentTimeMillis())>0
    override fun cancelSignup(id: PublicId,userId: PublicId): Boolean = jdbc.update("DELETE FROM community_event_signup WHERE event_id=? AND user_id=?",id.value,userId.value)>0
    override fun eventExists(id: PublicId): Boolean = (jdbc.queryForObject("SELECT count(*) FROM community_event WHERE id=?",Long::class.java,id.value) ?: 0L)==1L

    private fun categories(): List<String> = jdbc.query("SELECT DISTINCT category FROM community_event ORDER BY category",{rs,_ -> rs.getString(1)})
    private fun mapEvent(rs: ResultSet,viewerId: PublicId?,detail: Boolean=false): CommunityEvent {
        val start=rs.getLong("start_at"); val end=rs.getLong("end_at"); val now=System.currentTimeMillis(); val count=rs.getLong("signup_count"); val cap=rs.getInt("capacity")
        val signed=viewerId!=null && isSigned(PublicId(rs.getString("id")),viewerId)
        return CommunityEvent(rs.getString("id"),rs.getString("title"),rs.getString("cover"),rs.getString("location"),rs.getString("category"),
            start,end,cap,rs.getBigDecimal("fee").toDouble(),rs.getBoolean("online"),count,if(cap>0)(cap-count).coerceAtLeast(0).toInt() else null,
            when { end<=now->"ended"; start<=now->"ongoing"; else->"upcoming" },cap>0&&count>=cap,signed,
            EventOrganizer(rs.getString("organizer_user_id"),rs.getString("username"),rs.getString("display_name")?:rs.getString("username"),rs.getString("avatar")),
            viewerId?.value==rs.getString("organizer_user_id"),if(detail)rs.getString("description") else null)
    }
}