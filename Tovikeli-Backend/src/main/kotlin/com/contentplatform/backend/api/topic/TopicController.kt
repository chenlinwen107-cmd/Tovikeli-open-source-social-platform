package com.contentplatform.backend.api.topic

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.api.content.ContentResponse
import com.contentplatform.backend.repository.ContentRepository
import com.contentplatform.backend.application.topic.TopicUseCases
import com.contentplatform.backend.domain.common.PublicId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/topics")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class TopicController(
    private val topics: TopicUseCases,
    private val contentRepository: ContentRepository
) {
    @GetMapping
    fun list(@RequestParam(defaultValue = "20") limit: Int): ResponseEntity<Map<String, Any>> {
        if (limit !in 0..100) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(mapOf("topics" to topics.hot(limit).map { TopicResponse.from(it) }))
    }

    @GetMapping("/following")
    fun following(request: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        val caller = CallerResolver.resolve(request) ?: return ResponseEntity.ok(mapOf("topics" to emptyList<TopicResponse>()))
        return ResponseEntity.ok(mapOf("topics" to topics.following(caller.userId).map { TopicResponse.from(it, true) }))
    }

    @GetMapping("/{name}")
    fun detail(@PathVariable name: String, request: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        if (name.isBlank()) return ResponseEntity.badRequest().build()
        val topic = topics.findByName(name) ?: return ResponseEntity.notFound().build()
        val caller = CallerResolver.resolve(request)
        val following = caller?.let { topics.isFollowing(topic.id, it.userId) } ?: false
        val page = contentRepository.findPublicContentByTopic(topic.id, null, 50) ?: return ResponseEntity.notFound().build()
        val summary = com.contentplatform.backend.repository.TopicSummary(topic, topics.postCount(topic.id), topics.followerCount(topic.id), 0)
        return ResponseEntity.ok(mapOf(
            "topic" to TopicResponse.from(summary, following),
            "posts" to page.contents.map { ContentResponse.from(it) }
        ))
    }

    @PostMapping("/{name}/follow")
    fun follow(@PathVariable name: String, request: HttpServletRequest): ResponseEntity<Map<String, Boolean>> {
        val caller = CallerResolver.require(request)
        val topic = topics.findByName(name) ?: return ResponseEntity.notFound().build()
        val currentlyFollowing = topics.isFollowing(topic.id, caller.userId)
        val following = if (currentlyFollowing) {
            topics.unfollow(topic.id, caller.userId)
            false
        } else {
            topics.follow(topic.id, caller.userId)
            true
        }
        return ResponseEntity.ok(mapOf("following" to following))
    }

    @DeleteMapping("/{name}/follow")
    fun unfollow(@PathVariable name: String, request: HttpServletRequest): ResponseEntity<Map<String, Boolean>> {
        val caller = CallerResolver.require(request)
        val topic = topics.findByName(name) ?: return ResponseEntity.notFound().build()
        topics.unfollow(topic.id, caller.userId)
        return ResponseEntity.ok(mapOf("following" to false))
    }
}
