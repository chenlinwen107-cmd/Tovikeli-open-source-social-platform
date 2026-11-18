package com.contentplatform.backend.database.mapper

import com.contentplatform.backend.database.entity.UserEntity
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus

/**
 * Bidirectional mapper between Domain [User] and persistence [UserEntity].
 */
object UserMapper {

    fun toEntity(user: User): UserEntity = UserEntity(
        id = user.id.value,
        username = user.username,
        displayName = user.displayName,
        avatar = user.avatar,
        bio = user.bio,
        gender = user.gender,
        location = user.location,
        cover = user.cover,
        status = user.status.name,
        createdAt = user.createdAt,
        updatedAt = user.updatedAt
    )

    fun toDomain(entity: UserEntity): User = User(
        id = PublicId(entity.id),
        username = entity.username,
        displayName = entity.displayName,
        avatar = entity.avatar,
        bio = entity.bio,
        gender = entity.gender,
        location = entity.location,
        cover = entity.cover,
        status = UserStatus.valueOf(entity.status),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )
}
