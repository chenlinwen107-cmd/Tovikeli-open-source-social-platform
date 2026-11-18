package com.contentplatform.backend.application.content

import com.contentplatform.backend.adapter.ExternalContentInput
import com.contentplatform.backend.adapter.ExternalMediaInput
import com.contentplatform.backend.application.author.EnsureAndSaveAuthorUseCase
import com.contentplatform.backend.application.fetch.InstanceFetchContext
import com.contentplatform.backend.application.tag.EnsureAndSaveTagUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.Content
import com.contentplatform.backend.domain.media.Media
import com.contentplatform.backend.domain.media.MediaUrl
import com.contentplatform.backend.domain.tag.Tag
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.repository.MediaRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

/**
 * Result of one [SaveExternalContentUseCase.save] run.
 *
 * [content] is the persisted content aggregate: the newly saved content, or the
 * already-existing content when the natural key (instanceId, sourceContentId)
 * was already persisted (existing-return / no-update semantics preserved).
 *
 * Unresolved-reference reporting (locked decision C2, extended for media):
 *  - [unresolvedParentRef] / [unresolvedRepostOfRef] / [unresolvedQuoteOfRef]
 *    carry the EXTERNAL reference value when that relation was supplied in the
 *    input but could NOT be resolved to an already-persisted Content; they are
 *    null when the relation was resolved or was never supplied.
 *  - [unresolvedMediaRefs] carries every media reference that was supplied in
 *    `input.mediaRefs` but for which NO correlated ExternalMediaInput was
 *    provided in `mediaByExternalRef` (Stage 11). Such media are never
 *    fabricated: the content is still saved with only the resolved media
 *    attached (in their original relative order).
 *  - [warnings] is the structured, human-readable list of unresolved
 *    references (relations + media).
 *
 * Media (Stage 11 contract): [mediaRefsCount] counts all supplied media refs;
 * the correlated subset is persisted as real Media aggregates BEFORE the
 * content save and their deterministic ids are attached through
 * `content.mediaIds` / `content_media`. Missing correlation is reported (never
 * silently dropped or faked).
 */
data class SaveExternalContentResult(
    val content: Content,
    val unresolvedParentRef: String?,
    val unresolvedRepostOfRef: String?,
    val unresolvedQuoteOfRef: String?,
    val mediaRefsCount: Int,
    val unresolvedMediaRefs: List<String> = emptyList()
) {

    /** Total number of supplied references (relations + media) that could not be resolved. */
    val unresolvedReferenceCount: Int
        get() = listOf(unresolvedParentRef, unresolvedRepostOfRef, unresolvedQuoteOfRef)
            .count { it != null } + unresolvedMediaRefs.size

    /**
     * Structured unresolved-reference warnings, one per unresolved relation or
     * media ref. Empty when every supplied reference was resolved (or none was
     * supplied).
     */
    val warnings: List<String>
        get() = buildList {
            unresolvedParentRef?.let {
                add("parentRef=$it unresolved: no persisted content with natural key " +
                    "(instanceId, $it); relation kept null")
            }
            unresolvedRepostOfRef?.let {
                add("repostOfRef=$it unresolved: no persisted content with natural key " +
                    "(instanceId, $it); relation kept null")
            }
            unresolvedQuoteOfRef?.let {
                add("quoteOfRef=$it unresolved: no persisted content with natural key " +
                    "(instanceId, $it); relation kept null")
            }
            unresolvedMediaRefs.forEach {
                add("mediaRef=$it unresolved: no correlated ExternalMediaInput for " +
                    "(instanceId, $it); media skipped, no fake Media created")
            }
        }
}

