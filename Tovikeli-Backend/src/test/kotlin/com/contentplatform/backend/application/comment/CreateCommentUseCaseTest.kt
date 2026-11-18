package com.contentplatform.backend.application.comment

import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.source.Instance
import com.contentplatform.backend.domain.source.InstanceHealthStatus
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.CommentRepository
import com.contentplatform.backend.repository.ContentBrowsePage
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.InstanceRepository
import com.contentplatform.backend.repository.UserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

/**
 * Application tests for [CreateCommentUseCase] (Stage 18).
 */
class CreateCommentUseCaseTest {

    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    private class FakeUserRepository : UserRepository {
        val rows = mutableMapOf<String, User>()

        override fun findById(id: PublicId): User? =
            rows[id.value]

        override fun findByUsernameCaseInsensitive(username: String): User? =
            null

        override fun save(user: User): User {
            rows[user.id.value] = user
            return user
        }
    }

    private class FakeInstanceRepository : InstanceRepository {
        val rows = mutableMapOf<String, Instance>()

        override fun findById(id: PublicId): Instance? =
            rows[id.value]
    }

    private class FakeContentRepository : ContentRepository {
        val rows = mutableMapOf<String, Content>()

        override fun findById(id: PublicId): Content? =
            rows[id.value]

        override fun findByInstanceAndSourceContentId(
            instanceId: PublicId,
            sourceContentId: String
        ): Content? =
            rows.values.firstOrNull {
                it.instanceId == instanceId &&
                    it.sourceContentId == sourceContentId
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
            limit: Int
        ): ContentBrowsePage =
            throw UnsupportedOperationException("not used")

        override fun searchPublicContent(
            query: String,
            instanceId: PublicId?,
            authorId: PublicId?,
            sourceId: PublicId?,
            limit: Int
        ): List<Content> =
            throw UnsupportedOperationException("not used")

        override fun findPublicContentByTag(
            tagId: PublicId,
            cursor: ContentCursor?,
            limit: Int
        ): ContentBrowsePage =
            throw UnsupportedOperationException("not used")
    }

    private class FakeCommentRepository : CommentRepository {
        val rows = mutableMapOf<String, Comment>()
        var saveCount = 0
            private set

        override fun save(comment: Comment): Comment {
            saveCount++
            rows[comment.id.value] = comment
            return comment
        }

        override fun findById(id: PublicId): Comment? =
            rows[id.value]

        override fun findByContentId(
            contentId: PublicId,
            limit: Int
        ): List<Comment> =
            rows.values
                .filter {
                    it.contentId == contentId &&
                        it.status.name == "ACTIVE"
                }
                .sortedWith(
                    compareBy<Comment> { it.createdAt }
                        .thenBy { it.id.value }
                )
                .take(limit)

        override fun findByParentCommentId(
            parentCommentId: PublicId,
            limit: Int
        ): List<Comment> =
            rows.values
                .filter {
                    it.parentCommentId == parentCommentId &&
                        it.status.name == "ACTIVE"
                }
                .sortedWith(
                    compareBy<Comment> { it.createdAt }
                        .thenBy { it.id.value }
                )
                .take(limit)

        override fun update(comment: Comment): Comment {
            rows[comment.id.value] = comment
            return comment
        }
    }

