package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

data class BrowseHistoryItem(
    val id: Long,
    val type: String,
    val targetId: String,
    val title: String,
    val typeLabel: String,
    val link: String,
    val icon: String,
    val authorNickname: String,
    val cover: String,
    val solved: Boolean,
    val viewedAt: Long
)

interface BrowseHistoryRepository {
    fun upsert(userId: PublicId, type: String, targetId: String, title: String, typeLabel: String, link: String, icon: String, authorNickname: String, cover: String, solved: Boolean, viewedAt: Long)
    fun list(userId: PublicId, limit: Int): List<BrowseHistoryItem>
    fun delete(userId: PublicId, type: String, targetId: String): Boolean
    fun clear(userId: PublicId): Int
}
