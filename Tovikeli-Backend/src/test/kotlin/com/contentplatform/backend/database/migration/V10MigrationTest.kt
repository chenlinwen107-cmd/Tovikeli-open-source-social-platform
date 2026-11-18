package com.contentplatform.backend.database.migration

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Structural verification of the V10 migration (P8-F).
 *
 * No live PostgreSQL is available in the offline test environment (consistent
 * with every other test in this project). The migration's guarantees are
 * therefore verified textually: it must add the mstdn.social instance under the
 * existing `src_mastodon` source with the same production flags as the current
 * Mastodon instance, and it must be purely additive (no UPDATE/DELETE/ALTER and
 * no change to the existing `inst_mastodon_social` row).
 *
 * Flyway itself applies the migration to a real datasource at application
 * startup; this test keeps the committed V10 file aligned with the intended
 * production contract.
 */
class V10MigrationTest {

    /**
     * The SQL with line comments stripped and whitespace runs collapsed, so
     * alignment and prose comments can never make the assertions brittle.
     */
    private val sql: String by lazy {
        val raw = javaClass.classLoader
            .getResourceAsStream("db/migration/V10__seed_mstdn_social_instance.sql")
            ?: fail("V10__seed_mstdn_social_instance.sql not found on the test classpath")
        raw.bufferedReader().use { it.readText() }
            .replace(Regex("(?m)^\\s*--.*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsFragment(fragment: String) =
        assertTrue(sql.contains(fragment), "V10 must contain: $fragment")

    @Test
    fun addsTheMstdnSocialInstanceUnderTheExistingMastodonSource() {
        containsFragment("INSERT INTO instance (")
        containsFragment("'inst_mastodon_mstdn_social'")
        containsFragment("'src_mastodon'")
        containsFragment("'mstdn.social'")
    }

    @Test
    fun usesTheSameProductionMastodonInstanceFlags() {
        containsFragment("'mstdn.social', TRUE, TRUE, TRUE, FALSE, FALSE, NULL, 'HEALTHY'")
    }

    @Test
    fun isAdditiveAndLeavesFrozenDataUntouched() {
        assertTrue(!sql.contains("UPDATE "), "V10 must not update existing rows")
        assertTrue(!sql.contains("DELETE "), "V10 must not delete existing rows")
        assertTrue(!sql.contains("ALTER "), "V10 must not change the schema")
        assertTrue(!sql.contains("DROP "), "V10 must not drop anything")
        assertTrue(
            !sql.contains("'inst_mastodon_social'"),
            "the existing inst_mastodon_social row must stay untouched"
        )
    }
}
