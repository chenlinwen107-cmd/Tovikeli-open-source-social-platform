package com.contentplatform.backend.api.user

import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.api.content.ContentAuthorResponse

/**
 * Minimal content representation for Interaction history hydration.
 *
 * Only carries fields needed for Interaction history display.
 * Deliberately NOT [com.contentplatform.backend.api.content.ContentResponse].
 */
data class HydratedContentResponse(
    val id: String,
    val instanceId: String,
    val sourceContentId: String,
    val authorId: String,
    val sourceId: String,
    val text: String?,
    val visibility: String,
    val status: String,
    val author: ContentAuthorResponse? = null
) {
    companion object {
        fun from(content: Content, author: Author? = null): HydratedContentResponse = HydratedContentResponse(
            id = content.id.value,
            instanceId = content.instanceId.value,
            sourceContentId = content.sourceContentId,
            authorId = content.authorId.value,
            sourceId = content.sourceId.value,
            text = content.text,
            visibility = content.visibility.name,
            status = content.status.name,
            author = author?.let { ContentAuthorResponse(it.id.value, it.sourceAuthorId, it.username, it.displayName ?: it.name, it.avatar) }
        )
    }
}
