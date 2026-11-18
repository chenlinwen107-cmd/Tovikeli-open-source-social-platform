package com.contentplatform.backend.api.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.ContentCursor
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ContentCursorCodec].
 *
 * The HTTP cursor format is frozen as Base64URL("v1:<createdAt>:<contentId>").
 * Decoding is strict: every malformed shape below must raise
 * IllegalArgumentException (mapped to HTTP 400 by the controller) and must
 * never be silently repaired.
 */
class ContentCursorCodecTest {

    /** Encodes an arbitrary logical string the way a malformed cursor would. */
    private fun b64(logical: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(logical.toByteArray())

    // ------------------------------------------------------------------
    // Round trip
    // ------------------------------------------------------------------

    @Test
    fun encode_thenDecode_returnsOriginalCursor() {
        val cursor = ContentCursor(createdAt = 1_757_000_000_123L, id = PublicId("ct_abc"))

        val raw = ContentCursorCodec.encode(cursor)
        val decoded = ContentCursorCodec.decode(raw)

        assertEquals(cursor, decoded, "decode(encode(cursor)) must restore the cursor exactly")
    }

    @Test
    fun encode_usesUrlSafeBase64WithoutPadding() {
        val raw = ContentCursorCodec.encode(ContentCursor(createdAt = 123L, id = PublicId("ct_x")))
        assertFalse(raw.contains('='), "the Base64URL form must not carry padding")
        assertFalse(raw.contains('+'), "the Base64URL form must not use the + alphabet")
        assertFalse(raw.contains('/'), "the Base64URL form must not use the / alphabet")
    }

    @Test
    fun decode_acceptsVersionedLogicalContent() {
        val raw = b64("v1:1757000000123:ct_abcdef")
        assertEquals(
            ContentCursor(createdAt = 1_757_000_000_123L, id = PublicId("ct_abcdef")),
            ContentCursorCodec.decode(raw)
        )
    }

    // ------------------------------------------------------------------
    // Strict decoding failures
    // ------------------------------------------------------------------

    @Test
    fun decode_whenBlank_throws() {
        assertFailsWith<IllegalArgumentException> { ContentCursorCodec.decode("") }
        assertFailsWith<IllegalArgumentException> { ContentCursorCodec.decode("   ") }
    }

    @Test
    fun decode_whenMalformedBase64_throws() {
        assertFailsWith<IllegalArgumentException> { ContentCursorCodec.decode("!!!") }
    }

    @Test
    fun decode_whenMissingVersion_throws() {
        assertFailsWith<IllegalArgumentException> { ContentCursorCodec.decode(b64("123:ct_1")) }
    }

    @Test
    fun decode_whenInvalidVersion_throws() {
        assertFailsWith<IllegalArgumentException> { ContentCursorCodec.decode(b64("v2:123:ct_1")) }
    }

    @Test
    fun decode_whenWrongFieldCount_throws() {
        // Too many fields.
        assertFailsWith<IllegalArgumentException> { ContentCursorCodec.decode(b64("v1:123:ct_1:extra")) }
        // Too few fields (createdAt only, no content id).
        assertFailsWith<IllegalArgumentException> { ContentCursorCodec.decode(b64("v1:123")) }
        // Trailing colon leaves the id missing.
        assertFailsWith<IllegalArgumentException> { ContentCursorCodec.decode(b64("v1:123:")) }
    }

    @Test
    fun decode_whenCreatedAtNotALong_throws() {
        assertFailsWith<IllegalArgumentException> { ContentCursorCodec.decode(b64("v1:abc:ct_1")) }
        // Overflowing Long.
        assertFailsWith<IllegalArgumentException> {
            ContentCursorCodec.decode(b64("v1:99999999999999999999:ct_1"))
        }
    }

    @Test
    fun decode_whenCreatedAtNegative_throws() {
        assertFailsWith<IllegalArgumentException> { ContentCursorCodec.decode(b64("v1:-5:ct_1")) }
    }

    @Test
    fun decode_whenContentIdBlank_throws() {
        assertFailsWith<IllegalArgumentException> { ContentCursorCodec.decode(b64("v1:123:   ")) }
    }
}
