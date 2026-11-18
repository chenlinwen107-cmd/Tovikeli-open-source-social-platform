package com.contentplatform.backend.application.interaction

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.interaction.Interaction
import com.contentplatform.backend.domain.interaction.InteractionCursor
import com.contentplatform.backend.domain.interaction.InteractionPage
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.InteractionRepository
import com.contentplatform.backend.repository.UserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetUserInteractionHistoryUseCaseTest {

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

    private class FakeInteractionRepository : InteractionRepository {
        var lastFindUserId: PublicId? = null
            private set
        var lastFindType: InteractionType? = null
            private set
        var lastFindLimit: Int? = null
            private set
        var lastFindCursor: InteractionCursor? = null
            private set
        var findResult: InteractionPage = InteractionPage(emptyList(), null)

        override fun save(interaction: Interaction): Interaction = throw UnsupportedOperationException("not used")
        override fun findById(id: PublicId): Interaction? = throw UnsupportedOperationException("not used")
        override fun findByUserContentType(userId: PublicId, contentId: PublicId, type: InteractionType): Interaction? =
            throw UnsupportedOperationException("not used")
        override fun deleteByUserContentType(userId: PublicId, contentId: PublicId, type: InteractionType): Boolean =
            throw UnsupportedOperationException("not used")

        override fun findByUserId(
            userId: PublicId,
            type: InteractionType?,
            limit: Int,
            cursor: InteractionCursor?
        ): InteractionPage {
            lastFindUserId = userId
            lastFindType = type
            lastFindLimit = limit
            lastFindCursor = cursor
            return findResult
        }

        override fun countByContentId(contentId: PublicId): Map<InteractionType, Long> = emptyMap()
    }

    private class FakeContentRepository : ContentRepository {
        var lastFindByIdsIds: List<PublicId>? = null
            private set
        var findByIdsResult: List<Content> = emptyList()

        override fun findById(id: PublicId): Content? = null
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

        override fun findByIds(ids: List<PublicId>): List<Content> {
            lastFindByIdsIds = ids
            return findByIdsResult
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private val userId = PublicId("usr_alice")

    private fun user(status: UserStatus = UserStatus.ACTIVE): User = User(
        id = userId,
        username = "alice",
        displayName = "Alice",
        avatar = null,

        bio = null,

        gender = "secret",

        location = null,

        cover = null,

        status = status,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun interaction(
        contentIdValue: String = "ct_1",
        type: InteractionType = InteractionType.LIKE
    ): Interaction = Interaction.create(
        userId = userId,
        contentId = PublicId(contentIdValue),
        type = type
    )

    private fun sampleContent(
        id: String = "ct_sc1",
        sourceContentId: String = "sc_1"
    ): Content = Content(
        id = PublicId(id),
        instanceId = PublicId("inst_1"),
        sourceContentId = sourceContentId,
        authorId = PublicId("au_1"),
        sourceId = PublicId("src_1"),
        text = "sample",
        language = "en",
        visibility = ContentVisibility.PUBLIC,
        status = ContentStatus.ACTIVE,
        mediaIds = emptyList(),
        parentId = null,
        repostOfId = null,
        quoteOfId = null,
        topicId = null,
        externalCreatedAt = null,
        createdAt = 1000L,
        updatedAt = 1000L,
        metadata = emptyMap()
    )

    private fun useCase(
        users: FakeUserRepository = FakeUserRepository().also { it.rows[userId.value] = user() },
        interactions: FakeInteractionRepository = FakeInteractionRepository(),
        contentRepo: FakeContentRepository = FakeContentRepository()
    ) = GetUserInteractionHistoryUseCase(users, interactions, contentRepo, org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java))

    // ------------------------------------------------------------------
    // 1. User exists -> InteractionRepository is called
    // ------------------------------------------------------------------

    @Test
    fun getHistory_whenUserExists_callsInteractionRepository() {
        val interactions = FakeInteractionRepository()
        interactions.findResult = InteractionPage(emptyList(), null)
        val uc = useCase(interactions = interactions)

        uc.getHistory(userId, null, 20, null)

        assertEquals(userId, interactions.lastFindUserId, "InteractionRepository must be called")
    }

    // ------------------------------------------------------------------
    // 2-5. Parameters pass-through
    // ------------------------------------------------------------------

    @Test
    fun getHistory_passesUserIdThroughUnchanged() {
        val interactions = FakeInteractionRepository()
        val uc = useCase(interactions = interactions)
        uc.getHistory(userId, null, 20, null)
        assertEquals(userId, interactions.lastFindUserId)
    }

    @Test
    fun getHistory_passesTypeThroughUnchanged() {
        val interactions = FakeInteractionRepository()
        val uc = useCase(interactions = interactions)
        uc.getHistory(userId, InteractionType.FAVORITE, 20, null)
        assertEquals(InteractionType.FAVORITE, interactions.lastFindType)
    }

    @Test
    fun getHistory_passesNullTypeThroughUnchanged() {
        val interactions = FakeInteractionRepository()
        val uc = useCase(interactions = interactions)
        uc.getHistory(userId, null, 20, null)
        assertNull(interactions.lastFindType)
    }

    @Test
    fun getHistory_passesLimitThroughUnchanged() {
        val interactions = FakeInteractionRepository()
        val uc = useCase(interactions = interactions)
        uc.getHistory(userId, null, 42, null)
        assertEquals(42, interactions.lastFindLimit)
    }

    @Test
    fun getHistory_passesCursorThroughUnchanged() {
        val interactions = FakeInteractionRepository()
        val uc = useCase(interactions = interactions)
        val cursor = InteractionCursor(createdAt = 1_757_000_000_123L, interactionId = PublicId("int_abc"))
        uc.getHistory(userId, null, 20, cursor)
        assertEquals(cursor, interactions.lastFindCursor)
    }

    @Test
    fun getHistory_passesNullCursorThroughUnchanged() {
        val interactions = FakeInteractionRepository()
        val uc = useCase(interactions = interactions)
        uc.getHistory(userId, null, 20, null)
        assertNull(interactions.lastFindCursor)
    }

    // ------------------------------------------------------------------
    // 6. Page forwarding with correct nextCursor
    // ------------------------------------------------------------------

    @Test
    fun getHistory_repositoryReturnsPage_forwardsCorrectNextCursor() {
        val interactions = FakeInteractionRepository()
        val nextCursor = InteractionCursor(createdAt = 999L, interactionId = PublicId("int_2"))
        val i1 = interaction("ct_1", InteractionType.LIKE)
        interactions.findResult = InteractionPage(listOf(i1), nextCursor)
        val contentRepo = FakeContentRepository()
        contentRepo.findByIdsResult = listOf(sampleContent("ct_1", "sc_1"))
        val uc = useCase(interactions = interactions, contentRepo = contentRepo)

        val result = uc.getHistory(userId, null, 20, null)

        assertEquals(nextCursor, result.nextCursor)
    }

    // ------------------------------------------------------------------
    // 7. User not found -> InteractionQueryUserNotFoundException
    // ------------------------------------------------------------------

    @Test
    fun getHistory_whenUserNotFound_throwsUserNotFoundException() {
        val users = FakeUserRepository()
        val interactions = FakeInteractionRepository()
        val uc = GetUserInteractionHistoryUseCase(users, interactions, FakeContentRepository(), org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java))

        assertFailsWith<InteractionQueryUserNotFoundException> {
            uc.getHistory(userId, null, 20, null)
        }
        assertNull(interactions.lastFindUserId)
    }

    // ------------------------------------------------------------------
    // 8. DISABLED user still allows query
    // ------------------------------------------------------------------

    @Test
    fun getHistory_whenUserDisabled_allowsQuery() {
        val users = FakeUserRepository()
        users.rows[userId.value] = user(UserStatus.DISABLED)
        val interactions = FakeInteractionRepository()
        interactions.findResult = InteractionPage(listOf(interaction()), null)
        val uc = GetUserInteractionHistoryUseCase(users, interactions, FakeContentRepository(), org.mockito.Mockito.mock(com.contentplatform.backend.repository.AuthorRepository::class.java))

        val result = uc.getHistory(userId, null, 20, null)

        assertTrue(result.items.isNotEmpty())
        assertEquals(userId, interactions.lastFindUserId)
    }

    // ------------------------------------------------------------------
    // 9. Empty page -> no content fetch
    // ------------------------------------------------------------------

    @Test
    fun getHistory_emptyPage_returnsEmptyHydratedPage() {
        val interactions = FakeInteractionRepository()
        interactions.findResult = InteractionPage(emptyList(), null)
        val contentRepo = FakeContentRepository()
        val uc = useCase(interactions = interactions, contentRepo = contentRepo)

        val result = uc.getHistory(userId, InteractionType.BOOKMARK, 10, null)

        assertTrue(result.items.isEmpty())
        assertNull(result.nextCursor)
        assertNull(contentRepo.lastFindByIdsIds, "findByIds must not be called for empty page")
    }

    // ------------------------------------------------------------------
    // 10. Hydration: existing content -> hydrated
    // ------------------------------------------------------------------

    @Test
    fun getHistory_withExistingContent_hydratesContent() {
        val content = sampleContent("ct_1", "sc_1")
        val i1 = interaction("ct_1", InteractionType.LIKE)
        val interactions = FakeInteractionRepository()
        interactions.findResult = InteractionPage(listOf(i1), null)

        val contentRepo = FakeContentRepository()
        contentRepo.findByIdsResult = listOf(content)

        val uc = useCase(interactions = interactions, contentRepo = contentRepo)
        val result = uc.getHistory(userId, null, 20, null)

        assertEquals(1, result.items.size)
        assertEquals(i1, result.items[0].interaction)
        assertEquals(content, result.items[0].content)
    }

    // ------------------------------------------------------------------
    // 11. Hydration: missing content -> null
    // ------------------------------------------------------------------

    @Test
    fun getHistory_withMissingContent_contentIsNull() {
        val i1 = interaction("ct_missing", InteractionType.LIKE)
        val interactions = FakeInteractionRepository()
        interactions.findResult = InteractionPage(listOf(i1), null)

        val contentRepo = FakeContentRepository()
        contentRepo.findByIdsResult = emptyList()

        val uc = useCase(interactions = interactions, contentRepo = contentRepo)
        val result = uc.getHistory(userId, null, 20, null)

        assertEquals(1, result.items.size)
        assertEquals(i1, result.items[0].interaction)
        assertNull(result.items[0].content)
    }

    // ------------------------------------------------------------------
    // 12. Batch hydration: single findByIds call
    // ------------------------------------------------------------------

    @Test
    fun getHistory_multipleInteractions_singleBatchCall() {
        val i1 = interaction("ct_1", InteractionType.LIKE)
        val i2 = interaction("ct_1", InteractionType.FAVORITE)
        val i3 = interaction("ct_2", InteractionType.BOOKMARK)
        val interactions = FakeInteractionRepository()
        interactions.findResult = InteractionPage(listOf(i1, i2, i3), null)

        val contentRepo = FakeContentRepository()
        contentRepo.findByIdsResult = listOf(sampleContent("sc_1"))

        val uc = useCase(interactions = interactions, contentRepo = contentRepo)
        uc.getHistory(userId, null, 20, null)

        assertEquals(2, contentRepo.lastFindByIdsIds!!.size, "2 distinct content IDs: ct_1 and ct_2")
        assertTrue(contentRepo.lastFindByIdsIds!!.contains(PublicId("ct_1")))
        assertTrue(contentRepo.lastFindByIdsIds!!.contains(PublicId("ct_2")))
    }
}
