package com.contentplatform.backend.database.mapper

import com.contentplatform.backend.database.entity.TopicEntity
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.topic.Topic

object TopicMapper {
    fun toDomain(entity: TopicEntity): Topic = Topic(
        id = PublicId(entity.id),
        name = entity.name,
        normalizedName = entity.normalizedName,
        description = entity.description,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    fun toEntity(topic: Topic): TopicEntity = TopicEntity(
        id = topic.id.value,
        name = topic.name,
        normalizedName = topic.normalizedName,
        description = topic.description,
        createdAt = topic.createdAt,
        updatedAt = topic.updatedAt
    )
}
