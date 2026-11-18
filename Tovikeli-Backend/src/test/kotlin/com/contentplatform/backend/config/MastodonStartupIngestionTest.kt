package com.contentplatform.backend.config

import com.contentplatform.backend.application.content.ContentIngestSummary
import com.contentplatform.backend.application.fetch.FetchExternalContentEntryUseCase
import com.contentplatform.backend.domain.common.PublicId
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

/**
 * Behaviour tests for [MastodonStartupIngestion] (P8-D).
 *
 * The existing [FetchExternalContentEntryUseCase] is mocked so these tests pin
 * down the trigger's own contract: one bounded run per startup, the configured
 * instance id, contained failures and no propagation into the scheduler.
 */
class MastodonStartupIngestionTest {

    private val instanceId = "inst_mastodon_social"
    private val instancePublicId = PublicId(instanceId)

    private fun mockUseCase(): FetchExternalContentEntryUseCase =
        Mockito.mock(FetchExternalContentEntryUseCase::class.java)

    private fun summary(processed: Int = 1, media: Int = 0): ContentIngestSummary =
        ContentIngestSummary(
            contentsIngested = processed,
            mediaAttached = media,
            unresolvedMediaRefs = emptyList()
        )

    private fun scheduler(): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            setPoolSize(1)
            setThreadNamePrefix("test-mastodon-")
            setDaemon(true)
            initialize()
        }

    @Test
    fun `one startup schedules exactly one bounded run even if the event repeats`() {
        val useCase = mockUseCase()
        Mockito.`when`(useCase.fetchAndIngest(instancePublicId)).thenReturn(summary(processed = 3, media = 2))
        val taskScheduler = scheduler()
        try {
            val startup = MastodonStartupIngestion(useCase, taskScheduler, instanceId, 0)

            startup.onApplicationReady()
            startup.onApplicationReady()

            // Exactly one ingestion, regardless of duplicate readiness callbacks.
            Mockito.verify(useCase, Mockito.timeout(3_000).times(1)).fetchAndIngest(instancePublicId)
        } finally {
            taskScheduler.shutdown()
        }
    }

    @Test
    fun `configured instance id is passed to the ingestion entry point`() {
        val useCase = mockUseCase()
        Mockito.`when`(useCase.fetchAndIngest(instancePublicId)).thenReturn(summary())
        val taskScheduler = scheduler()
        try {
            val startup = MastodonStartupIngestion(useCase, taskScheduler, instanceId, 0)

            startup.onApplicationReady()

            Mockito.verify(useCase, Mockito.timeout(3_000)).fetchAndIngest(instancePublicId)
        } finally {
            taskScheduler.shutdown()
        }
    }

    @Test
    fun `a failing run is contained and a later run still works`() {
        val useCase = mockUseCase()
        Mockito.`when`(useCase.fetchAndIngest(instancePublicId))
            .thenThrow(RuntimeException("mastodon unreachable"))
            .thenReturn(summary())
        val taskScheduler = scheduler()
        try {
            val startup = MastodonStartupIngestion(useCase, taskScheduler, instanceId, 0)

            val failed = startup.ingestOnce()
            assertFalse(failed.success)
            assertNull(failed.summary)
            assertEquals("mastodon unreachable", failed.error)

            // The trigger is not permanently stopped by a failed run.
            val recovered = startup.ingestOnce()
            assertTrue(recovered.success)
        } finally {
            taskScheduler.shutdown()
        }
    }

    @Test
    fun `unknown instance is reported as skipped`() {
        val useCase = mockUseCase()
        Mockito.`when`(useCase.fetchAndIngest(instancePublicId)).thenReturn(null)
        val taskScheduler = scheduler()
        try {
            val startup = MastodonStartupIngestion(useCase, taskScheduler, instanceId, 0)

            val run = startup.ingestOnce()

            assertFalse(run.success)
            assertNull(run.summary)
            assertEquals("instance-or-source-not-found", run.error)
        } finally {
            taskScheduler.shutdown()
        }
    }

    @Test
    fun `blank instance id is contained instead of throwing`() {
        val useCase = mockUseCase()
        val taskScheduler = scheduler()
        try {
            val startup = MastodonStartupIngestion(useCase, taskScheduler, "", 0)

            val run = startup.ingestOnce()

            assertFalse(run.success)
            assertNull(run.summary)
            Mockito.verifyNoInteractions(useCase)
        } finally {
            taskScheduler.shutdown()
        }
    }

    @Test
    fun `scheduler rejection on startup is contained`() {
        val useCase = mockUseCase()
        val taskScheduler = Mockito.mock(TaskScheduler::class.java)
        Mockito.`when`(
            taskScheduler.schedule(
                Mockito.any(Runnable::class.java),
                Mockito.any(Instant::class.java)
            )
        ).thenThrow(IllegalStateException("scheduler closed"))

        val startup = MastodonStartupIngestion(useCase, taskScheduler, instanceId, 0)

        // Must not throw out of the readiness callback.
        startup.onApplicationReady()

        Mockito.verifyNoInteractions(useCase)
    }
}
