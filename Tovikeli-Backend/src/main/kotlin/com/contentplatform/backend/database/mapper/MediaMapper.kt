package com.contentplatform.backend.database.mapper

import com.contentplatform.backend.database.entity.MediaEntity
import com.contentplatform.backend.database.support.JsonSupport
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.media.*

object MediaMapper {
    fun toEntity(media: Media) = MediaEntity(
        media.id.value, media.sha256, media.sourceId.value, media.instanceId.value,
        media.thumbnail?.value, media.preview?.value, media.original?.value,
        media.altText, media.license, JsonSupport.toJson(media.metadata),
        media.kind.name, media.status.name, media.createdAt, media.updatedAt
    )

    fun toDomain(e: MediaEntity) = Media(
        id = PublicId(e.id), sha256 = e.sha256, sourceId = PublicId(e.sourceId),
        instanceId = PublicId(e.instanceId),
        thumbnail = e.thumbnailUrl?.let(::MediaUrl), preview = e.previewUrl?.let(::MediaUrl),
        original = e.originalUrl?.let(::MediaUrl), altText = e.altText, license = e.license,
        metadata = JsonSupport.fromJson(e.metadataJson ?: "{}"),
        kind = runCatching { MediaKind.valueOf(e.kind) }.getOrDefault(MediaKind.OTHER),
        status = MediaStatus.valueOf(e.status), createdAt = e.createdAt, updatedAt = e.updatedAt
    )
}
