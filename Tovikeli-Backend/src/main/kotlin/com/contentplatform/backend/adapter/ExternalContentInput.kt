package com.contentplatform.backend.adapter

import com.contentplatform.backend.domain.content.ContentVisibility

/**
 * Platform-independent, normalized content data as obtained from an external
 * platform by an Adapter.
 *
 * Field set strictly follows the current Content Domain capabilities.
 *
 * This is a pure fact carrier:
 *  - immutable Kotlin data class, no business methods
 *  - NO PublicId, no Repository/Entity, no Spring/JDBC/PostgreSQL type
 *  - NO policy decisions, NO domain identity generation
 *
 * The following are deliberately ABSENT because they are not external
 * normalized facts or belong to Application/Domain responsibilities:
 *  - createdAt / updatedAt / status (system lifecycle, Domain Factory)
 *  - PublicId (Domain Factory derives ct_ + SHA-256(instanceId + ":" + sourceContentId))
 *  - allowIndex / enabled / healthStatus / media policy (Application policy)
 *
 * References ([authorRef], [mediaRefs], [parentRef], [repostOfRef],
 * [quoteOfRef]) are EXTERNAL identifiers as seen by the platform — never
 * database PublicIds. Resolution to persisted aggregates is an Application
 * responsibility.
 *
 * [tags] carries ONLY the tags that belong to THIS content (per-content tag
 * facts, e.g. the hashtags of one Mastodon status). It is never a
 * batch-flattened tag list: an adapter must place each content's own tags into
 * its own input so ingestion can attach the right associations. Tag identity
 * (PLATFORM by verbatim name, EXTERNAL by instance + sourceTagId) stays inside
 * each [ExternalTagInput]; no PublicId is ever carried here.
 *
 * Content identity is (instance context + [sourceContentId]).
 */
data class ExternalContentInput(
    val instanceRef: ExternalInstanceRef,
    val sourceContentId: String,
    val authorRef: String,
    val text: String? = null,
    val language: String? = null,
    val externalCreatedAt: Long? = null,
    val visibility: ContentVisibility = ContentVisibility.UNKNOWN,
    val mediaRefs: List<String> = emptyList(),
    val parentRef: String? = null,
    val repostOfRef: String? = null,
    val quoteOfRef: String? = null,
    val metadata: Map<String, Any?> = emptyMap(),
    val tags: List<ExternalTagInput> = emptyList()
)
