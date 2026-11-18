package com.contentplatform.backend.api.tag

import com.contentplatform.backend.api.content.ContentCursorCodec
import com.contentplatform.backend.api.content.ContentResponse
import com.contentplatform.backend.application.content.GetContentByTagUseCase
import com.contentplatform.backend.application.tag.GetTagByIdUseCase
import com.contentplatform.backend.application.tag.SearchTagsUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.ContentCursor
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder

/**
 * Read-only Tag API.
 *
 *   GET /api/v1/tags                search tags by normalized name (Stage 15-C)
 *   GET /api/v1/tags/{id}           read one Tag detail
 *   GET /api/v1/tags/{tagId}/contents   public Content associated with a tag
 *                                       (Stage 15-C, keyset paginated)
 *
 * Flow: HTTP -> this controller -> [SearchTagsUseCase] / [GetTagByIdUseCase] /
 * [GetContentByTagUseCase]. The controller never touches a Repository directly
 * and contains no business logic. EXTERNAL and PLATFORM tags are both read
 * through the same use cases / DTOs; no origin-specific branching exists here.
 *
 * Search semantics (HTTP boundary only):
 *  - `q` is required and must be non-blank; missing/empty/whitespace is HTTP
 *    400 with an empty body. The value is trimmed before reaching the use case.
 *  - `limit` is optional (default 20, range 0..100). Out-of-range and
 *    non-integer values are HTTP 400 with an empty body. After full parameter
 *    validation, limit=0 returns HTTP 200 [] without invoking the use case.
 *  - Responses are a flat JSON `List<TagResponse>`; no envelope, no metadata,
 *    no ranking score.
 *
 * Tag -> Content semantics (HTTP boundary only):
 *  - `tagId` (path) uses the existing PublicId semantics; the requested tag is
 *    resolved once. A non-existent tag is HTTP 404 with an empty body; an
 *    existing tag with no matching public content is HTTP 200 [].
 *  - `limit` matches the browse/search contract (default 20, 0..100).
 *  - `cursor` is the existing Stage 14 versioned Base64URL content cursor,
 *    decoded strictly at this boundary (blank / malformed -> HTTP 400).
 *  - Pagination follows Stage 14: `Link: <...>; rel="next"` is returned only
 *    when another page exists, keeping the current limit and replacing the
 *    cursor. No X-Next-Cursor header, no envelope, no cursor metadata in the
 *    body (a flat `List<ContentResponse>`).
 *
 * Not-found semantics (detail): when the detail use case returns null the
 * controller responds HTTP 404 with an empty body; otherwise HTTP 200 with the
 * API [TagResponse].
 *
 * PublicId: raw path/path-variable values are wrapped by the existing
 * [PublicId] value object (its only rule: non-blank). No new parser and no
 * prefix contract is introduced at the API layer.
 *
 * Wiring: conditional on a configured datasource, mirroring the Stage 9
 * persistence-backed query use cases, so the no-datasource context keeps
 * working.
 */
@RestController
@RequestMapping("/api/v1/tags")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class TagController(
    private val getTagByIdUseCase: GetTagByIdUseCase,
    private val searchTagsUseCase: SearchTagsUseCase,
    private val getContentByTagUseCase: GetContentByTagUseCase
) {

    companion object {
        private const val DEFAULT_LIMIT = 20
        private const val MAX_LIMIT = 100
    }

    @GetMapping
    fun searchTags(
        @RequestParam(name = "q", required = false) q: String?,
        // default value is a compile-time literal (Spring annotation requirement);
        // it intentionally matches DEFAULT_LIMIT above.
        @RequestParam(name = "limit", defaultValue = "20") limit: Int
    ): ResponseEntity<List<TagResponse>> {
        // q is required and must be non-blank; blank q can never be a search.
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        if (limit !in 0..MAX_LIMIT) {
            return ResponseEntity.badRequest().build()
        }
        // limit=0 short-circuit: everything is validated, but no use case /
        // repository / DB access may happen.
        if (limit == 0) {
            return ResponseEntity.ok(emptyList())
        }
        val tags = searchTagsUseCase.search(q.trim(), limit)
        return ResponseEntity.ok(tags.map { TagResponse.from(it) })
    }

    @GetMapping("/{tagId}/contents")
    fun getContentByTag(
        @PathVariable("tagId") tagId: String,
        // default value is a compile-time literal (Spring annotation requirement);
        // it intentionally matches DEFAULT_LIMIT above.
        @RequestParam(name = "limit", defaultValue = "20") limit: Int,
        @RequestParam(name = "cursor", required = false) cursor: String?
    ): ResponseEntity<List<ContentResponse>> {
        if (tagId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        if (limit !in 0..MAX_LIMIT) {
            return ResponseEntity.badRequest().build()
        }
        // Absent cursor means "first page"; any present cursor is strictly
        // decoded here and malformed cursors are 400 (never reaching the use
        // case). Unknown-but-well-formed tags are NOT a cursor problem: they
        // are handled below via the tag existence check.
        val decodedCursor = if (cursor == null) {
            null
        } else {
            try {
                ContentCursorCodec.decode(cursor)
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().build()
            }
        }
        // limit=0 is frozen as "200 [] with no content page query": all
        // parameters above are validated first. The tag existence check still
        // runs so an unknown tag keeps its 404 semantics even at limit=0.
        val page = if (limit == 0) {
            getContentByTagUseCase.getByTag(PublicId(tagId), decodedCursor, 0)
        } else {
            getContentByTagUseCase.getByTag(PublicId(tagId), decodedCursor, limit)
        }
        if (page == null) {
            return ResponseEntity.notFound().build()
        }
        val body = page.contents.map { ContentResponse.from(it) }
        val nextCursor = page.nextCursor ?: return ResponseEntity.ok(body)
        return ResponseEntity.ok()
            .header(HttpHeaders.LINK, buildNextLink(PublicId(tagId), limit, nextCursor))
            .body(body)
    }

    @GetMapping("/{id}")
    fun getTag(@PathVariable("id") id: String): ResponseEntity<TagResponse> {
        if (id.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val tag = getTagByIdUseCase.getById(PublicId(id))
            ?: return ResponseEntity.notFound().build<TagResponse>()
        return ResponseEntity.ok(TagResponse.from(tag))
    }

    /**
     * Builds the standard `Link: <...>; rel="next"` header value for the next
     * tag-contents page. The URL keeps the current [limit] and replaces the
     * cursor with the newly generated Base64URL [nextCursor].
     */
    private fun buildNextLink(
        tagId: PublicId,
        limit: Int,
        nextCursor: ContentCursor
    ): String {
        val uri = UriComponentsBuilder
            .fromPath("/api/v1/tags/{tagId}/contents")
            .queryParam("limit", limit)
            .queryParam("cursor", ContentCursorCodec.encode(nextCursor))
            .buildAndExpand(tagId.value)
            .encode()
            .toUriString()
        return "<$uri>; rel=\"next\""
    }
}
