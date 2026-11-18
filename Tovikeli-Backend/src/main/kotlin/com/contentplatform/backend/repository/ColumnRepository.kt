package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId

data class ColumnAuthor(
    val id: String,
    val username: String,
    val nickname: String,
    val avatar: String? = null
)

data class ColumnArticle(
    val id: String,
    val title: String,
    val summary: String,
    val cover: String,
    val category: String,
    val featured: Boolean,
    val views: Long,
    val likeCount: Long,
    val commentCount: Long,
    val readMins: Int,
    val createdAt: Long,
    val author: ColumnAuthor,
    val liked: Boolean,
    val content: String? = null
)

interface ColumnRepository {
    fun list(category: String?, sort: String, userId: PublicId?, limit: Int, offset: Int): List<ColumnArticle>
    fun findById(id: PublicId, userId: PublicId?): ColumnArticle?
    fun listRelated(id: PublicId, category: String, userId: PublicId?, limit: Int): List<ColumnArticle>
    fun listTrending(userId: PublicId?, limit: Int): List<ColumnArticle>
    fun categoryCounts(): List<Pair<String, Long>>
    fun create(id: PublicId, title: String, category: String, cover: String, summary: String, createdAt: Long): ColumnArticle
    fun delete(id: PublicId): Boolean
}
