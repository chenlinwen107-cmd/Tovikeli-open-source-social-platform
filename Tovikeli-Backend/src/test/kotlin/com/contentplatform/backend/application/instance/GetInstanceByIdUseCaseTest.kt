package com.contentplatform.backend.application.instance

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.InstanceHealthStatus
import com.contentplatform.backend.repository.InstanceRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.jupiter.api.Test

class GetInstanceByIdUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fake InstanceRepository that records the requested id
    // ------------------------------------------------------------------

    private class FakeInstanceRepository : InstanceRepository {
        var stored: Instance? = null
        var lastRequestedId: PublicId? = null
            private set

        override fun findById(id: PublicId): Instance? {
            lastRequestedId = id
            return stored?.takeIf { it.id == id }
        }
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private fun instance(): Instance = Instance(
        id = PublicId("inst_misskey_io"),
        sourceId = PublicId("src_misskey"),
        domain = "misskey.io",
        displayName = null,
        enabled = true,
        allowIndex = true,
        allowMediaPreview = true,
        allowMediaCache = false,
        allowOriginalAccess = false,
        nsfwPolicy = null,
        healthStatus = InstanceHealthStatus.HEALTHY
    )

    // ------------------------------------------------------------------
    // 1. Repository returns Instance -> UseCase returns the same Instance
    // ------------------------------------------------------------------

    @Test
    fun get_whenInstanceExists_returnsSameInstance() {
        val repo = FakeInstanceRepository()
        val stored = instance()
        repo.stored = stored
        val uc = GetInstanceByIdUseCase(repo)

        val result = uc.getById(stored.id)

        assertSame(stored, result, "the repository aggregate must be returned unchanged")
    }

    // ------------------------------------------------------------------
    // 2. Repository returns null -> UseCase returns null
    // ------------------------------------------------------------------

    @Test
    fun get_whenInstanceAbsent_returnsNull() {
        val repo = FakeInstanceRepository()
        val uc = GetInstanceByIdUseCase(repo)

        assertNull(uc.getById(PublicId("inst_not_persisted")))
    }

    // ------------------------------------------------------------------
    // 3. The requested Instance PublicId is passed through correctly
    // ------------------------------------------------------------------

    @Test
    fun get_passesRequestedPublicIdToRepository() {
        val repo = FakeInstanceRepository()
        val uc = GetInstanceByIdUseCase(repo)

        val requested = PublicId("inst_requested_id")
        val result = uc.getById(requested)

        assertEquals(requested, repo.lastRequestedId, "repository must be queried with the requested id")
        assertNull(result)
    }
}
