package com.contentplatform.backend.application.fetch

import com.contentplatform.backend.application.content.ContentIngestSummary
import com.contentplatform.backend.domain.common.PublicId

/**
 * Minimal Application-facing port for "ingest one external instance once".
 *
 * It is implemented in the config layer by delegating to the existing
 * [FetchExternalContentEntryUseCase]; the startup trigger depends on this port
 * instead of the concrete use case so that multi-instance / failure-isolation
 * behaviour can be exercised without a database.
 *
 * This is NOT a framework / registry: one method, no state, no lifecycle.
 */
fun interface ExternalInstanceIngestion {

    /**
     * Runs one bounded ingestion for the given instance identifier.
     *
     * @return the ingestion summary, or null when the instance (or its owning
     *         source) is not persisted
     */
    fun ingest(instanceId: PublicId): ContentIngestSummary?
}
