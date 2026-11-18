package com.contentplatform.backend.api.content

import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.content.ContentDetail

data class TopicSummaryResponse(val id: String, val name: String)
data class ContentAuthorResponse(val id: String, val sourceAuthorId: String, val username: String, val nickname: String, val avatar: String?)

/**
 * API response DTO for one Content detail.
 *
 * Minimal stable projection of the current Content Domain aggregate. PublicId
 * value objects are projected as their plain string value.
 *
 * mediaIds is projected as the stored opaque list of media reference ids; the
 * ids are NOT expanded into Media objects (Media identity stays DEFERRED).
 *
 * [commentCount] and [interactionStatistics] are present on the detail
 * endpoint response (Stage 25). Browse/search responses use the
 * [from(Content)] factory which leaves them at their zero defaults.
 */
data class ContentResponse(
    val id: String,
    val instanceId: String,
    val sourceContentId: String,
    val authorId: String?,
    val author: ContentAuthorResponse? = null,
    val sourceId: String,
    val text: String?,
    val language: String?,
    val visibility: String,
    val status: String,
    val mediaIds: List<String>,
    val parentId: String?,
    val repostOfId: String?,
    val quoteOfId: String?,
    val externalCreatedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val metadata: Map<String, Any?>,
    val location: String? = null,
    val locked: Boolean = false,
    val anonymous: Boolean = false,
    val topic: TopicSummaryResponse? = null,
    val commentCount: Long = 0L,
    val interactionStatistics: InteractionStatisticsResponse = InteractionStatisticsResponse(
        total = 0L, like = 0L, favorite = 0L, repost = 0L, bookmark = 0L
    )
) {

    companion object {
        /** Maps one Domain [Content] aggregate to its API response projection (no statistics). */
        fun from(content: Content, locked: Boolean = false, author: Author? = null): ContentResponse = ContentResponse(
            id = content.id.value,
            instanceId = content.instanceId.value,
            sourceContentId = content.sourceContentId,
            authorId = if (content.metadata["anonymous"] == true) null else content.authorId.value,
            author = if (content.metadata["anonymous"] == true || author == null) null else ContentAuthorResponse(author.id.value, author.sourceAuthorId, author.username, author.displayName ?: author.name, author.avatar),
            sourceId = content.sourceId.value,
            text = if (locked) null else content.text,
            language = content.language,
            visibility = content.visibility.name,
            status = content.status.name,
            mediaIds = if (locked) emptyList() else content.mediaIds.map { it.value },
            parentId = content.parentId?.value,
            repostOfId = content.repostOfId?.value,
            quoteOfId = content.quoteOfId?.value,
            externalCreatedAt = content.externalCreatedAt,
            createdAt = content.createdAt,
            updatedAt = content.updatedAt,
            metadata = content.metadata.filterKeys { it != "passwordHash" },
            location = content.metadata["location"]?.toString(),
            locked = locked,
            anonymous = content.metadata["anonymous"] == true,
            topic = topicFrom(content)
        )

        /** Maps a [ContentDetail] to its API response projection with statistics. */
        fun from(detail: ContentDetail, locked: Boolean = false, author: Author? = null): ContentResponse = ContentResponse(
            id = detail.content.id.value,
            instanceId = detail.content.instanceId.value,
            sourceContentId = detail.content.sourceContentId,
            authorId = if (detail.content.metadata["anonymous"] == true) null else detail.content.authorId.value,
            author = if (detail.content.metadata["anonymous"] == true || author == null) null else ContentAuthorResponse(author.id.value, author.sourceAuthorId, author.username, author.displayName ?: author.name, author.avatar),
            sourceId = detail.content.sourceId.value,
            text = if (locked) null else detail.content.text,
            language = detail.content.language,
            visibility = detail.content.visibility.name,
            status = detail.content.status.name,
            mediaIds = if (locked) emptyList() else detail.content.mediaIds.map { it.value },
            parentId = detail.content.parentId?.value,
            repostOfId = detail.content.repostOfId?.value,
            quoteOfId = detail.content.quoteOfId?.value,
            externalCreatedAt = detail.content.externalCreatedAt,
            createdAt = detail.content.createdAt,
            updatedAt = detail.content.updatedAt,
            metadata = detail.content.metadata.filterKeys { it != "passwordHash" },
            location = detail.content.metadata["location"]?.toString(),
            locked = locked,
            anonymous = detail.content.metadata["anonymous"] == true,
            topic = topicFrom(detail.content),
            commentCount = detail.commentCount,
            interactionStatistics = InteractionStatisticsResponse.from(detail.interactionStatistics)
        )
        private fun topicFrom(content: Content): TopicSummaryResponse? {
            val name = content.metadata["topicName"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
            val id = content.metadata["topicId"]?.toString()?.takeIf { it.isNotBlank() } ?: content.topicId?.value ?: return null
            return TopicSummaryResponse(id, name)
        }
    }
}
