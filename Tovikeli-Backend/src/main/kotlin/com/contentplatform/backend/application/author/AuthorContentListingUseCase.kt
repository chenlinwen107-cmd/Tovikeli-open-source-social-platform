package com.contentplatform.backend.application.author

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Lists publicly browsable content scoped to one external Author.
 *
 * This is a thin pass-through over [ContentRepository.findPublicContent].
 * The only fixed filter is [authorId]; [ContentRepository.findPublicContent]
 * is called with instanceId=null and hasMedia=null so that only the author
 * scope is applied. Pagination and validation remain owned by the existing
 * repository/API contracts.
 *
 * Unknown or nonexistent author IDs naturally produce an empty
 * [ContentBrowsePage] through the content browse query; this use case does
 * not verify author existence.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class AuthorContentListingUseCase(
    private val contentRepository: ContentRepository
) {

    /**
     * Returns one page of at most [limit] publicly browsable Content
     * aggregates authored by [authorId], optionally starting after [cursor].
     */
    fun list(
        authorId: PublicId,
        cursor: ContentCursor?,
        limit: Int
    ): ContentBrowsePage =
        contentRepository.findPublicContent(
            instanceId = null,
            authorId = authorId,
            hasMedia = null,
            cursor = cursor,
            limit = limit
        )
}
