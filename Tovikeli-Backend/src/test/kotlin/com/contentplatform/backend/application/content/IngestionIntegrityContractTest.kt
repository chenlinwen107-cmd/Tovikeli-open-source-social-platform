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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

/**
 * Stage 43-B: Ingestion Integrity Contract regression tests.
 *
 * Covers frozen contracts I1–I10. Each test verifies one specific contract
 * invariant. Uses in-memory fakes (no Spring context, no database).
 */
class IngestionIntegrityContractTest {

    // ------------------------------------------------------------------
    // In-memory fakes
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

    /**
     * Fake that preserves status on update — mirrors the I5-contract behavior
     * of the real MediaJdbcRepository (status excluded from ON CONFLICT UPDATE).
     */
    private class FakeMediaRepository : MediaRepository {
        val rows = mutableMapOf<String, Media>()
        override fun findById(id: PublicId): Media? = rows[id.value]
        override fun findBySha256(sha256: String): Media? = rows.values.firstOrNull { it.sha256 == sha256 }
        override fun save(media: Media): Media {
            val existing = rows[media.id.value]
            if (existing != null) {
                // I5: preserve lifecycle status; refresh source metadata only.
                val updated = media.copy(
                    status = existing.status,
                    createdAt = existing.createdAt
                )
                rows[media.id.value] = updated
                return updated
            }
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

    private class FakeContentTagRepository : ContentTagRepository {
        val associations = mutableMapOf<String, MutableSet<String>>()
        override fun attach(contentId: PublicId, tagId: PublicId): Unit =
            attachAll(contentId, listOf(tagId))
        override fun attachAll(contentId: PublicId, tagIds: List<PublicId>) {
            if (tagIds.isEmpty()) return
            associations.getOrPut(contentId.value) { mutableSetOf() }
                .addAll(tagIds.map { it.value })
        }
        fun tagsOf(contentId: PublicId): Set<String> =
            associations[contentId.value].orEmpty()
    }

    private class RecordingTransactionManager : PlatformTransactionManager {
        var commitCount = 0
            private set
        var rollbackCount = 0
            private set
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
            SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) { commitCount++ }
        override fun rollback(status: TransactionStatus) { rollbackCount++ }
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
        mediaRefs: List<String> = emptyList(),
        tags: List<ExternalTagInput> = emptyList(),
        parentRef: String? = null,
        repostOfRef: String? = null,
        quoteOfRef: String? = null
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

    private fun mediaInput(
        sourceUrl: String = "https://x/o.png",
        previewUrl: String? = null,
        sha256: String? = null,
        altText: String? = null
    ) = ExternalMediaInput(
        instanceRef = ExternalInstanceRef("misskey.io"),
        sourceUrl = sourceUrl,
        previewUrl = previewUrl,
        sha256 = sha256,
        altText = altText
    )

    private fun seedAuthor(fx: Fixture): Author =
        fx.ensureAuthor.ensure(
            instanceId = instance.id,
            sourceAuthorId = "ext-a1",
            name = "Alice",
            username = "alice"
        )

    private fun mdId(externalMediaRef: String): PublicId =
        PublicId("md_" + IdentityHash.derive("${instance.id.value}:$externalMediaRef"))

    // ==================================================================
    // I2 — Content idempotency
    // ==================================================================

    @Nested
    inner class ContentIdempotency {

        @Test
        fun TC1_firstIngestion_createsContent() {
            val fx = Fixture()
            seedAuthor(fx)

            val result = fx.useCase.save(fx.context, input(sourceContentId = "sc-1"))

            assertNotNull(result.content)
            assertTrue(result.content.id.value.startsWith("ct_"))
            assertEquals(ContentStatus.ACTIVE, result.content.status)
            assertEquals(1, fx.contents.saveCount)
        }

        @Test
        fun TC2_repeatingSameIngestion_doesNotCreateSecondContent() {
            val fx = Fixture()
            seedAuthor(fx)

            val first = fx.useCase.save(fx.context, input(sourceContentId = "sc-dup"))
            val second = fx.useCase.save(fx.context, input(sourceContentId = "sc-dup", text = "changed"))

            assertEquals(first.content, second.content)
            assertEquals(1, fx.contents.saveCount)
        }

        @Test
        fun TC3_existingContentIsReturnedNotRecreated() {
            val fx = Fixture()
            seedAuthor(fx)

            val first = fx.useCase.save(fx.context, input(sourceContentId = "sc-orig", text = "original"))
            val second = fx.useCase.save(fx.context, input(sourceContentId = "sc-orig", text = "v2"))

            assertEquals(first.content.id, second.content.id)
            assertEquals("original", second.content.text, "existing content must not be overwritten")
        }
    }

    // ==================================================================
    // I2 — Content lifecycle protection
    // ==================================================================

    @Nested
    inner class ContentLifecycleProtection {

        @Test
        fun TC4_deletedContent_notImplicitlyRestoredByReIngestion() {
            val fx = Fixture()
            seedAuthor(fx)

            val first = fx.useCase.save(fx.context, input(sourceContentId = "sc-del"))
            val deleted = first.content.updateStatus(ContentStatus.DELETED)
            fx.contents.rows[deleted.id.value] = deleted

            val second = fx.useCase.save(fx.context, input(sourceContentId = "sc-del"))

            assertEquals(ContentStatus.DELETED, second.content.status)
            assertEquals(1, fx.contents.saveCount, "existing content must not be re-saved")
        }

        @Test
        fun TC5_hiddenContent_notImplicitlyRestoredByReIngestion() {
            val fx = Fixture()
            seedAuthor(fx)

            val first = fx.useCase.save(fx.context, input(sourceContentId = "sc-hide"))
            val hidden = first.content.updateStatus(ContentStatus.HIDDEN)
            fx.contents.rows[hidden.id.value] = hidden

            val second = fx.useCase.save(fx.context, input(sourceContentId = "sc-hide"))

            assertEquals(ContentStatus.HIDDEN, second.content.status)
            assertEquals(1, fx.contents.saveCount)
        }
    }

    // ==================================================================
    // I5 — Media lifecycle protection
    // ==================================================================

    @Nested
    inner class MediaLifecycleProtection {

        @Test
        fun TC6_deletedMedia_remainsDeletedAfterReIngestion() {
            val fx = Fixture()
            seedAuthor(fx)
            val mediaRef = "att-1"
            val mediaId = mdId(mediaRef)

            // First ingestion: creates media as ACTIVE.
            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", mediaRefs = listOf(mediaRef)),
                mediaByExternalRef = mapOf(mediaRef to mediaInput())
            )
            assertEquals(MediaStatus.ACTIVE, fx.media.rows[mediaId.value]!!.status)

            // Mark media as DELETED (simulates lifecycle operation).
            val deletedMedia = fx.media.rows[mediaId.value]!!.updateStatus(MediaStatus.DELETED)
            fx.media.rows[mediaId.value] = deletedMedia
            assertEquals(MediaStatus.DELETED, fx.media.rows[mediaId.value]!!.status)

            // Second ingestion: same media ref, different content.
            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-2", mediaRefs = listOf(mediaRef)),
                mediaByExternalRef = mapOf(mediaRef to mediaInput())
            )

            assertEquals(
                MediaStatus.DELETED,
                fx.media.rows[mediaId.value]!!.status,
                "I5: re-ingestion must NOT restore DELETED media to ACTIVE"
            )
        }

