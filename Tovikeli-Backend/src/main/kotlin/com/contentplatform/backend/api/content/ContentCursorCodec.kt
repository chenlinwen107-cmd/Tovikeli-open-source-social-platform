package com.contentplatform.backend.api.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.ContentCursor
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * HTTP-boundary codec for the versioned Base64URL content browse cursor.
 *
 * The logical (pre-encoding) format is frozen as:
 *
 * ```
 * v1:<createdAt>:<contentId>
 * ```
 *
 * and the value actually transmitted as the `cursor` query parameter is the
 * URL-safe Base64 encoding of that string (no padding, Base64URL alphabet).
 *
 * Decoding is strict by design: blank input, invalid Base64URL, a missing or
 * unknown version, the wrong number of fields, a missing/non-Long/negative
 * createdAt and an id that is not a valid content [PublicId] all raise
 * [IllegalArgumentException], which the controller maps to HTTP 400 (the use
 * case is never invoked). Malformed cursors are never silently repaired.
 *
 * The codec deliberately knows nothing about SQL or repository internals: it
 * only converts between the HTTP string form and the [ContentCursor] value
 * object. Base64 handling belongs to this API boundary, never to the
 * repository layer.
 */
object ContentCursorCodec {

    private const val VERSION = "v1"

    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()
    private val urlDecoder = Base64.getUrlDecoder()

    /**
     * Encodes [cursor] into the HTTP Base64URL form.
     */
    fun encode(cursor: ContentCursor): String {
        val logical = "$VERSION:${cursor.createdAt}:${cursor.id.value}"
        return urlEncoder.encodeToString(logical.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Strictly decodes the HTTP Base64URL [raw] cursor back into a
     * [ContentCursor]. Throws [IllegalArgumentException] for any malformed
     * input (see the class documentation for the exact validation rules).
     */
    fun decode(raw: String): ContentCursor {
        require(raw.isNotBlank()) { "cursor must not be blank" }
        val decodedText = try {
            String(urlDecoder.decode(raw), StandardCharsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("cursor is not valid Base64URL", e)
        }
        val parts = decodedText.split(":")
        require(parts.size == 3) { "cursor must contain exactly three ':'-separated fields" }
        require(parts[0] == VERSION) { "unsupported cursor version '${parts[0]}'" }
        val createdAt = parts[1].toLongOrNull()
            ?: throw IllegalArgumentException("cursor createdAt must be a valid Long")
        require(createdAt >= 0L) { "cursor createdAt must not be negative" }
        val contentId = try {
            PublicId(parts[2])
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("cursor content id must be a valid PublicId", e)
        }
        return ContentCursor(createdAt = createdAt, id = contentId)
    }
}
