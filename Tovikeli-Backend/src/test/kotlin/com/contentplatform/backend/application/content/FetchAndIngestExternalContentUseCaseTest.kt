package com.contentplatform.backend.application.content

import com.contentplatform.backend.adapter.ExternalAdapter
import com.contentplatform.backend.adapter.ExternalContentInput
import com.contentplatform.backend.adapter.ExternalFetchResult
import com.contentplatform.backend.adapter.ExternalInstanceRef
import com.contentplatform.backend.adapter.ExternalMediaInput
import com.contentplatform.backend.adapter.ExternalTagInput
import com.contentplatform.backend.adapter.FetchCommand
import com.contentplatform.backend.application.author.EnsureAndSaveAuthorUseCase
import com.contentplatform.backend.application.fetch.InstanceFetchContext
import com.contentplatform.backend.application.tag.EnsureAndSaveTagUseCase
import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.media.Media
import com.contentplatform.backend.domain.media.MediaStatus
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.InstanceHealthStatus
import com.contentplatform.backend.domain.source.SourceType
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.domain.tag.TagOrigin
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.ContentTagRepository
import com.contentplatform.backend.repository.MediaRepository
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

class FetchAndIngestExternalContentUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fakes (kept inside this file — no extra production file)
    // ------------------------------------------------------------------

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
        var saveCount = 0
            private set

        override fun findById(id: PublicId): Content? = rows[id.value]
        override fun findByInstanceAndSourceContentId(instanceId: PublicId, sourceContentId: String): Content? =
            rows.values.firstOrNull { it.instanceId == instanceId && it.sourceContentId == sourceContentId }
        override fun save(content: Content): Content {
            saveCount++
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

    /** Records content_tag associations; counts attachAll calls for batch checks. */
    private class FakeContentTagRepository : ContentTagRepository {
        val associations = mutableMapOf<String, MutableSet<String>>()
        var attachAllCalls = 0
            private set

        override fun attach(contentId: PublicId, tagId: PublicId): Unit =
            attachAll(contentId, listOf(tagId))

        override fun attachAll(contentId: PublicId, tagIds: List<PublicId>) {
            if (tagIds.isEmpty()) return
            attachAllCalls++
            associations.getOrPut(contentId.value) { mutableSetOf() }
                .addAll(tagIds.map { it.value })
        }

        fun tagsOf(contentId: PublicId): Set<String> =
            associations[contentId.value].orEmpty()
    }

    /**
     * Transaction manager that records whether commit or rollback was invoked.
     * Like the no-op managers used elsewhere it runs callbacks inline, but it
     * also lets tests assert the per-content transaction boundary (one commit
     * per independent content save — never a page-level transaction).
     */
    private class RecordingTransactionManager : PlatformTransactionManager {
        var commitCount = 0
            private set
        var rollbackCount = 0
            private set

        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
            SimpleTransactionStatus()

        override fun commit(status: TransactionStatus) {
            commitCount++
        }

        override fun rollback(status: TransactionStatus) {
            rollbackCount++
        }
    }

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

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private val instance = Instance(
        id = PublicId("inst_misskey_io"), sourceId = PublicId("src_misskey"),
        domain = "misskey.io", displayName = null, enabled = true,
        allowIndex = true, allowMediaPreview = true, allowMediaCache = false,
        allowOriginalAccess = false, nsfwPolicy = null,
        healthStatus = InstanceHealthStatus.HEALTHY
    )

    private fun mdId(externalMediaRef: String): PublicId =
        PublicId("md_" + IdentityHash.derive("${instance.id.value}:$externalMediaRef"))

    private inner class Fixture(result: ExternalFetchResult = ExternalFetchResult()) {
        val authors = FakeAuthorRepository()
        val contents = FakeContentRepository()
        val media = FakeMediaRepository()
        val tags = FakeTagRepository()
        val contentTags = FakeContentTagRepository()
        val txManager = RecordingTransactionManager()
        val transactionTemplate = TransactionTemplate(txManager)

        val saveContentWithAssemblies = SaveContentWithAssembliesUseCase(
            contentRepository = contents,
            authorRepository = authors,
            mediaRepository = media,
            transactionTemplate = transactionTemplate
        )
        val ensureAuthor = EnsureAndSaveAuthorUseCase(authors)
        val saveExternalContent = SaveExternalContentUseCase(
            ensureAndSaveAuthorUseCase = ensureAuthor,
            ensureAndSaveTagUseCase = EnsureAndSaveTagUseCase(tags),
            attachTagsToContentUseCase = AttachTagsToContentUseCase(contentTags),
            contentRepository = contents,
            mediaRepository = media,
            saveContentWithAssembliesUseCase = saveContentWithAssemblies,
            transactionTemplate = transactionTemplate
        )
        val adapter = FakeExternalAdapter(result)
        val useCase = FetchAndIngestExternalContentUseCase(
            externalAdapter = adapter,
            saveExternalContentUseCase = saveExternalContent
        )
        val context = InstanceFetchContext(instance)
    }

    private fun platformTag(name: String): ExternalTagInput = ExternalTagInput(
        instanceRef = ExternalInstanceRef(instance.domain),
        name = name,
        sourceTagId = null,
        normalizedName = null
    )

    private fun contentInput(
        sourceContentId: String,
        authorRef: String = "ext-a1",
        text: String = "hello from $sourceContentId",
        mediaRefs: List<String> = emptyList(),
        tags: List<ExternalTagInput> = emptyList()
    ) = ExternalContentInput(
        instanceRef = ExternalInstanceRef(instance.domain),
        sourceContentId = sourceContentId,
        authorRef = authorRef,
        text = text,
        language = "en",
        visibility = ContentVisibility.PUBLIC,
        mediaRefs = mediaRefs,
        metadata = emptyMap(),
        tags = tags
    )

    /** One correlated media input keyed by its external media reference. */
    private fun mediaInput(
        sourceUrl: String = "https://x/o.png",
        previewUrl: String? = null,
        originalUrl: String? = null,
        sha256: String? = null,
        altText: String? = null
    ) = ExternalMediaInput(
        instanceRef = ExternalInstanceRef(instance.domain),
        sourceUrl = sourceUrl,
        previewUrl = previewUrl,
        originalUrl = originalUrl,
        sha256 = sha256,
        altText = altText
    )

    // ------------------------------------------------------------------
    // 1. Content + correlated Media -> Content, Media and content_media persisted
    // ------------------------------------------------------------------

    @Test
    fun fetchAndIngest_contentWithCorrelatedMedia_persistsContentMediaAndContentMediaRelation() {
        val fx = Fixture(
            ExternalFetchResult(
                contents = listOf(
                    contentInput(
                        sourceContentId = "sc-1",
                        mediaRefs = listOf("att-1", "att-2")
                    )
                ),
                media = mapOf(
                    "att-1" to mediaInput(
                        sourceUrl = "https://x/1.png",
                        previewUrl = "https://x/1p.png",
                        sha256 = "s1",
                        altText = "one"
                    ),
                    "att-2" to mediaInput(sourceUrl = "https://x/2.png", sha256 = "s2")
                )
            )
        )

        val summary = fx.useCase.fetchAndIngest(fx.context, SourceType.MISSKEY)

        // Summary: one content ingested with two attached media.
        assertEquals(1, summary.contentsIngested)
        assertEquals(2, summary.mediaAttached)
        assertTrue(summary.unresolvedMediaRefs.isEmpty())

        // Adapter was driven exactly once, by a FetchCommand carrying only
        // source type + external domain (never a DB PublicId).
        assertEquals(1, fx.adapter.fetchCount)
        assertEquals(SourceType.MISSKEY, fx.adapter.lastCommand?.sourceType)
        assertEquals("misskey.io", fx.adapter.lastCommand?.instanceDomain)

        // Content row persisted.
        assertEquals(1, fx.contents.rows.size)
        val saved = fx.contents.rows.values.first()
        assertEquals(ContentStatus.ACTIVE, saved.status)
        assertEquals("hello from sc-1", saved.text)
        assertEquals(instance.id, saved.instanceId)
        assertEquals(instance.sourceId, saved.sourceId)

        // Every correlated Media row persisted with the resolved context identity.
        assertEquals(2, fx.media.rows.size)
        val expectedIds = listOf("att-1", "att-2").map(::mdId)
        val first = fx.media.rows.getValue(expectedIds[0].value)
        assertEquals(instance.id, first.instanceId)
        assertEquals(instance.sourceId, first.sourceId)
        assertEquals("s1", first.sha256)
        assertEquals("https://x/1p.png", first.preview?.value)
        assertEquals("https://x/1.png", first.original?.value)
        assertEquals(MediaStatus.ACTIVE, first.status)

        // content_media association: the persisted content carries the ordered
        // media PublicIds (persisted via content_media by ContentRepository.save).
        assertEquals(expectedIds, saved.mediaIds)

        // No failure: the content's own transaction (owned by
        // SaveExternalContentUseCase) commits; nothing rolls back.
        assertEquals(0, fx.txManager.rollbackCount)
        assertTrue(fx.txManager.commitCount >= 1)
    }

    // ------------------------------------------------------------------
    // 2. Multiple contents share the same result.media Map
    // ------------------------------------------------------------------

    @Test
    fun fetchAndIngest_multipleContentsShareTheSameMediaMap() {
        val fx = Fixture(
            ExternalFetchResult(
                contents = listOf(
                    contentInput(sourceContentId = "sc-1", mediaRefs = listOf("att-1", "att-2")),
                    contentInput(sourceContentId = "sc-2", mediaRefs = listOf("att-2", "att-3"))
                ),
                media = mapOf(
                    "att-1" to mediaInput(sourceUrl = "https://x/1.png"),
                    "att-2" to mediaInput(sourceUrl = "https://x/2.png"),
                    "att-3" to mediaInput(sourceUrl = "https://x/3.png")
                )
            )
        )

        val summary = fx.useCase.fetchAndIngest(fx.context, SourceType.MASTODON)

        // Both contents ingested; every media ref correlated from the shared map.
        assertEquals(2, summary.contentsIngested)
        assertEquals(4, summary.mediaAttached, "two contents x two attached media each")
        assertTrue(summary.unresolvedMediaRefs.isEmpty())

        // Both contents persisted; each with its own attached (content_media) ids.
        assertEquals(2, fx.contents.rows.size)
        assertEquals(1, fx.authors.rows.size, "shared authorRef must be ensured once")
        val sc1 = fx.contents.findByInstanceAndSourceContentId(instance.id, "sc-1")!!
        val sc2 = fx.contents.findByInstanceAndSourceContentId(instance.id, "sc-2")!!
        assertEquals(listOf("att-1", "att-2").map(::mdId), sc1.mediaIds)
        assertEquals(listOf("att-2", "att-3").map(::mdId), sc2.mediaIds)

        // Deterministic (instance, ref) identity: att-2 shared by both contents
        // maps to ONE Media row.
        assertEquals(3, fx.media.rows.size)

        // Per-content saves are independent (no page-level transaction): each
        // content triggers its own save through SaveExternalContentUseCase while
        // a single fetch fed both contents.
        assertEquals(2, fx.contents.saveCount)
        assertEquals(0, fx.txManager.rollbackCount)
        assertTrue(fx.txManager.commitCount >= 1)
        assertEquals(1, fx.adapter.fetchCount)
    }

    // ------------------------------------------------------------------
    // 3. No mediaRefs -> normal save, nothing attached
    // ------------------------------------------------------------------

    @Test
    fun fetchAndIngest_contentWithoutMediaRefs_savesNormally() {
        val fx = Fixture(
            ExternalFetchResult(
                contents = listOf(contentInput(sourceContentId = "sc-1"))
            )
        )

        val summary = fx.useCase.fetchAndIngest(fx.context, SourceType.MISSKEY)

        assertEquals(1, summary.contentsIngested)
        assertEquals(0, summary.mediaAttached)
        assertTrue(summary.unresolvedMediaRefs.isEmpty())
        assertEquals(1, fx.contents.rows.size, "content without media refs must still be saved")
        assertTrue(fx.contents.rows.values.first().mediaIds.isEmpty())
        assertTrue(fx.media.rows.isEmpty(), "no media may be persisted without media refs")
        assertEquals(0, fx.txManager.rollbackCount)
    }

    // ------------------------------------------------------------------
    // 4. Missing media ref -> Media skipped, content saved, ref reported
    // ------------------------------------------------------------------

    @Test
    fun fetchAndIngest_missingMediaRef_skipsMediaAndReportsUnresolved() {
        val fx = Fixture(
            ExternalFetchResult(
                contents = listOf(
                    contentInput(
                        sourceContentId = "sc-1",
                        mediaRefs = listOf("att-1", "att-missing", "att-2")
                    )
                ),
                media = mapOf(
                    "att-1" to mediaInput(sourceUrl = "https://x/1.png"),
                    "att-2" to mediaInput(sourceUrl = "https://x/2.png")
                )
            )
        )

        val summary = fx.useCase.fetchAndIngest(fx.context, SourceType.MISSKEY)

        // The correlated subset is attached, the missing ref is aggregated.
        assertEquals(1, summary.contentsIngested)
        assertEquals(2, summary.mediaAttached)
        assertEquals(listOf("att-missing"), summary.unresolvedMediaRefs)

        // No fake Media fabricated for the missing ref; only the two resolved ones.
        assertEquals(2, fx.media.rows.size)
        val saved = fx.contents.rows.values.first()
        assertEquals(listOf("att-1", "att-2").map(::mdId), saved.mediaIds)
        assertEquals(1, fx.contents.rows.size, "content itself is still saved")
    }

    @Test
    fun fetchAndIngest_unresolvedMediaRefsAreAggregatedAcrossContents() {
        val fx = Fixture(
            ExternalFetchResult(
                contents = listOf(
                    contentInput(sourceContentId = "sc-1", mediaRefs = listOf("att-1", "att-x")),
                    contentInput(sourceContentId = "sc-2", mediaRefs = listOf("att-y", "att-3"))
                ),
                media = mapOf(
                    "att-1" to mediaInput(sourceUrl = "https://x/1.png"),
                    "att-3" to mediaInput(sourceUrl = "https://x/3.png")
                )
            )
        )

        val summary = fx.useCase.fetchAndIngest(fx.context, SourceType.MISSKEY)

        assertEquals(2, summary.contentsIngested)
        assertEquals(2, summary.mediaAttached)
        // unresolved refs from every per-content result are aggregated in order
        assertEquals(listOf("att-x", "att-y"), summary.unresolvedMediaRefs)
        assertEquals(2, fx.media.rows.size)
        assertEquals(2, fx.contents.rows.size)
    }

    // ------------------------------------------------------------------
    // 5. Empty contents -> completes normally with a zero summary
    // ------------------------------------------------------------------

    @Test
    fun fetchAndIngest_emptyContents_completesNormally() {
        val fx = Fixture(ExternalFetchResult())

        val summary = fx.useCase.fetchAndIngest(fx.context, SourceType.PIXELFED)

        assertEquals(0, summary.contentsIngested)
        assertEquals(0, summary.mediaAttached)
        assertTrue(summary.unresolvedMediaRefs.isEmpty())
        assertTrue(fx.contents.rows.isEmpty())
        assertTrue(fx.media.rows.isEmpty())
        assertEquals(0, fx.contents.saveCount)
        assertEquals(0, fx.txManager.commitCount, "no content -> no transaction")
        // the fetch itself still happened with the correct command
        assertEquals(1, fx.adapter.fetchCount)
        assertEquals(SourceType.PIXELFED, fx.adapter.lastCommand?.sourceType)
        assertEquals("misskey.io", fx.adapter.lastCommand?.instanceDomain)
        assertNotNull(fx.adapter.lastCommand)
        assertNull(fx.adapter.lastCommand?.cursor)
    }
}
