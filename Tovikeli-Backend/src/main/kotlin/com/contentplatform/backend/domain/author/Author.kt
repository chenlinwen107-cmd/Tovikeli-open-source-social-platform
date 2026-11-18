package com.contentplatform.backend.domain.author

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId

data class Author(
    val id: PublicId,
    val instanceId: PublicId,
    val sourceAuthorId: String,
    val name: String,
    val username: String,
    val displayName: String?,
    val url: String?,
    val avatar: String?,
    val bio: String?,
    val status: String?
) {
    companion object {
        // Create from external adapter data.
        //
        // Natural key: (instanceId, sourceAuthorId)
        // PublicId:    au_ + SHA-256(instanceId + ":" + sourceAuthorId) first 32 hex chars
        fun createFromAdapter(
            instanceId: PublicId,
            sourceAuthorId: String,
            name: String,
            username: String,
            displayName: String?,
            url: String?,
            avatar: String?,
            bio: String?,
            status: String?
        ): Author {
            return Author(
                id = PublicId("au_" + IdentityHash.derive("${instanceId.value}:$sourceAuthorId")),
                instanceId = instanceId,
                sourceAuthorId = sourceAuthorId,
                name = name,
                username = username,
                displayName = displayName,
                url = url,
                avatar = avatar,
                bio = bio,
                status = status
            )
        }
    }
}
