package com.contentplatform.backend.adapter

/**
 * Batch result of an [ExternalAdapter.fetch] call.
 *
 * Carries the platform-independent normalized inputs produced by an adapter:
 *  - [authors]:  ExternalAuthorInput items in this batch
 *  - [contents]: ExternalContentInput items in this batch
 *  - [media]:    ExternalMediaInput items in this batch, correlated to their
 *                external media reference (Stage 11)
 *  - [tags]:     ExternalTagInput items in this batch
 *
 * [nextCursor] is an EXTERNAL continuation token returned by the platform; it
 * is a plain string cursor and is NOT a pagination framework.
 *
 * Media correlation ([media]): each entry is keyed by the EXTERNAL media
 * reference string — the same value carried in
 * ExternalContentInput.mediaRefs (e.g. the Mastodon attachment id). The key
 * lets the Application resolve the normalized media facts for every media ref
 * of a content. The key is an external platform reference, never a database
 * PublicId and never a Media PublicId.
 *
 * This is a pure fact carrier. It intentionally does NOT contain:
 *  - any database PublicId or DB id
 *  - any Media id / Media PublicId (Media identity is derived later by the
 *    Application from (instance, external media reference))
 *  - any repository / entity / domain aggregate
 */
data class ExternalFetchResult(
    val authors: List<ExternalAuthorInput> = emptyList(),
    val contents: List<ExternalContentInput> = emptyList(),
    val media: Map<String, ExternalMediaInput> = emptyMap(),
    val tags: List<ExternalTagInput> = emptyList(),
    val nextCursor: String? = null
)
