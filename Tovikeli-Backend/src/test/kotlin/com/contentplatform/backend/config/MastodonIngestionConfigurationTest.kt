package com.contentplatform.backend.config

import com.contentplatform.backend.adapter.mastodon.MastodonAdapter
import com.contentplatform.backend.application.author.EnsureAndSaveAuthorUseCase
import com.contentplatform.backend.application.content.FetchAndIngestExternalContentUseCase
import com.contentplatform.backend.application.content.SaveExternalContentUseCase
import com.contentplatform.backend.application.fetch.ExternalAuthorTagFetchUseCase
import com.contentplatform.backend.application.fetch.FetchExternalContentEntryUseCase
import com.contentplatform.backend.application.tag.EnsureAndSaveTagUseCase
import com.contentplatform.backend.repository.InstanceRepository
import com.contentplatform.backend.repository.SourceRepository
import java.time.Duration
import java.util.function.Supplier
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.client.RestClient

/**
 * Wiring + activation tests for [MastodonIngestionConfiguration] (P8-B/P8-D).
 *
 * Uses [ApplicationContextRunner] so the ingestion wiring can be resolved with
 * mocked persistence collaborators without a database. The full application
 * context (and its Flyway startup) is deliberately not used here, because a
 * datasource URL in a real context would require a reachable database.
 */
class MastodonIngestionConfigurationTest {

    private val datasource = "spring.datasource.url=jdbc:postgresql://localhost:5432/unused_in_wiring_test"

    private val master = "contentplatform.ingestion.enabled=true"
    private val mastodon = "contentplatform.ingestion.mastodon.enabled=true"
    private val startup = "contentplatform.ingestion.mastodon.startup-enabled=true"

    private fun runner(): ApplicationContextRunner = ApplicationContextRunner()
        .withUserConfiguration(MastodonIngestionConfiguration::class.java)
        .withUserConfiguration(ExternalIngestionConfiguration::class.java)
        .withBean(
            SaveExternalContentUseCase::class.java,
            Supplier { Mockito.mock(SaveExternalContentUseCase::class.java) }
        )
        .withBean(
            InstanceRepository::class.java,
            Supplier { Mockito.mock(InstanceRepository::class.java) }
        )
        .withBean(
            SourceRepository::class.java,
            Supplier { Mockito.mock(SourceRepository::class.java) }
        )
        .withBean(
            EnsureAndSaveAuthorUseCase::class.java,
            Supplier { Mockito.mock(EnsureAndSaveAuthorUseCase::class.java) }
        )
        .withBean(
            EnsureAndSaveTagUseCase::class.java,
            Supplier { Mockito.mock(EnsureAndSaveTagUseCase::class.java) }
        )

    @Test
    fun `all ingestion beans resolve when a datasource is configured`() {
        runner().withPropertyValues(datasource).run { context ->
            assertTrue(
                context.getBeansOfType(RestClient::class.java).isNotEmpty(),
                "a RestClient bean must be provided"
            )
            assertTrue(
                context.getBeansOfType(MastodonAdapter::class.java).isNotEmpty(),
                "MastodonAdapter must be wired"
            )
            assertTrue(
                context.getBeansOfType(FetchAndIngestExternalContentUseCase::class.java).isNotEmpty(),
                "FetchAndIngestExternalContentUseCase must be wired"
            )
            assertTrue(
                context.getBeansOfType(FetchExternalContentEntryUseCase::class.java).isNotEmpty(),
                "FetchExternalContentEntryUseCase must be wired"
            )
            assertTrue(
                context.getBeansOfType(ExternalAuthorTagFetchUseCase::class.java).isNotEmpty(),
                "ExternalAuthorTagFetchUseCase must be wired"
            )
            assertTrue(
                context.getBeansOfType(ThreadPoolTaskScheduler::class.java).isNotEmpty(),
                "the one-shot TaskScheduler must be wired"
            )
        }
    }

    @Test
    fun `ingestion wiring is absent when no datasource is configured`() {
        runner().run { context ->
            assertFalse(
                context.getBeansOfType(MastodonAdapter::class.java).isNotEmpty(),
                "no ingestion beans may exist without a datasource"
            )
            assertFalse(context.containsBean("mastodonStartupIngestion"))
        }
    }

    @Test
    fun `startup trigger is disabled by default`() {
        runner().withPropertyValues(datasource).run { context ->
            assertFalse(
                context.containsBean("mastodonStartupIngestion"),
                "the startup trigger must be absent unless explicitly enabled"
            )
        }
    }

    @Test
    fun `startup trigger stays disabled when any single flag is missing`() {
        val enablingCombinations = listOf(
            listOf(master),
            listOf(mastodon),
            listOf(startup),
            listOf(master, mastodon),
            listOf(master, startup),
            listOf(mastodon, startup),
        )
        for (props in enablingCombinations) {
            runner()
                .withPropertyValues(datasource, *props.toTypedArray())
                .run { context ->
                    assertFalse(
                        context.containsBean("mastodonStartupIngestion"),
                        "the startup trigger must be absent for $props"
                    )
                }
        }
    }

    @Test
    fun `startup trigger is created only when all three flags are enabled`() {
        runner()
            .withPropertyValues(datasource, master, mastodon, startup)
            .run { context ->
                assertTrue(
                    context.containsBean("mastodonStartupIngestion"),
                    "the startup trigger must exist when explicitly enabled"
                )
            }
    }

    @Test
    fun `publishing ApplicationReadyEvent never fails the context`() {
        runner()
            .withPropertyValues(datasource, master, mastodon, startup)
            .run { context ->
                val application = Mockito.mock(SpringApplication::class.java)
                repeat(2) {
                    context.publishEvent(
                        ApplicationReadyEvent(application, emptyArray(), context, Duration.ZERO)
                    )
                }
                assertTrue(context.isActive, "the context must remain healthy after startup events")
            }
    }
}
