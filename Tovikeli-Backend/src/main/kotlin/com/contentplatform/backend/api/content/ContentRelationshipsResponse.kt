package com.contentplatform.backend.api.content

import com.contentplatform.backend.domain.content.Content

/**
 * Lightweight API response DTO for a single related Content (Stage 31).
 *
 * Represents the existing Content data needed for display/navigation. Must
 * NOT contain commentCount, interactionStatistics, or recursive
 * relationships.
 */
data class RelatedContentResponse(
    val id: String,
    val instanceId: String,
    val sourceContentId: String,
    val authorId: String,
    val sourceId: String,
    val text: String?,
    val language: String?,
    val visibility: String,
    val status: String,
    val mediaIds: List<String>,
    val parentId: String?,
    val repostOfId: String?,
    val quoteOfId: String?,
    val externalCreatedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val metadata: Map<String, Any?>
) {
    companion object {
        fun from(content: Content): RelatedContentResponse = RelatedContentResponse(
            id = content.id.value,
            instanceId = content.instanceId.value,
            sourceContentId = content.sourceContentId,
            authorId = content.authorId.value,
            sourceId = content.sourceId.value,
            text = content.text,
            language = content.language,
            visibility = content.visibility.name,
            status = content.status.name,
            mediaIds = content.mediaIds.map { it.value },
            parentId = content.parentId?.value,
            repostOfId = content.repostOfId?.value,
            quoteOfId = content.quoteOfId?.value,
            externalCreatedAt = content.externalCreatedAt,
            createdAt = content.createdAt,
            updatedAt = content.updatedAt,
            metadata = content.metadata
        )
    }
}

/**
 * API response DTO for Content relationships (Stage 31).
 *
 * Each field is either a [RelatedContentResponse] for a publicly readable
 * related Content, or null when the target does not exist or is not
 * publicly readable.
 */
data class ContentRelationshipsResponse(
    val parent: RelatedContentResponse?,
    val repostOf: RelatedContentResponse?,
    val quoteOf: RelatedContentResponse?
)
