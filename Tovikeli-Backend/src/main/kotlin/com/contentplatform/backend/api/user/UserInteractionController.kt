package com.contentplatform.backend.api.user

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.interaction.GetUserInteractionHistoryUseCase
import com.contentplatform.backend.application.interaction.InteractionQueryUserNotFoundException
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.domain.interaction.InteractionCursor
import com.contentplatform.backend.domain.interaction.InteractionType
import jakarta.servlet.http.HttpServletRequest
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
 * Read-only user Interaction history API (Stage 19).
 *
 *   GET /api/v1/users/{userId}/interactions
 *
 * Flow: HTTP -> this controller -> [GetUserInteractionHistoryUseCase] ->
 * InteractionRepository. The controller never touches a Repository directly and
 * contains no business logic.
 *
 * Semantics (HTTP boundary only):
 *  - `userId` (path) uses the existing PublicId semantics; the requested user
 *    is resolved by the use case. A non-existent user is HTTP 404 with an empty
 *    body.
 *  - `type` is an optional query parameter (LIKE / FAVORITE / REPOST / BOOKMARK).
 *    Absent means "no type filter" (all interaction types). An invalid value is
 *    HTTP 400 with an empty body; the use case is never invoked for invalid input.
 *  - `limit` is an optional query parameter, default 20, accepted range 0..100.
 *    Out-of-range and non-integer values are HTTP 400 with an empty body;
 *    limit=0 is HTTP 200 [] without invoking the use case.
 *  - `cursor` is an optional query parameter carrying the versioned Base64URL
 *    keyset cursor of a previous page. Absent means "first page". The raw value
 *    is strictly decoded by [InteractionCursor.decode]; blank input, invalid
 *    Base64URL, a missing/unknown version, a malformed field count, a
 *    missing/non-Long/negative createdAt or an invalid interaction id are all
 *    answered with HTTP 400 and never reach the use case.
 *  - Pagination is stateless keyset pagination over the frozen ordering
 *    (created_at DESC, interaction_id DESC). When another page exists the
 *    response carries a standard HTTP `Link: <...>; rel="next"` header whose
 *    URL keeps the current userId, type, limit and replaces the cursor with
 *    the new nextCursor. No Link header is sent on the last page, on empty
 *    results, for limit=0 or for 400 responses.
 *  - The response body is a flat JSON array of [InteractionResponse]; no
 *    envelope, no pagination metadata object, no custom next-cursor header.
 *  - Content is hydrated as a lightweight [HydratedContentResponse] when
 *    available; null when the content does not exist (Stage 25).
 *  - DISABLED users are allowed; their interaction history is still queryable.
 *
 * Wiring: conditional on a configured datasource, mirroring the other
 * persistence-backed use cases, so the no-datasource context keeps working.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/interactions")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class UserInteractionController(
    private val getHistoryUseCase: GetUserInteractionHistoryUseCase
) {

    companion object {
        private const val DEFAULT_LIMIT = 20
        private const val MAX_LIMIT = 100
    }

    @GetMapping
    fun getUserInteractions(
        @PathVariable("userId") userId: String,
        @RequestParam(name = "type", required = false) type: String?,
        @RequestParam(name = "limit", defaultValue = "20") limit: Int,
        @RequestParam(name = "cursor", required = false) cursor: String?,
        servletRequest: HttpServletRequest
    ): ResponseEntity<List<InteractionResponse>> {
        val caller = CallerResolver.require(servletRequest)
        if (userId.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        if (caller.userId.value != userId) {
            return ResponseEntity.status(403).build()
        }
        val interactionType = if (type == null) {
            null
        } else {
            try {
                InteractionType.valueOf(type)
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().build()
            }
        }
        if (limit !in 0..MAX_LIMIT) {
            return ResponseEntity.badRequest().build()
        }
        val decodedCursor = if (cursor == null) {
            null
        } else {
            try {
                InteractionCursor.decode(cursor)
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().build()
            }
        }
        if (limit == 0) {
            return ResponseEntity.ok(emptyList())
        }
        val page = try {
            getHistoryUseCase.getHistory(caller.userId, interactionType, limit, decodedCursor)
        } catch (e: InteractionQueryUserNotFoundException) {
            return ResponseEntity.notFound().build()
        }
        val body = page.items.map { InteractionResponse.from(it) }
        val nextCursor = page.nextCursor ?: return ResponseEntity.ok(body)
        return ResponseEntity.ok()
            .header(HttpHeaders.LINK, buildNextLink(userId, interactionType, limit, nextCursor))
            .body(body)
    }

    private fun buildNextLink(
        userId: String,
        type: InteractionType?,
        limit: Int,
        nextCursor: InteractionCursor
    ): String {
        val builder = UriComponentsBuilder
            .fromPath("/api/v1/users/{userId}/interactions")
            .queryParam("limit", limit)
            .queryParam("cursor", nextCursor.encode())
        if (type != null) {
            builder.queryParam("type", type.name)
        }
        val uri = builder
            .buildAndExpand(userId)
            .encode()
            .toUriString()
        return "<$uri>; rel=\"next\""
    }
}
