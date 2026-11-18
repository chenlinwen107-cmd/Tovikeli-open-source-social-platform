package com.contentplatform.backend.application.author

import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.AuthorRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class EnsureAndSaveAuthorUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fake AuthorRepository
    // ------------------------------------------------------------------

    private class FakeAuthorRepository : AuthorRepository {
        val rows = mutableMapOf<String, Author>()
        var saveCount = 0
            private set

        override fun findById(id: PublicId): Author? = rows[id.value]

        override fun findByInstanceAndSourceAuthorId(
            instanceId: PublicId,
            sourceAuthorId: String
        ): Author? =
            rows.values.firstOrNull { it.instanceId == instanceId && it.sourceAuthorId == sourceAuthorId }

        override fun save(author: Author): Author {
            saveCount++
            rows[author.id.value] = author
            return author
        }
    }

    // ------------------------------------------------------------------
    // ensure() - absent natural key
    // ------------------------------------------------------------------

    @Test
    fun ensure_whenAbsent_createsAndSavesOnce() {
        val repo = FakeAuthorRepository()
        val uc = EnsureAndSaveAuthorUseCase(repo)

        val saved = uc.ensure(
            instanceId = PublicId("inst-1"),
            sourceAuthorId = "auth-42",
            name = "Alice",
            username = "alice"
        )

        assertNotNull(saved)
        assertTrue(saved.id.value.startsWith("au_"), "id should have au_ prefix")
        assertEquals(PublicId("inst-1"), saved.instanceId)
        assertEquals("auth-42", saved.sourceAuthorId)
        assertEquals("alice", saved.username)
        assertEquals(1, repo.saveCount)
        // persisted and findable by natural key
        assertEquals(saved, repo.findByInstanceAndSourceAuthorId(PublicId("inst-1"), "auth-42"))
    }

    @Test
    fun ensure_whenPresent_returnsExistingWithoutSecondSave() {
        val repo = FakeAuthorRepository()
        val uc = EnsureAndSaveAuthorUseCase(repo)
        val first = uc.ensure(PublicId("inst-1"), "auth-42", "Alice", "alice")
        assertEquals(1, repo.saveCount)

        // Second call with different profile data: must NOT overwrite or re-save.
        val again = uc.ensure(
            instanceId = PublicId("inst-1"),
            sourceAuthorId = "auth-42",
            name = "Renamed",
            username = "renamed"
        )

        assertEquals(first, again, "existing aggregate should be returned unchanged")
        assertEquals("Alice", again.name, "no overwrite of existing data")
        assertEquals(1, repo.saveCount, "save() must not be invoked for existing natural key")
    }

    @Test
    fun ensure_differentNaturalKeysProduceDifferentAggregates() {
        val repo = FakeAuthorRepository()
        val uc = EnsureAndSaveAuthorUseCase(repo)

        val a = uc.ensure(PublicId("inst-1"), "auth-1", "A", "a")
        val b = uc.ensure(PublicId("inst-1"), "auth-2", "B", "b")

        assertNotEquals(a.id, b.id)
        assertEquals(2, repo.saveCount)
    }
}
