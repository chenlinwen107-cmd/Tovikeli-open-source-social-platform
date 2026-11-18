package com.contentplatform.backend.database.entity

/**
 * Persistence row model for the `platform_user` table.
 *
 * Internal to the database layer; never returned above repository implementations.
 * status carries the [com.contentplatform.backend.domain.user.UserStatus] name
 * (ACTIVE / DISABLED), exactly like the existing enum-TEXT persistence mapping.
 */
data class UserEntity(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatar: String?,
    val bio: String?,
    val gender: String?,
    val location: String?,
    val cover: String?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)
