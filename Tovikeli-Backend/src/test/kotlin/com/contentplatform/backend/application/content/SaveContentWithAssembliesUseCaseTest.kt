package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.media.Media
import com.contentplatform.backend.domain.media.MediaStatus
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.MediaRepository
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

class SaveContentWithAssembliesUseCaseTest {

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

    private class FakeMediaRepository : MediaRepository {
        val rows = mutableMapOf<String, Media>()

        override fun findById(id: PublicId): Media? = rows[id.value]
        override fun findBySha256(sha256: String): Media? = rows.values.firstOrNull { it.sha256 == sha256 }
        override fun save(media: Media): Media {
            rows[media.id.value] = media
            return media
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

    /** No-op transaction manager: lets TransactionTemplate run the callback inline. */
    private class NoopTransactionManager : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus =
            SimpleTransactionStatus()
        override fun commit(status: TransactionStatus) = Unit
        override fun rollback(status: TransactionStatus) = Unit
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun author(): Author =
        Author.createFromAdapter(
            instanceId = PublicId("inst-1"), sourceAuthorId = "auth-1",
            name = "Alice", username = "alice",
            displayName = null, url = null, avatar = null, bio = null, status = null
        )

    private fun media(id: String): Media =
        Media(
            id = PublicId(id), sha256 = null,
            sourceId = PublicId("src-1"), instanceId = PublicId("inst-1"),
            thumbnail = null, preview = null, original = null,
            altText = null, license = "CC-BY-4.0", metadata = emptyMap(),
            status = MediaStatus.ACTIVE, createdAt = 1L, updatedAt = 1L
        )

    private fun useCase(
        authors: FakeAuthorRepository,
        mediaRepo: FakeMediaRepository,
        contents: FakeContentRepository
    ) = SaveContentWithAssembliesUseCase(
        contentRepository = contents,
        authorRepository = authors,
        mediaRepository = mediaRepo,
        transactionTemplate = TransactionTemplate(NoopTransactionManager())
    )

    // ------------------------------------------------------------------
    // Referenced aggregate checks (fail fast before any save)
    // ------------------------------------------------------------------

    @Test
    fun save_whenAuthorMissing_throwsAndNeverSavesContent() {
        val authors = FakeAuthorRepository() // empty
        val mediaRepo = FakeMediaRepository()
        val contents = FakeContentRepository()
        val uc = useCase(authors, mediaRepo, contents)

        assertFailsWith<ReferencedAggregateNotFoundException> {
            uc.save(
                instanceId = PublicId("inst-1"), sourceContentId = "sc-1",
                authorId = PublicId("au_missing"), sourceId = PublicId("src-1"),
                text = "hi", language = null, visibility = ContentVisibility.PUBLIC,
                mediaIds = emptyList()
            )
        }
        assertEquals(0, contents.saveCount)
    }

    @Test
    fun save_whenMediaMissing_throwsAndNeverSavesContent() {
        val authors = FakeAuthorRepository()
        val a = authors.save(author())
        val mediaRepo = FakeMediaRepository() // empty
        val contents = FakeContentRepository()
        val uc = useCase(authors, mediaRepo, contents)

        assertFailsWith<ReferencedAggregateNotFoundException> {
            uc.save(
                instanceId = PublicId("inst-1"), sourceContentId = "sc-1",
                authorId = a.id, sourceId = PublicId("src-1"),
                text = "hi", language = null, visibility = ContentVisibility.PUBLIC,
                mediaIds = listOf(PublicId("md_1"))
            )
        }
        assertEquals(0, contents.saveCount)
    }

    @Test
    fun save_whenSelfReferenceTargetMissing_throwsAndNeverSavesContent() {
        val authors = FakeAuthorRepository()
        val a = authors.save(author())
        val mediaRepo = FakeMediaRepository()
        mediaRepo.save(media("md_1"))
        val contents = FakeContentRepository()
        val uc = useCase(authors, mediaRepo, contents)

        assertFailsWith<ReferencedAggregateNotFoundException> {
            uc.save(
                instanceId = PublicId("inst-1"), sourceContentId = "sc-child",
                authorId = a.id, sourceId = PublicId("src-1"),
                text = "child", language = null, visibility = ContentVisibility.PUBLIC,
                mediaIds = listOf(PublicId("md_1")),
                parentId = PublicId("ct_parent_not_persisted")
            )
        }
        assertEquals(0, contents.saveCount)
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    fun save_whenNew_persistsContentWithMediaOrderAndNaturalKey() {
        val authors = FakeAuthorRepository()
        val a = authors.save(author())
        val mediaRepo = FakeMediaRepository()
        mediaRepo.save(media("md_1"))
        mediaRepo.save(media("md_2"))
        mediaRepo.save(media("md_3"))
        val contents = FakeContentRepository()
        val uc = useCase(authors, mediaRepo, contents)

        val saved = uc.save(
            instanceId = PublicId("inst-1"), sourceContentId = "sc-1",
            authorId = a.id, sourceId = PublicId("src-1"),
            text = "hello", language = "en", visibility = ContentVisibility.UNLISTED,
            mediaIds = listOf(PublicId("md_3"), PublicId("md_1"), PublicId("md_2")),
            externalCreatedAt = 1_600_000_000_000L,
            metadata = mapOf("n" to 1)
        )

        assertNotNull(saved)
        assertTrue(saved.id.value.startsWith("ct_"))
        assertEquals(ContentStatus.ACTIVE, saved.status)
        assertEquals(listOf(PublicId("md_3"), PublicId("md_1"), PublicId("md_2")), saved.mediaIds)
        assertEquals(1_600_000_000_000L, saved.externalCreatedAt)
        assertEquals(1, contents.saveCount)
        // findable by natural key and by id
        assertEquals(
            saved,
            contents.findByInstanceAndSourceContentId(PublicId("inst-1"), "sc-1")
        )
        assertEquals(saved, contents.findById(saved.id))
    }

    @Test
    fun save_whenSelfReferenceTargetExists_persistsChild() {
        val authors = FakeAuthorRepository()
        val a = authors.save(author())
        val mediaRepo = FakeMediaRepository()
        val contents = FakeContentRepository()
        val uc = useCase(authors, mediaRepo, contents)

        // First persist the parent content.
        val parent = uc.save(
            instanceId = PublicId("inst-1"), sourceContentId = "sc-parent",
            authorId = a.id, sourceId = PublicId("src-1"),
            text = "parent", language = "en", visibility = ContentVisibility.PUBLIC,
            mediaIds = emptyList()
        )

        // Then persist a child referencing the parent.
        val child = uc.save(
            instanceId = PublicId("inst-1"), sourceContentId = "sc-child",
            authorId = a.id, sourceId = PublicId("src-1"),
            text = "child", language = null, visibility = ContentVisibility.PUBLIC,
            mediaIds = emptyList(), parentId = parent.id
        )

        assertEquals(parent.id, child.parentId)
        assertEquals(2, contents.saveCount)
    }

    // ------------------------------------------------------------------
    // Natural-key dedup (no save() on existing content)
    // ------------------------------------------------------------------

    @Test
    fun save_whenContentAlreadyExists_returnsExistingWithoutSecondSave() {
        val authors = FakeAuthorRepository()
        val a = authors.save(author())
        val mediaRepo = FakeMediaRepository()
        val contents = FakeContentRepository()
        val uc = useCase(authors, mediaRepo, contents)

        val first = uc.save(
            instanceId = PublicId("inst-1"), sourceContentId = "sc-dup",
            authorId = a.id, sourceId = PublicId("src-1"),
            text = "v1", language = null, visibility = ContentVisibility.PUBLIC,
            mediaIds = emptyList()
        )
        assertEquals(1, contents.saveCount)

        val again = uc.save(
            instanceId = PublicId("inst-1"), sourceContentId = "sc-dup",
            authorId = a.id, sourceId = PublicId("src-1"),
            text = "v2-changed", language = null, visibility = ContentVisibility.PUBLIC,
            mediaIds = emptyList()
        )

        assertEquals(first, again, "existing content must be returned unchanged")
        assertEquals("v1", again.text, "existing content must not be overwritten")
        assertEquals(1, contents.saveCount, "save() must not be invoked for existing natural key")
    }
}
