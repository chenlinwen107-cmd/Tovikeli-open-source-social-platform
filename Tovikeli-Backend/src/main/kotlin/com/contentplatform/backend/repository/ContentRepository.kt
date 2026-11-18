package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus

interface ContentRepository {
    fun findById(id: PublicId): Content?
    fun findByInstanceAndSourceContentId(instanceId: PublicId, sourceContentId: String): Content?
    fun save(content: Content): Content
    fun findPublicContent(instanceId: PublicId?, authorId: PublicId?, hasMedia: Boolean?, cursor: ContentCursor?, limit: Int, viewerAuthorId: PublicId? = null): ContentBrowsePage =
        throw UnsupportedOperationException("findPublicContent requires a persistence-backed ContentRepository")
    fun findPublicContentSince(since: Long, cursor: ContentCursor?, limit: Int, viewerUserId: PublicId? = null): ContentBrowsePage =
        throw UnsupportedOperationException("findPublicContentSince requires a persistence-backed ContentRepository")
    fun findPublicContentByAuthors(authorIds: List<PublicId>, cursor: ContentCursor?, limit: Int): ContentBrowsePage =
        throw UnsupportedOperationException("findPublicContentByAuthors requires a persistence-backed ContentRepository")
    fun findPublicVideoContent(cursor: ContentCursor?, limit: Int): ContentBrowsePage =
        throw UnsupportedOperationException("findPublicVideoContent requires a persistence-backed ContentRepository")
    fun searchPublicContent(query: String, instanceId: PublicId?, authorId: PublicId?, sourceId: PublicId?, limit: Int): List<Content> =
        throw UnsupportedOperationException("searchPublicContent requires a persistence-backed ContentRepository")
    fun findPublicContentByTag(tagId: PublicId, cursor: ContentCursor?, limit: Int): ContentBrowsePage =
        throw UnsupportedOperationException("findPublicContentByTag requires a persistence-backed ContentRepository")
    fun findPublicContentByTopic(topicId: PublicId, cursor: ContentCursor?, limit: Int): ContentBrowsePage =
        throw UnsupportedOperationException("findPublicContentByTopic requires a persistence-backed ContentRepository")
    fun findByMediaId(mediaId: PublicId): Content? =
        throw UnsupportedOperationException("findByMediaId requires a persistence-backed ContentRepository")
    fun countActiveByAuthorId(authorId: PublicId): Long =
        throw UnsupportedOperationException("countActiveByAuthorId requires a persistence-backed ContentRepository")
    fun findByIds(ids: List<PublicId>): List<Content> =
        throw UnsupportedOperationException("findByIds requires a persistence-backed ContentRepository")
    fun findPublicByIds(ids: List<PublicId>): List<Content> =
        throw UnsupportedOperationException("findPublicByIds requires a persistence-backed ContentRepository")
    fun updateTopicId(id: PublicId, topicId: PublicId?): Boolean =
        throw UnsupportedOperationException("updateTopicId requires a persistence-backed ContentRepository")
    fun updateText(id: PublicId, text: String): Boolean =
        throw UnsupportedOperationException("updateText requires a persistence-backed ContentRepository")
    fun updateStatus(id: PublicId, newStatus: ContentStatus): Boolean =
        throw UnsupportedOperationException("updateStatus requires a persistence-backed ContentRepository")
    companion object { const val MAX_PUBLIC_BROWSE_LIMIT: Int = 100 }
}
