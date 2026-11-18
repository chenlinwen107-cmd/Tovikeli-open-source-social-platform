package com.contentplatform.backend.database.entity

data class MediaEntity(
    val id: String, val sha256: String?, val sourceId: String, val instanceId: String,
    val thumbnailUrl: String?, val previewUrl: String?, val originalUrl: String?,
    val altText: String?, val license: String?, val metadataJson: String?,
    val kind: String, val status: String, val createdAt: Long, val updatedAt: Long
)
