package com.contentplatform.backend.application.feed

import com.contentplatform.backend.application.content.ContentIngestSummary
import com.contentplatform.backend.application.fetch.ExternalInstanceIngestion
import com.contentplatform.backend.domain.common.PublicId
import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Deterministic tests for [FeedRefillCoordinator] (P9-G3).
 *
 * A manual [Executor] queues the background task instead of running it on a
 * thread, so scheduling, single-flight and guard release are all asserted
 * synchronously (no `Thread.sleep`, no flaky timing).
 */
class FeedRefillCoordinatorTest {

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    /** Records scheduled tasks and runs them only when the test asks. */
    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        var executions = 0
            private set

        override fun execute(command: Runnable) {
            executions++
            tasks.addLast(command)
        }

        /** Runs the oldest queued task, returning false when none is queued. */
        fun runNext(): Boolean {
            val task = tasks.pollFirst() ?: return false
            task.run()
            return true
        }

        val pending: Int
            get() = tasks.size
    }

    /** An executor that always rejects the task, proving scheduling errors. */
    private class FailingExecutor : Executor {
        var attempts = 0
            private set

        override fun execute(command: Runnable) {
            attempts++
            throw IllegalStateException("executor rejected")
        }
    }

    /** Records each instance it is asked to ingest; can fail selected instances. */
    private class RecordingIngestion(
        private val failFor: Set<String> = emptySet(),
        private val result: (String) -> ContentIngestSummary? = { ContentIngestSummary(1, 0, emptyList()) }
    ) : ExternalInstanceIngestion {
        val calls = mutableListOf<String>()

        override fun ingest(instanceId: PublicId): ContentIngestSummary? {
            calls += instanceId.value
            if (instanceId.value in failFor) {
                throw RuntimeException("boom-${instanceId.value}")
            }
            return result(instanceId.value)
        }
    }

    private fun coordinator(
        ingestion: ExternalInstanceIngestion,
        executor: Executor,
        ids: List<String> = listOf("a", "b"),
        enabled: Boolean = true
    ) = FeedRefillCoordinator(
        ingestion = ingestion,
        executor = executor,
        instanceIds = ids,
        enabled = enabled
    )

    // ------------------------------------------------------------------
    // enable / selection gates
    // ------------------------------------------------------------------

    @Test
    fun trigger_whenDisabled_doesNothing() {
        val executor = ManualExecutor()
        val ingestion = RecordingIngestion()

        coordinator(ingestion, executor, enabled = false).trigger()

        assertEquals(0, executor.executions)
        assertTrue(ingestion.calls.isEmpty())
    }

    @Test
    fun trigger_whenNoInstances_doesNothing() {
        val executor = ManualExecutor()
        val ingestion = RecordingIngestion()

        coordinator(ingestion, executor, ids = emptyList()).trigger()

        assertEquals(0, executor.executions)
        assertTrue(ingestion.calls.isEmpty())
    }

    // ------------------------------------------------------------------
    // non-blocking scheduling
    // ------------------------------------------------------------------

    @Test
    fun trigger_schedulesButDoesNotRunSynchronously() {
        val executor = ManualExecutor()
        val ingestion = RecordingIngestion()
        val coordinator = coordinator(ingestion, executor)

        coordinator.trigger()

        // Trigger returned without running ingestion synchronously.
        assertEquals(1, executor.pending)
        assertTrue(ingestion.calls.isEmpty())

        assertTrue(executor.runNext())
        assertEquals(listOf("a", "b"), ingestion.calls)
    }

    @Test
    fun runRefill_ingestsEveryInstanceSequentially() {
        val ingestion = RecordingIngestion()
        val coordinator = coordinator(ingestion, ManualExecutor())

        coordinator.runRefill()

        assertEquals(listOf("a", "b"), ingestion.calls)
    }

    // ------------------------------------------------------------------
    // single-flight
    // ------------------------------------------------------------------

    @Test
    fun trigger_isSingleFlightWhileScheduled() {
        val executor = ManualExecutor()
        val coordinator = coordinator(RecordingIngestion(), executor)

        coordinator.trigger()
        coordinator.trigger()
        coordinator.trigger()

        assertEquals(1, executor.executions, "only one refill may be scheduled while one is pending")
        assertEquals(1, executor.pending)
    }

    @Test
    fun trigger_afterCompletion_allowsAnotherRefill() {
        val executor = ManualExecutor()
        val coordinator = coordinator(RecordingIngestion(), executor)

        coordinator.trigger()
        assertTrue(executor.runNext())

        coordinator.trigger()

        assertEquals(2, executor.executions, "the guard must be released after a successful refill")
        assertEquals(1, executor.pending)
    }

    @Test
    fun trigger_afterBackgroundFailure_allowsRetry() {
        val executor = ManualExecutor()
        val ingestion = RecordingIngestion(failFor = setOf("a"))
        val coordinator = coordinator(ingestion, executor)

        coordinator.trigger()
        assertTrue(executor.runNext())

        coordinator.trigger()

        assertEquals(2, executor.executions, "the guard must be released even when ingestion failed")
    }

    // ------------------------------------------------------------------
    // failure isolation
    // ------------------------------------------------------------------

    @Test
    fun runRefill_whenOneInstanceFails_continuesOthersAndNeverPropagates() {
        val ingestion = RecordingIngestion(failFor = setOf("a"))
        val coordinator = coordinator(ingestion, ManualExecutor(), ids = listOf("a", "b", "c"))

        coordinator.runRefill()

        assertEquals(listOf("a", "b", "c"), ingestion.calls, "a failing instance must not stop the rest")
    }

    @Test
    fun trigger_whenExecutorThrows_releasesGuardAndNeverPropagates() {
        val executor = FailingExecutor()
        val coordinator = coordinator(RecordingIngestion(), executor)

        coordinator.trigger()
        coordinator.trigger()

        assertEquals(2, executor.attempts, "a scheduling failure must release the guard for a retry")
    }
}
