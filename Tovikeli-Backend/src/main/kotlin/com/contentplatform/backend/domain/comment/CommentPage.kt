package com.contentplatform.backend.domain.comment

/**
 * Comment-specific page result returned by comment queries (Stage 26).
 *
 * [items] holds at most the requested limit of domain aggregates: any probe
 * row fetched by the LIMIT requested+1 has-next detection is never included.
 * [nextCursor] is non-null exactly when another page follows and is then
 * derived from the last DELIVERED comment row (never from the probe row);
 * it is null on the last page, on empty results and when the request is not
 * paginated past the first page.
 *
 * Deliberately a small Comment-specific result, not a generic Page<T> or
 * pagination framework.
 */
data class CommentPage(
    val items: List<Comment>,
    val nextCursor: CommentCursor?
)
