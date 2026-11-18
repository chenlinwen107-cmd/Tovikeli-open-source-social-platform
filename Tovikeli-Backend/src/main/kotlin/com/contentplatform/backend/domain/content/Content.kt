package com.contentplatform.backend.domain.content

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId

data class Content(
    // Core identity and cross-domain references
    val id: PublicId,                    // Public ID with ct_ prefix
    val instanceId: PublicId,            // External instance identifier
    val sourceContentId: String,         // External source content ID (unique with instanceId)
    val authorId: PublicId,              // External author reference (Author.id)
    val sourceId: PublicId,              // External platform source reference (Source.id)

    // Content body
    val text: String?,                   // Content body (nullable for media-only content)
    val language: String?,               // Language code (optional, no strong detection in V1)

    // Content classification and lifecycle
    val visibility: ContentVisibility,   // Content visibility
    val status: ContentStatus,           // Content lifecycle status

    // Media relations
    val mediaIds: List<PublicId>,        // Ordered media references (Media.id), may be empty

    // Content relations
    val parentId: PublicId?,             // Reply-to content (Content.id)
    val repostOfId: PublicId?,           // Repost-of original content (Content.id)
    val quoteOfId: PublicId?,            // Quote-of content (Content.id)
    val topicId: PublicId?,              // First-party discussion topic

    // Timestamps
    val externalCreatedAt: Long?,        // External creation timestamp (unix ms, nullable)
    val createdAt: Long,                 // Platform ingestion timestamp (unix ms)
    val updatedAt: Long,                 // Last update timestamp (unix ms)

    // Source-specific metadata
    val metadata: Map<String, Any?>      // Source-specific metadata
) {
    // Core business logic methods (policy-driven)

    // Only clearly public content may enter public search/indexing
    fun isPubliclyIndexable(instanceAllowIndex: Boolean): Boolean {
        return status == ContentStatus.ACTIVE &&
               visibility == ContentVisibility.PUBLIC &&
               instanceAllowIndex
    }

    // Whether this content is an external repost of another content
    fun isExternalRepost(): Boolean {
        return repostOfId != null && parentId == null
    }

    // Deduplication support: external uniqueness is (instanceId, sourceContentId)
    fun getDeduplicationKey(): String {
        return "${instanceId.value}:${sourceContentId}"
    }

    // Update state and timestamps
    fun updateStatus(newStatus: ContentStatus): Content =
        copy(status = newStatus, updatedAt = System.currentTimeMillis())

    // Factory methods for creation
    companion object {
        // Create from external adapter data.
        //
        // Natural key: (instanceId, sourceContentId)
        // PublicId:    ct_ + SHA-256(instanceId + ":" + sourceContentId) first 32 hex chars
        fun createFromAdapter(
            instanceIdValue: String,
            sourceContentId: String,
            authorId: PublicId,
            sourceId: PublicId,
            text: String?,
            language: String?,
            visibility: ContentVisibility,
            mediaIds: List<PublicId>,
            parentId: PublicId?,
            repostOfId: PublicId?,
            quoteOfId: PublicId?,
            topicId: PublicId? = null,
            externalCreatedAt: Long?,
            metadata: Map<String, Any?>
        ): Content {
            val instanceId = PublicId(instanceIdValue)
            return Content(
                id = PublicId("ct_" + IdentityHash.derive("${instanceId.value}:$sourceContentId")),
                instanceId = instanceId,
                sourceContentId = sourceContentId,
                authorId = authorId,
                sourceId = sourceId,
                text = text,
                language = language,
                visibility = visibility,
                status = ContentStatus.ACTIVE,
                mediaIds = mediaIds,
                parentId = parentId,
                repostOfId = repostOfId,
                quoteOfId = quoteOfId,
                topicId = topicId,
                externalCreatedAt = externalCreatedAt,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                metadata = metadata
            )
        }
    }
}
