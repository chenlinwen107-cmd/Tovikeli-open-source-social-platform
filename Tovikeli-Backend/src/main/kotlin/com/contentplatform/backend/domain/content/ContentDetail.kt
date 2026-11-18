package com.contentplatform.backend.domain.content

import com.contentplatform.backend.domain.interaction.InteractionStatistics

/**
 * Read-side projection for a single content detail with aggregated statistics.
 *
 * Pairs the [Content] aggregate with its active comment count and per-type
 * interaction counts. Built at read time by
 * [com.contentplatform.backend.application.content.GetContentDetailUseCase].
 * Not a persistent entity; no mutable fields.
 */
data class ContentDetail(
    val content: Content,
    val commentCount: Long,
    val interactionStatistics: InteractionStatistics
)
