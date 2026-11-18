package com.contentplatform.backend.repository

import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.source.Instance

/**
 * Data access contract for [Instance].
 *
 * V1 only requires reading an instance by public id.
 *
 * Repository contracts are transaction-agnostic. Transaction boundaries belong
 * to Application use cases, not to this interface.
 */
interface InstanceRepository {

    /** Returns the instance with the given public id, or null when absent. */
    fun findById(id: PublicId): Instance?
}
