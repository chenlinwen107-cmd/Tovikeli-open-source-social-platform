package com.contentplatform.backend.config

import com.contentplatform.backend.application.content.ContentIngestSummary
import com.contentplatform.backend.application.fetch.ExternalInstanceIngestion
import com.contentplatform.backend.domain.common.PublicId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/**
 * Behaviour tests for the generic [ExternalStartupIngestion] (P9-F).
 *
 * The application port is faked, so multi-instance iteration, per-instance
 * at-most-once and failure isolation are verified without a database or
 * network. The trigger is not scheduled asynchronously here to keep the test
 * deterministic.
 */
class ExternalStartupIngestionTest {

    private fun summary(contents: Int) = ContentIngestSummary(
        contentsIngested = contents,
        mediaAttached = 0,
        unresolvedMediaRefs = emptyList()
    )

    private fun ingestion(
        calls: MutableList<String>,
        result: (String) -> ContentIngestSummary? = { summary(1) }
    ): ExternalInstanceIngestion = ExternalInstanceIngestion { instanceId ->
        calls += instanceId.value
        result(instanceId.value)
    }

    private fun trigger(
        ingestion: ExternalInstanceIngestion,
        ids: List<String>
    ) = ExternalStartupIngestion(
        ingestion = ingestion,
        taskScheduler = ThreadPoolTaskScheduler(),
        instanceIds = ids,
        startupDelayMs = 0
    )

    @Test
    fun ingestAll_runsEveryConfiguredInstanceSequentially() {
        val calls = mutableListOf<String>()
        val result = trigger(ingestion(calls), listOf("a", "b", "c")).ingestAll()

        assertEquals(listOf("a", "b", "c"), calls)
        assertEquals(3, result.size)
        assertTrue(result.all { it.success })
    }

    @Test
    fun ingestAll_isolatesOneInstanceFailure() {
        val calls = mutableListOf<String>()
        val port = ingestion(calls) { id ->
            if (id == "b") throw IllegalStateException("boom") else summary(1)
        }

        val result = trigger(port, listOf("a", "b", "c")).ingestAll()

        // All three were attempted despite b failing.
        assertEquals(listOf("a", "b", "c"), calls)
        assertTrue(result[0].success)
        assertTrue(!result[1].success)
        assertEquals("boom", result[1].error)
        assertTrue(result[2].success, "the instance after a failure must still run")
    }

    @Test
    fun ingestInstance_isAttemptedAtMostOncePerInstance() {
        val calls = mutableListOf<String>()
        val t = trigger(ingestion(calls), listOf("a"))

        val first = t.ingestInstance("a")
        val second = t.ingestInstance("a")

        assertTrue(first.success)
        assertTrue(!second.success)
        assertEquals("already-attempted", second.error)
        assertEquals(listOf("a"), calls, "the port must be called once")
    }

    @Test
    fun ingestInstance_nullSummaryReportsNotFound() {
        val calls = mutableListOf<String>()
        val t = trigger(ingestion(calls) { null }, listOf("missing"))

        val run = t.ingestInstance("missing")

        assertTrue(!run.success)
        assertEquals("instance-or-source-not-found", run.error)
        assertNull(run.summary)
    }

    @Test
    fun ingestAll_emptyInstanceListIsNoop() {
        val calls = mutableListOf<String>()
        val result = trigger(ingestion(calls), emptyList()).ingestAll()

        assertTrue(result.isEmpty())
        assertTrue(calls.isEmpty())
    }
}
