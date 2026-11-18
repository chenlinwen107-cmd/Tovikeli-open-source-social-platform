package com.contentplatform.backend.application.topic

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.topic.Topic
import com.contentplatform.backend.repository.TopicRepository
import com.contentplatform.backend.repository.TopicSummary
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class TopicUseCases(private val topics: TopicRepository) {
    fun ensure(name: String): Topic = topics.ensure(name)
    fun findByName(name: String): Topic? = topics.findByName(name)
    fun hot(limit: Int): List<TopicSummary> = topics.listHot(limit)
    fun search(name: String, limit: Int): List<TopicSummary> = topics.search(name, limit)
    fun following(userId: PublicId, limit: Int = 100): List<TopicSummary> = topics.listFollowing(userId, limit)
    fun isFollowing(topicId: PublicId, userId: PublicId): Boolean = topics.isFollowing(topicId, userId)
    fun follow(topicId: PublicId, userId: PublicId): Boolean = topics.follow(topicId, userId)
    fun unfollow(topicId: PublicId, userId: PublicId): Boolean = topics.unfollow(topicId, userId)
    fun postCount(topicId: PublicId): Long = topics.countPosts(topicId)
    fun followerCount(topicId: PublicId): Long = topics.countFollowers(topicId)
}
