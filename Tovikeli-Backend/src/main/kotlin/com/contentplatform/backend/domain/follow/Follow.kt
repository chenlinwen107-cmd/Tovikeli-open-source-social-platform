package com.contentplatform.backend.domain.follow

import com.contentplatform.backend.domain.common.PublicId

data class Follow(
    val followerUserId: PublicId,
    val followedUserId: PublicId,
    val createdAt: Long
)
