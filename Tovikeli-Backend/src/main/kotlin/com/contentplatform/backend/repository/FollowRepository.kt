package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.follow.Follow

interface FollowRepository {
    fun exists(followerUserId: PublicId, followedUserId: PublicId): Boolean
    fun save(follow: Follow): Follow
    fun delete(followerUserId: PublicId, followedUserId: PublicId): Boolean
    fun countFollowing(userId: PublicId): Long
    fun countFollowers(userId: PublicId): Long
    fun findFollowedUserIds(followerUserId: PublicId, limit: Int): List<PublicId>
    fun findFollowerUserIds(followedUserId: PublicId, limit: Int): List<PublicId>
}
