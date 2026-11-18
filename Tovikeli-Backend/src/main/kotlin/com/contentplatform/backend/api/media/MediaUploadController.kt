package com.contentplatform.backend.api.media

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.media.UploadMediaUseCase
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import jakarta.servlet.http.HttpServletRequest

data class UploadMediaResponse(
    val files: List<UploadMediaFileResponse>
)

data class UploadMediaFileResponse(
    val id: String,
    val type: String,
    val url: String,
    val originalName: String?,
    val size: Long
)

@RestController
@RequestMapping("/api/v1/media")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class MediaUploadController(
    private val uploadMediaUseCase: UploadMediaUseCase
) {
    @PostMapping("/upload")
    fun upload(
        @RequestPart("files", required = true) files: List<MultipartFile>,
        @RequestParam(name = "purpose", defaultValue = "content") purpose: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<UploadMediaResponse> {
        val caller = CallerResolver.require(servletRequest)
        if (files.isEmpty() || files.size > 9) {
            return ResponseEntity.badRequest().build()
        }

        return try {
            val uploaded = files.map { uploadMediaUseCase.upload(caller.userId, it, purpose) }
            ResponseEntity.ok(
                UploadMediaResponse(
                    uploaded.map {
                        UploadMediaFileResponse(
                            id = it.id,
                            type = it.type,
                            url = it.url,
                            originalName = it.originalName,
                            size = it.size
                        )
                    }
                )
            )
        } catch (_: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }
}
