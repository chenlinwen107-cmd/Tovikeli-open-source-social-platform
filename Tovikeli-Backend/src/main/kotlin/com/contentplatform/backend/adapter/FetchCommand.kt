package com.contentplatform.backend.adapter

import com.contentplatform.backend.domain.source.SourceType

/**
 * A fetch command: "go and fetch external items from this source/instance".
 *
 * This is NOT a content DTO. It describes how to drive a future Adapter fetch,
 * not the shape of fetched facts.
 *
 * Pagination fields ([cursor], [since], [until]) are future Adapter fetch
 * inputs only — no pagination framework, crawler, scheduler, queue or retry
 * framework is implemented here.
 *
 * [sourceType] reuses the Domain [SourceType]; no duplicate source type is
 * introduced. [instanceDomain] is the external instance domain; NO database
 * PublicId is used here (the Adapter must not depend on database identifiers).
 */
data class FetchCommand(
    val sourceType: SourceType,
    val instanceDomain: String? = null,
    val cursor: String? = null,
    val since: Long? = null,
    val until: Long? = null
)
