package com.contentplatform.backend.application.fetch

import com.contentplatform.backend.adapter.ExternalAdapter
import com.contentplatform.backend.adapter.ExternalContentInput
import com.contentplatform.backend.adapter.ExternalFetchResult
import com.contentplatform.backend.adapter.ExternalInstanceRef
import com.contentplatform.backend.adapter.FetchCommand
import com.contentplatform.backend.application.author.EnsureAndSaveAuthorUseCase
import com.contentplatform.backend.application.content.AttachTagsToContentUseCase
import com.contentplatform.backend.application.content.FetchAndIngestExternalContentUseCase
import com.contentplatform.backend.application.content.SaveContentWithAssembliesUseCase
import com.contentplatform.backend.application.content.SaveExternalContentUseCase
import com.contentplatform.backend.application.tag.EnsureAndSaveTagUseCase
import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.media.Media
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.InstanceHealthStatus
import com.contentplatform.backend.domain.source.Source
import com.contentplatform.backend.domain.source.SourceType
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.domain.tag.TagOrigin
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.ContentTagRepository
import com.contentplatform.backend.repository.InstanceRepository
import com.contentplatform.backend.repository.MediaRepository
import com.contentplatform.backend.repository.SourceRepository
import com.contentplatform.backend.repository.TagRepository
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

class FetchExternalContentEntryUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fakes (kept inside this file — no extra production file)
    // ------------------------------------------------------------------

    private class FakeInstanceRepository(instance: Instance? = null) : InstanceRepository {
        private val rows = mutableMapOf<String, Instance>()
        var queryCount = 0
            private set

        init {
            instance?.let { rows[it.id.value] = it }
        }

        override fun findById(id: PublicId): Instance? {
            queryCount++
            return rows[id.value]
        }
    }

    private class FakeSourceRepository(source: Source? = null) : SourceRepository {
        private val rows = mutableMapOf<String, Source>()
        var queryCount = 0
            private set

        init {
            source?.let { rows[it.id.value] = it }
        }

        override fun findById(id: PublicId): Source? {
            queryCount++
            return rows[id.value]
        }
    }

    private class FakeAuthorRepository : AuthorRepository {
        val rows = mutableMapOf<String, Author>()

        override fun findById(id: PublicId): Author? = rows[id.value]
        override fun findByInstanceAndSourceAuthorId(instanceId: PublicId, sourceAuthorId: String): Author? =
            rows.values.firstOrNull { it.instanceId == instanceId && it.sourceAuthorId == sourceAuthorId }
        override fun save(author: Author): Author {
            rows[author.id.value] = author
            return author
        }
    }

    private class FakeContentRepository : ContentRepository {
        val rows = mutableMapOf<String, Content>()

        override fun findById(id: PublicId): Content? = rows[id.value]
        override fun findByInstanceAndSourceContentId(instanceId: PublicId, sourceContentId: String): Content? =
            rows.values.firstOrNull { it.instanceId == instanceId && it.sourceContentId == sourceContentId }
        override fun save(content: Content): Content {
            rows[content.id.value] = content
            return content
        }
    }

    private class FakeMediaRepository : MediaRepository {
        val rows = mutableMapOf<String, Media>()

        override fun findById(id: PublicId): Media? = rows[id.value]
        override fun findBySha256(sha256: String): Media? = rows.values.firstOrNull { it.sha256 == sha256 }
        override fun save(media: Media): Media {
            rows[media.id.value] = media
            return media
        }
    }

    private class FakeTagRepository : TagRepository {
        val rows = mutableMapOf<String, Tag>()

        override fun findById(id: PublicId): Tag? = rows[id.value]
        override fun findByInstanceAndSourceTagId(instanceId: PublicId, sourceTagId: String): Tag? =
            rows.values.firstOrNull {
                it.origin == TagOrigin.EXTERNAL && it.instanceId == instanceId && it.sourceTagId == sourceTagId
            }
        override fun findPlatformByName(name: String): Tag? =
            rows.values.firstOrNull { it.origin == TagOrigin.PLATFORM && it.name == name }
        override fun save(tag: Tag): Tag {
            rows[tag.id.value] = tag
            return tag
        }
    }

    /** No-op content_tag fake required by the SaveExternalContentUseCase wiring. */
    private class FakeContentTagRepository : ContentTagRepository {
        override fun attach(contentId: PublicId, tagId: PublicId): Unit =
            attachAll(contentId, listOf(tagId))

        override fun attachAll(contentId: PublicId, tagIds: List<PublicId>) {
            // no-op: association persistence is not under test in this file
        }
    }

    /** Recording adapter: captures the exact FetchCommand the real downstream
     *  use case sent, so tests can assert sourceType / instanceDomain. */
    private class FakeExternalAdapter(
        private val result: ExternalFetchResult = ExternalFetchResult()
    ) : ExternalAdapter {
        var fetchCount = 0
            private set
        var lastCommand: FetchCommand? = null
            private set

        override fun fetch(command: FetchCommand): ExternalFetchResult {
            fetchCount++
            lastCommand = command
            return result
        }
    }

    /** No-op transaction manager (same pattern as the other no-op managers in
     *  this project): runs callbacks inline. */
    private class NoOpTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
            SimpleTransactionStatus()

        override fun commit(status: TransactionStatus) = Unit

        override fun rollback(status: TransactionStatus) = Unit
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private class Harness(
        val instances: FakeInstanceRepository,
        val sources: FakeSourceRepository,
        val contents: FakeContentRepository,
        val adapter: FakeExternalAdapter,
        val useCase: FetchExternalContentEntryUseCase
    )

    /** The real Stage 11B chain is wired underneath (fake repos + recording
     *  adapter), so delegation is verified end-to-end without mocking the
     *  concrete use case. */
    private fun harness(
        instance: Instance? = null,
        source: Source? = null,
        adapterResult: ExternalFetchResult = ExternalFetchResult()
    ): Harness {
        val instances = FakeInstanceRepository(instance)
        val sources = FakeSourceRepository(source)
        val authors = FakeAuthorRepository()
        val contents = FakeContentRepository()
        val media = FakeMediaRepository()
        val tags = FakeTagRepository()
        val contentTags = FakeContentTagRepository()
        val transactionTemplate = TransactionTemplate(NoOpTransactionManager())

        val saveContentWithAssemblies = SaveContentWithAssembliesUseCase(
            contentRepository = contents,
            authorRepository = authors,
            mediaRepository = media,
            transactionTemplate = transactionTemplate
        )
        val saveExternalContent = SaveExternalContentUseCase(
            ensureAndSaveAuthorUseCase = EnsureAndSaveAuthorUseCase(authors),
            ensureAndSaveTagUseCase = EnsureAndSaveTagUseCase(tags),
            attachTagsToContentUseCase = AttachTagsToContentUseCase(contentTags),
            contentRepository = contents,
            mediaRepository = media,
            saveContentWithAssembliesUseCase = saveContentWithAssemblies,
            transactionTemplate = transactionTemplate
        )
        val adapter = FakeExternalAdapter(adapterResult)
        val fetchAndIngest = FetchAndIngestExternalContentUseCase(
            externalAdapter = adapter,
            saveExternalContentUseCase = saveExternalContent
        )
        val useCase = FetchExternalContentEntryUseCase(
            instanceRepository = instances,
            sourceRepository = sources,
            fetchAndIngestExternalContentUseCase = fetchAndIngest
        )
        return Harness(instances, sources, contents, adapter, useCase)
    }

    private fun source(id: String, type: SourceType): Source =
        Source(id = PublicId(id), name = type.name.lowercase(), type = type, enabled = true)

    private fun instance(domain: String, source: Source): Instance =
        Instance(
            id = PublicId("inst_" + domain.replace('.', '_')),
            sourceId = source.id,
            domain = domain,
            displayName = null,
            enabled = true,
            allowIndex = true,
            allowMediaPreview = true,
            allowMediaCache = false,
            allowOriginalAccess = false,
            nsfwPolicy = null,
            healthStatus = InstanceHealthStatus.HEALTHY
        )

    private fun contentInput(instanceDomain: String, sourceContentId: String): ExternalContentInput =
        ExternalContentInput(
            instanceRef = ExternalInstanceRef(instanceDomain),
            sourceContentId = sourceContentId,
            authorRef = "ext-a1",
            text = "hello from $sourceContentId",
            language = "en",
            visibility = ContentVisibility.PUBLIC
        )

    // ------------------------------------------------------------------
    // 1. Instance exists + source exists
    //    -> Source.type resolved, InstanceFetchContext built, downstream called
    // ------------------------------------------------------------------

    @Test
    fun fetchAndIngest_whenInstanceAndSourceExist_resolvesSourceTypeAndDelegates() {
        val source = source("src_mastodon", SourceType.MASTODON)
        val inst = instance("mastodon.social", source)
        val h = harness(
            instance = inst,
            source = source,
            adapterResult = ExternalFetchResult(
                contents = listOf(contentInput(inst.domain, "sc-1"))
            )
        )

        val summary = h.useCase.fetchAndIngest(inst.id)

        // Delegation happened and its summary is returned.
        assertNotNull(summary)
        assertEquals(1, summary.contentsIngested)
        assertEquals(0, summary.mediaAttached)
        assertTrue(summary.unresolvedMediaRefs.isEmpty())

        // The Source was resolved through instance.sourceId.
        assertEquals(1, h.sources.queryCount)

        // SourceType reached the downstream FetchCommand from Source.type.
        assertEquals(SourceType.MASTODON, h.adapter.lastCommand?.sourceType)

        // InstanceFetchContext was built from the found instance: its domain is
        // exactly what the downstream command carried.
        assertEquals("mastodon.social", h.adapter.lastCommand?.instanceDomain)
        assertEquals(1, h.adapter.fetchCount)

        // End-to-end: the real SaveExternalContentUseCase persisted the fetched
        // content under the resolved instance / source identities.
        val saved = h.contents.findByInstanceAndSourceContentId(inst.id, "sc-1")
        assertNotNull(saved)
        assertEquals(inst.id, saved.instanceId)
        assertEquals(source.id, saved.sourceId)
    }

    // ------------------------------------------------------------------
    // 2. Instance missing
    //    -> null returned, source NOT queried, downstream NOT called
    // ------------------------------------------------------------------

    @Test
    fun fetchAndIngest_whenInstanceMissing_returnsNullAndDoesNothingElse() {
        val source = source("src_mastodon", SourceType.MASTODON)
        val h = harness(instance = null, source = source)

        val result = h.useCase.fetchAndIngest(PublicId("inst_absent"))

        assertNull(result)
        assertEquals(1, h.instances.queryCount)
        assertEquals(0, h.sources.queryCount, "source must not be queried when the instance is missing")
        assertEquals(0, h.adapter.fetchCount, "downstream fetch must not run when the instance is missing")
        assertTrue(h.contents.rows.isEmpty())
    }

    // ------------------------------------------------------------------
    // 3. Instance exists but source missing
    //    -> null returned, downstream NOT called
    // ------------------------------------------------------------------

    @Test
    fun fetchAndIngest_whenSourceMissing_returnsNullAndDoesNotFetch() {
        val inst = instance("mastodon.social", source("src_absent", SourceType.MASTODON))
        val h = harness(instance = inst, source = null)

        val result = h.useCase.fetchAndIngest(inst.id)

        assertNull(result)
        assertEquals(1, h.instances.queryCount)
        assertEquals(1, h.sources.queryCount, "the owning source lookup must run once")
        assertEquals(0, h.adapter.fetchCount, "downstream fetch must not run when the source is missing")
        assertTrue(h.contents.rows.isEmpty())
    }

    // ------------------------------------------------------------------
    // 4. sourceType comes from Source.type — never from the caller
    // ------------------------------------------------------------------

    @Test
    fun fetchAndIngest_sourceTypeComesFromSourceType_notFromCaller() {
        // The entry method takes ONLY instanceId — no sourceType parameter. The
        // only possible origin for the type that reaches the adapter is the
        // persisted Source.type. Use a distinctive type to prove the source.
        val source = source("src_pixelfed", SourceType.PIXELFED)
        val inst = instance("px.example", source)
        val h = harness(instance = inst, source = source)

        val summary = h.useCase.fetchAndIngest(inst.id)

        assertNotNull(summary)
        assertEquals(0, summary.contentsIngested)
        assertEquals(SourceType.PIXELFED, h.adapter.lastCommand?.sourceType)
        assertEquals("px.example", h.adapter.lastCommand?.instanceDomain)
        assertEquals(1, h.adapter.fetchCount)
        assertEquals(1, h.sources.queryCount)
    }
}
