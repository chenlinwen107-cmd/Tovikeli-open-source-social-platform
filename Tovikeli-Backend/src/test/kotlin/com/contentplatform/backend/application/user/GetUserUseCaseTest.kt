package com.contentplatform.backend.application.user

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.UserRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Application tests for [GetUserUseCase] (Stage 16).
 */
class GetUserUseCaseTest {

    private class FakeUserRepository : UserRepository {
        val rows = mutableMapOf<String, User>()

        override fun findById(id: PublicId): User? = rows[id.value]

        override fun findByUsernameCaseInsensitive(username: String): User? = null

        override fun save(user: User): User {
            rows[user.id.value] = user
            return user
        }
    }

    private fun user(id: String, status: UserStatus = UserStatus.ACTIVE): User = User(
        id = PublicId(id),
        username = "alice",
        displayName = "Alice",
        status = status,
        createdAt = 1L,
        updatedAt = 1L
    )

    @Test
    fun getById_whenUserExists_returnsTheSameAggregate() {
        val repo = FakeUserRepository()
        val stored = repo.save(user("usr_1", UserStatus.DISABLED))
        val useCase = GetUserUseCase(repo)

        val result = useCase.getById(stored.id)

        assertEquals(stored, result)
        assertEquals(UserStatus.DISABLED, result?.status)
    }

    @Test
    fun getById_whenAbsent_returnsNull() {
        val useCase = GetUserUseCase(FakeUserRepository())

        assertNull(useCase.getById(PublicId("usr_missing")))
    }
}
