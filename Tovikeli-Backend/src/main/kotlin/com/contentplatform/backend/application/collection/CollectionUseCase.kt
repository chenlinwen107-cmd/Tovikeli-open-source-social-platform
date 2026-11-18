package com.contentplatform.backend.application.collection

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CollectionDetail
import com.contentplatform.backend.repository.CollectionRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.util.UUID

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class CollectionUseCase(private val collections: CollectionRepository) {
    fun list(viewerId: PublicId?) = collections.list(null, viewerId)
    fun mine(viewerId: PublicId) = collections.list(viewerId, viewerId)
    fun detail(id: PublicId, viewerId: PublicId?): CollectionDetail? = collections.findById(id, viewerId)

    fun create(userId: PublicId, title: String, description: String, cover: String): CollectionDetail {
        require(title.trim().length in 2..80) { "invalid title" }
        require(description.length <= 300) { "invalid description" }
        require(cover.length <= 500) { "invalid cover" }
        val id = PublicId(UUID.randomUUID().toString())
        collections.create(id, userId, title, description, cover, System.currentTimeMillis())
        return detail(id, userId)!!
    }

    fun delete(userId: PublicId, id: PublicId): Boolean {
        requireOwner(userId, id)
        return collections.delete(id)
    }

    fun addItem(userId: PublicId, id: PublicId, targetType: String, targetId: PublicId): CollectionItemResult {
        requireOwner(userId, id)
        require(targetType == "post" || targetType == "article") { "invalid target type" }
        require(collections.targetExists(targetType, targetId)) { "target does not exist" }
        val item = collections.addItem(PublicId(UUID.randomUUID().toString()), id, targetType, targetId, collections.nextSortOrder(id))
        return CollectionItemResult(item.itemId, item.type)
    }

    fun removeItem(userId: PublicId, id: PublicId, itemId: PublicId): Boolean {
        requireOwner(userId, id)
        return collections.removeItem(id, itemId)
    }

    private fun requireOwner(userId: PublicId, id: PublicId) {
        if (!collections.owns(id, userId)) throw SecurityException("forbidden")
    }

}

data class CollectionItemResult(val itemId: String, val type: String)
