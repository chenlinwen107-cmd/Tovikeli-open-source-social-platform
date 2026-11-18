package com.contentplatform.backend.application.fetch

import com.contentplatform.backend.adapter.AdapterException
import com.contentplatform.backend.adapter.ExternalAdapter
import com.contentplatform.backend.adapter.ExternalAuthorInput
import com.contentplatform.backend.adapter.ExternalFetchResult
import com.contentplatform.backend.adapter.ExternalInstanceRef
import com.contentplatform.backend.adapter.ExternalTagInput
import com.contentplatform.backend.adapter.FetchCommand
import com.contentplatform.backend.application.author.EnsureAndSaveAuthorUseCase
import com.contentplatform.backend.application.tag.EnsureAndSaveTagUseCase
import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.InstanceHealthStatus
import com.contentplatform.backend.domain.source.SourceType
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.domain.tag.TagOrigin
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.TagRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class ExternalAuthorTagFetchUseCaseTest {

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

    private class FakeTagRepository : TagRepository {
        val rows = mutableMapOf<String, Tag>()
        override fun findById(id: PublicId): Tag? = rows[id.value]
        override fun findByInstanceAndSourceTagId(instanceId: PublicId, sourceTagId: String): Tag? =
            rows.values.firstOrNull { it.origin == TagOrigin.EXTERNAL && it.instanceId == instanceId && it.sourceTagId == sourceTagId }
        override fun findPlatformByName(name: String): Tag? =
            rows.values.firstOrNull { it.origin == TagOrigin.PLATFORM && it.name == name }
        override fun save(tag: Tag): Tag {
            rows[tag.id.value] = tag
            return tag
        }
    }

    private class FakeExternalAdapter(
        private val result: ExternalFetchResult = ExternalFetchResult()
    ) : ExternalAdapter {
        var lastCommand: FetchCommand? = null
            private set
        override fun fetch(command: FetchCommand): ExternalFetchResult {
            lastCommand = command
            return result
        }
    }

    private val instance = Instance(
        id = PublicId("inst_misskey_io"), sourceId = PublicId("src_misskey"),
        domain = "misskey.io", displayName = null, enabled = true,
        allowIndex = true, allowMediaPreview = true, allowMediaCache = false,
        allowOriginalAccess = false, nsfwPolicy = null,
        healthStatus = InstanceHealthStatus.HEALTHY
    )

    private fun useCase(
        adapter: ExternalAdapter,
        authorRepo: FakeAuthorRepository,
        tagRepo: FakeTagRepository
    ) = ExternalAuthorTagFetchUseCase(
        externalAdapter = adapter,
        ensureAndSaveAuthorUseCase = EnsureAndSaveAuthorUseCase(authorRepo),
        ensureAndSaveTagUseCase = EnsureAndSaveTagUseCase(tagRepo)
    )

    // ------------------------------------------------------------------
    // Orchestration happy path
    // ------------------------------------------------------------------

    @Test
    fun fetchAndEnsure_authorAndTagInputsAreEnsured() {
        val adapter = FakeExternalAdapter(
            ExternalFetchResult(
                authors = listOf(
                    ExternalAuthorInput(
                        instanceRef = ExternalInstanceRef("misskey.io"),
                        sourceAuthorId = "ext-a1", name = "Alice", username = "alice",
                        displayName = "Al", profileUrl = "https://m.io/@alice",
                        avatarUrl = "https://m.io/av.png", bio = "hi"
                    )
                ),
                tags = listOf(
                    ExternalTagInput(
                        instanceRef = ExternalInstanceRef("misskey.io"),
                        sourceIdRef = com.contentplatform.backend.adapter.ExternalSourceRef(SourceType.MISSKEY),
                        name = "art", sourceTagId = "ext-t1", normalizedName = "art"
                    )
                )
            )
        )
        val authorRepo = FakeAuthorRepository()
        val tagRepo = FakeTagRepository()
        val uc = useCase(adapter, authorRepo, tagRepo)

        val summary = uc.fetchAndEnsure(InstanceFetchContext(instance), SourceType.MISSKEY)

        assertEquals(1, summary.authorsEnsured)
        assertEquals(1, summary.tagsEnsured)
        // author persisted with correct field mapping (profileUrl->url, avatarUrl->avatar)
        val author = authorRepo.rows.values.first()
        assertEquals("ext-a1", author.sourceAuthorId)
        assertEquals("https://m.io/@alice", author.url)
        assertEquals("https://m.io/av.png", author.avatar)
        assertEquals(PublicId("inst_misskey_io"), author.instanceId)
        // tag persisted as EXTERNAL
        val tag = tagRepo.rows.values.first()
        assertEquals(TagOrigin.EXTERNAL, tag.origin)
        assertEquals("ext-t1", tag.sourceTagId)
        assertEquals("art", tag.name)
        // adapter only ever saw domain, never a DB id
        assertEquals("misskey.io", adapter.lastCommand?.instanceDomain)
        assertNull(adapter.lastCommand?.instanceDomain?.let { null })
    }

    @Test
    fun fetchAndEnsure_existingNaturalKeysAreNotDuplicated() {
        val adapter = FakeExternalAdapter(
            ExternalFetchResult(
                authors = listOf(
                    ExternalAuthorInput(
                        instanceRef = ExternalInstanceRef("misskey.io"),
                        sourceAuthorId = "ext-a1", name = "Alice", username = "alice"
                    )
                )
            )
        )
        val authorRepo = FakeAuthorRepository()
        val tagRepo = FakeTagRepository()
        val uc = useCase(adapter, authorRepo, tagRepo)

        uc.fetchAndEnsure(InstanceFetchContext(instance), SourceType.MISSKEY)
        val firstCount = authorRepo.rows.size

        // second identical fetch must NOT duplicate (existing natural key returned)
        uc.fetchAndEnsure(InstanceFetchContext(instance), SourceType.MISSKEY)

        assertEquals(1, firstCount)
        assertEquals(1, authorRepo.rows.size)
    }

    @Test
    fun fetchAndEnsure_platformTagWithoutSourceTagIdUsesPlatformEnsure() {
        val adapter = FakeExternalAdapter(
            ExternalFetchResult(
                tags = listOf(
                    ExternalTagInput(
                        instanceRef = ExternalInstanceRef("misskey.io"),
                        name = "Kotlin", sourceTagId = null, normalizedName = null
                    )
                )
            )
        )
        val authorRepo = FakeAuthorRepository()
        val tagRepo = FakeTagRepository()
        val uc = useCase(adapter, authorRepo, tagRepo)

        uc.fetchAndEnsure(InstanceFetchContext(instance), SourceType.MISSKEY)

        val tag = tagRepo.rows.values.first()
        assertEquals(TagOrigin.PLATFORM, tag.origin)
        assertEquals("Kotlin", tag.name)
        assertNull(tag.sourceTagId)
    }

    // ------------------------------------------------------------------
    // Error boundary: adapter failure surfaces as AdapterException
    // ------------------------------------------------------------------

    @Test
    fun fetchAndEnsure_adapterFailurePropagatesAsAdapterException() {
        val failingAdapter = object : ExternalAdapter {
            override fun fetch(command: FetchCommand): ExternalFetchResult =
                throw AdapterException("platform unreachable")
        }
        val uc = useCase(failingAdapter, FakeAuthorRepository(), FakeTagRepository())

        assertFailsWith<AdapterException> {
            uc.fetchAndEnsure(InstanceFetchContext(instance), SourceType.MISSKEY)
        }
    }

    // ------------------------------------------------------------------
    // Content boundary: content/media inputs are carried but NOT persisted
    // ------------------------------------------------------------------

    @Test
    fun fetchAndEnsure_contentAndMediaInputsAreNotPersisted() {
        val adapter = FakeExternalAdapter(
            ExternalFetchResult(
                contents = listOf(
                    com.contentplatform.backend.adapter.ExternalContentInput(
                        instanceRef = ExternalInstanceRef("misskey.io"),
                        sourceContentId = "ext-c1", authorRef = "ext-a1",
                        text = "note", mediaRefs = listOf("m1")
                    )
                ),
                media = mapOf(
                    "m1" to com.contentplatform.backend.adapter.ExternalMediaInput(
                        instanceRef = ExternalInstanceRef("misskey.io"),
                        sourceUrl = "https://m.io/x.png", sha256 = "abc"
                    )
                )
            )
        )
        val authorRepo = FakeAuthorRepository()
        val tagRepo = FakeTagRepository()
        val uc = useCase(adapter, authorRepo, tagRepo)

        // Content persistence is intentionally not attempted (Media PublicId UNRESOLVED):
        // the use case only handles authors/tags. It must not throw and must not write content.
        val summary = uc.fetchAndEnsure(InstanceFetchContext(instance), SourceType.MISSKEY)

        assertEquals(0, summary.authorsEnsured)
        assertEquals(0, summary.tagsEnsured)
        assertTrue(authorRepo.rows.isEmpty(), "no content-derived writes")
        assertTrue(tagRepo.rows.isEmpty())
        // no fake Media PublicId was ever created — nothing to assert beyond no-write.
        assertTrue(adapter.lastCommand?.instanceDomain == "misskey.io")
    }
}
