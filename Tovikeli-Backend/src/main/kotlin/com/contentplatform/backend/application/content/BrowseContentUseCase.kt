package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.AuthorRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Reads one page of the most recently created publicly browsable [Content]
 * aggregates.
 *
 * Stage 10B-1 Application Read Contract: this use case is a thin pass-through
 * over [ContentRepository.findPublicContent] and returns the repository page
 * unchanged. No business filtering, sorting, pagination or other rules are
 * implemented here; the repository contract owns the browse semantics.
 *
 * The requested [limit] is passed through unchanged. The repository contract
 * bounds it to 0..[ContentRepository.MAX_PUBLIC_BROWSE_LIMIT] and rejects
 * values outside that range; this use case deliberately performs no duplicate
 * validation of its own.
 *
 * Optional [instanceId] (Stage 13-A), [authorId] (Stage 13-B), [hasMedia]
 * (Stage 13-C) and [cursor] (Stage 14) arguments are pure pass-throughs:
 * non-null filters restrict the browse, a non-null [cursor] starts the page
 * after that position, and nulls leave the unfiltered first-page Stage 10B-2
 * behaviour unchanged. The filters combine with AND semantics at the
 * repository. No validity rules are applied here; wrapping an HTTP value into
 * a [PublicId] / [Boolean] or decoding the HTTP cursor is the API layer's
 * responsibility.
 *
 * Empty-page semantics: an empty page is returned when nothing is browsable
 * (never null); its nextCursor is null.
 *
 * Transaction notes: simple read-only query; no transaction boundary is
 * declared (consistent with [GetContentByIdUseCase] and the other
 * repository-dependent read use cases). Like them, this use case stays
 * conditional on the configured datasource.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class BrowseContentUseCase(
    private val contentRepository: ContentRepository,
    private val authorRepository: AuthorRepository
) {

    /**
     * Returns one page of at most [limit] of the most recently created
     * publicly browsable Content aggregates, optionally scoped to
     * [instanceId], [authorId] and/or [hasMedia] and optionally starting after
     * [cursor].
     */
    fun browse(
        instanceId: PublicId?,
        authorId: PublicId?,
        hasMedia: Boolean?,
        cursor: ContentCursor?,
        limit: Int,
        callerUserId: PublicId? = null
    ): ContentBrowsePage {
        val viewerAuthorId = callerUserId?.let {
            authorRepository.findByInstanceAndSourceAuthorId(PublicId("inst_local"), it.value)?.id
        }
        return contentRepository.findPublicContent(instanceId, authorId, hasMedia, cursor, limit, viewerAuthorId)
    }
}
