package com.contentplatform.backend.domain.media

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId

data class Media(
    val id: PublicId, val sha256: String?, val sourceId: PublicId, val instanceId: PublicId,
    val thumbnail: MediaUrl?, val preview: MediaUrl?, val original: MediaUrl?,
    val altText: String?, val license: String?, val metadata: Map<String, Any?>,
    val kind: MediaKind = MediaKind.OTHER, val status: MediaStatus,
    val createdAt: Long, val updatedAt: Long
) {
    fun isAvailable(sourceEnabled: Boolean) = status == MediaStatus.ACTIVE && sourceEnabled
    fun canAccessOriginal(sourceEnabled: Boolean, instanceAllowOriginalAccess: Boolean,
        sourceLicenseRestrictions: Boolean, apiAvailable: Boolean, apiExposurePolicy: Boolean) =
        isAvailable(sourceEnabled) && instanceAllowOriginalAccess && sourceLicenseRestrictions &&
        apiAvailable && apiExposurePolicy
    fun canProvidePreview(sourceEnabled: Boolean, instanceAllowPreview: Boolean) =
        isAvailable(sourceEnabled) && instanceAllowPreview
    fun updateStatus(newStatus: MediaStatus) = copy(status = newStatus, updatedAt = System.currentTimeMillis())
    fun isExpired() = status == MediaStatus.EXPIRED || status == MediaStatus.DELETED
    fun getDeduplicationKey() = sha256

    companion object {
        fun createFromAdapter(instanceId: PublicId, externalMediaRef: String, sourceId: PublicId,
            sha256: String? = null, thumbnail: MediaUrl? = null, preview: MediaUrl? = null,
            original: MediaUrl? = null, altText: String? = null, license: String? = null,
            metadata: Map<String, Any?> = emptyMap(), kind: MediaKind = MediaKind.OTHER): Media {
            val now = System.currentTimeMillis()
            return Media(
                id = PublicId("md_" + IdentityHash.derive(instanceId.value + ":" + externalMediaRef)),
                sha256 = sha256, sourceId = sourceId, instanceId = instanceId,
                thumbnail = thumbnail, preview = preview, original = original,
                altText = altText, license = license, metadata = metadata, kind = kind,
                status = MediaStatus.ACTIVE, createdAt = now, updatedAt = now
            )
        }
    }
}
