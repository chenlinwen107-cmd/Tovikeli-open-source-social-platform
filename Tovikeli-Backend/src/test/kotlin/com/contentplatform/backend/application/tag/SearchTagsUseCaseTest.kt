package com.contentplatform.backend.application.tag

import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.repository.TagRepository
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SearchTagsUseCaseTest {

    private class FakeTagRepository : TagRepository {
        var lastQuery: String? = null
            private set
        var lastLimit: Int? = null
            private set
        var searchResult: List<Tag> = emptyList()

        override fun findById(id: com.contentplatform.backend.domain.common.PublicId): Tag? = null
        override fun findByInstanceAndSourceTagId(
            instanceId: com.contentplatform.backend.domain.common.PublicId,
            sourceTagId: String
        ): Tag? = null
        override fun findPlatformByName(name: String): Tag? = null
        override fun save(tag: Tag): Tag = tag

        override fun search(query: String, limit: Int): List<Tag> {
            lastQuery = query
            lastLimit = limit
            return searchResult
        }
    }

    private fun tag(name: String, normalized: String): Tag = Tag.createPlatform(name, normalized)

    // ------------------------------------------------------------------
    // 1. Query + limit passed through unchanged
    // ------------------------------------------------------------------

    @Test
    fun search_passesQueryAndLimitThroughUnchanged() {
        val repo = FakeTagRepository()
        repo.searchResult = listOf(tag("Blue Archive", "bluearchive"))
        val uc = SearchTagsUseCase(repo)

        uc.search("blue", 42)

        assertEquals("blue", repo.lastQuery, "the query must reach the repository unchanged")
        assertEquals(42, repo.lastLimit, "the limit must reach the repository unchanged")
    }

    // ------------------------------------------------------------------
    // 2. Repository list returned unchanged
    // ------------------------------------------------------------------

    @Test
    fun search_whenRepositoryReturnsTags_returnsSameList() {
        val repo = FakeTagRepository()
        val stored = listOf(tag("Blue Archive", "bluearchive"), tag("BA Fanart", "ba-fanart"))
        repo.searchResult = stored
        val uc = SearchTagsUseCase(repo)

        val result = uc.search("blue", 10)

        assertSame(stored, result, "the repository list must be returned unchanged")
    }

    // ------------------------------------------------------------------
    // 3. Empty result stays empty
    // ------------------------------------------------------------------

    @Test
    fun search_whenNothingMatches_returnsEmptyList() {
        val repo = FakeTagRepository()
        val uc = SearchTagsUseCase(repo)

        val result = uc.search("no-such-tag", 10)

        assertTrue(result.isEmpty(), "an empty repository result must remain empty")
        assertEquals(10, repo.lastLimit)
    }
}
