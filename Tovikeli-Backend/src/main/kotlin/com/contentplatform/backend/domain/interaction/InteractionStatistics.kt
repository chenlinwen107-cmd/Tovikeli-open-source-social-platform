package com.contentplatform.backend.domain.interaction

/**
 * Per-type interaction count snapshot for a single content aggregate.
 *
 * Built at read time from SQL aggregation over [content_interaction].
 * Not a persistent entity; no mutable fields.
 */
data class InteractionStatistics(
    val total: Long,
    val like: Long,
    val favorite: Long,
    val repost: Long,
    val bookmark: Long
) {
    companion object {
        /** All-zero statistics for content with no interactions. */
        val ZERO = InteractionStatistics(
            total = 0L,
            like = 0L,
            favorite = 0L,
            repost = 0L,
            bookmark = 0L
        )

        /**
         * Builds an [InteractionStatistics] from a per-type count map.
         *
         * Types absent from the map default to zero.
         */
        fun fromCounts(counts: Map<InteractionType, Long>): InteractionStatistics {
            val like = counts[InteractionType.LIKE] ?: 0L
            val favorite = counts[InteractionType.FAVORITE] ?: 0L
            val repost = counts[InteractionType.REPOST] ?: 0L
            val bookmark = counts[InteractionType.BOOKMARK] ?: 0L
            return InteractionStatistics(
                total = like + favorite + repost + bookmark,
                like = like,
                favorite = favorite,
                repost = repost,
                bookmark = bookmark
            )
        }
    }
}
