package com.contentplatform.backend.domain.content

/**
 * Read-side projection for a Content's direct relationships (Stage 31).
 *
 * Exposes the three direct Content relationships already represented by
 * Content.parentId, Content.repostOfId, and Content.quoteOfId. Each field
 * is either the publicly readable related Content or null when the target
 * does not exist or is not publicly readable.
 *
 * Not a persistent entity; no mutable fields. One level only — no recursive
 * relationship traversal.
 */
data class ContentRelationships(
    val parent: Content?,
    val repostOf: Content?,
    val quoteOf: Content?
)
