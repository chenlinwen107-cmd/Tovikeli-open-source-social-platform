package com.contentplatform.backend.application.content

import com.contentplatform.backend.application.media.GetMediaByIdsUseCase
import com.contentplatform.backend.application.media.ReadableMedia
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.repository.ContentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Reads the media associated with a single Content aggregate (Stage 33).
 *
 * Returns the readable [ReadableMedia] entries for the Content's mediaIds,
 * ordered by the Content.mediaIds association order. Missing or unreadable
 * media are silently omitted from the result.
 *
 * Content lifecycle visibility (Stage 28): only ACTIVE content is surfaced
 * through public detail reads. DELETED, HIDDEN, UNAVAILABLE and UNKNOWN
 * content is treated as non-existent.
 *
 * Not-found semantics: null is returned when the Content does not exist
 * or is not ACTIVE. Mapping to HTTP 404 is the API layer's responsibility.
 *
 * Empty media: when the Content has no mediaIds, an empty list is returned
 * without invoking the media repository.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetContentMediaUseCase(
    private val contentRepository: ContentRepository,
    private val getMediaByIdsUseCase: GetMediaByIdsUseCase,
    private val contentAccessUseCase: ContentAccessUseCase
) {

    fun getMedia(contentId: PublicId, callerUserId: PublicId? = null): List<ReadableMedia>? {
        val content = contentRepository.findById(contentId) ?: return null
        if (content.status != ContentStatus.ACTIVE) return null
        if (contentAccessUseCase.state(content, callerUserId) != ContentAccessState.ALLOWED) return null

        if (content.mediaIds.isEmpty()) {
            return emptyList()
        }

        val readableMedia = getMediaByIdsUseCase.getByIds(content.mediaIds)
        if (readableMedia.isEmpty()) {
            return emptyList()
        }

        val readableById = readableMedia.associateBy { it.media.id }
        return content.mediaIds.mapNotNull { readableById[it] }
    }
}
