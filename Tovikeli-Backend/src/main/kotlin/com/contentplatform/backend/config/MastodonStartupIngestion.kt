package com.contentplatform.backend.config

import com.contentplatform.backend.application.content.ContentIngestSummary
import com.contentplatform.backend.application.fetch.FetchExternalContentEntryUseCase
import com.contentplatform.backend.domain.common.PublicId
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Free-tier-compatible startup trigger for the existing Mastodon ingestion
 * pipeline (P8-D).
 *
 * Why this exists: SnapDeploy Free auto-sleeps the container, so a repeating
 * `@Scheduled` background job is not a reliable production trigger. Instead, one
 * bounded ingestion attempt is made shortly after the application is ready, once
 * per JVM startup:
 *
 *   cold start -> Spring ready -> one bounded public-timeline fetch -> idle
 *
 * Contract:
 *  - NON-BLOCKING: [onApplicationReady] only schedules a one-shot task and
 *    returns immediately, so ApplicationReadyEvent is never held by a network
 *    call.
 *  - AT MOST ONCE PER JVM: an [AtomicBoolean] guarantees a single ingestion even
 *    if the event is delivered more than once.
 *  - BOUNDED: exactly one call to the existing
 *    [FetchExternalContentEntryUseCase], which performs exactly one adapter
 *    fetch. The returned cursor is never followed; there is no loop, no retry
 *    and no parallel execution.
 *  - BEST-EFFORT / NON-FATAL: every failure (network, timeout, malformed
 *    payload, persistence, unknown instance) is caught and logged. It can never
 *    fail application startup or stop the web server.
 *
 * This class adds no business logic; it only delegates to the existing
 * ingestion entry point.
 */
class MastodonStartupIngestion(
    private val fetchExternalContentEntryUseCase: FetchExternalContentEntryUseCase,
    private val taskScheduler: TaskScheduler,
    private val instanceId: String,
    private val startupDelayMs: Long
) {

    private val log = LoggerFactory.getLogger(MastodonStartupIngestion::class.java)

    /** One JVM startup = at most one scheduled ingestion attempt. */
    private val scheduled = AtomicBoolean(false)

    /**
     * Startup hook. Runs the ingestion once, shortly after readiness, on the
     * scheduler's own thread. Never blocks the caller and never throws.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (!scheduled.compareAndSet(false, true)) {
            return
        }
        try {
            val runAt = Instant.now().plusMillis(startupDelayMs.coerceAtLeast(0))
            taskScheduler.schedule({ ingestOnce() }, runAt)
        } catch (e: Exception) {
            // Scheduling must never endanger startup.
            log.error(
                "Mastodon startup ingestion could not be scheduled: instance={} error={}",
                instanceId,
                e.message,
                e
            )
        }
    }

    /**
     * Runs exactly one bounded ingestion for the configured instance and reports
     * the outcome. Never throws.
     */
    fun ingestOnce(): MastodonStartupIngestionRun {
        val startedAt = System.currentTimeMillis()
        return try {
            val summary = fetchExternalContentEntryUseCase.fetchAndIngest(PublicId(instanceId))
            val durationMs = System.currentTimeMillis() - startedAt

            if (summary == null) {
                log.warn(
                    "Mastodon startup ingestion skipped: instance={} is not persisted (or its source is missing); durationMs={}",
                    instanceId,
                    durationMs
                )
                MastodonStartupIngestionRun(
                    success = false,
                    instanceId = instanceId,
                    summary = null,
                    error = "instance-or-source-not-found"
                )
            } else {
                log.info(
                    "Mastodon startup ingestion finished: instance={} processed={} mediaAttached={} unresolvedMediaRefs={} durationMs={} success=true",
                    instanceId,
                    summary.contentsIngested,
                    summary.mediaAttached,
                    summary.unresolvedMediaRefs.size,
                    durationMs
                )
                MastodonStartupIngestionRun(
                    success = true,
                    instanceId = instanceId,
                    summary = summary,
                    error = null
                )
            }
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startedAt
            // Contains the failure so start-up and the web server stay healthy.
            log.error(
                "Mastodon startup ingestion failed: instance={} durationMs={} error={}",
                instanceId,
                durationMs,
                e.message,
                e
            )
            MastodonStartupIngestionRun(
                success = false,
                instanceId = instanceId,
                summary = null,
                error = e.message
            )
        }
    }
}

/**
 * Outcome of one [MastodonStartupIngestion.ingestOnce] call.
 *
 * [summary] is the existing aggregation returned by the ingestion pipeline (or
 * null when nothing was fetched). No new counters are invented here.
 */
data class MastodonStartupIngestionRun(
    val success: Boolean,
    val instanceId: String,
    val summary: ContentIngestSummary?,
    val error: String?
)
