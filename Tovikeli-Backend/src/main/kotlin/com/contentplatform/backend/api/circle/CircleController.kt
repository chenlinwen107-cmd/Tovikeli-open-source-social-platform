package com.contentplatform.backend.api.circle

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.api.content.ContentResponse
import com.contentplatform.backend.application.circle.CircleUseCase
import com.contentplatform.backend.domain.common.PublicId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CreateCircleRequest(val name: String, val description: String = "", val category: String = "兴趣", val icon: String = "compass", val color: String = "#2b54f0")
data class CirclePostRequest(val content: String, val mediaIds: List<String> = emptyList())
data class CircleChatRequest(val content: String)

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
@RequestMapping("/api/v1/circles")
class CircleController(
    private val circles: CircleUseCase,
    private val detail: com.contentplatform.backend.application.content.GetContentDetailUseCase
) {
    @GetMapping
    fun list(@RequestParam(required = false) category: String?, req: HttpServletRequest): ResponseEntity<Any> =
        ResponseEntity.ok(mapOf("circles" to circles.list(category, CallerResolver.resolve(req)?.userId)))

    @PostMapping
    fun create(@RequestBody r: CreateCircleRequest, req: HttpServletRequest): ResponseEntity<Any> {
        return try {
            ResponseEntity.status(201).body(mapOf("circle" to circles.create(CallerResolver.require(req).userId, r.name, r.description, r.category, r.icon, r.color)))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/{slug}")
    fun get(@PathVariable slug: String, req: HttpServletRequest): ResponseEntity<Any> {
        val c = circles.get(slug, CallerResolver.resolve(req)?.userId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("circle" to c, "members" to circles.members(c.id)))
    }

    @PostMapping("/{id}/join")
    fun join(@PathVariable id: Long, req: HttpServletRequest): ResponseEntity<Any> = toggle(id, req)

    @PostMapping("/{id}/leave")
    fun leave(@PathVariable id: Long, req: HttpServletRequest): ResponseEntity<Any> = toggle(id, req)

    private fun toggle(id: Long, req: HttpServletRequest): ResponseEntity<Any> {
        val u = CallerResolver.require(req).userId
        circles.toggleMember(u, id)
        val c = circles.get(id, u) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("joined" to c.joined, "memberCount" to c.memberCount))
    }

    @GetMapping("/{slug}/posts")
    fun posts(@PathVariable slug: String, req: HttpServletRequest): ResponseEntity<Any> {
        val c = circles.get(slug, CallerResolver.resolve(req)?.userId) ?: return ResponseEntity.notFound().build()
        val posts = circles.postIds(c.id).mapNotNull { id ->
            detail.getById(PublicId(id))?.let(ContentResponse::from)
        }
        return ResponseEntity.ok(mapOf("posts" to posts))
    }

    @PostMapping("/{slug}/posts")
    fun post(@PathVariable slug: String, @RequestBody r: CirclePostRequest, req: HttpServletRequest): ResponseEntity<Any> {
        return try {
            val u = CallerResolver.require(req).userId
            val c = circles.get(slug, u) ?: return ResponseEntity.notFound().build()
            val id = circles.post(u, c.id, r.content, r.mediaIds)
            ResponseEntity.status(201).body(mapOf("post" to ContentResponse.from(detail.getById(id)!!)))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/{slug}/chat")
    fun chat(@PathVariable slug: String, req: HttpServletRequest): ResponseEntity<Any> {
        val u = CallerResolver.resolve(req)?.userId
        val c = circles.get(slug, u) ?: return ResponseEntity.notFound().build()
        if (u == null || !circles.isMember(u, c.id)) return ResponseEntity.ok(mapOf("locked" to true, "messages" to emptyList<Any>()))
        return ResponseEntity.ok(mapOf("locked" to false, "messages" to circles.messages(c.id)))
    }

    @PostMapping("/{slug}/chat")
    fun sendChat(@PathVariable slug: String, @RequestBody r: CircleChatRequest, req: HttpServletRequest): ResponseEntity<Any> {
        return try {
            val u = CallerResolver.require(req).userId
            val c = circles.get(slug, u) ?: return ResponseEntity.notFound().build()
            ResponseEntity.status(201).body(mapOf("message" to circles.chat(u, c.id, r.content)))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }
}