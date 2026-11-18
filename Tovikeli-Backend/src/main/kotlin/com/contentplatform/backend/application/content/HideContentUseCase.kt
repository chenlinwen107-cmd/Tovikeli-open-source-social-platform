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
 * Hides a content aggregate by transitioning its status from
 * ACTIVE to HIDDEN.
 *
 * Returns [ContentLifecycleResult.SUCCESS] when the content existed and
 * was successfully hidden, [ContentLifecycleResult.NOT_FOUND] when the
 * content does not exist, or [ContentLifecycleResult.INVALID_TRANSITION]
 * when the current status does not allow hiding.
 *
 * Authorization is intentionally absent: the permission model does not
 * exist yet and is explicitly deferred.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class HideContentUseCase(
    private val contentRepository: ContentRepository,
    private val transactionTemplate: TransactionTemplate
) {
    fun hide(contentId: PublicId): ContentLifecycleResult =
        transactionTemplate.execute {
            val content = contentRepository.findById(contentId)
                ?: return@execute ContentLifecycleResult.NOT_FOUND

            if (!ContentLifecyclePolicy.canTransition(content.status, ContentStatus.HIDDEN)) {
                return@execute ContentLifecycleResult.INVALID_TRANSITION
            }

            contentRepository.updateStatus(contentId, ContentStatus.HIDDEN)
            ContentLifecycleResult.SUCCESS
        } ?: ContentLifecycleResult.NOT_FOUND
}
