package com.contentplatform.backend.api.tag

import com.contentplatform.backend.domain.tag.Tag

/**
 * API response DTO for one Tag detail.
 *
 * Minimal stable projection of the current Tag Domain aggregate. PublicId
 * value objects are projected as their plain string value. origin is the
 * TagOrigin name (PLATFORM / EXTERNAL). For a PLATFORM tag the external
 * reference fields (sourceTagId / instanceId / sourceId) are null; for an
 * EXTERNAL tag they carry the stored references. Both shapes are exposed
 * through this single DTO without any distinction logic in the controller.
 *
 * No derived external URLs, no search / ranking / SEO fields.
 */
data class TagResponse(
    val id: String,
    val name: String,
    val normalizedName: String?,
    val origin: String,
    val sourceTagId: String?,
    val instanceId: String?,
    val sourceId: String?,
    val createdAt: Long,
    val updatedAt: Long
) {

    companion object {
        /** Maps one Domain [Tag] aggregate to its API response projection. */
        fun from(tag: Tag): TagResponse = TagResponse(
            id = tag.id.value,
            name = tag.name,
            normalizedName = tag.normalizedName,
            origin = tag.origin.name,
            sourceTagId = tag.sourceTagId,
            instanceId = tag.instanceId?.value,
            sourceId = tag.sourceId?.value,
            createdAt = tag.createdAt,
            updatedAt = tag.updatedAt
        )
    }
}
