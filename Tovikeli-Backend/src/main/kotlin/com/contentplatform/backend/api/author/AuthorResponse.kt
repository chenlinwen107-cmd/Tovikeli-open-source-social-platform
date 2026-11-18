package com.contentplatform.backend.api.author

import com.contentplatform.backend.domain.author.Author

/**
 * API response DTO for one Author detail.
 *
 * Minimal stable projection of the current Author Domain aggregate. PublicId
 * value objects are projected as their plain string value. url / avatar are
 * the profile url / avatar url ALREADY stored on the Author aggregate by the
 * ingestion pipeline; they are passed through unchanged and are never derived
 * by this API layer.
 *
 * No derived external URLs, no search / ranking / SEO / interaction fields.
 */
data class AuthorResponse(
    val id: String,
    val instanceId: String,
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
        /** Maps one Domain [Author] aggregate to its API response projection. */
        fun from(author: Author): AuthorResponse = AuthorResponse(
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
    }
}
