package com.contentplatform.backend.api.feed

import com.contentplatform.backend.api.content.ContentCursorCodec
import com.contentplatform.backend.api.content.ContentResponse
import com.contentplatform.backend.application.feed.FeedQuery
import com.contentplatform.backend.application.feed.FeedResult
import com.contentplatform.backend.application.feed.HomeFeedUseCase
import com.contentplatform.backend.domain.feed.FeedType
import com.contentplatform.backend.application.feed.FeedRefillTrigger
import com.contentplatform.backend.repository.ContentCursor
import com.contentplatform.backend.repository.ContentRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder

/**
 * Public Feed API (P9-G2).
 *
 *   GET /api/v1/feed?limit=20&cursor=<opaque>
 *
 * Returns the newest content of the global public content pool, ordered by the
 * PLATFORM ingestion time (`content.created_at DESC, content.id DESC`). It
 * reuses the existing public browse capability unchanged:
 *
 *   HTTP -> [FeedController] -> [BrowseContentUseCase] -> ContentRepository.findPublicContent
 *
 * The Feed is the unfiltered browse: `instanceId`, `authorId` and `hasMedia` are
 * always null, so the frozen public semantics stay in the repository:
 * `content.status = ACTIVE AND content.visibility = PUBLIC AND
 * instance.allow_index = TRUE`. The controller never re-implements those rules
 * and never touches a Repository or SQL directly.
 *
 * Contract details (aligned with the existing public browse endpoint):
 *  - `limit` is optional, default 20, accepted range 0..[ContentRepository.MAX_PUBLIC_BROWSE_LIMIT]
 *    (100). Out-of-range input is answered with HTTP 400 before the use case is
 *    invoked; non-integer input is answered with HTTP 400 by Spring MVC's
 *    standard type conversion. No feed-specific limit cap is invented.
 *  - `limit=0` returns HTTP 200 with an empty array and does not invoke the use
 *    case (a malformed cursor still yields 400, matching the browse endpoint).
 *  - `cursor` is the existing versioned Base64URL stateless keyset cursor
 *    ([ContentCursorCodec]). Absent means first page; any present value is
 *    strictly decoded here and a malformed value yields HTTP 400. No second
 *    cursor encoder and no OFFSET / page-number pagination exist.
 *  - When another page exists the response carries the standard
 *    `Link: <...>; rel="next"` header whose URL keeps the current limit and
 *    carries the new cursor. No Link header is sent on the last page, on empty
 *    results, for limit=0 or for 400 responses.
 *  - The response body is the existing flat JSON array of [ContentResponse];
 *    no Feed-specific DTO, ranking, recommendation or seen/unseen field is
 *    introduced.
 *  - The endpoint is public: no session, no `X-User-Id` and no identity-based
 *    filtering. Anonymous requests succeed (consistent with the project's
 *    permitting security chain).
 *
 * Background refill (P9-G3):
 *  - When the repository returns a page with NO next cursor, the public pool has
 *    reached its tail, so one non-blocking background refill is requested
 *    through [FeedRefillTrigger]. The current page is still returned
 *    immediately; the request never waits for any source API.
 *  - The trigger is best-effort and never throws. A refill failure (or even a
 *    failure to schedule it) is isolated from the response and never turns the
 *    Feed request into a 5xx.
 *  - `limit=0`, invalid parameters and malformed cursors never trigger a refill
 *    (no page was queried).
 *
 * Wiring is conditional on a configured datasource, mirroring the existing
 * persistence-backed read controllers so the no-datasource context still starts.
 */
@RestController
@RequestMapping("/api/v1/feed")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class FeedController(
    private val homeFeedUseCase: HomeFeedUseCase,
    private val feedRefillTrigger: FeedRefillTrigger
) {

    private val log = LoggerFactory.getLogger(FeedController::class.java)

    companion object {
        private const val DEFAULT_FEED_LIMIT = 20
    }

    @GetMapping
    fun feed(
        @RequestParam(name = "limit", defaultValue = "20") limit: Int,
        @RequestParam(name = "cursor", required = false) cursor: String?,
        @RequestParam(name = "type", defaultValue = "latest") type: String,
        servletRequest: jakarta.servlet.http.HttpServletRequest
    ): ResponseEntity<List<ContentResponse>> {
        if (limit !in 0..ContentRepository.MAX_PUBLIC_BROWSE_LIMIT) {
            return ResponseEntity.badRequest().build()
        }
        val feedType = try {
            FeedType.valueOf(type.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
        val decodedCursor = if (cursor == null) null else try {
            ContentCursorCodec.decode(cursor)
        } catch (_: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }
        if (limit == 0) return ResponseEntity.ok(emptyList())

        val caller = com.contentplatform.backend.api.authentication.CallerResolver.resolve(servletRequest)
        if (feedType == FeedType.FOLLOWING && caller == null) {
            return ResponseEntity.status(401).build()
        }
        return when (val result = homeFeedUseCase.query(
            FeedQuery(feedType, caller?.userId, decodedCursor, limit)
        )) {
            is FeedResult.NotImplemented -> ResponseEntity.status(501).build()
            is FeedResult.Ready -> {
                if (result.page.nextCursor == null && feedType == FeedType.LATEST) {
                    requestBackgroundRefill()
                }
                val body = result.page.contents.map { ContentResponse.from(it) }
                val nextCursor = result.page.nextCursor ?: return ResponseEntity.ok(body)
                ResponseEntity.ok()
                    .header(HttpHeaders.LINK, buildNextLink(limit, feedType, nextCursor))
                    .body(body)
            }
        }
    }

    /**
     * Best-effort, non-blocking refill request. The [FeedRefillTrigger] contract
     * already guarantees it never throws, but the response stability is enforced
     * here as well: a refill problem must never turn a Feed read into a 5xx.
     */
    private fun requestBackgroundRefill() {
        try {
            feedRefillTrigger.trigger()
        } catch (e: Exception) {
            log.warn("Feed background refill request was ignored: error={}", e.message)
        }
    }

    /**
     * Builds the standard `Link: <...>; rel="next"` header value for the next
     * Feed page. The URL keeps the current limit and carries the newly generated
     * Base64URL [nextCursor] under the Feed path.
     */
    private fun buildNextLink(limit: Int, type: FeedType, nextCursor: ContentCursor): String {
        val uri = UriComponentsBuilder.fromPath("/api/v1/feed")
            .queryParam("limit", limit)
            .queryParam("type", type.name.lowercase())
            .queryParam("cursor", ContentCursorCodec.encode(nextCursor))
            .build()
            .encode()
            .toUriString()
        return "<$uri>; rel=\"next\""
    }
}