        @Test
        fun TC7_expiredMedia_remainsExpiredAfterReIngestion() {
            val fx = Fixture()
            seedAuthor(fx)
            val mediaRef = "att-exp"
            val mediaId = mdId(mediaRef)

            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", mediaRefs = listOf(mediaRef)),
                mediaByExternalRef = mapOf(mediaRef to mediaInput())
            )
            val expired = fx.media.rows[mediaId.value]!!.updateStatus(MediaStatus.EXPIRED)
            fx.media.rows[mediaId.value] = expired

            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-2", mediaRefs = listOf(mediaRef)),
                mediaByExternalRef = mapOf(mediaRef to mediaInput())
            )

            assertEquals(
                MediaStatus.EXPIRED,
                fx.media.rows[mediaId.value]!!.status,
                "I5: re-ingestion must NOT restore EXPIRED media to ACTIVE"
            )
        }

        @Test
        fun TC8_hiddenMedia_remainsHiddenAfterReIngestion() {
            val fx = Fixture()
            seedAuthor(fx)
            val mediaRef = "att-hid"
            val mediaId = mdId(mediaRef)

            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", mediaRefs = listOf(mediaRef)),
                mediaByExternalRef = mapOf(mediaRef to mediaInput())
            )
            val hidden = fx.media.rows[mediaId.value]!!.updateStatus(MediaStatus.HIDDEN)
            fx.media.rows[mediaId.value] = hidden

            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-2", mediaRefs = listOf(mediaRef)),
                mediaByExternalRef = mapOf(mediaRef to mediaInput())
            )

            assertEquals(
                MediaStatus.HIDDEN,
                fx.media.rows[mediaId.value]!!.status,
                "I5: re-ingestion must NOT restore HIDDEN media to ACTIVE"
            )
        }

        @Test
        fun TC9_sourceMetadataCanRefreshWithoutReplacingLifecycleStatus() {
            val fx = Fixture()
            seedAuthor(fx)
            val mediaRef = "att-meta"
            val mediaId = mdId(mediaRef)

            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", mediaRefs = listOf(mediaRef)),
                mediaByExternalRef = mapOf(
                    mediaRef to mediaInput(
                        sourceUrl = "https://x/old.png",
                        sha256 = "old_hash"
                    )
                )
            )
            val expired = fx.media.rows[mediaId.value]!!.updateStatus(MediaStatus.EXPIRED)
            fx.media.rows[mediaId.value] = expired

            // Re-ingest with updated source metadata.
            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-2", mediaRefs = listOf(mediaRef)),
                mediaByExternalRef = mapOf(
                    mediaRef to mediaInput(
                        sourceUrl = "https://x/new.png",
                        previewUrl = "https://x/new_preview.png",
                        sha256 = "new_hash",
                        altText = "updated alt"
                    )
                )
            )

            val media = fx.media.rows[mediaId.value]!!
            assertEquals(MediaStatus.EXPIRED, media.status, "lifecycle status must be preserved")
            assertEquals("new_hash", media.sha256, "source metadata can refresh")
            assertEquals("https://x/new_preview.png", media.preview?.value, "source metadata can refresh")
            assertEquals("updated alt", media.altText, "source metadata can refresh")
        }
    }

    // ==================================================================
    // I3 — Content media assembly (new content)
    // ==================================================================

    @Nested
    inner class ContentMediaAssembly {

        @Test
        fun TC10_newlyCreatedContent_preservesMediaOrder() {
            val fx = Fixture()
            seedAuthor(fx)
            val mediaByRef = mapOf(
                "att-1" to mediaInput(sourceUrl = "https://x/1.png"),
                "att-2" to mediaInput(sourceUrl = "https://x/2.png"),
                "att-3" to mediaInput(sourceUrl = "https://x/3.png")
            )

            val result = fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", mediaRefs = listOf("att-3", "att-1", "att-2")),
                mediaByExternalRef = mediaByRef
            )

            val expected = listOf("att-3", "att-1", "att-2").map(::mdId)
            assertEquals(expected, result.content.mediaIds, "media order must match external mediaRefs order")
        }

        @Test
        fun TC11_reIngestingExistingContent_doesNotReplaceMediaAssembly() {
            val fx = Fixture()
            seedAuthor(fx)
            val mediaByRef1 = mapOf("att-1" to mediaInput(sourceUrl = "https://x/1.png"))
            val mediaByRef2 = mapOf(
                "att-1" to mediaInput(sourceUrl = "https://x/1.png"),
                "att-2" to mediaInput(sourceUrl = "https://x/2.png")
            )

            val first = fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", mediaRefs = listOf("att-1")),
                mediaByExternalRef = mediaByRef1
            )
            assertEquals(listOf(mdId("att-1")), first.content.mediaIds)

            val second = fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", mediaRefs = listOf("att-1", "att-2")),
                mediaByExternalRef = mediaByRef2
            )

            assertEquals(first.content.id, second.content.id, "existing content returned")
            assertEquals(
                listOf(mdId("att-1")),
                second.content.mediaIds,
                "I4: existing content media assembly must not be replaced"
            )
        }

        @Test
        fun TC12_reIngestionDoesNotRemoveOldAssociationsBecauseFetchOmitsThem() {
            val fx = Fixture()
            seedAuthor(fx)
            val mediaByRef = mapOf(
                "att-1" to mediaInput(sourceUrl = "https://x/1.png"),
                "att-2" to mediaInput(sourceUrl = "https://x/2.png")
            )

            val first = fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", mediaRefs = listOf("att-1", "att-2")),
                mediaByExternalRef = mediaByRef
            )

            // Re-ingest with only att-1 (att-2 omitted from new fetch).
            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", mediaRefs = listOf("att-1")),
                mediaByExternalRef = mapOf("att-1" to mediaInput(sourceUrl = "https://x/1.png"))
            )

            assertEquals(
                listOf(mdId("att-1"), mdId("att-2")),
                first.content.mediaIds,
                "I4: original media assembly must be preserved"
            )
        }
    }

    // ==================================================================
    // I6 — Tag attachment remains additive-only
    // ==================================================================

    @Nested
    inner class TagBehavior {

        @Test
        fun TC13_repeatedIngestion_isAdditiveForContentTagAssociations() {
            val fx = Fixture()
            seedAuthor(fx)

            val first = fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", tags = listOf(platformTag("a"), platformTag("b")))
            )

            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", tags = listOf(platformTag("a"), platformTag("b"), platformTag("c")))
            )

            val tagA = fx.tags.findPlatformByName("a")!!
            val tagB = fx.tags.findPlatformByName("b")!!
            val tagC = fx.tags.findPlatformByName("c")!!

            assertEquals(
                setOf(tagA.id.value, tagB.id.value, tagC.id.value),
                fx.contentTags.tagsOf(first.content.id),
                "I6: tags must be additive; missing tagC must be added"
            )
        }

        @Test
        fun TC14_existingAssociations_notDeletedByLaterIngestion() {
            val fx = Fixture()
            seedAuthor(fx)

            val first = fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", tags = listOf(platformTag("x"), platformTag("y"), platformTag("z")))
            )

            // Re-ingest with only x and y (z omitted).
            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", tags = listOf(platformTag("x"), platformTag("y")))
            )

            val tagX = fx.tags.findPlatformByName("x")!!
            val tagY = fx.tags.findPlatformByName("y")!!
            val tagZ = fx.tags.findPlatformByName("z")!!

            assertEquals(
                setOf(tagX.id.value, tagY.id.value, tagZ.id.value),
                fx.contentTags.tagsOf(first.content.id),
                "I6: existing associations must not be deleted when a later ingestion omits the tag"
            )
        }
    }

    // ==================================================================
    // I7 — Relations resolve only against already-persisted Content
    // ==================================================================

    @Nested
    inner class RelationBehavior {

        @Test
        fun TC15_persistedRelationTarget_resolvesCorrectly() {
            val fx = Fixture()
            val author = seedAuthor(fx)

            // Persist parent first.
            val parent = fx.saveContentWithAssemblies.save(
                instanceId = instance.id,
                sourceContentId = "sc-parent",
                authorId = author.id,
                sourceId = instance.sourceId,
                text = "parent",
                language = null,
                visibility = ContentVisibility.PUBLIC,
                mediaIds = emptyList()
            )

            val child = fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-child", parentRef = "sc-parent")
            )

            assertEquals(parent.id, child.content.parentId)
            assertNull(child.unresolvedParentRef)
        }

        @Test
        fun TC16_sameBatchNotYetPersistedRelation_remainsUnresolved() {
            val fx = Fixture()
            seedAuthor(fx)

            // Batch: sc-child references sc-parent, but sc-parent is in the SAME
            // batch and not yet persisted when sc-child is processed.
            val parentInput = input(sourceContentId = "sc-parent")
            val childInput = input(sourceContentId = "sc-child", parentRef = "sc-parent")

            // Simulate batch order: child first, parent second.
            fx.useCase.save(fx.context, childInput)
            fx.useCase.save(fx.context, parentInput)

            // The child was saved first; parentRef was not yet in the DB.
            val child = fx.contents.findByInstanceAndSourceContentId(instance.id, "sc-child")!!
            assertNull(child.parentId, "same-batch not-yet-persisted target must remain unresolved")
        }

        @Test
        fun TC17_unresolvedRelation_doesNotCreateFakeContent() {
            val fx = Fixture()
            seedAuthor(fx)

            val result = fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-child", parentRef = "sc-nonexistent")
            )

            assertNull(result.content.parentId)
            assertEquals("sc-nonexistent", result.unresolvedParentRef)
            assertEquals(1, fx.contents.saveCount, "only the child content is saved")
            assertNull(
                fx.contents.findByInstanceAndSourceContentId(instance.id, "sc-nonexistent"),
                "no placeholder Content may be created for an unresolved relation"
            )
        }
    }

    // ==================================================================
    // I9 — Unresolved media references
    // ==================================================================

    @Nested
    inner class MediaCorrelation {

        @Test
        fun TC18_missingExternalMediaInput_producesUnresolvedReference() {
            val fx = Fixture()
            seedAuthor(fx)

            val result = fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", mediaRefs = listOf("att-missing")),
                mediaByExternalRef = emptyMap()
            )

            assertEquals(listOf("att-missing"), result.unresolvedMediaRefs)
            assertTrue(result.content.mediaIds.isEmpty())
        }

        @Test
        fun TC19_missingExternalMediaInput_doesNotCreateFakeMediaRow() {
            val fx = Fixture()
            seedAuthor(fx)

            fx.useCase.save(
                fx.context,
                input(sourceContentId = "sc-1", mediaRefs = listOf("att-missing")),
                mediaByExternalRef = emptyMap()
            )

            assertTrue(fx.media.rows.isEmpty(), "no Media row may be fabricated from a bare ref")
        }
    }

    // ==================================================================
    // I8 — Transaction / failure behavior
    // ==================================================================

    @Nested
    inner class TransactionBehavior {

        @Test
        fun TC20_failedContentTransaction_rollsBackOwnChanges() {
            val fx = Fixture()
            seedAuthor(fx)
            fx.contents.failOnSave = true

            try {
                fx.useCase.save(fx.context, input(sourceContentId = "sc-fail"))
            } catch (_: IllegalStateException) {
            }

            assertTrue(fx.txManager.rollbackCount >= 1, "rollback must be invoked on failure")
            assertEquals(0, fx.txManager.commitCount, "nothing may be committed on failure")
            assertTrue(fx.contents.rows.isEmpty(), "failed content must not be persisted")
        }

        @Test
        fun TC21_earlierCommittedContent_remainsCommittedWhenLaterFails() {
            val fx = Fixture()
            seedAuthor(fx)

            // First content succeeds.
            fx.useCase.save(fx.context, input(sourceContentId = "sc-ok"))
            assertEquals(1, fx.contents.rows.size)

            // Second content fails.
            fx.contents.failOnSave = true
            try {
                fx.useCase.save(fx.context, input(sourceContentId = "sc-fail"))
            } catch (_: IllegalStateException) {
            }

            // First content remains committed.
            assertNotNull(
                fx.contents.findByInstanceAndSourceContentId(instance.id, "sc-ok"),
                "earlier successfully committed content must remain"
            )
        }

        @Test
        fun TC22_laterContentNotProcessed_afterLoopAbort() {
            val fx = Fixture()
            seedAuthor(fx)

            // Simulate: first succeeds, second fails, third is never reached.
            fx.useCase.save(fx.context, input(sourceContentId = "sc-1"))
            fx.contents.failOnSave = true
            try {
                fx.useCase.save(fx.context, input(sourceContentId = "sc-fail"))
            } catch (_: IllegalStateException) {
            }
            // sc-3 would be processed in a loop; since the loop aborts on failure,
            // it must not exist.
            assertNull(
                fx.contents.findByInstanceAndSourceContentId(instance.id, "sc-3"),
                "content after a failure must not be silently processed"
            )
        }
    }
}
