package com.contentplatform.backend.adapter.mastodon

/**
 * Mastodon API JSON response model — STRICTLY PRIVATE to the Mastodon adapter
 * package. Never crosses the adapter boundary into Application/Domain.
 *
 * Field names mirror the Mastodon REST API (snake_case). The mapper used to
 * bind these models ignores unknown JSON properties, so future Mastodon fields
 * are tolerated.
 */
internal data class MastodonStatus(
    val id: String = "",
    val account: MastodonAccount? = null,
    val content: String? = null,
    val created_at: String? = null,
    val visibility: String? = null,
    val media_attachments: List<MastodonMediaAttachment>? = null,
    val in_reply_to_id: String? = null,
    val reblog: MastodonStatus? = null,
    val quote: MastodonQuote? = null,
    val language: String? = null,
    val tags: List<MastodonTag>? = null
)

/**
 * Mastodon 4.4+ `Status.quote` entity. PRIVATE to the adapter package.
 *
 * Since Mastodon 4.4.0 `Status.quote` is a `Quote` / `ShallowQuote` object, NOT
 * a `Status`. Two shapes exist:
 *  - full Quote:      `{ "state": "...", "quoted_status": { ...status... } }`
 *  - shallow Quote:   `{ "state": "...", "quoted_status_id": "123" }`
 *
 * All fields are optional/defaulted so any state (including future/unknown
 * states) and either shape bind without failing the whole status. The quoted
 * status is itself a [MastodonStatus]; its `id` is the only part the mapper
 * uses (as the existing `quoteOfRef`).
 */
internal data class MastodonQuote(
    val state: String? = null,
    val quoted_status: MastodonStatus? = null,
    val quoted_status_id: String? = null
)

/**
 * Mastodon account JSON model (nested inside a status or returned from
 * account endpoints). PRIVATE to the adapter package.
 */
internal data class MastodonAccount(
    val id: String = "",
    val username: String = "",
    val acct: String = "",
    val display_name: String? = null,
    val avatar: String? = null,
    val note: String? = null,
    val url: String? = null
)

/**
 * Mastodon media attachment JSON model. PRIVATE to the adapter package.
 *
 * The external media id is used inside the adapter for mediaRefs wiring AND as
 * the correlation key of ExternalFetchResult.media; it is the EXTERNAL media
 * reference, never a Domain Media identity and never exposed as any Media
 * PublicId.
 */
internal data class MastodonMediaAttachment(
    val id: String = "",
    val url: String? = null,
    val preview_url: String? = null,
    val remote_url: String? = null,
    val type: String? = null,
    val meta: MastodonMediaMeta? = null
)

/**
 * Mastodon media metadata (dimensions). PRIVATE to the adapter package.
 */
internal data class MastodonMediaMeta(
    val width: Int? = null,
    val height: Int? = null
)

/**
 * Mastodon hashtag JSON model. PRIVATE to the adapter package.
 */
internal data class MastodonTag(
    val name: String? = null,
    val url: String? = null
)
