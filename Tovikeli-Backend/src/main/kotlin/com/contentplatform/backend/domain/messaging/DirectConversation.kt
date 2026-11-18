package com.contentplatform.backend.domain.messaging

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId

data class DirectConversation(
    val id: PublicId,
    val userAId: PublicId,
    val userBId: PublicId,
    val createdAt: Long,
    val updatedAt: Long
) {
    init {
        require(userAId.value < userBId.value)
        require(userAId != userBId)
    }

    fun contains(userId: PublicId): Boolean =
        userAId == userId || userBId == userId

    fun otherUserId(userId: PublicId): PublicId {
        require(contains(userId))
        return if (userAId == userId) userBId else userAId
    }

    companion object {
        fun create(user1Id: PublicId, user2Id: PublicId): DirectConversation {
            require(user1Id != user2Id)
            val (a, b) = if (user1Id.value < user2Id.value) user1Id to user2Id else user2Id to user1Id
            val now = System.currentTimeMillis()
            return DirectConversation(
                id = PublicId("dc_" + IdentityHash.derive("${a.value}:${b.value}")),
                userAId = a,
                userBId = b,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
