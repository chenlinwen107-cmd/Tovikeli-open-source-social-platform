package com.contentplatform.backend.api.user

import com.contentplatform.backend.domain.user.User

/**
 * API response DTO for one Platform User.
 *
 * Minimal stable projection of the current User Domain aggregate. PublicId
 * value objects are projected as their plain string value. status is the
 * UserStatus name (ACTIVE / DISABLED). No password / login / session / role /
 * interaction / feed / recommendation fields exist in Stage 16.
 */
data class UserResponse(
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
) {

    companion object {
        /** Maps one Domain [User] aggregate to its API response projection. */
        fun from(user: User): UserResponse = UserResponse(
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
    }
}
