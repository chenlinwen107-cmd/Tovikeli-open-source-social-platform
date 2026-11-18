package com.contentplatform.backend.api.media

import com.contentplatform.backend.application.media.GetMediaByIdUseCase
import com.contentplatform.backend.application.media.GetMediaByIdsUseCase
import com.contentplatform.backend.application.media.MediaReadResult
import com.contentplatform.backend.domain.common.PublicId
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Read-only Media API.
 *
 *   GET /api/v1/media/{mediaId}       read one Media detail
 *   GET /api/v1/media/batch?ids=...   read multiple Media by ids
 *
 * Detail flow: HTTP -> this controller -> [GetMediaByIdUseCase] -> MediaRepository.
 * Batch flow:  HTTP -> this controller -> [GetMediaByIdsUseCase] -> MediaRepository.
 * The controller never touches a Repository directly and contains no business
 * logic.
 *
 * Detail not-found semantics: when the use case returns NotFound or NotReadable
 * the controller responds HTTP 404 with an empty body; otherwise HTTP 200 with
 * the API [MediaResponse].
 *
 * Batch semantics (HTTP boundary only):
 *  - `ids` is a required comma-separated list of Media PublicIds.
 *    Missing, empty, or whitespace-only input is HTTP 400.
 *  - Maximum 50 IDs per request; exceeding is HTTP 400.
 *  - Blank or whitespace-only individual IDs are HTTP 400.
 *  - The 50-ID limit is validated BEFORE deduplication.
 *  - Duplicate IDs are deduplicated preserving first-occurrence order.
 *  - Missing or not-readable Media are silently omitted from the result.
 *  - All omitted → HTTP 200 with empty array `[]`.
 *  - Output order follows the deduplicated request id first-occurrence order.
 *
 * PublicId: the raw path/query values are wrapped by the existing [PublicId]
 * value object (its only rule: non-blank). Blank input is explicitly rejected
 * with HTTP 400 before the use case is invoked.
 */
@RestController
@RequestMapping("/api/v1/media")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class MediaController(
    private val getMediaByIdUseCase: GetMediaByIdUseCase,
    private val getMediaByIdsUseCase: GetMediaByIdsUseCase
) {

    companion object {
        private const val MAX_BATCH_SIZE = 50
    }

    @GetMapping("/{mediaId}")
    fun getMedia(@PathVariable("mediaId") mediaId: String): ResponseEntity<MediaResponse> {
        if (mediaId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val result = getMediaByIdUseCase.getById(PublicId(mediaId))
        return when (result) {
            is MediaReadResult.Readable -> ResponseEntity.ok(
                MediaResponse.from(result.media, result.instance)
            )
            is MediaReadResult.NotFound,
            is MediaReadResult.NotReadable -> ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/batch")
    fun getMediaBatch(
        @RequestParam(name = "ids", required = false) ids: String?
    ): ResponseEntity<List<MediaResponse>> {
        if (ids.isNullOrBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val rawParts = ids.split(",")
        if (rawParts.size > MAX_BATCH_SIZE) {
            return ResponseEntity.badRequest().build()
        }
        val trimmedParts = rawParts.map { it.trim() }
        if (trimmedParts.any { it.isEmpty() }) {
            return ResponseEntity.badRequest().build()
        }
        val publicIds = trimmedParts.map { PublicId(it) }
        val deduplicated = publicIds.distinct()
        val readable = getMediaByIdsUseCase.getByIds(deduplicated)
        val body = readable.map { MediaResponse.from(it.media, it.instance) }
        return ResponseEntity.ok(body)
    }
}
