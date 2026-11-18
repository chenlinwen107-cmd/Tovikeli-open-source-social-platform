package com.contentplatform.backend.application.interaction

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.interaction.Interaction
import com.contentplatform.backend.domain.interaction.InteractionType
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.application.notification.NotificationUseCase
import com.contentplatform.backend.repository.InstanceRepository
import com.contentplatform.backend.repository.InteractionRepository
import com.contentplatform.backend.repository.UserRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Thrown when a create references a platform user that does not exist.
 */
class InteractionUserNotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when a create references content that does not exist.
 */
class InteractionContentNotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when the referenced content exists but is not currently
 * public/interactable (status != ACTIVE, or visibility != PUBLIC, or its
 * instance allow_index != TRUE).
 */
class InteractionContentNotInteractableException(message: String) : RuntimeException(message)

/**
 * Stage 17 Create Interaction use case.
 *
 * Creates one [Interaction] for the identity tuple (userId, contentId, type).
 *
 * Validation rules (frozen Stage 17 contract), executed inside the supplied
 * [TransactionTemplate]:
 *  1. The platform [com.contentplatform.backend.domain.user.User] must exist
 *     ([InteractionUserNotFoundException] otherwise).
 *  2. The [Content] must exist ([InteractionContentNotFoundException]
 *     otherwise).
 *  3. The content must satisfy the current public/interactable condition:
 *     status = ACTIVE, visibility = PUBLIC and the associated instance
 *     allow_index = TRUE. The check reuses the existing domain policy
 *     [Content.isPubliclyIndexable]; no Content semantics are modified.
 *     Otherwise [InteractionContentNotInteractableException].
 *  4. When the interaction already exists (natural key), the EXISTING
 *     aggregate is returned and no row is inserted (duplicate creates never
 *     produce duplicate rows).
 *
 * Concurrency: the database UNIQUE constraint (user_id, content_id, type) is
 * the final protection. When a concurrent create wins the race between the
 * existence check and the strict INSERT, the resulting [DuplicateKeyException]
 * is caught OUTSIDE the transaction (a PostgreSQL transaction is aborted by a
 * unique violation, so the retry-read must not run inside it) and the existing
 * row is re-read and returned.
 *
 * Transaction boundary: the whole validation + create + save sequence runs
 * inside the supplied [TransactionTemplate] (never @Transactional).
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class CreateInteractionUseCase(
    private val userRepository: UserRepository,
    private val contentRepository: ContentRepository,
    private val authorRepository: AuthorRepository,
    private val notifications: NotificationUseCase,
    private val instanceRepository: InstanceRepository,
    private val interactionRepository: InteractionRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    /**
     * Creates (or returns the existing) interaction for
     * ([userId], [contentId], [type]).
     *
     * @return a [CreateInteractionResult] carrying the resolved [Interaction]
     *         aggregate and a [CreateInteractionResult.created] flag:
     *          - `true` when a brand-new interaction was persisted.
     *          - `false` when the natural key already existed (idempotent
     *            re-read) or when a concurrent duplicate was resolved via the
     *            database UNIQUE constraint.
     * @throws InteractionUserNotFoundException when the user does not exist.
     * @throws InteractionContentNotFoundException when the content does not exist.
     * @throws InteractionContentNotInteractableException when the content is not
     *         currently public/interactable.
     */
    fun create(
        userId: PublicId,
        contentId: PublicId,
        type: InteractionType
    ): CreateInteractionResult {
        return try {
            transactionTemplate.execute {
                createInTransaction(userId, contentId, type)
            }!!
        } catch (e: DuplicateKeyException) {
            // A concurrent create committed the same natural key between our
            // existence check and our strict INSERT. The database UNIQUE
            // constraint is the final guarantee; return the existing row.
            val existing = interactionRepository.findByUserContentType(userId, contentId, type)
                ?: throw e
            CreateInteractionResult(interaction = existing, created = false)
        }
    }

    private fun createInTransaction(
        userId: PublicId,
        contentId: PublicId,
        type: InteractionType
    ): CreateInteractionResult {
        // 1. Platform user must exist.
        if (userRepository.findById(userId) == null) {
            throw InteractionUserNotFoundException("User $userId does not exist")
        }

        // 2. Content must exist.
        val content = contentRepository.findById(contentId)
            ?: throw InteractionContentNotFoundException("Content $contentId does not exist")

        // 3. Content must be public/interactable. The existing domain policy
        //    (status ACTIVE + visibility PUBLIC + instance allow_index TRUE) is
        //    reused verbatim; an absent instance row (impossible under the DB
        //    FK, but defensive) is treated as not interactable.
        val instance = instanceRepository.findById(content.instanceId)
        if (instance == null || !content.isPubliclyIndexable(instance.allowIndex)) {
            throw InteractionContentNotInteractableException(
                "Content $contentId is not publicly interactable (status=${content.status}, " +
                    "visibility=${content.visibility}, instanceAllowIndex=${instance?.allowIndex})"
            )
        }

        // 4. Natural-key dedup: an existing interaction is returned as-is;
        //    save() is never invoked for it (no upsert assumption).
        interactionRepository.findByUserContentType(userId, contentId, type)?.let { existing ->
            return CreateInteractionResult(interaction = existing, created = false)
        }

        // 5. Only a brand-new interaction aggregate is created and persisted.
        val saved = interactionRepository.save(Interaction.create(userId, contentId, type))

        // Only local first-party content has a safe mapping from Content.authorId
        // (Author.id) back to platform_user.id. External authors must never be
        // treated as platform users.
        if (type == InteractionType.LIKE) {
            val localAuthor = if (content.instanceId.value == "inst_local" && content.sourceId.value == "src_local") {
                authorRepository.findById(content.authorId)
            } else {
                null
            }
            val recipientId = localAuthor
                ?.sourceAuthorId
                ?.let { runCatching { PublicId(it) }.getOrNull() }
                ?.takeIf { it != userId }
                ?.takeIf { userRepository.findById(it) != null }

            if (recipientId != null) {
                notifications.create(
                    recipientId,
                    userId,
                    "like",
                    "post",
                    content.id.value,
                    content.text?.take(120).orEmpty()
                )
            }
        }

        return CreateInteractionResult(interaction = saved, created = true)
    }
}
