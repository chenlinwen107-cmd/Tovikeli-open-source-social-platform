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
 * Reads one [Media] aggregate by its public id with associated policy context.
 *
 * Stage 23-B Media Read API: orchestrates Media + Source + Instance lookup
 * and evaluates the read-availability policy.
 *
 * Policy evaluation:
 *  - Media must exist
 *  - Source must exist (defensive)
 *  - Instance must exist (defensive)
 *  - [Media.isAvailable] must return true (status == ACTIVE && source.enabled)
 *
 * Not-found / not-readable semantics: a sealed result type is returned (no
 * exceptions). Mapping to HTTP status codes is the API layer's responsibility.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetMediaByIdUseCase(
    private val mediaRepository: MediaRepository,
    private val sourceRepository: SourceRepository,
    private val instanceRepository: InstanceRepository
) {

    fun getById(id: PublicId): MediaReadResult {
        val media = mediaRepository.findById(id)
            ?: return MediaReadResult.NotFound

        val source = sourceRepository.findById(media.sourceId)
            ?: return MediaReadResult.NotFound

        val instance = instanceRepository.findById(media.instanceId)
            ?: return MediaReadResult.NotFound

        return if (media.isAvailable(source.enabled)) {
            MediaReadResult.Readable(media, source, instance)
        } else {
            MediaReadResult.NotReadable
        }
    }
}

sealed interface MediaReadResult {
    data object NotFound : MediaReadResult
    data object NotReadable : MediaReadResult
    data class Readable(
        val media: Media,
        val source: Source,
        val instance: Instance
    ) : MediaReadResult
}
