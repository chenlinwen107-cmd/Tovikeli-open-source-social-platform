package com.contentplatform.backend.application.content

import com.contentplatform.backend.adapter.ExternalContentInput
import com.contentplatform.backend.adapter.ExternalInstanceRef
import com.contentplatform.backend.adapter.ExternalMediaInput
import com.contentplatform.backend.adapter.ExternalTagInput
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
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.domain.tag.TagOrigin
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.ContentTagRepository
import com.contentplatform.backend.repository.MediaRepository
import com.contentplatform.backend.repository.TagRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

class SaveExternalContentUseCaseTest {

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

        /** When true, save() throws before persisting — simulates a DB write failure. */
        var failOnSave = false

        override fun findById(id: PublicId): Content? = rows[id.value]
        override fun findByInstanceAndSourceContentId(instanceId: PublicId, sourceContentId: String): Content? =
            rows.values.firstOrNull { it.instanceId == instanceId && it.sourceContentId == sourceContentId }
        override fun save(content: Content): Content {
            if (failOnSave) throw IllegalStateException("simulated database write failure")
            saveCount++
            rows[content.id.value] = content
            return content
        }
    }

    private class FakeMediaRepository : MediaRepository {
        val rows = mutableMapOf<String, Media>()

        /** When true, save() throws before persisting — simulates a DB write failure. */
        var failOnSave = false

        override fun findById(id: PublicId): Media? = rows[id.value]
        override fun findBySha256(sha256: String): Media? = rows.values.firstOrNull { it.sha256 == sha256 }
        override fun save(media: Media): Media {
            if (failOnSave) throw IllegalStateException("simulated media database write failure")
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

    /**
     * Records content_tag associations in memory and counts attachAll() calls so
     * tests can assert exact per-content association sets and that attachments
     * happen through batched attachAll (not per-tag repository queries).
     */
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
     * also lets tests assert the rollback-on-failure boundary.
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

    private inner class Fixture {
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
        val ensureTag = EnsureAndSaveTagUseCase(tags)
        val attachTags = AttachTagsToContentUseCase(contentTags)
        val useCase = SaveExternalContentUseCase(
            ensureAndSaveAuthorUseCase = ensureAuthor,
            ensureAndSaveTagUseCase = ensureTag,
            attachTagsToContentUseCase = attachTags,
            contentRepository = contents,
            mediaRepository = media,
            saveContentWithAssembliesUseCase = saveContentWithAssemblies,
            transactionTemplate = transactionTemplate
        )
        val context = InstanceFetchContext(instance)
    }

    /** Platform-style tag fact (no sourceTagId), the Mastodon hashtag shape. */
    private fun platformTag(name: String): ExternalTagInput = ExternalTagInput(
        instanceRef = ExternalInstanceRef("misskey.io"),
        name = name,
        sourceTagId = null,
        normalizedName = null
    )

    private fun input(
        sourceContentId: String,
        authorRef: String = "ext-a1",
        text: String = "hello from $sourceContentId",
        parentRef: String? = null,
        repostOfRef: String? = null,
        quoteOfRef: String? = null,
        mediaRefs: List<String> = emptyList(),
        tags: List<ExternalTagInput> = emptyList()
    ) = ExternalContentInput(
        instanceRef = ExternalInstanceRef("misskey.io"),
        sourceContentId = sourceContentId,
        authorRef = authorRef,
        text = text,
        language = "en",
        visibility = ContentVisibility.PUBLIC,
        mediaRefs = mediaRefs,
        parentRef = parentRef,
        repostOfRef = repostOfRef,
        quoteOfRef = quoteOfRef,
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
        instanceRef = ExternalInstanceRef("misskey.io"),
        sourceUrl = sourceUrl,
        previewUrl = previewUrl,
        originalUrl = originalUrl,
        sha256 = sha256,
        altText = altText
    )

    private fun seedAuthor(fx: Fixture, sourceAuthorId: String = "ext-a1", name: String = "Alice", username: String = "alice"): Author =
        fx.ensureAuthor.ensure(
            instanceId = instance.id,
            sourceAuthorId = sourceAuthorId,
            name = name,
            username = username
        )

    /** Persists a target content directly so it can act as an existing relation target. */
    private fun seedContent(fx: Fixture, sourceContentId: String, authorId: PublicId): Content =
        fx.saveContentWithAssemblies.save(
            instanceId = instance.id,
            sourceContentId = sourceContentId,
            authorId = authorId,
            sourceId = instance.sourceId,
            text = "target $sourceContentId",
            language = null,
            visibility = ContentVisibility.PUBLIC,
            mediaIds = emptyList()
        )

    // ------------------------------------------------------------------
    // 1. New content successfully created and saved
    // ------------------------------------------------------------------

    @Test
    fun save_whenNew_createsAndPersistsContent() {
        val fx = Fixture()
        val author = seedAuthor(fx)

        val result = fx.useCase.save(fx.context, input(sourceContentId = "sc-1"))

        val saved = result.content
        assertNotNull(saved)
        assertTrue(saved.id.value.startsWith("ct_"))
        assertEquals(ContentStatus.ACTIVE, saved.status)
        assertEquals(ContentVisibility.PUBLIC, saved.visibility)
        assertEquals("hello from sc-1", saved.text)
        assertEquals(instance.id, saved.instanceId)
        assertEquals(instance.sourceId, saved.sourceId)
        assertEquals(author.id, saved.authorId)
        assertTrue(saved.mediaIds.isEmpty())
        assertEquals(1, fx.contents.saveCount)
        assertEquals(0, result.unresolvedReferenceCount)
        assertTrue(result.warnings.isEmpty())
        // persisted and findable by natural key and id
        assertEquals(saved, fx.contents.findByInstanceAndSourceContentId(instance.id, "sc-1"))
        assertEquals(saved, fx.contents.findById(saved.id))
    }

    // ------------------------------------------------------------------
    // 2. authorRef resolved to existing Author.id (ensure reused, no duplicate)
    // ------------------------------------------------------------------

    @Test
    fun save_authorRefResolvesToExistingAuthorIdWithoutDuplicate() {
        val fx = Fixture()
        val author = seedAuthor(fx)

        val result = fx.useCase.save(fx.context, input(sourceContentId = "sc-1"))

        assertEquals(author.id, result.content.authorId)
        assertEquals(1, fx.authors.rows.size, "author must not be duplicated by ensure")
        // author stored once; content stored once
        assertEquals(1, fx.contents.saveCount)
    }

    // ------------------------------------------------------------------
    // 3. Existing parent -> parentId
    // ------------------------------------------------------------------

    @Test
    fun save_whenParentExists_resolvesParentId() {
        val fx = Fixture()
        val author = seedAuthor(fx)
        val parent = seedContent(fx, sourceContentId = "sc-parent", authorId = author.id)

        val result = fx.useCase.save(fx.context, input(sourceContentId = "sc-child", parentRef = "sc-parent"))

        assertEquals(parent.id, result.content.parentId)
        assertNull(result.unresolvedParentRef)
        assertEquals(0, result.unresolvedReferenceCount)
        assertEquals(2, fx.contents.saveCount)
    }

    // ------------------------------------------------------------------
    // 4. Existing repost target -> repostOfId
    // ------------------------------------------------------------------

    @Test
    fun save_whenRepostTargetExists_resolvesRepostOfId() {
        val fx = Fixture()
        val author = seedAuthor(fx)
        val original = seedContent(fx, sourceContentId = "sc-original", authorId = author.id)

        // Mastodon boost wrapper content: own id, repostOf points at the original.
        val result = fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-boost", repostOfRef = "sc-original")
        )

        assertEquals(original.id, result.content.repostOfId)
        assertNull(result.unresolvedRepostOfRef)
        assertEquals(0, result.unresolvedReferenceCount)
        // boost has its own distinct content id and natural key
        assertTrue(result.content.id.value.startsWith("ct_"))
        assertTrue(result.content.id != original.id)
        assertEquals(2, fx.contents.saveCount)
    }

    // ------------------------------------------------------------------
    // 5. Existing quote target -> quoteOfId
    // ------------------------------------------------------------------

    @Test
    fun save_whenQuoteTargetExists_resolvesQuoteOfId() {
        val fx = Fixture()
        val author = seedAuthor(fx)
        val quoted = seedContent(fx, sourceContentId = "sc-quoted", authorId = author.id)

        val result = fx.useCase.save(fx.context, input(sourceContentId = "sc-quote", quoteOfRef = "sc-quoted"))

        assertEquals(quoted.id, result.content.quoteOfId)
        assertNull(result.unresolvedQuoteOfRef)
        assertEquals(0, result.unresolvedReferenceCount)
        assertEquals(2, fx.contents.saveCount)
    }

    // ------------------------------------------------------------------
    // 6. Missing parent -> content still saved + unresolved warning
    // ------------------------------------------------------------------

    @Test
    fun save_whenParentMissing_contentStillSavedWithNullParentAndWarning() {
        val fx = Fixture()
        seedAuthor(fx)

        val result = fx.useCase.save(fx.context, input(sourceContentId = "sc-child", parentRef = "sc-not-persisted"))

        val saved = result.content
        assertNull(saved.parentId, "unresolved parent must stay null")
        assertEquals(1, fx.contents.saveCount, "content must still be saved")
        assertEquals("sc-not-persisted", result.unresolvedParentRef)
        assertEquals(1, result.unresolvedReferenceCount)
        assertTrue(result.warnings.any { it.contains("parentRef=sc-not-persisted") && it.contains("unresolved") })
    }

    // ------------------------------------------------------------------
    // 7. Missing repost target -> content still saved + unresolved warning
    // ------------------------------------------------------------------

    @Test
    fun save_whenRepostTargetMissing_contentStillSavedWithNullRepostOfAndWarning() {
        val fx = Fixture()
        seedAuthor(fx)

        val result = fx.useCase.save(fx.context, input(sourceContentId = "sc-boost", repostOfRef = "sc-not-persisted"))

        val saved = result.content
        assertNull(saved.repostOfId, "unresolved repostOf must stay null")
        assertEquals(1, fx.contents.saveCount, "content must still be saved")
        assertEquals("sc-not-persisted", result.unresolvedRepostOfRef)
        assertEquals(1, result.unresolvedReferenceCount)
        assertTrue(result.warnings.any { it.contains("repostOfRef=sc-not-persisted") && it.contains("unresolved") })
    }

    // ------------------------------------------------------------------
    // 8. Missing quote target -> content still saved + unresolved warning
    // ------------------------------------------------------------------

    @Test
    fun save_whenQuoteTargetMissing_contentStillSavedWithNullQuoteOfAndWarning() {
        val fx = Fixture()
        seedAuthor(fx)

        val result = fx.useCase.save(fx.context, input(sourceContentId = "sc-quote", quoteOfRef = "sc-not-persisted"))

        val saved = result.content
        assertNull(saved.quoteOfId, "unresolved quoteOf must stay null")
        assertEquals(1, fx.contents.saveCount, "content must still be saved")
        assertEquals("sc-not-persisted", result.unresolvedQuoteOfRef)
        assertEquals(1, result.unresolvedReferenceCount)
        assertTrue(result.warnings.any { it.contains("quoteOfRef=sc-not-persisted") && it.contains("unresolved") })
    }

    // ------------------------------------------------------------------
    // 9. Natural-key duplicate -> existing returned, no second save
    // ------------------------------------------------------------------

    @Test
    fun save_whenNaturalKeyDuplicate_returnsExistingWithoutSecondSave() {
        val fx = Fixture()
        seedAuthor(fx)

        val first = fx.useCase.save(fx.context, input(sourceContentId = "sc-dup"))
        assertEquals(1, fx.contents.saveCount)

        // A later identical natural key with different text must NOT update the row.
        val second = fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-dup", text = "v2-changed")
        )

        assertEquals(first.content, second.content, "existing content must be returned unchanged")
        assertEquals(1, fx.contents.saveCount, "save() must not be invoked for existing natural key")
    }

    // ------------------------------------------------------------------
    // 10. mediaRefs + correlated media -> media persisted, mediaIds in ref order
    // ------------------------------------------------------------------

    @Test
    fun save_whenMediaRefsCorrelated_persistsMediaAndAttachesInOrder() {
        val fx = Fixture()
        val author = seedAuthor(fx)
        val mediaByRef = mapOf(
            "att-1" to mediaInput(sourceUrl = "https://x/1.png", previewUrl = "https://x/1p.png", sha256 = "s1"),
            "att-2" to mediaInput(sourceUrl = "https://x/2.png", previewUrl = "https://x/2p.png", altText = "two"),
            "att-3" to mediaInput(sourceUrl = "https://x/3.png")
        )

        val result = fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-1", mediaRefs = listOf("att-1", "att-2", "att-3")),
            mediaByExternalRef = mediaByRef
        )

        // deterministic md_ identity, attached in mediaRefs order
        val expectedIds = listOf("att-1", "att-2", "att-3").map { ref ->
            PublicId("md_" + IdentityHash.derive("${instance.id.value}:$ref"))
        }
        assertEquals(expectedIds, result.content.mediaIds)
        assertEquals(3, result.mediaRefsCount)
        assertTrue(result.unresolvedMediaRefs.isEmpty())
        assertEquals(0, result.unresolvedReferenceCount)
        assertEquals(3, fx.media.rows.size, "every correlated media must be persisted")

        // saved media carry the resolved context identity + mapped external facts
        val first = fx.media.rows.getValue(expectedIds[0].value)
        assertEquals(instance.sourceId, first.sourceId)
        assertEquals(instance.id, first.instanceId)
        assertEquals("s1", first.sha256)
        assertEquals("https://x/1p.png", first.preview?.value)
        assertEquals("https://x/1.png", first.original?.value, "sourceUrl is the original fallback")
        assertEquals(MediaStatus.ACTIVE, first.status)
        val third = fx.media.rows.getValue(expectedIds[2].value)
        assertNull(third.preview)
        assertNull(third.altText)

        assertEquals(1, fx.contents.saveCount)
        assertEquals(author.id, result.content.authorId)
    }

    // ------------------------------------------------------------------
    // 11. mediaRefs without correlation -> unresolved warning, no fake Media
    // ------------------------------------------------------------------

    @Test
    fun save_whenMediaCorrelationMissing_recordsUnresolvedAndDoesNotFabricateMedia() {
        val fx = Fixture()
        seedAuthor(fx)

        // no correlation map provided (default): every ref is unresolved
        val result = fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-1", mediaRefs = listOf("att-1", "att-2", "att-3"))
        )

        assertTrue(result.content.mediaIds.isEmpty(), "no correlated media may be attached")
        assertEquals(listOf("att-1", "att-2", "att-3"), result.unresolvedMediaRefs)
        assertEquals(3, result.mediaRefsCount)
        assertEquals(3, result.unresolvedReferenceCount)
        assertTrue(result.warnings.any { it.contains("mediaRef=att-1") && it.contains("unresolved") })
        assertTrue(fx.media.rows.isEmpty(), "no Media row may be fabricated from a bare ref")
        assertEquals(1, fx.contents.saveCount, "content itself is still saved")
    }

    @Test
    fun save_whenSomeMediaCorrelationMissing_persistsOnlyResolvedInOrder() {
        val fx = Fixture()
        seedAuthor(fx)
        val mediaByRef = mapOf(
            "att-1" to mediaInput(sourceUrl = "https://x/1.png"),
            "att-3" to mediaInput(sourceUrl = "https://x/3.png")
        )

        val result = fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-1", mediaRefs = listOf("att-1", "att-missing", "att-3")),
            mediaByExternalRef = mediaByRef
        )

        val expected = listOf("att-1", "att-3").map { ref ->
            PublicId("md_" + IdentityHash.derive("${instance.id.value}:$ref"))
        }
        assertEquals(expected, result.content.mediaIds, "resolved media keep their relative ref order")
        assertEquals(listOf("att-missing"), result.unresolvedMediaRefs)
        assertEquals(2, fx.media.rows.size)
        assertEquals(1, result.unresolvedReferenceCount)
        assertEquals(1, fx.contents.saveCount)
    }

    // ------------------------------------------------------------------
    // 12. repeated ingestion -> no duplicate Media identity
    // ------------------------------------------------------------------

    @Test
    fun save_whenSameMediaRefIngestedRepeatedly_keepsSingleMediaIdentity() {
        val fx = Fixture()
        seedAuthor(fx)
        val mediaByRef = mapOf("att-1" to mediaInput(sourceUrl = "https://x/1.png"))
        val mediaId = PublicId("md_" + IdentityHash.derive("${instance.id.value}:att-1"))

        // two distinct contents sharing the same external media ref
        fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-1", mediaRefs = listOf("att-1")),
            mediaByExternalRef = mediaByRef
        )
        val second = fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-2", mediaRefs = listOf("att-1")),
            mediaByExternalRef = mediaByRef
        )

        assertEquals(mediaId, second.content.mediaIds.single())
        assertEquals(1, fx.media.rows.size, "same (instance, ref) must always map to one Media row")

        // repeated ingestion of the SAME content natural key returns existing content
        val third = fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-1", mediaRefs = listOf("att-1")),
            mediaByExternalRef = mediaByRef
        )
        assertEquals(1, fx.media.rows.size, "media identity must not be duplicated on repeat")
        assertEquals(2, fx.contents.saveCount, "only the two distinct contents are persisted")
    }

    // ------------------------------------------------------------------
    // 13. media write failure -> rollback (never commit)
    // ------------------------------------------------------------------

    @Test
    fun save_whenMediaWriteFails_rollsBackAndNeverCommits() {
        val fx = Fixture()
        seedAuthor(fx)
        fx.media.failOnSave = true

        assertFailsWith<IllegalStateException> {
            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", mediaRefs = listOf("att-1")),
                mediaByExternalRef = mapOf("att-1" to mediaInput())
            )
        }

        assertTrue(fx.txManager.rollbackCount >= 1, "rollback must be invoked on media write failure")
        assertEquals(0, fx.txManager.commitCount, "nothing may be committed on failure")
        assertTrue(fx.contents.rows.isEmpty(), "content must not be persisted when a media write fails")
        assertTrue(fx.media.rows.isEmpty(), "failed media write must not leave a row")
    }

    // ------------------------------------------------------------------
    // 14. Transaction failure -> rollback (never commit)
    // ------------------------------------------------------------------

    @Test
    fun save_whenDatabaseWriteFails_rollsBackAndNeverCommits() {
        val fx = Fixture()
        seedAuthor(fx)
        fx.contents.failOnSave = true

        assertFailsWith<IllegalStateException> {
            fx.useCase.save(fx.context, input(sourceContentId = "sc-1"))
        }

        // The failure must abort the transaction via rollback; no commit happened.
        assertTrue(fx.txManager.rollbackCount >= 1, "rollback must be invoked on failure")
        assertEquals(0, fx.txManager.commitCount, "nothing may be committed on failure")
        assertTrue(fx.contents.rows.isEmpty(), "failed content write must not be persisted")
    }

    // ------------------------------------------------------------------
    // 15. (Stage 15-B) content tags are ensured and attached per content
    // ------------------------------------------------------------------

    @Test
    fun save_whenTagsPresent_ensuresPlatformTagsAndAttachesToContent() {
        val fx = Fixture()
        seedAuthor(fx)

        val result = fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-tagged", tags = listOf(platformTag("anime"), platformTag("kotlin")))
        )

        // Tags were ensured through the REUSED EnsureAndSaveTagUseCase -> Tag rows exist.
        assertEquals(2, fx.tags.rows.size, "each distinct platform tag name must be ensured once")
        val animeTag = fx.tags.findPlatformByName("anime")!!
        val kotlinTag = fx.tags.findPlatformByName("kotlin")!!

        // The content got exactly its OWN associations, in one batched attachAll.
        assertEquals(setOf(animeTag.id.value, kotlinTag.id.value), fx.contentTags.tagsOf(result.content.id))
        assertEquals(1, fx.contentTags.attachAllCalls, "attachAll must be a single batched call per content")
        assertTrue(fx.tags.rows.values.all { it.id.value.startsWith("tg_") })
    }

    @Test
    fun save_whenNoTags_noTagRowsAndNoAttachCall() {
        val fx = Fixture()
        seedAuthor(fx)

        fx.useCase.save(fx.context, input(sourceContentId = "sc-plain"))

        assertTrue(fx.tags.rows.isEmpty(), "no tag may be ensured for tag-less content")
        assertEquals(0, fx.contentTags.attachAllCalls, "no attachment may happen for tag-less content")
    }

    // ------------------------------------------------------------------
    // 16. (Stage 15-B) per-content tags never leak onto other contents
    // ------------------------------------------------------------------

    @Test
    fun save_twoContentsWithDifferentTags_doNotCrossAttach() {
        val fx = Fixture()
        seedAuthor(fx)

        val contentA = fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-A", tags = listOf(platformTag("anime"), platformTag("bluearchive")))
        ).content
        val contentB = fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-B", tags = listOf(platformTag("illustration")))
        ).content

        val anime = fx.tags.findPlatformByName("anime")!!
        val bluearchive = fx.tags.findPlatformByName("bluearchive")!!
        val illustration = fx.tags.findPlatformByName("illustration")!!

        assertEquals(
            setOf(anime.id.value, bluearchive.id.value),
            fx.contentTags.tagsOf(contentA.id),
            "content A must carry only its own tags"
        )
        assertEquals(
            setOf(illustration.id.value),
            fx.contentTags.tagsOf(contentB.id),
            "content B must carry only its own tags"
        )
        // attachAll was still one batched call per content (no per-tag writes).
        assertEquals(2, fx.contentTags.attachAllCalls)
    }

    // ------------------------------------------------------------------
    // 17. (Stage 15-B) duplicate ingestion adds missing tags, never deletes
    // ------------------------------------------------------------------

    @Test
    fun save_reIngestionAddsMissingTagsAndNeverRemovesExistingAssociations() {
        val fx = Fixture()
        seedAuthor(fx)

        val first = fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-dup", tags = listOf(platformTag("tag1"), platformTag("tag2")))
        ).content
        val tag1 = fx.tags.findPlatformByName("tag1")!!
        val tag2 = fx.tags.findPlatformByName("tag2")!!

        // Re-ingest the SAME natural key with tag1 + tag2 + tag3.
        fx.useCase.save(
            fx.context,
            input(sourceContentId = "sc-dup", tags = listOf(platformTag("tag1"), platformTag("tag2"), platformTag("tag3")))
        )
        val tag3 = fx.tags.findPlatformByName("tag3")!!

        // Existing content was returned unchanged (no second content save) but the
        // NEW association was added without removing tag1/tag2.
        assertEquals(1, fx.contents.saveCount, "existing natural key must not trigger a content save")
        assertEquals(
            setOf(tag1.id.value, tag2.id.value, tag3.id.value),
            fx.contentTags.tagsOf(first.id),
            "re-ingestion must only add the missing association, never delete existing ones"
        )
        // attachAll ran once per ingestion (batched), twice total.
        assertEquals(2, fx.contentTags.attachAllCalls)
    }

    // ------------------------------------------------------------------
    // 18. (Stage 15-B) tag attachment failure rolls back the whole content
    // ------------------------------------------------------------------

    @Test
    fun save_whenTagAttachFails_rollsBackOuterContentTransaction() {
        val fx = Fixture()
        seedAuthor(fx)
        val failingContentTags = object : ContentTagRepository {
            override fun attach(contentId: PublicId, tagId: PublicId): Unit =
                attachAll(contentId, listOf(tagId))

            override fun attachAll(contentId: PublicId, tagIds: List<PublicId>) {
                throw IllegalStateException("simulated content_tag database write failure")
            }
        }
        val failingUseCase = SaveExternalContentUseCase(
            ensureAndSaveAuthorUseCase = fx.ensureAuthor,
            ensureAndSaveTagUseCase = fx.ensureTag,
            attachTagsToContentUseCase = AttachTagsToContentUseCase(failingContentTags),
            contentRepository = fx.contents,
            mediaRepository = fx.media,
            saveContentWithAssembliesUseCase = fx.saveContentWithAssemblies,
            transactionTemplate = fx.transactionTemplate
        )

        assertFailsWith<IllegalStateException> {
            failingUseCase.save(
                fx.context,
                input(sourceContentId = "sc-rollback", tags = listOf(platformTag("anime")))
            )
        }

        // The tag-attach failure must abort the OUTER per-content transaction
        // boundary owned by SaveExternalContentUseCase (rollback is invoked).
        //
        // NOTE on this project's naive RecordingTransactionManager: it commits
        // each nested TransactionTemplate.execute independently and never undoes
        // in-memory fakes, so exact commitCount/content-row assertions are not
        // meaningful here (SaveContentWithAssembliesUseCase commits at its own
        // inner Template.execute before the outer tag attach runs). In the real
        // Spring wiring both templates join ONE REQUIRED transaction, so a tag
        // failure rolls the whole content write back. This test asserts the
        // rollback boundary that the naive infrastructure CAN observe.
        assertTrue(fx.txManager.rollbackCount >= 1, "rollback must be invoked on tag attach failure")
    }
}
