package com.contentplatform.backend.application.interaction

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.interaction.Interaction
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.InstanceHealthStatus
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.InstanceRepository
import com.contentplatform.backend.repository.InteractionRepository
import com.contentplatform.backend.repository.UserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.dao.DuplicateKeyException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

/**
 * Application tests for [CreateInteractionUseCase] (Stage 17).
 */
class CreateInteractionUseCaseTest {

    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    private class FakeUserRepository : UserRepository {
        val rows = mutableMapOf<String, User>()

        override fun findById(id: PublicId): User? = rows[id.value]

        override fun findByUsernameCaseInsensitive(username: String): User? = null

        override fun search(query: String, limit: Int): List<User> = emptyList()



        override fun updateProfile(user: User): User {


            rows[user.id.value] = user


            return user


        }



        override fun save(user: User): User {
            rows[user.id.value] = user
            return user
        }
    }

    private class FakeInstanceRepository : InstanceRepository {
        val rows = mutableMapOf<String, Instance>()

        override fun findById(id: PublicId): Instance? = rows[id.value]
    }

    private class FakeContentRepository : ContentRepository {
        val rows = mutableMapOf<String, Content>()

        override fun findById(id: PublicId): Content? = rows[id.value]

        override fun findByInstanceAndSourceContentId(
            instanceId: PublicId,
            sourceContentId: String
        ): Content? =
            rows.values.firstOrNull {
                it.instanceId == instanceId && it.sourceContentId == sourceContentId
            }

        override fun save(content: Content): Content {
            rows[content.id.value] = content
            return content
        }

        override fun findPublicContent(
            instanceId: PublicId?,
            authorId: PublicId?,
            hasMedia: Boolean?,
            cursor: ContentCursor?,
            limit: Int,
            viewerAuthorId: PublicId?
        ): ContentBrowsePage = throw UnsupportedOperationException("not used")

        override fun searchPublicContent(
            query: String,
            instanceId: PublicId?,
            authorId: PublicId?,
            sourceId: PublicId?,
            limit: Int
        ): List<Content> = throw UnsupportedOperationException("not used")

        override fun findPublicContentByTag(
            tagId: PublicId,
            cursor: ContentCursor?,
            limit: Int
        ): ContentBrowsePage = throw UnsupportedOperationException("not used")
    }

    private class FakeInteractionRepository : InteractionRepository {
        val rows = mutableMapOf<String, Interaction>()
        var saveCount = 0
            private set

        // Read-committed race emulation (see armRaceForConcurrentInsert):
        //  - winner  = the interaction another transaction commits mid-race
        //  - preCheckDone = whether our pre-check read already happened
        private var winner: Interaction? = null
        private var preCheckDone = false

        /**
         * Arms a concurrent-create race. The [winner] commits the same natural
         * key between our pre-check (which must therefore still return null)
         * and our strict INSERT (which must then fail with a duplicate-key
         * violation). The winner becomes visible to the post-rollback re-read.
         */
        fun armRaceForConcurrentInsert(winner: Interaction) {
            this.winner = winner
            preCheckDone = false
        }

        private fun key(userId: PublicId, contentId: PublicId, type: InteractionType): String =
            "${userId.value}:${contentId.value}:${type.name}"

        override fun save(interaction: Interaction): Interaction {
            saveCount++
            val w = winner
            if (w != null) {
                // The other transaction's row is committed in the DB now; our
                // INSERT hits the unique constraint (user_id, content_id, type).
                rows[key(w.userId, w.contentId, w.type)] = w
                throw DuplicateKeyException(
                    "duplicate key value violates unique constraint \"uq_content_interaction_identity\""
                )
            }
            rows[key(interaction.userId, interaction.contentId, interaction.type)] = interaction
            return interaction
        }

        override fun findById(id: PublicId): Interaction? =
            rows.values.firstOrNull { it.id == id }

        override fun findByUserContentType(
            userId: PublicId,
            contentId: PublicId,
            type: InteractionType
        ): Interaction? {
            val w = winner
            if (w != null) {
                // First read = the pre-check, which must not yet see the
                // concurrently committing winner. Later reads (the re-read
                // after the duplicate-key rollback) see the winner row.
                return if (!preCheckDone) {
                    preCheckDone = true
                    null
                } else {
                    rows[key(userId, contentId, type)]
                }
            }
            return rows[key(userId, contentId, type)]
        }

        override fun deleteByUserContentType(
            userId: PublicId,
            contentId: PublicId,
            type: InteractionType
        ): Boolean = rows.remove(key(userId, contentId, type)) != null
    }

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
    // Fixtures
    // ------------------------------------------------------------------

