package com.contentplatform.backend.database.entity

/**
 * Persistence row model for the `instance` table.
 *
 * Internal to the database layer; never returned above repository implementations.
 */
data class InstanceEntity(
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
)
