package com.contentplatform.backend.adapter.misskey

/**
 * Misskey API JSON response model — STRICTLY PRIVATE to the Misskey adapter
 * package. Never crosses the adapter boundary into Application/Domain.
 *
 * Field names mirror the Misskey REST API (camelCase). The mapper used to bind
 * these models ignores unknown JSON properties, so future Misskey fields are
 * tolerated.
 *
 * Only the fields the V1 mapping actually needs are declared. Misskey-specific
 * fields that the current unified adapter model cannot carry are intentionally
 * omitted (no V1 domain extension for Misskey).
 */
internal data class MisskeyNote(
    val id: String = "",
    val text: String? = null,
    val cw: String? = null,
    val createdAt: String? = null,
    val userId: String? = null,
    val user: MisskeyUser? = null,
    val replyId: String? = null,
    val renoteId: String? = null,
    val renote: MisskeyNote? = null,
    val visibility: String? = null,
    val lang: String? = null,
    val tags: List<String>? = null,
    val files: List<MisskeyFile>? = null,
    val sensitive: Boolean? = null
)

/**
 * Misskey user JSON model (nested inside a note). PRIVATE to the adapter.
 */
internal data class MisskeyUser(
    val id: String = "",
    val username: String = "",
    val name: String? = null,
    val avatarUrl: String? = null,
    val description: String? = null,
    val host: String? = null,
    val isBot: Boolean? = null
)

/**
 * Misskey drive file JSON model. PRIVATE to the adapter.
 *
 * The external media id is used inside the adapter for mediaRefs wiring AND as
 * the correlation key of ExternalFetchResult.media; it is the EXTERNAL media
 * reference, never a Domain Media identity and never exposed as any Media
 * PublicId.
 */
internal data class MisskeyFile(
    val id: String = "",
    val name: String? = null,
    val type: String? = null,
    val url: String? = null,
    val thumbnailUrl: String? = null,
    val isSensitive: Boolean? = null,
    val comment: String? = null,
    val size: Long? = null,
    val properties: MisskeyFileProperties? = null
)

/**
 * Misskey drive file properties (dimensions). PRIVATE to the adapter.
 */
internal data class MisskeyFileProperties(
    val width: Int? = null,
    val height: Int? = null
)
