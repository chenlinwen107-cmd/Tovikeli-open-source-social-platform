package com.contentplatform.backend.domain.content

/**
 * Lifecycle transition rules for [Content].
 *
 * Only the following transitions are supported in V1:
 *   ACTIVE  -> DELETED
 *   ACTIVE  -> HIDDEN
 *   DELETED -> ACTIVE (restore)
 *   HIDDEN  -> ACTIVE (unhide)
 *
 * All other transitions are rejected as invalid.
 *
 * Authorization is intentionally absent from this policy: the permission
 * model does not exist yet and is explicitly deferred.
 */
object ContentLifecyclePolicy {

    private val validTransitions: Map<ContentStatus, Set<ContentStatus>> = mapOf(
        ContentStatus.ACTIVE  to setOf(ContentStatus.DELETED, ContentStatus.HIDDEN),
        ContentStatus.DELETED to setOf(ContentStatus.ACTIVE),
        ContentStatus.HIDDEN  to setOf(ContentStatus.ACTIVE)
    )

    /**
     * Returns `true` when the transition from [from] to [to] is allowed
     * by the V1 lifecycle policy.
     */
    fun canTransition(from: ContentStatus, to: ContentStatus): Boolean =
        validTransitions[from]?.contains(to) == true
}
