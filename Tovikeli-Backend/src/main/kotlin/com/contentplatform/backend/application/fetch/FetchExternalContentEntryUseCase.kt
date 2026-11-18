package com.contentplatform.backend.application.fetch

import com.contentplatform.backend.application.content.ContentIngestSummary
import com.contentplatform.backend.application.content.FetchAndIngestExternalContentUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.InstanceRepository
import com.contentplatform.backend.repository.SourceRepository

/**
 * Stage 12 External Content Fetch Entry — one PublicId-driven entry that
 * resolves an external [com.contentplatform.backend.domain.source.Instance] and
 * its owning Source type, then delegates to the Stage 11B content fetch +
 * ingest orchestration.
 *
 *   instanceId: PublicId
 *       -> InstanceRepository.findById(instanceId)
 *       -> null when the instance is not persisted (no source lookup, no fetch)
 *       -> InstanceFetchContext(instance)
 *       -> instance.sourceId
 *       -> SourceRepository.findById(instance.sourceId)
 *       -> null when the source is not persisted (no fetch)
 *       -> Source.type
 *       -> FetchAndIngestExternalContentUseCase.fetchAndIngest(
 *              context = InstanceFetchContext(instance),
 *              sourceType = sourceType)
 *       -> ContentIngestSummary
 *
 * Responsibilities / boundaries:
 *  - [SourceType] is NOT supplied by the caller: it is always derived from the
 *    persisted Source.type of the instance's source (Instance.sourceId). The
 *    Instance aggregate intentionally does not carry the source type (the same
 *    locked decision documented by [ExternalAuthorTagFetchUseCase] is reused).
 *  - No ExternalAdapter is touched here; delegation to
 *    [FetchAndIngestExternalContentUseCase] keeps the Adapter boundary closed.
 *  - Not-found semantics mirror the existing read use cases (e.g.
 *    GetInstanceByIdUseCase): null is returned, no exception is thrown;
 *    mapping a missing aggregate to an HTTP error is a caller responsibility.
 *  - Pure orchestration: no @Service, no transaction, no scheduler, no queue,
 *    no retry is introduced.
 */
class FetchExternalContentEntryUseCase(
    private val instanceRepository: InstanceRepository,
    private val sourceRepository: SourceRepository,
    private val fetchAndIngestExternalContentUseCase: FetchAndIngestExternalContentUseCase
) {

    /**
     * Resolves the instance and its source type, then ingests one external
     * content batch for that instance through the reused
     * [FetchAndIngestExternalContentUseCase].
     *
     * @return the aggregated [ContentIngestSummary] of the run, or null when
     *         the instance (or its owning source) is not persisted
     */
    fun fetchAndIngest(instanceId: PublicId): ContentIngestSummary? {
        val instance = instanceRepository.findById(instanceId) ?: return null
        val sourceType = sourceRepository.findById(instance.sourceId)?.type ?: return null
        return fetchAndIngestExternalContentUseCase.fetchAndIngest(
            context = InstanceFetchContext(instance),
            sourceType = sourceType
        )
    }
}
