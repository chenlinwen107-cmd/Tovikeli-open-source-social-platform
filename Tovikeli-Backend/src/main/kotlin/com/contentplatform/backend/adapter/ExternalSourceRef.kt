package com.contentplatform.backend.adapter

import com.contentplatform.backend.domain.source.SourceType

/**
 * Lightweight, platform-independent reference to an external platform source.
 *
 * Contains only the external source type (reusing the Domain [SourceType]).
 * It intentionally carries NO database PublicId, no Repository, no Entity and
 * no database identifier: the Application resolves the concrete persisted
 * Source from its own fetch context.
 */
data class ExternalSourceRef(
    val sourceType: SourceType
)
