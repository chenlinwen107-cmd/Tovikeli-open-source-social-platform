package com.contentplatform.backend.domain.tag

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId

data class Tag(
    // Core identity
    val id: PublicId,                    // Public ID with tg_ prefix
    val name: String,                    // Display name, preserved verbatim (never altered by normalization)
    val normalizedName: String?,         // Search normalization only; no semantic equivalence implied
    val origin: TagOrigin,               // PLATFORM or EXTERNAL

    // External source references (EXTERNAL tags only; null for PLATFORM tags)
    val sourceTagId: String?,            // External source tag ID
    val instanceId: PublicId?,           // External instance reference (Instance.id)
    val sourceId: PublicId?,             // External platform source reference (Source.id)

    // Timestamps
    val createdAt: Long,                 // Platform ingestion timestamp (unix ms)
    val updatedAt: Long                  // Last update timestamp (unix ms)
) {
    // Unique key for deduplication.
    // Origin is always included so PLATFORM and EXTERNAL tags never collide.
    fun getDeduplicationKey(): String {
        if (origin == TagOrigin.EXTERNAL) {
            val instance = instanceId
            val sourceTag = sourceTagId
            if (instance == null || sourceTag == null) {
                throw IllegalStateException(
                    "EXTERNAL tag requires both instanceId and sourceTagId for deduplication key"
                )
            }
            return "EXTERNAL:${instance.value}:${sourceTag}"
        }
        return "PLATFORM:$name"
    }

    // Returns the normalized name used for search normalization only.
    // Returns null when no normalized name is available.
    fun tryNormalize(): String? = normalizedName

    // Factory methods for creation
    companion object {
        // Create an EXTERNAL tag from adapter data.
        //
        // Natural key: (instanceId, sourceTagId)
        // PublicId:    tg_ + SHA-256(instanceId + ":" + sourceTagId) first 32 hex chars
        fun createFromAdapter(
            name: String,
            normalizedName: String?,
            sourceTagId: String,
            instanceId: PublicId,
            sourceId: PublicId
        ): Tag {
            return Tag(
                id = PublicId("tg_" + IdentityHash.derive("${instanceId.value}:$sourceTagId")),
                name = name,
                normalizedName = normalizedName,
                origin = TagOrigin.EXTERNAL,
                sourceTagId = sourceTagId,
                instanceId = instanceId,
                sourceId = sourceId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        // Create a PLATFORM tag from raw platform data.
        //
        // Natural key: name
        // PublicId:    tg_ + SHA-256(name) first 32 hex chars
        fun createPlatform(
            name: String,
            normalizedName: String?
        ): Tag {
            return Tag(
                id = PublicId("tg_" + IdentityHash.derive(name)),
                name = name,
                normalizedName = normalizedName,
                origin = TagOrigin.PLATFORM,
                sourceTagId = null,
                instanceId = null,
                sourceId = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
