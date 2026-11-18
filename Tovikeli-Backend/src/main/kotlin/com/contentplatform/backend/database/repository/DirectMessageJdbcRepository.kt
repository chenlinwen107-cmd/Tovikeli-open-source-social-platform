package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.messaging.DirectMessage
import com.contentplatform.backend.repository.DirectMessageRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

class DirectMessageJdbcRepository(private val jdbcTemplate: JdbcTemplate) : DirectMessageRepository {
    private val rowMapper = RowMapper { rs, _ ->
        DirectMessage(
            PublicId(rs.getString("id")),
            PublicId(rs.getString("conversation_id")),
            PublicId(rs.getString("sender_user_id")),
            rs.getString("body"),
            rs.getLong("created_at"),
            rs.getLong("read_at").let { if (rs.wasNull()) null else it }
        )
    }

    override fun findByConversation(conversationId: PublicId, limit: Int, beforeCreatedAt: Long?): List<DirectMessage> {
        require(limit >= 0)
        if (limit == 0) return emptyList()
        val sql = if (beforeCreatedAt == null)
            "SELECT id,conversation_id,sender_user_id,body,created_at,read_at FROM direct_message WHERE conversation_id=? ORDER BY created_at DESC,id DESC LIMIT ?"
        else
            "SELECT id,conversation_id,sender_user_id,body,created_at,read_at FROM direct_message WHERE conversation_id=? AND created_at<? ORDER BY created_at DESC,id DESC LIMIT ?"
        val args: Array<Any?> = if (beforeCreatedAt == null) arrayOf(conversationId.value, limit) else arrayOf(conversationId.value, beforeCreatedAt, limit)
        return jdbcTemplate.query(sql,rowMapper,*args).asReversed()
    }

    override fun save(message: DirectMessage): DirectMessage {
        jdbcTemplate.update(
            "INSERT INTO direct_message (id,conversation_id,sender_user_id,body,created_at,read_at) VALUES (?,?,?,?,?,?)",
            message.id.value,message.conversationId.value,message.senderUserId.value,message.body,message.createdAt,message.readAt
        )
        jdbcTemplate.update("UPDATE direct_conversation SET updated_at=? WHERE id=?",message.createdAt,message.conversationId.value)
        return message
    }

    override fun markRead(conversationId: PublicId, readerUserId: PublicId, readAt: Long): Int =
        jdbcTemplate.update(
            "UPDATE direct_message SET read_at=? WHERE conversation_id=? AND sender_user_id<>? AND read_at IS NULL",
            readAt,conversationId.value,readerUserId.value
        )

    override fun countUnread(conversationId: PublicId, readerUserId: PublicId): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM direct_message WHERE conversation_id=? AND sender_user_id<>? AND read_at IS NULL",
            Long::class.java,conversationId.value,readerUserId.value
        ) ?: 0L
}
