package com.contentplatform.backend.database.entity

data class TopicEntity(
    val id: String,
    val name: String,
    val normalizedName: String,
    val description: String?,
    val createdAt: Long,
    val updatedAt: Long
)
