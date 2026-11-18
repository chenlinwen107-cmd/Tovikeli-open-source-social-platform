package com.contentplatform.backend.api.instance

import com.contentplatform.backend.domain.source.Instance

/**
 * API response DTO for one Instance detail.
 *
 * Minimal stable projection of the current Instance Domain aggregate. PublicId
 * value objects are projected as their plain string value. healthStatus is the
 * InstanceHealthStatus name. sourceId references the owning Source.id.
 *
 * No derived external URLs, no media fields, no SEO / ranking fields.
 */
data class InstanceResponse(
    val id: String,
    val sourceId: String,
    val domain: String,
    val displayName: String?,
    val enabled: Boolean,
    val allowIndex: Boolean,
    val allowMediaPreview: Boolean,
    val allowMediaCache: Boolean,
    val allowOriginalAccess: Boolean,
    val nsfwPolicy: String?,
    val healthStatus: String
) {

    companion object {
        /** Maps one Domain [Instance] aggregate to its API response projection. */
        fun from(instance: Instance): InstanceResponse = InstanceResponse(
            id = instance.id.value,
            sourceId = instance.sourceId.value,
            domain = instance.domain,
            displayName = instance.displayName,
            enabled = instance.enabled,
            allowIndex = instance.allowIndex,
            allowMediaPreview = instance.allowMediaPreview,
            allowMediaCache = instance.allowMediaCache,
            allowOriginalAccess = instance.allowOriginalAccess,
            nsfwPolicy = instance.nsfwPolicy,
            healthStatus = instance.healthStatus.name
        )
    }
}
