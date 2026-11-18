package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.*
import org.springframework.jdbc.core.JdbcTemplate

class NotificationJdbcRepository(private val jdbc:JdbcTemplate):NotificationRepository {
    override fun list(recipientId:PublicId,limit:Int):List<NotificationItem> = jdbc.query(
        """SELECT n.id,n.type,n.target_type,n.target_id,n.preview,n.read,n.created_at,
                  u.id actor_id,u.username actor_username,COALESCE(u.display_name,u.username) actor_nickname,u.avatar actor_avatar
           FROM notification n LEFT JOIN platform_user u ON u.id=n.actor_id
           WHERE n.recipient_id=? ORDER BY n.created_at DESC,n.id DESC LIMIT ?""",
        {rs,_ -> NotificationItem(rs.getLong("id"),rs.getString("type"),rs.getString("target_type"),rs.getString("target_id"),rs.getString("preview"),rs.getBoolean("read"),rs.getLong("created_at"),
            rs.getString("actor_id")?.let{NotificationActor(it,rs.getString("actor_username"),rs.getString("actor_nickname"),rs.getString("actor_avatar"))})},
        recipientId.value,limit.coerceIn(1,100)
    )
    override fun create(recipientId:PublicId,actorId:PublicId?,type:String,targetType:String,targetId:String,preview:String):NotificationItem {
        val id=jdbc.queryForObject("""INSERT INTO notification(recipient_id,actor_id,type,target_type,target_id,preview,read,created_at) VALUES(?,?,?,?,?,?,FALSE,?) RETURNING id""",Long::class.java,recipientId.value,actorId?.value,type,targetType,targetId,preview,System.currentTimeMillis())!!
        return listById(recipientId,id)
    }
    private fun listById(user:PublicId,id:Long)=list(user,100).first{it.id==id}
    override fun markRead(recipientId:PublicId,id:Long)=jdbc.update("UPDATE notification SET read=TRUE WHERE id=? AND recipient_id=?",id,recipientId.value)>0
    override fun markAllRead(recipientId:PublicId)=jdbc.update("UPDATE notification SET read=TRUE WHERE recipient_id=? AND read=FALSE",recipientId.value)
}
