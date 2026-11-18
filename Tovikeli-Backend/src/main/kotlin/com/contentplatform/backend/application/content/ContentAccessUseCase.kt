package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.ContentRepository
import org.springframework.stereotype.Service

enum class ContentAccessState { ALLOWED, LOCKED, FORBIDDEN }

@Service
class ContentAccessUseCase(
    private val contentRepository: ContentRepository,
    private val authorRepository: AuthorRepository
) {
    private val localInstanceId = PublicId("inst_local")

    fun state(content: Content, callerUserId: PublicId?): ContentAccessState {
        if (isOwner(content, callerUserId)) return ContentAccessState.ALLOWED
        return when (content.visibility) {
            ContentVisibility.PUBLIC -> ContentAccessState.ALLOWED
            ContentVisibility.PRIVATE -> ContentAccessState.FORBIDDEN
            else -> ContentAccessState.FORBIDDEN
        }
    }

    fun canReadMedia(mediaId: PublicId, callerUserId: PublicId?): Boolean {
        val content = contentRepository.findByMediaId(mediaId) ?: return false
        return state(content, callerUserId) == ContentAccessState.ALLOWED
    }

    fun isOwner(content: Content, callerUserId: PublicId?): Boolean {
        if (callerUserId == null) return false
        val author = authorRepository.findByInstanceAndSourceAuthorId(localInstanceId, callerUserId.value)
            ?: return false
        return content.authorId == author.id
    }

}
