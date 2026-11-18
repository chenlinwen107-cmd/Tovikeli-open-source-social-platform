package com.contentplatform.backend.api.media

import com.contentplatform.backend.domain.media.Media
import com.contentplatform.backend.domain.source.Instance

/**
 * API response DTO for one Media detail.
 *
 * Minimal stable projection of the current Media Domain aggregate. PublicId
 * value objects are projected as their plain string value.
 *
 * URL visibility is policy-driven:
 *  - thumbnailUrl / previewUrl: null when instance.allowMediaPreview == false
 *  - originalUrl: null when instance.allowOriginalAccess == false
 */
data class MediaResponse(
    val id: String,
    val status: String,
    val sourceId: String,
    val instanceId: String,
    val thumbnailUrl: String?,
    val previewUrl: String?,
    val originalUrl: String?,
    val altText: String?,
    val license: String?,
    val metadata: Map<String, Any?>,
    val kind: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        fun from(media: Media, instance: Instance): MediaResponse = MediaResponse(
            id = media.id.value,
            status = media.status.name,
            sourceId = media.sourceId.value,
            instanceId = media.instanceId.value,
            thumbnailUrl = if (instance.allowMediaPreview) media.thumbnail?.value else null,
            previewUrl = if (instance.allowMediaPreview) media.preview?.value else null,
            originalUrl = if (instance.allowOriginalAccess) media.original?.value else null,
            altText = media.altText,
            license = media.license,
            metadata = media.metadata,
            kind = media.kind.name,
            createdAt = media.createdAt,
            updatedAt = media.updatedAt
        )
    }
}
