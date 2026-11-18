package com.contentplatform.backend.domain.interaction

import kotlin.test.Test
import kotlin.test.assertEquals

class InteractionStatisticsTest {

    @Test
    fun zero_hasAllZeros() {
        assertEquals(0L, InteractionStatistics.ZERO.total)
        assertEquals(0L, InteractionStatistics.ZERO.like)
        assertEquals(0L, InteractionStatistics.ZERO.favorite)
        assertEquals(0L, InteractionStatistics.ZERO.repost)
        assertEquals(0L, InteractionStatistics.ZERO.bookmark)
    }

    @Test
    fun fromCounts_emptyMap_returnsAllZeros() {
        val stats = InteractionStatistics.fromCounts(emptyMap())
        assertEquals(InteractionStatistics.ZERO, stats)
    }

    @Test
    fun fromCounts_singleType_returnsCorrectTotal() {
        val counts = mapOf(InteractionType.LIKE to 5L)
        val stats = InteractionStatistics.fromCounts(counts)
        assertEquals(5L, stats.total)
        assertEquals(5L, stats.like)
        assertEquals(0L, stats.favorite)
        assertEquals(0L, stats.repost)
        assertEquals(0L, stats.bookmark)
    }

    @Test
    fun fromCounts_allFourTypes_returnsCorrectTotals() {
        val counts = mapOf(
            InteractionType.LIKE to 3L,
            InteractionType.FAVORITE to 7L,
            InteractionType.REPOST to 2L,
            InteractionType.BOOKMARK to 4L
        )
        val stats = InteractionStatistics.fromCounts(counts)
        assertEquals(16L, stats.total)
        assertEquals(3L, stats.like)
        assertEquals(7L, stats.favorite)
        assertEquals(2L, stats.repost)
        assertEquals(4L, stats.bookmark)
    }

    @Test
    fun fromCounts_missingTypes_defaultToZero() {
        val counts = mapOf(
            InteractionType.LIKE to 10L,
            InteractionType.BOOKMARK to 1L
        )
        val stats = InteractionStatistics.fromCounts(counts)
        assertEquals(11L, stats.total)
        assertEquals(10L, stats.like)
        assertEquals(0L, stats.favorite)
        assertEquals(0L, stats.repost)
        assertEquals(1L, stats.bookmark)
    }
}
