package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.messaging.DirectConversation
import com.contentplatform.backend.repository.DirectConversationRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

class DirectConversationJdbcRepository(private val jdbcTemplate: JdbcTemplate) : DirectConversationRepository {
    private val rowMapper = RowMapper { rs, _ ->
        DirectConversation(
            PublicId(rs.getString("id")),
            PublicId(rs.getString("user_a_id")),
            PublicId(rs.getString("user_b_id")),
            rs.getLong("created_at"),
            rs.getLong("updated_at")
        )
    }

    override fun findById(id: PublicId): DirectConversation? =
        jdbcTemplate.query(
            "SELECT id,user_a_id,user_b_id,created_at,updated_at FROM direct_conversation WHERE id=?",
            rowMapper, id.value
        ).firstOrNull()

    override fun findBetween(user1Id: PublicId, user2Id: PublicId): DirectConversation? {
        val (a,b) = if (user1Id.value < user2Id.value) user1Id to user2Id else user2Id to user1Id
        return jdbcTemplate.query(
            "SELECT id,user_a_id,user_b_id,created_at,updated_at FROM direct_conversation WHERE user_a_id=? AND user_b_id=?",
            rowMapper, a.value, b.value
        ).firstOrNull()
    }

    override fun save(conversation: DirectConversation): DirectConversation {
        jdbcTemplate.update(
            "INSERT INTO direct_conversation (id,user_a_id,user_b_id,created_at,updated_at) VALUES (?,?,?,?,?)",
            conversation.id.value,conversation.userAId.value,conversation.userBId.value,conversation.createdAt,conversation.updatedAt
        )
        return conversation
    }

    override fun findForUser(userId: PublicId, limit: Int): List<DirectConversation> {
        require(limit >= 0)
        if (limit == 0) return emptyList()
        return jdbcTemplate.query(
            "SELECT id,user_a_id,user_b_id,created_at,updated_at FROM direct_conversation WHERE user_a_id=? OR user_b_id=? ORDER BY updated_at DESC,id DESC LIMIT ?",
            rowMapper,userId.value,userId.value,limit
        )
    }
}
