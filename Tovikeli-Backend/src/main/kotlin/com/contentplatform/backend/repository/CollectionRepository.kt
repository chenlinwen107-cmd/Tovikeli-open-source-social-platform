package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

data class CollectionOwner(
    val id: String,
    val username: String,
    val nickname: String,
    val avatar: String? = null
)

data class CollectionSummary(
    val id: String,
    val title: String,
    val description: String,
    val cover: String,
    val itemCount: Long,
    val owner: CollectionOwner,
    val isOwner: Boolean
)

data class CollectionItem(
    val itemId: String,
    val type: String,
    val data: Map<String, Any?>
)

data class CollectionDetail(
    val collection: CollectionSummary,
    val items: List<CollectionItem>
)

interface CollectionRepository {
    fun list(ownerId: PublicId?, viewerId: PublicId?): List<CollectionSummary>
    fun findById(id: PublicId, viewerId: PublicId?): CollectionDetail?
    fun create(id: PublicId, ownerId: PublicId, title: String, description: String, cover: String, createdAt: Long): CollectionSummary
    fun delete(id: PublicId): Boolean
    fun addItem(id: PublicId, collectionId: PublicId, targetType: String, targetId: PublicId, sortOrder: Int): CollectionItem
    fun removeItem(collectionId: PublicId, itemId: PublicId): Boolean
    fun owns(collectionId: PublicId, ownerId: PublicId): Boolean
    fun targetExists(targetType: String, targetId: PublicId): Boolean
    fun nextSortOrder(collectionId: PublicId): Int
}
