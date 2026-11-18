package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.MediaRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Thrown when an aggregate that a content references (author, media, or a
 * content referenced via parent/repost/quote) does not exist yet.
 */
class ReferencedAggregateNotFoundException(message: String) : RuntimeException(message)

/**
 * Persists a content aggregate together with its referenced assemblies inside a
 * single transaction.
 *
 * Responsibilities:
 *  - verifies the referenced [com.contentplatform.backend.domain.author.Author]
 *    already exists;
 *  - verifies every referenced media exists (order is preserved and persisted
 *    via content_media inside [ContentRepository.save]);
 *  - verifies parent / repost-of / quote-of self-references point to content
 *    that is already persisted (self-reference target must exist first);
 *  - deduplicates by the content natural key (instanceId, sourceContentId).
 *
 * IMPORTANT: this use case does NOT rely on any upsert guarantee of
 * [ContentRepository.save]. When the content already exists (by natural key),
 * the existing aggregate is returned and save() is never invoked. save() is
 * only called for a newly created aggregate.
 *
 * Transaction boundary: the whole verification + save sequence runs inside the
 * supplied [TransactionTemplate] so that the content row and its content_media
 * association rows are committed (or rolled back) atomically. The repository
 * implementations themselves declare no transaction.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class SaveContentWithAssembliesUseCase(
    private val contentRepository: ContentRepository,
    private val authorRepository: AuthorRepository,
    private val mediaRepository: MediaRepository,
    private val transactionTemplate: TransactionTemplate
) {

    /**
     * Persists the given content and returns the persisted aggregate.
     *
     * @return the newly saved content, or the already-existing content when the
     *         natural key (instanceId, sourceContentId) was already persisted.
     */
    fun save(
        instanceId: PublicId,
        sourceContentId: String,
        authorId: PublicId,
        sourceId: PublicId,
        text: String?,
        language: String?,
        visibility: ContentVisibility,
        mediaIds: List<PublicId>,
        parentId: PublicId? = null,
        repostOfId: PublicId? = null,
        quoteOfId: PublicId? = null,
        topicId: PublicId? = null,
        externalCreatedAt: Long? = null,
        metadata: Map<String, Any?> = emptyMap()
    ): Content {
        return transactionTemplate.execute {
            // 1. Author must exist (FK: content.author_id -> author).
            if (authorRepository.findById(authorId) == null) {
                throw ReferencedAggregateNotFoundException("Author $authorId does not exist")
            }

            // 2. All referenced media must exist (FK: content_media.media_id -> media).
            val missingMedia = mediaIds.filter { mediaRepository.findById(it) == null }
            if (missingMedia.isNotEmpty()) {
                throw ReferencedAggregateNotFoundException("Media not found: ${missingMedia.joinToString()}")
            }

            // 3. Self-references (parent / repost / quote) must target existing content.
            val selfRefs = listOfNotNull(parentId, repostOfId, quoteOfId)
            val missingSelf = selfRefs.filter { contentRepository.findById(it) == null }
            if (missingSelf.isNotEmpty()) {
                throw ReferencedAggregateNotFoundException(
                    "Referenced content not found (parent/repost/quote): ${missingSelf.joinToString()}"
                )
            }

            // 4. Natural-key dedup: existing content is returned as-is; save() is
            //    NOT invoked (no upsert assumption).
            contentRepository.findByInstanceAndSourceContentId(instanceId, sourceContentId)?.let { existing ->
                return@execute existing
            }

            // 5. Only a brand-new content aggregate is created and persisted.
            val content = Content.createFromAdapter(
                instanceIdValue = instanceId.value,
                sourceContentId = sourceContentId,
                authorId = authorId,
                sourceId = sourceId,
                text = text,
                language = language,
                visibility = visibility,
                mediaIds = mediaIds,
                parentId = parentId,
                repostOfId = repostOfId,
                quoteOfId = quoteOfId,
                topicId = topicId,
                externalCreatedAt = externalCreatedAt,
                metadata = metadata
            )
            contentRepository.save(content)
        } ?: throw IllegalStateException("Transaction executed without a result")
    }
}