    private val instanceId = PublicId("inst_public")
    private val userId = PublicId("usr_alice")
    private val contentId = PublicId("ct_1")

    private fun instance(allowIndex: Boolean = true): Instance = Instance(
        id = instanceId,
        sourceId = PublicId("src_misskey"),
        domain = "misskey.io",
        displayName = null,
        enabled = true,
        allowIndex = allowIndex,
        allowMediaPreview = true,
        allowMediaCache = false,
        allowOriginalAccess = false,
        nsfwPolicy = null,
        healthStatus = InstanceHealthStatus.HEALTHY
    )

    private fun user(): User = User(
        id = userId,
        username = "alice",
        displayName = null,
        avatar = null,

        bio = null,

        gender = "secret",

        location = null,

        cover = null,

        status = UserStatus.ACTIVE,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun content(
        id: PublicId = contentId,
        status: ContentStatus = ContentStatus.ACTIVE,
        visibility: ContentVisibility = ContentVisibility.PUBLIC
    ): Content = Content(
        id = id,
        instanceId = instanceId,
        sourceContentId = "sc-1",
        authorId = PublicId("au_1"),
        sourceId = PublicId("src_misskey"),
        text = "body",
        language = null,
        visibility = visibility,
        status = status,
        mediaIds = emptyList(),
        parentId = null,
        repostOfId = null,
        quoteOfId = null,
        topicId = null,
        externalCreatedAt = null,
        createdAt = 1L,
        updatedAt = 1L,
        metadata = emptyMap()
    )

    private inner class Fixture {
        val users = FakeUserRepository()
        val instances = FakeInstanceRepository()
        val contents = FakeContentRepository()
        val interactions = FakeInteractionRepository()
        val txManager = RecordingTransactionManager()

        val useCase = CreateInteractionUseCase(
            userRepository = users,
            contentRepository = contents,
            authorRepository = org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java),
            notifications = org.mockito.Mockito.mock(com.contentplatform.backend.application.notification.NotificationUseCase::class.java),
            instanceRepository = instances,
            interactionRepository = interactions,
            transactionTemplate = TransactionTemplate(txManager)
        )

        fun withInteractableTarget() = apply {
            users.rows[userId.value] = user()
            instances.rows[instanceId.value] = instance(allowIndex = true)
            contents.rows[contentId.value] = content()
        }
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    fun create_validTarget_createsAndPersistsInteractionInOneTransaction() {
        val f = Fixture().withInteractableTarget()

        val result = f.useCase.create(userId, contentId, InteractionType.LIKE)

        assertTrue(result.created, "first create must set created=true")
        assertTrue(result.interaction.id.value.startsWith("int_"))
        assertEquals(userId, result.interaction.userId)
        assertEquals(contentId, result.interaction.contentId)
        assertEquals(InteractionType.LIKE, result.interaction.type)
        assertEquals(1, f.interactions.saveCount)
        assertEquals(result.interaction, f.interactions.findByUserContentType(userId, contentId, InteractionType.LIKE))
        assertEquals(1, f.txManager.commitCount)
        assertEquals(0, f.txManager.rollbackCount)
    }

    @Test
    fun create_eachInteractionType_persistsItsOwnRow() {
        val f = Fixture().withInteractableTarget()

        InteractionType.entries.forEach { type ->
            val result = f.useCase.create(userId, contentId, type)
            assertTrue(result.created, "each fresh create must set created=true")
            assertNotNull(f.interactions.findByUserContentType(userId, contentId, type))
            assertEquals(type, result.interaction.type)
        }
        assertEquals(4, f.interactions.saveCount)
    }

    // ------------------------------------------------------------------
    // Duplicate handling
    // ------------------------------------------------------------------

    @Test
    fun create_whenInteractionAlreadyExists_returnsExistingWithoutSecondSave() {
        val f = Fixture().withInteractableTarget()
        val first = f.useCase.create(userId, contentId, InteractionType.BOOKMARK)
        val savesBefore = f.interactions.saveCount

        val again = f.useCase.create(userId, contentId, InteractionType.BOOKMARK)

        assertTrue(first.created, "first create must be created=true")
        assertFalse(again.created, "second create of same tuple must be created=false")
        assertEquals(first.interaction, again.interaction)
        assertEquals(savesBefore, f.interactions.saveCount, "a duplicate create must never insert again")
    }

    @Test
    fun create_concurrentDuplicateInsert_isHandledByReturningExistingRow() {
        val f = Fixture().withInteractableTarget()
        // A concurrent create commits the same natural key between our
        // pre-check and our strict INSERT; the database unique constraint is
        // the final protection and the existing row must be returned.
        val winner = Interaction.create(userId, contentId, InteractionType.FAVORITE)
        f.interactions.armRaceForConcurrentInsert(winner)

        val result = f.useCase.create(userId, contentId, InteractionType.FAVORITE)

        assertEquals(winner, result.interaction, "the concurrent winner must be returned")
        assertFalse(result.created, "concurrent duplicate must set created=false")
        assertEquals(1, f.interactions.saveCount, "only one insert attempt may happen")
        assertEquals(0, f.txManager.commitCount, "the failed transaction must not commit")
        assertTrue(f.txManager.rollbackCount >= 1, "the failed insert's transaction must roll back")
    }

    // ------------------------------------------------------------------
    // Validation failures
    // ------------------------------------------------------------------

    @Test
    fun create_whenUserMissing_throwsUserNotFoundAndSavesNothing() {
        val f = Fixture()
        f.instances.rows[instanceId.value] = instance(allowIndex = true)
        f.contents.rows[contentId.value] = content()

        val e = assertFailsWith<InteractionUserNotFoundException> {
            f.useCase.create(userId, contentId, InteractionType.LIKE)
        }
        assertTrue(e.message!!.contains(userId.value))
        assertEquals(0, f.interactions.saveCount)
    }

    @Test
    fun create_whenContentMissing_throwsContentNotFoundAndSavesNothing() {
        val f = Fixture()
        f.users.rows[userId.value] = user()

        assertFailsWith<InteractionContentNotFoundException> {
            f.useCase.create(userId, contentId, InteractionType.LIKE)
        }
        assertEquals(0, f.interactions.saveCount)
    }

    @Test
    fun create_whenUserAndContentMissing_throwsUserNotFound() {
        val f = Fixture()

        assertFailsWith<InteractionUserNotFoundException> {
            f.useCase.create(userId, contentId, InteractionType.LIKE)
        }
    }

    @Test
    fun create_inactiveContent_isRejectedAsNotInteractable() {
        val f = Fixture()
        f.users.rows[userId.value] = user()
        f.instances.rows[instanceId.value] = instance(allowIndex = true)
        f.contents.rows[contentId.value] = content(status = ContentStatus.DELETED)

        val e = assertFailsWith<InteractionContentNotInteractableException> {
            f.useCase.create(userId, contentId, InteractionType.LIKE)
        }
        assertTrue(e.message!!.contains(contentId.value))
        assertEquals(0, f.interactions.saveCount)
    }

    @Test
    fun create_nonPublicContent_isRejectedAsNotInteractable() {
        val f = Fixture()
        f.users.rows[userId.value] = user()
        f.instances.rows[instanceId.value] = instance(allowIndex = true)
        f.contents.rows[contentId.value] = content(visibility = ContentVisibility.PRIVATE)

        assertFailsWith<InteractionContentNotInteractableException> {
            f.useCase.create(userId, contentId, InteractionType.LIKE)
        }
        assertEquals(0, f.interactions.saveCount)
    }

    @Test
    fun create_contentOnDisallowedInstance_isRejectedAsNotInteractable() {
        val f = Fixture()
        f.users.rows[userId.value] = user()
        f.instances.rows[instanceId.value] = instance(allowIndex = false)
        f.contents.rows[contentId.value] = content()

        assertFailsWith<InteractionContentNotInteractableException> {
            f.useCase.create(userId, contentId, InteractionType.LIKE)
        }
        assertEquals(0, f.interactions.saveCount)
    }

    @Test
    fun create_contentWithMissingInstanceRow_isRejectedAsNotInteractable() {
        val f = Fixture()
        f.users.rows[userId.value] = user()
        // No instance row at all (defensive path; DB FK normally prevents it).
        f.contents.rows[contentId.value] = content()

        assertFailsWith<InteractionContentNotInteractableException> {
            f.useCase.create(userId, contentId, InteractionType.LIKE)
        }
        assertEquals(0, f.interactions.saveCount)
    }

    @Test
    fun create_unlistedContent_isRejectedAsNotInteractable() {
        val f = Fixture()
        f.users.rows[userId.value] = user()
        f.instances.rows[instanceId.value] = instance(allowIndex = true)
        f.contents.rows[contentId.value] = content(visibility = ContentVisibility.UNLISTED)

        assertFailsWith<InteractionContentNotInteractableException> {
            f.useCase.create(userId, contentId, InteractionType.LIKE)
        }
        assertNull(f.interactions.findByUserContentType(userId, contentId, InteractionType.LIKE))
    }
}
