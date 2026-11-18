package com.contentplatform.backend.application.feed

/**
 * Application-facing port used by the Feed HTTP boundary to request a
 * non-blocking background refill of the public content pool (P9-G3).
 *
 * Contract:
 *  - [trigger] MUST return immediately and MUST NOT throw.
 *  - It only signals "the public pool looks exhausted"; whether a refill is
 *    actually started (single-flight, configuration, instance selection) is
 *    entirely the implementation's concern.
 *
 * The Feed request never waits for, and never observes the failure of, the
 * background refill. This port exists so the API layer does not depend on the
 * ingestion pipeline, a scheduler or a thread lifecycle.
 */
fun interface FeedRefillTrigger {

    /** Requests one background refill attempt; never blocks and never throws. */
    fun trigger()
}
