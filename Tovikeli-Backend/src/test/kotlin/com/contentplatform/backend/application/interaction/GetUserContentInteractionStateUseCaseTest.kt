package com.contentplatform.backend.application.interaction

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.interaction.Interaction
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.domain.interaction.UserContentInteractionState
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.InteractionRepository
import com.contentplatform.backend.repository.UserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetUserContentInteractionStateUseCaseTest {

    private val userId = PublicId("au_user")
    private val contentId = PublicId("ct_content")

    private class FakeUserRepository(private val user: User?) : UserRepository {
        override fun findById(id: PublicId): User? = user
        override fun findByUsernameCaseInsensitive(username: String): User? = null
        override fun search(query: String, limit: Int): List<User> = emptyList()

        override fun updateProfile(user: User): User = user


        override fun save(user: User): User = user
    }

    private class FakeContentRepository(private val content: Content?) : ContentRepository {
        override fun findById(id: PublicId): Content? = content
        override fun findByInstanceAndSourceContentId(instanceId: PublicId, sourceContentId: String): Content? = null
        override fun save(content: Content): Content = content
        override fun findPublicContent(
            instanceId: PublicId?, authorId: PublicId?, hasMedia: Boolean?,
            cursor: com.contentplatform.backend.repository.ContentCursor?, limit: Int,
            viewerAuthorId: PublicId?
        ): com.contentplatform.backend.repository.ContentBrowsePage =
            com.contentplatform.backend.repository.ContentBrowsePage(emptyList(), null)
        override fun searchPublicContent(
            query: String, instanceId: PublicId?, authorId: PublicId?, sourceId: PublicId?, limit: Int
        ): List<Content> = emptyList()
        override fun findPublicContentByTag(
            tagId: PublicId, cursor: com.contentplatform.backend.repository.ContentCursor?, limit: Int
        ): com.contentplatform.backend.repository.ContentBrowsePage =
            com.contentplatform.backend.repository.ContentBrowsePage(emptyList(), null)
        override fun findByIds(ids: List<PublicId>): List<Content> = emptyList()
        override fun findPublicByIds(ids: List<PublicId>): List<Content> = emptyList()
    }

    private class FakeInteractionRepository(
        private val interactions: List<Interaction> = emptyList()
    ) : InteractionRepository {
        var lastUserId: PublicId? = null
            private set
        var lastContentId: PublicId? = null
            private set
        override fun save(interaction: Interaction): Interaction = interaction
        override fun findById(id: PublicId): Interaction? = null
        override fun findByUserContentType(userId: PublicId, contentId: PublicId, type: InteractionType): Interaction? = null
        override fun deleteByUserContentType(userId: PublicId, contentId: PublicId, type: InteractionType): Boolean = false
        override fun findByUserId(userId: PublicId, type: InteractionType?, limit: Int, cursor: com.contentplatform.backend.domain.interaction.InteractionCursor?): com.contentplatform.backend.domain.interaction.InteractionPage =
            com.contentplatform.backend.domain.interaction.InteractionPage(emptyList(), null)
        override fun countByContentId(contentId: PublicId): Map<InteractionType, Long> = emptyMap()
        override fun findByUserAndContent(userId: PublicId, contentId: PublicId): List<Interaction> {
            lastUserId = userId
            lastContentId = contentId
            return interactions
        }
    }

    private fun user(): User = User.create(username = "alice", displayName = "Alice")

    private fun content(status: ContentStatus = ContentStatus.ACTIVE): Content = Content.createFromAdapter(
        instanceIdValue = "inst_1",
        sourceContentId = "sc_1",
        authorId = PublicId("au_author"),
        sourceId = PublicId("src_1"),
        text = "content",
        language = "en",
        visibility = ContentVisibility.PUBLIC,
        mediaIds = emptyList(),
        parentId = null,
        repostOfId = null,
        quoteOfId = null,
        externalCreatedAt = null,
        metadata = emptyMap()
    ).let { it.copy(id = contentId, status = status) }

    private fun interaction(type: InteractionType): Interaction =
        Interaction.create(userId = userId, contentId = contentId, type = type)

    // ------------------------------------------------------------------
    // User missing → null
    // ------------------------------------------------------------------

    @Test
    fun getState_userNotFound_returnsNull() {
        val useCase = GetUserContentInteractionStateUseCase(
            FakeUserRepository(null),
            FakeContentRepository(content()),
            FakeInteractionRepository()
        )

        val state = useCase.getState(userId, contentId)

        assertNull(state)
    }

    // ------------------------------------------------------------------
    // Content missing → null
    // ------------------------------------------------------------------

    @Test
    fun getState_contentNotFound_returnsNull() {
        val useCase = GetUserContentInteractionStateUseCase(
            FakeUserRepository(user()),
            FakeContentRepository(null),
            FakeInteractionRepository()
        )

        val state = useCase.getState(userId, contentId)

        assertNull(state)
    }

    // ------------------------------------------------------------------
    // DELETED content → null
    // ------------------------------------------------------------------

    @Test
    fun getState_deletedContent_returnsNull() {
        val useCase = GetUserContentInteractionStateUseCase(
            FakeUserRepository(user()),
            FakeContentRepository(content(status = ContentStatus.DELETED)),
            FakeInteractionRepository()
        )

        val state = useCase.getState(userId, contentId)

        assertNull(state)
    }

    // ------------------------------------------------------------------
    // HIDDEN content → null
    // ------------------------------------------------------------------

    @Test
    fun getState_hiddenContent_returnsNull() {
        val useCase = GetUserContentInteractionStateUseCase(
            FakeUserRepository(user()),
            FakeContentRepository(content(status = ContentStatus.HIDDEN)),
            FakeInteractionRepository()
        )

        val state = useCase.getState(userId, contentId)

        assertNull(state)
    }

    // ------------------------------------------------------------------
    // UNAVAILABLE content → null
    // ------------------------------------------------------------------

    @Test
    fun getState_unavailableContent_returnsNull() {
        val useCase = GetUserContentInteractionStateUseCase(
            FakeUserRepository(user()),
            FakeContentRepository(content(status = ContentStatus.UNAVAILABLE)),
            FakeInteractionRepository()
        )

        val state = useCase.getState(userId, contentId)

        assertNull(state)
    }

    // ------------------------------------------------------------------
    // UNKNOWN content → null
    // ------------------------------------------------------------------

    @Test
    fun getState_unknownContent_returnsNull() {
        val useCase = GetUserContentInteractionStateUseCase(
            FakeUserRepository(user()),
            FakeContentRepository(content(status = ContentStatus.UNKNOWN)),
            FakeInteractionRepository()
        )

        val state = useCase.getState(userId, contentId)

        assertNull(state)
    }

    // ------------------------------------------------------------------
    // No interactions → all false
    // ------------------------------------------------------------------

    @Test
    fun getState_noInteractions_allFalse() {
        val useCase = GetUserContentInteractionStateUseCase(
            FakeUserRepository(user()),
            FakeContentRepository(content()),
            FakeInteractionRepository(emptyList())
        )

        val state = useCase.getState(userId, contentId)!!

        assertEquals(contentId, state.contentId)
        assertFalse(state.like)
        assertFalse(state.favorite)
        assertFalse(state.repost)
        assertFalse(state.bookmark)
    }

    // ------------------------------------------------------------------
    // LIKE only
    // ------------------------------------------------------------------

    @Test
    fun getState_likeOnly_likeTrue() {
        val useCase = GetUserContentInteractionStateUseCase(
            FakeUserRepository(user()),
            FakeContentRepository(content()),
            FakeInteractionRepository(listOf(interaction(InteractionType.LIKE)))
        )

        val state = useCase.getState(userId, contentId)!!

        assertTrue(state.like)
        assertFalse(state.favorite)
        assertFalse(state.repost)
        assertFalse(state.bookmark)
    }

    // ------------------------------------------------------------------
    // Mixed types
    // ------------------------------------------------------------------

    @Test
    fun getState_mixedTypes_correctFlags() {
        val useCase = GetUserContentInteractionStateUseCase(
            FakeUserRepository(user()),
            FakeContentRepository(content()),
            FakeInteractionRepository(
                listOf(
                    interaction(InteractionType.LIKE),
                    interaction(InteractionType.BOOKMARK)
                )
            )
        )

        val state = useCase.getState(userId, contentId)!!

        assertTrue(state.like)
        assertFalse(state.favorite)
        assertFalse(state.repost)
        assertTrue(state.bookmark)
    }

    // ------------------------------------------------------------------
    // All four types
    // ------------------------------------------------------------------

    @Test
    fun getState_allFourTypes_allTrue() {
        val useCase = GetUserContentInteractionStateUseCase(
            FakeUserRepository(user()),
            FakeContentRepository(content()),
            FakeInteractionRepository(
                listOf(
                    interaction(InteractionType.LIKE),
                    interaction(InteractionType.FAVORITE),
                    interaction(InteractionType.REPOST),
                    interaction(InteractionType.BOOKMARK)
                )
            )
        )

        val state = useCase.getState(userId, contentId)!!

        assertTrue(state.like)
        assertTrue(state.favorite)
        assertTrue(state.repost)
        assertTrue(state.bookmark)
    }

    // ------------------------------------------------------------------
    // Repository called exactly once
    // ------------------------------------------------------------------

    @Test
    fun getState_repositoryCalledExactlyOnce() {
        val interactionRepo = FakeInteractionRepository(
            listOf(interaction(InteractionType.LIKE))
        )
        val useCase = GetUserContentInteractionStateUseCase(
            FakeUserRepository(user()),
            FakeContentRepository(content()),
            interactionRepo
        )

        useCase.getState(userId, contentId)

        assertEquals(userId, interactionRepo.lastUserId)
        assertEquals(contentId, interactionRepo.lastContentId)
    }
}
