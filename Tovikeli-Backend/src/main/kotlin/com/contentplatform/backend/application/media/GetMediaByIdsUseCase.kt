package com.contentplatform.backend.application.media

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.media.Media
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.Source
import com.contentplatform.backend.repository.InstanceRepository
import com.contentplatform.backend.repository.MediaRepository
import com.contentplatform.backend.repository.SourceRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Reads multiple [Media] aggregates by their public ids with associated policy context.
 *
 * Stage 24 Bulk Media Read API: orchestrates Media + Source + Instance lookup
 * for each requested id and evaluates the read-availability policy.
 *
 * Policy evaluation (identical to [GetMediaByIdUseCase]):
 *  - Media must exist
 *  - Source must exist (defensive)
 *  - Instance must exist (defensive)
 *  - [Media.isAvailable] must return true (status == ACTIVE && source.enabled)
 *
 * Missing / not-readable Media are silently omitted from the result list.
 * The output order follows the deduplicated request id first-occurrence order.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetMediaByIdsUseCase(
    private val mediaRepository: MediaRepository,
    private val sourceRepository: SourceRepository,
    private val instanceRepository: InstanceRepository
) {

    /**
     * Returns readable [ReadableMedia] entries for the requested [ids], preserving
     * the deduplicated request order. Missing or not-readable Media are omitted.
     */
    fun getByIds(ids: List<PublicId>): List<ReadableMedia> {
        val result = mutableListOf<ReadableMedia>()
        for (id in ids) {
            val media = mediaRepository.findById(id) ?: continue
            val source = sourceRepository.findById(media.sourceId) ?: continue
            val instance = instanceRepository.findById(media.instanceId) ?: continue
            if (media.isAvailable(source.enabled)) {
                result.add(ReadableMedia(media, source, instance))
            }
        }
        return result
    }
}

/**
 * Internal application result type for batch Media reads. Not a Domain model.
 */
data class ReadableMedia(
    val media: Media,
    val source: Source,
    val instance: Instance
)
