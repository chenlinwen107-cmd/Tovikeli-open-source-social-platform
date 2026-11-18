package com.contentplatform.backend.domain.interaction

/**
 * Interaction-specific page result returned by interaction queries (Stage 19).
 *
 * [items] holds at most the requested limit of domain aggregates: any probe
 * row fetched by the LIMIT requested+1 has-next detection is never included.
 * [nextCursor] is non-null exactly when another page follows and is then
 * derived from the last DELIVERED interaction row (never from the probe row);
 * it is null on the last page, on empty results and when the request is not
 * paginated past the first page.
 *
 * Deliberately a small Interaction-specific result, not a generic Page<T> or
 * pagination framework.
 */
data class InteractionPage(
    val items: List<Interaction>,
    val nextCursor: InteractionCursor?
)
