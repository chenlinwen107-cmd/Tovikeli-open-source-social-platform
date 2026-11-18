package com.contentplatform.backend.application.interaction

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.Interaction
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.repository.InteractionRepository
import com.contentplatform.backend.repository.UserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

/**
 * Application tests for [DeleteInteractionUseCase] (Stage 17).
 */
class DeleteInteractionUseCaseTest {

    // ------------------------------------------------------------------
    // In-memory fake InteractionRepository
    // ------------------------------------------------------------------

    private class FakeInteractionRepository : InteractionRepository {
        val rows = mutableMapOf<String, Interaction>()

        private fun key(userId: PublicId, contentId: PublicId, type: InteractionType): String =
            "${userId.value}:${contentId.value}:${type.name}"

        override fun save(interaction: Interaction): Interaction {
            rows[key(interaction.userId, interaction.contentId, interaction.type)] = interaction
            return interaction
        }

        override fun findById(id: PublicId): Interaction? =
            rows.values.firstOrNull { it.id == id }

        override fun findByUserContentType(
            userId: PublicId,
            contentId: PublicId,
            type: InteractionType
        ): Interaction? = rows[key(userId, contentId, type)]

        override fun deleteByUserContentType(
            userId: PublicId,
            contentId: PublicId,
            type: InteractionType
        ): Boolean = rows.remove(key(userId, contentId, type)) != null
    }

    // ------------------------------------------------------------------
    // In-memory fake UserRepository
    // ------------------------------------------------------------------

    private class FakeUserRepository : UserRepository {
        val users = mutableMapOf<PublicId, User>()

        override fun save(user: User): User {
            users[user.id] = user
            return user
        }

        override fun findByUsernameCaseInsensitive(username: String): User? =
            users.values.firstOrNull { it.username.equals(username, ignoreCase = true) }

        override fun findById(id: PublicId): User? = users[id]
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

    private fun fixture() = object {
        val interactionRepository = FakeInteractionRepository()
        val userRepository = FakeUserRepository()
        val txManager = RecordingTransactionManager()
        val useCase = DeleteInteractionUseCase(
            userRepository,
            interactionRepository,
            TransactionTemplate(txManager)
        )
    }

    private val userId = PublicId("usr_alice")
    private val contentId = PublicId("ct_1")

    private fun fakeUser(id: PublicId = userId): User = User(
        id = id,
        username = id.value.removePrefix("usr_"),
        displayName = null,
        status = com.contentplatform.backend.domain.user.UserStatus.ACTIVE,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L
    )

    // ------------------------------------------------------------------
    // Existing interaction
    // ------------------------------------------------------------------

    @Test
    fun delete_whenInteractionExists_physicallyDeletesItAndReturnsTrue() {
        val f = fixture()
        f.userRepository.users[userId] = fakeUser()
        val saved = f.interactionRepository.save(Interaction.create(userId, contentId, InteractionType.LIKE))

        val deleted = f.useCase.delete(userId, contentId, InteractionType.LIKE)

        assertTrue(deleted)
        assertNull(
            f.interactionRepository.findByUserContentType(userId, contentId, InteractionType.LIKE),
            "the row must be physically gone"
        )
        assertNull(f.interactionRepository.findById(saved.id))
        assertEquals(1, f.txManager.commitCount)
        assertEquals(0, f.txManager.rollbackCount)
    }

    @Test
    fun delete_onlyRemovesTheRequestedType() {
        val f = fixture()
        f.userRepository.users[userId] = fakeUser()
        f.interactionRepository.save(Interaction.create(userId, contentId, InteractionType.LIKE))
        f.interactionRepository.save(Interaction.create(userId, contentId, InteractionType.BOOKMARK))

        val deleted = f.useCase.delete(userId, contentId, InteractionType.LIKE)

        assertTrue(deleted)
        assertNull(f.interactionRepository.findByUserContentType(userId, contentId, InteractionType.LIKE))
        assertTrue(f.interactionRepository.findByUserContentType(userId, contentId, InteractionType.BOOKMARK) != null)
    }

    // ------------------------------------------------------------------
    // Missing interaction / missing targets -> idempotent no-op
    // ------------------------------------------------------------------

    @Test
    fun delete_whenInteractionMissing_returnsFalseWithoutError() {
        val f = fixture()
        f.userRepository.users[userId] = fakeUser()

        val deleted = f.useCase.delete(userId, contentId, InteractionType.REPOST)

        assertFalse(deleted, "a missing interaction is a successful idempotent no-op")
        assertEquals(1, f.txManager.commitCount, "the no-op still runs inside one transaction")
        assertEquals(0, f.txManager.rollbackCount)
    }

    @Test
    fun delete_whenUserNotFound_throwsInteractionUserNotFoundException() {
        val f = fixture()

        assertFailsWith<InteractionUserNotFoundException> {
            f.useCase.delete(PublicId("usr_ghost"), contentId, InteractionType.LIKE)
        }

        assertEquals(0, f.txManager.commitCount)
    }

    @Test
    fun delete_twice_secondCallIsIdempotentFalse() {
        val f = fixture()
        f.userRepository.users[userId] = fakeUser()
        f.interactionRepository.save(Interaction.create(userId, contentId, InteractionType.BOOKMARK))

        assertTrue(f.useCase.delete(userId, contentId, InteractionType.BOOKMARK))
        assertFalse(f.useCase.delete(userId, contentId, InteractionType.BOOKMARK))
    }
}
