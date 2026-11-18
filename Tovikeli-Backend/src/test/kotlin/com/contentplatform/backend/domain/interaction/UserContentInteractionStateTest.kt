package com.contentplatform.backend.domain.interaction

import com.contentplatform.backend.domain.common.PublicId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserContentInteractionStateTest {

    private val contentId = PublicId("ct_test")

    private fun interaction(type: InteractionType): Interaction =
        Interaction.create(
            userId = PublicId("au_user"),
            contentId = contentId,
            type = type
        )

    // ------------------------------------------------------------------
    // All false
    // ------------------------------------------------------------------

    @Test
    fun fromInteractions_emptyList_allFalse() {
        val state = UserContentInteractionState.fromInteractions(contentId, emptyList())

        assertEquals(contentId, state.contentId)
        assertFalse(state.like)
        assertFalse(state.favorite)
        assertFalse(state.repost)
        assertFalse(state.bookmark)
    }

    // ------------------------------------------------------------------
    // LIKE only
    // ------------------------------------------------------------------

    @Test
    fun fromInteractions_likeOnly_likeTrue() {
        val state = UserContentInteractionState.fromInteractions(
            contentId,
            listOf(interaction(InteractionType.LIKE))
        )

        assertTrue(state.like)
        assertFalse(state.favorite)
        assertFalse(state.repost)
        assertFalse(state.bookmark)
    }

    // ------------------------------------------------------------------
    // FAVORITE only
    // ------------------------------------------------------------------

    @Test
    fun fromInteractions_favoriteOnly_favoriteTrue() {
        val state = UserContentInteractionState.fromInteractions(
            contentId,
            listOf(interaction(InteractionType.FAVORITE))
        )

        assertFalse(state.like)
        assertTrue(state.favorite)
        assertFalse(state.repost)
        assertFalse(state.bookmark)
    }

    // ------------------------------------------------------------------
    // REPOST only
    // ------------------------------------------------------------------

    @Test
    fun fromInteractions_repostOnly_repostTrue() {
        val state = UserContentInteractionState.fromInteractions(
            contentId,
            listOf(interaction(InteractionType.REPOST))
        )

        assertFalse(state.like)
        assertFalse(state.favorite)
        assertTrue(state.repost)
        assertFalse(state.bookmark)
    }

    // ------------------------------------------------------------------
    // BOOKMARK only
    // ------------------------------------------------------------------

    @Test
    fun fromInteractions_bookmarkOnly_bookmarkTrue() {
        val state = UserContentInteractionState.fromInteractions(
            contentId,
            listOf(interaction(InteractionType.BOOKMARK))
        )

        assertFalse(state.like)
        assertFalse(state.favorite)
        assertFalse(state.repost)
        assertTrue(state.bookmark)
    }

    // ------------------------------------------------------------------
    // Mixed states
    // ------------------------------------------------------------------

    @Test
    fun fromInteractions_mixedLikeAndBookmark_correctFlags() {
        val state = UserContentInteractionState.fromInteractions(
            contentId,
            listOf(
                interaction(InteractionType.LIKE),
                interaction(InteractionType.BOOKMARK)
            )
        )

        assertTrue(state.like)
        assertFalse(state.favorite)
        assertFalse(state.repost)
        assertTrue(state.bookmark)
    }

    // ------------------------------------------------------------------
    // All true
    // ------------------------------------------------------------------

    @Test
    fun fromInteractions_allFourTypes_allTrue() {
        val state = UserContentInteractionState.fromInteractions(
            contentId,
            listOf(
                interaction(InteractionType.LIKE),
                interaction(InteractionType.FAVORITE),
                interaction(InteractionType.REPOST),
                interaction(InteractionType.BOOKMARK)
            )
        )

        assertTrue(state.like)
        assertTrue(state.favorite)
        assertTrue(state.repost)
        assertTrue(state.bookmark)
    }
}
