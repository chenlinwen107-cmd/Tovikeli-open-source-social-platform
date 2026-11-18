package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.ContentTagRepository
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AttachTagsToContentUseCase] (Stage 15-B).
 *
 * The use case owns ONLY the Content <-> Tag association step: it receives an
 * already-persisted content id and already-resolved tag ids, collapses input
 * duplicates, and delegates exactly one batched attachAll per non-empty call.
 * It never creates a Content and never creates/ensures a Tag (that is
 * EnsureAndSaveTagUseCase's job) — both boundaries are asserted here by
 * construction (the fake only implements [ContentTagRepository]).
 */
class AttachTagsToContentUseCaseTest {

    private class RecordingContentTagRepository : ContentTagRepository {
        val attachAllCalls = mutableListOf<Pair<PublicId, List<PublicId>>>()
        var attachSingleCalls = 0
            private set

        override fun attach(contentId: PublicId, tagId: PublicId): Unit {
            attachSingleCalls++
            attachAll(contentId, listOf(tagId))
        }

        override fun attachAll(contentId: PublicId, tagIds: List<PublicId>) {
            attachAllCalls += contentId to tagIds
        }
    }

    private fun repository() = RecordingContentTagRepository()
    private fun useCase(repo: RecordingContentTagRepository) = AttachTagsToContentUseCase(repo)

    private val contentA = PublicId("ct_a")
    private val contentB = PublicId("ct_b")
    private val tag1 = PublicId("tg_1")
    private val tag2 = PublicId("tg_2")
    private val tag3 = PublicId("tg_3")

    // ------------------------------------------------------------------
    // 1. One content + one tag -> one batched attachAll
    // ------------------------------------------------------------------

    @Test
    fun attach_oneContentOneTag_delegatesSingleBatchedAttachAll() {
        val repo = repository()
        val uc = useCase(repo)

        uc.attach(contentA, listOf(tag1))

        assertEquals(1, repo.attachAllCalls.size, "exactly one attachAll must be issued")
        assertEquals(contentA to listOf(tag1), repo.attachAllCalls.single())
        assertEquals(0, repo.attachSingleCalls, "the use case must use the batched path only")
    }

    // ------------------------------------------------------------------
    // 2. One content + multiple tags -> single batched attachAll
    // ------------------------------------------------------------------

    @Test
    fun attach_oneContentMultipleTags_delegatesSingleBatchedAttachAll() {
        val repo = repository()
        val uc = useCase(repo)

        uc.attach(contentA, listOf(tag1, tag2, tag3))

        assertEquals(1, repo.attachAllCalls.size, "multiple tags must be one batched call (no N+1)")
        assertEquals(contentA to listOf(tag1, tag2, tag3), repo.attachAllCalls.single())
    }

    // ------------------------------------------------------------------
    // 3. Duplicate tag ids are de-duplicated at the use case layer
    // ------------------------------------------------------------------

    @Test
    fun attach_duplicateTagIds_areCollapsedBeforeDelegation() {
        val repo = repository()
        val uc = useCase(repo)

        uc.attach(contentA, listOf(tag1, tag1, tag2, tag1))

        assertEquals(1, repo.attachAllCalls.size)
        assertEquals(
            contentA to listOf(tag1, tag2),
            repo.attachAllCalls.single(),
            "duplicate tag ids in the input must be collapsed"
        )
    }

    // ------------------------------------------------------------------
    // 4. Empty tag list performs no repository call at all
    // ------------------------------------------------------------------

    @Test
    fun attach_emptyTagList_doesNotInvokeRepository() {
        val repo = repository()
        val uc = useCase(repo)

        uc.attach(contentA, emptyList())

        assertTrue(repo.attachAllCalls.isEmpty(), "no attachAll may be issued for an empty tag list")
        assertEquals(0, repo.attachSingleCalls)
    }

    // ------------------------------------------------------------------
    // 5. Multiple contents never share one call; each gets its own attachAll
    // ------------------------------------------------------------------

    @Test
    fun attach_twoContents_eachGetsItsOwnBatchedAttachAll() {
        val repo = repository()
        val uc = useCase(repo)

        uc.attach(contentA, listOf(tag1))
        uc.attach(contentB, listOf(tag2, tag3))

        assertEquals(2, repo.attachAllCalls.size, "each content must get exactly one batched call")
        assertEquals(contentA to listOf(tag1), repo.attachAllCalls[0])
        assertEquals(contentB to listOf(tag2, tag3), repo.attachAllCalls[1])
    }
}
