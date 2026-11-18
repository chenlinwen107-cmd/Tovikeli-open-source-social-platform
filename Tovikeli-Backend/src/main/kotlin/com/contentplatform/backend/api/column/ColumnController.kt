package com.contentplatform.backend.api.column

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.column.ColumnUseCase
import com.contentplatform.backend.application.history.BrowseHistoryUseCase
import com.contentplatform.backend.domain.common.PublicId
import com.contentplatform.backend.repository.ColumnArticle
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CreateColumnArticleRequest(
    val title: String,
    val category: String,
    val cover: String = "",
    val summary: String = "",
    val content: String
)

@RestController
@RequestMapping("/api/v1/articles")
@ConditionalOnProperty(name = ["spring.datasource.url"])
class ColumnController(private val columns: ColumnUseCase, private val history: BrowseHistoryUseCase) {
    @GetMapping
    fun list(
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "new") sort: String,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        req: HttpServletRequest
    ): ResponseEntity<Any> {
        if (offset < 0 || limit !in 1..100 || sort !in setOf("new", "hot")) return ResponseEntity.badRequest().build()
        val userId = CallerResolver.resolve(req)?.userId
        val articles = columns.list(category, sort, userId, limit, offset)
        val featured = articles.firstOrNull { it.featured }
        val categories = columns.categories().map { mapOf("name" to it.first, "count" to it.second) }
        return ResponseEntity.ok(mapOf(
            "featured" to featured?.let { mapArticle(it, false) },
            "articles" to articles.map { mapArticle(it, false) },
            "categories" to categories,
            "total" to articles.size,
            "hasMore" to (articles.size == limit)
        ))
    }

    @GetMapping("/trending")
    fun trending(req: HttpServletRequest): ResponseEntity<Any> {
        val articles = columns.trending(CallerResolver.resolve(req)?.userId, 10)
        return ResponseEntity.ok(mapOf("articles" to articles.map { mapArticle(it, false) }))
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: String, req: HttpServletRequest): ResponseEntity<Any> {
        val result = columns.detail(PublicId(id), CallerResolver.resolve(req)?.userId)
            ?: return ResponseEntity.notFound().build()
        CallerResolver.resolve(req)?.userId?.let { u -> history.record(u, "article", id, result.first.title, "文章", "/article/$id", "file-text", result.first.author.nickname, result.first.cover) }
        return ResponseEntity.ok(mapOf(
            "article" to mapArticle(result.first, true),
            "related" to result.second.map { mapArticle(it, false) }
        ))
    }

    @PostMapping
    fun create(@RequestBody body: CreateColumnArticleRequest, req: HttpServletRequest): ResponseEntity<Any> = try {
        val article = columns.create(
            CallerResolver.require(req).userId,
            body.title, body.category, body.cover, body.summary, body.content
        )
        ResponseEntity.status(201).body(mapOf("article" to mapArticle(article, true)))
    } catch (e: IllegalArgumentException) {
        ResponseEntity.badRequest().build()
    }

    @PostMapping("/{id}/like")
    fun like(@PathVariable id: String, req: HttpServletRequest): ResponseEntity<Any> = try {
        val article = columns.like(CallerResolver.require(req).userId, PublicId(id))
            ?: return ResponseEntity.notFound().build()
        ResponseEntity.ok(mapOf("liked" to article.liked, "likeCount" to article.likeCount))
    } catch (e: RuntimeException) {
        ResponseEntity.badRequest().build()
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String, req: HttpServletRequest): ResponseEntity<Void> = try {
        if (columns.remove(CallerResolver.require(req).userId, PublicId(id))) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
    } catch (e: SecurityException) {
        ResponseEntity.status(403).build()
    }

    private fun mapArticle(a: ColumnArticle, includeContent: Boolean): Map<String, Any?> = mapOf(
        "id" to a.id,
        "title" to a.title,
        "summary" to a.summary,
        "cover" to a.cover,
        "category" to a.category,
        "featured" to a.featured,
        "views" to a.views,
        "likeCount" to a.likeCount,
        "commentCount" to a.commentCount,
        "readMins" to a.readMins,
        "createdAt" to a.createdAt,
        "author" to mapOf(
            "id" to a.author.id,
            "username" to a.author.username,
            "nickname" to a.author.nickname,
            "avatar" to a.author.avatar
        ),
        "liked" to a.liked,
        "content" to if (includeContent) a.content else null
    )
}
