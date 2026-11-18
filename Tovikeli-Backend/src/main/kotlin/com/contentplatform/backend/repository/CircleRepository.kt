package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

data class CircleSummary(val id: Long, val slug: String, val name: String, val description: String, val category: String, val icon: String, val color: String, val ownerId: String, val memberCount: Long, val postCount: Long, val joined: Boolean)
data class CircleMember(val id: String, val username: String, val nickname: String, val avatar: String?)
data class CircleChatMessage(val id: Long, val content: String, val createdAt: Long, val author: CircleMember)

interface CircleRepository {
    fun list(category: String?, userId: PublicId?): List<CircleSummary>
    fun findBySlug(slug: String, userId: PublicId?): CircleSummary?
    fun findById(id: Long, userId: PublicId?): CircleSummary?
    fun create(name: String, slug: String, description: String, category: String, icon: String, color: String, ownerId: PublicId, now: Long): CircleSummary
    fun toggleMember(circleId: Long, userId: PublicId): Boolean
    fun isMember(circleId: Long, userId: PublicId): Boolean
    fun members(circleId: Long, limit: Int = 24): List<CircleMember>
    fun addPost(circleId: Long, contentId: PublicId, now: Long)
    fun postIds(circleId: Long, limit: Int = 50): List<String>
    fun chat(circleId: Long, limit: Int = 100): List<CircleChatMessage>
    fun addChat(circleId: Long, userId: PublicId, content: String, now: Long): CircleChatMessage
}