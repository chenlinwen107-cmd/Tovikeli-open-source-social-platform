package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.messaging.DirectConversation

interface DirectConversationRepository {
    fun findById(id: PublicId): DirectConversation?
    fun findBetween(user1Id: PublicId, user2Id: PublicId): DirectConversation?
    fun save(conversation: DirectConversation): DirectConversation
    fun findForUser(userId: PublicId, limit: Int): List<DirectConversation>
}
