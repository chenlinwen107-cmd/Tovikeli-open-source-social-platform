package com.contentplatform.backend.application.user

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.repository.UserRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Reads one Platform [User] aggregate by its public id.
 *
 * This use case is a thin pass-through over [UserRepository.findById] and
 * returns the complete Domain aggregate, or null when the user does not exist.
 *
 * Not-found semantics: null is returned (no exception is thrown). Mapping a
 * missing aggregate to HTTP 404 is the API layer's responsibility and is NOT
 * handled here.
 *
 * Transaction notes: simple read-only query; no transaction boundary is
 * declared. Consistent with the other repository-dependent read use cases,
 * this use case stays conditional on the configured datasource.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetUserUseCase(
    private val userRepository: UserRepository
) {

    /**
     * Returns the platform user with the given public id, or null when absent.
     */
    fun getById(id: PublicId): User? = userRepository.findById(id)

    fun getByUsername(username: String): User? =
        userRepository.findByUsernameCaseInsensitive(username.trim())

    fun search(query: String, limit: Int): List<User> =
        userRepository.search(query.trim(), limit)
}
