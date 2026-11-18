package com.contentplatform.backend.application.content

import com.contentplatform.backend.adapter.ExternalAdapter
import com.contentplatform.backend.adapter.FetchCommand
import com.contentplatform.backend.application.fetch.InstanceFetchContext
import com.contentplatform.backend.domain.source.SourceType

/**
 * Stage 11B Content fetch + ingest orchestration over the External Adapter
 * boundary:
 *
 *   Resolved InstanceFetchContext + SourceType
 *       -> builds a FetchCommand (source type + instance domain only, no DB
 *          PublicId)   [same contract as ExternalAuthorTagFetchUseCase]
 *       -> ExternalAdapter.fetch(command)
 *       -> ExternalFetchResult
 *       -> for EVERY ExternalContentInput in result.contents:
 *            SaveExternalContentUseCase.save(context, content,
 *                mediaByExternalRef = result.media)
 *
 * Responsibilities / boundaries:
 *  - Each content is saved INDEPENDENTLY through the reused
 *    [SaveExternalContentUseCase]; there is intentionally NO page-level (whole
 *    batch) transaction added here. The per-content transaction boundary owned
 *    by [SaveExternalContentUseCase] is untouched.
 *  - The same ExternalFetchResult.media correlation map is handed to every
 *    content save of the batch, so media facts fetched once can be resolved
 *    against every mediaRef that references them.
 *  - Media attachment / unresolved-reference reporting is delegated to the
 *    reused [SaveExternalContentUseCase]; nothing here fabricates Media, and no
 *    content-related policy is re-implemented.
 *  - The Application owns instance resolution/policy; [InstanceFetchContext]
 *    (holding the resolved Instance) is NEVER passed to the adapter — the
 *    FetchCommand only carries the external instance domain.
 *  - [sourceType] is supplied by the caller exactly like
 *    ExternalAuthorTagFetchUseCase; the adapter receives only the source type
 *    in the FetchCommand, never a DB identifier.
 *  - A fetch failure surfaces as AdapterException; a single content-save
 *    failure propagates out of that content's own transaction and aborts the
 *    batch loop (callers decide skip/log/retry). No retry framework is
 *    introduced.
 */
class FetchAndIngestExternalContentUseCase(
    private val externalAdapter: ExternalAdapter,
    private val saveExternalContentUseCase: SaveExternalContentUseCase
) {

    /**
     * Runs one fetch for the given resolved instance context and persists every
     * returned external content (with its correlated media) via the reused
     * [SaveExternalContentUseCase].
     *
     * @return the aggregated [ContentIngestSummary] of this run
     */
    fun fetchAndIngest(
        context: InstanceFetchContext,
        sourceType: SourceType
    ): ContentIngestSummary {
        val command = FetchCommand(
            sourceType = sourceType,
            instanceDomain = context.instanceDomain
        )
        val result = externalAdapter.fetch(command)

        var contentsIngested = 0
        var mediaAttached = 0
        val unresolvedMediaRefs = mutableListOf<String>()

        result.contents.forEach { content ->
            val saveResult = saveExternalContentUseCase.save(
                context = context,
                input = content,
                mediaByExternalRef = result.media
            )
            contentsIngested++
            mediaAttached += saveResult.content.mediaIds.size
            unresolvedMediaRefs += saveResult.unresolvedMediaRefs
        }

        return ContentIngestSummary(
            contentsIngested = contentsIngested,
            mediaAttached = mediaAttached,
            unresolvedMediaRefs = unresolvedMediaRefs
        )
    }
}

/**
 * Lightweight aggregated summary of one fetch + ingest run.
 *
 * - [contentsIngested]:   number of ExternalContentInput items saved via
 *   [SaveExternalContentUseCase] in this run.
 * - [mediaAttached]:      total number of resolved Media PublicIds attached to
 *   the ingested contents (accumulated per save result from
 *   `content.mediaIds.size`, so one Media attached to two contents counts
 *   twice — the count tracks attachments, not distinct Media rows).
 * - [unresolvedMediaRefs]: every media ref from any ingested content that had
 *   no correlated ExternalMediaInput and was therefore skipped (never faked),
 *   aggregated across all save results of this run.
 */
data class ContentIngestSummary(
    val contentsIngested: Int,
    val mediaAttached: Int,
    val unresolvedMediaRefs: List<String>
)
