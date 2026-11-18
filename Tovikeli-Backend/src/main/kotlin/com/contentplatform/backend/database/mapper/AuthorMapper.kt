package com.contentplatform.backend.database.mapper

import com.contentplatform.backend.database.entity.AuthorEntity
import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.PublicId

/**
 * Bidirectional mapper between Domain [Author] and persistence [AuthorEntity].
 */
object AuthorMapper {

    fun toEntity(author: Author): AuthorEntity = AuthorEntity(
        id = author.id.value,
        instanceId = author.instanceId.value,
        sourceAuthorId = author.sourceAuthorId,
        name = author.name,
        username = author.username,
        displayName = author.displayName,
        url = author.url,
        avatar = author.avatar,
        bio = author.bio,
        status = author.status
    )

    fun toDomain(entity: AuthorEntity): Author = Author(
        id = PublicId(entity.id),
        instanceId = PublicId(entity.instanceId),
        sourceAuthorId = entity.sourceAuthorId,
        name = entity.name,
        username = entity.username,
        displayName = entity.displayName,
        url = entity.url,
        avatar = entity.avatar,
        bio = entity.bio,
        status = entity.status
    )
}
