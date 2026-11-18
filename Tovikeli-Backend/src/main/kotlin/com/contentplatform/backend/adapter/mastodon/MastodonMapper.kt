package com.contentplatform.backend.adapter.mastodon

import com.contentplatform.backend.adapter.AdapterException
import com.contentplatform.backend.adapter.ExternalAuthorInput
import com.contentplatform.backend.adapter.ExternalContentInput
import com.contentplatform.backend.adapter.ExternalFetchResult
import com.contentplatform.backend.adapter.ExternalInstanceRef
import com.contentplatform.backend.adapter.ExternalMediaInput
import com.contentplatform.backend.adapter.ExternalTagInput
import com.contentplatform.backend.domain.content.ContentVisibility
import com.contentplatform.backend.domain.source.SourceType
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Pure Mastodon -> External*Input mapping core.
 *
 * STRICTLY PRIVATE to the Mastodon adapter package. This class:
 *  - converts raw Mastodon JSON models into platform-independent 7A inputs
 *  - performs HTML -> plain-text conversion of `status.content`
 *  - parses the Link header into a nextCursor (rel="next" max_id)
 *  - maps visibility to the existing Domain ContentVisibility enum
 *  - maps reblog / quote references WITHOUT recursive fetching
 *
 * It NEVER generates a Media PublicId, never touches a repository, never
 * creates Domain aggregates, and never contacts the network.
 */
