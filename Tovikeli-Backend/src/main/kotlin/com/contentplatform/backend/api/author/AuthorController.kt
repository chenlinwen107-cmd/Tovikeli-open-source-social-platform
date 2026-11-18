package com.contentplatform.backend.api.author

import com.contentplatform.backend.api.content.ContentCursorCodec
import com.contentplatform.backend.api.content.ContentResponse
import com.contentplatform.backend.application.author.AuthorContentListingUseCase
import com.contentplatform.backend.application.author.GetAuthorByIdUseCase
import com.contentplatform.backend.application.author.GetLocalAuthorByUserIdUseCase
import com.contentplatform.backend.domain.common.PublicId
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
 * Read-only Author API.
 *
 *   GET /api/v1/authors/{id}              Author detail
 *   GET /api/v1/authors/{authorId}/contents  Public content by author
 *
 * Detail flow: HTTP -> this controller -> [GetAuthorByIdUseCase] -> AuthorRepository.
 * Content listing flow: HTTP -> this controller -> [AuthorContentListingUseCase] -> ContentRepository.
 * The controller never touches a Repository directly and contains no business
 * logic.
 *
 * Detail not-found semantics: when the use case returns null the controller
 * responds HTTP 404 with an empty body; otherwise HTTP 200 with the API
 * [AuthorResponse].
 *
 * Content listing semantics (HTTP boundary only):
 *  - `limit` is an optional query parameter, default 20.
 *  - Accepted range 0..100. Out-of-range input is answered with HTTP 400
 *    before the use case is invoked.
 *  - `cursor` is an optional query parameter carrying the versioned Base64URL
 *    keyset cursor. Absent means "first page". Malformed cursors are answered
 *    with HTTP 400 and never reach the use case.
 *  - limit=0 returns HTTP 200 with an empty array without invoking the use
 *    case. All other parameters are still validated before the early return.
 *  - Unknown/syntactically valid authorId is NOT a 404: it yields HTTP 200
 *    with an empty array (browse semantics).
 *  - Pagination uses the standard `Link: <...>; rel="next"` header whose URL
 *    remains under `/api/v1/authors/{authorId}/contents`.
 *
 * PublicId: the raw path variable is wrapped by the existing [PublicId] value
 * object (its only rule: non-blank). No new parser and no prefix contract is
 * introduced at the API layer.
 *
 * Wiring: conditional on a configured datasource, mirroring the Stage 9
 * persistence-backed query use cases, so the no-datasource context keeps
 * working.
 */
@RestController
@RequestMapping("/api/v1/authors")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class AuthorController(
    private val getAuthorByIdUseCase: GetAuthorByIdUseCase,
    private val getLocalAuthorByUserIdUseCase: GetLocalAuthorByUserIdUseCase,
    private val authorContentListingUseCase: AuthorContentListingUseCase
) {

    companion object {
        private const val DEFAULT_BROWSE_LIMIT = 20
        private const val MAX_BROWSE_LIMIT = 100
    }

    @GetMapping("/by-user/{userId}")
    fun getLocalAuthorByUserId(
        @PathVariable userId: String
    ): ResponseEntity<AuthorResponse> {
        val author = getLocalAuthorByUserIdUseCase.getByUserId(
            PublicId(userId)
        ) ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(AuthorResponse.from(author))
    }

    @GetMapping("/{id}")
    fun getAuthor(@PathVariable("id") id: String): ResponseEntity<AuthorResponse> {
        val author = getAuthorByIdUseCase.getById(PublicId(id))
            ?: return ResponseEntity.notFound().build<AuthorResponse>()
        return ResponseEntity.ok(AuthorResponse.from(author))
    }

    @GetMapping("/{authorId}/contents")
    fun getAuthorContents(
        @PathVariable("authorId") authorId: String,
        @RequestParam(name = "limit", defaultValue = "20") limit: Int,
        @RequestParam(name = "cursor", required = false) cursor: String?
    ): ResponseEntity<List<ContentResponse>> {
        if (authorId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        if (limit !in 0..MAX_BROWSE_LIMIT) {
            return ResponseEntity.badRequest().build()
        }
        val decodedCursor = if (cursor == null) {
            null
        } else {
            try {
                ContentCursorCodec.decode(cursor)
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().build()
            }
        }
        if (limit == 0) {
            return ResponseEntity.ok(emptyList())
        }

        val page = authorContentListingUseCase.list(
            authorId = PublicId(authorId),
            cursor = decodedCursor,
            limit = limit
        )
        val body = page.contents.map { ContentResponse.from(it) }
        val nextCursor = page.nextCursor ?: return ResponseEntity.ok(body)
        return ResponseEntity.ok()
            .header(HttpHeaders.LINK, buildNextLink(authorId, limit, nextCursor))
            .body(body)
    }

    private fun buildNextLink(
        authorId: String,
        limit: Int,
        nextCursor: com.contentplatform.backend.repository.ContentCursor
    ): String {
        val uri = UriComponentsBuilder.fromPath("/api/v1/authors/$authorId/contents")
            .queryParam("limit", limit)
            .queryParam("cursor", ContentCursorCodec.encode(nextCursor))
            .build().encode().toUriString()
        return "<$uri>; rel=\"next\""
    }
}
