package com.contentplatform.backend.database.mapper

import com.contentplatform.backend.database.entity.ContentEntity
import com.contentplatform.backend.database.support.JsonSupport
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility

/**
 * Bidirectional mapper between Domain [Content] and persistence [ContentEntity].
 *
 * mediaIds is not part of [ContentEntity]; the repository supplies the ordered
 * media ids separately when reconstructing the aggregate.
 */
object ContentMapper {

    fun toEntity(content: Content): ContentEntity = ContentEntity(
        id = content.id.value,
        instanceId = content.instanceId.value,
        sourceContentId = content.sourceContentId,
        authorId = content.authorId.value,
        sourceId = content.sourceId.value,
        text = content.text,
        language = content.language,
        visibility = content.visibility.name,
        status = content.status.name,
        parentId = content.parentId?.value,
        repostOfId = content.repostOfId?.value,
        quoteOfId = content.quoteOfId?.value,
        topicId = content.topicId?.value,
        externalCreatedAt = content.externalCreatedAt,
        createdAt = content.createdAt,
        updatedAt = content.updatedAt,
        metadataJson = JsonSupport.toJson(content.metadata)
    )

    fun toDomain(entity: ContentEntity, mediaIds: List<PublicId>): Content = Content(
        id = PublicId(entity.id),
        instanceId = PublicId(entity.instanceId),
        sourceContentId = entity.sourceContentId,
        authorId = PublicId(entity.authorId),
        sourceId = PublicId(entity.sourceId),
        text = entity.text,
        language = entity.language,
        visibility = ContentVisibility.valueOf(entity.visibility),
        status = ContentStatus.valueOf(entity.status),
        mediaIds = mediaIds,
        parentId = entity.parentId?.let { PublicId(it) },
        repostOfId = entity.repostOfId?.let { PublicId(it) },
        quoteOfId = entity.quoteOfId?.let { PublicId(it) },
        topicId = entity.topicId?.let { PublicId(it) },
        externalCreatedAt = entity.externalCreatedAt,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        metadata = JsonSupport.fromJson(entity.metadataJson)
    )
}
