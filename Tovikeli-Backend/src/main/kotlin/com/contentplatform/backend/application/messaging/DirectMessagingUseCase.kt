package com.contentplatform.backend.application.messaging

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.messaging.DirectConversation
import com.contentplatform.backend.domain.messaging.DirectMessage
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.repository.DirectConversationRepository
import com.contentplatform.backend.repository.DirectMessageRepository
import com.contentplatform.backend.repository.UserRepository
import org.springframework.stereotype.Service

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
class DirectMessagingUseCase(
    private val conversationRepository: DirectConversationRepository,
    private val messageRepository: DirectMessageRepository,
    private val userRepository: UserRepository
) {
    fun getOrCreateConversation(callerId: PublicId, otherUserId: PublicId): DirectConversation {
        require(callerId != otherUserId) { "Cannot message yourself" }
        val other = userRepository.findById(otherUserId)
            ?: throw NoSuchElementException("User not found")
        require(other.status.name == "ACTIVE") { "User is not active" }

        return conversationRepository.findBetween(callerId, otherUserId)
            ?: runCatching {
                conversationRepository.save(DirectConversation.create(callerId, otherUserId))
            }.getOrElse {
                conversationRepository.findBetween(callerId, otherUserId)
                    ?: throw it
            }
    }

    fun getConversation(callerId: PublicId, conversationId: PublicId): DirectConversation? =
        conversationRepository.findById(conversationId)
            ?.takeIf { it.contains(callerId) }

    fun listConversations(callerId: PublicId, limit: Int): List<DirectConversationSummary> {
        val safeLimit = limit.coerceIn(1, 100)
        return conversationRepository.findForUser(callerId, safeLimit).map { conversation ->
            val other = userRepository.findById(conversation.otherUserId(callerId))
                ?: return@map null
            val messages = messageRepository.findByConversation(conversation.id, 1)
            val last = messages.lastOrNull()
            DirectConversationSummary(
                conversation = conversation,
                otherUser = other,
                lastMessage = last,
                unreadCount = messageRepository.countUnread(conversation.id, callerId)
            )
        }.filterNotNull()
    }

    fun listMessages(
        callerId: PublicId,
        conversationId: PublicId,
        limit: Int,
        beforeCreatedAt: Long?
    ): List<DirectMessage> {
        require(conversationRepository.findById(conversationId)?.contains(callerId) == true) {
            "Conversation access denied"
        }
        return messageRepository.findByConversation(conversationId, limit.coerceIn(1, 100), beforeCreatedAt)
    }

    fun sendMessage(callerId: PublicId, conversationId: PublicId, body: String): DirectMessage {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw NoSuchElementException("Conversation not found")
        require(conversation.contains(callerId)) { "Conversation access denied" }
        return messageRepository.save(DirectMessage.create(conversationId, callerId, body))
    }

    fun markRead(callerId: PublicId, conversationId: PublicId) {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw NoSuchElementException("Conversation not found")
        require(conversation.contains(callerId)) { "Conversation access denied" }
        messageRepository.markRead(conversationId, callerId, System.currentTimeMillis())
    }

    data class DirectConversationSummary(
        val conversation: DirectConversation,
        val otherUser: User,
        val lastMessage: DirectMessage?,
        val unreadCount: Long
    )
}
