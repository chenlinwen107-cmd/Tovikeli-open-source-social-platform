package com.contentplatform.backend.application.interaction

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.repository.InteractionRepository
import com.contentplatform.backend.repository.UserRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Stage 17 Delete Interaction use case.
 *
 * Removes one [com.contentplatform.backend.domain.interaction.Interaction]
 * identified by its natural key (userId, contentId, type).
 *
 * Rules (frozen Stage 17 contract):
 *  - The platform user must exist ([InteractionUserNotFoundException]
 *    otherwise).
 *  - Existing interaction -> it is physically deleted.
 *  - Missing interaction   -> successful idempotent no-op.
 *
 * The delete is a physical DELETE (no soft delete, no history tables) executed
 * inside the supplied [TransactionTemplate] (never @Transactional).
 *
 * Return semantics: true when a row was actually deleted; false when no such
 * interaction existed (idempotent result).
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class DeleteInteractionUseCase(
    private val userRepository: UserRepository,
    private val interactionRepository: InteractionRepository,
    private val transactionTemplate: TransactionTemplate
) {

    /**
     * Physically deletes the interaction (userId, contentId, type).
     *
     * @return true when an interaction was deleted, false when no interaction
     *         existed (idempotent no-op).
     * @throws InteractionUserNotFoundException when the user does not exist.
     */
    fun delete(
        userId: PublicId,
        contentId: PublicId,
        type: InteractionType
    ): Boolean {
        if (userRepository.findById(userId) == null) {
            throw InteractionUserNotFoundException("User $userId does not exist")
        }
        return transactionTemplate.execute {
            interactionRepository.deleteByUserContentType(userId, contentId, type)
        }
    }
}
