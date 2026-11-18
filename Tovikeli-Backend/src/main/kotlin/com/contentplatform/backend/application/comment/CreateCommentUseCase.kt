package com.contentplatform.backend.application.comment

import com.contentplatform.backend.domain.comment.Comment
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.CommentRepository
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.InstanceRepository
import com.contentplatform.backend.repository.UserRepository
import com.contentplatform.backend.application.notification.NotificationUseCase
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Thrown when a create references a platform user that does not exist.
 */
class CommentUserNotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when a create references content that does not exist.
 */
class CommentContentNotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when the referenced content exists but is not currently
 * public/interactable.
 */
class CommentContentNotInteractableException(message: String) : RuntimeException(message)

/**
 * Thrown when a reply references a comment that does not exist.
 */
class CommentParentNotFoundException(message: String) : RuntimeException(message)

/**
 * Thrown when a reply references a parent comment belonging to
 * a different content aggregate.
 */
class CommentParentContentMismatchException(message: String) : RuntimeException(message)

/**
 * Stage 18 Create Comment use case.
 *
 * Validation and persistence are executed inside the supplied
 * [TransactionTemplate].
 *
 * Rules:
 *  1. The platform user must exist.
 *  2. The content must exist.
 *  3. The content must be currently public/interactable:
 *     ACTIVE + PUBLIC + instance allow_index = TRUE.
 *  4. When parentCommentId is supplied:
 *     - the parent comment must exist;
 *     - the parent must belong to the same content.
 *  5. A new Comment is created and persisted.
 *
 * Unlike Interaction, comments are not naturally unique:
 * the same user may create multiple comments on the same content.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class CreateCommentUseCase(
    private val userRepository: UserRepository,
    private val contentRepository: ContentRepository,
    private val instanceRepository: InstanceRepository,
    private val commentRepository: CommentRepository,
    private val transactionTemplate: TransactionTemplate,
    private val notifications: NotificationUseCase
) {

    /**
     * Creates a new comment.
     *
     * @throws CommentUserNotFoundException when the user does not exist.
     * @throws CommentContentNotFoundException when the content does not exist.
     * @throws CommentContentNotInteractableException when the content is not
     *         currently public/interactable.
     * @throws CommentParentNotFoundException when the supplied parent does
     *         not exist.
     * @throws CommentParentContentMismatchException when the supplied parent
     *         belongs to another content.
     * @throws IllegalArgumentException when the comment text is blank.
     */
    fun create(
        userId: PublicId,
        contentId: PublicId,
        parentCommentId: PublicId?,
        text: String
    ): Comment {
        return transactionTemplate.execute {
            // 1. Platform user must exist.
            if (userRepository.findById(userId) == null) {
                throw CommentUserNotFoundException(
                    "User $userId does not exist"
                )
            }

            // 2. Content must exist.
            val content = contentRepository.findById(contentId)
                ?: throw CommentContentNotFoundException(
                    "Content $contentId does not exist"
                )

            // 3. Content must be public/interactable.
            val instance = instanceRepository.findById(content.instanceId)

            if (
                instance == null ||
                !content.isPubliclyIndexable(instance.allowIndex)
            ) {
                throw CommentContentNotInteractableException(
                    "Content $contentId is not publicly interactable " +
                        "(status=${content.status}, " +
                        "visibility=${content.visibility}, " +
                        "instanceAllowIndex=${instance?.allowIndex})"
                )
            }

            // 4. Optional parent validation.
            if (parentCommentId != null) {
                val parent = commentRepository.findById(parentCommentId)
                    ?: throw CommentParentNotFoundException(
                        "Parent comment $parentCommentId does not exist"
                    )

                if (parent.contentId != contentId) {
                    throw CommentParentContentMismatchException(
                        "Parent comment $parentCommentId belongs to " +
                            "content ${parent.contentId}, not $contentId"
                    )
                }
            }

            // 5. Comments are entity-based and have no natural-key
            //    deduplication. Every successful create gets a new ID.
            val saved = commentRepository.save(
                Comment.create(
                    userId = userId,
                    contentId = contentId,
                    parentCommentId = parentCommentId,
                    text = text
                )
            )
            if (parentCommentId != null) {
                val parent = commentRepository.findById(parentCommentId)
                if (parent != null && parent.userId != userId) {
                    notifications.create(parent.userId, userId, "reply", "comment", saved.id.value, saved.text.take(120))
                }
            }

            // Mentions are resolved against first-party platform users only.
            // External Author identities are deliberately not treated as user IDs.
            Regex("""@([A-Za-z0-9_]{1,40})""").findAll(saved.text)
                .map { it.groupValues[1] }
                .distinctBy { it.lowercase(java.util.Locale.ROOT) }
                .take(10)
                .forEach { username ->
                    userRepository.findByUsernameCaseInsensitive(username)?.let { mentioned ->
                        if (mentioned.id != userId) {
                            notifications.create(
                                mentioned.id,
                                userId,
                                "mention",
                                "comment",
                                saved.id.value,
                                saved.text.take(120)
                            )
                        }
                    }
                }

            saved
    }
}}
