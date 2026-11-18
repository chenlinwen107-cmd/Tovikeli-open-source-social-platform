package com.contentplatform.backend.adapter

/**
 * Platform-independent, normalized tag data as obtained from an external
 * platform by an Adapter.
 *
 * Two tag kinds are supported:
 *  - EXTERNAL tag: identity is (instance context + [sourceTagId]); [name] is
 *    the verbatim external display name, [normalizedName] is only a search
 *    aid and does NOT participate in identity.
 *  - PLATFORM tag: identity is the verbatim [name] itself; external fields are
 *    null. PLATFORM tags are created by the platform itself, not via external
 *    adapter data — so this DTO is primarily intended for EXTERNAL tags, while
 *    keeping the field shape explicit about what a platform tag needs.
 *
 * [name] must be preserved verbatim (never normalized/trimmed here).
 * [normalizedName] is search normalization only.
 *
 * This is a pure fact carrier: no PublicId, no Repository/Entity, no
 * Spring/JDBC/PostgreSQL type, no domain identity generation. The final tg_
 * PublicId is derived by the Domain Factory.
 */
data class ExternalTagInput(
    val instanceRef: ExternalInstanceRef,
    val sourceIdRef: ExternalSourceRef? = null,
    val name: String,
    val sourceTagId: String? = null,
    val normalizedName: String? = null
)
