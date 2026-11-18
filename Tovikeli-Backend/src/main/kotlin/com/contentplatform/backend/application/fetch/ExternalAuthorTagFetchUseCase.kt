package com.contentplatform.backend.application.fetch

import com.contentplatform.backend.adapter.AdapterException
import com.contentplatform.backend.adapter.ExternalAdapter
import com.contentplatform.backend.adapter.ExternalAuthorInput
import com.contentplatform.backend.adapter.ExternalTagInput
import com.contentplatform.backend.adapter.FetchCommand
import com.contentplatform.backend.application.author.EnsureAndSaveAuthorUseCase
import com.contentplatform.backend.application.tag.EnsureAndSaveTagUseCase
import com.contentplatform.backend.domain.author.Author
import com.contentplatform.backend.domain.source.SourceType
import com.contentplatform.backend.domain.tag.Tag

/**
 * Minimal Application orchestration over the External Adapter boundary:
 *
 *   Resolved InstanceFetchContext + SourceType
 *       -> builds a FetchCommand (source type + instance domain only, no DB PublicId)
 *       -> ExternalAdapter.fetch(command)
 *       -> ExternalFetchResult
 *       -> maps ExternalAuthorInput to the existing EnsureAndSaveAuthorUseCase
 *       -> maps ExternalTagInput      to the existing EnsureAndSaveTagUseCase
 *
 * Responsibilities / boundaries:
 *  - The Application owns instance resolution/policy; [InstanceFetchContext]
 *    (holding the resolved [com.contentplatform.backend.domain.source.Instance])
 *    is NEVER passed to the adapter.
 *  - [sourceType] is supplied by the caller because the Instance aggregate does
 *    not carry the source type; the caller (which resolved the Instance from
 *    its known Source) provides it. The adapter receives only the source type
 *    in the FetchCommand, never a DB identifier.
 *  - Author/Tag natural-key ensure behaviour is REUSED from the existing Use
 *    Cases; no natural-key logic is copied here.
 *  - Content persistence is intentionally NOT attempted in this stage (Media
 *    PublicId is UNRESOLVED). Any ExternalContentInput that the adapter returns
 *    is intentionally not persisted by this use case.
 *  - A fetch failure surfaces as [AdapterException]; callers decide skip/log.
 *    No retry framework is introduced.
 */
class ExternalAuthorTagFetchUseCase(
    private val externalAdapter: ExternalAdapter,
    private val ensureAndSaveAuthorUseCase: EnsureAndSaveAuthorUseCase,
    private val ensureAndSaveTagUseCase: EnsureAndSaveTagUseCase
) {

    /**
     * Runs one fetch for the given resolved instance context and ensures all
     * returned authors and tags are persisted.
     *
     * @return the number of authors and tags ensured in this run
     */
    fun fetchAndEnsure(context: InstanceFetchContext, sourceType: SourceType): FetchSummary {
        val command = FetchCommand(
            sourceType = sourceType,
            instanceDomain = context.instanceDomain
        )
        val result = externalAdapter.fetch(command)

        result.authors.forEach { ensureAuthor(context, it) }
        result.tags.forEach { ensureTag(context, it) }

        return FetchSummary(
            authorsEnsured = result.authors.size,
            tagsEnsured = result.tags.size
        )
    }

    /**
     * Maps one [ExternalAuthorInput] to the existing author ensure use case.
     *
     * Field mapping follows the actual Domain Author fields:
     *  - profileUrl -> Domain `url`
     *  - avatarUrl  -> Domain `avatar`
     *  - remaining fields map directly; `status` is left null (not present in
     *    the 7A ExternalAuthorInput).
     */
    private fun ensureAuthor(context: InstanceFetchContext, input: ExternalAuthorInput): Author =
        ensureAndSaveAuthorUseCase.ensure(
            instanceId = context.instance.id,
            sourceAuthorId = input.sourceAuthorId,
            name = input.name,
            username = input.username,
            displayName = input.displayName,
            url = input.profileUrl,
            avatar = input.avatarUrl,
            bio = input.bio,
            status = null
        )

    /**
     * Maps one [ExternalTagInput] to the existing tag ensure use case.
     *
     *  - when sourceTagId is present -> EXTERNAL tag (identity
     *    instanceId + sourceTagId)
     *  - when sourceTagId is null    -> PLATFORM tag (identity verbatim name)
     *
     * name is always preserved verbatim.
     */
    private fun ensureTag(context: InstanceFetchContext, input: ExternalTagInput): Tag =
        if (input.sourceTagId != null) {
            ensureAndSaveTagUseCase.ensureExternal(
                name = input.name,
                normalizedName = input.normalizedName,
                sourceTagId = input.sourceTagId,
                instanceId = context.instance.id,
                sourceId = context.instance.sourceId
            )
        } else {
            ensureAndSaveTagUseCase.ensurePlatform(
                name = input.name,
                normalizedName = input.normalizedName
            )
        }
}

/**
 * Lightweight summary of one fetch+ensure run.
 */
data class FetchSummary(
    val authorsEnsured: Int,
    val tagsEnsured: Int
)
