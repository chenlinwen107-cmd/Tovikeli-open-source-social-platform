package com.contentplatform.backend.domain.interaction

import com.contentplatform.backend.domain.common.PublicId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Domain tests for [InteractionPage] query helper (Stage 19).
 */
class InteractionPageTest {

    private fun sampleInteraction(
        id: String = "int_abc",
        userId: String = "usr_alice",
        contentId: String = "ct_1",
        type: InteractionType = InteractionType.LIKE,
        createdAt: Long = 1_000L
    ): Interaction = Interaction(
        id = PublicId(id),
        userId = PublicId(userId),
        contentId = PublicId(contentId),
        type = type,
        createdAt = createdAt
    )

    // ------------------------------------------------------------------
    // Basic construction
    // ------------------------------------------------------------------

    @Test
    fun page_withItemsAndNextCursor() {
        val items = listOf(sampleInteraction())
        val cursor = InteractionCursor(createdAt = 1_000L, interactionId = PublicId("int_abc"))
        val page = InteractionPage(items = items, nextCursor = cursor)

        assertEquals(1, page.items.size)
        assertNotNull(page.nextCursor)
        assertEquals(cursor, page.nextCursor)
    }

    @Test
    fun page_withEmptyItems_hasNullCursor() {
        val page = InteractionPage(items = emptyList(), nextCursor = null)

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun page_withItemsAndNullCursor_isLastPage() {
        val items = listOf(sampleInteraction(), sampleInteraction(id = "int_def"))
        val page = InteractionPage(items = items, nextCursor = null)

        assertEquals(2, page.items.size)
        assertNull(page.nextCursor)
    }

    // ------------------------------------------------------------------
    // Data class equality
    // ------------------------------------------------------------------

    @Test
    fun dataClassEquality_byItemsAndCursor() {
        val items = listOf(sampleInteraction())
        val cursor = InteractionCursor(createdAt = 1_000L, interactionId = PublicId("int_abc"))

        val a = InteractionPage(items = items, nextCursor = cursor)
        val b = InteractionPage(items = items, nextCursor = cursor)

        assertEquals(a, b)
    }

    @Test
    fun differentItems_produceDifferentPages() {
        val a = InteractionPage(
            items = listOf(sampleInteraction(id = "int_1")),
            nextCursor = null
        )
        val b = InteractionPage(
            items = listOf(sampleInteraction(id = "int_2")),
            nextCursor = null
        )

        assertTrue(a != b)
    }

    @Test
    fun differentCursors_produceDifferentPages() {
        val items = listOf(sampleInteraction())
        val cursorA = InteractionCursor(createdAt = 100L, interactionId = PublicId("int_a"))
        val cursorB = InteractionCursor(createdAt = 200L, interactionId = PublicId("int_b"))

        val a = InteractionPage(items = items, nextCursor = cursorA)
        val b = InteractionPage(items = items, nextCursor = cursorB)

        assertTrue(a != b)
    }

    // ------------------------------------------------------------------
    // Cursor round-trip through page
    // ------------------------------------------------------------------

    @Test
    fun nextCursor_encodesAndDecodesThroughPage() {
        val cursor = InteractionCursor(createdAt = 9_999L, interactionId = PublicId("int_page"))
        val page = InteractionPage(items = emptyList(), nextCursor = cursor)

        val raw = page.nextCursor!!.encode()
        val decoded = InteractionCursor.decode(raw)

        assertEquals(cursor, decoded)
    }

    // ------------------------------------------------------------------
    // Multiple items
    // ------------------------------------------------------------------

    @Test
    fun page_preservesItemOrder() {
        val items = listOf(
            sampleInteraction(id = "int_1", createdAt = 300L),
            sampleInteraction(id = "int_2", createdAt = 200L),
            sampleInteraction(id = "int_3", createdAt = 100L)
        )
        val page = InteractionPage(items = items, nextCursor = null)

        assertEquals("int_1", page.items[0].id.value)
        assertEquals("int_2", page.items[1].id.value)
        assertEquals("int_3", page.items[2].id.value)
    }
}
