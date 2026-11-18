package com.contentplatform.backend.application.authentication

import com.contentplatform.backend.domain.user.User
import com.contentplatform.backend.domain.user.UserStatus
import com.contentplatform.backend.repository.UserCredentialRepository
import com.contentplatform.backend.repository.UserRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class AuthenticateUserUseCase(
    private val userRepository: UserRepository,
    private val userCredentialRepository: UserCredentialRepository,
    private val passwordEncoder: PasswordEncoder
) {
    fun authenticate(username: String, password: String): User {
        val user = userRepository.findByUsernameCaseInsensitive(username)
            ?: throw InvalidCredentialsException()

        if (user.status == UserStatus.DISABLED) {
            throw DisabledUserAuthenticationException()
        }

        val passwordHash = userCredentialRepository
            .findPasswordHashByUsername(username)
            ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(password, passwordHash)) {
            throw InvalidCredentialsException()
        }

        return user
    }
}
