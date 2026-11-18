package com.contentplatform.backend.api.comment

import com.contentplatform.backend.api.authentication.AuthenticationException
import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.comment.CommentContentNotFoundException
import com.contentplatform.backend.application.comment.CommentLikeUseCase
import com.contentplatform.backend.application.comment.CommentNotFoundException
import com.contentplatform.backend.application.comment.CommentContentNotInteractableException
import com.contentplatform.backend.application.comment.CommentParentContentMismatchException
import com.contentplatform.backend.application.comment.CommentParentNotFoundException
import com.contentplatform.backend.application.comment.CommentUserNotFoundException
import com.contentplatform.backend.application.comment.CreateCommentUseCase
import com.contentplatform.backend.application.comment.DeleteCommentUseCase
import com.contentplatform.backend.application.comment.EditCommentResult
import com.contentplatform.backend.application.comment.EditCommentUseCase
import com.contentplatform.backend.application.comment.GetCommentRepliesUseCase
import com.contentplatform.backend.application.comment.GetCommentUseCase
import com.contentplatform.backend.application.comment.GetCommentsByContentUseCase
import com.contentplatform.backend.domain.comment.CommentCursor
import com.contentplatform.backend.domain.common.PublicId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder

/**
 * Platform Comment API (Stage 21-A + 21-B + 21-C1 + Stage 26 + Stage 37 + Stage 40-C).
 *
 *   POST   /api/v1/contents/{contentId}/comments       create comment  (authenticated)
 *   GET    /api/v1/contents/{contentId}/comments       list comments by content  (public)
 *   PATCH  /api/v1/comments/{commentId}                edit comment  (authenticated, self-only)
 *   DELETE /api/v1/comments/{commentId}                delete comment  (authenticated, self-only)
 *
 * Flow: HTTP -> this controller -> UseCase -> CommentRepository.
 * The controller never touches a Repository directly.
 *
 * Authentication: protected endpoints extract the caller identity from the
 * server-side session via [CallerResolver.require]. The caller's [PublicId]
 * is used for authorization; userId is NOT accepted from request body/query.
 *
 * Create semantics (HTTP boundary only):
 *  - `contentId` (path) is validated at this boundary; blank is HTTP 400.
 *  - Request body JSON is required; missing / malformed JSON is HTTP 400 via
 *    Spring MVC's standard handling.
 *  - `text` is required and must be non-blank; a missing, empty or
 *    whitespace-only value is HTTP 400 with an empty body.
 *  - `parentCommentId` is optional; an invalid (blank) value is HTTP 400.
 *  - A newly created comment is HTTP 201 Created with the API
 *    [CommentResponse] projection.
 *  - A non-existent user is HTTP 404 via [CommentUserNotFoundException].
 *  - A non-existent content is HTTP 404 via [CommentContentNotFoundException].
 *  - Content that is not public/interactable is HTTP 409 via
 *    [CommentContentNotInteractableException].
 *  - A non-existent parent comment is HTTP 404 via
 *    [CommentParentNotFoundException].
 *  - A parent comment belonging to another content is HTTP 409 via
 *    [CommentParentContentMismatchException].
 *  - Missing or invalid session returns HTTP 401.
 *
 * List semantics (HTTP boundary only):
 *  - `contentId` (path) is validated; blank is HTTP 400.
 *  - `limit` (query, optional): default 20, range 0..100. Out-of-range or
 *    non-numeric is HTTP 400.
 *  - `cursor` (query, optional): versioned Base64URL keyset cursor. Absent
 *    means "first page". The raw value is strictly decoded by
 *    [CommentCursor.decode]; blank input, invalid Base64URL, a missing/unknown
 *    version, a malformed field count, a missing/non-Long/negative createdAt
 *    or an invalid comment id are all answered with HTTP 400 and never reach
 *    the use case.
 *  - Pagination is stateless keyset pagination over the frozen ordering
 *    (created_at ASC, id ASC). When another page exists the response carries
 *    a standard HTTP `Link: <...>; rel="next"` header whose URL keeps the
 *    current contentId and limit and replaces the cursor with the new
 *    nextCursor. No Link header is sent on the last page, on empty results,
 *    for limit=0 or for 400 responses.
 *  - Returns HTTP 200 with a JSON array of [CommentResponse].
 *  - Non-existing content returns HTTP 200 [].
 *
 * Edit semantics (HTTP boundary only):
 *  - `commentId` (path) is validated at this boundary; blank is HTTP 400.
 *  - Request body JSON is required; missing / malformed JSON is HTTP 400.
 *  - `text` is required and must be non-blank; missing, empty or
 *    whitespace-only is HTTP 400.
 *  - Only ACTIVE comments may be edited; DELETED comments return HTTP 422.
 *  - Only the comment owner may edit; non-owner returns HTTP 403.
 *  - A non-existent comment returns HTTP 404.
 *  - Successful edit returns HTTP 200 with the updated [CommentResponse].
 *  - Missing or invalid session returns HTTP 401.
 *
 * Delete semantics (HTTP boundary only):
 *  - `commentId` (path) is validated at this boundary; blank is HTTP 400.
 *  - Soft delete: ACTIVE -> DELETED. Already DELETED remains DELETED.
 *  - Successful delete, comment not found, already DELETED, and repeated
 *    delete all return HTTP 204 No Content.
 *  - Non-owner attempting to delete an ACTIVE comment returns HTTP 403.
 *  - A non-existent user is HTTP 404 via [CommentUserNotFoundException].
 *  - Missing or invalid session returns HTTP 401.
 *
 * Wiring: conditional on a configured datasource, mirroring the other
 * persistence-backed use cases, so the no-datasource context keeps working.
 */
