package com.contentplatform.backend.application.user

import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.repository.UserCredentialRepository
import com.contentplatform.backend.repository.UserRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Thrown when a platform user create collides with an existing username.
 *
 * The username identity space is case-insensitive.
 */
class UsernameAlreadyExistsException(message: String) : RuntimeException(message)

/**
 * Creates a Platform User and its authentication credential atomically.
 *
 * User identity remains in the User domain aggregate. Password credentials are
 * deliberately kept outside the User domain model and are persisted through the
 * separate UserCredentialRepository.
 *
 * Transaction boundary:
 * - username existence check
 * - User creation
 * - User persistence
 * - password hashing
 * - credential persistence
 *
 * all run inside one TransactionTemplate transaction.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class CreateUserUseCase(
    private val userRepository: UserRepository,
    private val userCredentialRepository: UserCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val transactionTemplate: TransactionTemplate
) {

    /**
     * Creates and persists a new ACTIVE platform user with a password
     * credential.
     *
     * The raw password is never passed to the repository layer and is never
     * stored in plaintext.
     */
    fun create(
        username: String,
        password: String,
        displayName: String?
    ): User =
        transactionTemplate.execute {
            if (userRepository.findByUsernameCaseInsensitive(username) != null) {
                throw UsernameAlreadyExistsException("username already exists: $username")
            }

            val user = User.create(
                username = username,
                displayName = displayName
            )

            val passwordHash = requireNotNull(passwordEncoder.encode(password)) {
                "password encoder returned null"
            }

            try {
                userRepository.save(user)
                userCredentialRepository.savePasswordHash(
                    userId = user.id,
                    passwordHash = passwordHash
                )
                user
            } catch (e: DuplicateKeyException) {
                // Concurrent create won the race between the existence check
                // and strict User INSERT.
                throw UsernameAlreadyExistsException(
                    "username already exists: $username"
                )
            }
        }
}
