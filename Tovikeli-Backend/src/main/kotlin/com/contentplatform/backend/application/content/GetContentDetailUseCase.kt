package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.ContentDetail
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.interaction.InteractionStatistics
import com.contentplatform.backend.repository.CommentRepository
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.InteractionRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Reads one [ContentDetail] for a single content aggregate, combining
 * the [com.contentplatform.backend.domain.content.Content] with its active
 * comment count and per-type interaction statistics.
 *
 * This use case is intentionally separate from
 * [GetContentByIdUseCase] which remains a thin pass-through for the raw
 * content aggregate without aggregation.
 *
 * Statistics are computed at read time from SQL aggregation; no persistent
 * counter columns are involved.
 *
 * Not-found semantics: null is returned when the content does not exist
 * or when the content is not ACTIVE. Mapping to HTTP 404 is the API
 * layer's responsibility.
 *
 * Lifecycle visibility (Stage 28): only ACTIVE content is surfaced
 * through the public detail endpoint. DELETED, HIDDEN, UNAVAILABLE and
 * UNKNOWN content is treated as non-existent for public reads.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetContentDetailUseCase(
    private val contentRepository: ContentRepository,
    private val commentRepository: CommentRepository,
    private val interactionRepository: InteractionRepository
) {

    fun getById(id: PublicId): ContentDetail? {
        val content = contentRepository.findById(id) ?: return null
        if (content.status != ContentStatus.ACTIVE) return null
        val commentCount = commentRepository.countActiveByContentId(id)
        val typeCounts = interactionRepository.countByContentId(id)
        val interactionStatistics = InteractionStatistics.fromCounts(typeCounts)
        return ContentDetail(
            content = content,
            commentCount = commentCount,
            interactionStatistics = interactionStatistics
        )
    }
}
