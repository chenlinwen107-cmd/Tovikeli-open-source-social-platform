package com.contentplatform.backend.application.content

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.ContentTagRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Attaches already-resolved Tag ids to an already-persisted Content aggregate
 * (Stage 15-B).
 *
 * Responsibilities / boundaries:
 *  - Input is a persisted [Content] id plus already-resolved [Tag] ids; this
 *    use case NEVER creates a Content and NEVER creates/ensures a Tag (that is
 *    [com.contentplatform.backend.application.tag.EnsureAndSaveTagUseCase]'s
 *    job, reused by the ingestion orchestration).
 *  - Attaching is additive and idempotent: duplicate tag ids are collapsed and
 *    the repository's ON CONFLICT DO NOTHING guarantees existing associations
 *    of the content are never replaced or removed.
 *  - An empty list performs no repository write.
 *
 * Transaction notes: at most one batched repository write, atomic by itself;
 * no transaction boundary is declared here. Outer multi-repository callers
 * (external content ingestion) own the transaction.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class AttachTagsToContentUseCase(
    private val contentTagRepository: ContentTagRepository
) {

    /**
     * Attaches all [tagIds] to [contentId] in one batched write. Duplicate ids
     * are collapsed; an empty list is a no-op.
     */
    fun attach(contentId: PublicId, tagIds: List<PublicId>) {
        if (tagIds.isEmpty()) return
        contentTagRepository.attachAll(contentId, tagIds.distinct())
    }
}
