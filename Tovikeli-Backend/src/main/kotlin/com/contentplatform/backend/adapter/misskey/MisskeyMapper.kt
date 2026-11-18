package com.contentplatform.backend.adapter.misskey

import com.contentplatform.backend.adapter.ExternalAuthorInput
import com.contentplatform.backend.adapter.ExternalContentInput
import com.contentplatform.backend.adapter.ExternalFetchResult
import com.contentplatform.backend.adapter.ExternalInstanceRef
import com.contentplatform.backend.adapter.ExternalMediaInput
import com.contentplatform.backend.adapter.ExternalTagInput
import com.contentplatform.backend.domain.content.ContentVisibility
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Pure Misskey -> External*Input mapping core.
 *
 * STRICTLY PRIVATE to the Misskey adapter package. This class:
 *  - converts raw Misskey JSON models into platform-independent adapter inputs
 *  - filters sensitive / content-warning notes at the adapter boundary
 *  - maps visibility to the existing Domain ContentVisibility enum
 *  - maps renote (boost) / reply references WITHOUT recursive fetching
 *  - maps files into ExternalMediaInput entries correlated by file id
 *
 * It NEVER generates a Media PublicId, never touches a repository, never
 * creates Domain aggregates, and never contacts the network.
 *
 * sensitive / CW policy (P9-F1 approved decision A):
 *  - a note with `sensitive == true`, OR `cw != null`, OR any of its effective
 *    files with `isSensitive == true` produces NO content, NO tag and NO media.
 *  - the same check is applied to the renoted note when the note is a boost,
 *    so a sensitive original cannot leak through a non-sensitive wrapper.
 *  - no sensitive field is introduced into the unified model; filtering is the
 *    only handling and happens entirely here.
 */
internal class MisskeyMapper(
    private val instanceDomain: String
) {

    private val instanceRef = ExternalInstanceRef(instanceDomain = instanceDomain)

    /**
     * Maps a parsed page of Misskey notes into an [ExternalFetchResult].
     *
     * There is no pagination cursor: the Misskey local-timeline response is a
     * plain array with no continuation token, and P9-F1 intentionally does not
     * implement an infinite pagination loop.
     */
    fun mapPage(notes: List<MisskeyNote>): ExternalFetchResult {
        val authors = LinkedHashMap<String, ExternalAuthorInput>()
        val contents = ArrayList<ExternalContentInput>()
        val media = LinkedHashMap<String, ExternalMediaInput>()
        val tags = LinkedHashMap<String, ExternalTagInput>()

        for (note in notes) {
            // Adapter-boundary sensitive/CW exclusion: the note contributes
            // nothing to the pipeline.
            if (isSensitive(note) || (note.renote != null && isSensitive(note.renote))) {
                continue
            }

            note.user?.let { user ->
                authors.putIfAbsent(user.id, mapAuthor(user))
            }
            contents += mapContent(note)

            val effective = note.renote ?: note
            effective.files.orEmpty().forEach { file ->
                media.putIfAbsent(file.id, mapMedia(file))
            }
            effective.tags.orEmpty().forEach { tag ->
                tags.putIfAbsent(tag, mapTag(tag))
            }
        }

        return ExternalFetchResult(
            authors = authors.values.toList(),
            contents = contents,
            // Correlation-preserving map: key = external file id, which is the
            // SAME value placed into content.mediaRefs.
            media = media,
            tags = tags.values.toList(),
            nextCursor = null
        )
    }

    /**
     * Approved P9-F1 sensitive / CW fact set. A note is excluded when it is
     * explicitly sensitive, carries a content warning, or exposes at least one
     * sensitive file.
     */
    internal fun isSensitive(note: MisskeyNote): Boolean =
        note.sensitive == true ||
            note.cw != null ||
            note.files.orEmpty().any { it.isSensitive == true }

    // ------------------------------------------------------------------
    // Author mapping
    // ------------------------------------------------------------------

    internal fun mapAuthor(user: MisskeyUser): ExternalAuthorInput = ExternalAuthorInput(
        instanceRef = instanceRef,
        sourceAuthorId = user.id,
        name = user.name?.takeIf { it.isNotBlank() } ?: user.username,
        username = user.username,
        displayName = user.name,
        avatarUrl = user.avatarUrl,
        profileUrl = null,
        bio = user.description
    )

    // ------------------------------------------------------------------
    // Content mapping
    // ------------------------------------------------------------------

    internal fun mapContent(note: MisskeyNote): ExternalContentInput {
        // Renote wrapper: when note.renote is present, this note is a boost of
        // the wrapped note -> repostOfRef = wrapped id. No recursion.
        val effective = note.renote ?: note
        val isRepost = note.renote != null

        val contentTags = effective.tags.orEmpty().map(::mapTag)

        return ExternalContentInput(
            instanceRef = instanceRef,
            sourceContentId = if (isRepost) note.id else effective.id,
            authorRef = note.user?.id ?: note.userId.orEmpty(),
            text = effective.text,
            language = effective.lang,
            externalCreatedAt = effective.createdAt?.let(::parseIsoToMillis),
            visibility = mapVisibility(effective.visibility),
            mediaRefs = effective.files.orEmpty().map { it.id },
            parentRef = effective.replyId,
            repostOfRef = if (isRepost) effective.id else null,
            // Misskey has no dedicated quote entity in this V1 mapping.
            quoteOfRef = null,
            metadata = emptyMap(),
            tags = contentTags
        )
    }

    // ------------------------------------------------------------------
    // Media mapping
    // ------------------------------------------------------------------

    internal fun mapMedia(file: MisskeyFile): ExternalMediaInput = ExternalMediaInput(
        instanceRef = instanceRef,
        sourceUrl = file.url,
        previewUrl = file.thumbnailUrl,
        originalUrl = file.url,
        sha256 = null,
        mimeType = file.type,
        width = file.properties?.width,
        height = file.properties?.height,
        altText = file.comment,
        license = null
    )

    // ------------------------------------------------------------------
    // Tag mapping (Misskey note tags are plain strings -> platform tags)
    // ------------------------------------------------------------------

    internal fun mapTag(name: String): ExternalTagInput = ExternalTagInput(
        instanceRef = instanceRef,
        sourceIdRef = null,
        name = name,
        sourceTagId = null,
        normalizedName = null
    )

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Misskey note visibility -> existing Domain ContentVisibility.
     *
     * "home" is mapped to UNLISTED (not PUBLIC) so it never enters the public
     * pool, mirroring the existing public browse/search gate.
     */
    internal fun mapVisibility(raw: String?): ContentVisibility = when (raw) {
        "public" -> ContentVisibility.PUBLIC
        "home" -> ContentVisibility.UNLISTED
        "followers" -> ContentVisibility.FOLLOWERS
        "specified" -> ContentVisibility.PRIVATE
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
}
