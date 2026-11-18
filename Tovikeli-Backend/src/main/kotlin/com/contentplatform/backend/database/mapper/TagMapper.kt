package com.contentplatform.backend.database.mapper

import com.contentplatform.backend.database.entity.TagEntity
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.domain.tag.TagOrigin

/**
 * Bidirectional mapper between Domain [Tag] and persistence [TagEntity].
 */
object TagMapper {

    fun toEntity(tag: Tag): TagEntity = TagEntity(
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

    fun toDomain(entity: TagEntity): Tag = Tag(
        id = PublicId(entity.id),
        name = entity.name,
        normalizedName = entity.normalizedName,
        origin = TagOrigin.valueOf(entity.origin),
        sourceTagId = entity.sourceTagId,
        instanceId = entity.instanceId?.let { PublicId(it) },
        sourceId = entity.sourceId?.let { PublicId(it) },
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )
}