    private class RecordingTransactionManager :
        PlatformTransactionManager {

        var commitCount = 0
            private set

        var rollbackCount = 0
            private set

        override fun getTransaction(
            definition: TransactionDefinition?
        ): TransactionStatus =
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
    private val otherUserId = PublicId("usr_bob")
    private val contentId = PublicId("ct_1")
    private val otherContentId = PublicId("ct_2")
    private val parentId = PublicId("cmt_parent")

    private fun instance(
        allowIndex: Boolean = true
    ): Instance =
        Instance(
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

    private fun user(
        id: PublicId = userId
    ): User =
        User(
            id = id,
            username = if (id == userId) "alice" else "bob",
            displayName = null,
            status = UserStatus.ACTIVE,
            createdAt = 1L,
            updatedAt = 1L
        )

    private fun content(
        id: PublicId = contentId,
        status: ContentStatus = ContentStatus.ACTIVE,
        visibility: ContentVisibility = ContentVisibility.PUBLIC
    ): Content =
        Content(
            id = id,
            instanceId = instanceId,
            sourceContentId = "sc-${id.value}",
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
            externalCreatedAt = null,
            createdAt = 1L,
            updatedAt = 1L,
            metadata = emptyMap()
        )

    private fun parentComment(
        contentId: PublicId = this.contentId
    ): Comment =
        Comment(
            id = parentId,
            userId = userId,
            contentId = contentId,
            parentCommentId = null,
            text = "parent",
            status = com.contentplatform.backend.domain.comment.CommentStatus.ACTIVE,
            createdAt = 1L,
            updatedAt = 1L
        )

    private inner class Fixture {
        val users = FakeUserRepository()
        val instances = FakeInstanceRepository()
        val contents = FakeContentRepository()
        val comments = FakeCommentRepository()
        val txManager = RecordingTransactionManager()

        val useCase = CreateCommentUseCase(
            userRepository = users,
            contentRepository = contents,
            instanceRepository = instances,
            commentRepository = comments,
            transactionTemplate = TransactionTemplate(txManager)
        )

        fun withInteractableTarget() = apply {
            users.rows[userId.value] = user()
            instances.rows[instanceId.value] =
                instance(allowIndex = true)
            contents.rows[contentId.value] = content()
        }
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    fun create_validTopLevelComment_createsAndPersistsInOneTransaction() {
        val f = Fixture().withInteractableTarget()

        val created = f.useCase.create(
            userId = userId,
            contentId = contentId,
            parentCommentId = null,
            text = "hello"
        )

        assertTrue(created.id.value.startsWith("cmt_"))
        assertEquals(userId, created.userId)
        assertEquals(contentId, created.contentId)
        assertNull(created.parentCommentId)
        assertEquals("hello", created.text)
        assertEquals(1, f.comments.saveCount)
        assertEquals(created, f.comments.findById(created.id))
        assertEquals(1, f.txManager.commitCount)
        assertEquals(0, f.txManager.rollbackCount)
    }

    @Test
    fun create_replyToExistingParent_createsCommentWithParent() {
        val f = Fixture().withInteractableTarget()

        f.comments.rows[parentId.value] =
            parentComment(contentId)

        val created = f.useCase.create(
            userId = userId,
            contentId = contentId,
            parentCommentId = parentId,
            text = "reply"
        )

        assertEquals(parentId, created.parentCommentId)
        assertEquals(contentId, created.contentId)
        assertEquals("reply", created.text)
        assertEquals(1, f.comments.saveCount)
    }

    @Test
    fun create_sameUserCanCreateMultipleCommentsOnSameContent() {
        val f = Fixture().withInteractableTarget()

        val first = f.useCase.create(
            userId,
            contentId,
            null,
            "first"
        )

        val second = f.useCase.create(
            userId,
            contentId,
            null,
            "second"
        )

        assertTrue(first.id != second.id)
        assertEquals(2, f.comments.saveCount)
        assertEquals(2, f.comments.rows.size)
    }

    // ------------------------------------------------------------------
    // Validation failures
    // ------------------------------------------------------------------

    @Test
    fun create_whenUserMissing_throwsUserNotFoundAndSavesNothing() {
        val f = Fixture()

        f.instances.rows[instanceId.value] =
            instance(allowIndex = true)
        f.contents.rows[contentId.value] =
            content()

        val e = assertFailsWith<CommentUserNotFoundException> {
            f.useCase.create(
                userId,
                contentId,
                null,
                "hello"
            )
        }

        assertTrue(e.message!!.contains(userId.value))
        assertEquals(0, f.comments.saveCount)
        assertEquals(0, f.txManager.commitCount)
        assertTrue(f.txManager.rollbackCount >= 1)
    }

    @Test
    fun create_whenContentMissing_throwsContentNotFoundAndSavesNothing() {
        val f = Fixture()

        f.users.rows[userId.value] = user()

        assertFailsWith<CommentContentNotFoundException> {
            f.useCase.create(
                userId,
                contentId,
                null,
                "hello"
            )
        }

        assertEquals(0, f.comments.saveCount)
    }

    @Test
    fun create_whenUserAndContentMissing_throwsUserNotFound() {
        val f = Fixture()

        assertFailsWith<CommentUserNotFoundException> {
            f.useCase.create(
                userId,
                contentId,
                null,
                "hello"
            )
        }

        assertEquals(0, f.comments.saveCount)
    }

    @Test
    fun create_inactiveContent_isRejectedAsNotInteractable() {
        val f = Fixture()

        f.users.rows[userId.value] = user()
        f.instances.rows[instanceId.value] =
            instance(allowIndex = true)
        f.contents.rows[contentId.value] =
            content(status = ContentStatus.DELETED)

        val e =
            assertFailsWith<CommentContentNotInteractableException> {
                f.useCase.create(
                    userId,
                    contentId,
                    null,
                    "hello"
                )
            }

        assertTrue(e.message!!.contains(contentId.value))
        assertEquals(0, f.comments.saveCount)
    }

    @Test
    fun create_nonPublicContent_isRejectedAsNotInteractable() {
        val f = Fixture()

        f.users.rows[userId.value] = user()
        f.instances.rows[instanceId.value] =
            instance(allowIndex = true)
        f.contents.rows[contentId.value] =
            content(
                visibility = ContentVisibility.PRIVATE
            )

        assertFailsWith<CommentContentNotInteractableException> {
            f.useCase.create(
                userId,
                contentId,
                null,
                "hello"
            )
        }

        assertEquals(0, f.comments.saveCount)
    }

    @Test
    fun create_contentOnDisallowedInstance_isRejectedAsNotInteractable() {
        val f = Fixture()

        f.users.rows[userId.value] = user()
        f.instances.rows[instanceId.value] =
            instance(allowIndex = false)
        f.contents.rows[contentId.value] =
            content()

        assertFailsWith<CommentContentNotInteractableException> {
            f.useCase.create(
                userId,
                contentId,
                null,
                "hello"
            )
        }

        assertEquals(0, f.comments.saveCount)
    }

    @Test
    fun create_contentWithMissingInstanceRow_isRejectedAsNotInteractable() {
        val f = Fixture()

        f.users.rows[userId.value] = user()
        f.contents.rows[contentId.value] =
            content()

        assertFailsWith<CommentContentNotInteractableException> {
            f.useCase.create(
                userId,
                contentId,
                null,
                "hello"
            )
        }

        assertEquals(0, f.comments.saveCount)
    }

    @Test
    fun create_unlistedContent_isRejectedAsNotInteractable() {
        val f = Fixture()

        f.users.rows[userId.value] = user()
        f.instances.rows[instanceId.value] =
            instance(allowIndex = true)
        f.contents.rows[contentId.value] =
            content(
                visibility = ContentVisibility.UNLISTED
            )

        assertFailsWith<CommentContentNotInteractableException> {
            f.useCase.create(
                userId,
                contentId,
                null,
                "hello"
            )
        }

        assertEquals(0, f.comments.saveCount)
    }

    // ------------------------------------------------------------------
    // Parent validation
    // ------------------------------------------------------------------

    @Test
    fun create_whenParentMissing_throwsParentNotFoundAndSavesNothing() {
        val f = Fixture().withInteractableTarget()

        assertFailsWith<CommentParentNotFoundException> {
            f.useCase.create(
                userId,
                contentId,
                parentId,
                "reply"
            )
        }

        assertEquals(0, f.comments.saveCount)
    }

    @Test
    fun create_whenParentBelongsToDifferentContent_throwsMismatchAndSavesNothing() {
        val f = Fixture().withInteractableTarget()

        f.contents.rows[otherContentId.value] =
            content(id = otherContentId)

        f.comments.rows[parentId.value] =
            parentComment(otherContentId)

        val e =
            assertFailsWith<CommentParentContentMismatchException> {
                f.useCase.create(
                    userId,
                    contentId,
                    parentId,
                    "reply"
                )
            }

        assertTrue(
            e.message!!.contains(otherContentId.value)
        )
        assertEquals(0, f.comments.saveCount)
    }

    // ------------------------------------------------------------------
    // Text validation
    // ------------------------------------------------------------------

    @Test
    fun create_blankText_isRejectedAndSavesNothing() {
        val f = Fixture().withInteractableTarget()

        assertFailsWith<IllegalArgumentException> {
            f.useCase.create(
                userId,
                contentId,
                null,
                "   "
            )
        }

        assertEquals(0, f.comments.saveCount)
    }
}
