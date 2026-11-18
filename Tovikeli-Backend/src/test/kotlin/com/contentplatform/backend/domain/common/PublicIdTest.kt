package com.contentplatform.backend.domain.common

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class PublicIdTest {

    // ------------------------------------------------------------------
    // generic value object behavior
    // ------------------------------------------------------------------

    @Test
    fun valueEquality_sameValueEqual() {
        assertEquals(PublicId("ct_abc"), PublicId("ct_abc"))
        assertEquals(PublicId("any-string"), PublicId("any-string"))
        assertEquals(PublicId("ct_abc").hashCode(), PublicId("ct_abc").hashCode())
    }

    @Test
    fun valueEquality_differentValueNotEqual() {
        assertNotEquals(PublicId("ct_abc"), PublicId("au_abc"))
        assertNotEquals(PublicId("abc"), PublicId("abcd"))
    }

    // ------------------------------------------------------------------
    // no type prefix enforcement
    // ------------------------------------------------------------------

    @Test
    fun acceptsValueWithoutTypePrefix() {
        // PublicId must NOT force ct_/au_/md_/tg_ prefixes because
        // Source / Instance also use PublicId.
        val id = PublicId("some-source-or-instance-id")

        assertEquals("some-source-or-instance-id", id.value)
    }

    @Test
    fun acceptsAnyNonBlankValue() {
        val id = PublicId("inst_1")

        assertEquals("inst_1", id.value)
        assertTrue(id.value.isNotBlank())
    }

    // ------------------------------------------------------------------
    // generic basic validity
    // ------------------------------------------------------------------

    @Test
    fun rejectsBlankValue() {
        assertFailsWith<IllegalArgumentException> {
            PublicId("")
        }
        assertFailsWith<IllegalArgumentException> {
            PublicId("   ")
        }
    }
}
