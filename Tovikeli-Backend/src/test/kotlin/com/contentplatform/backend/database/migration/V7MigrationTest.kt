package com.contentplatform.backend.database.migration

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Structural verification of the V7 migration (Stage 18).
 *
 * No live PostgreSQL is available in the offline test environment.
 * The migration's guarantees are therefore verified textually against
 * the frozen V7 contract: table shape, primary key, three ON DELETE
 * RESTRICT foreign keys, required NOT NULL columns, and the three
 * required access-pattern indexes.
 */
class V7MigrationTest {

    /** The SQL with all whitespace runs collapsed. */
    private val sql: String by lazy {
        val raw = javaClass.classLoader
            .getResourceAsStream("db/migration/V7__content_comment.sql")
            ?: fail("V7__content_comment.sql not found on the test classpath")

        raw.bufferedReader().use { it.readText() }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsFragment(fragment: String) =
        assertTrue(sql.contains(fragment), "V7 must contain: $fragment")

    @Test
    fun tableAndColumns_matchTheFrozenContract() {
        containsFragment("CREATE TABLE content_comment (")
        containsFragment("id TEXT PRIMARY KEY")
        containsFragment(
            "user_id TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT"
        )
        containsFragment(
            "content_id TEXT NOT NULL REFERENCES content (id) ON DELETE RESTRICT"
        )
        containsFragment(
            "parent_comment_id TEXT REFERENCES content_comment (id) ON DELETE RESTRICT"
        )
        containsFragment("text TEXT NOT NULL")
        containsFragment("status TEXT NOT NULL")
        containsFragment("created_at BIGINT NOT NULL")
        containsFragment("updated_at BIGINT NOT NULL")
    }

    @Test
    fun accessIndexes_coverContentParentAndUserQueryPatterns() {
        containsFragment(
            "ON content_comment (content_id, created_at ASC, id ASC)"
        )
        containsFragment(
            "ON content_comment (parent_comment_id, created_at ASC, id ASC)"
        )
        containsFragment(
            "ON content_comment (user_id, created_at DESC)"
        )
    }

    @Test
    fun doesNotIntroduceForbiddenConcepts() {
        assertTrue(!sql.contains("CHECK ("), "V7 must not introduce CHECK constraints")
        assertTrue(!sql.contains("DELETE FROM"), "Comment deletion is soft-delete; no physical DELETE migration")
        assertTrue(!sql.contains("interaction"), "V7 must not couple Comment to Interaction")
    }
}
