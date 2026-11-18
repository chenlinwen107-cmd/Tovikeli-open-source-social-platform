package com.contentplatform.backend.application.tag

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.repository.TagRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

/**
 * Reads one [Tag] aggregate by its public id.
 *
 * Stage 9 Application Read / Query Foundation: this use case is a thin
 * pass-through over [TagRepository.findById] and returns the complete
 * Domain aggregate (EXTERNAL or PLATFORM tag), or null when the tag does not
 * exist.
 *
 * Not-found semantics: null is returned (no exception is thrown). Mapping a
 * missing aggregate to HTTP 404 is the future API layer's responsibility and
 * is NOT handled here.
 *
 * Transaction notes: simple read-only query; no transaction boundary is
 * declared (consistent with the other repository-dependent use cases this
 * use case stays conditional on the configured datasource).
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class GetTagByIdUseCase(
    private val tagRepository: TagRepository
) {

    /**
     * Returns the tag with the given public id, or null when absent.
     */
    fun getById(id: PublicId): Tag? = tagRepository.findById(id)
}
