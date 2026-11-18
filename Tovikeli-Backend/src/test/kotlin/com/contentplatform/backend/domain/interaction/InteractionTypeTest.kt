package com.contentplatform.backend.domain.interaction

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Domain tests for the [InteractionType] enum (Stage 17).
 *
 * The frozen Stage 17 contract defines exactly LIKE / FAVORITE / REPOST /
 * BOOKMARK. COMMENT and every other interaction concept are out of scope and
 * must NOT appear in this enum (the legacy PlatformInteractionType with
 * COMMENT is a separate, frozen concept).
 */
class InteractionTypeTest {

    @Test
    fun entries_areExactlyTheFourFrozenStage17Types() {
        assertEquals(
            listOf("LIKE", "FAVORITE", "REPOST", "BOOKMARK"),
            InteractionType.entries.map { it.name }
        )
    }
}
