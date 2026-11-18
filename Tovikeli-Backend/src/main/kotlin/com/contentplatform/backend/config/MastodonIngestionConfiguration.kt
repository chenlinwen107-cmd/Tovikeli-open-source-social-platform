package com.contentplatform.backend.config

import com.contentplatform.backend.adapter.ExternalAdapterRouter
import com.contentplatform.backend.adapter.mastodon.MastodonAdapter
import com.contentplatform.backend.application.author.EnsureAndSaveAuthorUseCase
import com.contentplatform.backend.application.content.FetchAndIngestExternalContentUseCase
import com.contentplatform.backend.application.content.SaveExternalContentUseCase
import com.contentplatform.backend.application.fetch.ExternalAuthorTagFetchUseCase
import com.contentplatform.backend.application.fetch.FetchExternalContentEntryUseCase
import com.contentplatform.backend.application.tag.EnsureAndSaveTagUseCase
import com.contentplatform.backend.repository.InstanceRepository
import com.contentplatform.backend.repository.SourceRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Minimal production wiring for the existing Mastodon ingestion pipeline
 * (P8-B / P8-D).
 *
 * This configuration adds wiring only. It does not change the domain, the
 * persistence semantics, the mapper, or any API contract:
 *
 *   RestClient  ->  MastodonAdapter (ExternalAdapter)
 *                        |
 *                        v
 *   FetchAndIngestExternalContentUseCase  ->  SaveExternalContentUseCase (@Service)
 *                        |
 *                        v
 *   FetchExternalContentEntryUseCase  (PublicId-driven entry, used by the trigger)
 *
 *   ExternalAuthorTagFetchUseCase is also exposed because it is the other
 *   existing adapter-facing orchestration; it remains unused by the trigger.
 *
 * The beans are active whenever a datasource is configured, mirroring every
 * other persistence-backed bean in this project, so the no-datasource context
 * still starts.
 *
 * The trigger is a one-shot STARTUP trigger (P8-D), not a repeating scheduler:
 * see [mastodonStartupIngestion]. It is DISABLED by default and requires three
 * explicit flags. A single [ThreadPoolTaskScheduler] with one thread exists only
 * to run that one delayed, bounded attempt off the startup thread.
 *
 * HTTP: a single Spring [RestClient] built on the JDK-backed
 * [SimpleClientHttpRequestFactory] is used. No second HTTP stack is added and no
 * production domain is baked in as a base URL — the Mastodon adapter builds the
 * absolute URL from the instance domain carried by the fetch context.
 */
@Configuration
@ConditionalOnProperty(name = ["spring.datasource.url"])
class MastodonIngestionConfiguration(
    private val environment: Environment
) {

    @Bean
    fun mastodonRestClient(): RestClient {
        val connectTimeoutMs = environment.getProperty(
            "contentplatform.ingestion.mastodon.connect-timeout-ms",
            Long::class.java,
            DEFAULT_CONNECT_TIMEOUT_MS
        )
        val readTimeoutMs = environment.getProperty(
            "contentplatform.ingestion.mastodon.read-timeout-ms",
            Long::class.java,
            DEFAULT_READ_TIMEOUT_MS
        )
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            setReadTimeout(Duration.ofMillis(readTimeoutMs))
        }
        return RestClient.builder()
            .requestFactory(requestFactory)
            .build()
    }

    @Bean
    fun mastodonAdapter(mastodonRestClient: RestClient): MastodonAdapter =
        MastodonAdapter(mastodonRestClient)

    @Bean
    fun fetchAndIngestExternalContentUseCase(
        externalAdapterRouter: ExternalAdapterRouter,
        saveExternalContentUseCase: SaveExternalContentUseCase
    ): FetchAndIngestExternalContentUseCase =
        FetchAndIngestExternalContentUseCase(externalAdapterRouter, saveExternalContentUseCase)

    @Bean
    fun fetchExternalContentEntryUseCase(
        instanceRepository: InstanceRepository,
        sourceRepository: SourceRepository,
        fetchAndIngestExternalContentUseCase: FetchAndIngestExternalContentUseCase
    ): FetchExternalContentEntryUseCase =
        FetchExternalContentEntryUseCase(
            instanceRepository,
            sourceRepository,
            fetchAndIngestExternalContentUseCase
        )

    @Bean
    fun externalAuthorTagFetchUseCase(
        externalAdapterRouter: ExternalAdapterRouter,
        ensureAndSaveAuthorUseCase: EnsureAndSaveAuthorUseCase,
        ensureAndSaveTagUseCase: EnsureAndSaveTagUseCase
    ): ExternalAuthorTagFetchUseCase =
        ExternalAuthorTagFetchUseCase(
            externalAdapterRouter,
            ensureAndSaveAuthorUseCase,
            ensureAndSaveTagUseCase
        )

    /**
     * Single-thread scheduler used ONLY to run the one-shot startup ingestion
     * after a short delay, off the startup thread. It holds no repeating task.
     * It is a plain Spring bean, so the project stays free of any scheduler
     * framework.
     */
    @Bean
    fun mastodonIngestionTaskScheduler(): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            setPoolSize(1)
            setThreadNamePrefix("mastodon-ingestion-")
            setDaemon(true)
            setWaitForTasksToCompleteOnShutdown(false)
        }

    /**
     * The controlled startup trigger (P8-D).
     *
     * Created only when all three flags are explicitly `true`:
     *   - contentplatform.ingestion.enabled
     *   - contentplatform.ingestion.mastodon.enabled
     *   - contentplatform.ingestion.mastodon.startup-enabled
     *
     * The default (absent / false) means the trigger does not exist at all, so a
     * redeploy never starts fetching on its own.
     */
    @Bean
    @ConditionalOnProperty(
        name = [
            "contentplatform.ingestion.enabled",
            "contentplatform.ingestion.mastodon.enabled",
            "contentplatform.ingestion.mastodon.startup-enabled",
        ],
        havingValue = "true"
    )
    fun mastodonStartupIngestion(
        fetchExternalContentEntryUseCase: FetchExternalContentEntryUseCase,
        mastodonIngestionTaskScheduler: ThreadPoolTaskScheduler
    ): MastodonStartupIngestion =
        MastodonStartupIngestion(
            fetchExternalContentEntryUseCase = fetchExternalContentEntryUseCase,
            taskScheduler = mastodonIngestionTaskScheduler,
            instanceId = environment.getProperty(
                "contentplatform.ingestion.mastodon.instance-id",
                "inst_mastodon_social"
            ),
            startupDelayMs = environment.getProperty(
                "contentplatform.ingestion.mastodon.startup-delay-ms",
                Long::class.java,
                DEFAULT_STARTUP_DELAY_MS
            )
        )

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000L
        const val DEFAULT_READ_TIMEOUT_MS = 15_000L
        const val DEFAULT_STARTUP_DELAY_MS = 2_000L
    }
}
