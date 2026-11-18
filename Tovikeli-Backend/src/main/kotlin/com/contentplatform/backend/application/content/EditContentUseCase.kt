package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.ContentLifecycleResult
import com.contentplatform.backend.domain.content.ContentStatus
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.ContentRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class EditContentUseCase(
    private val contentRepository: ContentRepository,
    private val authorRepository: AuthorRepository,
    private val transactionTemplate: TransactionTemplate
) {
    companion object {
        private val LOCAL_INSTANCE_ID = PublicId("inst_local")
    }

    fun edit(
        contentId: PublicId,
        callerUserId: PublicId,
        text: String
    ): ContentLifecycleResult = transactionTemplate.execute {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) {
            return@execute ContentLifecycleResult.INVALID_TRANSITION
        }

        val content = contentRepository.findById(contentId)
            ?: return@execute ContentLifecycleResult.NOT_FOUND

        val localAuthor = authorRepository.findByInstanceAndSourceAuthorId(
            LOCAL_INSTANCE_ID,
            callerUserId.value
        ) ?: return@execute ContentLifecycleResult.FORBIDDEN

        if (content.authorId != localAuthor.id) {
            return@execute ContentLifecycleResult.FORBIDDEN
        }

        if (content.status != ContentStatus.ACTIVE) {
            return@execute ContentLifecycleResult.INVALID_TRANSITION
        }

        contentRepository.updateText(contentId, normalizedText)
        ContentLifecycleResult.SUCCESS
    } ?: ContentLifecycleResult.NOT_FOUND
}
