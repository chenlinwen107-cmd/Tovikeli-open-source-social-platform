package com.contentplatform.backend.database.mapper

import com.contentplatform.backend.database.entity.InstanceEntity
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.InstanceHealthStatus

/**
 * Bidirectional mapper between Domain [Instance] and persistence [InstanceEntity].
 */
object InstanceMapper {

    fun toEntity(instance: Instance): InstanceEntity = InstanceEntity(
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

    fun toDomain(entity: InstanceEntity): Instance = Instance(
        id = PublicId(entity.id),
        sourceId = PublicId(entity.sourceId),
        domain = entity.domain,
        displayName = entity.displayName,
        enabled = entity.enabled,
        allowIndex = entity.allowIndex,
        allowMediaPreview = entity.allowMediaPreview,
        allowMediaCache = entity.allowMediaCache,
        allowOriginalAccess = entity.allowOriginalAccess,
        nsfwPolicy = entity.nsfwPolicy,
        healthStatus = InstanceHealthStatus.valueOf(entity.healthStatus)
    )
}
