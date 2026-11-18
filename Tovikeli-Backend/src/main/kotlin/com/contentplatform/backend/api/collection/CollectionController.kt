package com.contentplatform.backend.api.collection

import com.contentplatform.backend.api.authentication.CallerResolver
import com.contentplatform.backend.application.collection.CollectionUseCase
import com.contentplatform.backend.domain.common.PublicId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class CreateCollectionRequest(val title: String, val description: String = "", val cover: String = "")
data class AddCollectionItemRequest(val targetType: String, val targetId: String)

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = ["spring.datasource.url"])
@RequestMapping("/api/v1/collections")
class CollectionController(private val collections: CollectionUseCase) {
    @GetMapping
    fun list(req: HttpServletRequest): ResponseEntity<Any> =
        ResponseEntity.ok(mapOf("collections" to collections.list(CallerResolver.resolve(req)?.userId)))

    @GetMapping("/mine")
    fun mine(req: HttpServletRequest): ResponseEntity<Any> = try {
        ResponseEntity.ok(mapOf("collections" to collections.mine(CallerResolver.require(req).userId)))
    } catch (_: SecurityException) {
        ResponseEntity.status(401).build()
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: String, req: HttpServletRequest): ResponseEntity<Any> =
        collections.detail(PublicId(id), CallerResolver.resolve(req)?.userId)?.let {
            ResponseEntity.ok(mapOf("collection" to mapSummary(it.collection), "items" to it.items.map { item -> mapItem(item) }))
        } ?: ResponseEntity.notFound().build()

    @PostMapping
    fun create(@RequestBody body: CreateCollectionRequest, req: HttpServletRequest): ResponseEntity<Any> = try {
        val detail = collections.create(CallerResolver.require(req).userId, body.title, body.description, body.cover)
        ResponseEntity.status(201).body(mapOf("collection" to mapSummary(detail.collection)))
    } catch (_: IllegalArgumentException) {
        ResponseEntity.badRequest().build()
    }

    @PostMapping("/{id}/items")
    fun add(@PathVariable id: String, @RequestBody body: AddCollectionItemRequest, req: HttpServletRequest): ResponseEntity<Any> = try {
        val result = collections.addItem(CallerResolver.require(req).userId, PublicId(id), body.targetType, PublicId(body.targetId))
        ResponseEntity.status(201).body(mapOf("item" to mapOf("itemId" to result.itemId, "type" to result.type)))
    } catch (_: IllegalArgumentException) {
        ResponseEntity.badRequest().build()
    } catch (_: SecurityException) {
        ResponseEntity.status(403).build()
    }

    @DeleteMapping("/{id}/items/{itemId}")
    fun remove(@PathVariable id: String, @PathVariable itemId: String, req: HttpServletRequest): ResponseEntity<Void> = try {
        if (collections.removeItem(CallerResolver.require(req).userId, PublicId(id), PublicId(itemId))) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
    } catch (_: IllegalArgumentException) { ResponseEntity.badRequest().build() }
      catch (_: SecurityException) { ResponseEntity.status(403).build() }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: String, req: HttpServletRequest): ResponseEntity<Void> = try {
        if (collections.delete(CallerResolver.require(req).userId, PublicId(id))) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
    } catch (_: IllegalArgumentException) { ResponseEntity.badRequest().build() }
      catch (_: SecurityException) { ResponseEntity.status(403).build() }

    private fun mapSummary(s: com.contentplatform.backend.repository.CollectionSummary) = mapOf(
        "id" to s.id, "title" to s.title, "description" to s.description, "cover" to s.cover,
        "itemCount" to s.itemCount, "owner" to mapOf("id" to s.owner.id, "username" to s.owner.username, "nickname" to s.owner.nickname, "avatar" to s.owner.avatar),
        "isOwner" to s.isOwner
    )
    private fun mapItem(i: com.contentplatform.backend.repository.CollectionItem) = mapOf(
        "itemId" to i.itemId, "type" to i.type
    ) + i.data
}
