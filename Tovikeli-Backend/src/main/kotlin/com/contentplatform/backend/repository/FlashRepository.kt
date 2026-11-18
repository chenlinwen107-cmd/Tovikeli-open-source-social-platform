package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

data class FlashItem(
    val id: String,
    val title: String,
    val summary: String,
    val category: String,
    val url: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

data class FlashListResult(val flash: List<FlashItem>)

interface FlashRepository {
    fun list(category: String?, query: String?, limit: Int): FlashListResult
    fun findById(id: PublicId): FlashItem?
    fun create(id: PublicId, title: String, summary: String, category: String, url: String, pinned: Boolean, now: Long): FlashItem
    fun update(id: PublicId, title: String, summary: String, category: String, url: String, pinned: Boolean, now: Long): FlashItem?
    fun delete(id: PublicId): Boolean
}