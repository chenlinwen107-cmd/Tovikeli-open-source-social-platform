package com.contentplatform.backend.database.repository

import com.contentplatform.backend.domain.common.PublicId
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

/**
 * Tag-search unit tests for [TagJdbcRepository.search] (Stage 15-C).
 *
 * No database is involved. [FakeJdbcTemplate] intercepts every query call and
 * emulates the SQL encoded in the repository: a case-insensitive substring
 * match on normalized_name, ordering normalized_name ASC / name ASC / id ASC,
 * and LIMIT <bound>.
 */
class TagJdbcRepositorySearchTest {

    private class FakeJdbcTemplate : JdbcTemplate() {

        val executedSql = mutableListOf<String>()
        val queryArgs = mutableListOf<List<Any?>>()

        /** Candidate tag rows (as if already matching normalized_name). */
        var rows: List<Map<String, Any?>> = emptyList()

        override fun <T> query(sql: String, rowMapper: RowMapper<T>, vararg args: Any?): List<T> {
            executedSql += sql
            queryArgs += args.toList()

            val pattern = (args[0] as String).removePrefix("%").removeSuffix("%")
            val limit = (args[1] as Number).toInt()
            val filtered = rows.filter { row ->
                val normalized = row["normalized_name"] as? String ?: return@filter false
                normalized.contains(pattern, ignoreCase = true)
            }
            val ordered = filtered.sortedWith(
                compareBy<Map<String, Any?>> { it["normalized_name"] as String }
                    .thenBy { it["name"] as String }
                    .thenBy { it["id"] as String }
            )
            return mapRows(ordered.take(limit), rowMapper)
        }

        private fun <T> mapRows(rows: List<Map<String, Any?>>, rowMapper: RowMapper<T>): List<T> {
            if (rows.isEmpty()) return emptyList()
            val rs = resultSetFor(rows)
            val mapped = ArrayList<T>(rows.size)
            var rowNum = 0
            while (rs.next()) {
                mapped.add(rowMapper.mapRow(rs, rowNum))
                rowNum++
            }
            return mapped
        }

        private fun resultSetFor(rows: List<Map<String, Any?>>): ResultSet {
            val cursor = AtomicInteger(-1)
            var wasNull = false
            val loader = TagJdbcRepository::class.java.classLoader
            val proxy = Proxy.newProxyInstance(
                loader,
                arrayOf(ResultSet::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "next" -> cursor.incrementAndGet() < rows.size
                    "wasNull" -> wasNull
                    "getString" -> {
                        val label = args?.get(0) as? String
                        val value = rows.getOrNull(cursor.get())?.get(label)
                        wasNull = value == null
                        value as? String
                    }
                    "getLong" -> {
                        val label = args?.get(0) as? String
                        val value = rows.getOrNull(cursor.get())?.get(label)
                        wasNull = value == null
                        (value as? Number)?.toLong() ?: 0L
                    }
                    else -> defaultFor(method.returnType)
                }
            }
            @Suppress("UNCHECKED_CAST")
            return proxy as ResultSet
        }

        private fun defaultFor(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            else -> null
        }
    }

    private fun tagRow(id: String, name: String, normalizedName: String, origin: String = "PLATFORM"): Map<String, Any?> =
        mapOf(
            "id" to id,
            "name" to name,
            "normalized_name" to normalizedName,
            "origin" to origin,
            "source_tag_id" to null,
            "instance_id" to null,
            "source_id" to null,
            "created_at" to 1L,
            "updated_at" to 1L
        )

    private fun repository(fake: FakeJdbcTemplate): TagJdbcRepository = TagJdbcRepository(fake)

    // ------------------------------------------------------------------
    // 1. SQL shape: normalized_name ILIKE + frozen ordering + LIMIT
    // ------------------------------------------------------------------

    @Test
    fun search_emitsNormalizedNameMatchWithFrozenOrdering() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(tagRow("tg_1", "Blue Archive", "bluearchive"))
        val repo = repository(fake)

        val result = repo.search("blue", 20)

