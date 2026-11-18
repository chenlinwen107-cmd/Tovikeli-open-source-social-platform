package com.contentplatform.backend.adapter

/**
 * Lightweight, platform-independent reference to an external instance.
 *
 * Contains only the external instance domain. It intentionally carries NO
 * database PublicId, no Repository, no Entity and no database identifier.
 *
 * The Application resolves the concrete persisted Instance by matching the
 * domain within its own fetch context (the same instance that initiated the
 * fetch); the Repository Contract has no findByDomain for a reason — lookups
 * stay in Application orchestration state, not in this contract.
 */
data class ExternalInstanceRef(
    val instanceDomain: String
)
