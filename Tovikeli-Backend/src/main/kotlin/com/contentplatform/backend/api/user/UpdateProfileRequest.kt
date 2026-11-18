package com.contentplatform.backend.api.user

data class UpdateProfileRequest(
    val displayName: String? = null,
    val avatar: String? = null,
    val bio: String? = null,
    val gender: String? = null,
    val location: String? = null,
    val cover: String? = null
)
