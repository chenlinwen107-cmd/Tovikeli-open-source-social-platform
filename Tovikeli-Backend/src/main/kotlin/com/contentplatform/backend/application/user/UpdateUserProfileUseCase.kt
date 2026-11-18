package com.contentplatform.backend.application.user

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.UserRepository
import com.contentplatform.backend.repository.AuthorRepository
import org.springframework.stereotype.Service

@Service
class UpdateUserProfileUseCase(
    private val userRepository: UserRepository,
    private val authorRepository: AuthorRepository
) {
    fun update(userId: PublicId, request: com.contentplatform.backend.api.user.UpdateProfileRequest) =
        userRepository.findById(userId)?.let { current ->
            userRepository.updateProfile(current.copy(
                displayName = request.displayName?.trim()?.takeIf { it.isNotBlank() },
                avatar = request.avatar?.trim()?.takeIf { it.isNotBlank() },
                bio = request.bio?.trim()?.takeIf { it.isNotBlank() },
                gender = request.gender?.trim()?.takeIf { it.isNotBlank() } ?: current.gender,
                location = request.location?.trim()?.takeIf { it.isNotBlank() },
                cover = request.cover?.trim()?.takeIf { it.isNotBlank() }
            ).copy(updatedAt = System.currentTimeMillis()).also { updated ->
                authorRepository.findByInstanceAndSourceAuthorId(PublicId("inst_local"), userId.value)?.let { author ->
                    authorRepository.save(author.copy(
                        name = updated.username,
                        username = updated.username,
                        displayName = updated.displayName,
                        avatar = updated.avatar,
                        bio = updated.bio
                    ))
                }
            })
        }
}
