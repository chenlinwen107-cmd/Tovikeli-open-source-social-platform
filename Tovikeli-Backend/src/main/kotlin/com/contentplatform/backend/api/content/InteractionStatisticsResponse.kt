package com.contentplatform.backend.api.content

import com.contentplatform.backend.domain.interaction.InteractionStatistics

/**
 * API response DTO for interaction statistics nested within [ContentResponse].
 */
data class InteractionStatisticsResponse(
    val total: Long,
    val like: Long,
    val favorite: Long,
    val repost: Long,
    val bookmark: Long
) {
    companion object {
        fun from(stats: InteractionStatistics): InteractionStatisticsResponse =
            InteractionStatisticsResponse(
                total = stats.total,
                like = stats.like,
                favorite = stats.favorite,
                repost = stats.repost,
                bookmark = stats.bookmark
            )
    }
}