/**
 * Stage 8/11 Content + Media ingestion orchestration:
 *
 *   ExternalContentInput + InstanceFetchContext + correlated ExternalMediaInputs
 *       -> authorRef resolved via EnsureAndSaveAuthorUseCase (reused)
 *       -> parentRef / repostOfRef / quoteOfRef resolved ONLY against
 *          already-persisted Content (ContentRepository natural-key lookup)
 *       -> each mediaRef with a correlated ExternalMediaInput:
 *            Media.createFromAdapter (deterministic md_ identity) -> MediaRepository.save
 *       -> resolved Media PublicIds (mediaRefs order preserved) passed as
 *          mediaIds to SaveContentWithAssembliesUseCase
 *       -> Content.createFromAdapter() via SaveContentWithAssembliesUseCase
 *          (verifies the saved media exist, then persists content + content_media)
 *       -> persisted inside the transaction owned by THIS use case
 *
 * Responsibilities / boundaries:
 *  - Transaction boundary (locked decision): THIS use case owns the outer
 *    [TransactionTemplate]. [EnsureAndSaveAuthorUseCase] (author write),
 *    media writes (MediaRepository) and
 *    [SaveContentWithAssembliesUseCase] (content write) join the SAME
 *    transaction through Spring REQUIRED propagation. Repositories declare no
 *    transaction. No transaction framework is introduced.
 *  - Media identity (Stage 11 contract): md_ + SHA-256(instanceId + ":" +
 *    externalMediaRef), derived inside Media.createFromAdapter. sha256 is only
 *    a dedup candidate and never takes part in identity. Media.sourceId /
 *    Media.instanceId come from the resolved context.instance.
 *  - Media correlation: media inputs arrive as a Map keyed by the same EXTERNAL
 *    reference strings found in `input.mediaRefs` (carried by
 *    ExternalFetchResult.media). A ref without a correlated input is reported
 *    as unresolved — never faked, never auto-created from the ref alone.
 *  - Author ensure behaviour is REUSED (never re-implemented). When the author
 *    is not yet persisted, the ensure creates it using the external author ref
 *    as the only identity available on [ExternalContentInput]; in a full fetch
 *    pipeline the author is normally ensured beforehand from the richer
 *    ExternalAuthorInput carried by the same ExternalFetchResult.
 *  - [sourceId] is taken from the already-resolved [InstanceFetchContext.instance];
 *    no new Source lookup API is introduced.
 *  - Reference resolution ONLY considers already-persisted targets (locked
 *    decisions C2/C4): same-batch, not-yet-persisted targets are treated as
 *    unresolved references — never fetched, created, deferred or updated.
 *  - Natural-key dedup and existing-return / no-update semantics are inherited
 *    from [SaveContentWithAssembliesUseCase] and preserved untouched.
 *  - The Mastodon Adapter / Mapper is NOT touched (locked decision C3 / Stage
 *    7C): a boost wrapper Content produced by the adapter keeps its own
 *    sourceContentId / PublicId and a repostOfRef to the original; when the
 *    original is already persisted the repostOf relation is set, otherwise it
 *    is reported unresolved and kept null.
 */
