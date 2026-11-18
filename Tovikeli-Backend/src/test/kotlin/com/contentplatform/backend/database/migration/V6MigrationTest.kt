package com.contentplatform.backend.database.migration

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Structural verification of the V6 migration (Stage 17).
 *
 * No live PostgreSQL is available in the offline test environment (consistent
 * with every other test in this project). The migration's guarantees are
 * therefore verified textually against the frozen V6 contract: table shape,
 * PK, the two ON DELETE RESTRICT FKs, the UNIQUE (user_id, content_id, type)
 * constraint, and the two required access-pattern indexes.
 *
 * Flyway itself applies the migration to a real datasource at application
 * startup; this test ensures the committed V6 file still matches the frozen
 * Stage 17 contract so the review can rely on the file as-is.
 */
class V6MigrationTest {

    /** The SQL with all whitespace runs collapsed, so column alignment can
     *  never make the assertions brittle. */
    private val sql: String by lazy {
        val raw = javaClass.classLoader
            .getResourceAsStream("db/migration/V6__content_interaction.sql")
            ?: fail("V6__content_interaction.sql not found on the test classpath")
        raw.bufferedReader().use { it.readText() }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsFragment(fragment: String) =
        assertTrue(sql.contains(fragment), "V6 must contain: $fragment")

    @Test
    fun tableAndColumns_matchTheFrozenContract() {
        containsFragment("CREATE TABLE content_interaction (")
        containsFragment("id TEXT PRIMARY KEY")
        containsFragment("user_id TEXT NOT NULL REFERENCES platform_user (id) ON DELETE RESTRICT")
        containsFragment("content_id TEXT NOT NULL REFERENCES content (id) ON DELETE RESTRICT")
        containsFragment("type TEXT NOT NULL")
        containsFragment("created_at BIGINT NOT NULL")
    }

    @Test
    fun uniqueConstraint_protectsTheInteractionIdentityTriple() {
        containsFragment(
            "CONSTRAINT uq_content_interaction_identity UNIQUE (user_id, content_id, type)"
        )
    }

    @Test
    fun accessIndexes_coverUserAndContentQueryPatterns() {
        containsFragment("ON content_interaction (user_id, created_at DESC)")
        containsFragment("ON content_interaction (content_id, type, created_at DESC)")
    }

    @Test
    fun doesNotIntroduceForbiddenConcepts() {
        assertTrue(!sql.contains("CHECK ("), "type is stored as enum TEXT; no CHECK constraint")
        assertTrue(!sql.contains("comment"), "COMMENT is out of scope for Stage 17")
    }
}
