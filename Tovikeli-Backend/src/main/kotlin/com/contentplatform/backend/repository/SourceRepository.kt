package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.source.Source

/**
 * Data access contract for [Source].
 *
 * V1 only requires reading a source by public id.
 *
 * Repository contracts are transaction-agnostic. Transaction boundaries belong
 * to Application use cases, not to this interface.
 */
interface SourceRepository {

    /** Returns the source with the given public id, or null when absent. */
    fun findById(id: PublicId): Source?
}
