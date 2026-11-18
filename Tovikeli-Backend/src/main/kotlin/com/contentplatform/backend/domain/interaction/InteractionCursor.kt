package com.contentplatform.backend.domain.interaction

import com.contentplatform.backend.domain.common.PublicId
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Stateless keyset cursor for interaction pagination (Stage 19).
 *
 * The cursor identifies the position of the LAST DELIVERED interaction row of a
 * page using the frozen ordering key (created_at DESC, interaction_id DESC).
 *
 *  - [createdAt] = interaction.created_at (unix ms, BIGINT in PostgreSQL)
 *  - [interactionId] = interaction.id (an interaction [PublicId])
 *
 * The cursor is fully self-contained: no cursor table, session, Redis or
 * server-side token is involved. It is deliberately Interaction-specific and is
 * NOT a generic pagination framework.
 *
 * The logical (pre-encoding) format is frozen as:
 * ```
 * v1:<createdAt>:<interactionId>
 * ```
 * and the value transmitted as a query parameter is the URL-safe Base64 encoding
 * of that string (no padding, Base64URL alphabet).
 *
 * Decoding is strict by design: blank input, invalid Base64URL, a missing or
 * unknown version, the wrong number of fields, a missing/non-Long/negative
 * createdAt and an interaction id that is not a valid [PublicId] all raise
 * [IllegalArgumentException]. Malformed cursors are never silently repaired.
 */
data class InteractionCursor(
    val createdAt: Long,
    val interactionId: PublicId
) {

    /**
     * Encodes this cursor into the HTTP Base64URL form.
     */
    fun encode(): String {
        val logical = "$VERSION:${createdAt}:${interactionId.value}"
        return URL_ENCODER.encodeToString(logical.toByteArray(StandardCharsets.UTF_8))
    }

    companion object {

        private const val VERSION = "v1"

        private val URL_ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val URL_DECODER = Base64.getUrlDecoder()

        /**
         * Strictly decodes the HTTP Base64URL [raw] cursor back into an
         * [InteractionCursor]. Throws [IllegalArgumentException] for any
         * malformed input (see the class documentation for the exact
         * validation rules).
         */
        fun decode(raw: String): InteractionCursor {
            require(raw.isNotBlank()) { "cursor must not be blank" }
            val decodedText = try {
                String(URL_DECODER.decode(raw), StandardCharsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("cursor is not valid Base64URL", e)
            }
            val parts = decodedText.split(":")
            require(parts.size == 3) { "cursor must contain exactly three ':'-separated fields" }
            require(parts[0] == VERSION) { "unsupported cursor version '${parts[0]}'" }
            val createdAt = parts[1].toLongOrNull()
                ?: throw IllegalArgumentException("cursor createdAt must be a valid Long")
            require(createdAt >= 0L) { "cursor createdAt must not be negative" }
            val interactionId = try {
                PublicId(parts[2])
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("cursor interaction id must be a valid PublicId", e)
            }
            return InteractionCursor(createdAt = createdAt, interactionId = interactionId)
        }
    }
}