@RestController
@ConditionalOnProperty(name = ["spring.datasource.url"])
class CommentController(
    private val createCommentUseCase: CreateCommentUseCase,
    private val deleteCommentUseCase: DeleteCommentUseCase,
    private val editCommentUseCase: EditCommentUseCase,
    private val getCommentRepliesUseCase: GetCommentRepliesUseCase,
    private val getCommentUseCase: GetCommentUseCase,
    private val getCommentsByContentUseCase: GetCommentsByContentUseCase,
    private val commentLikeUseCase: CommentLikeUseCase
) {

    @PostMapping("/api/v1/contents/{contentId}/comments")
    fun createComment(
        @PathVariable("contentId") contentId: String,
        @RequestBody request: CreateCommentRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<CommentResponse> {
        val caller = CallerResolver.require(servletRequest)
        if (contentId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val text = request.text.orEmpty()
        if (text.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val parentCommentId: String? = request.parentCommentId?.trim()?.ifBlank { null }
        if (request.parentCommentId != null && parentCommentId == null) {
            return ResponseEntity.badRequest().build()
        }
        val contentIdPid = PublicId(contentId)
        val parentPid = parentCommentId?.let { PublicId(it) }
        return try {
            val comment = createCommentUseCase.create(caller.userId, contentIdPid, parentPid, text)
            ResponseEntity.status(201).body(CommentResponse.from(comment))
        } catch (e: CommentUserNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (e: CommentContentNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (e: CommentContentNotInteractableException) {
            ResponseEntity.status(409).build()
        } catch (e: CommentParentNotFoundException) {
            ResponseEntity.notFound().build()
        } catch (e: CommentParentContentMismatchException) {
            ResponseEntity.status(409).build()
        }
    }

    @GetMapping("/api/v1/contents/{contentId}/comments")
    fun getCommentsByContent(
        @PathVariable("contentId") contentId: String,
        @RequestParam(name = "limit", defaultValue = "20") limit: Int,
        @RequestParam(name = "cursor", required = false) cursor: String?
    ): ResponseEntity<List<CommentResponse>> {
        if (contentId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        if (limit < 0 || limit > 100) {
            return ResponseEntity.badRequest().build()
        }
        val decodedCursor = if (cursor == null) {
            null
        } else {
            try {
                CommentCursor.decode(cursor)
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().build()
            }
        }
        if (limit == 0) {
            return ResponseEntity.ok(emptyList())
        }
        val page = getCommentsByContentUseCase.getByContent(PublicId(contentId), limit, decodedCursor)
        val body = page.items.map { CommentResponse.from(it) }
        val nextCursor = page.nextCursor ?: return ResponseEntity.ok(body)
        return ResponseEntity.ok()
            .header(HttpHeaders.LINK, buildCommentsNextLink(contentId, limit, nextCursor))
            .body(body)
    }

    @DeleteMapping("/api/v1/comments/{commentId}")
    fun deleteComment(
        @PathVariable("commentId") commentId: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<Void> {
        val caller = CallerResolver.require(servletRequest)
        if (commentId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val commentPid = PublicId(commentId)
        return try {
            val existing = getCommentUseCase.get(commentPid)
            if (existing == null) {
                deleteCommentUseCase.delete(commentPid, caller.userId)
                ResponseEntity.noContent().build()
            } else {
                val deleted = deleteCommentUseCase.delete(commentPid, caller.userId)
                if (deleted) {
                    ResponseEntity.noContent().build()
                } else {
                    ResponseEntity.status(403).build()
                }
            }
        } catch (e: CommentUserNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @PatchMapping("/api/v1/comments/{commentId}")
    fun editComment(
        @PathVariable("commentId") commentId: String,
        @RequestBody request: EditCommentRequest,
        servletRequest: HttpServletRequest
    ): ResponseEntity<CommentResponse> {
        val caller = CallerResolver.require(servletRequest)
        if (commentId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val text = request.text.orEmpty()
        if (text.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val result = editCommentUseCase.edit(PublicId(commentId), caller.userId, text)
        return when (result) {
            is EditCommentResult.Success -> ResponseEntity.ok(CommentResponse.from(result.comment))
            is EditCommentResult.NotFound -> ResponseEntity.notFound().build()
            is EditCommentResult.Forbidden -> ResponseEntity.status(403).build()
            is EditCommentResult.Rejected -> ResponseEntity.status(422).build()
        }
    }

    @GetMapping("/api/v1/comments/{commentId}/like")
    fun getCommentLike(
        @PathVariable("commentId") commentId: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<CommentLikeResponse> {
        if (commentId.isBlank()) return ResponseEntity.badRequest().build()
        val userId = runCatching { CallerResolver.require(servletRequest).userId }.getOrNull()
        val state = commentLikeUseCase.get(PublicId(commentId), userId)
        return ResponseEntity.ok(CommentLikeResponse.from(state))
    }

    @PostMapping("/api/v1/comments/{commentId}/like")
    fun likeComment(
        @PathVariable("commentId") commentId: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<CommentLikeResponse> {
        val caller = CallerResolver.require(servletRequest)
        if (commentId.isBlank()) return ResponseEntity.badRequest().build()
        return try {
            ResponseEntity.ok(CommentLikeResponse.from(commentLikeUseCase.like(PublicId(commentId), caller.userId)))
        } catch (e: CommentNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/api/v1/comments/{commentId}/like")
    fun unlikeComment(
        @PathVariable("commentId") commentId: String,
        servletRequest: HttpServletRequest
    ): ResponseEntity<CommentLikeResponse> {
        val caller = CallerResolver.require(servletRequest)
        if (commentId.isBlank()) return ResponseEntity.badRequest().build()
        return try {
            ResponseEntity.ok(CommentLikeResponse.from(commentLikeUseCase.unlike(PublicId(commentId), caller.userId)))
        } catch (e: CommentNotFoundException) {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/api/v1/comments/{commentId}")
    fun getComment(
        @PathVariable("commentId") commentId: String
    ): ResponseEntity<CommentResponse> {
        if (commentId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        val comment = getCommentUseCase.get(PublicId(commentId))
        return if (comment != null) {
            ResponseEntity.ok(CommentResponse.from(comment))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/api/v1/comments/{commentId}/replies")
    fun getCommentReplies(
        @PathVariable("commentId") commentId: String,
        @RequestParam(name = "limit", defaultValue = "20") limit: Int,
        @RequestParam(name = "cursor", required = false) cursor: String?
    ): ResponseEntity<List<CommentResponse>> {
        if (commentId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        if (limit < 0 || limit > 100) {
            return ResponseEntity.badRequest().build()
        }
        val decodedCursor = if (cursor == null) {
            null
        } else {
            try {
                CommentCursor.decode(cursor)
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().build()
            }
        }
        if (limit == 0) {
            return ResponseEntity.ok(emptyList())
        }
        val page = getCommentRepliesUseCase.getReplies(PublicId(commentId), limit, decodedCursor)
        val body = page.items.map { CommentResponse.from(it) }
        val nextCursor = page.nextCursor ?: return ResponseEntity.ok(body)
        return ResponseEntity.ok()
            .header(HttpHeaders.LINK, buildRepliesNextLink(commentId, limit, nextCursor))
            .body(body)
    }

    private fun buildCommentsNextLink(contentId: String, limit: Int, nextCursor: CommentCursor): String {
        val builder = UriComponentsBuilder
            .fromPath("/api/v1/contents/{contentId}/comments")
            .queryParam("limit", limit)
            .queryParam("cursor", nextCursor.encode())
        val uri = builder
            .buildAndExpand(contentId)
            .encode()
            .toUriString()
        return "<$uri>; rel=\"next\""
    }

    private fun buildRepliesNextLink(commentId: String, limit: Int, nextCursor: CommentCursor): String {
        val builder = UriComponentsBuilder
            .fromPath("/api/v1/comments/{commentId}/replies")
            .queryParam("limit", limit)
            .queryParam("cursor", nextCursor.encode())
        val uri = builder
            .buildAndExpand(commentId)
            .encode()
            .toUriString()
        return "<$uri>; rel=\"next\""
    }
}
