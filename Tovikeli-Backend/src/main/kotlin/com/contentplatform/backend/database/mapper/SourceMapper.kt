package com.contentplatform.backend.database.mapper

import com.contentplatform.backend.database.entity.SourceEntity
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.source.Source
import com.contentplatform.backend.domain.source.SourceType

/**
 * Bidirectional mapper between Domain [Source] and persistence [SourceEntity].
 */
object SourceMapper {

    fun toEntity(source: Source): SourceEntity = SourceEntity(
        id = source.id.value,
        name = source.name,
        type = source.type.name,
        enabled = source.enabled
    )

    fun toDomain(entity: SourceEntity): Source = Source(
        id = PublicId(entity.id),
        name = entity.name,
        type = SourceType.valueOf(entity.type),
        enabled = entity.enabled
    )
}
