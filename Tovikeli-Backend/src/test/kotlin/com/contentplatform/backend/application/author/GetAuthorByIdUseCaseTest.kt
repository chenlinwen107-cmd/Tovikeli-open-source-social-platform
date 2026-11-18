package com.contentplatform.backend.application.author

import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.AuthorRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

class GetAuthorByIdUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fake AuthorRepository that records the requested id
    // ------------------------------------------------------------------

    private class FakeAuthorRepository : AuthorRepository {
        val rows = mutableMapOf<String, Author>()
        var lastRequestedId: PublicId? = null
            private set

        override fun findById(id: PublicId): Author? {
            lastRequestedId = id
            return rows[id.value]
        }

        override fun findByInstanceAndSourceAuthorId(
            instanceId: PublicId,
            sourceAuthorId: String
        ): Author? =
            rows.values.firstOrNull { it.instanceId == instanceId && it.sourceAuthorId == sourceAuthorId }

        override fun save(author: Author): Author {
            rows[author.id.value] = author
            return author
        }
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private fun author(): Author = Author.createFromAdapter(
        instanceId = PublicId("inst_misskey_io"),
        sourceAuthorId = "ext-1",
        name = "Alice",
        username = "alice",
        displayName = null,
        url = null,
        avatar = null,
        bio = null,
        status = null
    )

    // ------------------------------------------------------------------
    // 1. Repository returns Author -> UseCase returns the same Author
    // ------------------------------------------------------------------

    @Test
    fun get_whenAuthorExists_returnsSameAuthor() {
        val repo = FakeAuthorRepository()
        val stored = repo.save(author())
        val uc = GetAuthorByIdUseCase(repo)

        val result = uc.getById(stored.id)

        assertSame(stored, result, "the repository aggregate must be returned unchanged")
    }

    // ------------------------------------------------------------------
    // 2. Repository returns null -> UseCase returns null
    // ------------------------------------------------------------------

    @Test
    fun get_whenAuthorAbsent_returnsNull() {
        val repo = FakeAuthorRepository()
        val uc = GetAuthorByIdUseCase(repo)

        assertNull(uc.getById(PublicId("au_not_persisted")))
    }

    // ------------------------------------------------------------------
    // 3. The requested Author PublicId is passed through correctly
    // ------------------------------------------------------------------

    @Test
    fun get_passesRequestedPublicIdToRepository() {
        val repo = FakeAuthorRepository()
        val uc = GetAuthorByIdUseCase(repo)

        val requested = PublicId("au_requested_id")
        val result = uc.getById(requested)

        assertEquals(requested, repo.lastRequestedId, "repository must be queried with the requested id")
        assertNull(result)
    }
}
