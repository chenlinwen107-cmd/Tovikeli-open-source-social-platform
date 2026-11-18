package com.contentplatform.backend.adapter

/**
 * Platform-independent, normalized media data as obtained from an external
 * platform by an Adapter.
 *
 * This is a pure fact carrier:
 *  - immutable Kotlin data class, no business methods
 *  - NO PublicId, no Repository/Entity, no Spring/JDBC/PostgreSQL type
 *  - NO policy decisions, NO domain identity generation
 *
 * IMPORTANT: Media PublicId is UNKNOWN (deferred). This DTO must NEVER derive,
 * carry or hint at any Media PublicId. In particular:
 *  - md_ + sha256  is forbidden
 *  - md_ + sourceMediaId is forbidden (sourceMediaId does not exist)
 *  - URL -> PublicId is forbidden
 *  - no MediaIdentityFactory
 *
 * [sha256] is only a dedup candidate for exact-file lookup; it is NOT an
 * identity. [mimeType] / [width] / [height] are preserved as external facts;
 * the current Media Domain has no fixed fields for them, so whether they later
 * enter Media.metadata is a future Application/Adapter decision — not decided
 * here, and NOT a Domain change.
 */
data class ExternalMediaInput(
    val instanceRef: ExternalInstanceRef,
    val sourceUrl: String? = null,
    val previewUrl: String? = null,
    val originalUrl: String? = null,
    val sha256: String? = null,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val altText: String? = null,
    val license: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
)
