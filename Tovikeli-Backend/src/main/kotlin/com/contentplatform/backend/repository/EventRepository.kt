package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

data class EventOrganizer(val id: String, val username: String, val nickname: String, val avatar: String? = null)

data class CommunityEvent(
    val id: String, val title: String, val cover: String, val location: String, val category: String,
    val startAt: Long, val endAt: Long, val capacity: Int, val fee: Double, val online: Boolean,
    val signupCount: Long, val spotsLeft: Int?, val status: String, val full: Boolean, val signed: Boolean,
    val organizer: EventOrganizer, val isOrganizer: Boolean, val description: String? = null
)

data class EventListResult(val events: List<CommunityEvent>, val categories: List<String>, val upcomingCount: Long)
data class EventDetail(val event: CommunityEvent, val attendees: List<EventOrganizer>)

interface EventRepository {
    fun list(filter: String?, category: String?, viewerId: PublicId?, limit: Int): EventListResult
    fun findById(id: PublicId, viewerId: PublicId?): EventDetail?
    fun create(id: PublicId, organizerId: PublicId, title: String, cover: String, location: String, category: String,
               startAt: Long, endAt: Long, capacity: Int, fee: Double, online: Boolean, description: String, createdAt: Long): CommunityEvent
    fun delete(id: PublicId): Boolean
    fun isOrganizer(id: PublicId, userId: PublicId): Boolean
    fun isSigned(id: PublicId, userId: PublicId): Boolean
    fun signup(id: PublicId, userId: PublicId): Boolean
    fun cancelSignup(id: PublicId, userId: PublicId): Boolean
    fun eventExists(id: PublicId): Boolean
}