package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.ContentLifecyclePolicy
import com.contentplatform.backend.domain.content.ContentLifecycleResult
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.ContentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Soft-deletes a content aggregate by transitioning its status from
 * ACTIVE to DELETED.
 *
 * Authorization resolves the authenticated platform user to the corresponding
 * local Author aggregate before comparing ownership with content.authorId.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class DeleteContentUseCase(
    private val contentRepository: ContentRepository,
    private val authorRepository: AuthorRepository,
    private val transactionTemplate: TransactionTemplate
) {
    companion object {
        private val LOCAL_INSTANCE_ID = PublicId("inst_local")
    }

    fun delete(contentId: PublicId, callerUserId: PublicId): ContentLifecycleResult =
        transactionTemplate.execute {
            val content = contentRepository.findById(contentId)
                ?: return@execute ContentLifecycleResult.NOT_FOUND

            val localAuthor = authorRepository.findByInstanceAndSourceAuthorId(
                LOCAL_INSTANCE_ID,
                callerUserId.value
            ) ?: return@execute ContentLifecycleResult.FORBIDDEN

            if (content.authorId != localAuthor.id) {
                return@execute ContentLifecycleResult.FORBIDDEN
            }

            if (!ContentLifecyclePolicy.canTransition(content.status, ContentStatus.DELETED)) {
                return@execute ContentLifecycleResult.INVALID_TRANSITION
            }

            contentRepository.updateStatus(contentId, ContentStatus.DELETED)
            ContentLifecycleResult.SUCCESS
        } ?: ContentLifecycleResult.NOT_FOUND
}
