package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.ContentLifecyclePolicy
import com.contentplatform.backend.domain.content.ContentLifecycleResult
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.repository.ContentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Restores a content aggregate by transitioning its status from
 * DELETED or HIDDEN back to ACTIVE.
 *
 * Returns [ContentLifecycleResult.SUCCESS] when the content existed and
 * was successfully restored, [ContentLifecycleResult.NOT_FOUND] when the
 * content does not exist, or [ContentLifecycleResult.INVALID_TRANSITION]
 * when the current status does not allow restoration.
 *
 * Authorization is intentionally absent: the permission model does not
 * exist yet and is explicitly deferred.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class RestoreContentUseCase(
    private val contentRepository: ContentRepository,
    private val transactionTemplate: TransactionTemplate
) {
    fun restore(contentId: PublicId): ContentLifecycleResult =
        transactionTemplate.execute {
            val content = contentRepository.findById(contentId)
                ?: return@execute ContentLifecycleResult.NOT_FOUND

            if (!ContentLifecyclePolicy.canTransition(content.status, ContentStatus.ACTIVE)) {
                return@execute ContentLifecycleResult.INVALID_TRANSITION
            }

            contentRepository.updateStatus(contentId, ContentStatus.ACTIVE)
            ContentLifecycleResult.SUCCESS
        } ?: ContentLifecycleResult.NOT_FOUND
}
