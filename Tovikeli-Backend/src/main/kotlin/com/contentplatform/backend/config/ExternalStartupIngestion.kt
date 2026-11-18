package com.contentplatform.backend.config

import com.contentplatform.backend.application.content.ContentIngestSummary
import com.contentplatform.backend.application.fetch.ExternalInstanceIngestion
import com.contentplatform.backend.domain.common.PublicId
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Generic, multi-platform startup trigger (P9-F).
 *
 * Why this exists: the Mastodon-only [MastodonStartupIngestion] handles a single
 * instance. P9-F introduces a controlled INSTANCE LIST so several whitelisted
 * instances (e.g. misskey.art) can be warmed once per JVM start without building
 * a resident scheduler / queue / worker system.
 *
 * Contract:
 *  - NON-BLOCKING: [onApplicationReady] only schedules one one-shot task and
 *    returns immediately, so ApplicationReadyEvent is never held by a network
 *    call.
 *  - AT MOST ONCE PER JVM PER INSTANCE: one [AtomicBoolean] guards the scheduled
 *    run and a per-instance [ConcurrentHashMap] set guards each instance, so an
 *    instance is attempted at most once even if the trigger fires twice.
 *  - BOUNDED: each instance performs exactly one fetch through the existing
 *    entry pipeline. There is no loop over cursors, no retry and no parallel
 *    execution (instances run sequentially).
 *  - FAILURE-ISOLATED: one instance failing (network / parse / persistence /
 *    unknown instance) is caught and reported; it never stops the remaining
 *    instances and never fails application startup.
 *
 * This class adds no business logic; it only iterates the configured instance
 * ids and delegates to the existing ingestion entry point.
 */
class ExternalStartupIngestion(
    private val ingestion: ExternalInstanceIngestion,
    private val taskScheduler: TaskScheduler,
    private val instanceIds: List<String>,
    private val startupDelayMs: Long
) {

    private val log = LoggerFactory.getLogger(ExternalStartupIngestion::class.java)

    /** One JVM startup = at most one scheduled run. */
    private val scheduled = AtomicBoolean(false)

    /** Per-instance at-most-once guard (defensive, independent of scheduling). */
    private val attempted = ConcurrentHashMap.newKeySet<String>()

    /**
     * Startup hook. Schedules one bounded, sequential run shortly after
     * readiness. Never blocks the caller and never throws.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (instanceIds.isEmpty()) {
            return
        }
        if (!scheduled.compareAndSet(false, true)) {
            return
        }
        try {
            val runAt = Instant.now().plusMillis(startupDelayMs.coerceAtLeast(0))
            taskScheduler.schedule({ ingestAll() }, runAt)
        } catch (e: Exception) {
            // Scheduling must never endanger startup.
            log.error("External startup ingestion could not be scheduled: error={}", e.message, e)
        }
    }

    /**
     * Runs every configured instance sequentially. A failure of one instance is
     * contained and does not abort the others.
     */
    fun ingestAll(): List<ExternalStartupIngestionRun> =
        instanceIds.map { ingestInstance(it) }

    /**
     * Runs exactly one bounded ingestion for a single instance and reports the
     * outcome. Never throws. An instance is attempted at most once per JVM.
     */
    fun ingestInstance(instanceId: String): ExternalStartupIngestionRun {
        if (!attempted.add(instanceId)) {
            return ExternalStartupIngestionRun(
                success = false,
                instanceId = instanceId,
                summary = null,
                error = "already-attempted"
            )
        }

        val startedAt = System.currentTimeMillis()
        return try {
            val summary = ingestion.ingest(PublicId(instanceId))
            val durationMs = System.currentTimeMillis() - startedAt

            if (summary == null) {
                log.warn(
                    "External startup ingestion skipped: instance={} is not persisted (or its source is missing); durationMs={}",
                    instanceId,
                    durationMs
                )
                ExternalStartupIngestionRun(
                    success = false,
                    instanceId = instanceId,
                    summary = null,
                    error = "instance-or-source-not-found"
                )
            } else {
                log.info(
                    "External startup ingestion finished: instance={} processed={} mediaAttached={} unresolvedMediaRefs={} durationMs={} success=true",
                    instanceId,
                    summary.contentsIngested,
                    summary.mediaAttached,
                    summary.unresolvedMediaRefs.size,
                    durationMs
                )
                ExternalStartupIngestionRun(
                    success = true,
                    instanceId = instanceId,
                    summary = summary,
                    error = null
                )
            }
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startedAt
            // Contains the failure so the remaining instances and the web server
            // stay healthy.
            log.error(
                "External startup ingestion failed: instance={} durationMs={} error={}",
                instanceId,
                durationMs,
                e.message,
                e
            )
            ExternalStartupIngestionRun(
                success = false,
                instanceId = instanceId,
                summary = null,
                error = e.message
            )
        }
    }
}

/**
 * Outcome of one [ExternalStartupIngestion.ingestInstance] call.
 *
 * [summary] is the existing aggregation returned by the ingestion pipeline (or
 * null when nothing was fetched). No new counters are invented here.
 */
data class ExternalStartupIngestionRun(
    val success: Boolean,
    val instanceId: String,
    val summary: ContentIngestSummary?,
    val error: String?
)