@Service
@ConditionalOnProperty(name = ["spring.datasource.url"])
class SaveExternalContentUseCase(
    private val ensureAndSaveAuthorUseCase: EnsureAndSaveAuthorUseCase,
    private val ensureAndSaveTagUseCase: EnsureAndSaveTagUseCase,
    private val attachTagsToContentUseCase: AttachTagsToContentUseCase,
    private val contentRepository: ContentRepository,
    private val mediaRepository: MediaRepository,
    private val saveContentWithAssembliesUseCase: SaveContentWithAssembliesUseCase,
    private val transactionTemplate: TransactionTemplate
) {

    /**
     * Ingests one external content input together with its correlated media.
     *
     * @param context the Application-level fetch context whose [InstanceFetchContext.instance]
     *        carries the resolved instance (id + sourceId) this content belongs to
     * @param input   the normalized external content facts
     * @param mediaByExternalRef correlated media facts keyed by the SAME external
     *        reference strings as [ExternalContentInput.mediaRefs]; normally the
     *        ExternalFetchResult.media map from the same adapter fetch
     * @return the persisted content plus structured unresolved-reference info
     */
    fun save(
        context: InstanceFetchContext,
        input: ExternalContentInput,
        mediaByExternalRef: Map<String, ExternalMediaInput> = emptyMap()
    ): SaveExternalContentResult {
        return transactionTemplate.execute {
            val instanceId = context.instance.id
            val sourceId = context.instance.sourceId

            // Author: reused ensure (check-then-create) keyed by the external author ref.
            val authorId = ensureAndSaveAuthorUseCase.ensure(
                instanceId = instanceId,
                sourceAuthorId = input.authorRef,
                name = input.authorRef,
                username = input.authorRef
            ).id

            // Resolve relations ONLY against already-persisted content.
            val resolvedParent = resolveExistingContent(instanceId, input.parentRef)
            val resolvedRepostOf = resolveExistingContent(instanceId, input.repostOfRef)
            val resolvedQuoteOf = resolveExistingContent(instanceId, input.quoteOfRef)

            // Media (Stage 11): persist every correlated media BEFORE content save,
            // preserving the mediaRefs order. Missing correlation is reported as an
            // unresolved media ref; no fake Media is ever created.
            val resolvedMediaIds = ArrayList<PublicId>(input.mediaRefs.size)
            val unresolvedMediaRefs = ArrayList<String>()
            input.mediaRefs.forEach { ref ->
                val mediaInput = mediaByExternalRef[ref]
                if (mediaInput == null) {
                    unresolvedMediaRefs += ref
                } else {
                    val media = Media.createFromAdapter(
                        instanceId = instanceId,
                        externalMediaRef = ref,
                        sourceId = sourceId,
                        sha256 = mediaInput.sha256,
                        preview = mediaInput.previewUrl?.let { MediaUrl(it) },
                        original = mediaInput.originalUrl?.let { MediaUrl(it) }
                            ?: mediaInput.sourceUrl?.let { MediaUrl(it) },
                        altText = mediaInput.altText,
                        license = mediaInput.license,
                        metadata = mediaInput.metadata
                    )
                    mediaRepository.save(media)
                    resolvedMediaIds += media.id
                }
            }

            // Delegate: verifies the saved media exist, natural-key dedup,
            // Content.createFromAdapter, FK-safe save + content_media ordering —
            // runs in the SAME transaction (REQUIRED propagation).
            val saved = saveContentWithAssembliesUseCase.save(
                instanceId = instanceId,
                sourceContentId = input.sourceContentId,
                authorId = authorId,
                sourceId = sourceId,
                text = input.text,
                language = input.language,
                visibility = input.visibility,
                mediaIds = resolvedMediaIds,
                parentId = resolvedParent?.id,
                repostOfId = resolvedRepostOf?.id,
                quoteOfId = resolvedQuoteOf?.id,
                externalCreatedAt = input.externalCreatedAt,
                metadata = input.metadata
            )

            // Tags (Stage 15-B): every ExternalTagInput carried by THIS content
            // is ensured through the REUSED EnsureAndSaveTagUseCase (no
            // natural-key logic is copied here) and attached through
            // AttachTagsToContentUseCase in the SAME transaction. Attaching is
            // additive and idempotent (ON CONFLICT DO NOTHING): existing
            // associations of a re-ingested content are never replaced/deleted,
            // only the associations present in this input are ensured to exist.
            val tagIds = ensureTagIds(context, input.tags)
            attachTagsToContentUseCase.attach(contentId = saved.id, tagIds = tagIds)

            SaveExternalContentResult(
                content = saved,
                unresolvedParentRef = if (input.parentRef != null && resolvedParent == null) input.parentRef else null,
                unresolvedRepostOfRef = if (input.repostOfRef != null && resolvedRepostOf == null) input.repostOfRef else null,
                unresolvedQuoteOfRef = if (input.quoteOfRef != null && resolvedQuoteOf == null) input.quoteOfRef else null,
                mediaRefsCount = input.mediaRefs.size,
                unresolvedMediaRefs = unresolvedMediaRefs
            )
        }
    }

    /**
     * Ensures every tag fact of [input] through the reused
     * [EnsureAndSaveTagUseCase] and returns the resolved [Tag] ids.
     *
     * Routing mirrors the existing tag boundary: a tag with a sourceTagId is an
     * EXTERNAL tag (identity instance + sourceTagId); a tag without one is a
     * PLATFORM tag (identity verbatim name). The content always attaches only
     * its OWN tags — never a batch-flattened list.
     */
    private fun ensureTagIds(
        context: InstanceFetchContext,
        tags: List<com.contentplatform.backend.adapter.ExternalTagInput>
    ): List<PublicId> =
        tags.map { tagInput ->
            val tag: Tag = if (tagInput.sourceTagId != null) {
                ensureAndSaveTagUseCase.ensureExternal(
                    name = tagInput.name,
                    normalizedName = tagInput.normalizedName,
                    sourceTagId = tagInput.sourceTagId,
                    instanceId = context.instance.id,
                    sourceId = context.instance.sourceId
                )
            } else {
                ensureAndSaveTagUseCase.ensurePlatform(
                    name = tagInput.name,
                    normalizedName = tagInput.normalizedName
                )
            }
            tag.id
        }

    /**
     * Resolves one external content reference to an already-persisted [Content]
     * via the natural key (instanceId, externalSourceContentId), or returns
     * null when the target is not in the database yet.
     *
     * This is the ONLY supported resolution path (locked decisions C2/C4): no
     * batch query, no placeholder creation, no recursive fetch, no deferred
     * reference and no automatic empty-content creation.
     */
    private fun resolveExistingContent(
        instanceId: PublicId,
        externalSourceContentId: String?
    ): Content? {
        if (externalSourceContentId == null) return null
        return contentRepository.findByInstanceAndSourceContentId(instanceId, externalSourceContentId)
    }
}
