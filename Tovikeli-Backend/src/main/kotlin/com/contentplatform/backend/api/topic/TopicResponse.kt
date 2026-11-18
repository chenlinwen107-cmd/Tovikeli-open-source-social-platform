package com.contentplatform.backend.api.topic

import com.contentplatform.backend.repository.TopicSummary

data class TopicResponse(
    val id: String,
    val name: String,
    val description: String?,
    val postCount: Long,
    val followers: Long,
    val hot: Long,
    val isFollowing: Boolean = false
) {
    companion object {
        fun from(s: TopicSummary, following: Boolean = false) = TopicResponse(
            id = s.topic.id.value, name = s.topic.name, description = s.topic.description,
            postCount = s.postCount, followers = s.followerCount, hot = s.hot, isFollowing = following
        )
    }
}
