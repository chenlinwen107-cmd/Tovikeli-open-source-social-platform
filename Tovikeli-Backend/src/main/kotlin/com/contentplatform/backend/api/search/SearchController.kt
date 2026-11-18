package com.contentplatform.backend.api.search

import com.contentplatform.backend.api.content.ContentResponse
import com.contentplatform.backend.application.content.SearchContentUseCase
import com.contentplatform.backend.application.forum.ForumUseCase
import com.contentplatform.backend.application.topic.TopicUseCases
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.AuthorRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Read-only full-text Search API (Stage 15-A Search Foundation).
 *
 *   GET /api/v1/search   full-text search over public content bodies
 *
 * Search flow: HTTP -> this controller -> [SearchContentUseCase] ->
 * ContentRepository. The controller never touches a Repository directly and
 * contains no business logic.
 *
 * Search semantics (HTTP boundary only):
 *  - `q` is a required query parameter (free text, no query language). A
 *    missing, empty or whitespace-only value is answered with HTTP 400 and an
 *    empty body before any use case invocation.
 *  - `limit` is an optional query parameter, default 20, accepted range
 *    0..100 (mirroring the ContentRepository browse limit bound). Negative,
 *    out-of-range and non-integer values are answered with HTTP 400 with an
 *    empty body; limit=0 is answered with HTTP 200 [] without calling the
 *    use case or the repository.
 *  - `instanceId`, `authorId` and `sourceId` are optional filters with the
 *    project's PublicId semantics: an absent value means no filter, a blank/
 *    whitespace value is answered with HTTP 400 and an empty body, and a
 *    syntactically valid but unknown id yields HTTP 200 [] (search is not a
 *    404 domain).
 *  - Every request parameter is validated first; only after full validation
 *    passes is the limit=0 short-circuit or the use case invocation reached.
 *  - The response body is a flat JSON `List<ContentResponse>`; no envelope,
 *    no search metadata, no relevance scores and no pagination headers are
 *    returned.
 *
 * Wiring: conditional on a configured datasource, mirroring the Stage 9/10B
 * persistence-backed read use cases so the no-datasource context keeps
 * working.
 */
@RestController
@RequestMapping("/api/v1/search")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class SearchController(
    private val searchContentUseCase: SearchContentUseCase,
    private val forumUseCase: ForumUseCase,
    private val topicUseCases: TopicUseCases,
    private val authorRepository: AuthorRepository
) {

    companion object {
        private const val DEFAULT_SEARCH_LIMIT = 20
        private const val MAX_SEARCH_LIMIT = 100
    }

    @GetMapping
    fun searchContent(
        @RequestParam(name = "q", required = false) q: String?,
        // default value is a compile-time literal (Spring annotation requirement);
        // it intentionally matches DEFAULT_SEARCH_LIMIT above.
        @RequestParam(name = "limit", defaultValue = "20") limit: Int,
        @RequestParam(name = "instanceId", required = false) instanceId: String?,
        @RequestParam(name = "authorId", required = false) authorId: String?,
        @RequestParam(name = "sourceId", required = false) sourceId: String?
    ): ResponseEntity<List<ContentResponse>> {
        // q is required and must be non-blank: reject before anything else.
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest().build()
        }
        if (limit !in 0..MAX_SEARCH_LIMIT) {
            return ResponseEntity.badRequest().build()
        }
        // Filters keep the PublicId boundary rules used by Browse: absent
        // means "no filter", blank/whitespace is a 400 (wrapping it into
        // PublicId would throw and surface as a 500, and no error framework
        // exists), and unknown-but-valid ids simply match nothing.
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
        val sourceFilter = when {
            sourceId == null -> null
            sourceId.isBlank() -> return ResponseEntity.badRequest().build()
            else -> PublicId(sourceId)
        }
        // limit=0 is frozen as "200 [] with no DB access": all parameters have
        // already been validated, and the use case is deliberately not invoked.
        if (limit == 0) {
            return ResponseEntity.ok(emptyList())
        }

        val content = searchContentUseCase.search(q.trim(), instanceFilter, authorFilter, sourceFilter, limit)
        return ResponseEntity.ok(content.map { ContentResponse.from(it) })
    }
    @GetMapping("/all")
    fun searchAll(
        @RequestParam(name = "q", required = false) q: String?,
        @RequestParam(name = "limit", defaultValue = "20") limit: Int
    ): ResponseEntity<Map<String, Any>> {
        if (q == null || q.isBlank() || limit !in 1..100) return ResponseEntity.badRequest().build()
        val keyword = q.trim()
        val content = searchContentUseCase.search(keyword, null, null, null, limit)
        val contentResponses = content.map { item -> ContentResponse.from(item, author = authorRepository.findById(item.authorId)) }
        val threads = forumUseCase.searchThreads(keyword, limit)
        val topics = topicUseCases.search(keyword, limit)
        return ResponseEntity.ok(mapOf(
            "posts" to contentResponses,
            "threads" to threads,
            "topics" to topics.map { mapOf(
                "id" to it.topic.id.value,
                "name" to it.topic.name,
                "post_count" to it.postCount,
                "followers" to it.followerCount,
                "hot" to it.hot
            ) }
        ))
    }

}
