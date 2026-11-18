package com.contentplatform.backend.application.media

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.media.Media
import com.contentplatform.backend.domain.media.MediaKind
import com.contentplatform.backend.domain.media.MediaUrl
import com.contentplatform.backend.repository.MediaRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

data class UploadedMedia(
    val id: String,
    val type: String,
    val url: String,
    val originalName: String?,
    val size: Long
)

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class UploadMediaUseCase(
    private val mediaRepository: MediaRepository,
    @Value("\${tovikeli.media.storage-dir:./data/media}") storageDir: String,
    @Value("\${tovikeli.media.max-file-size-bytes:104857600}") private val maxFileSize: Long
) {
    companion object {
        private val LOCAL_INSTANCE_ID = PublicId("inst_local")
        private val LOCAL_SOURCE_ID = PublicId("src_local")
    }

    private val storageRoot: Path = Path.of(storageDir).toAbsolutePath().normalize()

    fun upload(userId: PublicId, file: MultipartFile, purpose: String = "content"): UploadedMedia {
        require(!file.isEmpty) { "file must not be empty" }
        require(file.size <= maxFileSize) { "file exceeds maximum size" }

        val contentType = file.contentType?.lowercase()?.trim().orEmpty()
        val kind = when {
            contentType.startsWith("image/") -> MediaKind.IMAGE
            contentType.startsWith("video/") -> MediaKind.VIDEO
            else -> throw IllegalArgumentException("only image and video files are supported")
        }

        val extension = extensionFor(contentType, file.originalFilename)
        val externalRef = UUID.randomUUID().toString()
        val media = Media.createFromAdapter(
            instanceId = LOCAL_INSTANCE_ID,
            externalMediaRef = externalRef,
            sourceId = LOCAL_SOURCE_ID,
            sha256 = sha256(file.bytes),
            original = MediaUrl("/api/v1/media/${mediaIdPlaceholder(externalRef)}/file"),
            metadata = mapOf(
                "uploadedByUserId" to userId.value,
                "purpose" to purpose,
                "originalFilename" to file.originalFilename,
                "contentType" to contentType,
                "size" to file.size
            ),
            kind = kind
        )

        val target = storageRoot.resolve("${media.id.value}.$extension").normalize()
        require(target.parent == storageRoot) { "invalid media path" }

        Files.createDirectories(storageRoot)
        file.inputStream.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }

        val finalMedia = media.copy(
            original = MediaUrl("/api/v1/media/${media.id.value}/file"),
            preview = MediaUrl("/api/v1/media/${media.id.value}/file"),
            thumbnail = if (kind == MediaKind.IMAGE) MediaUrl("/api/v1/media/${media.id.value}/file") else null
        )
        mediaRepository.save(finalMedia)

        return UploadedMedia(
            id = finalMedia.id.value,
            type = kind.name.lowercase(),
            url = finalMedia.original?.value.orEmpty(),
            originalName = file.originalFilename,
            size = file.size
        )
    }

    fun resolveStoredFile(media: Media): Path {
        val contentType = media.metadata["contentType"]?.toString().orEmpty()
        val extension = extensionFor(contentType, media.metadata["originalFilename"]?.toString())
        return storageRoot.resolve("${media.id.value}.$extension").normalize().also {
            require(it.parent == storageRoot) { "invalid media path" }
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun extensionFor(contentType: String, filename: String?): String {
        val fromName = filename?.substringAfterLast('.', "")?.lowercase()
        if (!fromName.isNullOrBlank() && fromName.length <= 8 && fromName.all { it.isLetterOrDigit() }) {
            return fromName
        }
        return when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/avif" -> "avif"
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "video/x-matroska" -> "mkv"
            else -> "bin"
        }
    }

    private fun mediaIdPlaceholder(externalRef: String): String =
        "md_" + com.contentplatform.backend.domain.common.IdentityHash.derive(
            LOCAL_INSTANCE_ID.value + ":" + externalRef
        )
}
