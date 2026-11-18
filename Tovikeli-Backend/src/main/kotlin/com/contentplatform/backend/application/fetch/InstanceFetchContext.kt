package com.contentplatform.backend.application.fetch

import com.contentplatform.backend.domain.source.Instance

/**
 * Minimal Application-level context for one external fetch run.
 *
 * It wraps the [Instance] that the Application has ALREADY resolved (via
 * InstanceRepository.findById(PublicId)) and needs for policy checks while
 * orchestrating a fetch + ensure flow.
 *
 * Through [instance] the Application can read:
 *  - instance.id, sourceId, domain
 *  - enabled, allowIndex, allowMediaPreview, allowMediaCache,
 *    allowOriginalAccess, nsfwPolicy, healthStatus
 *
 * IMPORTANT:
 *  - This context is an APPLICATION object. It is NEVER passed to an
 *    ExternalAdapter.
 *  - The adapter only receives a FetchCommand whose instanceDomain carries the
 *    EXTERNAL instance domain semantics. The Instance DB PublicId must never
 *    enter the Adapter contract.
 *  - This is NOT a context framework / registry / generic context system.
 */
data class InstanceFetchContext(
    val instance: Instance
) {

    /** Convenience view of the external instance domain (adapter-facing value). */
    val instanceDomain: String
        get() = instance.domain
}
