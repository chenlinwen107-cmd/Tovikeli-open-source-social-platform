package com.contentplatform.backend.application.feed

import com.contentplatform.backend.application.fetch.ExternalInstanceIngestion
import com.contentplatform.backend.domain.common.PublicId
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

/**
 * Minimal, JVM-local coordinator that turns a "Feed reached its tail" signal
 * into one non-blocking background refill (P9-G3).
 *
 * Responsibilities (and nothing else):
 *  - single-flight: an [AtomicBoolean] gate ensures at most ONE refill runs at a
 *    time inside this JVM. Concurrent tail signals are skipped, not queued.
 *  - background execution: the refill task is handed to the injected [Executor]
 *    (the existing Spring task scheduler in production) and [trigger] returns
 *    immediately, so the Feed request never waits for a source API.
 *  - reuse of the existing ingestion pipeline: each configured instance is fed
 *    through the existing [ExternalInstanceIngestion] port (which delegates to
 *    [com.contentplatform.backend.application.fetch.FetchExternalContentEntryUseCase]).
 *    No source HTTP, parsing or persistence logic is re-implemented here.
 *  - failure isolation: a per-instance ingestion failure is caught and logged;
 *    the guard is released in a `finally`, so a failing refill can never leave
 *    the gate stuck or propagate an exception.
 *
 * Deliberately NOT implemented here: cooldowns, retries, quotas, ranking,
 * recommendations, distributed locks, queues or any persistent refill state.
 * The guard is process-local and intentionally the simplest mechanism that
 * prevents duplicate concurrent refills.
 */
class FeedRefillCoordinator(
    private val ingestion: ExternalInstanceIngestion,
    private val executor: Executor,
    private val instanceIds: List<String>,
    private val enabled: Boolean
) : FeedRefillTrigger {

    private val log = LoggerFactory.getLogger(FeedRefillCoordinator::class.java)

    /** Single-flight gate: true while a refill task is scheduled or running. */
    private val running = AtomicBoolean(false)

    override fun trigger() {
        if (!enabled || instanceIds.isEmpty()) {
            return
        }
        // Another refill is already scheduled/running: skip this signal.
        if (!running.compareAndSet(false, true)) {
            return
        }
        try {
            executor.execute { runRefill() }
        } catch (e: Exception) {
            // Scheduling itself failed. Never propagate to the Feed request, and
            // release the gate so a later tail signal can try again.
            running.set(false)
            log.error("Feed refill could not be scheduled: error={}", e.message, e)
        }
    }

    /**
     * Runs one refill pass over every configured instance, sequentially, and
     * always releases the single-flight gate afterwards. Never throws: one
     * instance failing does not stop the others.
     */
    fun runRefill() {
        try {
            instanceIds.forEach { ingestOne(it) }
        } finally {
            running.set(false)
        }
    }

    /** Ingests one instance and contains any failure. Never throws. */
    private fun ingestOne(instanceId: String) {
        val startedAt = System.currentTimeMillis()
        try {
            val summary = ingestion.ingest(PublicId(instanceId))
            val durationMs = System.currentTimeMillis() - startedAt
            if (summary == null) {
                log.warn(
                    "Feed refill skipped instance={} (not persisted or source missing); durationMs={}",
                    instanceId,
                    durationMs
                )
            } else {
                log.info(
                    "Feed refill finished instance={} processed={} mediaAttached={} unresolvedMediaRefs={} durationMs={}",
                    instanceId,
                    summary.contentsIngested,
                    summary.mediaAttached,
                    summary.unresolvedMediaRefs.size,
                    durationMs
                )
            }
        } catch (e: Exception) {
            log.error(
                "Feed refill failed instance={} error={}",
                instanceId,
                e.message,
                e
            )
        }
    }
}
