package com.contentplatform.backend.database.migration

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Structural verification of the V11 migration (P9-F).
 *
 * No live PostgreSQL is available in the offline test environment (consistent
 * with every other test in this project). The migration's guarantees are
 * therefore verified textually: it must add the misskey.art instance under the
 * existing `src_misskey` source with the same production flags as the current
 * public-content instances, and it must be purely additive (no UPDATE/DELETE/
 * ALTER and no change to the existing Misskey instance or to the frozen
 * schema).
 *
 * Flyway itself applies the migration to a real datasource at application
 * startup; this test keeps the committed V11 file aligned with the intended
 * production contract.
 */
class V11MigrationTest {

    /**
     * The SQL with line comments stripped and whitespace runs collapsed, so
     * alignment and prose comments can never make the assertions brittle.
     */
    private val sql: String by lazy {
        val raw = javaClass.classLoader
            .getResourceAsStream("db/migration/V11__seed_misskey_art_instance.sql")
            ?: fail("V11__seed_misskey_art_instance.sql not found on the test classpath")
        raw.bufferedReader().use { it.readText() }
            .replace(Regex("(?m)^\\s*--.*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsFragment(fragment: String) =
        assertTrue(sql.contains(fragment), "V11 must contain: $fragment")

    @Test
    fun addsMisskeyArtUnderTheExistingMisskeySource() {
        containsFragment("INSERT INTO instance (")
        containsFragment("'inst_misskey_art'")
        containsFragment("'src_misskey'")
        containsFragment("'misskey.art'")
    }

    @Test
    fun usesTheSameProductionInstanceFlags() {
        containsFragment("'misskey.art', TRUE, TRUE, TRUE, FALSE, FALSE, NULL, 'HEALTHY'")
    }

    @Test
    fun isAdditiveAndLeavesFrozenDataUntouched() {
        assertTrue(!sql.contains("UPDATE "), "V11 must not update existing rows")
        assertTrue(!sql.contains("DELETE "), "V11 must not delete existing rows")
        assertTrue(!sql.contains("ALTER "), "V11 must not change the schema")
        assertTrue(!sql.contains("DROP "), "V11 must not drop anything")
        assertTrue(
            !sql.contains("'inst_misskey_io'"),
            "the existing inst_misskey_io row must stay untouched"
        )
    }
}
