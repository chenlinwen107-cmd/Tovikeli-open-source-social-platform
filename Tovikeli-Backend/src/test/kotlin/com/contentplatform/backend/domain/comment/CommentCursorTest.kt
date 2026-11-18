package com.contentplatform.backend.domain.comment

import com.contentplatform.backend.domain.common.PublicId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Unit tests for [CommentCursor].
 *
 * The cursor format is frozen as Base64URL("v1:<createdAt>:<commentId>").
 * Decoding is strict: every malformed shape below must raise
 * IllegalArgumentException and must never be silently repaired.
 */
class CommentCursorTest {

    private fun b64(logical: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(logical.toByteArray())

    // ------------------------------------------------------------------
    // Round trip
    // ------------------------------------------------------------------

    @Test
    fun encode_thenDecode_returnsOriginalCursor() {
        val cursor = CommentCursor(
            createdAt = 1_757_000_000_123L,
            commentId = PublicId("cmt_abc")
        )

        val raw = cursor.encode()
        val decoded = CommentCursor.decode(raw)

        assertEquals(cursor, decoded, "decode(encode(cursor)) must restore the cursor exactly")
    }

    @Test
    fun encode_usesUrlSafeBase64WithoutPadding() {
        val raw = CommentCursor(createdAt = 123L, commentId = PublicId("cmt_x")).encode()
        assertFalse(raw.contains('='), "the Base64URL form must not carry padding")
        assertFalse(raw.contains('+'), "the Base64URL form must not use the + alphabet")
        assertFalse(raw.contains('/'), "the Base64URL form must not use the / alphabet")
    }

    @Test
    fun decode_acceptsVersionedLogicalContent() {
        val raw = b64("v1:1757000000123:cmt_abcdef")
        assertEquals(
            CommentCursor(createdAt = 1_757_000_000_123L, commentId = PublicId("cmt_abcdef")),
            CommentCursor.decode(raw)
        )
    }

    // ------------------------------------------------------------------
    // Different createdAt
    // ------------------------------------------------------------------

    @Test
    fun differentCreatedAt_producesDifferentCursor() {
        val a = CommentCursor(createdAt = 100L, commentId = PublicId("cmt_x"))
        val b = CommentCursor(createdAt = 200L, commentId = PublicId("cmt_x"))

        assertFailsWith<AssertionError> { assertEquals(a, b) }
    }

    @Test
    fun differentCreatedAt_encodesToDifferentStrings() {
        val a = CommentCursor(createdAt = 100L, commentId = PublicId("cmt_x")).encode()
        val b = CommentCursor(createdAt = 200L, commentId = PublicId("cmt_x")).encode()

        assert(a != b) { "different createdAt must produce different Base64URL strings" }
    }

    // ------------------------------------------------------------------
    // Same createdAt + different commentId
    // ------------------------------------------------------------------

    @Test
    fun sameCreatedAt_differentCommentId_producesDifferentCursor() {
        val a = CommentCursor(createdAt = 100L, commentId = PublicId("cmt_a"))
        val b = CommentCursor(createdAt = 100L, commentId = PublicId("cmt_b"))

        assertFailsWith<AssertionError> { assertEquals(a, b) }
    }

    @Test
    fun sameCreatedAt_differentCommentId_encodesToDifferentStrings() {
        val a = CommentCursor(createdAt = 100L, commentId = PublicId("cmt_a")).encode()
        val b = CommentCursor(createdAt = 100L, commentId = PublicId("cmt_b")).encode()

        assert(a != b) { "different commentId must produce different Base64URL strings" }
    }

    // ------------------------------------------------------------------
    // Zero createdAt edge case
    // ------------------------------------------------------------------

    @Test
    fun zeroCreatedAt_isAccepted() {
        val cursor = CommentCursor(createdAt = 0L, commentId = PublicId("cmt_zero"))
        val decoded = CommentCursor.decode(cursor.encode())
        assertEquals(cursor, decoded)
    }

    // ------------------------------------------------------------------
    // Strict decoding failures
    // ------------------------------------------------------------------

    @Test
    fun decode_whenBlank_throws() {
        assertFailsWith<IllegalArgumentException> { CommentCursor.decode("") }
        assertFailsWith<IllegalArgumentException> { CommentCursor.decode("   ") }
    }

    @Test
    fun decode_whenMalformedBase64_throws() {
        assertFailsWith<IllegalArgumentException> { CommentCursor.decode("!!!") }
    }

    @Test
    fun decode_whenMissingVersion_throws() {
        assertFailsWith<IllegalArgumentException> { CommentCursor.decode(b64("123:cmt_1")) }
    }

    @Test
    fun decode_whenInvalidVersion_throws() {
        assertFailsWith<IllegalArgumentException> { CommentCursor.decode(b64("v2:123:cmt_1")) }
    }

    @Test
    fun decode_whenWrongFieldCount_throws() {
        // Too many fields.
        assertFailsWith<IllegalArgumentException> {
            CommentCursor.decode(b64("v1:123:cmt_1:extra"))
        }
        // Too few fields (createdAt only, no comment id).
        assertFailsWith<IllegalArgumentException> {
            CommentCursor.decode(b64("v1:123"))
        }
        // Trailing colon leaves the id missing.
        assertFailsWith<IllegalArgumentException> {
            CommentCursor.decode(b64("v1:123:"))
        }
    }

    @Test
    fun decode_whenCreatedAtNotALong_throws() {
        assertFailsWith<IllegalArgumentException> { CommentCursor.decode(b64("v1:abc:cmt_1")) }
        // Overflowing Long.
        assertFailsWith<IllegalArgumentException> {
            CommentCursor.decode(b64("v1:99999999999999999999:cmt_1"))
        }
    }

    @Test
    fun decode_whenCreatedAtNegative_throws() {
        assertFailsWith<IllegalArgumentException> { CommentCursor.decode(b64("v1:-5:cmt_1")) }
    }

    @Test
    fun decode_whenCommentIdBlank_throws() {
        assertFailsWith<IllegalArgumentException> { CommentCursor.decode(b64("v1:123:   ")) }
    }

    @Test
    fun decode_whenCommentIdEmpty_throws() {
        assertFailsWith<IllegalArgumentException> { CommentCursor.decode(b64("v1:123:")) }
    }

    // ------------------------------------------------------------------
    // Data class equality
    // ------------------------------------------------------------------

    @Test
    fun dataClassEquality_byCreatedAtAndCommentId() {
        val a = CommentCursor(createdAt = 500L, commentId = PublicId("cmt_same"))
        val b = CommentCursor(createdAt = 500L, commentId = PublicId("cmt_same"))
        assertEquals(a, b)
    }

    @Test
    fun hashCode_consistent() {
        val cursor = CommentCursor(createdAt = 999L, commentId = PublicId("cmt_h"))
        assertEquals(cursor.hashCode(), cursor.hashCode())
    }

    // ------------------------------------------------------------------
    // Realistic round-trip with large timestamps
    // ------------------------------------------------------------------

    @Test
    fun realisticTimestamp_roundTripsCorrectly() {
        val now = System.currentTimeMillis()
        val cursor = CommentCursor(createdAt = now, commentId = PublicId("cmt_realistic"))
        val decoded = CommentCursor.decode(cursor.encode())
        assertEquals(now, decoded.createdAt)
        assertEquals(PublicId("cmt_realistic"), decoded.commentId)
    }

    @Test
    fun maxTimestamp_roundTripsCorrectly() {
        val cursor = CommentCursor(
            createdAt = Long.MAX_VALUE,
            commentId = PublicId("cmt_max")
        )
        val decoded = CommentCursor.decode(cursor.encode())
        assertEquals(Long.MAX_VALUE, decoded.createdAt)
    }
}
