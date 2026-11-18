package com.contentplatform.backend.api.content

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.content.BrowseContentUseCase
import com.contentplatform.backend.application.content.PublishContentUseCase
import com.contentplatform.backend.application.content.DeleteContentUseCase
import com.contentplatform.backend.application.content.EditContentUseCase
import com.contentplatform.backend.application.content.GetContentDetailUseCase
import com.contentplatform.backend.application.content.GetContentMediaUseCase
import com.contentplatform.backend.application.content.GetContentRelationshipsUseCase
import com.contentplatform.backend.application.content.HideContentUseCase
import com.contentplatform.backend.application.content.RestoreContentUseCase
import com.contentplatform.backend.application.content.ContentAccessUseCase
import com.contentplatform.backend.application.content.ContentAccessState
import com.contentplatform.backend.application.history.BrowseHistoryUseCase
import com.contentplatform.backend.repository.AuthorRepository
import com.contentplatform.backend.api.media.MediaResponse
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.content.ContentLifecycleResult
import com.contentplatform.backend.repository.ContentCursor
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder

/**
 * Read-only Content API.
 *
 *   GET /api/v1/contents        browse recently created public Content
 *   GET /api/v1/contents/{id}   read one Content detail
 *
 * Browse flow: HTTP -> this controller -> [BrowseContentUseCase] -> ContentRepository.
 * Detail flow:  HTTP -> this controller -> [GetContentDetailUseCase] -> ContentRepository + CommentRepository + InteractionRepository.
 * The controller never touches a Repository directly and contains no business
 * logic.
 *
 * Browse semantics (HTTP boundary only):
 *  - `limit` is an optional query parameter, default 20.
 *  - Accepted range 0..100. The bound mirrors the ContentRepository contract's
 *    MAX_PUBLIC_BROWSE_LIMIT so out-of-range input is answered with HTTP 400
 *    before the use case is invoked and no repository exception travels up.
 *  - Non-integer input is answered with HTTP 400 by Spring MVC's standard
 *    type-conversion handling (no custom error framework).
 *  - limit=0 and empty browse results both yield HTTP 200 with an empty array.
 *  - `instanceId` and `authorId` are optional query parameters (Stage 13-A /
 *    Stage 13-B). A non-blank value is wrapped into a [PublicId] at this HTTP
 *    boundary and forwarded as an instance/author filter; filters combine with
 *    AND semantics. An absent value means "no filter", while a blank/
 *    whitespace value is answered with HTTP 400 and no [BrowseContentUseCase]
 *    invocation happens for blank input. A syntactically valid but unknown
 *    instanceId/authorId is NOT an error: it yields HTTP 200 with an empty
 *    array (browse semantics, no 404).
 *  - `hasMedia` is an optional query parameter (Stage 13-C) accepted as the
 *    case-insensitive literals "true"/"false" and parsed explicitly at this
 *    HTTP boundary. Absent means "no media-presence filter". The empty string,
 *    whitespace-only input and any other literal are answered with HTTP 400
 *    (the value is deliberately NOT handed to Spring's Boolean conversion,
 *    which would silently treat an empty string as false) and the use case is
 *    never invoked for invalid input.
 *  - `cursor` is an optional query parameter (Stage 14) carrying the versioned
 *    Base64URL keyset cursor of a previous page. Absent means "first page".
 *    The raw value is strictly decoded by [ContentCursorCodec]: blank input,
 *    invalid Base64URL, a missing/unknown version, a malformed field count, a
 *    missing/non-Long/negative createdAt or an invalid content id are all
 *    answered with HTTP 400 and never reach the use case.
 *  - Pagination is stateless keyset pagination over the frozen ordering
 *    (created_at DESC, c.id DESC). When another page exists the response
 *    carries a standard HTTP `Link: <...>; rel="next"` header whose URL keeps
 *    the current limit and filters and replaces the cursor with the new
 *    nextCursor. No Link header is sent on the last page, on empty results,
 *    for limit=0 or for 400 responses. The response body itself stays a flat
 *    JSON array; no envelope, no pagination metadata object and no custom
 *    next-cursor header are introduced.
 *  - No search, ranking or envelope semantics exist here.
 *
 * Not-found semantics (detail): when the use case returns null the controller
 * responds HTTP 404 with an empty body; otherwise HTTP 200 with the API
 * [ContentResponse].
 *
 * PublicId: the raw path variable is wrapped by the existing [PublicId] value
 * object (its only rule: non-blank). No new parser and no prefix contract is
 * introduced at the API layer.
 *
 * Wiring: conditional on a configured datasource, mirroring the Stage 9/10B
 * persistence-backed query use cases, so the no-datasource context keeps
 * working.
 */
