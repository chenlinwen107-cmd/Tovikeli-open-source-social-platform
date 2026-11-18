package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.topic.Topic

data class TopicSummary(
    val topic: Topic,
    val postCount: Long,
    val followerCount: Long,
    val hot: Long
)

interface TopicRepository {
    fun findById(id: PublicId): Topic?
    fun findByName(name: String): Topic?
    fun ensure(name: String, description: String? = null): Topic
    fun search(name: String, limit: Int): List<TopicSummary>
    fun listHot(limit: Int): List<TopicSummary>
    fun listFollowing(userId: PublicId, limit: Int = 100): List<TopicSummary>
    fun countPosts(topicId: PublicId): Long
    fun countFollowers(topicId: PublicId): Long
    fun isFollowing(topicId: PublicId, userId: PublicId): Boolean
    fun follow(topicId: PublicId, userId: PublicId): Boolean
    fun unfollow(topicId: PublicId, userId: PublicId): Boolean
}
