package com.contentplatform.backend.domain.messaging

import com.contentplatform.backend.domain.common.PublicId
import java.util.UUID

data class DirectMessage(
    val id: PublicId,
    val conversationId: PublicId,
    val senderUserId: PublicId,
    val body: String,
    val createdAt: Long,
    val readAt: Long?
) {
    init {
        require(body.isNotBlank())
        require(body.length <= MAX_BODY_LENGTH)
    }

    companion object {
        const val MAX_BODY_LENGTH = 5000
        fun create(conversationId: PublicId, senderUserId: PublicId, body: String): DirectMessage {
            val normalized = body.trim()
            require(normalized.isNotBlank())
            require(normalized.length <= MAX_BODY_LENGTH)
            return DirectMessage(
                id = PublicId("dm_" + UUID.randomUUID().toString()),
                conversationId = conversationId,
                senderUserId = senderUserId,
                body = normalized,
                createdAt = System.currentTimeMillis(),
                readAt = null
            )
        }
    }
}
