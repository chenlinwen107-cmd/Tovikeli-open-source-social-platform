package com.contentplatform.backend.domain.source

import com.contentplatform.backend.domain.common.PublicId

data class Instance(
    val id: PublicId,
    val sourceId: PublicId,
    val domain: String,
    val displayName: String?,
    val enabled: Boolean,
    val allowIndex: Boolean,
    val allowMediaPreview: Boolean,
    val allowMediaCache: Boolean,
    val allowOriginalAccess: Boolean,
    val nsfwPolicy: String?,
    val healthStatus: InstanceHealthStatus
)
