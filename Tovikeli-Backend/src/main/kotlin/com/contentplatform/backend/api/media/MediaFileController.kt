package com.contentplatform.backend.api.media

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.media.GetMediaByIdUseCase
import com.contentplatform.backend.application.media.UploadMediaUseCase
import com.contentplatform.backend.application.content.ContentAccessUseCase
import com.contentplatform.backend.application.content.ContentAccessState
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.media.MediaStatus
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/media")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class MediaFileController(
    private val getMediaByIdUseCase: GetMediaByIdUseCase,
    private val uploadMediaUseCase: UploadMediaUseCase,
    private val contentAccessUseCase: ContentAccessUseCase
) {
    @GetMapping("/{mediaId}/file")
    fun file(
        @PathVariable mediaId: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Resource> {
        val result = getMediaByIdUseCase.getById(PublicId(mediaId))
        if (result !is com.contentplatform.backend.application.media.MediaReadResult.Readable) {
            return ResponseEntity.notFound().build()
        }
        val media = result.media
        val caller = CallerResolver.resolve(servletRequest)
        if (media.status != MediaStatus.ACTIVE || media.instanceId.value != "inst_local") {
            return ResponseEntity.notFound().build()
        }
        val publicProfileMedia = media.metadata["purpose"]?.toString() == "profile"
        if (!publicProfileMedia) {
            CallerResolver.require(servletRequest)
            val content = contentAccessUseCase.canReadMedia(media.id, caller?.userId)
            if (!content) return ResponseEntity.notFound().build()
        }

        val resource = FileSystemResource(uploadMediaUseCase.resolveStoredFile(media))
        if (!resource.exists() || !resource.isReadable) {
            return ResponseEntity.notFound().build()
        }

        val contentType = media.metadata["contentType"]?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { MediaType.parseMediaType(it) }.getOrNull() }
            ?: MediaType.APPLICATION_OCTET_STREAM

        return ResponseEntity.ok()
            .contentType(contentType)
            .contentLength(resource.contentLength())
            .body(resource)
    }
}
