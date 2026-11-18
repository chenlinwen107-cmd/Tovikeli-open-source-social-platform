package com.contentplatform.backend.application.user

import com.contentplatform.backend.domain.common.IdentityHash
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.UserCredentialRepository
import com.contentplatform.backend.repository.UserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

class CreateUserUseCaseTest {

    private class FakeUserRepository : UserRepository {
        val rows = mutableMapOf<String, User>()
        var saveCount = 0
            private set
        var findByUsernameCount = 0
            private set
        var duplicateKeyOnSave = false

        override fun findById(id: PublicId): User? = rows[id.value]

        override fun findByUsernameCaseInsensitive(username: String): User? {
            findByUsernameCount++
            return rows.values.firstOrNull {
                it.username.equals(username, ignoreCase = true)
            }
        }

        override fun search(query: String, limit: Int): List<User> = emptyList()

        override fun updateProfile(user: User): User {
            rows[user.id.value] = user
            return user
        }

        override fun save(user: User): User {
            saveCount++
            if (duplicateKeyOnSave) {
                throw DuplicateKeyException(
                    "duplicate key value violates unique constraint \"uq_platform_user_username\""
                )
            }
            rows[user.id.value] = user
            return user
        }
    }

    private class FakeUserCredentialRepository : UserCredentialRepository {
        val passwordHashes = mutableMapOf<String, String>()
        var saveCount = 0
            private set

        override fun findPasswordHashByUsername(username: String): String? =
            passwordHashes.entries.firstOrNull { (_, _) ->
                false
            }?.value

        override fun savePasswordHash(userId: PublicId, passwordHash: String) {
            saveCount++
            passwordHashes[userId.value] = passwordHash
        }
    }

    private class FakePasswordEncoder : PasswordEncoder {
        val seenPasswords = mutableListOf<String>()

        override fun encode(rawPassword: CharSequence?): String? {
            val value = requireNotNull(rawPassword).toString()
            seenPasswords += value
            return "HASHED:$value"
        }

        override fun matches(
            rawPassword: CharSequence?,
            encodedPassword: String?
        ): Boolean =
            rawPassword != null &&
                encodedPassword == "HASHED:${rawPassword}"
    }

    private class RecordingTransactionManager : PlatformTransactionManager {
        var commitCount = 0
            private set
        var rollbackCount = 0
            private set

        override fun getTransaction(
            definition: TransactionDefinition?
        ): TransactionStatus = SimpleTransactionStatus()

        override fun commit(status: TransactionStatus) {
            commitCount++
        }

        override fun rollback(status: TransactionStatus) {
            rollbackCount++
        }
    }

    private fun fixture() = object {
        val repository = FakeUserRepository()
        val credentialRepository = FakeUserCredentialRepository()
        val passwordEncoder = FakePasswordEncoder()
        val txManager = RecordingTransactionManager()
        val useCase = CreateUserUseCase(
            repository,
            credentialRepository,
            passwordEncoder,
            TransactionTemplate(txManager)
        )
    }

    @Test
    fun create_newUsername_createsActiveUserAndCredentialAndCommits() {
        val f = fixture()

        val user = f.useCase.create(
            username = "alice",
            password = "secret123",
            displayName = "Alice Example"
        )

        assertEquals(
            PublicId("usr_" + IdentityHash.derive("alice")),
            user.id
        )
        assertEquals("alice", user.username)
        assertEquals("Alice Example", user.displayName)
        assertEquals(UserStatus.ACTIVE, user.status)

        assertEquals(1, f.repository.saveCount)
        assertEquals(1, f.credentialRepository.saveCount)

        assertEquals(
            "HASHED:secret123",
            f.credentialRepository.passwordHashes[user.id.value]
        )
        assertEquals(listOf("secret123"), f.passwordEncoder.seenPasswords)

        assertEquals(1, f.txManager.commitCount)
        assertEquals(0, f.txManager.rollbackCount)

        assertNotNull(f.credentialRepository.passwordHashes[user.id.value])
        assertTrue(
            f.credentialRepository.passwordHashes[user.id.value] != "secret123"
        )
    }

    @Test
    fun create_withoutDisplayName_persistsNullDisplayNameAndCredential() {
        val f = fixture()

        val user = f.useCase.create(
            username = "bob",
            password = "secret123",
            displayName = null
        )

        assertEquals(null, user.displayName)
        assertEquals(1, f.credentialRepository.saveCount)
    }

    @Test
    fun create_duplicateUsername_doesNotEncodeOrSave() {
        val f = fixture()
        f.useCase.create(
            username = "alice",
            password = "secret123",
            displayName = null
        )

        val hashCountBefore = f.passwordEncoder.seenPasswords.size
        val saveCountBefore = f.credentialRepository.saveCount

        assertFailsWith<UsernameAlreadyExistsException> {
            f.useCase.create(
                username = "ALICE",
                password = "another-secret",
                displayName = null
            )
        }

        assertEquals(hashCountBefore, f.passwordEncoder.seenPasswords.size)
        assertEquals(saveCountBefore, f.credentialRepository.saveCount)
        assertEquals(1, f.txManager.commitCount)
        assertTrue(f.txManager.rollbackCount >= 1)
    }

    @Test
    fun create_duplicateKeyOnUserSave_translatesToUsernameAlreadyExists() {
        val f = fixture()
        f.repository.duplicateKeyOnSave = true

        assertFailsWith<UsernameAlreadyExistsException> {
            f.useCase.create(
                username = "alice",
                password = "secret123",
                displayName = null
            )
        }

        assertEquals(1, f.repository.saveCount)
        assertEquals(0, f.credentialRepository.saveCount)
        assertEquals(0, f.txManager.commitCount)
        assertTrue(f.txManager.rollbackCount >= 1)
    }
}