        assertEquals(1, result.size)
        val sql = fake.executedSql.single()
        assertTrue(sql.contains("normalized_name ILIKE ?"), "the match must be on normalized_name only")
        assertTrue(sql.contains("ORDER BY normalized_name ASC, name ASC, id ASC"), "ordering must be frozen")
        assertTrue(sql.contains("LIMIT ?"), "LIMIT must be parameterized")
        assertEquals(listOf<Any?>("%blue%", 20), fake.queryArgs.single(), "pattern + limit must bind in order")
    }

    // ------------------------------------------------------------------
    // 2. Matching + ordering across normalized/name/id
    // ------------------------------------------------------------------

    @Test
    fun search_ordersByNormalizedNameThenNameThenId() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            tagRow("tg_b", "Zebra Tag", "blue-zebra"),
            tagRow("tg_a", "Alpha Tag", "blue"),
            tagRow("tg_c", "Blue Berry", "blueberry")
        )
        val repo = repository(fake)

        val result = repo.search("blue", 20)

        // Lexicographic ASC over the normalized names:
        // "blue" < "blue-zebra" (prefix sorts first) < "blueberry"
        // ('-' 0x2D sorts before 'b' 0x62 at the sixth char).
        assertEquals(
            listOf("tg_a", "tg_b", "tg_c"),
            result.map { it.id.value },
            "normalized_name ASC must decide the order"
        )
    }

    @Test
    fun search_whenNormalizedNamesEqual_ordersByNameThenId() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            tagRow("tg_2", "z-name", "same"),
            tagRow("tg_1", "a-name", "same")
        )
        val repo = repository(fake)

        val result = repo.search("same", 20)

        assertEquals(listOf("tg_1", "tg_2"), result.map { it.id.value }, "name then id break ties")
    }

    // ------------------------------------------------------------------
    // 3. Non-matching rows excluded
    // ------------------------------------------------------------------

    @Test
    fun search_excludesNonMatchingNormalizedNames() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            tagRow("tg_1", "Blue Archive", "bluearchive"),
            tagRow("tg_2", "Kotlin", "kotlin")
        )
        val repo = repository(fake)

        val result = repo.search("blue", 20)

        assertEquals(listOf("tg_1"), result.map { it.id.value }, "tags whose normalizedName does not match must be excluded")
    }

    @Test
    fun search_whenNothingMatches_returnsEmptyList() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(tagRow("tg_1", "Kotlin", "kotlin"))
        val repo = repository(fake)

        val result = repo.search("zzz", 20)

        assertTrue(result.isEmpty())
    }

    // ------------------------------------------------------------------
    // 4. Limit semantics
    // ------------------------------------------------------------------

    @Test
    fun search_appliesRequestedLimit() {
        val fake = FakeJdbcTemplate()
        fake.rows = (1..5).map { tagRow("tg_$it", "name $it", "blue-$it") }
        val repo = repository(fake)

        val result = repo.search("blue", 3)

        assertEquals(listOf<Any?>("%blue%", 3), fake.queryArgs.single())
        assertEquals(3, result.size)
    }

    @Test
    fun search_whenLimitZero_returnsEmptyWithoutQuerying() {
        val fake = FakeJdbcTemplate()
        val repo = repository(fake)

        val result = repo.search("blue", 0)

        assertTrue(result.isEmpty())
        assertTrue(fake.executedSql.isEmpty(), "limit 0 must short-circuit without executing any query")
    }

    @Test
    fun search_rejectsInvalidLimits() {
        val fake = FakeJdbcTemplate()
        val repo = repository(fake)

        assertFailsWith<IllegalArgumentException> { repo.search("blue", -1) }
        assertFailsWith<IllegalArgumentException> { repo.search("blue", 101) }
    }

    // ------------------------------------------------------------------
    // 5. Aggregate reconstruction stays complete
    // ------------------------------------------------------------------

    @Test
    fun search_reconstructsCompleteTagAggregates() {
        val fake = FakeJdbcTemplate()
        fake.rows = listOf(
            mapOf(
                "id" to "tg_ext",
                "name" to "misskey",
                "normalized_name" to "misskey",
                "origin" to "EXTERNAL",
                "source_tag_id" to "ext-9",
                "instance_id" to "inst_misskey_io",
                "source_id" to "src_misskey",
                "created_at" to 42L,
                "updated_at" to 43L
            )
        )
        val repo = repository(fake)

        val result = repo.search("misskey", 20)

        assertEquals(1, result.size)
        val tag = result.single()
        assertEquals(PublicId("tg_ext"), tag.id)
        assertEquals("misskey", tag.name)
        assertEquals("misskey", tag.normalizedName)
        assertEquals("EXTERNAL", tag.origin.name)
        assertEquals("ext-9", tag.sourceTagId)
        assertEquals(42L, tag.createdAt)
    }
}
