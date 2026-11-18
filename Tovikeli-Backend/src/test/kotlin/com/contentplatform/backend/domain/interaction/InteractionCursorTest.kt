package com.contentplatform.backend.domain.interaction

import com.contentplatform.backend.domain.common.PublicId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Unit tests for [InteractionCursor].
 *
 * The cursor format is frozen as Base64URL("v1:<createdAt>:<interactionId>").
 * Decoding is strict: every malformed shape below must raise
 * IllegalArgumentException and must never be silently repaired.
 */
class InteractionCursorTest {

    private fun b64(logical: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(logical.toByteArray())

    // ------------------------------------------------------------------
    // Round trip
    // ------------------------------------------------------------------

    @Test
    fun encode_thenDecode_returnsOriginalCursor() {
        val cursor = InteractionCursor(
            createdAt = 1_757_000_000_123L,
            interactionId = PublicId("int_abc")
        )

        val raw = cursor.encode()
        val decoded = InteractionCursor.decode(raw)

        assertEquals(cursor, decoded, "decode(encode(cursor)) must restore the cursor exactly")
    }

    @Test
    fun encode_usesUrlSafeBase64WithoutPadding() {
        val raw = InteractionCursor(createdAt = 123L, interactionId = PublicId("int_x")).encode()
        assertFalse(raw.contains('='), "the Base64URL form must not carry padding")
        assertFalse(raw.contains('+'), "the Base64URL form must not use the + alphabet")
        assertFalse(raw.contains('/'), "the Base64URL form must not use the / alphabet")
    }

    @Test
    fun decode_acceptsVersionedLogicalContent() {
        val raw = b64("v1:1757000000123:int_abcdef")
        assertEquals(
            InteractionCursor(createdAt = 1_757_000_000_123L, interactionId = PublicId("int_abcdef")),
            InteractionCursor.decode(raw)
        )
    }

    // ------------------------------------------------------------------
    // Different createdAt
    // ------------------------------------------------------------------

    @Test
    fun differentCreatedAt_producesDifferentCursor() {
        val a = InteractionCursor(createdAt = 100L, interactionId = PublicId("int_x"))
        val b = InteractionCursor(createdAt = 200L, interactionId = PublicId("int_x"))

        assertFailsWith<AssertionError> { assertEquals(a, b) }
    }

    @Test
    fun differentCreatedAt_encodesToDifferentStrings() {
        val a = InteractionCursor(createdAt = 100L, interactionId = PublicId("int_x")).encode()
        val b = InteractionCursor(createdAt = 200L, interactionId = PublicId("int_x")).encode()

        assert(a != b) { "different createdAt must produce different Base64URL strings" }
    }

    // ------------------------------------------------------------------
    // Same createdAt + different interactionId
    // ------------------------------------------------------------------

    @Test
    fun sameCreatedAt_differentInteractionId_producesDifferentCursor() {
        val a = InteractionCursor(createdAt = 100L, interactionId = PublicId("int_a"))
        val b = InteractionCursor(createdAt = 100L, interactionId = PublicId("int_b"))

        assertFailsWith<AssertionError> { assertEquals(a, b) }
    }

    @Test
    fun sameCreatedAt_differentInteractionId_encodesToDifferentStrings() {
        val a = InteractionCursor(createdAt = 100L, interactionId = PublicId("int_a")).encode()
        val b = InteractionCursor(createdAt = 100L, interactionId = PublicId("int_b")).encode()

        assert(a != b) { "different interactionId must produce different Base64URL strings" }
    }

    // ------------------------------------------------------------------
    // Zero createdAt edge case
    // ------------------------------------------------------------------

    @Test
    fun zeroCreatedAt_isAccepted() {
        val cursor = InteractionCursor(createdAt = 0L, interactionId = PublicId("int_zero"))
        val decoded = InteractionCursor.decode(cursor.encode())
        assertEquals(cursor, decoded)
    }

    // ------------------------------------------------------------------
    // Strict decoding failures
    // ------------------------------------------------------------------

    @Test
    fun decode_whenBlank_throws() {
        assertFailsWith<IllegalArgumentException> { InteractionCursor.decode("") }
        assertFailsWith<IllegalArgumentException> { InteractionCursor.decode("   ") }
    }

    @Test
    fun decode_whenMalformedBase64_throws() {
        assertFailsWith<IllegalArgumentException> { InteractionCursor.decode("!!!") }
    }

    @Test
    fun decode_whenMissingVersion_throws() {
        assertFailsWith<IllegalArgumentException> { InteractionCursor.decode(b64("123:int_1")) }
    }

    @Test
    fun decode_whenInvalidVersion_throws() {
        assertFailsWith<IllegalArgumentException> { InteractionCursor.decode(b64("v2:123:int_1")) }
    }

    @Test
    fun decode_whenWrongFieldCount_throws() {
        // Too many fields.
        assertFailsWith<IllegalArgumentException> {
            InteractionCursor.decode(b64("v1:123:int_1:extra"))
        }
        // Too few fields (createdAt only, no interaction id).
        assertFailsWith<IllegalArgumentException> {
            InteractionCursor.decode(b64("v1:123"))
        }
        // Trailing colon leaves the id missing.
        assertFailsWith<IllegalArgumentException> {
            InteractionCursor.decode(b64("v1:123:"))
        }
    }

    @Test
    fun decode_whenCreatedAtNotALong_throws() {
        assertFailsWith<IllegalArgumentException> { InteractionCursor.decode(b64("v1:abc:int_1")) }
        // Overflowing Long.
        assertFailsWith<IllegalArgumentException> {
            InteractionCursor.decode(b64("v1:99999999999999999999:int_1"))
        }
    }

    @Test
    fun decode_whenCreatedAtNegative_throws() {
        assertFailsWith<IllegalArgumentException> { InteractionCursor.decode(b64("v1:-5:int_1")) }
    }

    @Test
    fun decode_whenInteractionIdBlank_throws() {
        assertFailsWith<IllegalArgumentException> { InteractionCursor.decode(b64("v1:123:   ")) }
    }

    @Test
    fun decode_whenInteractionIdEmpty_throws() {
        assertFailsWith<IllegalArgumentException> { InteractionCursor.decode(b64("v1:123:")) }
    }

    // ------------------------------------------------------------------
    // Data class equality
    // ------------------------------------------------------------------

    @Test
    fun dataClassEquality_byCreatedAtAndInteractionId() {
        val a = InteractionCursor(createdAt = 500L, interactionId = PublicId("int_same"))
        val b = InteractionCursor(createdAt = 500L, interactionId = PublicId("int_same"))
        assertEquals(a, b)
    }

    @Test
    fun hashCode_consistent() {
        val cursor = InteractionCursor(createdAt = 999L, interactionId = PublicId("int_h"))
        assertEquals(cursor.hashCode(), cursor.hashCode())
    }

    // ------------------------------------------------------------------
    // Realistic round-trip with large timestamps
    // ------------------------------------------------------------------

    @Test
    fun realisticTimestamp_roundTripsCorrectly() {
        val now = System.currentTimeMillis()
        val cursor = InteractionCursor(createdAt = now, interactionId = PublicId("int_realistic"))
        val decoded = InteractionCursor.decode(cursor.encode())
        assertEquals(now, decoded.createdAt)
        assertEquals(PublicId("int_realistic"), decoded.interactionId)
    }

    @Test
    fun maxTimestamp_roundTripsCorrectly() {
        val cursor = InteractionCursor(
            createdAt = Long.MAX_VALUE,
            interactionId = PublicId("int_max")
        )
        val decoded = InteractionCursor.decode(cursor.encode())
        assertEquals(Long.MAX_VALUE, decoded.createdAt)
    }
}
