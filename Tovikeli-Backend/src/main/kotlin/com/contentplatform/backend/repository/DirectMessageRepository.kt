package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.messaging.DirectMessage

interface DirectMessageRepository {
    fun findByConversation(conversationId: PublicId, limit: Int, beforeCreatedAt: Long? = null): List<DirectMessage>
    fun save(message: DirectMessage): DirectMessage
    fun markRead(conversationId: PublicId, readerUserId: PublicId, readAt: Long): Int
    fun countUnread(conversationId: PublicId, readerUserId: PublicId): Long
}