internal class MastodonMapper(
    private val sourceType: SourceType,
    private val instanceDomain: String
) {

    private val instanceRef = ExternalInstanceRef(instanceDomain = instanceDomain)

    /**
     * Maps a parsed page of Mastodon statuses + the Link header into an
     * [ExternalFetchResult].
     *
     * @param statuses    parsed status JSON models
     * @param linkHeader  raw HTTP Link header (nullable when absent)
     */
    fun mapPage(
        statuses: List<MastodonStatus>,
        linkHeader: String?
    ): ExternalFetchResult {
        val authors = LinkedHashMap<String, ExternalAuthorInput>()
        val contents = ArrayList<ExternalContentInput>()
        val media = LinkedHashMap<String, ExternalMediaInput>()
        val tags = LinkedHashMap<String, ExternalTagInput>()

        for (status in statuses) {
            status.account?.let { account ->
                val key = account.id
                authors.putIfAbsent(key, mapAuthor(account))
            }
            contents += mapContent(status)
            status.media_attachments.orEmpty().forEach { attach ->
                media.putIfAbsent(attach.id, mapMedia(attach))
            }
            status.tags.orEmpty().forEach { tag ->
                tag.name?.let { tags.putIfAbsent(it, mapTag(it)) }
            }
        }

        return ExternalFetchResult(
            authors = authors.values.toList(),
            contents = contents,
            // Correlation-preserving map: key = external attachment id, which is
            // the SAME value placed into content.mediaRefs. The key survives so
            // the Application can resolve each content's media inputs.
            media = media,
            tags = tags.values.toList(),
            nextCursor = parseNextCursor(linkHeader)
        )
    }

    // ------------------------------------------------------------------
    // Author mapping
    // ------------------------------------------------------------------

    internal fun mapAuthor(account: MastodonAccount): ExternalAuthorInput = ExternalAuthorInput(
        instanceRef = instanceRef,
        sourceAuthorId = account.id,
        name = account.display_name?.takeIf { it.isNotBlank() } ?: account.username,
        username = account.acct.ifBlank { account.username },
        displayName = account.display_name,
        avatarUrl = account.avatar,
        profileUrl = account.url,
        bio = account.note?.let(::stripHtml)
    )

    // ------------------------------------------------------------------
    // Content mapping
    // ------------------------------------------------------------------

    internal fun mapContent(status: MastodonStatus): ExternalContentInput {
        // Reblog wrapper: when status.reblog is present, this status is a boost
        // of the wrapped status -> repostOfRef = wrapped id. No recursion.
        val effective = status.reblog ?: status
        val isRepost = status.reblog != null

        // Per-content tag facts: ONLY this status' own hashtags (from the
        // effective/reblogged status) are placed on this content. Tags are never
        // flattened across a fetch batch onto every content.
        val contentTags = effective.tags.orEmpty().mapNotNull { tag -> tag.name?.let(::mapTag) }

        return ExternalContentInput(
            instanceRef = instanceRef,
            sourceContentId = if (isRepost) status.id else effective.id,
            authorRef = status.account?.id ?: "",
            text = effective.content?.let(::stripHtml),
            language = effective.language,
            externalCreatedAt = effective.created_at?.let(::parseIsoToMillis),
            visibility = mapVisibility(effective.visibility),
            mediaRefs = effective.media_attachments.orEmpty().map { it.id },
            parentRef = effective.in_reply_to_id,
            repostOfRef = if (isRepost) effective.id else null,
            quoteOfRef = quoteRefOf(effective.quote),
            metadata = emptyMap(),
            tags = contentTags
        )
    }

    /**
     * Resolves the quoted status reference from the Mastodon 4.4+ `Status.quote`
     * entity (Quote / ShallowQuote), preserving the existing `quoteOfRef`
     * business meaning:
     *  - full Quote   -> `quote.quoted_status.id`
     *  - shallow Quote -> `quote.quoted_status_id`
     *  - no usable id -> null
     *
     * The quote's `state` is intentionally ignored (unknown states are fine);
     * this never fails the surrounding status mapping.
     */
    private fun quoteRefOf(quote: MastodonQuote?): String? {
        if (quote == null) return null
        return quote.quoted_status?.id?.takeIf { it.isNotBlank() }
            ?: quote.quoted_status_id?.takeIf { it.isNotBlank() }
    }

    // ------------------------------------------------------------------
    // Media mapping
    // ------------------------------------------------------------------

    internal fun mapMedia(attach: MastodonMediaAttachment): ExternalMediaInput = ExternalMediaInput(
        instanceRef = instanceRef,
        sourceUrl = attach.url,
        previewUrl = attach.preview_url,
        originalUrl = attach.remote_url ?: attach.url,
        mimeType = attach.type?.let { mimeOf(it) },
        width = attach.meta?.width,
        height = attach.meta?.height,
        sha256 = null
    )

    // ------------------------------------------------------------------
    // Tag mapping (only when present in the same response; no extra request)
    // ------------------------------------------------------------------

    internal fun mapTag(name: String): ExternalTagInput = ExternalTagInput(
        instanceRef = instanceRef,
        sourceIdRef = null, // EXTERNAL tags need a sourceTagId; hashtags here map as platform tags by verbatim name
        name = name,
        sourceTagId = null,
        normalizedName = null
    )

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun mapVisibility(raw: String?): ContentVisibility = when (raw) {
        "public" -> ContentVisibility.PUBLIC
        "unlisted" -> ContentVisibility.UNLISTED
        "private" -> ContentVisibility.FOLLOWERS
        "direct" -> ContentVisibility.PRIVATE
        else -> ContentVisibility.UNKNOWN
    }

    private fun parseIsoToMillis(iso: String): Long? = try {
        OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    } catch (e: Exception) {
        // Also try plain Instant (UTC, no offset) before giving up.
        try {
            Instant.parse(iso).toEpochMilli()
        } catch (e2: Exception) {
            null
        }
    }

    /**
     * Minimal HTML -> plain-text conversion using only the JDK.
     *
     * Removes tags and decodes the most common entities. It is intentionally
     * NOT a full HTML parser (no new dependency). If input is not HTML-like it
     * is returned unchanged.
     */
    internal fun stripHtml(raw: String): String {
        if (!raw.contains('<')) return raw
        val noTags = raw.replace(Regex("<[^>]*>"), "")
        return decodeEntities(noTags).trim()
    }

    private fun decodeEntities(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")

    private fun mimeOf(mastodonType: String): String? = when (mastodonType) {
        "image" -> "image/*"
        "video" -> "video/*"
        "gifv" -> "video/*"
        "audio" -> "audio/*"
        else -> null
    }

    /**
     * Parses the HTTP Link header for rel="next" and extracts the max_id query
     * parameter as the next cursor. Returns null when absent/unparseable.
     */
    internal fun parseNextCursor(linkHeader: String?): String? {
        if (linkHeader == null) return null
        val linkPattern = Regex("<([^>]+)>\\s*;\\s*rel=\"next\"")
        val match = linkPattern.find(linkHeader) ?: return null
        val url = match.groupValues.getOrNull(1) ?: return null
        val maxId = Regex("[?&]max_id=([^&]+)").find(url)?.groupValues?.getOrNull(1)
            ?: return null
        return maxId.ifBlank { null }
    }
}

/**
 * Convenience: re-export the AdapterException factory used at the HTTP boundary.
 */
internal fun mastodonError(message: String, cause: Throwable? = null): AdapterException =
    AdapterException(message, cause)
