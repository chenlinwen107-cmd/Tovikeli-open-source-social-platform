package com.contentplatform.backend.config

import com.contentplatform.backend.adapter.ExternalAdapterRouter
import com.contentplatform.backend.adapter.mastodon.MastodonAdapter
import com.contentplatform.backend.adapter.misskey.MisskeyAdapter
import com.contentplatform.backend.application.fetch.ExternalInstanceIngestion
import com.contentplatform.backend.application.fetch.FetchExternalContentEntryUseCase
import com.contentplatform.backend.application.feed.FeedRefillCoordinator
import com.contentplatform.backend.application.feed.FeedRefillTrigger
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * P9-F multi-platform ingestion wiring: Misskey adapter + [ExternalAdapterRouter]
 * + the generic startup trigger.
 *
 * This configuration adds wiring only. It does not change the domain, the
 * persistence semantics, the mapping, or any API contract:
 *
 *   MastodonAdapter ─┐
 *                    ├─> ExternalAdapterRouter (ExternalAdapter) ─> use cases (interface-typed)
 *   MisskeyAdapter  ─┘
 *
 * The router is injected into the existing use-case beans by its concrete type
 * (see [MastodonIngestionConfiguration]), so there is no `ExternalAdapter`
 * injection ambiguity and no `List<ExternalAdapter>` self-injection.
 *
 * Active whenever a datasource is configured, mirroring every other
 * persistence-backed bean, so the no-datasource context still starts.
 */
@Configuration
@ConditionalOnProperty(name = ["spring.datasource.url"])
class ExternalIngestionConfiguration(
    private val environment: Environment
) {

    @Bean
    fun misskeyRestClient(): RestClient {
        val connectTimeoutMs = environment.getProperty(
            "contentplatform.ingestion.misskey.connect-timeout-ms",
            Long::class.java,
            DEFAULT_CONNECT_TIMEOUT_MS
        )
        val readTimeoutMs = environment.getProperty(
            "contentplatform.ingestion.misskey.read-timeout-ms",
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
    fun misskeyAdapter(misskeyRestClient: RestClient): MisskeyAdapter =
        MisskeyAdapter(misskeyRestClient)

    /**
     * Platform dispatch. Concrete adapters are injected explicitly; the router
     * is never part of a `List<ExternalAdapter>` it consumes.
     */
    @Bean
    fun externalAdapterRouter(
        mastodonAdapter: MastodonAdapter,
        misskeyAdapter: MisskeyAdapter
    ): ExternalAdapterRouter =
        ExternalAdapterRouter(
            mastodonAdapter = mastodonAdapter,
            misskeyAdapter = misskeyAdapter
        )

    /**
     * Application-facing ingestion port used by the generic startup trigger.
     * Delegates to the existing PublicId-driven entry use case.
     */
    @Bean
    fun externalInstanceIngestion(
        fetchExternalContentEntryUseCase: FetchExternalContentEntryUseCase
    ): ExternalInstanceIngestion =
        ExternalInstanceIngestion { instanceId ->
            fetchExternalContentEntryUseCase.fetchAndIngest(instanceId)
        }

    /**
     * Feed-refill trigger (P9-G3).
     *
     * Always present while a datasource is configured so the Feed controller has
     * a collaborator, but inert unless explicitly enabled by BOTH:
     *   - contentplatform.ingestion.enabled = true
     *   - contentplatform.ingestion.feed-refill.enabled = true
     * and the shared controlled instance list
     * (contentplatform.ingestion.startup.instance-ids) is non-empty.
     *
     * It reuses the existing ingestion port ([externalInstanceIngestion]) and the
     * existing one-thread scheduler; no new ingestion entry, scheduler or thread
     * lifecycle is introduced. The scheduler is passed as a plain JDK
     * [java.util.concurrent.Executor], so the coordinator stays free of Spring
     * scheduling types.
     */
    @Bean
    fun feedRefillTrigger(
        externalInstanceIngestion: ExternalInstanceIngestion,
        mastodonIngestionTaskScheduler: ThreadPoolTaskScheduler
    ): FeedRefillTrigger =
        FeedRefillCoordinator(
            ingestion = externalInstanceIngestion,
            executor = mastodonIngestionTaskScheduler,
            instanceIds = parseInstanceIds(
                environment.getProperty("contentplatform.ingestion.startup.instance-ids")
            ),
            enabled = environment.getProperty(
                "contentplatform.ingestion.enabled",
                Boolean::class.java,
                false
            ) && environment.getProperty(
                "contentplatform.ingestion.feed-refill.enabled",
                Boolean::class.java,
                false
            )
        )

    /**
     * Generic multi-platform startup trigger.
     *
     * Created only when both flags are explicitly `true`:
     *   - contentplatform.ingestion.enabled
     *   - contentplatform.ingestion.startup.enabled
     *
     * and a non-empty controlled instance-id list is configured through
     * `contentplatform.ingestion.startup.instance-ids` (comma separated). The
     * default (absent / false / empty) means no fetch is attempted.
     *
     * The existing one-thread `mastodonIngestionTaskScheduler` bean is reused
     * off the startup thread; no new scheduler system is introduced.
     */
    @Bean
    @ConditionalOnProperty(
        name = [
            "contentplatform.ingestion.enabled",
            "contentplatform.ingestion.startup.enabled",
        ],
        havingValue = "true"
    )
    fun externalStartupIngestion(
        externalInstanceIngestion: ExternalInstanceIngestion,
        mastodonIngestionTaskScheduler: ThreadPoolTaskScheduler
    ): ExternalStartupIngestion =
        ExternalStartupIngestion(
            ingestion = externalInstanceIngestion,
            taskScheduler = mastodonIngestionTaskScheduler,
            instanceIds = parseInstanceIds(
                environment.getProperty("contentplatform.ingestion.startup.instance-ids")
            ),
            startupDelayMs = environment.getProperty(
                "contentplatform.ingestion.startup.delay-ms",
                Long::class.java,
                DEFAULT_STARTUP_DELAY_MS
            )
        )

    private fun parseInstanceIds(raw: String?): List<String> =
        raw.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000L
        const val DEFAULT_READ_TIMEOUT_MS = 15_000L
        const val DEFAULT_STARTUP_DELAY_MS = 2_000L
    }
}