@RestController
@RequestMapping("/api/v1/contents")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class ContentController(
    private val browseContentUseCase: BrowseContentUseCase,
    private val publishContentUseCase: PublishContentUseCase,
    private val getContentDetailUseCase: GetContentDetailUseCase,
    private val getContentMediaUseCase: GetContentMediaUseCase,
    private val getContentRelationshipsUseCase: GetContentRelationshipsUseCase,
    private val deleteContentUseCase: DeleteContentUseCase,
    private val editContentUseCase: EditContentUseCase,
    private val hideContentUseCase: HideContentUseCase,
    private val restoreContentUseCase: RestoreContentUseCase,
    private val contentAccessUseCase: ContentAccessUseCase,
    private val browseHistoryUseCase: BrowseHistoryUseCase,
    private val authorRepository: AuthorRepository
) {

    companion object {
        private const val DEFAULT_BROWSE_LIMIT = 20
        private const val MAX_BROWSE_LIMIT = 100
    }

    @PostMapping
    fun publishContent(
        @RequestBody request: PublishContentRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Map<String, String>> {
        val caller = CallerResolver.require(servletRequest)

        val content = try {
            publishContentUseCase.publish(
                userId = caller.userId,
                text = request.text.orEmpty(),
                mediaIds = request.mediaIds.map { PublicId(it) },
                poll = request.poll?.let { com.contentplatform.backend.application.poll.CreatePollDraft(it.options, it.multi, it.days) },
                location = request.location,
                visibility = request.visibility,
                anonymous = request.anonymous
            )
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }

        return ResponseEntity.ok(
            mapOf("id" to content.id.value)
        )
    }

    @GetMapping
    fun browseContent(
        // default value is a compile-time literal (Spring annotation requirement);
        // it intentionally matches DEFAULT_BROWSE_LIMIT above.
        @RequestParam(name = "limit", defaultValue = "20") limit: Int,
        @RequestParam(name = "instanceId", required = false) instanceId: String?,
        @RequestParam(name = "authorId", required = false) authorId: String?,
        @RequestParam(name = "hasMedia", required = false) hasMedia: String?,
        @RequestParam(name = "cursor", required = false) cursor: String?,
        servletRequest: HttpServletRequest
    ): ResponseEntity<List<ContentResponse>> {
        if (limit !in 0..MAX_BROWSE_LIMIT) {
            return ResponseEntity.badRequest().build()
        }
        // Absent instanceId/authorId means "no filter". A blank value is a
        // client error answered with HTTP 400: wrapping it into PublicId would
        // throw IllegalArgumentException and surface as a 500 (no error
        // framework exists), so blank input is rejected here instead and the
        // use case is never invoked.
        val instanceFilter = when {
            instanceId == null -> null
            instanceId.isBlank() -> return ResponseEntity.badRequest().build()
            else -> PublicId(instanceId)
        }
        val authorFilter = when {
            authorId == null -> null
            authorId.isBlank() -> return ResponseEntity.badRequest().build()
            else -> PublicId(authorId)
        }
        // hasMedia is parsed explicitly from the raw string. Binding it as a
        // Boolean directly would let Spring's Boolean conversion silently turn
        // the empty string into false; the frozen HTTP contract requires the
        // empty/blank value and any non-"true"/"false" literal to be a 400.
        val mediaFilter = when {
            hasMedia == null -> null
            hasMedia.equals("true", ignoreCase = true) -> true
            hasMedia.equals("false", ignoreCase = true) -> false
            else -> return ResponseEntity.badRequest().build()
        }
        // Absent cursor means "first page". Any present cursor is strictly
        // decoded at this HTTP boundary; malformed cursors are answered with
        // 400 and never reach the use case (no error framework exists, so the
        // codec's IllegalArgumentException must not escape as a 500).
        val decodedCursor = if (cursor == null) {
            null
        } else {
            try {
                ContentCursorCodec.decode(cursor)
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().build()
            }
        }
        // limit=0 is frozen as "200 [] with no DB query": every parameter above
        // has already been validated (so a malformed cursor still yields 400),
        // and the repository is deliberately not invoked.
        if (limit == 0) {
            return ResponseEntity.ok(emptyList())
        }

        val page = browseContentUseCase.browse(instanceFilter, authorFilter, mediaFilter, decodedCursor, limit, CallerResolver.resolve(servletRequest)?.userId)
        val body = page.contents.map { ContentResponse.from(it) }
        val nextCursor = page.nextCursor ?: return ResponseEntity.ok(body)
        return ResponseEntity.ok()
            .header(HttpHeaders.LINK, buildNextLink(limit, instanceFilter, authorFilter, mediaFilter, nextCursor))
            .body(body)
    }

    /**
     * Builds the standard `Link: <...>; rel="next"` header value for the next
     * browse page. The URL keeps the current limit and filters verbatim and
     * carries the newly generated Base64URL [nextCursor]; when a filter is
     * absent it is omitted from the URL.
     */
    private fun buildNextLink(
        limit: Int,
        instanceId: PublicId?,
        authorId: PublicId?,
        hasMedia: Boolean?,
        nextCursor: ContentCursor
    ): String {
        val builder = UriComponentsBuilder.fromPath("/api/v1/contents")
            .queryParam("limit", limit)
            .apply {
                if (instanceId != null) queryParam("instanceId", instanceId.value)
                if (authorId != null) queryParam("authorId", authorId.value)
                if (hasMedia != null) queryParam("hasMedia", hasMedia.toString())
            }
            .queryParam("cursor", ContentCursorCodec.encode(nextCursor))
        val uri = builder.build().encode().toUriString()
        return "<$uri>; rel=\"next\""
    }

    @GetMapping("/{id}")
    fun getContent(@PathVariable("id") id: String, servletRequest: HttpServletRequest): ResponseEntity<ContentResponse> {
        val detail = getContentDetailUseCase.getById(PublicId(id))
            ?: return ResponseEntity.notFound().build()
        val caller = CallerResolver.resolve(servletRequest)
        val state = contentAccessUseCase.state(detail.content, caller?.userId)
        if (state == ContentAccessState.FORBIDDEN) return ResponseEntity.notFound().build()
        if (caller != null && state != ContentAccessState.LOCKED) {
            val author = authorRepository.findById(detail.content.authorId)
            browseHistoryUseCase.record(
                userId = caller.userId,
                type = "post",
                targetId = id,
                title = detail.content.text?.replace(Regex("\\s+"), " ")?.take(120).orEmpty().ifBlank { "动态" },
                typeLabel = "动态",
                link = "/post/$id",
                icon = "file-text",
                authorNickname = author?.displayName ?: author?.name ?: author?.username.orEmpty()
            )
        }
        val detailAuthor = if (detail.content.metadata["anonymous"] == true) null else authorRepository.findById(detail.content.authorId)
        return ResponseEntity.ok(ContentResponse.from(detail, state == ContentAccessState.LOCKED, detailAuthor))
    }

    /**
     * Returns the media associated with a Content (Stage 33).
     *
     * Expands Content.mediaIds into full [MediaResponse] objects, preserving
     * the association order. Missing or unreadable media are silently omitted.
     * An empty list is a valid result (HTTP 200 with []).
     *
     * Not-found semantics: when the Content does not exist or is not ACTIVE,
     * the controller responds HTTP 404.
     */
    @GetMapping("/{contentId}/media")
    fun getContentMedia(@PathVariable contentId: String, servletRequest: HttpServletRequest): ResponseEntity<List<MediaResponse>> {
        val id = try { PublicId(contentId) } catch (_: IllegalArgumentException) {
            return ResponseEntity.notFound().build()
        }
        val readableMediaList = getContentMediaUseCase.getMedia(id, CallerResolver.resolve(servletRequest)?.userId)
            ?: return ResponseEntity.notFound().build()
        val body = readableMediaList.map { MediaResponse.from(it.media, it.instance) }
        return ResponseEntity.ok(body)
    }

    /**
     * Returns the direct Content relationships (Stage 31).
     *
     * Exposes parentId, repostOfId, and quoteOfId as related Content. A
     * missing/inaccessible related Content yields null for that field; it
     * does not cause the entire request to fail.
     *
     * Not-found semantics: when the current Content does not exist or is not
     * ACTIVE, the controller responds HTTP 404. Otherwise HTTP 200 with the
     * relationships (all fields may be null).
     */
    @GetMapping("/{contentId}/relationships")
    fun getRelationships(@PathVariable contentId: String): ResponseEntity<ContentRelationshipsResponse> {
        val id = try { PublicId(contentId) } catch (_: IllegalArgumentException) {
            return ResponseEntity.notFound().build()
        }
        val relationships = getContentRelationshipsUseCase.getRelationships(id)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(
            ContentRelationshipsResponse(
                parent = relationships.parent?.let { RelatedContentResponse.from(it) },
                repostOf = relationships.repostOf?.let { RelatedContentResponse.from(it) },
                quoteOf = relationships.quoteOf?.let { RelatedContentResponse.from(it) }
            )
        )
    }

    /**
     * Soft-deletes a content aggregate: ACTIVE -> DELETED.
     *
     * Returns HTTP 204 No Content on success, 404 when the content does not
     * exist, or 400 when the current status does not allow deletion.
     */
    @PutMapping("/{contentId}")
    fun editContent(
        @PathVariable contentId: String,
        @RequestBody request: EditContentRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<ContentResponse> {
        val caller = CallerResolver.require(servletRequest)
        val id = try { PublicId(contentId) } catch (_: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
        return when (editContentUseCase.edit(id, caller.userId, request.text.orEmpty())) {
            ContentLifecycleResult.SUCCESS -> {
                val updated = getContentDetailUseCase.getById(id)
                    ?: return ResponseEntity.notFound().build()
                ResponseEntity.ok(ContentResponse.from(updated))
            }
            ContentLifecycleResult.NOT_FOUND -> ResponseEntity.notFound().build()
            ContentLifecycleResult.INVALID_TRANSITION -> ResponseEntity.badRequest().build()
            ContentLifecycleResult.FORBIDDEN -> ResponseEntity.status(403).build()
        }
    }

    @PostMapping("/{contentId}/delete")
    fun deleteContent(
        @PathVariable contentId: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Void> {
        val caller = CallerResolver.require(servletRequest)
        val id = try { PublicId(contentId) } catch (_: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
        return when (deleteContentUseCase.delete(id, caller.userId)) {
            ContentLifecycleResult.SUCCESS -> ResponseEntity.noContent().build()
            ContentLifecycleResult.NOT_FOUND -> ResponseEntity.notFound().build()
            ContentLifecycleResult.INVALID_TRANSITION -> ResponseEntity.badRequest().build()
            ContentLifecycleResult.FORBIDDEN -> ResponseEntity.status(403).build()
        }
    }

    @PostMapping("/{contentId}/hide")
    fun hideContent(
        @PathVariable contentId: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Void> {
        CallerResolver.require(servletRequest)
        val id = try { PublicId(contentId) } catch (_: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
        return when (hideContentUseCase.hide(id)) {
            ContentLifecycleResult.SUCCESS -> ResponseEntity.noContent().build()
            ContentLifecycleResult.NOT_FOUND -> ResponseEntity.notFound().build()
            ContentLifecycleResult.INVALID_TRANSITION -> ResponseEntity.badRequest().build()
            ContentLifecycleResult.FORBIDDEN -> ResponseEntity.status(403).build()
        }
    }

    @PostMapping("/{contentId}/restore")
    fun restoreContent(
        @PathVariable contentId: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Void> {
        CallerResolver.require(servletRequest)
        val id = try { PublicId(contentId) } catch (_: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
        return when (restoreContentUseCase.restore(id)) {
            ContentLifecycleResult.SUCCESS -> ResponseEntity.noContent().build()
            ContentLifecycleResult.NOT_FOUND -> ResponseEntity.notFound().build()
            ContentLifecycleResult.INVALID_TRANSITION -> ResponseEntity.badRequest().build()
            ContentLifecycleResult.FORBIDDEN -> ResponseEntity.status(403).build()
        }
    }
}
