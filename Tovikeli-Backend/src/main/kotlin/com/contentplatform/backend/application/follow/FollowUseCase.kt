package com.contentplatform.backend.application.follow

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.follow.Follow
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.repository.FollowRepository
import com.contentplatform.backend.repository.UserRepository
import com.contentplatform.backend.application.notification.NotificationUseCase
import org.springframework.stereotype.Service

@Service
class FollowUseCase(
    private val followRepository: FollowRepository,
    private val userRepository: UserRepository,
    private val notifications: NotificationUseCase,
) {
    fun get(callerUserId: PublicId, targetUserId: PublicId): Boolean =
        followRepository.exists(callerUserId, targetUserId)

    fun follow(callerUserId: PublicId, targetUserId: PublicId): Follow {
        require(callerUserId != targetUserId) { "cannot follow yourself" }
        if (followRepository.exists(callerUserId, targetUserId)) {
            return followRepository.findFollowedUserIds(callerUserId, 100).firstOrNull { it == targetUserId }?.let { Follow(callerUserId, targetUserId, 0L) }
                ?: throw IllegalStateException("follow state changed concurrently")
        }
        val follow = followRepository.save(Follow(callerUserId, targetUserId, System.currentTimeMillis()))
        notifications.create(targetUserId, callerUserId, "follow", "user", callerUserId.value)
        return follow
    }

    fun unfollow(callerUserId: PublicId, targetUserId: PublicId): Boolean =
        followRepository.delete(callerUserId, targetUserId)

    fun listFollowing(userId: PublicId, limit: Int): List<User> =
        resolveUsers(followRepository.findFollowedUserIds(userId, normalizedLimit(limit)))

    fun listFollowers(userId: PublicId, limit: Int): List<User> =
        resolveUsers(followRepository.findFollowerUserIds(userId, normalizedLimit(limit)))

    private fun resolveUsers(ids: List<PublicId>): List<User> =
        ids.mapNotNull(userRepository::findById)

    private fun normalizedLimit(limit: Int): Int {
        require(limit in 1..100) { "limit must be between 1 and 100" }
        return limit
    }
}
