package com.contentplatform.backend.database.repository

import com.contentplatform.backend.repository.BrowseHistoryItem
import com.contentplatform.backend.repository.BrowseHistoryRepository
import com.contentplatform.backend.domain.common.PublicId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class BrowseHistoryJdbcRepository(private val jdbc: JdbcTemplate) : BrowseHistoryRepository {
    override fun upsert(userId: PublicId,type:String,targetId:String,title:String,typeLabel:String,link:String,icon:String,authorNickname:String,cover:String,solved:Boolean,viewedAt:Long) {
        jdbc.update(
            """INSERT INTO browse_history(user_id,target_type,target_id,title,type_label,link,icon,author_nickname,cover,solved,viewed_at)
               VALUES(?,?,?,?,?,?,?,?,?,?,?)
               ON CONFLICT(user_id,target_type,target_id) DO UPDATE SET
                 title=EXCLUDED.title,type_label=EXCLUDED.type_label,link=EXCLUDED.link,icon=EXCLUDED.icon,
                 author_nickname=EXCLUDED.author_nickname,cover=EXCLUDED.cover,solved=EXCLUDED.solved,viewed_at=EXCLUDED.viewed_at""",
            userId.value,type,targetId,title,typeLabel,link,icon,authorNickname,cover,solved,viewedAt)
    }
    override fun list(userId: PublicId,limit:Int):List<BrowseHistoryItem> = jdbc.query(
        """SELECT id,target_type,target_id,title,type_label,link,icon,author_nickname,cover,solved,viewed_at
           FROM browse_history WHERE user_id=? ORDER BY viewed_at DESC,id DESC LIMIT ?""",
        {rs,_->BrowseHistoryItem(rs.getLong("id"),rs.getString("target_type"),rs.getString("target_id"),rs.getString("title"),rs.getString("type_label"),rs.getString("link"),rs.getString("icon"),rs.getString("author_nickname"),rs.getString("cover"),rs.getBoolean("solved"),rs.getLong("viewed_at"))},
        userId.value,limit)
    override fun delete(userId:PublicId,type:String,targetId:String)=jdbc.update("DELETE FROM browse_history WHERE user_id=? AND target_type=? AND target_id=?",userId.value,type,targetId)>0
    override fun clear(userId:PublicId)=jdbc.update("DELETE FROM browse_history WHERE user_id=?",userId.value)
}
