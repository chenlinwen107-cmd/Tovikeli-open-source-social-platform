package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.content.Content

/**
 * Content-specific browse page result returned by
 * [ContentRepository.findPublicContent] (Stage 14).
 *
 * [contents] holds at most the requested limit of domain aggregates: any probe
 * row fetched by the LIMIT requested+1 has-next detection is never included.
 * [nextCursor] is non-null exactly when another page follows and is then
 * derived from the last DELIVERED content row (never from the probe row); it
 * is null on the last page, on empty results and when the request is not
 * paginated past the first page.
 *
 * Deliberately a small Content-specific result, not a generic Page<T> or
 * pagination framework.
 */
data class ContentBrowsePage(
    val contents: List<Content>,
    val nextCursor: ContentCursor?
)
