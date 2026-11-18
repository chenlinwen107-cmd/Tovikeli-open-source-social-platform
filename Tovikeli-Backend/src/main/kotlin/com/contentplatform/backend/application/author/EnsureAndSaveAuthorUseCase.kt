package com.contentplatform.backend.application.author

import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.AuthorRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Ensures that an external author is persisted, keyed by its natural key
 * (instanceId, sourceAuthorId).
 *
 * IMPORTANT: this use case does NOT rely on any upsert guarantee of
 * [AuthorRepository.save]. The flow is an explicit existence check first:
 *  - when the author already exists (by natural key), the existing aggregate is
 *    returned and save() is never invoked;
 *  - only when the author does NOT exist is a new aggregate created and saved.
 *
 * Transaction notes: this use case performs at most one repository write which
 * is atomic by itself, so it declares no transaction boundary. If callers need
 * this to participate in a larger multi-repository transaction, the caller
 * (an outer use case) owns that transaction.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class EnsureAndSaveAuthorUseCase(
    private val authorRepository: AuthorRepository
) {

    /**
     * Ensures the author for (instanceId, sourceAuthorId) exists.
     *
     * @return the existing author when present, or the newly saved author when
     *         the natural key was not yet persisted.
     */
    fun ensure(
        instanceId: PublicId,
        sourceAuthorId: String,
        name: String,
        username: String,
        displayName: String? = null,
        url: String? = null,
        avatar: String? = null,
        bio: String? = null,
        status: String? = null
    ): Author {
        authorRepository.findByInstanceAndSourceAuthorId(instanceId, sourceAuthorId)?.let { existing ->
            return existing
        }

        val author = Author.createFromAdapter(
            instanceId = instanceId,
            sourceAuthorId = sourceAuthorId,
            name = name,
            username = username,
            displayName = displayName,
            url = url,
            avatar = avatar,
            bio = bio,
            status = status
        )
        return authorRepository.save(author)
    }
}
